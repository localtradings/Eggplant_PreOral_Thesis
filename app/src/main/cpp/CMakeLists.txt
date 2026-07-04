cmake_minimum_required(VERSION 3.22.1)
project(eggplant_detector LANGUAGES CXX)

add_library(ncnn SHARED IMPORTED)
set_target_properties(
    ncnn
    PROPERTIES IMPORTED_LOCATION
    "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libncnn.so"
)

add_library(eggplant_detector SHARED eggplant_detector_jni.cpp)
target_compile_features(eggplant_detector PRIVATE cxx_std_17)
target_compile_options(eggplant_detector PRIVATE -Wall -Wextra -Werror)
target_include_directories(
    eggplant_detector
    PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/third_party/ncnn/include"
)

find_library(log-lib log)
find_library(android-lib android)
find_library(vulkan-lib vulkan)
target_link_libraries(eggplant_detector ncnn ${log-lib} ${android-lib} ${vulkan-lib})
