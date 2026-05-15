#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


SCRIPT_ROOT = Path(__file__).resolve().parents[3]


def getenv(name: str, default: str) -> str:
    return os.environ.get(name, default)


def require_command(name: str) -> None:
    if shutil.which(name) is None:
        raise SystemExit(f"Required command is missing: {name}")


def run(args: list[str], *, log: Path | None = None, cwd: Path | None = None) -> None:
    print("+ " + " ".join(args), flush=True)
    if log is None:
        subprocess.run(args, cwd=cwd, check=True)
        return

    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open("w", encoding="utf-8") as log_file:
        process = subprocess.Popen(
            args,
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        assert process.stdout is not None
        for line in process.stdout:
            sys.stdout.write(line)
            log_file.write(line)
        return_code = process.wait()

    if return_code != 0:
        raise subprocess.CalledProcessError(return_code, args)


def command_output(args: list[str]) -> str:
    return subprocess.check_output(args, text=True).strip()


def write_env_file(path: str | None, entries: dict[str, str]) -> None:
    if not path:
        return

    with Path(path).open("a", encoding="utf-8") as env_file:
        for key, value in entries.items():
            env_file.write(f"{key}={value}\n")


def append_path_file(path: str | None, value: Path) -> None:
    if not path:
        return

    with Path(path).open("a", encoding="utf-8") as path_file:
        path_file.write(f"{value}\n")


def zip_directory(source_dir: Path, zip_path: Path) -> None:
    zip_path.parent.mkdir(parents=True, exist_ok=True)
    if zip_path.exists():
        zip_path.unlink()

    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(source_dir.rglob("*")):
            archive.write(path, path.relative_to(source_dir.parent))


@dataclass(frozen=True)
class BuildConfig:
    openvino_ref: str
    openvino_genai_ref: str
    openvino_contrib_ref: str
    onetbb_ref: str
    openvino_repo: str
    openvino_contrib_repo: str
    openvino_genai_repo: str
    onetbb_repo: str
    android_abi: str
    android_platform: str
    android_ndk_version: str
    android_sdk_root: Path
    android_ndk: Path
    run_root: Path
    ccache_dir: Path

    @classmethod
    def from_env(cls) -> BuildConfig:
        runner_temp = getenv("RUNNER_TEMP", "")
        default_root = Path(runner_temp) / "openvino-android-prebuilds" if runner_temp else SCRIPT_ROOT / ".tmp" / "openvino-android-prebuilds"
        android_sdk = getenv("ANDROID_SDK_ROOT", getenv("ANDROID_HOME", ""))
        if not android_sdk:
            raise SystemExit("ANDROID_SDK_ROOT or ANDROID_HOME must be set.")

        android_ndk_version = getenv("ANDROID_NDK_VERSION", "29.0.14206865")
        versioned_ndk = Path(android_sdk) / "ndk" / android_ndk_version
        android_ndk = versioned_ndk if versioned_ndk.is_dir() else Path(getenv("ANDROID_NDK", str(versioned_ndk)))
        if not android_ndk.is_dir():
            raise SystemExit(f"Android NDK not found: {android_ndk}")

        run_root = Path(getenv("RUN_ROOT", str(default_root)))
        if str(run_root) in {"", "/"}:
            raise SystemExit(f"RUN_ROOT must point to a disposable build directory, got: '{run_root}'")

        ccache_dir = Path(getenv("CCACHE_DIR", str((Path(runner_temp) if runner_temp else run_root) / "ccache")))

        return cls(
            openvino_ref=getenv("OPENVINO_REF", "android-mbind-compat"),
            openvino_genai_ref=getenv("OPENVINO_GENAI_REF", "master"),
            openvino_contrib_ref=getenv("OPENVINO_CONTRIB_REF", "master"),
            onetbb_ref=getenv("ONETBB_REF", "v2023.0.0"),
            openvino_repo=getenv("OPENVINO_REPO", "https://github.com/embedded-dev-research/openvino.git"),
            openvino_contrib_repo=getenv("OPENVINO_CONTRIB_REPO", "https://github.com/openvinotoolkit/openvino_contrib.git"),
            openvino_genai_repo=getenv("OPENVINO_GENAI_REPO", "https://github.com/openvinotoolkit/openvino.genai.git"),
            onetbb_repo=getenv("ONETBB_REPO", "https://github.com/uxlfoundation/oneTBB.git"),
            android_abi=getenv("ANDROID_ABI", "arm64-v8a"),
            android_platform=getenv("ANDROID_PLATFORM", "35"),
            android_ndk_version=android_ndk_version,
            android_sdk_root=Path(android_sdk),
            android_ndk=android_ndk,
            run_root=run_root,
            ccache_dir=ccache_dir,
        )

    @property
    def src_dir(self) -> Path:
        return self.run_root / "src"

    @property
    def build_dir(self) -> Path:
        return self.run_root / "build"

    @property
    def install_dir(self) -> Path:
        return self.run_root / "install"

    @property
    def artifacts_dir(self) -> Path:
        return self.run_root / "artifacts"

    @property
    def package_name(self) -> str:
        return f"openvino-android-{self.android_abi}-{self.openvino_ref}"

    @property
    def package_root(self) -> Path:
        return self.artifacts_dir / "package" / self.package_name

    @property
    def zip_path(self) -> Path:
        return self.artifacts_dir / f"{self.package_name}.zip"

    @property
    def llvm_prebuilt_dir(self) -> Path:
        prebuilt_root = self.android_ndk / "toolchains" / "llvm" / "prebuilt"
        host_glob = "darwin-*" if sys.platform == "darwin" else "linux-*"
        matches = sorted(prebuilt_root.glob(host_glob))
        if not matches:
            raise SystemExit(f"Could not locate Android NDK LLVM prebuilt tools under {self.android_ndk}")
        return matches[0]

    def export_runtime_environment(self) -> None:
        os.environ["ANDROID_NDK"] = str(self.android_ndk)
        os.environ["CCACHE_DIR"] = str(self.ccache_dir)
        os.environ["PATH"] = f"{self.llvm_prebuilt_dir / 'bin'}{os.pathsep}{os.environ['PATH']}"


def cmake_android_args(config: BuildConfig) -> list[str]:
    return [
        f"-DCMAKE_TOOLCHAIN_FILE={config.android_ndk / 'build' / 'cmake' / 'android.toolchain.cmake'}",
        f"-DANDROID_ABI={config.android_abi}",
        f"-DANDROID_PLATFORM={config.android_platform}",
        "-DANDROID_STL=c++_shared",
    ]


def prepare(config: BuildConfig) -> None:
    config.export_runtime_environment()
    if config.run_root.exists():
        shutil.rmtree(config.run_root)
    for path in [config.src_dir, config.build_dir, config.install_dir, config.artifacts_dir, config.ccache_dir]:
        path.mkdir(parents=True, exist_ok=True)

    write_env_file(
        os.environ.get("GITHUB_ENV"),
        {
            "RUN_ROOT": str(config.run_root),
            "SRC_DIR": str(config.src_dir),
            "BUILD_DIR": str(config.build_dir),
            "INSTALL_DIR": str(config.install_dir),
            "ARTIFACTS_DIR": str(config.artifacts_dir),
            "PACKAGE_NAME": config.package_name,
            "PACKAGE_ROOT": str(config.package_root),
            "ZIP_PATH": str(config.zip_path),
            "ANDROID_NDK": str(config.android_ndk),
            "LLVM_PREBUILT_DIR": str(config.llvm_prebuilt_dir),
            "CCACHE_DIR": str(config.ccache_dir),
        },
    )
    append_path_file(os.environ.get("GITHUB_PATH"), config.llvm_prebuilt_dir / "bin")

    print(f"RUN_ROOT={config.run_root}")
    print(f"ANDROID_NDK={config.android_ndk}")
    print(f"LLVM_PREBUILT_DIR={config.llvm_prebuilt_dir}")


def clone_ref(repo_url: str, ref: str, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    run(
        [
            "git",
            "clone",
            "--recursive",
            "--depth",
            "1",
            "--shallow-submodules",
            "--jobs",
            "4",
            "--branch",
            ref,
            repo_url,
            str(destination),
        ]
    )


def checkout_sources(config: BuildConfig) -> None:
    require_command("git")
    config.src_dir.mkdir(parents=True, exist_ok=True)
    clone_ref(config.openvino_repo, config.openvino_ref, config.src_dir / "openvino")
    clone_ref(config.openvino_contrib_repo, config.openvino_contrib_ref, config.src_dir / "openvino_contrib")
    clone_ref(config.openvino_genai_repo, config.openvino_genai_ref, config.src_dir / "openvino.genai")
    clone_ref(config.onetbb_repo, config.onetbb_ref, config.src_dir / "oneTBB")


def record_source_manifest(config: BuildConfig) -> None:
    require_command("git")
    config.artifacts_dir.mkdir(parents=True, exist_ok=True)

    lines = [
        "OpenVINO Android prebuild source manifest",
        f"Generated at: {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}",
        "",
        f"openvino_ref={config.openvino_ref}",
        f"openvino_commit={command_output(['git', '-C', str(config.src_dir / 'openvino'), 'rev-parse', 'HEAD'])}",
        f"openvino_genai_ref={config.openvino_genai_ref}",
        f"openvino_genai_commit={command_output(['git', '-C', str(config.src_dir / 'openvino.genai'), 'rev-parse', 'HEAD'])}",
        f"openvino_contrib_ref={config.openvino_contrib_ref}",
        f"openvino_contrib_commit={command_output(['git', '-C', str(config.src_dir / 'openvino_contrib'), 'rev-parse', 'HEAD'])}",
        f"onetbb_ref={config.onetbb_ref}",
        f"onetbb_commit={command_output(['git', '-C', str(config.src_dir / 'oneTBB'), 'rev-parse', 'HEAD'])}",
        "",
        f"android_abi={config.android_abi}",
        f"android_platform={config.android_platform}",
        f"android_ndk_version={config.android_ndk_version}",
    ]
    (config.artifacts_dir / "source-manifest.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    if shutil.which("ccache"):
        run(["ccache", "--zero-stats"])


def build_onetbb(config: BuildConfig) -> None:
    config.export_runtime_environment()
    require_command("cmake")
    require_command("ccache")
    run(
        [
            "cmake",
            "-S",
            str(config.src_dir / "oneTBB"),
            "-B",
            str(config.build_dir / "oneTBB"),
            "-G",
            "Ninja",
            "-DCMAKE_BUILD_TYPE=Release",
            f"-DCMAKE_INSTALL_PREFIX={config.install_dir / 'oneTBB'}",
            *cmake_android_args(config),
            "-DTBB_TEST=OFF",
            "-DTBB_EXAMPLES=OFF",
            "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--undefined-version",
            "-DCMAKE_C_COMPILER_LAUNCHER=ccache",
            "-DCMAKE_CXX_COMPILER_LAUNCHER=ccache",
        ],
        log=config.artifacts_dir / "onetbb-configure.log",
    )
    run(["cmake", "--build", str(config.build_dir / "oneTBB")], log=config.artifacts_dir / "onetbb-build.log")
    run(["cmake", "--install", str(config.build_dir / "oneTBB")], log=config.artifacts_dir / "onetbb-install.log")


def configure_openvino(config: BuildConfig) -> None:
    config.export_runtime_environment()
    require_command("cmake")
    require_command("ccache")
    run(
        [
            "cmake",
            "-S",
            str(config.src_dir / "openvino"),
            "-B",
            str(config.build_dir / "openvino-android"),
            "-G",
            "Ninja",
            "-DCMAKE_BUILD_TYPE=Release",
            f"-DCMAKE_INSTALL_PREFIX={config.install_dir / 'openvino-android'}",
            *cmake_android_args(config),
            f"-DOPENVINO_EXTRA_MODULES={config.src_dir / 'openvino_contrib' / 'modules' / 'java_api'};{config.src_dir / 'openvino.genai'}",
            "-DTHREADING=TBB",
            f"-DTBB_DIR={config.install_dir / 'oneTBB' / 'lib' / 'cmake' / 'TBB'}",
            "-DENABLE_INTEL_GPU=OFF",
            "-DENABLE_INTEL_NPU=OFF",
            "-DENABLE_TEMPLATE=OFF",
            "-DENABLE_TESTS=OFF",
            "-DENABLE_FUNCTIONAL_TESTS=OFF",
            "-DENABLE_SAMPLES=OFF",
            "-DENABLE_TOOLS=OFF",
            "-DENABLE_PYTHON=OFF",
            "-DENABLE_OV_ONNX_FRONTEND=OFF",
            "-DENABLE_OV_PADDLE_FRONTEND=OFF",
            "-DENABLE_OV_PYTORCH_FRONTEND=OFF",
            "-DENABLE_OV_TF_FRONTEND=OFF",
            "-DENABLE_OV_TF_LITE_FRONTEND=OFF",
            "-DENABLE_OV_JAX_FRONTEND=OFF",
            "-DENABLE_OV_IR_FRONTEND=ON",
            "-DENABLE_PLUGINS_XML=ON",
            "-DENABLE_SNIPPETS_LIBXSMM_TPP=OFF",
            "-DENABLE_GGUF=ON",
            "-DENABLE_CLANG_FORMAT=OFF",
            "-DENABLE_CLANG_TIDY=OFF",
            "-DCMAKE_C_COMPILER_LAUNCHER=ccache",
            "-DCMAKE_CXX_COMPILER_LAUNCHER=ccache",
        ],
        log=config.artifacts_dir / "openvino-configure.log",
    )


def build_openvino_runtime(config: BuildConfig) -> None:
    config.export_runtime_environment()
    require_command("cmake")
    run(
        [
            "cmake",
            "--build",
            str(config.build_dir / "openvino-android"),
            "--target",
            "openvino",
            "openvino_c",
            "ov_frontends",
            "ov_plugins",
        ],
        log=config.artifacts_dir / "openvino-runtime-build.log",
    )
    validate_acl_archive(config)


def build_openvino_genai(config: BuildConfig) -> None:
    config.export_runtime_environment()
    require_command("cmake")
    run(
        [
            "cmake",
            "--build",
            str(config.build_dir / "openvino-android"),
            "--target",
            "openvino_tokenizers",
            "openvino_genai",
            "openvino_genai_c",
        ],
        log=config.artifacts_dir / "openvino-genai-build.log",
    )


def build_openvino_java_api(config: BuildConfig) -> None:
    config.export_runtime_environment()
    require_command("cmake")
    require_command("javac")
    require_command("jar")
    run(
        [
            "cmake",
            "--build",
            str(config.build_dir / "openvino-android"),
            "--target",
            "inference_engine_java_api",
        ],
        log=config.artifacts_dir / "openvino-java-jni-build.log",
    )
    build_java_api_jar(config)


def validate_acl_archive(config: BuildConfig) -> None:
    acl_archive = config.build_dir / "openvino-android" / "src" / "plugins" / "intel_cpu" / "thirdparty" / "acl_build" / "build" / "arm64-v8.2-a" / "libarm_compute-static.a"
    if not acl_archive.is_file() or acl_archive.stat().st_size == 0:
        raise SystemExit(f"ACL archive is missing or empty: {acl_archive}")

    object_count = len(command_output([str(config.llvm_prebuilt_dir / "bin" / "llvm-ar"), "t", str(acl_archive)]).splitlines())
    if object_count <= 0:
        raise SystemExit(f"ACL archive contains no objects: {acl_archive}")

    message = f"ACL archive object count: {object_count}"
    print(message)
    (config.artifacts_dir / "acl-archive-check.log").write_text(message + "\n", encoding="utf-8")


def build_java_api_jar(config: BuildConfig) -> None:
    java_out = config.artifacts_dir / "java-api"
    jar_path = java_out / f"openvino-java-api-{config.openvino_ref}-android.jar"
    if java_out.exists():
        shutil.rmtree(java_out)
    (java_out / "classes").mkdir(parents=True)

    sources = sorted((config.src_dir / "openvino_contrib" / "modules" / "java_api" / "src" / "main" / "java").rglob("*.java"))
    source_list = java_out / "java-sources.txt"
    source_list.write_text("\n".join(str(source) for source in sources) + "\n", encoding="utf-8")
    run(["javac", "--release", "11", "-d", str(java_out / "classes"), f"@{source_list}"])
    run(["jar", "--create", "--file", str(jar_path), "-C", str(java_out / "classes"), "."])


def install_openvino(config: BuildConfig) -> None:
    config.export_runtime_environment()
    require_command("cmake")
    run(["cmake", "--install", str(config.build_dir / "openvino-android")], log=config.artifacts_dir / "openvino-install.log")


def package_prebuild(config: BuildConfig) -> None:
    config.export_runtime_environment()
    runtime_dir = config.install_dir / "openvino-android" / "runtime"
    ndk_libcxx = config.llvm_prebuilt_dir / "sysroot" / "usr" / "lib" / "aarch64-linux-android" / "libc++_shared.so"

    package_parent = config.artifacts_dir / "package"
    if package_parent.exists():
        shutil.rmtree(package_parent)

    java_dir = config.package_root / "java"
    jni_dir = config.package_root / "android-jni" / config.android_abi
    metadata_dir = config.package_root / "metadata"
    for path in [java_dir, jni_dir, metadata_dir]:
        path.mkdir(parents=True, exist_ok=True)

    shutil.copytree(runtime_dir, config.package_root / "runtime")
    shutil.copy2(config.artifacts_dir / "java-api" / f"openvino-java-api-{config.openvino_ref}-android.jar", java_dir)
    shutil.copy2(ndk_libcxx, jni_dir)
    for library in sorted((runtime_dir / "lib" / "aarch64").glob("*.so")):
        shutil.copy2(library, jni_dir)
    for library in sorted(runtime_dir.glob("lib/*/libopenvino_tokenizers.so")):
        shutil.copy2(library, jni_dir)
    for plugins_xml in sorted((runtime_dir / "lib").glob("openvino-*/plugins.xml")):
        plugins_dir = jni_dir / plugins_xml.parent.name
        plugins_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(plugins_xml, plugins_dir)
    for library in sorted((runtime_dir / "3rdparty" / "tbb" / "lib").glob("*.so")):
        shutil.copy2(library, jni_dir)
    shutil.copy2(config.artifacts_dir / "source-manifest.txt", metadata_dir / "source-manifest.txt")

    (config.package_root / "README.md").write_text(
        f"""# OpenVINO Android {config.android_abi} prebuild

Contents:
- `runtime/`: installed OpenVINO Runtime, OpenVINO GenAI, OpenVINO Tokenizers, OpenVINO Java JNI bridge, CMake config files, headers, and TBB runtime.
- `java/`: Java API classes jar for `org.intel.openvino.*`.
- `android-jni/{config.android_abi}/`: shared libraries ready to copy into an Android app `src/main/jniLibs/{config.android_abi}` directory, including `libc++_shared.so` from the Android NDK.
- `metadata/source-manifest.txt`: exact source refs and commits used for this build.

This package is built from fresh source checkouts for Android {config.android_abi}, Android platform {config.android_platform}, and Android NDK {config.android_ndk_version}.
""",
        encoding="utf-8",
    )

    zip_directory(config.package_root, config.zip_path)
    print(f"{config.zip_path} {config.zip_path.stat().st_size} bytes")
    write_env_file(
        os.environ.get("GITHUB_OUTPUT"),
        {
            "artifact_path": str(config.zip_path),
            "artifact_name": config.zip_path.name,
            "package_name": config.package_name,
        },
    )


def ccache_stats(config: BuildConfig) -> None:
    if shutil.which("ccache") is None:
        return

    config.artifacts_dir.mkdir(parents=True, exist_ok=True)
    run(["ccache", "--show-stats"], log=config.artifacts_dir / "ccache-stats.log")


STAGES = {
    "prepare": prepare,
    "checkout-sources": checkout_sources,
    "record-source-manifest": record_source_manifest,
    "build-onetbb": build_onetbb,
    "configure-openvino": configure_openvino,
    "build-openvino-runtime": build_openvino_runtime,
    "build-openvino-genai": build_openvino_genai,
    "build-openvino-java-api": build_openvino_java_api,
    "install-openvino": install_openvino,
    "package-prebuild": package_prebuild,
    "ccache-stats": ccache_stats,
}


def run_all(config: BuildConfig) -> None:
    for stage in STAGES:
        if stage == "ccache-stats":
            continue
        STAGES[stage](config)
    ccache_stats(config)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("stage", nargs="?", default="all", choices=["all", *STAGES.keys()])
    args = parser.parse_args()

    config = BuildConfig.from_env()
    if args.stage == "all":
        run_all(config)
        return

    STAGES[args.stage](config)


if __name__ == "__main__":
    main()
