package ru.phantom.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.phantom.PhantomPlugin;
import ru.phantom.config.BotSettings;
import ru.phantom.util.SkinFetcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Реестр всех фейковых игроков на сервере. */
public class BotManager {

    private final PhantomPlugin plugin;
    private final Map<UUID, PhantomBot> bots = new LinkedHashMap<>();

    public BotManager(PhantomPlugin plugin) {
        this.plugin = plugin;
    }

    /** Результат попытки создания бота. */
    public record CreateResult(PhantomBot bot, String error) {
        public boolean ok() {
            return bot != null;
        }
    }

    /**
     * Создаёт и спавнит бота.
     *
     * @param name     ник бота (максимум 16 символов — ограничение протокола)
     * @param location точка спавна
     * @param owner    владелец (может быть null для консоли)
     */
    public CreateResult create(String name, Location location, Player owner) {
        return create(name, location, owner, true);
    }

    /**
     * @param loadSkin грузить ли скин сразу (при восстановлении из файла скин
     *                 применяется позже, чтобы не переспавнивать бота дважды)
     */
    public CreateResult create(String name, Location location, Player owner, boolean loadSkin) {
        if (name == null || name.isBlank()) {
            return new CreateResult(null, "Ник не может быть пустым");
        }
        if (name.length() > 16) {
            return new CreateResult(null, "Ник длиннее 16 символов");
        }
        if (!name.matches("[A-Za-z0-9_]+")) {
            return new CreateResult(null, "Ник может содержать только A-Z, 0-9 и _");
        }
        if (Bukkit.getPlayerExact(name) != null) {
            return new CreateResult(null, "Игрок с таким ником уже на сервере");
        }

        int globalLimit = plugin.getConfig().getInt("limits.max-bots-global", 50);
        if (globalLimit >= 0 && bots.size() >= globalLimit) {
            return new CreateResult(null, "Достигнут глобальный лимит ботов (" + globalLimit + ")");
        }
        if (owner != null) {
            int perPlayer = plugin.getConfig().getInt("limits.max-bots-per-player", 5);
            if (perPlayer >= 0 && countByOwner(owner.getUniqueId()) >= perPlayer) {
                return new CreateResult(null, "Ты достиг своего лимита ботов (" + perPlayer + ")");
            }
        }

        // Оффлайн-UUID как у обычного нелицензионного игрока — так бота
        // корректно воспринимают плагины прав и статистики.
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (bots.containsKey(uuid)) {
            return new CreateResult(null, "Бот с таким ником уже существует");
        }

        BotSettings settings = new BotSettings(name);
        settings.applyDefaults(
                plugin.getConfig().getConfigurationSection("defaults"),
                plugin.getConfig().getConfigurationSection("ai"),
                plugin.getConfig().getConfigurationSection("realism"));

        PhantomBot bot = new PhantomBot(uuid, name, owner == null ? null : owner.getUniqueId(), settings);
        if (!bot.spawn(location)) {
            return new CreateResult(null, "Не удалось заспавнить бота");
        }
        bots.put(uuid, bot);

        // Скин подгружаем асинхронно, чтобы не вешать основной поток.
        if (loadSkin) {
            loadSkinAsync(bot, name);
        }
        return new CreateResult(bot, null);
    }

    /** Асинхронно грузит скин и применяет его в основном потоке. */
    public void loadSkinAsync(PhantomBot bot, String skinOwner) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            SkinFetcher.Skin skin = SkinFetcher.fetch(skinOwner);
            if (skin == null) {
                return;
            }
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
                if (!bot.isSpawned()) {
                    return;
                }
                bot.getSettings().setSkin(skin.value(), skin.signature());
                bot.getSettings().setSkinOwner(skinOwner);
                bot.refreshSkin();
            });
        });
    }

    /** Удаляет бота с сервера. */
    public void remove(PhantomBot bot) {
        plugin.getPossessionManager().releaseIfPossessed(bot);
        bot.despawn();
        bots.remove(bot.getUuid());
    }

    /** Удаляет всех ботов (используется при выключении). */
    public void removeAll() {
        for (PhantomBot bot : new ArrayList<>(bots.values())) {
            plugin.getPossessionManager().releaseIfPossessed(bot);
            bot.despawn();
        }
        bots.clear();
    }

    public PhantomBot byUuid(UUID uuid) {
        return bots.get(uuid);
    }

    public PhantomBot byName(String name) {
        for (PhantomBot bot : bots.values()) {
            if (bot.getName().equalsIgnoreCase(name)) {
                return bot;
            }
        }
        return null;
    }

    public Collection<PhantomBot> all() {
        return bots.values();
    }

    public List<PhantomBot> byOwner(UUID owner) {
        List<PhantomBot> out = new ArrayList<>();
        for (PhantomBot bot : bots.values()) {
            if (owner.equals(bot.getOwnerUuid())) {
                out.add(bot);
            }
        }
        return out;
    }

    public int countByOwner(UUID owner) {
        return byOwner(owner).size();
    }

    public int size() {
        return bots.size();
    }

    /** Проверяет, является ли игрок ботом этого плагина. */
    public boolean isBot(Player player) {
        return player != null && bots.containsKey(player.getUniqueId());
    }

    public PhantomBot fromPlayer(Player player) {
        return player == null ? null : bots.get(player.getUniqueId());
    }
}
