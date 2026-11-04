package org.ssdd.inference;

import java.nio.file.Path;

public interface InferenceSessionFactory {
    InferenceSession openAndWarm(Path model);
    void validateOnCpu(Path model);
    InferenceSession openFrozen(Path model,InferenceSession.DeviceInfo device,InferenceProperties settings);
}
