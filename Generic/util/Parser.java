package Generic.util;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

public class Parser {
    public static Date ToDate(String dateString) throws Exception {
        return ToDate(dateString, "uuuu-MM-dd");
    }

    public static Date ToDate(String dateString, String format) throws Exception {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format)
                .withResolverStyle(ResolverStyle.STRICT);
        LocalDate localDate = LocalDate.parse(dateString, formatter);
        return Date.valueOf(localDate);
    }

    public static Timestamp ToTimestamp(String timestampString) {
        return ToTimestamp(timestampString, "uuuu-MM-dd HH:mm:ss");
    }

    public static Timestamp ToTimestamp(String timestampString, String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format, Locale.FRANCE)
                .withResolverStyle(ResolverStyle.STRICT);
        LocalDateTime localDateTime = LocalDateTime.parse(timestampString, formatter);
        return Timestamp.valueOf(localDateTime);
    }
}
