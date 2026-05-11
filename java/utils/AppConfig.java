package utils;

/**
 * Centralized configuration for the Metro Route Optimization System.
 * Follows the Singleton pattern for global access to system settings.
 */
public class AppConfig {
    
    // Server Configuration
    public static final int JAVA_API_PORT = 8081;
    public static final String ML_SERVICE_BASE_URL = "http://localhost:5000/api";
    
    // Caching Configuration
    public static final int CACHE_MAX_SIZE = 500;
    public static final long CACHE_EXPIRY_MS = 1000 * 60 * 15; // 15 minutes
    
    // Performance Thresholds
    public static final long ROUTE_TIME_THRESHOLD_MS = 50;
    public static final long ML_TIME_THRESHOLD_MS = 200;
    
    // Algorithm Settings
    public static final int ASTAR_SWITCH_THRESHOLD = 30; // Number of stations
    public static final double DEFAULT_CONGESTION_FACTOR = 1.0;
    
    // Feature Flags
    public static final boolean ENABLE_CACHE = true;
    public static final boolean ENABLE_ML_FALLBACK = true;
    public static final boolean DEBUG_MODE = true;

    private AppConfig() {
        // Prevent instantiation
    }
}
