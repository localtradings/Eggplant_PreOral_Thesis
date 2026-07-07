# Machine Learning Workspace

This folder retains the approved model's dataset pointer and evaluation evidence. It does not contain runtime model binaries or PyTorch checkpoints.

- `evaluation/eggplant-yolo26m/` contains the v3/768 training metrics, plots, and confusion matrix.
- `dataset/eggplant-diseases/` contains the v3 dataset configuration. The labeled image and label folders are not included.

The Android application loads only the checksum-pinned NCNN pair in `app/src/main/assets/models/eggplant-yolo26m/`. Use `tools/model-export/export_ncnn.py` to validate and install an approved external NCNN export; the tool does not require PyTorch or Ultralytics. The runtime uses the v3 clean 768 model for tap-to-capture inference, with hold-to-preview live assistance only.
