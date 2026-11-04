package org.ssdd.task;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.ssdd.config.StorageProperties;
import org.ssdd.inference.DetectionImageDecoder;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Durable private raw uploads; never exposed by a static route or packed in the application. */
@Component
public class TaskStorage {
    public record Input(String name,String key,String sha256,long bytes) {}
    private final Path directory;
    public TaskStorage(StorageProperties config){directory=config.root().resolve("task-inputs");}
    public Input store(String id,int index,String name,DetectionImageDecoder.Upload upload) {
        if(index<0||index>=20)throw new IllegalArgumentException("Invalid task input index");UUID.fromString(id);
        Path temporary=null;
        try {
            Files.createDirectories(directory);root();Path folder=directory.resolve(id);Files.createDirectories(folder);safeFolder(folder);
            String key="task-inputs/"+id+"/"+String.format(Locale.ROOT,"%02d.image",index);
            temporary=Files.createTempFile(folder,"upload-",".tmp");Files.write(temporary,upload.bytes());Files.move(temporary,resolve(key),StandardCopyOption.ATOMIC_MOVE);
            return new Input(name,key,upload.sha256(),upload.bytes().length);
        }catch(IOException ex){throw new TaskFault("INPUT_STORAGE_UNAVAILABLE",true);}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
    public MultipartFile open(String taskId,int index,Input input) {
        String expected="task-inputs/"+UUID.fromString(taskId)+"/"+String.format(Locale.ROOT,"%02d.image",index);
        if(!expected.equals(input.key())||input.bytes()<1||input.bytes()>10485760||!input.sha256().matches("[0-9a-f]{64}"))throw new TaskFault("INPUT_INVALID",false);
        try {
            root();Path path=resolve(input.key());
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)!=input.bytes())throw new TaskFault("INPUT_MISSING",false);
            return new MultipartFile(){
                public String getName(){return "images";}
                public String getOriginalFilename(){return input.name();}
                public String getContentType(){return "application/octet-stream";}
                public boolean isEmpty(){return input.bytes()==0;}
                public long getSize(){return input.bytes();}
                public byte[] getBytes() throws IOException{return Files.readAllBytes(path);}
                public InputStream getInputStream() throws IOException{return Files.newInputStream(path);}
                public void transferTo(File destination) throws IOException{Files.copy(path,destination.toPath());}
            };
        }catch(IOException ex){throw new TaskFault("INPUT_MISSING",false);}
    }
    public boolean discard(String id) {
        UUID.fromString(id);
        try {
            if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS))return true;
            root();Path folder=directory.resolve(id);if(!Files.exists(folder,LinkOption.NOFOLLOW_LINKS))return true;safeFolder(folder);
            try(var children=Files.list(folder)){for(var child:children.toList()){
                String name=child.getFileName().toString();if(!name.matches("[0-9]{2}\\.image|upload-.*\\.tmp"))return false;
                if(!Files.isRegularFile(child,LinkOption.NOFOLLOW_LINKS))return false;Files.deleteIfExists(child);
            }}
            Files.deleteIfExists(folder);return true;
        }catch(IOException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Task private input cleanup failed; task={}",id);return false;}
    }
    private void root() throws IOException {if(!directory.toRealPath().equals(directory.toAbsolutePath().normalize()))throw new IOException("Unsafe task input directory");}
    private void safeFolder(Path folder) throws IOException {if(Files.isSymbolicLink(folder)||!Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsafe task input folder");}
    private Path resolve(String key) throws IOException {
        if(key==null||!key.matches("task-inputs/[0-9a-f-]{36}/[0-9]{2}\\.image"))throw new TaskFault("INPUT_INVALID",false);
        Path path=directory.getParent().resolve(key).normalize();if(!path.startsWith(directory)||Files.isSymbolicLink(path))throw new IOException("Unsafe task input path");safeFolder(path.getParent());return path;
    }
}
