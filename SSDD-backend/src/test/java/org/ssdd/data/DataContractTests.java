package org.ssdd.data;

import org.apache.commons.csv.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ssdd.config.StorageProperties;
import org.ssdd.common.exception.BusinessException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DataContractTests {
    @TempDir Path directory;
    @Test void csvEscapesQuotedMultilineUnicodeAndNeutralizesSpreadsheetFormulas() throws Exception {
        var storage=new CsvFiles(new StorageProperties(directory));
        var prepared=storage.generate("sample",List.of("名称","值"),csv->{
            CsvFiles.row(csv,List.of("钢板,\"测试\"\n下一行","=HYPERLINK(\"x\")"));
            CsvFiles.row(csv,List.of("\t+SUM(1,2)","  @cmd"));CsvFiles.row(csv,List.of(-10,1.25));
        });
        byte[] bytes=Files.readAllBytes(prepared.path());assertArrayEquals(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf},Arrays.copyOf(bytes,3));
        try(var parser=CSVParser.parse(new String(bytes,3,bytes.length-3,StandardCharsets.UTF_8),CSVFormat.RFC4180)) {
            var rows=parser.getRecords();assertEquals(4,rows.size());assertEquals("钢板,\"测试\"\n下一行",rows.get(1).get(0));assertTrue(rows.get(1).get(1).startsWith("'="));
            assertEquals("'\t+SUM(1,2)",rows.get(2).get(0));assertEquals("'  @cmd",rows.get(2).get(1));assertEquals("-10",rows.get(3).get(0));
        }
        try(var input=prepared.response().getBody().getInputStream()){assertTrue(input.readAllBytes().length>0);}
        assertFalse(Files.exists(prepared.path()));
    }
    @Test void csvGenerationFailureDeletesPartialTemporaryFile() throws Exception {
        var storage=new CsvFiles(new StorageProperties(directory));
        assertThrows(IllegalStateException.class,()->storage.generate("failure",List.of("x"),csv->{CsvFiles.row(csv,List.of("before failure"));throw new IllegalStateException("injected");}));
        try(var paths=Files.list(directory.resolve("exports"))){assertEquals(0,paths.count());}
    }
    @Test void validatesDateRangeOffsetsOrderingAndDatabaseBounds() {
        assertEquals(DateRange.parse("2026-11-04T00:00:00Z",null).start(),DateRange.parse("2026-11-04T08:00:00+08:00",null).start());
        for(String date:List.of("2026-11-04","not-a-date","0001-01-01T00:00:00Z"))assertThrows(BusinessException.class,()->DateRange.parse(date,null));
        assertThrows(BusinessException.class,()->DateRange.parse("2026-11-04T00:00:01Z","2026-11-04T00:00:00Z"));
        var args=new ArrayList<Object>();assertEquals(" AND r.created_at>=? AND r.created_at<=?",DateRange.parse("2026-11-04T00:00:00Z","2026-11-04T00:00:00Z").append("r.created_at",args));assertEquals(2,args.size());
    }
}
