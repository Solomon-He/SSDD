package org.ssdd.inference;

import ai.onnxruntime.OrtEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.support.OnnxFixtures;
import java.nio.file.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class FrozenSessionTests {
    @TempDir Path directory;
    @Test void frozenAutoFallbackKeepsCpuEvenWhenServerConfigurationChanges() throws Exception {
        var factory=new OrtInferenceSessionFactory(OrtInferenceSessionFactoryTests.config(InferenceProperties.Device.CPU,0));
        var saved=new InferenceProperties(InferenceProperties.Device.AUTO,999,1,2,6L*1024*1024*1024,Duration.ofSeconds(30));
        var device=new InferenceSession.DeviceInfo("auto","cpu","CUDA_INITIALIZATION_FAILED",OrtEnvironment.getEnvironment().getVersion());
        try(var session=factory.openFrozen(Files.write(directory.resolve("model.onnx"),OnnxFixtures.valid()),device,saved)){
            assertEquals(device,session.deviceInfo());assertEquals(4*1600*256,session.run(new float[3*1600*256]).length);
        }finally{factory.shutdown();}
    }
    @Test void frozenCudaOrRuntimeMismatchNeverSilentlyChangesProvider() throws Exception {
        var factory=new OrtInferenceSessionFactory(OrtInferenceSessionFactoryTests.config(InferenceProperties.Device.CPU,0));
        var saved=new InferenceProperties(InferenceProperties.Device.AUTO,999,1,2,6L*1024*1024*1024,Duration.ofSeconds(30));
        Path model=Files.write(directory.resolve("model.onnx"),OnnxFixtures.valid());
        try{
            assertEquals(503,assertThrows(BusinessException.class,()->factory.openFrozen(model,new InferenceSession.DeviceInfo("auto","cuda",null,OrtEnvironment.getEnvironment().getVersion()),saved)).getStatus().value());
            assertEquals(503,assertThrows(BusinessException.class,()->factory.openFrozen(model,new InferenceSession.DeviceInfo("cpu","cpu",null,"incompatible-runtime"),saved)).getStatus().value());
        }finally{factory.shutdown();}
    }
    @Test void missingNativeRuntimeIsRetryableInsteadOfTerminatingDispatcher() {
        var factory=new OrtInferenceSessionFactory(OrtInferenceSessionFactoryTests.config(InferenceProperties.Device.CPU,0));
        try(var environment=org.mockito.Mockito.mockStatic(OrtEnvironment.class)) {
            environment.when(OrtEnvironment::getEnvironment).thenThrow(new NoClassDefFoundError("injected missing native runtime"));
            assertEquals(503,assertThrows(BusinessException.class,()->factory.openFrozen(directory.resolve("unused.onnx"),new InferenceSession.DeviceInfo("cpu","cpu",null,"1.31.1"),OrtInferenceSessionFactoryTests.config(InferenceProperties.Device.CPU,0))).getStatus().value());
        }finally{factory.shutdown();}
    }
}
