# bRankup Configuration Guide

## Overview

bRankup now uses a modular configuration system with separate files for different systems, making configuration easier to manage and organize.

## 📁 Configuration Files

### 1. `config.yml` - Main Configuration
Contains global settings, database configuration, and performance options.

### 2. `rankup.yml` - Rankup System Configuration
Contains all rankup-related settings including costs, rewards, and display options.

### 3. `prestige.yml` - Prestige System Configuration
Contains all prestige-related settings including requirements, resets, and rewards.

### 4. `messages.yml` - Message Configuration
Contains all plugin messages and placeholders.

## 🔧 Configuration Structure

### Main Configuration (`config.yml`)

```yaml
# Database settings
database:
  type: SQLite
  mysql:
    host: "localhost"
    port: 3306
    database: "brankup"
    username: "user"
    password: "password"
    table-prefix: "brankup_"

# Currency definitions
currencies:
  gold: "Gold"
  pearls: "Pearls"

# Configuration file references
config-files:
  rankup: "rankup.yml"
  prestige: "prestige.yml"

# Performance settings
performance:
  auto-progression-frequency: 100  # Check every 5 seconds
  cache:
    player-data-ttl: 30           # minutes
    cleanup-interval: 5           # minutes
  cost-calculation:
    enable-caching: true
    max-cache-size: 1000
  rewards:
    batch-size: 5
    batch-delay: 2

# Auto-progression settings
auto-progression:
  allow-simultaneous: true
  simultaneous-settings:
    enabled: true
    priority: "prestige-first"
  auto-disable:
    enabled: true
    disable-other: true
  logging:
    level: "INFO"
    log-simultaneous: true
    log-auto-disable: true

# Auto-progression summary
auto-progression-summary:
  enabled: true
  interval-seconds: 120
  message:
    - ""
    - "<gold><b>Auto-Progression Summary</b> (<interval>)"
    - "[if_rankups]<gray>» You have ranked up <aqua><count_rankups></aqua> times."
    - "[if_prestiges]<gray>» You have prestiged <gold><count_prestiges></gold> times."
    - ""
```

### Rankup Configuration (`rankup.yml`)

```yaml
# Enable/disable the rankup system
enabled: true

# Command alias for rankup
command-alias: "rankup"

# Maximum rank level
limit: 50

# Feature settings
features:
  auto-rankup:
    enabled: true
    permission: "brankup.rank.auto"
    delay: 10 # In Ticks
  max-rankup:
    enabled: true
    permission: "brankup.rank.max"

# Currency and cost settings
currency-settings:
  currency-type: gold
  calculation-mode: EXPONENTIAL # EXPONENTIAL / LINEAR
  
  exponential:
    base-cost: 1000
    cost-multiplier: 1.15
  
  linear:
    base-cost: 1000
    cost-per-level: 1000

# Prestige cost scaling for rankups
prestige-cost-scaling:
  enabled: true
  mode: "MULTIPLIER" # MULTIPLIER / ADDITIVE
  value: 0.25

# Display settings
display-settings:
  rank-display: "&8[&b%brankup_rank_level%&8]"
  max-level-display: "&c&lMax Level"
  
  progress-bar:
    enabled: true
    character: "■"
    amount: 10
    color-achieved: "&a"
    color-current: "&e"
    color-needed: "&7"
    
  rankup-title:
    enabled: true
    title: "<gradient:#FAD961:#F76B1C><b>LEVEL UP!</b></gradient>"
    subtitle: "<gray>You are now Rank <aqua><new_rank></aqua>!"
    fade-in: 10
    stay: 40
    fade-out: 10

# Sound settings
sounds:
  enabled: true
  on-rankup:
    sound-id: "ENTITY_PLAYER_LEVELUP"
    volume: 1.0
    pitch: 1.0
  on-fail:
    sound-id: "ENTITY_VILLAGER_NO"
    volume: 1.0
    pitch: 1.0

# Reward settings
rewards:
  every-level:
    - "eco give %brankup_player_name% 1000"
  
  first-time-rewards:
    "50":
      - "broadcast &e%brankup_player_name% &fhas reached the max level for the first time!"
  
  interval-rewards:
    "every-10":
      - "crate give %brankup_player_name% RankKey 1"
```

### Prestige Configuration (`prestige.yml`)

```yaml
# Enable/disable the prestige system
enabled: true

# Command alias for prestige
command-alias: "prestige"

# Maximum prestige level
limit: 50

# Requirements for prestiging
requirements:
  requires-max-level: true

# Reset settings when prestiging
reset-settings:
  reset-rankup: true
  reset-currencies:
    gold: true

# Feature settings
features:
  auto-prestige:
    enabled: true
    permission: "brankup.prestige.auto"
    delay: 10 # In Ticks
  max-prestige:
    enabled: true
    permission: "brankup.prestige.max"

# Currency and cost settings
currency-settings:
  currency-type: pearls
  calculation-mode: EXPONENTIAL
  
  exponential:
    base-cost: 1000
    cost-multiplier: 1.15
  
  linear:
    base-cost: 1000
    cost-per-level: 1000

# Display settings
display-settings:
  prestige-display: "&8[&bLevel %brankup_prestige_level%&8]"
  max-level-display: "&c&lMax Prestige"
  
  progress-bar:
    enabled: true
    character: "■"
    amount: 10
    color-achieved: "&a"
    color-current: "&e"
    color-needed: "&7"

  prestige-title:
    enabled: true
    title: "&6&lLEVEL UP!"
    subtitle: "&7You are now Level &b%brankup_prestige_level_next%&7!"
    fade-in: 10
    stay: 40
    fade-out: 10

# Sound settings
sounds:
  enabled: true
  on-prestige:
    sound-id: "ENTITY_PLAYER_LEVELUP"
    volume: 1.0
    pitch: 1.0
  on-fail:
    sound-id: "ENTITY_VILLAGER_NO"
    volume: 1.0
    pitch: 1.0

# Reward settings
rewards:
  every-prestige:
    - "eco give %brankup_player_name% 1000"
  
  first-time-rewards:
    "50":
      - "broadcast &e%brankup_player_name% &fhas reached the max level for the first time!"
  
  interval-rewards:
    "every-10":
      - "crate give %brankup_player_name% RankKey 1"
```

## 🚀 Benefits of Modular Configuration

### 1. **Easier Management**
- Edit rankup settings without touching prestige settings
- Focus on one system at a time
- Clear separation of concerns

### 2. **Better Organization**
- Related settings grouped together
- Easier to find specific configurations
- Cleaner file structure

### 3. **Independent Backups**
- Backup individual system configurations
- Restore specific systems without affecting others
- Version control for specific features

### 4. **Easier Troubleshooting**
- Isolate issues to specific configuration files
- Test changes without affecting other systems
- Clear error reporting per file

## 📝 Configuration Best Practices

### 1. **Backup Before Changes**
Always backup your configuration files before making changes.

### 2. **Test Changes**
Test configuration changes on a development server first.

### 3. **Use Comments**
Add comments to explain complex settings for future reference.

### 4. **Validate Syntax**
Use a YAML validator to check syntax before loading.

### 5. **Incremental Changes**
Make small changes and test rather than changing everything at once.

## 🔄 Reloading Configuration

### Reload All Configurations
```bash
/brankupadmin reload
```

### Reload Specific Configuration
```bash
/brankupadmin reload rankup
/brankupadmin reload prestige
/brankupadmin reload messages
```

## 🆘 Troubleshooting

### Common Issues

1. **Configuration Not Loading**
   - Check file permissions
   - Verify YAML syntax
   - Check console for errors

2. **Settings Not Applied**
   - Reload the specific configuration
   - Check for typos in configuration keys
   - Verify configuration file paths

3. **Performance Issues**
   - Adjust `auto-progression-frequency` in main config
   - Modify cache TTL settings
   - Check batch processing settings

### Getting Help

- Check the console for error messages
- Use `/performance` command to monitor performance
- Review the performance optimization guide
- Check file permissions and syntax

## 📚 Additional Resources

- **Performance Optimization Guide**: `PERFORMANCE_OPTIMIZATION.md`
- **Plugin Commands**: See `plugin.yml` for available commands
- **Placeholder Reference**: Check `messages.yml` for available placeholders
- **Support**: Visit bumpier.dev for additional help 