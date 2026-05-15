from __future__ import annotations

import os
import shutil
import zipfile
from pathlib import Path

from .common import BuildConfig, run, write_env_file


def zip_directory(source_dir: Path, zip_path: Path) -> None:
    zip_path.parent.mkdir(parents=True, exist_ok=True)
    if zip_path.exists():
        zip_path.unlink()

    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(source_dir.rglob("*")):
            archive.write(path, path.relative_to(source_dir.parent))


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
