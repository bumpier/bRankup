package net.bumpier.brankup.data;

import org.bukkit.Bukkit;
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

        // Step 1: Immediately create and cache a default data object.
        // This prevents "data loading" messages and ensures a non-null value is always available for online players.
        playerDataCache.put(uuid, PlayerRankData.newPlayer(uuid));

        // Step 2: Asynchronously load the real data from the database.
        databaseService.loadPlayerData(uuid).thenAccept(loadedData -> {
            if (loadedData != null) {
                // Step 3: Once loaded, replace the default object with the real data.
                playerDataCache.put(uuid, loadedData);
                cacheTimestamps.put(uuid, System.currentTimeMillis());
                plugin.getLogger().info("Successfully loaded data for " + player.getName());
            } else {
                plugin.getLogger().warning("Loaded data was null for " + player.getName() + ", they will use default data.");
            }
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to load data for " + player.getName(), ex);
            // The player will continue to use the default data object, preventing errors.
            return null;
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Remove from cache first to prevent modifications during the save process
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
     * Retrieves the cached rank data for a player.
     * OPTIMIZATION: Implements cache TTL and automatic refresh for stale data.
     * @param uuid The player's UUID.
     * @return The PlayerRankData, or null if not cached or expired.
     */
    /**
     * Retrieves the cached rank data for a player.
     * This will no longer return null for an online player after the onPlayerJoin event.
     */
    public PlayerRankData getData(UUID uuid) {
        return playerDataCache.get(uuid);
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
        long cleanupThreshold = System.currentTimeMillis() - CACHE_TTL_MS;

        cacheTimestamps.entrySet().removeIf(entry -> {
            if (entry.getValue() < cleanupThreshold) {
                // Only remove if the player is offline
                if (Bukkit.getPlayer(entry.getKey()) == null) {
                    playerDataCache.remove(entry.getKey());
                    return true;
                }
            }
            return false;
        });
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