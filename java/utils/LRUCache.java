package utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU Cache implementation with performance metrics
 * Thread-safe cache with automatic cleanup
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    
    private final int maxSize;
    private final long ttlMillis; // Time to live in milliseconds
    private long hits = 0;
    private long misses = 0;
    private long totalRequests = 0;
    private long evictions = 0;
    
    // Inner class to store cached values with timestamp
    private static class CacheEntry<V> {
        final V value;
        final long timestamp;
        
        CacheEntry(V value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - timestamp > ttlMillis;
        }
    }
    
    // Separate map for cache entries with timestamps
    private final Map<K, CacheEntry<V>> cacheEntries;
    
    public LRUCache(int maxSize, long ttlMillis) {
        super(maxSize + 1, 0.75f, true); // access-order, removeEldestEntry
        this.maxSize = maxSize;
        this.ttlMillis = ttlMillis;
        this.cacheEntries = new LinkedHashMap<>(maxSize + 1, 0.75f, true);
    }
    
    /**
     * Get value from cache
     */
    public synchronized V getWithMetrics(K key) {
        totalRequests++;
        
        CacheEntry<V> entry = cacheEntries.get(key);
        if (entry == null) {
            misses++;
            return null;
        }
        
        // Check if entry is expired
        if (entry.isExpired(ttlMillis)) {
            remove(key);
            misses++;
            return null;
        }
        
        hits++;
        // Update access order by getting from super
        super.get(key);
        return entry.value;
    }
    
    /**
     * Put value in cache
     */
    public synchronized void putWithMetrics(K key, V value) {
        CacheEntry<V> entry = new CacheEntry<>(value);
        cacheEntries.put(key, entry);
        super.put(key, value); // This will trigger removeEldestEntry if needed
    }
    
    /**
     * Remove entry from cache
     */
    @Override
    public synchronized V remove(Object key) {
        cacheEntries.remove(key);
        return super.remove(key);
    }
    
    /**
     * Clear cache
     */
    @Override
    public synchronized void clear() {
        cacheEntries.clear();
        super.clear();
        hits = 0;
        misses = 0;
        totalRequests = 0;
        evictions = 0;
    }
    
    /**
     * Remove expired entries
     */
    public synchronized int cleanupExpired() {
        int removed = 0;
        var iterator = cacheEntries.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired(ttlMillis)) {
                iterator.remove();
                super.remove(entry.getKey());
                removed++;
            }
        }
        return removed;
    }
    
    /**
     * Check if cache contains key (non-expired)
     */
    public synchronized boolean containsKeyNonExpired(K key) {
        CacheEntry<V> entry = cacheEntries.get(key);
        if (entry == null) {
            return false;
        }
        
        if (entry.isExpired(ttlMillis)) {
            remove(key);
            return false;
        }
        
        return true;
    }
    
    /**
     * Get cache size (non-expired entries only)
     */
    public synchronized int getActiveSize() {
        cleanupExpired();
        return size();
    }
    
    /**
     * Override removeEldestEntry for LRU eviction
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        if (size() > maxSize) {
            cacheEntries.remove(eldest.getKey());
            evictions++;
            return true;
        }
        return false;
    }
    
    /**
     * Get performance statistics
     */
    public synchronized CacheStats getStats() {
        cleanupExpired();
        double hitRate = totalRequests > 0 ? (double) hits / totalRequests : 0.0;
        
        return new CacheStats(
            size(),
            maxSize,
            hits,
            misses,
            totalRequests,
            evictions,
            hitRate,
            ttlMillis / 1000.0 // Convert to seconds
        );
    }
    
    /**
     * Print performance statistics
     */
    public synchronized void printStats() {
        CacheStats stats = getStats();
        System.out.println("📊 LRU Cache Statistics:");
        System.out.println("   Active Size: " + stats.activeSize + "/" + stats.maxSize);
        System.out.println("   Hit Rate: " + String.format("%.2f%%", stats.hitRate * 100));
        System.out.println("   Total Requests: " + stats.totalRequests);
        System.out.println("   Cache Hits: " + stats.hits);
        System.out.println("   Cache Misses: " + stats.misses);
        System.out.println("   Evictions: " + stats.evictions);
        System.out.println("   TTL: " + String.format("%.1f seconds", stats.ttlSeconds));
    }
    
    /**
     * Cache statistics data class
     */
    public static class CacheStats {
        public final int activeSize;
        public final int maxSize;
        public final long hits;
        public final long misses;
        public final long totalRequests;
        public final long evictions;
        public final double hitRate;
        public final double ttlSeconds;
        
        public CacheStats(int activeSize, int maxSize, long hits, long misses, 
                         long totalRequests, long evictions, double hitRate, double ttlSeconds) {
            this.activeSize = activeSize;
            this.maxSize = maxSize;
            this.hits = hits;
            this.misses = misses;
            this.totalRequests = totalRequests;
            this.evictions = evictions;
            this.hitRate = hitRate;
            this.ttlSeconds = ttlSeconds;
        }
        
        @Override
        public String toString() {
            return String.format(
                "LRU Cache[size=%d/%d, hit_rate=%.2f%%, hits=%d, misses=%d, evictions=%d]",
                activeSize, maxSize, hitRate * 100, hits, misses, evictions
            );
        }
    }
    
    /**
     * Create route cache key
     */
    public static String createRouteKey(String source, String destination, String mode, int time) {
        return String.format("%s-%s-%s-%d", source, destination, mode, time);
    }
    
    /**
     * Create station cache key
     */
    public static String createStationKey(String station, int time) {
        return String.format("%s-%d", station, time);
    }
    
    /**
     * Create congestion cache key
     */
    public static String createCongestionKey(String station, int time) {
        return String.format("congestion-%s-%d", station, time);
    }
}
