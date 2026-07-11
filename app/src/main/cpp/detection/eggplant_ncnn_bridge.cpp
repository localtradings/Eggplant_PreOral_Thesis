#include <jni.h>
#include <android/trace.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <mutex>
#include <string>
#include <vector>

#include "ncnn/cpu.h"
#include "ncnn/gpu.h"
#include "ncnn/mat.h"
#include "ncnn/net.h"

namespace {

constexpr float kNmsThreshold = 0.45f;
constexpr int kMaxDetections = 100;
constexpr int kMaximumCpuInferenceThreads = 4;

struct Proposal {
    int class_index;
    float confidence;
    float left;
    float top;
    float right;
    float bottom;
};

struct Engine {
    ncnn::Net net;
    int input_size;
    int class_count;
    std::mutex inference_mutex;
    std::vector<unsigned char> rgb_scratch;
};

std::mutex gpu_mutex;
bool gpu_initialized = false;

class DebugTraceSection {
public:
#if defined(EGGPLANT_DEBUG_TRACE)
    explicit DebugTraceSection(const char* name) : active_(true) {
        ATrace_beginSection(name);
    }

    void end() {
        if (active_) {
            ATrace_endSection();
            active_ = false;
        }
    }

    ~DebugTraceSection() { end(); }

private:
    bool active_;
#else
    explicit DebugTraceSection(const char*) {}
    void end() {}
#endif
};

int bounded_inference_thread_count() {
    const int cpu_count = std::max(1, ncnn::get_cpu_count());
    const int half_cpu_count = std::max(1, cpu_count / 2);
    return std::min(half_cpu_count, kMaximumCpuInferenceThreads);
}

void configure_stable_cpu_runtime() {
    // The packaged NCNN runtime contains LLVM OpenMP. On Android, CPU affinity
    // can change between camera sessions; keep affinity disabled, but allow a
    // small bounded worker pool so the 768px model does not run single-threaded.
    const int inference_threads = bounded_inference_thread_count();
    const std::string thread_count = std::to_string(inference_threads);
    setenv("OMP_NUM_THREADS", thread_count.c_str(), 1);
    setenv("OMP_THREAD_LIMIT", thread_count.c_str(), 1);
    setenv("OMP_DYNAMIC", "FALSE", 1);
    setenv("OMP_WAIT_POLICY", "PASSIVE", 1);
    setenv("OMP_PROC_BIND", "FALSE", 1);
    setenv("KMP_AFFINITY", "disabled", 1);
    setenv("KMP_BLOCKTIME", "0", 1);
    unsetenv("OMP_PLACES");
    ncnn::set_omp_num_threads(inference_threads);
    ncnn::set_omp_dynamic(0);
    ncnn::set_kmp_blocktime(0);
}

void configure_stable_cpu_options(ncnn::Option& options) {
    options.num_threads = bounded_inference_thread_count();
    options.openmp_blocktime = 0;
}

void ensure_gpu_instance() {
    std::lock_guard<std::mutex> lock(gpu_mutex);
    if (!gpu_initialized) {
        ncnn::create_gpu_instance();
        gpu_initialized = true;
    }
}

float intersection_over_union(const Proposal& a, const Proposal& b) {
    const float left = std::max(a.left, b.left);
    const float top = std::max(a.top, b.top);
    const float right = std::min(a.right, b.right);
    const float bottom = std::min(a.bottom, b.bottom);
    const float intersection = std::max(0.0f, right - left) * std::max(0.0f, bottom - top);
    const float area_a = std::max(0.0f, a.right - a.left) * std::max(0.0f, a.bottom - a.top);
    const float area_b = std::max(0.0f, b.right - b.left) * std::max(0.0f, b.bottom - b.top);
    const float union_area = area_a + area_b - intersection;
    return union_area <= 0.0f ? 0.0f : intersection / union_area;
}

std::string to_string(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jfloatArray detection_failure(JNIEnv* env, const char* code, const char* message) {
    const std::string detail = std::string("NCNN_") + code + ": " + message;
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    if (exception != nullptr) env->ThrowNew(exception, detail.c_str());
    return nullptr;
}

jfloatArray run_detection(
    JNIEnv* env,
    Engine* engine,
    const unsigned char* pixels,
    int width,
    int height,
    float confidence_threshold
) {
    DebugTraceSection preprocess_trace("eggplant.native.preprocess");
    const float scale = std::min(
        static_cast<float>(engine->input_size) / static_cast<float>(width),
        static_cast<float>(engine->input_size) / static_cast<float>(height)
    );
    const int resized_width = std::max(1, static_cast<int>(std::round(width * scale)));
    const int resized_height = std::max(1, static_cast<int>(std::round(height * scale)));
    ncnn::Mat resized = ncnn::Mat::from_pixels_resize(
        pixels,
        ncnn::Mat::PIXEL_RGB,
        width,
        height,
        resized_width,
        resized_height
    );
    if (resized.empty()) return detection_failure(env, "PREPROCESS_FAILED", "Could not resize the camera frame.");

    const int pad_left = (engine->input_size - resized_width) / 2;
    const int pad_top = (engine->input_size - resized_height) / 2;
    ncnn::Mat input(engine->input_size, engine->input_size, 3);
    if (input.empty()) return detection_failure(env, "ALLOCATION_FAILED", "Could not allocate the model input tensor.");
    input.fill(114.0f);
    for (int channel = 0; channel < 3; ++channel) {
        const ncnn::Mat source_channel = resized.channel(channel);
        ncnn::Mat target_channel = input.channel(channel);
        for (int y = 0; y < resized_height; ++y) {
            const float* source_row = source_channel.row(y);
            float* target_row = target_channel.row(y + pad_top) + pad_left;
            std::copy(source_row, source_row + resized_width, target_row);
        }
    }
    float* input_values = input;
    const size_t input_values_count = input.total();
    for (size_t index = 0; index < input_values_count; ++index) {
        input_values[index] *= 1.0f / 255.0f;
    }
    preprocess_trace.end();

    DebugTraceSection inference_trace("eggplant.native.extract");
    ncnn::Extractor extractor = engine->net.create_extractor();
    if (extractor.input("in0", input) != 0) {
        return detection_failure(env, "INPUT_FAILED", "NCNN rejected the input tensor.");
    }
    ncnn::Mat output;
    if (extractor.extract("out0", output) != 0) {
        return detection_failure(env, "EXTRACT_FAILED", "NCNN could not run model inference.");
    }
    if (output.empty() || output.h != 4 + engine->class_count || output.w <= 0) {
        return detection_failure(env, "INVALID_OUTPUT", "The model output shape does not match its runtime manifest.");
    }
    inference_trace.end();

    DebugTraceSection postprocess_trace("eggplant.native.postprocess");
    std::vector<Proposal> proposals;
    proposals.reserve(output.w);
    for (int candidate = 0; candidate < output.w; ++candidate) {
        int best_class = -1;
        float best_confidence = confidence_threshold;
        for (int class_index = 0; class_index < engine->class_count; ++class_index) {
            const float confidence = output.row(4 + class_index)[candidate];
            if (confidence >= best_confidence) {
                best_confidence = confidence;
                best_class = class_index;
            }
        }
        if (best_class < 0) continue;

        const float center_x = output.row(0)[candidate];
        const float center_y = output.row(1)[candidate];
        const float box_width = output.row(2)[candidate];
        const float box_height = output.row(3)[candidate];
        Proposal proposal{
            best_class,
            best_confidence,
            std::clamp((center_x - box_width * 0.5f - pad_left) / scale, 0.0f, static_cast<float>(width)),
            std::clamp((center_y - box_height * 0.5f - pad_top) / scale, 0.0f, static_cast<float>(height)),
            std::clamp((center_x + box_width * 0.5f - pad_left) / scale, 0.0f, static_cast<float>(width)),
            std::clamp((center_y + box_height * 0.5f - pad_top) / scale, 0.0f, static_cast<float>(height)),
        };
        if (proposal.right > proposal.left && proposal.bottom > proposal.top) proposals.push_back(proposal);
    }

    std::sort(proposals.begin(), proposals.end(), [](const Proposal& a, const Proposal& b) {
        return a.confidence > b.confidence;
    });
    std::vector<Proposal> selected;
    selected.reserve(std::min(static_cast<int>(proposals.size()), kMaxDetections));
    for (const Proposal& proposal : proposals) {
        bool suppressed = false;
        for (const Proposal& kept : selected) {
            if (proposal.class_index == kept.class_index &&
                intersection_over_union(proposal, kept) > kNmsThreshold) {
                suppressed = true;
                break;
            }
        }
        if (!suppressed) selected.push_back(proposal);
        if (static_cast<int>(selected.size()) >= kMaxDetections) break;
    }

    std::vector<float> flattened;
    flattened.reserve(selected.size() * 6);
    for (const Proposal& proposal : selected) {
        flattened.insert(flattened.end(), {
            static_cast<float>(proposal.class_index),
            proposal.confidence,
            proposal.left,
            proposal.top,
            proposal.right,
            proposal.bottom,
        });
    }
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(flattened.size()));
    if (result != nullptr && !flattened.empty()) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(flattened.size()), flattened.data());
    }
    return result;
}

void convert_rgba_to_rotated_rgb(
    Engine* engine,
    const unsigned char* rgba,
    int row_stride,
    int crop_left,
    int crop_top,
    int crop_width,
    int crop_height,
    int rotation_degrees,
    int& output_width,
    int& output_height
) {
    output_width = rotation_degrees == 90 || rotation_degrees == 270 ? crop_height : crop_width;
    output_height = rotation_degrees == 90 || rotation_degrees == 270 ? crop_width : crop_height;
    engine->rgb_scratch.resize(static_cast<size_t>(output_width) * output_height * 3);
    for (int source_y = 0; source_y < crop_height; ++source_y) {
        const unsigned char* row = rgba +
            static_cast<size_t>(crop_top + source_y) * row_stride +
            static_cast<size_t>(crop_left) * 4;
        for (int source_x = 0; source_x < crop_width; ++source_x) {
            int target_x = source_x;
            int target_y = source_y;
            if (rotation_degrees == 90) {
                target_x = crop_height - 1 - source_y;
                target_y = source_x;
            } else if (rotation_degrees == 180) {
                target_x = crop_width - 1 - source_x;
                target_y = crop_height - 1 - source_y;
            } else if (rotation_degrees == 270) {
                target_x = source_y;
                target_y = crop_width - 1 - source_x;
            }
            const unsigned char* source = row + source_x * 4;
            unsigned char* target = engine->rgb_scratch.data() +
                (static_cast<size_t>(target_y) * output_width + target_x) * 3;
            target[0] = source[0];
            target[1] = source[1];
            target[2] = source[2];
        }
    }
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_eggplant_detector_detection_ncnn_NativeNcnnBridge_hasVulkan(JNIEnv*, jobject) {
    ensure_gpu_instance();
    return ncnn::get_gpu_count() > 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_eggplant_detector_detection_ncnn_NativeNcnnBridge_create(
    JNIEnv* env,
    jobject,
    jstring param_path,
    jstring bin_path,
    jint input_size,
    jint class_count,
    jboolean use_vulkan
) {
    if (input_size <= 0 || class_count <= 0) return 0;
    auto* engine = new Engine();
    engine->input_size = input_size;
    engine->class_count = class_count;
    configure_stable_cpu_runtime();
    if (use_vulkan == JNI_TRUE) ensure_gpu_instance();
    configure_stable_cpu_options(engine->net.opt);
    engine->net.opt.use_vulkan_compute = use_vulkan == JNI_TRUE && ncnn::get_gpu_count() > 0;
    const std::string param = to_string(env, param_path);
    const std::string weights = to_string(env, bin_path);
    if (param.empty() || weights.empty() ||
        engine->net.load_param(param.c_str()) != 0 ||
        engine->net.load_model(weights.c_str()) != 0) {
        delete engine;
        return 0;
    }
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_eggplant_detector_detection_ncnn_NativeNcnnBridge_detect(
    JNIEnv* env,
    jobject,
    jlong handle,
    jbyteArray rgb_bytes,
    jint width,
    jint height,
    jfloat confidence_threshold
) {
    auto* engine = reinterpret_cast<Engine*>(handle);
    if (engine == nullptr || rgb_bytes == nullptr || width <= 0 || height <= 0 ||
        env->GetArrayLength(rgb_bytes) != width * height * 3) {
        return detection_failure(env, "INVALID_INPUT", "RGB frame dimensions or buffer length are invalid.");
    }
    std::lock_guard<std::mutex> lock(engine->inference_mutex);
    jboolean copied = JNI_FALSE;
    auto* pixels = reinterpret_cast<unsigned char*>(env->GetByteArrayElements(rgb_bytes, &copied));
    if (pixels == nullptr) return detection_failure(env, "BUFFER_ACCESS_FAILED", "Could not access the RGB frame buffer.");
    jfloatArray result = run_detection(env, engine, pixels, width, height, confidence_threshold);
    env->ReleaseByteArrayElements(rgb_bytes, reinterpret_cast<jbyte*>(pixels), JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_eggplant_detector_detection_ncnn_NativeNcnnBridge_detectRgba(
    JNIEnv* env,
    jobject,
    jlong handle,
    jobject rgba_bytes,
    jint width,
    jint height,
    jint row_stride,
    jint crop_left,
    jint crop_top,
    jint crop_width,
    jint crop_height,
    jint rotation_degrees,
    jfloat confidence_threshold
) {
    auto* engine = reinterpret_cast<Engine*>(handle);
    if (engine == nullptr || rgba_bytes == nullptr || width <= 0 || height <= 0 ||
        row_stride < width * 4 ||
        crop_left < 0 || crop_top < 0 || crop_width <= 0 || crop_height <= 0 ||
        crop_left + crop_width > width || crop_top + crop_height > height ||
        (rotation_degrees != 0 && rotation_degrees != 90 && rotation_degrees != 180 && rotation_degrees != 270)) {
        return detection_failure(env, "INVALID_INPUT", "Direct camera frame metadata is invalid.");
    }
    auto* pixels = reinterpret_cast<unsigned char*>(env->GetDirectBufferAddress(rgba_bytes));
    const jlong capacity = env->GetDirectBufferCapacity(rgba_bytes);
    if (pixels == nullptr || capacity < static_cast<jlong>(row_stride) * height) {
        return detection_failure(env, "BUFFER_ACCESS_FAILED", "Camera frame is not a complete direct buffer.");
    }
    std::lock_guard<std::mutex> lock(engine->inference_mutex);
    int output_width = 0;
    int output_height = 0;
    convert_rgba_to_rotated_rgb(
        engine,
        pixels,
        row_stride,
        crop_left,
        crop_top,
        crop_width,
        crop_height,
        rotation_degrees,
        output_width,
        output_height
    );
    return run_detection(
        env,
        engine,
        engine->rgb_scratch.data(),
        output_width,
        output_height,
        confidence_threshold
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_eggplant_detector_detection_ncnn_NativeNcnnBridge_destroy(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<Engine*>(handle);
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM*, void*) {
    configure_stable_cpu_runtime();
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNI_OnUnload(JavaVM*, void*) {
    std::lock_guard<std::mutex> lock(gpu_mutex);
    if (gpu_initialized) {
        ncnn::destroy_gpu_instance();
        gpu_initialized = false;
    }
}
