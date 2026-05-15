#!/usr/bin/env python3
"""Export a Hugging Face causal LLM to an OpenVINO GenAI-ready bundle."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.parse
import urllib.request
import venv
from pathlib import Path


DEFAULT_MODEL_ID = "OpenVINO/Qwen3-0.6B-int4-ov"
REQUIRED_EXPORT_FILES = (
    "openvino_model.xml",
    "openvino_model.bin",
)
OPENVINO_BUNDLE_PATTERNS = (
    "*.json",
    "*.txt",
    "*.xml",
    "*.bin",
    "merges.txt",
    "vocab.json",
    "tokenizer.model",
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


def export_is_complete(output: Path, model_id: str, weight_format: str) -> bool:
    marker = output / ".openvino_llm_export_complete"
    if not marker.exists() or not all((output / name).exists() for name in REQUIRED_EXPORT_FILES):
        return False

    marker_values = dict(
        line.split("=", 1)
        for line in marker.read_text(encoding="utf-8").splitlines()
        if "=" in line
    )
    return marker_values.get("model_id") == model_id and marker_values.get("weight_format") == weight_format


def repo_has_openvino_bundle(model_id: str) -> bool:
    try:
        url = f"https://huggingface.co/api/models/{urllib.parse.quote(model_id, safe='/')}?blobs=true"
        with urllib.request.urlopen(url, timeout=45) as response:
            model_info = json.load(response)
        files = {sibling.get("rfilename") for sibling in model_info.get("siblings", [])}
    except Exception:
        return False
    return all(name in files for name in REQUIRED_EXPORT_FILES)


def download_openvino_bundle(python: Path, model_id: str, output: Path) -> None:
    script = """
import shutil
import sys
from pathlib import Path

from huggingface_hub import snapshot_download

model_id = sys.argv[1]
output = Path(sys.argv[2])
patterns = sys.argv[3].split("\\n")

snapshot_dir = Path(snapshot_download(repo_id=model_id, allow_patterns=patterns))

if output.exists():
    shutil.rmtree(output)
output.mkdir(parents=True)

for source in snapshot_dir.rglob("*"):
    if not source.is_file():
        continue
    relative = source.relative_to(snapshot_dir)
    target = output / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
"""
    run(
        [
            str(python),
            "-c",
            script,
            model_id,
            str(output),
            "\n".join(OPENVINO_BUNDLE_PATTERNS),
        ],
    )


def ensure_openvino_tokenizer(venv_dir: Path | None, output: Path) -> None:
    if (output / "openvino_tokenizer.xml").is_file():
        return

    if not (output / "tokenizer.json").is_file():
        return

    if venv_dir is None:
        convert_tokenizer = Path("convert_tokenizer")
    else:
        convert_tokenizer = executable_in_venv(venv_dir, "convert_tokenizer")

    run(
        [
            str(convert_tokenizer),
            str(output),
            "--output",
            str(output),
            "--with-detokenizer",
            "--tokenizer-output-type",
            "i64",
            "--detokenizer-input-type",
            "i64",
        ],
    )


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
    if export_is_complete(args.output, args.model_id, args.weight_format) and not args.force:
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

    downloaded_openvino_bundle = repo_has_openvino_bundle(args.model_id)
    if downloaded_openvino_bundle:
        download_openvino_bundle(python, args.model_id, args.output)
        ensure_openvino_tokenizer(args.venv, args.output)
    else:
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
    action = "Downloaded" if downloaded_openvino_bundle else "Exported"
    print(f"{action} {args.model_id} to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
