package org.ssdd.storage;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.config.StorageProperties;
import org.ssdd.detection.DetectionErrors;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** No public static mapping: only published DB references are exposed by the signed controller. */
@Component
public class DetectionStorage {
    private final Path directory;
    public DetectionStorage(StorageProperties config) { directory=config.root().resolve("results"); }
    public Map<String,String> save(String id,Map<String,BufferedImage> images) {
        UUID.fromString(id);var keys=new LinkedHashMap<String,String>();
        try {
            Files.createDirectories(directory);checkRoot();
            Path folder=directory.resolve(id);Files.createDirectory(folder);
            for(var image:images.entrySet()) {
                String key="results/"+id+"/"+image.getKey()+".png";
                Path path=resolve(key);
                if(!ImageIO.write(image.getValue(),"png",path.toFile()))throw new IOException("PNG encoder unavailable");
                keys.put(image.getKey(),key);
            }
            return Collections.unmodifiableMap(keys);
        } catch(IOException|RuntimeException failure) {
            discard(id);
            if(failure instanceof BusinessException business)throw business;
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"检测产物存储失败");
        }
    }
    public FileSystemResource resource(String key) {
        try {checkRoot();Path path=resolve(key);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw DetectionErrors.missing();return new FileSystemResource(path);}
        catch(IOException ex) {throw DetectionErrors.missing();}
    }
    private Path resolve(String key) throws IOException {
        if(key==null||!key.matches("results/[0-9a-f-]{36}/(original|mask|overlay|class-[1-4])\\.png"))throw DetectionErrors.missing();
        Path path=directory.getParent().resolve(key).normalize();
        if(!path.startsWith(directory)||Files.isSymbolicLink(path.getParent())||Files.isSymbolicLink(path))throw DetectionErrors.missing();
        return path;
    }
    private void checkRoot() throws IOException {
        if(Files.isSymbolicLink(directory)||!directory.toRealPath().equals(directory.toAbsolutePath().normalize()))throw new IOException("Managed result directory cannot contain symbolic links");
    }
    public void discard(String id) {
        if(id==null||!id.matches("[0-9a-f-]{36}"))return;
        try {
            if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS))return;
            checkRoot();Path folder=directory.resolve(id);if(!Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS))return;
            try(var children=Files.list(folder)) {for(Path file:children.toList()) {
                if(file.getFileName().toString().matches("(original|mask|overlay|class-[1-4])\\.png"))Files.deleteIfExists(file);
            }}
            Files.deleteIfExists(folder);
        } catch(IOException ex) {LoggerFactory.getLogger(getClass()).warn("Detection file compensation failed; record={}",id);}
    }
}
