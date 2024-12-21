package client;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple logging manager that provides basic logging functionality
 * with different log levels and timestamp formatting.
 */
public class loggerManager {
    // Singleton instance
    private static Map<Class<?>, loggerManager> instances = new HashMap<>();
    
    // Log levels
    public static final int ERROR = 1;
    public static final int WARN = 2;
    public static final int INFO = 3;
    public static final int DEBUG = 4;
    public static final int TRACE = 5;
    
    private static int currentLogLevel = DEBUG; // Default log level
    private final Class<?> loggerClass;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    private loggerManager(Class<?> clazz) {
        this.loggerClass = clazz;
    }
    
    /**
     * Gets or creates a logger instance for the specified class
     */
    public static synchronized loggerManager getInstance(Class<?> clazz) {
        return instances.computeIfAbsent(clazz, k -> new loggerManager(k));
    }
    
    /**
     * Sets the global log level
     */
    public static void setLogLevel(int level) {
        currentLogLevel = level;
    }
    
    /**
     * Internal method to format and print log messages
     */
    private void log(int level, String message, PrintStream stream) {
        if (level <= currentLogLevel) {
            String levelStr = getLevelString(level);
            String timestamp = dateFormat.format(new Date());
            stream.println(String.format("%s [%s] %s - %s", 
                timestamp, levelStr, loggerClass.getSimpleName(), message));
        }
    }
    
    /**
     * Converts log level to string representation
     */
    private String getLevelString(int level) {
        switch (level) {
            case ERROR: return "ERROR";
            case WARN:  return "WARN ";
            case INFO:  return "INFO ";
            case DEBUG: return "DEBUG";
            case TRACE: return "TRACE";
            default:    return "?????";
        }
    }
    
    /**
     * Log methods for different levels
     */
    public void error(String message) {
        log(ERROR, message, System.err);
    }
    
    public void warn(String message) {
        log(WARN, message, System.out);
    }
    
    public void info(String message) {
        log(INFO, message, System.out);
    }
    
    public void debug(String message) {
        log(DEBUG, message, System.out);
    }
    
    public void trace(String message) {
        log(TRACE, message, System.out);
    }
} 