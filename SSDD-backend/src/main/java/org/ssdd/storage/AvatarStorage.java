package org.ssdd.storage;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.ssdd.common.exception.BusinessException;
import org.ssdd.config.StorageProperties;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class AvatarStorage {
    public record Prepared(String key) {}
    private static final long MAX_BYTES=2L*1024*1024;
    private final Path directory;
    public AvatarStorage(StorageProperties properties) { directory=properties.root().resolve("avatars").normalize(); }
    public Prepared prepare(String userId,MultipartFile file) {
        if(file==null||file.isEmpty())throw bad("请上传头像文件");
        if(file.getSize()>MAX_BYTES)throw bad("头像文件不能超过 2 MiB");
        try(var input=new MemoryCacheImageInputStream(file.getInputStream())) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw bad("不支持的头像图片格式");
            ImageReader reader=readers.next();
            BufferedImage decoded;
            try {
                reader.setInput(input,true,true);
                if(!Set.of("jpeg","jpg","png","gif","webp").contains(reader.getFormatName().toLowerCase(Locale.ROOT)))throw bad("不支持的头像图片格式");
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0||height<=0||width>4096||height>4096||(long)width*height>16_000_000)throw bad("头像尺寸过大");
                decoded=reader.read(0); // Animated files are decoded as a single static frame.
            } finally { reader.dispose(); }
            double scale=Math.min(1,512.0/Math.max(decoded.getWidth(),decoded.getHeight()));
            var normalized=new BufferedImage(Math.max(1,(int)Math.round(decoded.getWidth()*scale)),Math.max(1,(int)Math.round(decoded.getHeight()*scale)),BufferedImage.TYPE_INT_ARGB);
            var graphics=normalized.createGraphics();
            try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.drawImage(decoded,0,0,normalized.getWidth(),normalized.getHeight(),null); }
            finally { graphics.dispose(); }
            Files.createDirectories(directory);
            if(Files.isSymbolicLink(directory))throw new IOException("Managed avatar directory cannot be a symbolic link");
            String filename=userId+"_"+UUID.randomUUID()+".png";
            Path temporary=Files.createTempFile(directory,"avatar-",".tmp");
            try {
                if(!ImageIO.write(normalized,"png",temporary.toFile()))throw new IOException("PNG encoder unavailable");
                Files.move(temporary,directory.resolve(filename),StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary); }
            return new Prepared("avatars/"+filename);
        } catch(BusinessException ex) { throw ex; }
        catch(javax.imageio.IIOException ex) { throw bad("头像图片无法解码"); }
        catch(IOException ex) { throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"头像处理或存储失败"); }
    }
    public byte[] read(String filename) {
        if(!filename.matches("[0-9a-f-]{36}_[0-9a-f-]{36}\\.png"))throw missing();
        Path path=directory.resolve(filename).normalize();
        if(!path.startsWith(directory)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw missing();
        try { return Files.readAllBytes(path); } catch(IOException ex) { throw missing(); }
    }
    public void discard(String userId,Prepared prepared) { deleteOwned(userId,prepared.key()); }
    public void afterCommitDelete(String userId,String key) {
        if(key==null)return;
        if(!TransactionSynchronizationManager.isSynchronizationActive())throw new IllegalStateException("Avatar reference updates require a transaction");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteOwned(userId,key); }
        });
    }
    private void deleteOwned(String userId,String key) {
        if(key==null||!key.matches("avatars/"+java.util.regex.Pattern.quote(userId)+"_[0-9a-f-]{36}\\.png"))return;
        try { Files.deleteIfExists(directory.resolve(key.substring("avatars/".length()))); }
        catch(IOException ex) { LoggerFactory.getLogger(getClass()).warn("Managed old avatar cleanup failed"); }
    }
    private static BusinessException bad(String text) { return new BusinessException(HttpStatus.BAD_REQUEST,text); }
    private static BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND,"头像不存在"); }
}
