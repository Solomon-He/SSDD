package org.ssdd.inference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit GPU build/DLL environment; default CPU verify never reports this as executed. */
class DetectionGpuVerification {
    @Test void productionPipelineMatchesCpuForAllImagesUnderExistingP25Tolerance() throws Exception {
        var json=new ObjectMapper();var policy=json.readTree(getClass().getResource("/inference/tolerance-policy.json"));
        var cpu=new OrtInferenceSessionFactory(OrtInferenceSessionFactoryTests.config(InferenceProperties.Device.CPU,0));
        var cuda=new OrtInferenceSessionFactory(OrtInferenceSessionFactoryTests.config(InferenceProperties.Device.CUDA,0));
        var decoder=new DetectionImageDecoder(DetectionPipelineTests.config());var processor=new V2DetectionPostprocessor();var rows=new ArrayList<Map<String,Object>>();
        try(var models=Files.list(Path.of("models"));var images=Files.list(Path.of("test-images"))) {
            var paths=images.filter(p->p.toString().endsWith(".jpg")).sorted().toList();assertEquals(11,paths.size());
            for(Path model:models.filter(p->p.toString().endsWith(".onnx")).sorted().toList())try(var a=cpu.openAndWarm(model);var b=cuda.openAndWarm(model)) {
                assertEquals("cuda",b.deviceInfo().actual());
                for(Path path:paths) {
                    var image=decoder.decode(Files.readAllBytes(path)).rgb();var input=decoder.tensor(image);float[] expected=a.run(input),actual=b.run(input);
                    double max=0,maxFlipDistance=0;int flips=0;
                    for(int i=0;i<expected.length;i++) {
                        double delta=Math.abs((double)actual[i]-expected[i]);max=Math.max(max,delta);
                        assertTrue(delta<=policy.path("per_element_logit_atol").asDouble()+policy.path("per_element_logit_rtol").asDouble()*Math.abs(expected[i]));
                        if((actual[i]>=0)!=(expected[i]>=0)){flips++;maxFlipDistance=Math.max(maxFlipDistance,Math.abs(1/(1+Math.exp(-expected[i]))-.5));}
                    }
                    assertTrue(flips/(double)expected.length<=policy.path("max_raw_mask_disagreement_fraction").asDouble());
                    assertTrue(maxFlipDistance<=policy.path("max_cpu_probability_distance_from_threshold_for_flips").asDouble());
                    var ca=processor.process(expected,image);var cb=processor.process(actual,image);assertArrayEquals(ca.components(),cb.components());
                    var ious=new ArrayList<Double>();
                    for(int c=1;c<=4;c++) {
                        var ra=ca.images().get("class-"+c).getRaster();var rb=cb.images().get("class-"+c).getRaster();long intersection=0,union=0;
                        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){boolean va=ra.getSample(x,y,0)>0,vb=rb.getSample(x,y,0)>0;if(va&&vb)intersection++;if(va||vb)union++;}
                        double iou=union==0?1:intersection/(double)union;assertTrue(iou>=policy.path("min_filtered_mask_iou_per_class").asDouble());ious.add(iou);
                    }
                    rows.add(Map.of("model",model.getFileName().toString(),"image",path.getFileName().toString(),"actual_device","cuda","max_abs_error",max,"raw_mask_flips",flips,"filtered_iou",ious,"component_counts_match",true));
                }
            }
            assertEquals(22,rows.size());Files.createDirectories(Path.of("target/p4"));json.writeValue(Path.of("target/p4/gpu-native.json").toFile(),rows);
        } finally {cpu.shutdown();cuda.shutdown();}
    }
}
