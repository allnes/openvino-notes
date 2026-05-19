#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import venv
import zipfile
from pathlib import Path
from typing import Any


def run(command: list[str], cwd: Path | None = None) -> None:
    subprocess.run(command, cwd=cwd, check=True)


def venv_python(venv_dir: Path) -> Path:
    if os.name == "nt":
        return venv_dir / "Scripts" / "python.exe"
    return venv_dir / "bin" / "python"


def ensure_venv(venv_dir: Path) -> Path:
    venv_dir = venv_dir.resolve()
    python = venv_python(venv_dir)
    if not python.exists():
        venv.EnvBuilder(with_pip=True).create(venv_dir)
        run([str(python), "-m", "pip", "install", "--upgrade", "pip"])
    return python


def ensure_python_packages(python: Path) -> None:
    check = subprocess.run(
        [str(python), "-c", "import openvino, ultralytics"],
        check=False,
    )
    if check.returncode != 0:
        run([str(python), "-m", "pip", "install", "-U", "openvino", "ultralytics"])


def export_model(
    python: Path,
    model_name: str,
    work_dir: Path,
    image_size: int,
) -> Path:
    export_code = f"""
from pathlib import Path
from ultralytics import YOLO

model = YOLO({model_name!r})
attempts = [
    dict(nms=True),
    dict(end2end=True),
]
last_error = None
for extra in attempts:
    try:
        model.export(format="openvino", imgsz={image_size}, batch=1, dynamic=False, **extra)
        break
    except Exception as exc:
        last_error = exc
else:
    raise last_error

expected = Path({model_name!r}).stem + "_openvino_model"
print(Path.cwd() / expected)
"""
    result = subprocess.run(
        [str(python), "-c", export_code],
        cwd=work_dir,
        check=True,
        capture_output=True,
        text=True,
    )
    model_dir = Path(result.stdout.strip().splitlines()[-1])
    if not model_dir.is_dir():
        raise FileNotFoundError(f"Ultralytics did not create OpenVINO model directory: {model_dir}")
    return model_dir


def write_coco_names(
    python: Path,
    model_name: str,
    work_dir: Path,
    output_file: Path,
) -> None:
    names_code = f"""
from pathlib import Path
from ultralytics import YOLO

names = YOLO({model_name!r}).names
Path({str(output_file)!r}).write_text(
    "\\n".join(names[i] for i in range(len(names))) + "\\n",
    encoding="utf-8",
)
"""
    run([str(python), "-c", names_code], cwd=work_dir)


def verify_openvino_model(
    python: Path,
    model_xml: Path,
    image_size: int,
) -> None:
    verify_code = f"""
from openvino import Core

core = Core()
model = core.read_model({str(model_xml)!r})
compiled = core.compile_model(model, "CPU")
outputs = list(compiled.outputs)
if not outputs:
    raise RuntimeError("Model has no outputs")
shape = [int(dim) for dim in outputs[0].shape]
if len(shape) < 2 or shape[-1] != 6:
    raise RuntimeError(f"Expected end-to-end YOLO output with last dimension 6, got {{shape}}")
inputs = list(compiled.inputs)
if not inputs:
    raise RuntimeError("Model has no inputs")
input_shape = [int(dim) for dim in inputs[0].shape]
if input_shape[-1] != {image_size} or input_shape[-2] != {image_size}:
    raise RuntimeError(f"Expected {image_size}x{image_size} input, got {{input_shape}}")
print("Verified OpenVINO YOLO model", input_shape, shape)
"""
    run([str(python), "-c", verify_code])


def copy_model_files(
    model_dir: Path,
    output_dir: Path,
    model_stem: str,
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    required = [f"{model_stem}.xml", f"{model_stem}.bin", "metadata.yaml"]
    missing = [name for name in required if not (model_dir / name).is_file()]
    if missing:
        raise FileNotFoundError(f"OpenVINO model export is missing required files: {missing}")

    for name in required:
        shutil.copy2(model_dir / name, output_dir / name)


def sha256(file: Path) -> str:
    digest = hashlib.sha256()
    with file.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_manifest(
    output_dir: Path,
    model_name: str,
    image_size: int,
) -> None:
    files = sorted(path for path in output_dir.iterdir() if path.is_file())
    manifest: dict[str, Any] = {
        "format": "openvino-yolo-image-tagger",
        "model": model_name,
        "image_size": image_size,
        "files": {path.name: {"sha256": sha256(path), "size": path.stat().st_size} for path in files},
    }
    (output_dir / "openvino_vision_manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def write_zip(
    output_dir: Path,
    zip_output: Path,
) -> None:
    zip_output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(output_dir.iterdir()):
            if file.is_file():
                archive.write(file, file.name)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="yolo26n.pt")
    parser.add_argument("--image-size", type=int, default=640)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--work-dir", required=True, type=Path)
    parser.add_argument("--zip-output", type=Path)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    args.output_dir = args.output_dir.resolve()
    args.work_dir = args.work_dir.resolve()
    if args.zip_output:
        args.zip_output = args.zip_output.resolve()
    args.work_dir.mkdir(parents=True, exist_ok=True)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    python = ensure_venv(args.work_dir / ".venv")
    ensure_python_packages(python)

    model_dir = export_model(python, args.model, args.work_dir, args.image_size)
    model_stem = Path(args.model).stem
    copy_model_files(model_dir, args.output_dir, model_stem)
    write_coco_names(python, args.model, args.work_dir, args.output_dir / "coco.names")
    verify_openvino_model(python, args.output_dir / f"{model_stem}.xml", args.image_size)
    write_manifest(args.output_dir, args.model, args.image_size)
    if args.zip_output:
        write_zip(args.output_dir, args.zip_output)
        print(f"Wrote {args.zip_output}")


if __name__ == "__main__":
    main()
