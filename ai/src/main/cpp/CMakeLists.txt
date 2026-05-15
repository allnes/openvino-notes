cmake_minimum_required(VERSION 3.22.1)

project(notes_llm)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (NOT DEFINED OPENVINO_GENAI_ANDROID_DIR)
    message(FATAL_ERROR "Set -DOPENVINO_GENAI_ANDROID_DIR to an Android OpenVINO GenAI package root.")
endif ()

set(OPENVINO_PREBUILD_INCLUDE_DIRS "")
foreach (include_dir
        "${OPENVINO_GENAI_ANDROID_DIR}/include"
        "${OPENVINO_GENAI_ANDROID_DIR}/runtime/include")
    if (EXISTS "${include_dir}")
        list(APPEND OPENVINO_PREBUILD_INCLUDE_DIRS "${include_dir}")
    endif ()
endforeach ()

if (NOT OPENVINO_PREBUILD_INCLUDE_DIRS)
    message(FATAL_ERROR "OpenVINO include directory was not found in ${OPENVINO_GENAI_ANDROID_DIR}.")
endif ()

set(OPENVINO_PREBUILD_LIBRARY_DIRS "")
foreach (library_dir
        "${OPENVINO_GENAI_ANDROID_DIR}/android-jni/${ANDROID_ABI}"
        "${OPENVINO_GENAI_ANDROID_DIR}/runtime/lib/aarch64"
        "${OPENVINO_GENAI_ANDROID_DIR}/lib/${ANDROID_ABI}"
        "${OPENVINO_GENAI_ANDROID_DIR}/${ANDROID_ABI}/lib")
    if (EXISTS "${library_dir}")
        list(APPEND OPENVINO_PREBUILD_LIBRARY_DIRS "${library_dir}")
    endif ()
endforeach ()

if (NOT OPENVINO_PREBUILD_LIBRARY_DIRS)
    message(FATAL_ERROR "OpenVINO library directory was not found in ${OPENVINO_GENAI_ANDROID_DIR}.")
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
    ${OPENVINO_PREBUILD_INCLUDE_DIRS}
)

target_link_directories(
    notes_llm
    PRIVATE
    ${OPENVINO_PREBUILD_LIBRARY_DIRS}
)

find_library(log_lib log)

target_link_libraries(
    notes_llm
    PRIVATE
    openvino_genai
    openvino
    ${log_lib}
)
