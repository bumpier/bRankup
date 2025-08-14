package net.bumpier.brankup.util;

import net.bumpier.brankup.bRankup;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Performance monitoring utility for bRankup plugin.
 * Tracks various performance metrics to help identify bottlenecks.
 */
public class PerformanceMonitor {
    
    private final bRankup plugin;
    private final Logger logger;
    
    // Performance metrics
    private final AtomicLong totalAutoProgressionChecks = new AtomicLong(0);
    private final AtomicLong totalRankups = new AtomicLong(0);
    private final AtomicLong totalPrestiges = new AtomicLong(0);
    private final AtomicLong totalCostCalculations = new AtomicLong(0);
    private final AtomicLong totalDatabaseOperations = new AtomicLong(0);
    
    // Timing metrics
    private final Map<String, Long> operationStartTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> operationDurations = new ConcurrentHashMap<>();
    
    // Cache performance
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    // Memory usage tracking
    private final AtomicLong peakMemoryUsage = new AtomicLong(0);
    
    public PerformanceMonitor(bRankup plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        
        // Initialize operation duration trackers
        operationDurations.put("auto-progression", new AtomicLong(0));
        operationDurations.put("cost-calculation", new AtomicLong(0));
        operationDurations.put("database-operation", new AtomicLong(0));
        operationDurations.put("reward-dispatching", new AtomicLong(0));
        
        // Start periodic performance reporting
        startPerformanceReporting();
    }
    
    /**
     * Start timing an operation
     */
    public void startOperation(String operationName) {
        operationStartTimes.put(operationName, System.nanoTime());
    }
    
    /**
     * End timing an operation and record duration
     */
    public void endOperation(String operationName) {
        Long startTime = operationStartTimes.remove(operationName);
        if (startTime != null) {
            long duration = System.nanoTime() - startTime;
            AtomicLong durationTracker = operationDurations.get(operationName);
            if (durationTracker != null) {
                durationTracker.addAndGet(duration);
            }
        }
    }
    
    /**
     * Record an auto-progression check
     */
    public void recordAutoProgressionCheck() {
        totalAutoProgressionChecks.incrementAndGet();
    }
    
    /**
     * Record a successful rankup
     */
    public void recordRankup() {
        totalRankups.incrementAndGet();
    }
    
    /**
     * Record a successful prestige
     */
    public void recordPrestige() {
        totalPrestiges.incrementAndGet();
    }
    
    /**
     * Record a cost calculation
     */
    public void recordCostCalculation() {
        totalCostCalculations.incrementAndGet();
    }
    
    /**
     * Record a database operation
     */
    public void recordDatabaseOperation() {
        totalDatabaseOperations.incrementAndGet();
    }
    
    /**
     * Record a cache hit
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }
    
    /**
     * Record a cache miss
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }
    
    /**
     * Update peak memory usage
     */
    public void updateMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long currentMemory = runtime.totalMemory() - runtime.freeMemory();
        long currentPeak = peakMemoryUsage.get();
        
        while (currentMemory > currentPeak && !peakMemoryUsage.compareAndSet(currentPeak, currentMemory)) {
            currentPeak = peakMemoryUsage.get();
        }
    }
    
    /**
     * Get cache hit ratio
     */
    public double getCacheHitRatio() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        
        return total > 0 ? (double) hits / total : 0.0;
    }
    
    /**
     * Get average operation duration in milliseconds
     */
    public double getAverageOperationDuration(String operationName) {
        AtomicLong durationTracker = operationDurations.get(operationName);
        if (durationTracker == null) return 0.0;
        
        long totalDuration = durationTracker.get();
        long operationCount = getOperationCount(operationName);
        
        return operationCount > 0 ? (totalDuration / 1_000_000.0) / operationCount : 0.0;
    }
    
    /**
     * Get operation count based on operation name
     */
    private long getOperationCount(String operationName) {
        return switch (operationName) {
            case "auto-progression" -> totalAutoProgressionChecks.get();
            case "cost-calculation" -> totalCostCalculations.get();
            case "database-operation" -> totalDatabaseOperations.get();
            case "reward-dispatching" -> totalRankups.get() + totalPrestiges.get();
            default -> 0;
        };
    }
    
    /**
     * Start periodic performance reporting
     */
    private void startPerformanceReporting() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (logger.isLoggable(java.util.logging.Level.FINE)) {
                    reportPerformance();
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60, 20L * 60); // Report every minute
    }
    
    /**
     * Generate performance report
     */
    private void reportPerformance() {
        updateMemoryUsage();
        
        StringBuilder report = new StringBuilder();
        report.append("=== bRankup Performance Report ===\n");
        report.append("Auto-progression checks: ").append(totalAutoProgressionChecks.get()).append("\n");
        report.append("Total rankups: ").append(totalRankups.get()).append("\n");
        report.append("Total prestiges: ").append(totalPrestiges.get()).append("\n");
        report.append("Cost calculations: ").append(totalCostCalculations.get()).append("\n");
        report.append("Database operations: ").append(totalDatabaseOperations.get()).append("\n");
        report.append("Cache hit ratio: ").append(String.format("%.2f%%", getCacheHitRatio() * 100)).append("\n");
        report.append("Peak memory usage: ").append(String.format("%.2f MB", peakMemoryUsage.get() / 1024.0 / 1024.0)).append("\n");
        
        // Operation timing
        report.append("\n--- Operation Timing (ms) ---\n");
        for (String operation : operationDurations.keySet()) {
            double avgDuration = getAverageOperationDuration(operation);
            report.append(operation).append(": ").append(String.format("%.3f", avgDuration)).append("\n");
        }
        
        logger.fine(report.toString());
    }
    
    /**
     * Reset all performance metrics
     */
    public void resetMetrics() {
        totalAutoProgressionChecks.set(0);
        totalRankups.set(0);
        totalPrestiges.set(0);
        totalCostCalculations.set(0);
        totalDatabaseOperations.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        peakMemoryUsage.set(0);
        
        for (AtomicLong duration : operationDurations.values()) {
            duration.set(0);
        }
        
        logger.info("Performance metrics have been reset.");
    }
    
    /**
     * Get current performance summary for commands
     */
    public String getPerformanceSummary() {
        updateMemoryUsage();
        
        return String.format(
            "Performance Summary:\n" +
            "• Auto-progression checks: %d\n" +
            "• Total rankups: %d\n" +
            "• Total prestiges: %d\n" +
            "• Cache hit ratio: %.1f%%\n" +
            "• Peak memory: %.1f MB",
            totalAutoProgressionChecks.get(),
            totalRankups.get(),
            totalPrestiges.get(),
            getCacheHitRatio() * 100,
            peakMemoryUsage.get() / 1024.0 / 1024.0
        );
    }
} 