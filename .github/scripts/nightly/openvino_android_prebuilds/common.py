from __future__ import annotations

import os
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


SCRIPT_ROOT = Path(__file__).resolve().parents[4]


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
        default_root = (
            Path(runner_temp) / "openvino-android-prebuilds"
            if runner_temp
            else SCRIPT_ROOT / ".tmp" / "openvino-android-prebuilds"
        )
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
