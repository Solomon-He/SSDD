"""Read-only ONNX inspection. Install diagnostic dependencies under target, not globally."""
import argparse
import collections
import hashlib
import json
from pathlib import Path

import onnx
from onnx import TensorProto, numpy_helper
from PIL import Image


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def value_info(value):
    tensor = value.type.tensor_type
    return {
        "name": value.name,
        "dtype": TensorProto.DataType.Name(tensor.elem_type),
        "shape": [d.dim_param if d.dim_param else d.dim_value for d in tensor.shape.dim],
    }


def inspect(path):
    model = onnx.load(str(path), load_external_data=False)
    onnx.checker.check_model(model)
    graph = model.graph
    producers = {output: node for node in graph.node for output in node.output}
    constants = {item.name: item for item in graph.initializer}
    input_consumers = []
    for input_value in graph.input:
        for node in graph.node:
            if input_value.name in node.input:
                input_consumers.append({"input": input_value.name, "op": node.op_type,
                                        "node": node.name, "outputs": list(node.output)})
    output_paths = []
    for output in graph.output:
        cursor = output.name
        chain = []
        # Follow data input upstream through common output wrappers.
        for _ in range(8):
            node = producers.get(cursor)
            if node is None:
                break
            chain.append({"op": node.op_type, "node": node.name,
                          "inputs": list(node.input), "outputs": list(node.output)})
            if node.op_type not in {"Identity", "Cast", "Transpose", "Reshape", "Sigmoid", "Softmax"}:
                break
            cursor = node.input[0]
        output_paths.append({"output": output.name, "producer_chain": chain})
    input_transforms = []
    cursor = graph.input[0].name
    for _ in range(6):
        consumers = [n for n in graph.node if cursor in n.input]
        if len(consumers) != 1 or consumers[0].op_type not in {"Div", "Sub", "Mul", "Add", "Cast"}:
            break
        node = consumers[0]
        arguments = {}
        for name in node.input:
            if name == cursor:
                continue
            if name in constants:
                arguments[name] = numpy_helper.to_array(constants[name]).tolist()
            elif name in producers and producers[name].op_type == "Constant":
                value = next(a.t for a in producers[name].attribute if a.name == "value")
                arguments[name] = numpy_helper.to_array(value).tolist()
        input_transforms.append({"op": node.op_type, "arguments": arguments})
        cursor = node.output[0]
    small_constants = {}
    for name, tensor in constants.items():
        if tensor.data_location == TensorProto.EXTERNAL:
            continue
        if 0 < __import__("math").prod(tensor.dims) <= 12:
            if any(token in name.lower() for token in ["mean", "std", "scale", "norm"]):
                small_constants[name] = numpy_helper.to_array(tensor).tolist()
    return {
        "file": path.name,
        "bytes": path.stat().st_size,
        "sha256": sha256(path),
        "checker": "passed",
        "ir_version": model.ir_version,
        "opsets": [{"domain": o.domain, "version": o.version} for o in model.opset_import],
        "producer": {"name": model.producer_name, "version": model.producer_version},
        "doc_string": model.doc_string,
        "metadata": {p.key: p.value for p in model.metadata_props},
        "inputs": [value_info(v) for v in graph.input],
        "outputs": [value_info(v) for v in graph.output],
        "node_count": len(graph.node),
        "ops": dict(sorted(collections.Counter(n.op_type for n in graph.node).items())),
        "input_consumers": input_consumers,
        "embedded_input_transforms": input_transforms,
        "output_paths": output_paths,
        "named_preprocessing_constants": small_constants,
        "external_tensor_count": sum(t.data_location == TensorProto.EXTERNAL for t in graph.initializer),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    models = [inspect(p) for p in sorted((args.root / "models").glob("*.onnx"))]
    images = []
    for path in sorted((args.root / "test-images").glob("*.jpg")):
        with Image.open(path) as im:
            im.load()
            images.append({"file": path.name, "width": im.width, "height": im.height,
                           "mode": im.mode, "format": im.format, "exif_orientation": im.getexif().get(274),
                           "sha256": sha256(path)})
    report = {"onnx_parser_version": onnx.__version__, "models": models, "images": images}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for item in models:
        print(item["file"], item["inputs"], "->", item["outputs"])
        print("Output producers:", item["output_paths"])
        print("Metadata:", item["metadata"])
    print("Images:", len(images), sorted({(i["width"], i["height"]) for i in images}))


if __name__ == "__main__":
    main()
