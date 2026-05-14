#!/usr/bin/env python3
"""Export a Hugging Face causal LLM to an OpenVINO GenAI-ready bundle."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import venv
from pathlib import Path


DEFAULT_MODEL_ID = "Qwen/Qwen2.5-0.5B-Instruct"
REQUIRED_EXPORT_FILES = (
    "openvino_model.xml",
    "openvino_model.bin",
)


def run(command: list[str]) -> None:
    subprocess.check_call(command)


def executable_in_venv(venv_dir: Path, executable: str) -> Path:
    bin_dir = "Scripts" if os.name == "nt" else "bin"
    suffix = ".exe" if os.name == "nt" else ""
    return venv_dir / bin_dir / f"{executable}{suffix}"


def ensure_venv(venv_dir: Path) -> tuple[Path, Path]:
    python = executable_in_venv(venv_dir, "python")
    optimum_cli = executable_in_venv(venv_dir, "optimum-cli")
    if not python.exists():
        venv.EnvBuilder(with_pip=True).create(venv_dir)
    return python, optimum_cli


def export_is_complete(output: Path) -> bool:
    marker = output / ".openvino_llm_export_complete"
    return marker.exists() and all((output / name).exists() for name in REQUIRED_EXPORT_FILES)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-id", default=DEFAULT_MODEL_ID)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--weight-format", default="int4", choices=("int4", "int8", "fp16"))
    parser.add_argument("--venv", type=Path)
    parser.add_argument("--force", action="store_true")
    parser.add_argument(
        "--install-deps",
        action="store_true",
        help="Install/upgrade host-side OpenVINO export dependencies before running optimum-cli.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if export_is_complete(args.output) and not args.force:
        print(f"Reusing existing OpenVINO model bundle at {args.output}")
        return 0

    python = Path(sys.executable)
    optimum_cli = Path("optimum-cli")
    if args.venv is not None:
        python, optimum_cli = ensure_venv(args.venv)

    if args.install_deps:
        run(
            [
                str(python),
                "-m",
                "pip",
                "install",
                "--upgrade",
                "optimum-intel[openvino]",
                "openvino-genai",
                "openvino-tokenizers",
                "huggingface_hub",
            ],
        )

    run(
        [
            str(optimum_cli),
            "export",
            "openvino",
            "--model",
            args.model_id,
            "--weight-format",
            args.weight_format,
            str(args.output),
        ],
    )

    (args.output / ".openvino_llm_export_complete").write_text(
        f"model_id={args.model_id}\nweight_format={args.weight_format}\n",
        encoding="utf-8",
    )
    print(f"Exported {args.model_id} to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
