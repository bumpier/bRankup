package net.bumpier.brankup.progression;

import net.bumpier.brankup.bRankup;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.logging.Level;

/**
 * Manages all progression types and their dependency chains.
 * This class handles the dynamic loading and management of progression types.
 */
public class ProgressionChainManager {
    
    private final bRankup plugin;
    private final Map<String, ProgressionType> progressionTypes;
    private final Map<String, List<String>> progressionChains;
    private final List<String> progressionOrder;
    
    public ProgressionChainManager(bRankup plugin) {
        this.plugin = plugin;
        this.progressionTypes = new HashMap<>();
        this.progressionChains = new HashMap<>();
        this.progressionOrder = new ArrayList<>();
    }
    
    /**
     * Load all progression types from configuration
     */
    public void loadProgressionTypes() {
        progressionTypes.clear();
        progressionChains.clear();
        progressionOrder.clear();
        
        ConfigurationSection typesSection = plugin.getConfigManager().getMainConfig()
            .getConfigurationSection("progression-types");
        
        if (typesSection == null) {
            plugin.getLogger().warning("No progression types defined in config.yml");
            return;
        }
        
        // First pass: create all progression types
        for (String typeId : typesSection.getKeys(false)) {
            ConfigurationSection typeSection = typesSection.getConfigurationSection(typeId);
            if (typeSection == null) continue;
            
            String configFile = typeSection.getString("config-file");
            String follows = typeSection.getString("follows");
            String displayName = typeSection.getString("display-name", typeId);
            String command = typeSection.getString("command", typeId);
            
            if (configFile == null) {
                plugin.getLogger().warning("Missing config-file for progression type: " + typeId);
                continue;
            }
            
            // Load the configuration file
            FileConfiguration config = plugin.getConfigManager().getConfig(configFile);
            if (config == null) {
                plugin.getLogger().warning("Could not load config file: " + configFile);
                continue;
            }
            
            ProgressionType progressionType = new ProgressionType(typeId, configFile, follows, 
                displayName, command, config);
            
            progressionTypes.put(typeId, progressionType);
            plugin.getLogger().info("Loaded progression type: " + typeId + " -> " + displayName);
        }
        
        // Second pass: build dependency chains and validate
        buildProgressionChains();
        validateProgressionChains();
        
        // Third pass: determine progression order
        determineProgressionOrder();
        
        plugin.getLogger().info("Loaded " + progressionTypes.size() + " progression types");
        plugin.getLogger().info("Progression order: " + String.join(" -> ", progressionOrder));
    }
    
    /**
     * Build dependency chains for all progression types
     */
    private void buildProgressionChains() {
        for (ProgressionType type : progressionTypes.values()) {
            List<String> chain = new ArrayList<>();
            String currentType = type.getId();
            
            // Build the chain by following the 'follows' references
            while (currentType != null) {
                chain.add(0, currentType); // Add to beginning to maintain order
                ProgressionType current = progressionTypes.get(currentType);
                currentType = current != null ? current.getFollows() : null;
            }
            
            progressionChains.put(type.getId(), chain);
        }
    }
    
    /**
     * Validate that all progression chains are valid
     */
    private void validateProgressionChains() {
        for (ProgressionType type : progressionTypes.values()) {
            if (type.getFollows() != null && !progressionTypes.containsKey(type.getFollows())) {
                plugin.getLogger().warning("Progression type " + type.getId() + 
                    " follows unknown type: " + type.getFollows());
            }
            
            // Check for circular dependencies
            List<String> chain = progressionChains.get(type.getId());
            if (chain != null && chain.size() != new HashSet<>(chain).size()) {
                plugin.getLogger().severe("Circular dependency detected in progression type: " + type.getId());
            }
        }
    }
    
    /**
     * Determine the order of progression types based on dependencies
     */
    private void determineProgressionOrder() {
        // Use topological sort to determine order
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacencyList = new HashMap<>();
        
        // Initialize
        for (String typeId : progressionTypes.keySet()) {
            inDegree.put(typeId, 0);
            adjacencyList.put(typeId, new ArrayList<>());
        }
        
        // Build adjacency list and calculate in-degrees
        for (ProgressionType type : progressionTypes.values()) {
            if (type.getFollows() != null) {
                adjacencyList.get(type.getFollows()).add(type.getId());
                inDegree.put(type.getId(), inDegree.get(type.getId()) + 1);
            }
        }
        
        // Topological sort using Kahn's algorithm
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            progressionOrder.add(current);
            
            for (String neighbor : adjacencyList.get(current)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        // Check if we processed all types
        if (progressionOrder.size() != progressionTypes.size()) {
            plugin.getLogger().warning("Could not determine complete progression order. " +
                "Some types may have circular dependencies.");
        }
    }
    
    /**
     * Get a progression type by ID
     */
    public ProgressionType getProgressionType(String typeId) {
        return progressionTypes.get(typeId);
    }
    
    /**
     * Get all progression types
     */
    public Collection<ProgressionType> getAllProgressionTypes() {
        return progressionTypes.values();
    }
    
    /**
     * Get the progression chain for a specific type
     */
    public List<String> getProgressionChain(String typeId) {
        return progressionChains.getOrDefault(typeId, new ArrayList<>());
    }
    
    /**
     * Get the progression order (from base to highest)
     */
    public List<String> getProgressionOrder() {
        return new ArrayList<>(progressionOrder);
    }
    
    /**
     * Check if a player can progress to a specific type
     */
    public boolean canProgress(String typeId, Map<String, Long> playerLevels) {
        ProgressionType type = progressionTypes.get(typeId);
        if (type == null || !type.isEnabled()) {
            return false;
        }
        
        if (type.getFollows() == null) {
            return true; // Base progression type
        }
        
        // Check if player has max level in the required progression type
        Long requiredLevel = playerLevels.get(type.getFollows());
        if (requiredLevel == null) {
            return false;
        }
        
        ProgressionType requiredType = progressionTypes.get(type.getFollows());
        if (requiredType == null) {
            return false;
        }
        
        if (type.requiresMaxLevel() && requiredLevel < requiredType.getLimit()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get the next progression type in the chain
     */
    public ProgressionType getNextProgressionType(String currentTypeId) {
        int currentIndex = progressionOrder.indexOf(currentTypeId);
        if (currentIndex == -1 || currentIndex >= progressionOrder.size() - 1) {
            return null; // No next type
        }
        
        String nextTypeId = progressionOrder.get(currentIndex + 1);
        return progressionTypes.get(nextTypeId);
    }
    
    /**
     * Get the previous progression type in the chain
     */
    public ProgressionType getPreviousProgressionType(String currentTypeId) {
        int currentIndex = progressionOrder.indexOf(currentTypeId);
        if (currentIndex <= 0) {
            return null; // No previous type
        }
        
        String previousTypeId = progressionOrder.get(currentIndex - 1);
        return progressionTypes.get(previousTypeId);
    }
    
    /**
     * Get all progression types that follow a specific type
     */
    public List<ProgressionType> getFollowingProgressionTypes(String typeId) {
        List<ProgressionType> following = new ArrayList<>();
        for (ProgressionType type : progressionTypes.values()) {
            if (typeId.equals(type.getFollows())) {
                following.add(type);
            }
        }
        return following;
    }
    
    /**
     * Check if a progression type is the base type (has no dependencies)
     */
    public boolean isBaseProgressionType(String typeId) {
        ProgressionType type = progressionTypes.get(typeId);
        return type != null && type.getFollows() == null;
    }
    
    /**
     * Check if a progression type is the highest type (nothing follows it)
     */
    public boolean isHighestProgressionType(String typeId) {
        return getFollowingProgressionTypes(typeId).isEmpty();
    }
    
    /**
     * Get the base progression type (first in the chain)
     */
    public ProgressionType getBaseProgressionType() {
        if (progressionOrder.isEmpty()) {
            return null;
        }
        return progressionTypes.get(progressionOrder.get(0));
    }
    
    /**
     * Get the highest progression type (last in the chain)
     */
    public ProgressionType getHighestProgressionType() {
        if (progressionOrder.isEmpty()) {
            return null;
        }
        return progressionTypes.get(progressionOrder.get(progressionOrder.size() - 1));
    }
    
    /**
     * Reload a specific progression type
     */
    public boolean reloadProgressionType(String typeId) {
        ProgressionType type = progressionTypes.get(typeId);
        if (type == null) {
            return false;
        }
        
        // Reload the configuration file
        FileConfiguration config = plugin.getConfigManager().getConfig(type.getConfigFile());
        if (config == null) {
            return false;
        }
        
        // Create new progression type with reloaded config
        ProgressionType newType = new ProgressionType(type.getId(), type.getConfigFile(), 
            type.getFollows(), type.getDisplayName(), type.getCommand(), config);
        
        // Update the type
        progressionTypes.put(typeId, newType);
        
        // Rebuild chains and order
        buildProgressionChains();
        validateProgressionChains();
        determineProgressionOrder();
        
        plugin.getLogger().info("Reloaded progression type: " + typeId);
        return true;
    }
    
    /**
     * Get progression statistics
     */
    public Map<String, Object> getProgressionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total-types", progressionTypes.size());
        stats.put("enabled-types", progressionTypes.values().stream()
            .filter(ProgressionType::isEnabled).count());
        stats.put("progression-order", progressionOrder);
        stats.put("base-type", getBaseProgressionType() != null ? 
            getBaseProgressionType().getId() : "none");
        stats.put("highest-type", getHighestProgressionType() != null ? 
            getHighestProgressionType().getId() : "none");
        
        return stats;
    }
} 