from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("export_ncnn.py")


class InstallNcnnTest(unittest.TestCase):
    def test_installs_approved_ncnn_pair_and_generates_pinned_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source = root / "source"
            assets = root / "assets"
            source.mkdir()
            param = source / "model.ncnn.param"
            weights = source / "model.ncnn.bin"
            param.write_text(
                "7767517\n2 2\nInput in0 0 1 in0\nConcat output 1 1 in0 out0 0=0\n",
                encoding="utf-8",
            )
            weights.write_bytes(b"approved-ncnn-weights")

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--source-ncnn-dir",
                    str(source),
                    "--assets",
                    str(assets),
                ],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual("", result.stderr)
            self.assertEqual(0, result.returncode)
            self.assertEqual(param.read_bytes(), (assets / param.name).read_bytes())
            self.assertEqual(weights.read_bytes(), (assets / weights.name).read_bytes())

            metadata = json.loads((assets / "model-metadata.json").read_text(encoding="utf-8"))
            self.assertEqual("eggplant-yolo26m-v3-clean-768-20260707", metadata["modelVersion"])
            self.assertEqual("ncnn-20260526", metadata["runtimeVersion"])
            self.assertEqual(768, metadata["inputSize"])
            self.assertEqual(0.15, metadata["confidenceThreshold"])
            self.assertEqual(hashlib.sha256(param.read_bytes()).hexdigest(), metadata["paramSha256"])
            self.assertEqual(hashlib.sha256(weights.read_bytes()).hexdigest(), metadata["binSha256"])
            self.assertEqual("8.4.90", metadata["exporterVersion"])
            self.assertEqual("AGPL-3.0", metadata["license"])


if __name__ == "__main__":
    unittest.main()
