package net.bumpier.brankup.data;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface defining the contract for all database operations.
 * All operations are asynchronous and return a CompletableFuture.
 */
public interface IDatabaseService {

    /**
     * Initializes the database connection and creates necessary tables.
     */
    void initialize();

    /**
     * Asynchronously loads a player's data from the database.
     * If the player does not exist, it resolves with a default PlayerRankData object.
     *
     * @param uuid The UUID of the player to load.
     * @return A CompletableFuture that will complete with the player's data.
     */
    CompletableFuture<PlayerRankData> loadPlayerData(UUID uuid);

    /**
     * Asynchronously saves a player's data to the database.
     * This performs an "upsert" (insert or update).
     *
     * @param data The PlayerRankData object to save.
     * @return A CompletableFuture that will complete when the operation is finished.
     */
    CompletableFuture<Void> savePlayerData(PlayerRankData data);

    /**
     * Asynchronously loads the set of claimed one-time reward keys for a player.
     * @param uuid The player's UUID.
     * @return A CompletableFuture that completes with a set of reward keys.
     */
    CompletableFuture<Set<String>> loadClaimedRewards(UUID uuid);

    /**
     * Asynchronously saves a claimed one-time reward for a player to prevent it from being claimed again.
     * @param uuid The player's UUID.
     * @param rewardKey The unique key for the reward.
     * @return A CompletableFuture that completes when the save is finished.
     */
    CompletableFuture<Void> saveClaimedReward(UUID uuid, String rewardKey);

    /**
     * Closes the database connection pool and cleans up resources.
     */
    void shutdown();
}