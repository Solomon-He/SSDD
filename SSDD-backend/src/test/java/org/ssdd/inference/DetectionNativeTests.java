package org.ssdd.inference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DetectionNativeTests {
    @Test void productionDecodeAndPostprocessMatchAllTwentyTwoP0ReferenceMasks() throws Exception {
        var json=new ObjectMapper();var reference=json.readTree(getClass().getResource("/inference/java-cpu-validation.json"));
        var factory=new OrtInferenceSessionFactory(OrtInferenceSessionFactoryTests.config(InferenceProperties.Device.CPU,0));
        var decoder=new DetectionImageDecoder(DetectionPipelineTests.config());var processor=new V2DetectionPostprocessor();var rows=new ArrayList<Map<String,Object>>();
        try {
            for(var model:reference.path("models"))try(var handle=factory.openAndWarm(Path.of("models",model.path("file").asText()))) {
                for(var sample:model.path("samples")) {
                    var image=decoder.decode(Files.readAllBytes(Path.of("test-images",sample.path("image").asText()))).rgb();
                    float[] logits=handle.run(decoder.tensor(image));var output=processor.process(logits,image);
                    assertEquals(sample.path("union_pixels").asLong(),output.unionPixels());assertEquals(sample.path("defect_count").asInt(),output.count());
                    var hashes=new ArrayList<String>();
                    for(int c=0;c<4;c++) {
                        assertEquals(sample.path("class_pixels").get(c).asLong(),output.pixels()[c]);assertEquals(sample.path("class_components").get(c).asInt(),output.components()[c]);
                        var raster=output.images().get("class-"+(c+1)).getRaster();byte[] mask=new byte[image.getWidth()*image.getHeight()];
                        for(int p=0;p<mask.length;p++)mask[p]=(byte)(raster.getSample(p%image.getWidth(),p/image.getWidth(),0)/255);
                        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mask));hashes.add(hash);assertEquals(sample.path("mask_sha256").get(c).asText(),hash);
                    }
                    rows.add(Map.of("model",model.path("file").asText(),"image",sample.path("image").asText(),"mask_sha256",hashes,"components",output.components(),"pixels",output.pixels(),"union_pixels",output.unionPixels()));
                }
            }
            assertEquals(22,rows.size());Files.createDirectories(Path.of("target/p4"));json.writeValue(Path.of("target/p4/cpu-native.json").toFile(),rows);
        } finally {factory.shutdown();}
    }
}
