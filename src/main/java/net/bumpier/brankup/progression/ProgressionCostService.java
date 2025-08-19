package net.bumpier.brankup.progression;

import net.bumpier.brankup.bRankup;
import net.bumpier.brankup.data.PlayerRankData;
import org.bukkit.configuration.ConfigurationSection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Collections;
import java.util.LinkedHashMap;

public class ProgressionCostService {

    private final String calculationMode;
    private final BigDecimal linearBaseCost;
    private final BigDecimal linearCostPerLevel;
    private final BigDecimal exponentialBaseCost;
    private final double exponentialMultiplier;

    // REFACTORED: Generic scaling settings
    private final boolean scalingEnabled;
    private final String scalingType;
    private final String scalingMode;
    private final double scalingValue;

    // Cache with bounded size and LRU eviction policy
    private final Map<String, BigDecimal> costCache;
    private final int maxCacheSize;
    private final BigDecimal scalingMultiplier;

    // Cache metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    // Pre-computed powers for common exponents (optimization for exponential calculation)
    private final Map<Integer, Double> powerCache = new ConcurrentHashMap<>();

    public ProgressionCostService(ConfigurationSection currencySettings) {
        this.calculationMode = currencySettings.getString("calculation-mode", "EXPONENTIAL").toUpperCase();
        this.linearBaseCost = new BigDecimal(currencySettings.getString("linear.base-cost", "1000"));
        this.linearCostPerLevel = new BigDecimal(currencySettings.getString("linear.cost-per-level", "1000"));
        this.exponentialBaseCost = new BigDecimal(currencySettings.getString("exponential.base-cost", "1000"));
        this.exponentialMultiplier = currencySettings.getDouble("exponential.cost-multiplier", 1.15);

        // REFACTORED: Read generic scaling config
        ConfigurationSection scalingConfig = currencySettings.getConfigurationSection("cost-scaling");
        if (scalingConfig != null) {
            this.scalingEnabled = scalingConfig.getBoolean("enabled", false);
            this.scalingType = scalingConfig.getString("scale-with");
            this.scalingMode = scalingConfig.getString("mode", "MULTIPLIER").toUpperCase();
            this.scalingValue = scalingConfig.getDouble("value", 0.0);
        } else {
            this.scalingEnabled = false;
            this.scalingType = null;
            this.scalingMode = "MULTIPLIER";
            this.scalingValue = 0.0;
        }

        this.scalingMultiplier = BigDecimal.valueOf(this.scalingValue);

        // Initialize cache with bounded size and LRU eviction policy
        this.maxCacheSize = currencySettings.getInt("max-cache-size", 1000);

        // Create a LinkedHashMap with access-order that automatically removes oldest entries when size exceeds maxCacheSize
        this.costCache = Collections.synchronizedMap(new LinkedHashMap<String, BigDecimal>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, BigDecimal> eldest) {
                return size() > maxCacheSize;
            }
        });

        // Pre-compute powers for common exponents (0-50)
        for (int i = 0; i <= 50; i++) {
            powerCache.put(i, Math.pow(exponentialMultiplier, i));
        }
    }

    /**
     * Calculates the cost for the next level, optionally applying dynamic scaling.
     * @param currentLevel The current level of this progression type.
     * @param playerData The full data object for the player, used to get scaling levels.
     * @return The final calculated cost.
     */
    public BigDecimal getCost(long currentLevel, PlayerRankData playerData) {
        // Cache key now needs to incorporate the scaling level to be accurate
        long scalingLevel = (scalingEnabled && scalingType != null) ? playerData.getProgressionLevel(scalingType) : 0;
        long cacheKey = (currentLevel << 32) | scalingLevel;

        if (costCache.containsKey(cacheKey)) {
            return costCache.get(cacheKey);
        }

        BigDecimal baseCost = switch (calculationMode) {
            case "LINEAR" -> calculateLinearCost(currentLevel);
            case "EXPONENTIAL" -> calculateExponentialCost(currentLevel);
            default -> BigDecimal.ZERO;
        };

        BigDecimal finalCost;
        if (scalingEnabled && scalingType != null && scalingLevel > 0) {
            finalCost = applyScaling(baseCost, scalingLevel);
        } else {
            finalCost = baseCost;
        }

        costCache.put(String.valueOf(cacheKey), finalCost);
        return finalCost;
    }

    public void clearCache() {
        costCache.clear();
    }

    private BigDecimal applyScaling(BigDecimal baseCost, long scalingLevel) {
        if ("MULTIPLIER".equals(scalingMode)) {
            // Formula: baseCost * (1 + (scalingLevel * value))
            BigDecimal multiplier = BigDecimal.ONE.add(scalingMultiplier.multiply(BigDecimal.valueOf(scalingLevel)));
            return baseCost.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
        } else if ("ADDITIVE".equals(scalingMode)) {
            // Formula: baseCost + (scalingLevel * value)
            BigDecimal addition = scalingMultiplier.multiply(BigDecimal.valueOf(scalingLevel));
            return baseCost.add(addition).setScale(0, RoundingMode.HALF_UP);
        }
        return baseCost;
    }

    private BigDecimal calculateLinearCost(long currentLevel) {
        BigDecimal levelMultiplier = linearCostPerLevel.multiply(BigDecimal.valueOf(currentLevel));
        return linearBaseCost.add(levelMultiplier);
    }

    private BigDecimal calculateExponentialCost(long currentLevel) {
        double result = exponentialBaseCost.doubleValue() * Math.pow(exponentialMultiplier, currentLevel);
        return BigDecimal.valueOf(result).setScale(0, RoundingMode.HALF_UP);
    }
}
