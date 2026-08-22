cmake_minimum_required(VERSION 3.22.1)
project(campusai_mnn LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(campusai_mnn SHARED campusai_mnn_jni.cpp)
target_include_directories(campusai_mnn PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/vendor/mnn/include"
    "${CMAKE_CURRENT_SOURCE_DIR}/vendor/mnn"
)
target_compile_definitions(campusai_mnn PRIVATE MNN_BUILD_LLM=1)
target_compile_options(campusai_mnn PRIVATE -fexceptions -frtti -Wall -Wextra)
target_link_options(campusai_mnn PRIVATE "-Wl,-z,max-page-size=16384")

set(MNN_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
foreach(name IN ITEMS MNN MNN_Express MNN_CL MNN_Vulkan MNNOpenCV MNNAudio llm)
    add_library(${name} SHARED IMPORTED)
    set_target_properties(${name} PROPERTIES IMPORTED_LOCATION "${MNN_LIB_DIR}/lib${name}.so")
endforeach()

find_library(log_lib log)
find_library(android_lib android)
target_link_libraries(campusai_mnn PRIVATE llm MNN_Express MNN MNN_CL MNN_Vulkan MNNOpenCV MNNAudio ${android_lib} ${log_lib})
