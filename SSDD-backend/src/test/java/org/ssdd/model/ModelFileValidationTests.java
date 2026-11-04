package org.ssdd.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.ssdd.config.StorageProperties;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.storage.ModelStorage;
import org.ssdd.support.OnnxFixtures;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelFileValidationTests {
    @TempDir Path root;
    @Test void acceptsDeclaredEmbeddedV2AndRejectsUnsafeOrIncompatibleGraphs() throws Exception {
        var guard=new OnnxModelGuard();
        var valid=Files.write(root.resolve("valid.onnx"),OnnxFixtures.valid());
        assertEquals("onnx_metadata",guard.inspect(valid,ModelStorage.hash(valid)));
        var invalid=List.of(OnnxFixtures.external(),OnnxFixtures.nestedExternal(),OnnxFixtures.wrongChannels(),
                OnnxFixtures.sigmoidOutput(),OnnxFixtures.customDomain(),OnnxFixtures.missingContract(),"not protobuf".getBytes());
        for(int i=0;i<invalid.size();i++) {
            var path=Files.write(root.resolve("bad-"+i+".onnx"),invalid.get(i));
            assertEquals(400,assertThrows(BusinessException.class,()->guard.inspect(path,ModelStorage.hash(path))).getStatus().value());
        }
    }
    @Test void boundsMalformedLengthDelimitedMetadataBeforeAllocation() throws Exception {
        var path=Files.write(root.resolve("broken.onnx"),new byte[]{114,(byte)255,(byte)255,(byte)255,(byte)255,7});
        assertEquals(400,assertThrows(BusinessException.class,()->new OnnxModelGuard().inspect(path,ModelStorage.hash(path))).getStatus().value());
    }
    @Test void storesMeasuredBytesUnderUuidAndDetectsTampering() throws Exception {
        var storage=new ModelStorage(new StorageProperties(root));
        var file=new MockMultipartFile("model","../../original.onnx","application/octet-stream",OnnxFixtures.valid());
        var stored=storage.store(UUID.randomUUID().toString(),file);
        assertEquals("original.onnx",stored.fileName());assertEquals(file.getSize(),stored.size());
        var path=storage.verify(stored.key(),stored.size(),stored.sha256());
        Files.write(path,new byte[(int)stored.size()]);
        assertThrows(BusinessException.class,()->storage.verify(stored.key(),stored.size(),stored.sha256()));
        assertThrows(BusinessException.class,()->storage.verify("models/../../application-local.yml",1,"x"));
        storage.delete(stored.key());assertFalse(Files.exists(path));
    }
    @Test void rejectsOversizeBeforeReadingPayload() throws Exception {
        var storage=new ModelStorage(new StorageProperties(root));
        var huge=new MockMultipartFile("model","huge.onnx","application/octet-stream",new byte[]{1}) {
            @Override public long getSize() { return ModelStorage.MAX_BYTES+1; }
            @Override public java.io.InputStream getInputStream() { fail("Oversize content must not be opened");return null; }
        };
        assertThrows(BusinessException.class,()->storage.store(UUID.randomUUID().toString(),huge));
        assertFalse(Files.exists(root.resolve("models")));
    }
}
