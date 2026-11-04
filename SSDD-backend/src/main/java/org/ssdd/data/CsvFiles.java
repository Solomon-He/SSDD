package org.ssdd.data;

import org.apache.commons.csv.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.ssdd.config.StorageProperties;
import org.ssdd.common.exception.BusinessException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;

@Component
public class CsvFiles {
    private final Path directory;
    public CsvFiles(StorageProperties storage){directory=storage.root().resolve("exports");}
    public record Prepared(Path path,String filename) {
        public void discard(){try{Files.deleteIfExists(path);}catch(IOException ex){org.slf4j.LoggerFactory.getLogger(CsvFiles.class).warn("CSV temporary file cleanup failed");}}
        public ResponseEntity<Resource> response() {
            var resource=new FileSystemResource(path){
                @Override public InputStream getInputStream() throws IOException {
                    return new FilterInputStream(Files.newInputStream(path)){
                        @Override public void close() throws IOException {try{super.close();}finally{discard();}}
                    };
                }
            };
            return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8))
                    .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString()).body(resource);
        }
    }
    public Prepared generate(String prefix,List<String> header,Consumer<CSVPrinter> write) {
        Path path=null;
        try {
            Files.createDirectories(directory);
            if(!directory.toRealPath().equals(directory.toAbsolutePath().normalize()))throw new IOException("Managed CSV directory cannot contain symbolic links");
            path=Files.createTempFile(directory,prefix+"-",".csv");
            try(var writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8);var csv=new CSVPrinter(writer,CSVFormat.RFC4180)) {
                writer.write('\ufeff');row(csv,header);write.accept(csv);
            }
            return new Prepared(path,prefix+"_"+LocalDate.now(ZoneOffset.UTC)+".csv");
        } catch(IOException|RuntimeException ex) {
            if(path!=null)try{Files.deleteIfExists(path);}catch(IOException ignored){org.slf4j.LoggerFactory.getLogger(getClass()).warn("CSV failed generation cleanup unavailable");}
            if(ex instanceof RuntimeException runtime&&!(ex instanceof UncheckedIOException))throw runtime;
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"CSV 生成失败");
        }
    }
    public static void row(CSVPrinter csv,List<?> values) {
        try {csv.printRecord(values.stream().map(CsvFiles::safe).toList());}
        catch(IOException ex){throw new UncheckedIOException(ex);}
    }
    public static Object safe(Object value) {
        if(!(value instanceof String text))return value;
        int index=0;while(index<text.length()&&(Character.isWhitespace(text.charAt(index))||Character.isISOControl(text.charAt(index))||text.charAt(index)=='\ufeff'))index++;
        if(index<text.length()&&"=+-@".indexOf(text.charAt(index))>=0)return "'"+text;
        // Spreadsheet importers also interpret leading tab/CR as formulas in some modes.
        if(!text.isEmpty()&&(text.charAt(0)=='\t'||text.charAt(0)=='\r'||text.charAt(0)=='\n'))return "'"+text;
        return text;
    }
}
