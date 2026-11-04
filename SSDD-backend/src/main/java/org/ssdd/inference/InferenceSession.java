package org.ssdd.inference;

/** A warmed, validated native session. The caller owns its lifetime. */
public interface InferenceSession extends AutoCloseable {
    record DeviceInfo(String requested, String actual, String fallbackReason, String runtimeVersion) {}
    record RunResult(float[] logits,double inferenceMillis) {}
    DeviceInfo deviceInfo();
    float[] run(float[] rgbNchw);
    default RunResult runTimed(float[] rgbNchw) {
        long started=System.nanoTime();float[] logits=run(rgbNchw);
        return new RunResult(logits,(System.nanoTime()-started)/1_000_000.0);
    }
    @Override void close();
}
