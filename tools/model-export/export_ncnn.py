#!/usr/bin/env python3
"""Install an approved NCNN model pair into the Android asset contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
from pathlib import Path


MODEL_VERSION = "eggplant-yolo26m-v3-clean-768-20260707"
RUNTIME_VERSION = "ncnn-20260526"
EXPORTER_VERSION = "8.4.90"
MODEL_LICENSE = "AGPL-3.0"
INPUT_SIZE = 768
CONFIDENCE_THRESHOLD = 0.12
LABELS = [
    "Fruit_Rot",
    "Fruit_borer",
    "Healthy Leaf",
    "Healthy Plant",
    "Insect-Pest",
    "Leaf-Spot",
    "Melon_Thrips",
    "Mosaic",
    "White-Mold",
    "Wilt",
]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_ncnn_pair(source_ncnn_dir: Path) -> tuple[Path, Path]:
    param = source_ncnn_dir / "model.ncnn.param"
    weights = source_ncnn_dir / "model.ncnn.bin"
    if not param.is_file() or not weights.is_file():
        raise SystemExit(
            "The source directory must contain model.ncnn.param and model.ncnn.bin."
        )
    param_text = param.read_text(encoding="utf-8")
    if "Input" not in param_text or "in0" not in param_text or "out0" not in param_text:
        raise SystemExit("The NCNN parameter graph does not expose the expected in0/out0 contract.")
    if weights.stat().st_size == 0:
        raise SystemExit("The NCNN weights file is empty.")
    return param, weights


def atomic_copy(source: Path, destination: Path) -> None:
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    try:
        shutil.copy2(source, temporary)
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)


def install_ncnn(source_ncnn_dir: Path, assets: Path) -> None:
    param, weights = validate_ncnn_pair(source_ncnn_dir)
    assets.mkdir(parents=True, exist_ok=True)
    atomic_copy(param, assets / param.name)
    atomic_copy(weights, assets / weights.name)

    metadata = {
        "modelVersion": MODEL_VERSION,
        "runtimeVersion": RUNTIME_VERSION,
        "exporterVersion": EXPORTER_VERSION,
        "license": MODEL_LICENSE,
        "licenseUrl": "https://www.ultralytics.com/license",
        "inputSize": INPUT_SIZE,
        "confidenceThreshold": CONFIDENCE_THRESHOLD,
        "paramSha256": sha256(param),
        "binSha256": sha256(weights),
        "labels": LABELS,
    }
    metadata_path = assets / "model-metadata.json"
    temporary_metadata = metadata_path.with_suffix(".json.tmp")
    try:
        temporary_metadata.write_text(
            json.dumps(metadata, indent=2) + "\n",
            encoding="utf-8",
        )
        os.replace(temporary_metadata, metadata_path)
    finally:
        temporary_metadata.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Install the approved 768px NCNN model into Android assets."
    )
    parser.add_argument("--source-ncnn-dir", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    args = parser.parse_args()
    install_ncnn(args.source_ncnn_dir, args.assets)


if __name__ == "__main__":
    main()
