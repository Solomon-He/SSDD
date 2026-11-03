"""Compare Java ONNX output and postprocessing with Python ORT and the supplied source."""
import argparse
import hashlib
import importlib.util
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import cv2
import numpy as np
import onnxruntime as ort

sys.dont_write_bytecode = True


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--training-root", type=Path, required=True)
    args = parser.parse_args()
    root = args.root.resolve()
    training = args.training_root.resolve()
    spec = importlib.util.spec_from_file_location("reference_postprocess", training / "src/postprocess.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    source_files = ["src/model.py", "src/dataset.py", "src/postprocess.py", "src/tta.py",
                    "src/augmentation.py", "configs/Unet_densenet201.yaml", "configs/Unet++_resnet34.yaml",
                    "docs/deploy_v2_node_onnxruntime.md", "steel-defect-v2.ipynb"]
    sources = [{"path": str(training / p), "sha256": digest(training / p)} for p in source_files]
    report = {"status": "passed", "generated_at": datetime.now(timezone.utc).isoformat(),
              "onnxruntime": ort.__version__, "numpy": np.__version__,
              "opencv": cv2.__version__, "scope": "Java/Python ONNX numerical alignment and original Python postprocess; not PyTorch checkpoint or accuracy validation",
              "source_files": sources, "models": []}
    image_path = sorted((root / "test-images").glob("*.jpg"))[0]
    image = cv2.cvtColor(cv2.imread(str(image_path)), cv2.COLOR_BGR2RGB)
    h, w = image.shape[:2]
    for model in sorted((root / "models").glob("*.onnx")):
        results = root / "target/p0-validation" / model.stem
        inputs = np.fromfile(results / "input.f32", dtype="<f4").reshape(1, 3, h, w)
        java = np.fromfile(results / "logits.f32", dtype="<f4").reshape(1, 4, h, w)
        options = ort.SessionOptions()
        options.intra_op_num_threads = 2
        options.inter_op_num_threads = 1
        options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        session = ort.InferenceSession(str(model), sess_options=options, providers=["CPUExecutionProvider"])
        python = session.run(["output"], {"input": inputs})[0]
        assert np.allclose(java, python, rtol=1e-4, atol=1e-4), model.name
        probs = 1.0 / (1.0 + np.exp(-java[0].astype(np.float64)))
        expected = module.apply_postprocess(probs, pixel_threshold=0.5,
                                           min_area=[600, 600, 900, 2000], min_component_size=150)
        actual = np.stack([np.fromfile(results / f"mask-{c + 1}.u8", dtype=np.uint8).reshape(h, w) for c in range(4)])
        assert np.array_equal(expected, actual), model.name
        tensor_image = inputs[0].transpose(1, 2, 0)
        delta = np.abs(tensor_image - image.astype(np.float32))
        # Check the decoder independently instead of assuming JPEG implementations match.
        assert np.array_equal(tensor_image, image), "ImageIO/OpenCV input differed on this reference image"
        item = {"file": model.name, "image": image_path.name,
                "max_abs_logit_error": float(np.max(np.abs(java - python))),
                "rtol": 1e-4, "atol": 1e-4, "logits_allclose": True,
                "source_postprocess_differing_pixels": int(np.count_nonzero(expected != actual)),
                "class_pixels": expected.sum(axis=(1, 2)).tolist(),
                "imageio_opencv_max_pixel_difference": float(delta.max()),
                "imageio_opencv_differing_values": int(np.count_nonzero(delta)),
                "mask_sha256": [hashlib.sha256(expected[c].tobytes()).hexdigest() for c in range(4)]}
        report["models"].append(item)
        print(model.name, item)
    # Synthetic fixture: exact threshold, diagonal 8-connectivity and overlap.
    logits = np.full((4, 3, 3), -100.0)
    logits[0, 0, 0] = 0
    logits[0, 1, 1] = 10
    logits[1, 1, 1] = 10
    masks = module.apply_postprocess(1 / (1 + np.exp(-logits)), 0.5, [0] * 4, 0)
    assert masks.sum(axis=(1, 2)).tolist() == [2, 1, 0, 0]
    report["synthetic_threshold_and_overlap_fixture"] = "passed"
    (root / "docs/p0/reference-check.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
