package net.bumpier.brankup.data;

import net.bumpier.brankup.bRankup;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class PlayerManagerService implements Listener {

    private final bRankup plugin;
    private final IDatabaseService databaseService;

    // Dedicated executor for player data operations
    private final ExecutorService executor = Executors.newFixedThreadPool(2, 
            r -> new Thread(r, "bRankup-PlayerData-Thread"));

    private final Map<UUID, PlayerRankData> playerDataCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<PlayerRankData>> loadingFutures = new ConcurrentHashMap<>();

    // Cache metrics
    private long cacheHits = 0;
    private long cacheMisses = 0;

    // Cache settings
    private final long cacheTtlMs;
    private final int maxCachedPlayers;

    public PlayerManagerService(bRankup plugin, IDatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;

        // Load cache settings from config
        this.cacheTtlMs = TimeUnit.MINUTES.toMillis(
            plugin.getConfigManager().getMainConfig().getLong("performance.cache.player-data-ttl", 30));
        this.maxCachedPlayers = plugin.getConfigManager().getMainConfig().getInt("performance.cache.max-cached-players", 0);

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Calculate cleanup interval from config
        long cleanupIntervalMinutes = plugin.getConfigManager().getMainConfig().getLong("performance.cache.cleanup-interval", 5);
        long cleanupIntervalTicks = TimeUnit.MINUTES.toSeconds(cleanupIntervalMinutes) * 20L;

        // Schedule cleanup task
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(
            plugin, this::performCacheCleanup, cleanupIntervalTicks, cleanupIntervalTicks);

        plugin.getLogger().info("Player data cache initialized with TTL: " + 
            TimeUnit.MILLISECONDS.toMinutes(cacheTtlMs) + " minutes, max players: " + 
            (maxCachedPlayers == 0 ? "unlimited" : maxCachedPlayers));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        getOrLoadData(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        CompletableFuture<PlayerRankData> loadingFuture = loadingFutures.remove(uuid);
        if (loadingFuture != null) {
            loadingFuture.cancel(true);
        }

        PlayerRankData data = playerDataCache.remove(uuid);
        cacheTimestamps.remove(uuid);
        if (data != null) {
            databaseService.savePlayerData(data).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to save data for " + event.getPlayer().getName(), ex);
                return null;
            });
        }
    }

    /**
     * Synchronously gets player data from the cache.
     * If data is not available, it returns a temporary default object to prevent
     * placeholders from getting stuck on "Loading..." and triggers an async load.
     *
     * @param uuid The player's UUID.
     * @return A non-null PlayerRankData object (either cached or a temporary default).
     */
    public PlayerRankData getDataSynchronously(UUID uuid) {
        PlayerRankData data = playerDataCache.get(uuid);
        if (data == null) {
            // Data isn't loaded. Trigger an async load in the background.
            getOrLoadData(uuid);
            // Return a temporary, non-null object for now.
            return PlayerRankData.newPlayer(uuid);
        }
        return data;
    }

    public CompletableFuture<PlayerRankData> getOrLoadData(UUID uuid) {
        // Track performance
        plugin.getPerformanceMonitor().startOperation("player-data-load");

        // Check if we're already loading this player's data
        CompletableFuture<PlayerRankData> loadingFuture = loadingFutures.get(uuid);
        if (loadingFuture != null) {
            plugin.getPerformanceMonitor().endOperation("player-data-load");
            return loadingFuture;
        }

        // Check if data is in cache and not expired
        PlayerRankData cachedData = playerDataCache.get(uuid);
        if (cachedData != null) {
            Long timestamp = cacheTimestamps.get(uuid);
            if (timestamp != null && (System.currentTimeMillis() - timestamp) < cacheTtlMs) {
                // Cache hit
                cacheHits++;
                plugin.getPerformanceMonitor().recordCacheHit();
                plugin.getPerformanceMonitor().endOperation("player-data-load");
                return CompletableFuture.completedFuture(cachedData);
            }
        }

        // Cache miss - need to load from database
        cacheMisses++;
        plugin.getPerformanceMonitor().recordCacheMiss();

        CompletableFuture<PlayerRankData> newLoadFuture = CompletableFuture.supplyAsync(() -> {
            try {
                // Load data from database
                PlayerRankData data = databaseService.loadPlayerData(uuid).join();
                if (data == null) {
                    data = PlayerRankData.newPlayer(uuid);
                }

                // Update cache
                playerDataCache.put(uuid, data);
                cacheTimestamps.put(uuid, System.currentTimeMillis());

                // Enforce max cache size if needed
                if (maxCachedPlayers > 0 && playerDataCache.size() > maxCachedPlayers) {
                    performCacheCleanup();
                }

                return data;
            } finally {
                loadingFutures.remove(uuid);
                plugin.getPerformanceMonitor().endOperation("player-data-load");
            }
        }, executor); // Use our dedicated executor

        loadingFutures.put(uuid, newLoadFuture);
        return newLoadFuture;
    }

    /**
     * Gets player data ONLY if it is already in the cache. Does not trigger a database load.
     * @param uuid The player's UUID.
     * @return The cached PlayerRankData, or null if not in cache.
     */
    public PlayerRankData getData(UUID uuid) {
        return playerDataCache.get(uuid);
    }

    /**
     * Saves all cached player data to the database in an optimized batch operation.
     * This method is called during plugin shutdown or reload.
     */
    public void saveAll() {
        if (playerDataCache.isEmpty()) return;

        plugin.getLogger().info("Saving data for " + playerDataCache.size() + " cached players...");
        plugin.getPerformanceMonitor().startOperation("batch-save");

        // Create a list of futures to track completion
        List<CompletableFuture<Void>> saveFutures = new ArrayList<>();

        // Save each player's data
        for (PlayerRankData data : playerDataCache.values()) {
            saveFutures.add(databaseService.savePlayerData(data));
        }

        // Wait for all saves to complete
        CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0])).join();

        plugin.getPerformanceMonitor().endOperation("batch-save");
        plugin.getLogger().info("All player data saved successfully.");
    }

    /**
     * Shuts down the player manager service, saving all data and cleaning up resources.
     * This should be called when the plugin is disabled.
     */
    public void shutdown() {
        // Save all player data
        saveAll();

        // Shutdown the executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clear caches
        clearCache();

        // Log cache statistics
        double hitRatio = cacheHits + cacheMisses > 0 
            ? (double) cacheHits / (cacheHits + cacheMisses) * 100 
            : 0;
        plugin.getLogger().info(String.format(
            "Cache statistics - Hits: %d, Misses: %d, Ratio: %.1f%%", 
            cacheHits, cacheMisses, hitRatio));
    }

    private void performCacheCleanup() {
        long cleanupThreshold = System.currentTimeMillis() - cacheTtlMs;

        // Track performance metrics
        plugin.getPerformanceMonitor().startOperation("cache-cleanup");

        // First, remove expired entries
        cacheTimestamps.entrySet().removeIf(entry -> {
            if (entry.getValue() < cleanupThreshold) {
                playerDataCache.remove(entry.getKey());
                return true;
            }
            return false;
        });

        // Then, if we have a max cache size, trim down to that size
        if (maxCachedPlayers > 0 && playerDataCache.size() > maxCachedPlayers) {
            // Sort entries by timestamp (oldest first)
            cacheTimestamps.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(playerDataCache.size() - maxCachedPlayers)
                .forEach(entry -> {
                    playerDataCache.remove(entry.getKey());
                    cacheTimestamps.remove(entry.getKey());
                });
        }

        plugin.getPerformanceMonitor().endOperation("cache-cleanup");
    }

    public void clearCache() {
        playerDataCache.clear();
        cacheTimestamps.clear();
        loadingFutures.clear();
        plugin.getLogger().info("Player data cache cleared.");
    }
}
