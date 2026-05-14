#!/usr/bin/env bash
set -euo pipefail

OPENVINO_REF="${OPENVINO_REF:-2026.1.2}"
OPENVINO_GENAI_REF="${OPENVINO_GENAI_REF:-2026.1.2.0}"
OPENVINO_CONTRIB_REF="${OPENVINO_CONTRIB_REF:-releases/2026/1}"
ONETBB_REF="${ONETBB_REF:-v2023.0.0}"

ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-35}"
ANDROID_NDK_VERSION="${ANDROID_NDK_VERSION:-29.0.14206865}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
ANDROID_NDK="${ANDROID_NDK:-${ANDROID_SDK_ROOT:+$ANDROID_SDK_ROOT/ndk/$ANDROID_NDK_VERSION}}"

BUILD_PARALLELISM="${BUILD_PARALLELISM:-2}"
RUN_ROOT="${RUN_ROOT:-${RUNNER_TEMP:-$PWD/.tmp}/openvino-android-prebuilds}"

OPENVINO_REPO="${OPENVINO_REPO:-https://github.com/openvinotoolkit/openvino.git}"
OPENVINO_CONTRIB_REPO="${OPENVINO_CONTRIB_REPO:-https://github.com/openvinotoolkit/openvino_contrib.git}"
OPENVINO_GENAI_REPO="${OPENVINO_GENAI_REPO:-https://github.com/openvinotoolkit/openvino.genai.git}"
ONETBB_REPO="${ONETBB_REPO:-https://github.com/uxlfoundation/oneTBB.git}"

SRC_DIR="$RUN_ROOT/src"
BUILD_DIR="$RUN_ROOT/build"
INSTALL_DIR="$RUN_ROOT/install"
ARTIFACTS_DIR="$RUN_ROOT/artifacts"
PACKAGE_NAME="openvino-android-${ANDROID_ABI}-${OPENVINO_REF}"
PACKAGE_ROOT="$ARTIFACTS_DIR/package/$PACKAGE_NAME"
ZIP_PATH="$ARTIFACTS_DIR/$PACKAGE_NAME.zip"

require_command() {
    local command_name="$1"

    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "Required command is missing: $command_name" >&2
        exit 1
    fi
}

validate_run_root() {
    if [[ -z "$RUN_ROOT" || "$RUN_ROOT" == "/" ]]; then
        echo "RUN_ROOT must point to a disposable build directory, got: '$RUN_ROOT'" >&2
        exit 1
    fi
}

detect_ndk_llvm_prebuilt_dir() {
    local prebuilt_root="$ANDROID_NDK/toolchains/llvm/prebuilt"
    local host_glob

    case "$(uname -s)" in
        Darwin)
            host_glob="darwin-*"
            ;;
        Linux)
            host_glob="linux-*"
            ;;
        *)
            echo "Unsupported host OS for Android NDK prebuilt tools: $(uname -s)" >&2
            exit 1
            ;;
    esac

    find "$prebuilt_root" -maxdepth 1 -type d -name "$host_glob" | sort | head -n 1
}

clone_ref() {
    local repo_url="$1"
    local ref="$2"
    local destination="$3"

    git clone --recursive --depth 1 --shallow-submodules --jobs 4 --branch "$ref" "$repo_url" "$destination"
}

write_source_manifest() {
    local manifest="$ARTIFACTS_DIR/source-manifest.txt"

    {
        echo "OpenVINO Android prebuild source manifest"
        echo "Generated at: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
        echo
        printf 'openvino_ref=%s\n' "$OPENVINO_REF"
        printf 'openvino_commit=%s\n' "$(git -C "$SRC_DIR/openvino" rev-parse HEAD)"
        printf 'openvino_genai_ref=%s\n' "$OPENVINO_GENAI_REF"
        printf 'openvino_genai_commit=%s\n' "$(git -C "$SRC_DIR/openvino.genai" rev-parse HEAD)"
        printf 'openvino_contrib_ref=%s\n' "$OPENVINO_CONTRIB_REF"
        printf 'openvino_contrib_commit=%s\n' "$(git -C "$SRC_DIR/openvino_contrib" rev-parse HEAD)"
        printf 'onetbb_ref=%s\n' "$ONETBB_REF"
        printf 'onetbb_commit=%s\n' "$(git -C "$SRC_DIR/oneTBB" rev-parse HEAD)"
        echo
        printf 'android_abi=%s\n' "$ANDROID_ABI"
        printf 'android_platform=%s\n' "$ANDROID_PLATFORM"
        printf 'android_ndk_version=%s\n' "$ANDROID_NDK_VERSION"
    } > "$manifest"
}

patch_acl_toolchain_prefix() {
    local acl_config="$SRC_DIR/openvino/src/plugins/intel_cpu/thirdparty/ACLConfig.cmake"

    # SCons must use NDK LLVM archive tools. Otherwise macOS host ranlib can
    # corrupt Android ACL static archives and break CPU plugin linking.
    perl -0pi -e 's/ov_arm_compute_add_option\("toolchain_prefix" ""\)/ov_arm_compute_add_option("toolchain_prefix" "llvm-")/' "$acl_config"

    if ! grep -q 'ov_arm_compute_add_option("toolchain_prefix" "llvm-")' "$acl_config"; then
        echo "Failed to patch ACL toolchain_prefix in $acl_config" >&2
        exit 1
    fi
}

build_onetbb() {
    cmake -S "$SRC_DIR/oneTBB" \
        -B "$BUILD_DIR/oneTBB" \
        -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR/oneTBB" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ANDROID_ABI" \
        -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
        -DANDROID_STL=c++_shared \
        -DTBB_TEST=OFF \
        -DTBB_EXAMPLES=OFF \
        -DCMAKE_SHARED_LINKER_FLAGS=-Wl,--undefined-version \
        -DCMAKE_C_COMPILER_LAUNCHER=ccache \
        -DCMAKE_CXX_COMPILER_LAUNCHER=ccache \
        2>&1 | tee "$ARTIFACTS_DIR/onetbb-configure.log"

    cmake --build "$BUILD_DIR/oneTBB" --parallel "$BUILD_PARALLELISM" \
        2>&1 | tee "$ARTIFACTS_DIR/onetbb-build.log"

    cmake --install "$BUILD_DIR/oneTBB" \
        2>&1 | tee "$ARTIFACTS_DIR/onetbb-install.log"
}

configure_openvino() {
    cmake -S "$SRC_DIR/openvino" \
        -B "$BUILD_DIR/openvino-android" \
        -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR/openvino-android" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ANDROID_ABI" \
        -DANDROID_PLATFORM="$ANDROID_PLATFORM" \
        -DANDROID_STL=c++_shared \
        -DOPENVINO_EXTRA_MODULES="$SRC_DIR/openvino_contrib/modules/java_api;$SRC_DIR/openvino.genai" \
        -DTHREADING=TBB \
        -DTBB_DIR="$INSTALL_DIR/oneTBB/lib/cmake/TBB" \
        -DENABLE_INTEL_GPU=OFF \
        -DENABLE_INTEL_NPU=OFF \
        -DENABLE_TEMPLATE=OFF \
        -DENABLE_TESTS=OFF \
        -DENABLE_FUNCTIONAL_TESTS=OFF \
        -DENABLE_SAMPLES=OFF \
        -DENABLE_TOOLS=OFF \
        -DENABLE_PYTHON=OFF \
        -DENABLE_OV_ONNX_FRONTEND=OFF \
        -DENABLE_OV_PADDLE_FRONTEND=OFF \
        -DENABLE_OV_PYTORCH_FRONTEND=OFF \
        -DENABLE_OV_TF_FRONTEND=OFF \
        -DENABLE_OV_TF_LITE_FRONTEND=OFF \
        -DENABLE_OV_JAX_FRONTEND=OFF \
        -DENABLE_OV_IR_FRONTEND=ON \
        -DENABLE_SNIPPETS_LIBXSMM_TPP=OFF \
        -DENABLE_GGUF=ON \
        -DENABLE_CLANG_FORMAT=OFF \
        -DENABLE_CLANG_TIDY=OFF \
        -DCMAKE_C_COMPILER_LAUNCHER=ccache \
        -DCMAKE_CXX_COMPILER_LAUNCHER=ccache \
        2>&1 | tee "$ARTIFACTS_DIR/openvino-configure.log"
}

build_openvino() {
    cmake --build "$BUILD_DIR/openvino-android" --parallel "$BUILD_PARALLELISM" \
        2>&1 | tee "$ARTIFACTS_DIR/openvino-build.log"
}

validate_acl_archive() {
    local acl_archive="$BUILD_DIR/openvino-android/src/plugins/intel_cpu/thirdparty/acl_build/build/arm64-v8.2-a/libarm_compute-static.a"
    local object_count

    if [[ ! -s "$acl_archive" ]]; then
        echo "ACL archive is missing or empty: $acl_archive" >&2
        exit 1
    fi

    object_count="$("$LLVM_PREBUILT_DIR/bin/llvm-ar" t "$acl_archive" | wc -l | tr -d ' ')"
    if [[ "$object_count" -le 0 ]]; then
        echo "ACL archive contains no objects: $acl_archive" >&2
        exit 1
    fi

    echo "ACL archive object count: $object_count" | tee "$ARTIFACTS_DIR/acl-archive-check.log"
}

install_openvino() {
    cmake --install "$BUILD_DIR/openvino-android" \
        2>&1 | tee "$ARTIFACTS_DIR/openvino-install.log"
}

build_java_api_jar() {
    local java_out="$ARTIFACTS_DIR/java-api"
    local jar_path="$java_out/openvino-java-api-${OPENVINO_REF}-android.jar"

    rm -rf "$java_out"
    mkdir -p "$java_out/classes"

    find "$SRC_DIR/openvino_contrib/modules/java_api/src/main/java" -name '*.java' | sort > "$java_out/java-sources.txt"
    javac --release 11 -d "$java_out/classes" @"$java_out/java-sources.txt"
    jar --create --file "$jar_path" -C "$java_out/classes" .
}

package_prebuild() {
    local runtime_dir="$INSTALL_DIR/openvino-android/runtime"
    local ndk_libcxx="$LLVM_PREBUILT_DIR/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"

    rm -rf "$ARTIFACTS_DIR/package"
    mkdir -p "$PACKAGE_ROOT/java" "$PACKAGE_ROOT/android-jni/$ANDROID_ABI" "$PACKAGE_ROOT/metadata"

    cp -R "$runtime_dir" "$PACKAGE_ROOT/runtime"
    cp "$ARTIFACTS_DIR/java-api/openvino-java-api-${OPENVINO_REF}-android.jar" "$PACKAGE_ROOT/java/"
    cp "$ndk_libcxx" "$PACKAGE_ROOT/android-jni/$ANDROID_ABI/"
    cp "$runtime_dir/lib/aarch64"/*.so "$PACKAGE_ROOT/android-jni/$ANDROID_ABI/"
    cp "$runtime_dir/3rdparty/tbb/lib"/*.so "$PACKAGE_ROOT/android-jni/$ANDROID_ABI/"
    cp "$ARTIFACTS_DIR/source-manifest.txt" "$PACKAGE_ROOT/metadata/source-manifest.txt"

    cat > "$PACKAGE_ROOT/README.md" <<EOF
# OpenVINO Android ${ANDROID_ABI} prebuild

Contents:
- \`runtime/\`: installed OpenVINO Runtime, OpenVINO GenAI, OpenVINO Tokenizers, OpenVINO Java JNI bridge, CMake config files, headers, and TBB runtime.
- \`java/\`: Java API classes jar for \`org.intel.openvino.*\`.
- \`android-jni/${ANDROID_ABI}/\`: shared libraries ready to copy into an Android app \`src/main/jniLibs/${ANDROID_ABI}\` directory, including \`libc++_shared.so\` from the Android NDK.
- \`metadata/source-manifest.txt\`: exact source refs and commits used for this build.

This package is built from fresh source checkouts for Android ${ANDROID_ABI}, Android platform ${ANDROID_PLATFORM}, and Android NDK ${ANDROID_NDK_VERSION}.
EOF

    (
        cd "$ARTIFACTS_DIR/package"
        zip -qr "$ZIP_PATH" "$PACKAGE_NAME"
    )

    ls -lh "$ZIP_PATH"
}

emit_github_outputs() {
    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
        {
            printf 'artifact_path=%s\n' "$ZIP_PATH"
            printf 'artifact_name=%s\n' "$(basename "$ZIP_PATH")"
            printf 'package_name=%s\n' "$PACKAGE_NAME"
        } >> "$GITHUB_OUTPUT"
    fi
}

main() {
    validate_run_root

    if [[ -z "$ANDROID_SDK_ROOT" ]]; then
        echo "ANDROID_SDK_ROOT or ANDROID_HOME must be set." >&2
        exit 1
    fi
    if [[ -z "$ANDROID_NDK" || ! -d "$ANDROID_NDK" ]]; then
        echo "Android NDK not found: $ANDROID_NDK" >&2
        exit 1
    fi

    require_command cmake
    require_command ninja
    require_command git
    require_command javac
    require_command jar
    require_command zip
    require_command ccache

    LLVM_PREBUILT_DIR="$(detect_ndk_llvm_prebuilt_dir)"
    if [[ -z "$LLVM_PREBUILT_DIR" ]]; then
        echo "Could not locate Android NDK LLVM prebuilt tools under $ANDROID_NDK" >&2
        exit 1
    fi
    export LLVM_PREBUILT_DIR
    export PATH="$LLVM_PREBUILT_DIR/bin:$PATH"

    echo "Using RUN_ROOT=$RUN_ROOT"
    echo "Using ANDROID_NDK=$ANDROID_NDK"
    echo "Using LLVM_PREBUILT_DIR=$LLVM_PREBUILT_DIR"

    rm -rf "$RUN_ROOT"
    mkdir -p "$SRC_DIR" "$BUILD_DIR" "$INSTALL_DIR" "$ARTIFACTS_DIR"

    clone_ref "$OPENVINO_REPO" "$OPENVINO_REF" "$SRC_DIR/openvino"
    clone_ref "$OPENVINO_CONTRIB_REPO" "$OPENVINO_CONTRIB_REF" "$SRC_DIR/openvino_contrib"
    clone_ref "$OPENVINO_GENAI_REPO" "$OPENVINO_GENAI_REF" "$SRC_DIR/openvino.genai"
    clone_ref "$ONETBB_REPO" "$ONETBB_REF" "$SRC_DIR/oneTBB"

    write_source_manifest
    patch_acl_toolchain_prefix
    ccache --zero-stats || true

    build_onetbb
    configure_openvino
    build_openvino
    validate_acl_archive
    install_openvino
    build_java_api_jar
    package_prebuild

    ccache --show-stats 2>&1 | tee "$ARTIFACTS_DIR/ccache-stats.log" || true
    emit_github_outputs
}

main "$@"
