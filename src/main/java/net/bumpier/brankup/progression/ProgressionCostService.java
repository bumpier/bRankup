package net.bumpier.brankup.progression;

import org.bukkit.configuration.ConfigurationSection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ProgressionCostService {

    private final String calculationMode;
    private final BigDecimal linearBaseCost;
    private final BigDecimal linearCostPerLevel;
    private final BigDecimal exponentialBaseCost;
    private final double exponentialMultiplier;

    private final boolean scalingEnabled;
    private final String scalingMode;
    private final double scalingValue;
    
    // OPTIMIZATION: Cache expensive calculations to avoid repeated computation
    private final Map<Long, BigDecimal> costCache = new ConcurrentHashMap<>();
    private final Map<Long, BigDecimal> scalingCache = new ConcurrentHashMap<>();
    
    // OPTIMIZATION: Cache constants to avoid repeated BigDecimal creation
    private final BigDecimal scalingMultiplier;
    private final BigDecimal scalingAdditive;

    public ProgressionCostService(ConfigurationSection currencySettings) {
        this.calculationMode = currencySettings.getString("calculation-mode", "EXPONENTIAL").toUpperCase();
        this.linearBaseCost = new BigDecimal(currencySettings.getString("linear.base-cost", "1000"));
        this.linearCostPerLevel = new BigDecimal(currencySettings.getString("linear.cost-per-level", "1000"));
        this.exponentialBaseCost = new BigDecimal(currencySettings.getString("exponential.base-cost", "1000"));
        this.exponentialMultiplier = currencySettings.getDouble("exponential.cost-multiplier", 1.15);

        ConfigurationSection scalingConfig = currencySettings.getConfigurationSection("prestige-cost-scaling");
        if (scalingConfig != null) {
            this.scalingEnabled = scalingConfig.getBoolean("enabled", false);
            this.scalingMode = scalingConfig.getString("mode", "MULTIPLIER").toUpperCase();
            this.scalingValue = scalingConfig.getDouble("value", 0.0);
        } else {
            this.scalingEnabled = false;
            this.scalingMode = "MULTIPLIER";
            this.scalingValue = 0.0;
        }
        
        // OPTIMIZATION: Pre-calculate scaling constants
        this.scalingMultiplier = BigDecimal.valueOf(scalingValue);
        this.scalingAdditive = BigDecimal.valueOf(scalingValue);
    }

    /**
     * Calculates the cost for the next level, optionally applying prestige scaling.
     * OPTIMIZATION: Uses caching to avoid repeated expensive calculations.
     * @param currentLevel The current rank or prestige level.
     * @param prestigeLevel The player's current prestige level (used for rankup cost scaling).
     * @return The final calculated cost.
     */
    public BigDecimal getCost(long currentLevel, long prestigeLevel) {
        // OPTIMIZATION: Create cache key for this specific calculation
        long cacheKey = (currentLevel << 32) | prestigeLevel;
        
        // OPTIMIZATION: Check cache first
        BigDecimal cachedCost = costCache.get(cacheKey);
        if (cachedCost != null) {
            return cachedCost;
        }
        
        BigDecimal baseCost = switch (calculationMode) {
            case "LINEAR" -> calculateLinearCost(currentLevel);
            case "EXPONENTIAL" -> calculateExponentialCost(currentLevel);
            default -> BigDecimal.ZERO;
        };

        BigDecimal finalCost;
        if (scalingEnabled && prestigeLevel > 0) {
            finalCost = applyPrestigeScaling(baseCost, prestigeLevel);
        } else {
            finalCost = baseCost;
        }
        
        // OPTIMIZATION: Cache the result for future use
        costCache.put(cacheKey, finalCost);
        
        return finalCost;
    }
    
    /**
     * OPTIMIZATION: Clear cache when configuration changes to ensure consistency
     */
    public void clearCache() {
        costCache.clear();
        scalingCache.clear();
    }

    private BigDecimal applyPrestigeScaling(BigDecimal baseCost, long prestigeLevel) {
        // OPTIMIZATION: Check scaling cache first
        long scalingCacheKey = prestigeLevel;
        BigDecimal cachedScaling = scalingCache.get(scalingCacheKey);
        if (cachedScaling != null) {
            return baseCost.multiply(cachedScaling).setScale(2, RoundingMode.HALF_UP);
        }
        
        BigDecimal scalingResult;
        if (scalingMode.equals("MULTIPLIER")) {
            // OPTIMIZATION: Use pre-calculated constants and more efficient math
            // Formula: baseCost * (1 + (prestigeLevel * scalingValue))
            BigDecimal prestigeMultiplier = BigDecimal.ONE.add(scalingMultiplier.multiply(BigDecimal.valueOf(prestigeLevel)));
            scalingResult = prestigeMultiplier;
        } else if (scalingMode.equals("ADDITIVE")) {
            // OPTIMIZATION: Use pre-calculated constants
            // Formula: baseCost + (prestigeLevel * scalingValue)
            BigDecimal prestigeAddition = scalingAdditive.multiply(BigDecimal.valueOf(prestigeLevel));
            scalingResult = BigDecimal.ONE.add(prestigeAddition.divide(baseCost, 10, RoundingMode.HALF_UP));
        } else {
            scalingResult = BigDecimal.ONE;
        }
        
        // OPTIMIZATION: Cache scaling result for future use
        scalingCache.put(scalingCacheKey, scalingResult);
        
        return baseCost.multiply(scalingResult).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateLinearCost(long currentLevel) {
        // OPTIMIZATION: More efficient linear calculation
        if (currentLevel == 0) {
            return linearBaseCost;
        }
        BigDecimal levelMultiplier = linearCostPerLevel.multiply(BigDecimal.valueOf(currentLevel));
        return linearBaseCost.add(levelMultiplier);
    }

    private BigDecimal calculateExponentialCost(long currentLevel) {
        // OPTIMIZATION: More efficient exponential calculation with early returns
        if (currentLevel == 0) {
            return exponentialBaseCost;
        }
        if (currentLevel == 1) {
            return exponentialBaseCost.multiply(BigDecimal.valueOf(exponentialMultiplier));
        }
        
        // OPTIMIZATION: Use Math.pow for double precision, then convert to BigDecimal
        // This is much faster than BigDecimal.pow for large exponents
        double result = exponentialBaseCost.doubleValue() * Math.pow(exponentialMultiplier, currentLevel);
        return BigDecimal.valueOf(result).setScale(2, RoundingMode.HALF_UP);
    }
}