package org.ssdd.inference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.model.OnnxModelGuard;
import org.ssdd.storage.ModelStorage;
import org.ssdd.support.OnnxFixtures;
import javax.imageio.ImageIO;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrtInferenceSessionFactoryTests {
    @TempDir Path directory;
    static InferenceProperties config(InferenceProperties.Device device,int id) {
        return new InferenceProperties(device,id,1,2,6L*1024*1024*1024,Duration.ofSeconds(30));
    }
    @Test void loadsRealSyntheticV2AndRejectsWrongSignatureAndNonfiniteOutput() throws Exception {
        var factory=new OrtInferenceSessionFactory(config(InferenceProperties.Device.CPU,0));
        try {
            Path valid=Files.write(directory.resolve("valid.onnx"),OnnxFixtures.valid());
            new OnnxModelGuard().inspect(valid,ModelStorage.hash(valid));
            factory.validateOnCpu(valid);
            try(var session=factory.openAndWarm(valid)) {
                assertEquals("cpu",session.deviceInfo().actual());
                assertEquals(4*256*1600,session.run(new float[OrtInferenceSessionFactory.INPUT_ELEMENTS]).length);
                assertThrows(BusinessException.class,()->session.run(new float[3]));
            }
            Path wrong=Files.write(directory.resolve("wrong.onnx"),OnnxFixtures.wrongChannels());
            assertThrows(BusinessException.class,()->factory.validateOnCpu(wrong));
            Path nonfinite=Files.write(directory.resolve("nan.onnx"),OnnxFixtures.nonfinite());
            assertEquals(400,assertThrows(BusinessException.class,()->factory.validateOnCpu(nonfinite)).getStatus().value());
        } finally { factory.shutdown(); }
    }
    @Test void rejectsForcedCudaAndObservablyFallsBackForInvalidDevice() throws Exception {
        Path valid=Files.write(directory.resolve("valid.onnx"),OnnxFixtures.valid());
        var forced=new OrtInferenceSessionFactory(config(InferenceProperties.Device.CUDA,999));
        var auto=new OrtInferenceSessionFactory(config(InferenceProperties.Device.AUTO,999));
        try {
            assertEquals(503,assertThrows(BusinessException.class,()->forced.openAndWarm(valid)).getStatus().value());
            try(var handle=auto.openAndWarm(valid)) {
                assertEquals("cpu",handle.deviceInfo().actual());assertEquals("CUDA_INITIALIZATION_FAILED",handle.deviceInfo().fallbackReason());
            }
        } finally { forced.shutdown();auto.shutdown(); }
    }
    @Test void bundledModelsMatchRecordedCpuLogitsUsingProductionPipeline() throws Exception {
        var json=new ObjectMapper();var reference=json.readTree(getClass().getResource("/inference/cpu-baseline.json"));
        var factory=new OrtInferenceSessionFactory(config(InferenceProperties.Device.CPU,0));
        try {
            for(var model:reference.path("models")) {
                Path path=Path.of("models",model.path("file").asText());
                assertEquals("bundled_sha256",new OnnxModelGuard().inspect(path,ModelStorage.hash(path)));
                var sample=model.path("samples").get(0);
                float[] values=input(Path.of("test-images",sample.path("image").asText()));
                try(var handle=factory.openAndWarm(path)) {
                    var output=handle.run(values);var bytes=ByteBuffer.allocate(output.length*4).order(ByteOrder.LITTLE_ENDIAN);bytes.asFloatBuffer().put(output);
                    assertEquals(sample.path("logits_sha256").asText(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array())));
                }
            }
        } finally { factory.shutdown(); }
    }
    static float[] input(Path image) throws Exception {
        var decoded=ImageIO.read(image.toFile());int size=decoded.getWidth()*decoded.getHeight();float[] input=new float[size*3];
        for(int y=0;y<decoded.getHeight();y++)for(int x=0;x<decoded.getWidth();x++) {
            int rgb=decoded.getRGB(x,y),p=y*decoded.getWidth()+x;input[p]=(rgb>>16)&255;input[size+p]=(rgb>>8)&255;input[size*2+p]=rgb&255;
        }
        return input;
    }
}
