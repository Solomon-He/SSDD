package org.ssdd.inference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.ssdd.inference.OrtInferenceSessionFactoryTests.*;

/** Explicit opt-in: -Pgpu -Dtest=OrtGpuVerification. Not selected by ordinary Surefire tests. */
class OrtGpuVerification {
    @Test void productionCudaSessionsMatchFreshCpuForBothRealModels() throws Exception {
        var cpu=new OrtInferenceSessionFactory(config(InferenceProperties.Device.CPU,0));
        var gpu=new OrtInferenceSessionFactory(config(InferenceProperties.Device.CUDA,0));
        var rows=new ArrayList<Map<String,Object>>();
        try {
            float[] input=input(Path.of("test-images/0025bde0c.jpg"));
            try(var files=Files.list(Path.of("models"))) {
                var models=files.filter(p->p.toString().endsWith(".onnx")).sorted().toList();assertEquals(2,models.size());
                for(Path model:models) {
                    try(var a=cpu.openAndWarm(model);var b=gpu.openAndWarm(model)) {
                        assertEquals("cuda",b.deviceInfo().actual());float[] expected=a.run(input),actual=b.run(input);double max=0;
                        for(int i=0;i<expected.length;i++) { double delta=Math.abs((double)actual[i]-expected[i]);max=Math.max(max,delta);assertTrue(delta<=.001+.001*Math.abs(expected[i])); }
                        rows.add(Map.of("model",model.getFileName().toString(),"actual_device",b.deviceInfo().actual(),"max_abs_error",max));
                    }
                }
            }
            Files.createDirectories(Path.of("target/p3"));new ObjectMapper().writeValue(Path.of("target/p3/gpu-native.json").toFile(),rows);
        } finally { cpu.shutdown();gpu.shutdown(); }
    }
}
