# Third-Party Notices

This file records the principal licenses directly relevant to the packaged detector and deployed administration service. Dependency-specific license metadata remains available in the corresponding package manifests and source distributions.

## Ultralytics YOLO model and exporter

The packaged model metadata at `app/src/main/assets/models/eggplant-yolo26m/model-metadata.json` identifies:

- model: `eggplant-yolo26m-v3-clean-768-20260707`
- exporter: Ultralytics `8.4.90`
- license: `AGPL-3.0`
- license information: <https://www.ultralytics.com/license>

Ultralytics' official documentation states that its YOLO code and models use AGPL-3.0 unless covered by an Enterprise License: <https://docs.ultralytics.com/help/contributing/#license>. The unmodified AGPL-3.0 license text is included at `third_party/licenses/AGPL-3.0.txt` for the packaged model/export.

No Enterprise License is asserted by this repository. If the model owner has separate license rights, those rights should be reviewed and documented by the owner before changing this notice.

## NCNN

The Android application packages NCNN runtime libraries identified by the model metadata as `ncnn-20260526`.

NCNN is Copyright (C) 2017 Tencent and distributed under the BSD 3-Clause License. NCNN's distribution also identifies bundled components under zlib, BSD 2-Clause, and BSD 3-Clause terms. The complete notice supplied with the included NCNN distribution is preserved at:

`app/src/main/cpp/third_party/ncnn/LICENSE.txt`

Official project: <https://github.com/Tencent/ncnn>

## Source availability

Corresponding project source and release materials are published at:

<https://github.com/localtradings/Eggplant_Finals_Thesis>

This notice documents third-party material and does not declare a repository-wide license for original application code. It is informational and is not legal advice. Project owners remain responsible for confirming rights to the training data, trained weights, logos, photos, and any separately licensed artifacts, and for determining whether AGPL-3.0 or an Enterprise License governs distribution of the complete combined application.
