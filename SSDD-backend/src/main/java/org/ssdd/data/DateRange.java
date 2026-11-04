package org.ssdd.data;

import org.ssdd.detection.DetectionErrors;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;

/** ISO instants with explicit offset; both endpoints are inclusive at MySQL microsecond precision. */
public record DateRange(Instant start,Instant end) {
    public static DateRange parse(String start,String end) {
        try {
            Instant a=parseOne(start),b=parseOne(end);
            if(a!=null&&b!=null&&a.isAfter(b))throw DetectionErrors.bad("开始时间不能晚于结束时间");
            return new DateRange(a,b);
        } catch(DateTimeParseException ex) {throw DetectionErrors.bad("日期必须是带时区的 ISO 8601 时间");}
    }
    private static Instant parseOne(String value) {
        if(value==null||value.isBlank())return null;
        if(value.length()>64)throw DetectionErrors.bad("日期参数过长");
        Instant instant=OffsetDateTime.parse(value).toInstant();
        if(instant.isBefore(Instant.parse("1000-01-01T00:00:00Z"))||instant.isAfter(Instant.parse("9999-12-31T23:59:59Z")))throw DetectionErrors.bad("日期超出数据库支持范围");
        return instant;
    }
    public String append(String column,List<Object> args) {
        String sql="";
        if(start!=null){sql+=" AND "+column+">=?";args.add(Timestamp.from(start));}
        if(end!=null){sql+=" AND "+column+"<=?";args.add(Timestamp.from(end));}
        return sql;
    }
}
