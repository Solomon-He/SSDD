# P0 V2 模型验证工具

这是独立的诊断 Maven 工程，不参与后端应用启动，不添加 Web 接口。验证数据位于后端根目录的 `models/`、`test-images/`；报告位于 `docs/p0/`，原始张量和试跑预览位于 `target/p0-validation/`。

## Java 17 CPU 验证

在 `SSDD-backend` 目录执行（替换为本机 Java 17 可执行文件）：

```powershell
.\mvnw.cmd -B -ntp -f .\tools\p0-model-validation\pom.xml "-Dp0.java=D:\JDK\jdk-17\bin\java.exe" test
```

`p0.java` 控制测试进程，Maven 本身可运行于较新 JDK，编译目标固定 17。工具会断言测试进程实际为 Java 17，避免把 `release=17` 当成已在 Java 17 运行。本次使用的便携 JRE 位于 `target/p0-tools/jre17/`，不属于系统安装；清理 target 后可改用自己的 Java 17。

依赖固定 `com.microsoft.onnxruntime:onnxruntime:1.31.1`；Jackson/JUnit 由 Spring Boot 3.5.16 parent 管理。默认运行两个现有模型、11 张 1600×256 图片、动态维度小样例、错误通道拒绝、重复推理及后处理边界测试。输出推理耗时不含 JPEG 解码和后处理。

## Python 结构与交叉验证

Python 仅用于 P0 诊断，不是后端运行依赖。本次 Python 3.13.9，ONNX 1.23.2、ONNX Runtime 1.31.1、OpenCV 4.12.0.88；完整已解析包版本见 `docs/p0/diagnostic-python-packages.json`，固定依赖见本目录的 `requirements.txt`。

在后端根目录创建独立虚拟环境，然后执行：

```powershell
python -m venv .\target\p0-tools\venv
.\target\p0-tools\venv\Scripts\python.exe -m pip install -r .\tools\p0-model-validation\requirements.txt
.\target\p0-tools\venv\Scripts\python.exe -m pip check
.\target\p0-tools\venv\Scripts\python.exe .\tools\p0-model-validation\inspect_models.py --root . --output .\docs\p0\onnx-structure.json
.\target\p0-tools\venv\Scripts\python.exe .\tools\p0-model-validation\verify_reference.py --root . --training-root 'D:\DeepLearning\severstal-steel-defect-detection'
```

最终复验使用上述虚拟环境，未修改系统 Python 包。初次探测使用过的 `target/p0-tools/python*` 是临时目录，不用于最终复现；不要将它们加入系统 PYTHONPATH。

交叉验证先使用 Java 导出的同一输入张量比较 Java/Python ONNX logits，再将 Java logits 送入原项目 `src/postprocess.py`，与 Java 四通道掩码逐像素比较。另用 OpenCV 独立解码参考 JPEG 核对 RGB 输入。该检查覆盖每个模型的一张参考图，不代表全部格式/插值算法等价，也不代表与 PyTorch checkpoint 做过比较。

输出 JSON 只有全部断言通过后才标记 `passed`。模型或图片变化后应重新生成结构报告、资产哈希和数值结果；不要手工调整结果文件中的测试结论。
