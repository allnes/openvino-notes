cmake_minimum_required(VERSION 3.22.1)

project(notes_llm)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (NOT DEFINED OPENVINO_GENAI_ANDROID_DIR)
    message(FATAL_ERROR "Set -DOPENVINO_GENAI_ANDROID_DIR to an Android OpenVINO GenAI package root.")
endif ()

add_library(
    notes_llm
    SHARED
    llm_bridge_jni.cpp
    llm_engine.cpp
)

target_include_directories(
    notes_llm
    PRIVATE
    "${OPENVINO_GENAI_ANDROID_DIR}/include"
)

target_link_directories(
    notes_llm
    PRIVATE
    "${OPENVINO_GENAI_ANDROID_DIR}/lib/${ANDROID_ABI}"
    "${OPENVINO_GENAI_ANDROID_DIR}/${ANDROID_ABI}/lib"
)

find_library(log_lib log)

target_link_libraries(
    notes_llm
    PRIVATE
    openvino_genai
    openvino
    ${log_lib}
)
