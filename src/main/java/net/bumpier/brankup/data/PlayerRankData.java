package net.bumpier.brankup.data;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents the rank and prestige data for a single player.
 * This object is cached in memory for online players.
 */
public class PlayerRankData {

    private final UUID uuid;
    private long rank;
    private long prestige;
    private boolean autoRankupEnabled;
    private boolean autoPrestigeEnabled;
    private final Set<String> claimedOneTimeRewards;

    public PlayerRankData(UUID uuid, long rank, long prestige, boolean autoRankupEnabled, boolean autoPrestigeEnabled, Set<String> claimedRewards) {
        this.uuid = uuid;
        this.rank = rank;
        this.prestige = prestige;
        this.autoRankupEnabled = autoRankupEnabled;
        this.autoPrestigeEnabled = autoPrestigeEnabled;
        this.claimedOneTimeRewards = claimedRewards != null ? claimedRewards : new HashSet<>();
    }

    public static PlayerRankData newPlayer(UUID uuid) {
        return new PlayerRankData(uuid, 0, 0, false, false, new HashSet<>());
    }

    public Set<String> getClaimedOneTimeRewards() {
        return claimedOneTimeRewards;
    }

    public void addClaimedReward(String rewardKey) {
        this.claimedOneTimeRewards.add(rewardKey);
    }

    public UUID getUuid() {
        return uuid;
    }

    public long getRank() {
        return rank;
    }

    public void setRank(long rank) {
        this.rank = rank;
    }

    public void incrementRank() {
        this.rank++;
    }

    public long getPrestige() {
        return prestige;
    }

    public void setPrestige(long prestige) {
        this.prestige = prestige;
    }

    public boolean isAutoRankupEnabled() {
        return autoRankupEnabled;
    }

    public void setAutoRankupEnabled(boolean autoRankupEnabled) {
        this.autoRankupEnabled = autoRankupEnabled;
    }

    public void incrementPrestige() {
        this.prestige++;
    }

    public boolean isAutoPrestigeEnabled() {
        return autoPrestigeEnabled;
    }

    public void setAutoPrestigeEnabled(boolean autoPrestigeEnabled) {
        this.autoPrestigeEnabled = autoPrestigeEnabled;
    }
}