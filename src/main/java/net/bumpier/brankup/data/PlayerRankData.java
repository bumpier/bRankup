package net.bumpier.brankup.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerRankData {

    private final UUID uuid;
    private final Map<String, Long> progressionLevels = new ConcurrentHashMap<>();
    private final Set<String> claimedOneTimeRewards;
    private final Map<String, Boolean> autoProgressionStates = new ConcurrentHashMap<>();

    public PlayerRankData(UUID uuid, Map<String, Long> levels, Set<String> claimedRewards, Map<String, Boolean> autoStates) {
        this.uuid = uuid;
        if (levels != null) {
            this.progressionLevels.putAll(levels);
        }
        this.claimedOneTimeRewards = claimedRewards != null ? claimedRewards : new HashSet<>();
        if (autoStates != null) {
            this.autoProgressionStates.putAll(autoStates);
        }
    }

    public static PlayerRankData newPlayer(UUID uuid) {
        Map<String, Long> defaultLevels = new HashMap<>();
        defaultLevels.put("rankup", 0L);
        return new PlayerRankData(uuid, defaultLevels, new HashSet<>(), new HashMap<>());
    }

    public long getProgressionLevel(String progressionId) {
        return progressionLevels.getOrDefault(progressionId, 0L);
    }

    public void setProgressionLevel(String progressionId, long level) {
        progressionLevels.put(progressionId, level);
    }

    public void incrementProgressionLevel(String progressionId) {
        progressionLevels.merge(progressionId, 1L, Long::sum);
    }

    public boolean isAutoProgressionEnabled(String progressionId) {
        return autoProgressionStates.getOrDefault(progressionId, false);
    }

    public void setAutoProgressionEnabled(String progressionId, boolean enabled) {
        autoProgressionStates.put(progressionId, enabled);
    }

    public Map<String, Long> getAllProgressionLevels() {
        return progressionLevels;
    }

    // Legacy getters for backward compatibility with PAPI expansion
    public long getRank() { return getProgressionLevel("rankup"); }
    public void setRank(long level) { setProgressionLevel("rankup", level); }
    public void incrementRank() { incrementProgressionLevel("rankup"); }
    public long getPrestige() { return getProgressionLevel("prestige"); }
    public void setPrestige(long level) { setProgressionLevel("prestige", level); }
    public void incrementPrestige() { incrementProgressionLevel("prestige"); }

    public Set<String> getClaimedOneTimeRewards() { return claimedOneTimeRewards; }
    public void addClaimedReward(String rewardKey) { this.claimedOneTimeRewards.add(rewardKey); }
    public UUID getUuid() { return uuid; }
}