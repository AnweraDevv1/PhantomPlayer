package ru.phantom.core;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.phantom.PhantomPlugin;
import ru.phantom.config.BotSettings;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Сохранение и восстановление ботов между перезапусками сервера. */
public class BotStorage {

    private final PhantomPlugin plugin;
    private final File file;

    public BotStorage(PhantomPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bots.yml");
    }

    /** Записывает всех текущих ботов в bots.yml. */
    public void save() {
        if (!plugin.getConfig().getBoolean("persistence.save-bots", true)) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();

        for (PhantomBot bot : plugin.getBotManager().all()) {
            Location loc = bot.getLocation();
            if (loc == null) {
                continue;
            }
            ConfigurationSection section = yaml.createSection(bot.getName());
            BotSettings settings = bot.getSettings();

            section.set("owner", bot.getOwnerUuid() == null ? null : bot.getOwnerUuid().toString());
            section.set("world", loc.getWorld().getName());
            section.set("x", loc.getX());
            section.set("y", loc.getY());
            section.set("z", loc.getZ());
            section.set("yaw", loc.getYaw());
            section.set("pitch", loc.getPitch());

            section.set("skin-owner", settings.getSkinOwner());
            section.set("tab-visible", settings.isTabVisible());
            section.set("collidable", settings.isCollidable());
            section.set("glowing", settings.isGlowing());
            section.set("name-tag-visible", settings.isNameTagVisible());
            section.set("sneaking", settings.isSneaking());

            section.set("gamemode", settings.getGameMode().name());
            section.set("max-health", settings.getMaxHealth());
            section.set("invulnerable", settings.isInvulnerable());
            section.set("drop-inventory", settings.isDropInventoryOnDeath());
            section.set("auto-respawn", settings.isAutoRespawn());
            section.set("auto-respawn-delay", settings.getAutoRespawnDelay());
            section.set("walk-speed", settings.getWalkSpeed());

            section.set("ai-mode", settings.getAiMode().name());
            section.set("target-policy", settings.getTargetPolicy().name());
            section.set("look-at-nearest", settings.isLookAtNearest());
            section.set("follow-distance", settings.getFollowDistance());
            section.set("attack-radius", settings.getAttackRadius());
            section.set("attack-cooldown", settings.getAttackCooldown());

            section.set("hunger", settings.isHunger());
            section.set("fire-damage", settings.isTakeFireDamage());
            section.set("drowning", settings.isDrowning());
            section.set("idle-head", settings.isIdleHeadMovement());
            section.set("idle-swing", settings.isIdleSwing());
            section.set("chat-enabled", settings.isChatEnabled());

            if (bot.getGuardPoint() != null) {
                Location guard = bot.getGuardPoint();
                section.set("guard.world", guard.getWorld().getName());
                section.set("guard.x", guard.getX());
                section.set("guard.y", guard.getY());
                section.set("guard.z", guard.getZ());
            }

            Player bukkit = bot.getBukkitPlayer();
            if (bukkit != null) {
                section.set("inventory", bukkit.getInventory().getContents());
                section.set("armor", bukkit.getInventory().getArmorContents());
                section.set("health", bukkit.getHealth());
            }
        }

        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Cannot create plugin folder");
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save bots.yml: " + e.getMessage());
        }
    }

    /** Восстанавливает ботов из bots.yml. */
    public void load() {
        if (!plugin.getConfig().getBoolean("persistence.save-bots", true) || !file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int restored = 0;

        for (String name : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            World world = Bukkit.getWorld(section.getString("world", ""));
            if (world == null) {
                plugin.getLogger().warning("Skipping bot " + name + ": world not found");
                continue;
            }
            Location loc = new Location(world,
                    section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));

            String ownerRaw = section.getString("owner");
            Player owner = ownerRaw == null ? null : Bukkit.getPlayer(UUID.fromString(ownerRaw));

            BotManager.CreateResult result = plugin.getBotManager().create(name, loc, owner, false);
            if (!result.ok()) {
                plugin.getLogger().warning("Skipping bot " + name + ": " + result.error());
                continue;
            }
            PhantomBot bot = result.bot();
            applySection(bot, section, ownerRaw);
            restored++;
        }
        if (restored > 0) {
            plugin.getLogger().info("Restored bots: " + restored);
        }
    }

    private void applySection(PhantomBot bot, ConfigurationSection section, String ownerRaw) {
        BotSettings settings = bot.getSettings();

        settings.setSkinOwner(section.getString("skin-owner", bot.getName()));
        settings.setTabVisible(section.getBoolean("tab-visible", true));
        settings.setCollidable(section.getBoolean("collidable", true));
        settings.setGlowing(section.getBoolean("glowing", false));
        settings.setNameTagVisible(section.getBoolean("name-tag-visible", true));
        settings.setSneaking(section.getBoolean("sneaking", false));

        try {
            settings.setGameMode(GameMode.valueOf(section.getString("gamemode", "SURVIVAL")));
        } catch (IllegalArgumentException ignored) {
        }
        settings.setMaxHealth(section.getDouble("max-health", 20.0));
        settings.setInvulnerable(section.getBoolean("invulnerable", false));
        settings.setDropInventoryOnDeath(section.getBoolean("drop-inventory", true));
        settings.setAutoRespawn(section.getBoolean("auto-respawn", true));
        settings.setAutoRespawnDelay(section.getInt("auto-respawn-delay", 100));
        settings.setWalkSpeed((float) section.getDouble("walk-speed", 0.2));

        try {
            settings.setAiMode(BotSettings.AiMode.valueOf(section.getString("ai-mode", "IDLE")));
            settings.setTargetPolicy(BotSettings.TargetPolicy.valueOf(
                    section.getString("target-policy", "MONSTERS")));
        } catch (IllegalArgumentException ignored) {
        }
        settings.setLookAtNearest(section.getBoolean("look-at-nearest", true));
        settings.setFollowDistance(section.getDouble("follow-distance", 3.0));
        settings.setAttackRadius(section.getDouble("attack-radius", 12.0));
        settings.setAttackCooldown(section.getInt("attack-cooldown", 12));

        settings.setHunger(section.getBoolean("hunger", false));
        settings.setTakeFireDamage(section.getBoolean("fire-damage", true));
        settings.setDrowning(section.getBoolean("drowning", true));
        settings.setIdleHeadMovement(section.getBoolean("idle-head", true));
        settings.setIdleSwing(section.getBoolean("idle-swing", false));
        settings.setChatEnabled(section.getBoolean("chat-enabled", true));

        if (section.contains("guard.world")) {
            World guardWorld = Bukkit.getWorld(section.getString("guard.world", ""));
            if (guardWorld != null) {
                bot.setGuardPoint(new Location(guardWorld,
                        section.getDouble("guard.x"),
                        section.getDouble("guard.y"),
                        section.getDouble("guard.z")));
            }
        }

        Player bukkit = bot.getBukkitPlayer();
        if (bukkit != null) {
            @SuppressWarnings("unchecked")
            var inventory = (java.util.List<org.bukkit.inventory.ItemStack>) section.getList("inventory");
            if (inventory != null) {
                bukkit.getInventory().setContents(inventory.toArray(new org.bukkit.inventory.ItemStack[0]));
            }
            @SuppressWarnings("unchecked")
            var armor = (java.util.List<org.bukkit.inventory.ItemStack>) section.getList("armor");
            if (armor != null) {
                bukkit.getInventory().setArmorContents(armor.toArray(new org.bukkit.inventory.ItemStack[0]));
            }
            double health = section.getDouble("health", settings.getMaxHealth());
            bukkit.setHealth(Math.max(0.5, Math.min(health, settings.getMaxHealth())));
        }

        bot.applySettings();

        // Скин восстанавливаем асинхронно.
        plugin.getBotManager().loadSkinAsync(bot, settings.getSkinOwner());
    }
}
