package org.ssdd.inference;

import ai.onnxruntime.*;
import ai.onnxruntime.providers.OrtCUDAProviderOptions;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.model.ModelErrors;
import java.nio.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OrtInferenceSessionFactory implements InferenceSessionFactory {
    public static final long[] INPUT_SHAPE = {1, 3, 256, 1600};
    public static final int INPUT_ELEMENTS = 3 * 256 * 1600;
    private final InferenceProperties config;
    private final Semaphore slots;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "ssdd-inference-deadlines"); thread.setDaemon(true); return thread;
    });

    public OrtInferenceSessionFactory(InferenceProperties config) {
        this.config = config;
        this.slots = new Semaphore(config.maxConcurrency());
    }

    @Override public void validateOnCpu(Path path) {
        try (var ignored = open(path, InferenceProperties.Device.CPU, "cpu-validation", null)) { }
        catch (BusinessException ex) {
            if (ex.getMessage().equals("推理输出无效")) throw ModelErrors.bad("模型预热输出不是有限的四通道 logits");
            throw ex;
        }
    }

    @Override public InferenceSession openAndWarm(Path path) {
        if (config.device() == InferenceProperties.Device.CPU) return open(path, config.device(), "cpu", null);
        try { return open(path, InferenceProperties.Device.CUDA, config.device().name().toLowerCase(Locale.ROOT), null); }
        catch (BusinessException failure) {
            if (config.device() == InferenceProperties.Device.CUDA)
                throw ModelErrors.unavailable("CUDA 初始化或预热失败，请检查 GPU 构建、运行库和可用显存");
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("CUDA candidate unavailable; creating CPU candidate; reason=CUDA_INITIALIZATION_FAILED");
            return open(path, InferenceProperties.Device.CPU, "auto", "CUDA_INITIALIZATION_FAILED");
        }
    }

    private NativeSession open(Path path, InferenceProperties.Device actual, String requested, String fallback) {
        OrtSession.SessionOptions options = null;
        OrtCUDAProviderOptions cuda = null;
        NativeSession handle = null;
        boolean returned = false;
        try {
            var env = OrtEnvironment.getEnvironment();
            env.setTelemetry(false);
            options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1);
            options.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
            if (actual == InferenceProperties.Device.CUDA) {
                if (!OrtEnvironment.getAvailableProviders().contains(OrtProvider.CUDA))
                    throw ModelErrors.unavailable("当前运行工件不支持 CUDA");
                cuda = new OrtCUDAProviderOptions(config.deviceId());
                cuda.add("use_tf32", "0"); cuda.add("gpu_mem_limit", Long.toString(config.gpuMemoryLimitBytes()));
                cuda.add("arena_extend_strategy", "kSameAsRequested"); cuda.add("cudnn_conv_algo_search", "DEFAULT");
                cuda.add("cudnn_conv_use_max_workspace", "0"); cuda.add("do_copy_in_default_stream", "1");
                options.addCUDA(cuda);
            } else options.addCPU(true);
            var session = env.createSession(path.toString(), options);
            handle = new NativeSession(session, options, cuda,
                    new InferenceSession.DeviceInfo(requested, actual.name().toLowerCase(Locale.ROOT), fallback, env.getVersion()));
            validateSignature(session);
            float[] warmup = new float[INPUT_ELEMENTS];
            for (int i = 0; i < warmup.length; i++) warmup[i] = i % 256;
            handle.run(warmup,true);
            returned = true;
            return handle;
        } catch (OrtException ex) {
            throw actual == InferenceProperties.Device.CUDA ? ModelErrors.unavailable("CUDA 模型初始化失败")
                    : ModelErrors.bad("ONNX 无法加载或不满足 V2 输入输出契约");
        } catch (LinkageError ex) {
            throw ModelErrors.unavailable("推理原生运行库不可用");
        } finally {
            // On success ownership transfers to the returned handle; on failure close every native object.
            if (handle != null && !returned) handle.close();
            else if (handle == null) {
                if (options != null) options.close();
                if (cuda != null) cuda.close();
            }
        }
    }

    private static void validateSignature(OrtSession session) throws OrtException {
        if (!session.getInputNames().equals(Set.of("input")) || !session.getOutputNames().equals(Set.of("output")))
            throw ModelErrors.bad("模型必须只有 input 输入和 output 输出");
        checkTensor(session.getInputInfo().get("input").getInfo(), 3);
        checkTensor(session.getOutputInfo().get("output").getInfo(), 4);
    }

    private static void checkTensor(ValueInfo value, int channels) {
        if (!(value instanceof TensorInfo tensor) || tensor.type != OnnxJavaType.FLOAT)
            throw ModelErrors.bad("模型输入输出必须为 float32 张量");
        long[] shape = tensor.getShape();
        if (shape.length != 4 || shape[1] != channels || (shape[0] != -1 && shape[0] != 1)
                || (shape[2] != -1 && shape[2] != 256) || (shape[3] != -1 && shape[3] != 1600))
            throw ModelErrors.bad("模型必须兼容 NCHW [1,3,256,1600] 输入及四通道输出");
    }

    private final class NativeSession implements InferenceSession {
        private final OrtSession session;
        private final OrtSession.SessionOptions options;
        private final OrtCUDAProviderOptions cuda;
        private final DeviceInfo info;
        private final AtomicBoolean closed = new AtomicBoolean();
        NativeSession(OrtSession session, OrtSession.SessionOptions options, OrtCUDAProviderOptions cuda, DeviceInfo info) {
            this.session=session; this.options=options; this.cuda=cuda; this.info=info;
        }
        @Override public DeviceInfo deviceInfo() { return info; }
        @Override public float[] run(float[] values) { return run(values,false).logits(); }
        @Override public RunResult runTimed(float[] values) {return run(values,false);}
        private RunResult run(float[] values,boolean warmup) {
            if (values == null || values.length != INPUT_ELEMENTS) throw ModelErrors.bad("推理输入尺寸错误");
            if (closed.get()) throw ModelErrors.unavailable("推理 Session 已关闭");
            try {
                boolean acquired=warmup?slots.tryAcquire(config.runTimeout().toMillis(),TimeUnit.MILLISECONDS):slots.tryAcquire();
                if(!acquired)throw ModelErrors.busy();
            } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw ModelErrors.busy(); }
            try {
                var direct = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                direct.put(values).rewind();
                var env = OrtEnvironment.getEnvironment();
                try (var input = OnnxTensor.createTensor(env, direct, INPUT_SHAPE)) {
                    var runOptions = new OrtSession.RunOptions();
                    var completed = new AtomicBoolean(); var expired = new AtomicBoolean();
                    var deadline = deadlines.schedule(() -> {
                        synchronized (runOptions) {
                            if (!completed.get()) {
                                expired.set(true);
                                try { runOptions.setTerminate(true); } catch (OrtException ignored) { }
                            }
                        }
                    }, config.runTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    long nativeStart=System.nanoTime();
                    try (var output = session.run(Map.of("input", input), runOptions)) {
                        double nativeMillis=(System.nanoTime()-nativeStart)/1_000_000.0;
                        if (expired.get()) throw timeout();
                        var tensor = (OnnxTensor)output.get("output").orElseThrow();
                        if (!Arrays.equals(tensor.getInfo().getShape(), new long[]{1,4,256,1600}))
                            throw ModelErrors.unavailable("推理输出无效");
                        var buffer = tensor.getFloatBuffer();
                        float[] logits = new float[buffer.remaining()]; buffer.get(logits);
                        for (float value : logits) if (!Float.isFinite(value)) throw ModelErrors.unavailable("推理输出无效");
                        return new RunResult(logits,nativeMillis);
                    } catch (OrtException ex) {
                        if (expired.get()) throw timeout();
                        throw ModelErrors.unavailable("模型推理失败");
                    } finally {
                        synchronized (runOptions) { completed.set(true); deadline.cancel(false); runOptions.close(); }
                    }
                } catch (OrtException ex) { throw ModelErrors.unavailable("推理张量创建失败"); }
            } finally { slots.release(); }
        }
        @Override public void close() {
            if (!closed.compareAndSet(false,true)) return;
            try { session.close(); }
            catch (OrtException ex) { throw new IllegalStateException("Native Session release failed"); }
            finally { try { options.close(); } finally { if (cuda != null) cuda.close(); } }
        }
    }
    private static BusinessException timeout() {
        return new BusinessException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT, "推理超出处理预算");
    }
    @PreDestroy void shutdown() { deadlines.shutdown(); }
}
