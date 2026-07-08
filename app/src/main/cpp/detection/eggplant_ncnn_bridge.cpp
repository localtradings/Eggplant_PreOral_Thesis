#include <jni.h>

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
};

std::mutex gpu_mutex;
bool gpu_initialized = false;

void configure_stable_cpu_runtime() {
    // The packaged NCNN runtime contains LLVM OpenMP. On Android, CPU affinity
    // can change between camera sessions; keep OpenMP out of topology/affinity
    // setup so the second scan cannot abort the whole process in native code.
    setenv("OMP_NUM_THREADS", "1", 1);
    setenv("OMP_THREAD_LIMIT", "1", 1);
    setenv("OMP_DYNAMIC", "FALSE", 1);
    setenv("OMP_PROC_BIND", "FALSE", 1);
    setenv("KMP_AFFINITY", "disabled", 1);
    setenv("KMP_BLOCKTIME", "0", 1);
    unsetenv("OMP_PLACES");
    ncnn::set_omp_num_threads(1);
    ncnn::set_omp_dynamic(0);
    ncnn::set_kmp_blocktime(0);
}

void configure_stable_cpu_options(ncnn::Option& options) {
    options.num_threads = 1;
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
        return env->NewFloatArray(0);
    }

    configure_stable_cpu_runtime();

    jboolean copied = JNI_FALSE;
    auto* pixels = reinterpret_cast<unsigned char*>(env->GetByteArrayElements(rgb_bytes, &copied));
    if (pixels == nullptr) return env->NewFloatArray(0);

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
    env->ReleaseByteArrayElements(rgb_bytes, reinterpret_cast<jbyte*>(pixels), JNI_ABORT);

    const int pad_left = (engine->input_size - resized_width) / 2;
    const int pad_right = engine->input_size - resized_width - pad_left;
    const int pad_top = (engine->input_size - resized_height) / 2;
    const int pad_bottom = engine->input_size - resized_height - pad_top;
    (void)pad_right;
    (void)pad_bottom;
    ncnn::Mat input(engine->input_size, engine->input_size, 3);
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

    ncnn::Extractor extractor = engine->net.create_extractor();
    extractor.input("in0", input);
    ncnn::Mat output;
    if (extractor.extract("out0", output) != 0 || output.h != 4 + engine->class_count) {
        return env->NewFloatArray(0);
    }

    std::vector<Proposal> proposals;
    proposals.reserve(output.w);
    for (int candidate = 0; candidate < output.w; ++candidate) {
        int best_class = -1;
        float best_confidence = confidence_threshold;
        for (int class_index = 0; class_index < engine->class_count; ++class_index) {
            const float confidence = output.row(4 + class_index)[candidate];
            if (confidence > best_confidence) {
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
        if (proposal.right > proposal.left && proposal.bottom > proposal.top) {
            proposals.push_back(proposal);
        }
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
