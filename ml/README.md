# Machine Learning Workspace

This folder contains local training and export artifacts. These files are not loaded directly by the Android application and are intentionally excluded from Git because of their size.

- `source-models/eggplant-yolo26m/` contains the original PyTorch checkpoints.
- `exports/eggplant-yolo26m/ncnn-fp16/` contains the complete local NCNN export and smoke-test helper.
- `evaluation/eggplant-yolo26m/` contains training metrics, plots, and the confusion matrix.
- `dataset/eggplant-diseases/` contains the dataset configuration. The labeled image and label folders are not currently available in this repository.

The Android application loads only the deployment copy in `app/src/main/assets/models/eggplant-yolo26m/`.
