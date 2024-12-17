package client;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class LoggerManager {
    private static LoggerManager instance = null;
    private Logger logger;

    private LoggerManager(Class<?> clazz) {
        logger = Logger.getLogger(clazz);
        PropertyConfigurator.configure("resources/log4j.properties");
    }

    public static LoggerManager getInstance(Class<?> clazz) {
        if (instance == null) {
            instance = new LoggerManager(clazz);
        }
        return instance;
    }

    public void debug(String message) {
        logger.debug(message);
    }

    public void info(String message) {
        logger.info(message);
    }

    public void warn(String message) {
        logger.warn(message);
    }

    public void error(String message) {
        logger.error(message);
    }

    public void trace(String message) {
        logger.trace(message);
    }
} 