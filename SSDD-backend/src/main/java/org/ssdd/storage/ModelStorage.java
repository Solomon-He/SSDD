package org.ssdd.storage;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.ssdd.config.StorageProperties;
import org.ssdd.model.ModelErrors;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

@Component
public class ModelStorage {
    public static final long MAX_BYTES=500L*1024*1024;
    public record Stored(String key, String fileName, long size, String sha256) {}
    private final Path directory;
    public ModelStorage(StorageProperties properties) { directory=properties.root().resolve("models").normalize(); }

    public Stored store(String id, MultipartFile file) {
        if (file==null || file.isEmpty()) throw ModelErrors.bad("请上传 ONNX 模型文件");
        if (file.getSize()>MAX_BYTES) throw ModelErrors.bad("模型文件不能超过 500 MiB");
        String name=Objects.toString(file.getOriginalFilename(),"").replace('\\','/');
        name=name.substring(name.lastIndexOf('/')+1);
        if (name.length()>255 || name.chars().anyMatch(Character::isISOControl) || !name.toLowerCase(Locale.ROOT).endsWith(".onnx"))
            throw ModelErrors.bad("模型文件必须为 .onnx 格式");
        Path temporary=null;
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) throw new IOException("Managed model directory is a symlink");
            temporary=Files.createTempFile(directory,"model-",".tmp");
            var digest=MessageDigest.getInstance("SHA-256"); long size=0;
            try (var source=file.getInputStream(); var output=Files.newOutputStream(temporary)) {
                byte[] buffer=new byte[64*1024]; int count;
                while ((count=source.read(buffer))!=-1) {
                    size+=count; if (size>MAX_BYTES) throw ModelErrors.bad("模型文件不能超过 500 MiB");
                    digest.update(buffer,0,count); output.write(buffer,0,count);
                }
            }
            if (size==0) throw ModelErrors.bad("模型文件为空");
            String key="models/"+UUID.fromString(id)+".onnx";
            Files.move(temporary,path(key),StandardCopyOption.ATOMIC_MOVE);
            return new Stored(key,name,size,HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException ex) { throw ModelErrors.unavailable("模型文件存储失败"); }
        finally {
            if (temporary!=null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    public Path verify(String key,long size,String expectedHash) {
        Path path=path(key);
        try {
            if (Files.isSymbolicLink(directory) || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)!=size)
                throw ModelErrors.bad("模型文件缺失或大小已变化");
            if (!hash(path).equals(expectedHash)) throw ModelErrors.bad("模型文件校验失败");
            return path;
        } catch (IOException ex) { throw ModelErrors.bad("模型文件无法读取"); }
    }
    private Path path(String key) {
        if (key==null || !key.matches("models/[0-9a-f-]{36}\\.onnx")) throw ModelErrors.bad("模型存储键无效");
        return directory.resolve(key.substring("models/".length()));
    }
    public void delete(String key) {
        try { Files.deleteIfExists(path(key)); }
        catch (IOException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Managed model cleanup failed; key={}",key); }
    }
    public static String hash(Path path) throws IOException {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            try (var input=Files.newInputStream(path)) {
                byte[] block=new byte[64*1024]; int size;
                while ((size=input.read(block))!=-1) digest.update(block,0,size);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
