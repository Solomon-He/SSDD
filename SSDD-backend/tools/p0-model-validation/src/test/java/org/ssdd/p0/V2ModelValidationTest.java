package org.ssdd.p0;

import ai.onnxruntime.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Explicitly run standalone P0 tool; these are compatibility checks, not accuracy claims. */
class V2ModelValidationTest {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final int[] COLORS = {0xef4444, 0x10b981, 0x3b82f6, 0xf59e0b};
    private final Path root = Path.of(System.getProperty("p0.root")).toAbsolutePath().normalize();
    private final Path output = root.resolve("target/p0-validation");

    @Test
    void validatesV2ModelsOnJava17Cpu() throws Exception {
        assertEquals(17, Runtime.version().feature(), "Run this tool with -Dp0.java=<Java 17 executable>");
        Files.createDirectories(output);
        List<Path> models;
        List<Path> images;
        try (var files = Files.list(root.resolve("models"))) {
            models = files.filter(p -> p.toString().endsWith(".onnx")).sorted().toList();
        }
        try (var files = Files.list(root.resolve("test-images"))) {
            images = files.filter(p -> p.toString().endsWith(".jpg")).sorted().toList();
        }
        assertEquals(2, models.size());
        assertEquals(11, images.size());
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        env.setTelemetry(false);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generated_at", Instant.now().toString());
        report.put("java_version", System.getProperty("java.version"));
        report.put("java_vendor", System.getProperty("java.vendor"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.put("cpu", System.getenv("PROCESSOR_IDENTIFIER"));
        report.put("available_processors", Runtime.getRuntime().availableProcessors());
        var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        report.put("physical_memory_bytes", os.getTotalMemorySize());
        report.put("runtime_version", env.getVersion());
        report.put("available_providers", OrtEnvironment.getAvailableProviders().toString());
        report.put("execution_provider", "CPU");
        report.put("intra_op_threads", 2);
        report.put("inter_op_threads", 1);
        report.put("warmup_runs_per_model", 1);
        report.put("pipeline", "single_pass_without_TTA");
        report.put("preprocessing", "ImageIO JPEG decode; RGB float32 [0,255]; NCHW; native 256x1600; no external normalization");
        report.put("postprocessing", Map.of("pixel_threshold", 0.5, "comparison", ">=", "connectivity", 8,
                "min_component_size", 150, "min_area", V2Postprocessor.MIN_AREA));
        List<Map<String, Object>> modelReports = new ArrayList<>();
        report.put("models", modelReports);
        for (Path model : models) {
            String stem = model.getFileName().toString().replace(".onnx", "");
            Path modelOutput = output.resolve(stem);
            Files.createDirectories(modelOutput);
            try (var options = new OrtSession.SessionOptions()) {
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                options.setIntraOpNumThreads(2);
                options.setInterOpNumThreads(1);
                long loadStart = System.nanoTime();
                try (var session = env.createSession(model.toString(), options)) {
                    Map<String, Object> mr = new LinkedHashMap<>();
                    modelReports.add(mr);
                    mr.put("file", model.getFileName().toString());
                    mr.put("load_ms", elapsed(loadStart));
                    mr.put("inputs", nodes(session.getInputInfo()));
                    mr.put("outputs", nodes(session.getOutputInfo()));
                    mr.put("custom_metadata", session.getMetadata().getCustomMetadata());
                    assertEquals(Set.of("input"), session.getInputNames());
                    assertEquals(Set.of("output"), session.getOutputNames());
                    TensorInfo inputInfo = (TensorInfo) session.getInputInfo().get("input").getInfo();
                    TensorInfo outputInfo = (TensorInfo) session.getOutputInfo().get("output").getInfo();
                    assertEquals(OnnxJavaType.FLOAT, inputInfo.type);
                    assertEquals(3, inputInfo.getShape()[1]);
                    assertEquals(4, outputInfo.getShape()[1]);
                    List<Map<String, Object>> runs = new ArrayList<>();
                    mr.put("samples", runs);
                    BufferedImage first = ImageIO.read(images.get(0).toFile());
                    float[] firstInput = toRgbNchw(first);
                    run(env, session, firstInput, new long[]{1, 3, first.getHeight(), first.getWidth()});
                    for (int imageIndex = 0; imageIndex < images.size(); imageIndex++) {
                        Path image = images.get(imageIndex);
                        BufferedImage decoded = ImageIO.read(image.toFile());
                        assertNotNull(decoded);
                        assertEquals(1600, decoded.getWidth());
                        assertEquals(256, decoded.getHeight());
                        int width = decoded.getWidth(), height = decoded.getHeight();
                        float[] input = toRgbNchw(decoded);
                        var result = run(env, session, input, new long[]{1, 3, height, width});
                        assertArrayEquals(new long[]{1, 4, height, width}, result.shape());
                        var masks = V2Postprocessor.process(result.logits(), width, height,
                                V2Postprocessor.MIN_COMPONENT, V2Postprocessor.MIN_AREA);
                        Map<String, Object> sample = new LinkedHashMap<>();
                        sample.put("image", image.getFileName().toString());
                        sample.put("output_shape", result.shape());
                        sample.put("run_ms", result.millis());
                        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
                        for (float f : result.logits()) { assertTrue(Float.isFinite(f)); min = Math.min(min, f); max = Math.max(max, f); }
                        sample.put("logit_min", min);
                        sample.put("logit_max", max);
                        sample.put("class_pixels", masks.pixels());
                        sample.put("class_components", masks.components());
                        sample.put("defect_count", Arrays.stream(masks.components()).sum());
                        sample.put("union_pixels", masks.unionPixels());
                        sample.put("overlap_pixels", masks.overlapPixels());
                        sample.put("defect_ratio", masks.unionPixels() / (double) (width * height));
                        sample.put("mask_sha256", hashes(masks.channels()));
                        runs.add(sample);
                        System.out.printf(Locale.ROOT, "%s / %s: %.1f ms, pixels=%s%n", stem,
                                image.getFileName(), result.millis(), Arrays.toString(masks.pixels()));
                        if (imageIndex == 0) {
                            writeFloats(modelOutput.resolve("input.f32"), input);
                            writeFloats(modelOutput.resolve("logits.f32"), result.logits());
                            for (int c = 0; c < 4; c++) Files.write(modelOutput.resolve("mask-" + (c + 1) + ".u8"), masks.channels()[c]);
                            previews(decoded, result.logits(), masks, modelOutput);
                            var repeat = run(env, session, input, new long[]{1, 3, height, width});
                            float maxDifference = 0;
                            for (int j = 0; j < result.logits().length; j++) {
                                maxDifference = Math.max(maxDifference, Math.abs(result.logits()[j] - repeat.logits()[j]));
                            }
                            mr.put("repeat_max_abs_logit_difference", maxDifference);
                            assertEquals(0, maxDifference, "Same-session sequential CPU result should be deterministic here");
                        }
                    }
                    List<Map<String, Object>> probes = new ArrayList<>();
                    mr.put("shape_probes", probes);
                    for (long[] shape : List.of(new long[]{1, 3, 128, 128}, new long[]{2, 3, 128, 128})) {
                        int elements = Math.toIntExact(Arrays.stream(shape).reduce(1, (a, b) -> a * b));
                        float[] values = new float[elements];
                        Arrays.fill(values, 128);
                        var result = run(env, session, values, shape);
                        assertArrayEquals(new long[]{shape[0], 4, shape[2], shape[3]}, result.shape());
                        probes.add(Map.of("input", shape, "output", result.shape(), "status", "passed"));
                    }
                    try (var wrong = OnnxTensor.createTensor(env, FloatBuffer.wrap(new float[128 * 128]), new long[]{1, 1, 128, 128})) {
                        assertThrows(OrtException.class, () -> { try (var ignored = session.run(Map.of("input", wrong))) { } });
                    }
                    mr.put("wrong_channel_count_rejected", true);
                    double[] times = runs.stream().mapToDouble(r -> (double) r.get("run_ms")).sorted().toArray();
                    mr.put("sample_count", times.length);
                    mr.put("p50_run_ms", times[(int) Math.ceil(times.length * 0.50) - 1]);
                    mr.put("p95_run_ms", times[(int) Math.ceil(times.length * 0.95) - 1]);
                    mr.put("mean_run_ms", Arrays.stream(times).average().orElseThrow());
                }
            }
            JSON.writeValue(root.resolve("docs/p0/java-cpu-validation.json").toFile(), report);
        }
        report.put("status", "passed");
        JSON.writeValue(root.resolve("docs/p0/java-cpu-validation.json").toFile(), report);
    }

    @Test
    void packsRgbInNchwWithoutNormalization() {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff8040);
        image.setRGB(1, 0, 0x102030);
        assertArrayEquals(new float[]{255, 16, 128, 32, 64, 48}, toRgbNchw(image));
    }

    private record Run(float[] logits, long[] shape, double millis) {}

    private static Run run(OrtEnvironment env, OrtSession session, float[] values, long[] shape) throws OrtException {
        try (var tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(values), shape)) {
            long start = System.nanoTime();
            try (var results = session.run(Map.of("input", tensor))) {
                double millis = elapsed(start);
                OnnxTensor output = (OnnxTensor) results.get("output").orElseThrow();
                FloatBuffer buffer = output.getFloatBuffer();
                float[] logits = new float[buffer.remaining()];
                buffer.get(logits);
                return new Run(logits, output.getInfo().getShape(), millis);
            }
        }
    }

    private static List<Map<String, Object>> nodes(Map<String, NodeInfo> nodes) {
        return nodes.entrySet().stream().map(e -> {
            TensorInfo ti = (TensorInfo) e.getValue().getInfo();
            return Map.<String, Object>of("name", e.getKey(), "type", ti.type.toString(), "shape", ti.getShape());
        }).toList();
    }

    static float[] toRgbNchw(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight(), size = width * height;
        float[] values = new float[3 * size];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int rgb = image.getRGB(x, y), p = y * width + x;
            values[p] = (rgb >> 16) & 255;
            values[size + p] = (rgb >> 8) & 255;
            values[2 * size + p] = rgb & 255;
        }
        return values;
    }

    private static void writeFloats(Path path, float[] values) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        b.asFloatBuffer().put(values);
        Files.write(path, b.array());
    }

    private static List<String> hashes(byte[][] masks) throws Exception {
        List<String> hashes = new ArrayList<>();
        for (byte[] mask : masks) hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mask)));
        return hashes;
    }

    private static void previews(BufferedImage original, float[] logits, V2Postprocessor.Masks masks, Path path) throws IOException {
        int w = original.getWidth(), h = original.getHeight(), size = w * h;
        BufferedImage index = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        BufferedImage overlay = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int p = 0; p < size; p++) {
            int selected = -1;
            for (int c = 0; c < 4; c++) if (masks.channels()[c][p] != 0 &&
                    (selected < 0 || logits[c * size + p] > logits[selected * size + p])) selected = c;
            int x = p % w, y = p / w, rgb = original.getRGB(x, y);
            index.getRaster().setSample(x, y, 0, selected + 1);
            if (selected >= 0) {
                int color = COLORS[selected], blended = 0;
                for (int shift : new int[]{16, 8, 0}) {
                    int value = (int) Math.round(0.55 * ((rgb >> shift) & 255) + 0.45 * ((color >> shift) & 255));
                    blended |= value << shift;
                }
                rgb = blended;
            }
            overlay.setRGB(x, y, rgb);
        }
        ImageIO.write(original, "png", path.resolve("original.png").toFile());
        ImageIO.write(index, "png", path.resolve("preview-index.png").toFile());
        ImageIO.write(overlay, "png", path.resolve("overlay.png").toFile());
        for (int c = 0; c < 4; c++) {
            BufferedImage binary = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
            for (int p = 0; p < size; p++) binary.getRaster().setSample(p % w, p / w, 0, masks.channels()[c][p] * 255);
            ImageIO.write(binary, "png", path.resolve("mask-" + (c + 1) + ".png").toFile());
        }
    }

    private static double elapsed(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
}
