# bRankup Flexible Progression Ladder System

## 🎯 Overview

bRankup now features a **flexible, extensible progression ladder system** that allows server administrators to easily create custom progression chains like:

```
Rankup → Prestige → Rebirth → Ascension → Transcendence
```

**No coding required!** Simply create a YAML configuration file and define the progression type in the main config.

## 🚀 Key Features

### **1. Dynamic Progression Types**
- **Plug-and-Play**: Add new progression types without touching the core code
- **Dependency Chains**: Each type can require completion of previous types
- **Automatic Validation**: System checks for circular dependencies and invalid configurations
- **Hot Reloading**: Reload individual progression types without restarting the server

### **2. Flexible Configuration**
- **Separate Files**: Each progression type has its own configuration file
- **Custom Requirements**: Define custom unlock conditions beyond just max level
- **Flexible Resets**: Choose what gets reset when progressing (ranks, currencies, custom actions)
- **Rich Rewards**: Support for every-progression, first-time, and interval-based rewards

### **3. Built-in Features**
- **Auto-Progression**: Automatic progression when conditions are met
- **Max Progression**: Bulk progression to maximum level
- **Permission System**: Granular permissions for each feature
- **Display Customization**: Custom titles, progress bars, and formatting

## 📁 File Structure

```
bRankup/
├── config.yml              # Main configuration + progression type definitions
├── rankup.yml             # Rankup system configuration
├── prestige.yml           # Prestige system configuration
├── rebirth.yml            # Example rebirth system (commented out)
├── messages.yml           # Message configuration
└── plugin.yml             # Plugin metadata and commands
```

## ⚙️ Configuration

### **Main Configuration (`config.yml`)**

```yaml
progression-types:
  rankup:
    config-file: "rankup.yml"
    follows: null  # Base progression type
    display-name: "Rankup"
    command: "rankup"
    
  prestige:
    config-file: "prestige.yml"
    follows: "rankup"  # Requires max rankup
    display-name: "Prestige"
    command: "prestige"
    
  rebirth:
    config-file: "rebirth.yml"
    follows: "prestige"  # Requires max prestige
    display-name: "Rebirth"
    command: "rebirth"
```

### **Progression Type Configuration (e.g., `rebirth.yml`)**

```yaml
# Core Settings
enabled: true
command-alias: "rebirth"
limit: 25

# Requirements
requirements:
  requires-max-level: true
  custom-requirements: []

# Reset Settings
resets:
  reset-previous: true
  reset-currencies:
    gold: true
    pearls: true
  custom-resets: []

# Features
features:
  auto-rebirth:
    enabled: true
    permission: "brankup.rebirth.auto"
    delay: 15
  max-rebirth:
    enabled: true
    permission: "brankup.rebirth.max"

# Currency & Costs
currency-settings:
  currency-type: "souls"
  calculation-mode: "EXPONENTIAL"
  exponential:
    base-cost: 10000
    cost-multiplier: 1.25

# Display & Rewards
display-settings:
  rebirth-display: "&8[&dRebirth %brankup_rebirth_level%&8]"
  max-level-display: "&c&lMax Rebirth"

rewards:
  every-rebirth:
    - "broadcast &d%brankup_player_name% has achieved Rebirth %brankup_rebirth_level%!"
    - "eco give %brankup_player_name% 5000"
```

## 🔧 Adding a New Progression Type

### **Step 1: Create Configuration File**
Create `myprogression.yml` in the plugin folder:

```yaml
enabled: true
command-alias: "myprogression"
limit: 100

requirements:
  requires-max-level: true
  custom-requirements: []

resets:
  reset-previous: true
  reset-currencies:
    gold: true
  custom-resets: []

features:
  auto-myprogression:
    enabled: true
    permission: "brankup.myprogression.auto"
    delay: 20
  max-myprogression:
    enabled: true
    permission: "brankup.myprogression.max"

currency-settings:
  currency-type: "mycurrency"
  calculation-mode: "LINEAR"
  linear:
    base-cost: 1000
    cost-per-level: 500

display-settings:
  myprogression-display: "&8[&6MyProgression %brankup_myprogression_level%&8]"
  max-level-display: "&c&lMax MyProgression"

rewards:
  every-myprogression:
    - "broadcast &6%brankup_player_name% achieved MyProgression %brankup_myprogression_level%!"
```

### **Step 2: Add to Main Config**
Add this section to `config.yml`:

```yaml
progression-types:
  # ... existing types ...
  
  myprogression:
    config-file: "myprogression.yml"
    follows: "prestige"  # Requires max prestige
    display-name: "My Progression"
    command: "myprogression"
```

### **Step 3: Restart Plugin**
The new progression type will be automatically loaded and available!

## 🎮 Commands

### **Progression Management Commands**

| Command | Permission | Description |
|---------|------------|-------------|
| `/progression list` | `brankup.admin.progression` | List all progression types |
| `/progression info <type>` | `brankup.admin.progression` | Show detailed info about a type |
| `/progression reload <type>` | `brankup.admin.progression` | Reload a specific progression type |
| `/progression stats` | `brankup.admin.progression` | Show system statistics |

### **Example Usage**
```bash
/progression list
# Shows: rankup → prestige → rebirth

/progression info rebirth
# Shows detailed configuration and settings

/progression reload rebirth
# Reloads rebirth configuration without restart
```

## 🔗 Progression Chains

### **Dependency Resolution**
The system automatically:
- **Builds dependency chains** based on the `follows` field
- **Validates dependencies** to prevent circular references
- **Determines progression order** using topological sorting
- **Checks unlock conditions** before allowing progression

### **Example Chain**
```
rankup (base) → prestige (requires max rankup) → rebirth (requires max prestige)
```

### **Unlock Logic**
```java
// Player can progress to rebirth if:
1. Has max prestige level (50)
2. Rebirth is enabled
3. Meets any custom requirements
4. Has sufficient currency
```

## 💰 Currency System

### **Supported Calculation Modes**
- **EXPONENTIAL**: `cost = base * multiplier^level`
- **LINEAR**: `cost = base + (level * increment)`

### **Currency Types**
- **Built-in**: `money` (Vault), `gold`, `pearls`
- **Custom**: Any EdPrison currency ID
- **Multiple**: Each progression type can use different currencies

### **Example Costs**
```yaml
# Exponential scaling
exponential:
  base-cost: 1000
  cost-multiplier: 1.15

# Results in:
# Level 1: 1,150
# Level 2: 1,322
# Level 3: 1,520
# Level 4: 1,748
```

## 🎁 Reward System

### **Reward Types**
1. **Every Progression**: Executed every time the player progresses
2. **First Time**: Executed only the first time reaching a specific level
3. **Interval**: Executed at specific level intervals

### **Example Rewards**
```yaml
rewards:
  every-progression:
    - "broadcast &a%brankup_player_name% achieved %brankup_progression_type% %brankup_progression_level%!"
    - "eco give %brankup_player_name% 1000"
    - "effect give %brankup_player_name% minecraft:glowing 30 1"
  
  first-time-rewards:
    "25":  # At level 25
      - "broadcast &6&l%brankup_player_name% &fhas achieved MAX LEVEL!"
      - "crate give %brankup_player_name% MaxLevelKey 1"
  
  interval-rewards:
    "every-5":  # Every 5 levels
      - "crate give %brankup_player_name% IntervalKey 1"
```

### **Available Placeholders**
- `%brankup_player_name%` - Player's name
- `%brankup_progression_type%` - Progression type name
- `%brankup_progression_level%` - Current progression level
- `%brankup_progression_type_level%` - Specific type level (e.g., `%brankup_rebirth_level%`)

## 🔄 Reset System

### **What Gets Reset**
- **Previous Progression**: Reset the level of the previous progression type
- **Currencies**: Reset specific currency balances to 0
- **Custom Actions**: Execute custom reset commands

### **Example Resets**
```yaml
resets:
  reset-previous: true      # Reset prestige to 0 when rebirth
  reset-currencies:
    gold: true              # Reset gold balance
    pearls: true            # Reset pearls balance
    money: false            # Keep money balance
  custom-resets:
    - "crate give %brankup_player_name% ResetKey 1"
    - "effect clear %brankup_player_name%"
```

## 🎨 Display Customization

### **Progress Display**
```yaml
display-settings:
  rebirth-display: "&8[&dRebirth %brankup_rebirth_level%&8]"
  max-level-display: "&c&lMax Rebirth"
  
  progress-bar:
    enabled: true
    character: "■"
    amount: 15
    color-achieved: "&d"
    color-current: "&e"
    color-needed: "&7"
```

### **Titles and Sounds**
```yaml
rebirth-title:
  enabled: true
  title: "&d&lREBIRTH ACHIEVED!"
  subtitle: "&7You have transcended to Rebirth &d%brankup_rebirth_level%&7!"
  fade-in: 15
  stay: 60
  fade-out: 15

sounds:
  enabled: true
  on-rebirth:
    sound-id: "ENTITY_ENDER_DRAGON_GROWL"
    volume: 1.0
    pitch: 0.8
```

## 🚀 Performance Features

### **Optimized Operations**
- **Caching**: Progression types are cached in memory
- **Lazy Loading**: Configurations loaded only when needed
- **Batch Processing**: Multiple operations grouped for efficiency
- **Async Support**: Non-blocking progression checks

### **Memory Management**
- **Automatic Cleanup**: Expired cache entries removed periodically
- **Configurable TTL**: Set how long data stays in memory
- **Resource Monitoring**: Track memory usage and performance

## 🛠️ Troubleshooting

### **Common Issues**

#### **1. Progression Type Not Loading**
```yaml
# Check config.yml
progression-types:
  mytype:
    config-file: "mytype.yml"  # File must exist
    follows: "existingtype"     # Must reference existing type
```

#### **2. Circular Dependencies**
```
Error: Circular dependency detected in progression type: mytype
# Solution: Check the 'follows' chain for loops
```

#### **3. Missing Permissions**
```yaml
# Ensure permissions are defined in plugin.yml
permissions:
  brankup.mytype.auto:
    description: Auto-progression for mytype
    default: true
```

### **Debug Commands**
```bash
/progression stats          # View system statistics
/progression info <type>    # Check specific type configuration
/progression reload <type>  # Reload problematic configuration
```

## 📊 Monitoring and Statistics

### **System Statistics**
```bash
/progression stats
# Shows:
# - Total progression types
# - Enabled types
# - Base and highest types
# - Progression order
```

### **Performance Monitoring**
```bash
/performance
# Shows:
# - Cache hit rates
# - Operation timings
# - Memory usage
# - Database performance
```

## 🔮 Future Enhancements

### **Planned Features**
- **Web Dashboard**: Browser-based progression management
- **Discord Integration**: Progression notifications and management
- **Advanced Requirements**: Time-based, achievement-based, or custom logic
- **Progression Trees**: Multiple paths and branching progression
- **Statistics Tracking**: Player progression analytics and leaderboards

### **API Extensions**
- **Hook System**: Events for other plugins to listen to
- **Custom Requirements**: Plugin-defined unlock conditions
- **External Integrations**: Connect with other economy or progression systems

## 📝 Best Practices

### **1. Naming Conventions**
- Use descriptive names: `rebirth`, `ascension`, `transcendence`
- Keep IDs lowercase and simple
- Use consistent display names

### **2. Configuration Organization**
- Group related settings logically
- Use clear, descriptive comments
- Test configurations before production

### **3. Performance Considerations**
- Limit progression types to reasonable numbers (5-10 max)
- Use appropriate delays for auto-progression
- Monitor memory usage with many types

### **4. User Experience**
- Provide clear progression paths
- Balance costs and rewards appropriately
- Use meaningful reset strategies

## 🎯 Conclusion

The bRankup Flexible Progression Ladder System provides a powerful, extensible foundation for creating complex progression mechanics without any coding knowledge. Server administrators can now easily create unique progression experiences that keep players engaged and motivated.

**Key Benefits:**
- ✅ **No Coding Required** - Pure configuration-based system
- ✅ **Infinite Extensibility** - Add as many types as needed
- ✅ **Automatic Management** - Dependencies, validation, and ordering handled automatically
- ✅ **Performance Optimized** - Built with the same optimization principles as the core plugin
- ✅ **Easy Maintenance** - Separate files, hot reloading, and comprehensive management tools

Start building your progression ladder today! 🚀 