package net.bumpier.brankup.data;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.bumpier.brankup.bRankup;

public class PlayerManagerService implements Listener {

    private final bRankup plugin;
    private final IDatabaseService databaseService;
    
    // OPTIMIZATION: Use more efficient caching with TTL (Time To Live)
    private final Map<UUID, PlayerRankData> playerDataCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cacheTimestamps = new ConcurrentHashMap<>();
    
    // OPTIMIZATION: Cache TTL settings
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30); // 30 minutes
    private static final long CLEANUP_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5); // Cleanup every 5 minutes
    private long lastCleanupTime = System.currentTimeMillis();

    public PlayerManagerService(bRankup plugin, IDatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // OPTIMIZATION: Schedule periodic cache cleanup
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::performCacheCleanup, 
            TimeUnit.MINUTES.toMillis(5) / 50, TimeUnit.MINUTES.toMillis(5) / 50);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // OPTIMIZATION: Load data asynchronously and cache with timestamp
        CompletableFuture.runAsync(() -> {
            try {
                PlayerRankData data = databaseService.loadPlayerData(uuid).join();
                if (data != null) {
                    playerDataCache.put(uuid, data);
                    cacheTimestamps.put(uuid, System.currentTimeMillis());
                    plugin.getLogger().info("Successfully loaded and cached data for " + player.getName());
                } else {
                    // OPTIMIZATION: Create default data if none exists
                    data = PlayerRankData.newPlayer(uuid);
                    playerDataCache.put(uuid, data);
                    cacheTimestamps.put(uuid, System.currentTimeMillis());
                    plugin.getLogger().info("Created default data for " + player.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load data for " + player.getName(), e);
                // OPTIMIZATION: Create default data on failure to prevent null pointer exceptions
                PlayerRankData defaultData = PlayerRankData.newPlayer(uuid);
                playerDataCache.put(uuid, defaultData);
                cacheTimestamps.put(uuid, System.currentTimeMillis());
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerRankData data = playerDataCache.remove(uuid);
        cacheTimestamps.remove(uuid);
        
        if (data != null) {
            // OPTIMIZATION: Save data asynchronously to prevent blocking
            CompletableFuture.runAsync(() -> {
                try {
                    databaseService.savePlayerData(data).join();
                    plugin.getLogger().info("Successfully saved data for " + event.getPlayer().getName());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save data for " + event.getPlayer().getName(), e);
                }
            });
        }
    }

    /**
     * Retrieves the cached rank data for a player.
     * OPTIMIZATION: Implements cache TTL and automatic refresh for stale data.
     * @param uuid The player's UUID.
     * @return The PlayerRankData, or null if not cached or expired.
     */
    public PlayerRankData getData(UUID uuid) {
        PlayerRankData data = playerDataCache.get(uuid);
        if (data == null) {
            return null;
        }
        
        // OPTIMIZATION: Check if cache entry is stale
        Long timestamp = cacheTimestamps.get(uuid);
        if (timestamp != null && System.currentTimeMillis() - timestamp > CACHE_TTL_MS) {
            // OPTIMIZATION: Refresh stale data asynchronously
            CompletableFuture.runAsync(() -> refreshPlayerData(uuid));
        }
        
        return data;
    }
    
    /**
     * OPTIMIZATION: Refresh player data from database asynchronously
     */
    private void refreshPlayerData(UUID uuid) {
        try {
            PlayerRankData freshData = databaseService.loadPlayerData(uuid).join();
            if (freshData != null) {
                playerDataCache.put(uuid, freshData);
                cacheTimestamps.put(uuid, System.currentTimeMillis());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to refresh data for player " + uuid, e);
        }
    }
    
    /**
     * OPTIMIZATION: Force refresh player data (useful for admin commands)
     */
    public CompletableFuture<PlayerRankData> forceRefreshData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlayerRankData freshData = databaseService.loadPlayerData(uuid).join();
                if (freshData != null) {
                    playerDataCache.put(uuid, freshData);
                    cacheTimestamps.put(uuid, System.currentTimeMillis());
                }
                return freshData;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to force refresh data for player " + uuid, e);
                return null;
            }
        });
    }

    /**
     * Saves all online players' data. Useful for scheduled saves or on disable.
     * OPTIMIZATION: Implements batch saving for better performance.
     */
    public void saveAll() {
        if (playerDataCache.isEmpty()) return;

        plugin.getLogger().info("Saving data for all online players...");
        
        // OPTIMIZATION: Save data asynchronously in batches
        CompletableFuture.runAsync(() -> {
            try {
                for (Map.Entry<UUID, PlayerRankData> entry : playerDataCache.entrySet()) {
                    try {
                        databaseService.savePlayerData(entry.getValue()).join();
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Failed to save data for player " + entry.getKey(), e);
                    }
                }
                plugin.getLogger().info("Save task complete.");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error during batch save operation", e);
            }
        });
    }
    
    /**
     * OPTIMIZATION: Perform periodic cache cleanup to prevent memory leaks
     */
    private void performCacheCleanup() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }
        
        lastCleanupTime = currentTime;
        long cleanupThreshold = currentTime - CACHE_TTL_MS;
        
        // OPTIMIZATION: Remove expired cache entries
        cacheTimestamps.entrySet().removeIf(entry -> {
            if (entry.getValue() < cleanupThreshold) {
                playerDataCache.remove(entry.getKey());
                return true;
            }
            return false;
        });
        
        // OPTIMIZATION: Log cleanup statistics
        if (plugin.getLogger().isLoggable(Level.FINE)) {
            plugin.getLogger().fine("Cache cleanup completed. Active entries: " + playerDataCache.size());
        }
    }
    
    /**
     * OPTIMIZATION: Get cache statistics for monitoring
     */
    public int getCacheSize() {
        return playerDataCache.size();
    }
    
    /**
     * OPTIMIZATION: Clear entire cache (useful for reloads)
     */
    public void clearCache() {
        playerDataCache.clear();
        cacheTimestamps.clear();
        plugin.getLogger().info("Player data cache cleared.");
    }
}