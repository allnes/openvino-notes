#!/usr/bin/env python3
"""Export Gemma 3 270M IT to an OpenVINO GenAI-ready INT4 bundle."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


DEFAULT_MODEL_ID = "google/gemma-3-270m-it"


def run(command: list[str]) -> None:
    subprocess.check_call(command)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-id", default=DEFAULT_MODEL_ID)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--weight-format", default="int4", choices=("int4", "int8", "fp16"))
    parser.add_argument(
        "--install-deps",
        action="store_true",
        help="Install/upgrade host-side OpenVINO export dependencies before running optimum-cli.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)

    if args.install_deps:
        run(
            [
                sys.executable,
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
            "optimum-cli",
            "export",
            "openvino",
            "--model",
            args.model_id,
            "--weight-format",
            args.weight_format,
            str(args.output),
        ],
    )
    print(f"Exported {args.model_id} to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
