#!/usr/bin/env python3
"""Export the approved eggplant checkpoint to the pinned Android asset contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path

from ultralytics import YOLO


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


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--weights", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--imgsz", type=int, default=640)
    args = parser.parse_args()

    model = YOLO(args.weights)
    if list(model.names.values()) != LABELS:
        raise SystemExit(f"Unexpected label order: {model.names}")

    exported = Path(
        model.export(format="ncnn", imgsz=args.imgsz, batch=1, half=True, device="cpu")
    )
    param = exported / "model.ncnn.param"
    weights = exported / "model.ncnn.bin"
    if not param.is_file() or not weights.is_file():
        raise SystemExit("NCNN export did not produce the expected param/bin pair.")

    args.assets.mkdir(parents=True, exist_ok=True)
    shutil.copy2(param, args.assets / "model.ncnn.param")
    shutil.copy2(weights, args.assets / "model.ncnn.bin")
    metadata = {
        "modelVersion": "eggplant-yolo26m-b96-20260704",
        "runtimeVersion": "ncnn-20260526",
        "inputSize": args.imgsz,
        "confidenceThreshold": 0.2,
        "paramSha256": sha256(param),
        "binSha256": sha256(weights),
        "labels": LABELS,
    }
    (args.assets / "model-metadata.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )


if __name__ == "__main__":
    main()
