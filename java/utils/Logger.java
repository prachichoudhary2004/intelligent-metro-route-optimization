package utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Professional structured logging utility.
 */
public class Logger {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public enum Level {
        INFO, WARN, ERROR, DEBUG, METRICS
    }

    public static void log(Level level, String component, String message) {
        String timestamp = LocalDateTime.now().format(formatter);
        String threadName = Thread.currentThread().getName();
        
        System.out.printf("[%s] [%-5s] [%s] [%s] %s%n", 
            timestamp, level, threadName, component, message);
    }

    public static void info(String component, String message) {
        log(Level.INFO, component, message);
    }

    public static void warn(String component, String message) {
        log(Level.WARN, component, message);
    }

    public static void error(String component, String message) {
        log(Level.ERROR, component, message);
    }

    public static void debug(String component, String message) {
        if (AppConfig.DEBUG_MODE) {
            log(Level.DEBUG, component, message);
        }
    }

    public static void metrics(String component, String metricName, long value, String unit) {
        log(Level.METRICS, component, String.format("%s: %d %s", metricName, value, unit));
    }
}
