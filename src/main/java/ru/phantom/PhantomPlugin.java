package ru.phantom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.phantom.ai.BotAI;
import ru.phantom.command.PhantomCommand;
import ru.phantom.control.PossessionInput;
import ru.phantom.control.PossessionManager;
import ru.phantom.core.BotListener;
import ru.phantom.core.BotManager;
import ru.phantom.core.BotStorage;
import ru.phantom.core.PhantomBot;
import ru.phantom.gui.BotGui;
import ru.phantom.gui.ChatInput;
import ru.phantom.gui.GuiListener;
import ru.phantom.util.Msg;

import java.util.ArrayList;

/** Точка входа PhantomPlayer. */
public class PhantomPlugin extends JavaPlugin {

    private BotManager botManager;
    private PossessionManager possessionManager;
    private BotGui gui;
    private ChatInput chatInput;
    private BotAI ai;
    private BotStorage storage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Msg.setPrefix(getConfig().getString("messages.prefix"));

        botManager = new BotManager(this);
        possessionManager = new PossessionManager(this);
        gui = new BotGui(this);
        chatInput = new ChatInput(this);
        ai = new BotAI(this);
        storage = new BotStorage(this);

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new BotListener(this), this);
        getServer().getPluginManager().registerEvents(new PossessionInput(this), this);
        getServer().getPluginManager().registerEvents(chatInput, this);

        PhantomCommand command = new PhantomCommand(this);
        var phantom = getCommand("phantom");
        if (phantom != null) {
            phantom.setExecutor(command);
            phantom.setTabCompleter(command);
        }

        startTicker();

        // Восстанавливаем сохранённых ботов после полной загрузки миров.
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> storage.load(), 20L);
        startAutosave();

        // Логи держим на ASCII: консоли серверов часто не в UTF-8.
        getLogger().info("PhantomPlayer enabled. Server: " + Bukkit.getVersion());
    }

    @Override
    public void onDisable() {
        if (storage != null && botManager != null) {
            storage.save();
        }
        if (possessionManager != null) {
            possessionManager.releaseAll();
        }
        if (botManager != null) {
            botManager.removeAll();
        }
        getLogger().info("PhantomPlayer disabled.");
    }

    /** Периодическое сохранение ботов. */
    private void startAutosave() {
        int seconds = getConfig().getInt("persistence.autosave-interval", 300);
        if (seconds <= 0) {
            return;
        }
        long ticks = seconds * 20L;
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> storage.save(), ticks, ticks);
    }

    /** Главный цикл: ИИ, авто-респавн, поддержка камеры вселения. */
    private void startTicker() {
        int interval = Math.max(1, getConfig().getInt("ai.tick-interval", 2));

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            for (PhantomBot bot : new ArrayList<>(botManager.all())) {
                try {
                    tickBot(bot);
                } catch (Throwable t) {
                    getLogger().warning("Bot tick error [" + bot.getName() + "]: " + t);
                }
            }
            tickPossessions();
        }, interval, interval);
    }

    private void tickBot(PhantomBot bot) {
        // Обработка отложенного респавна
        if (bot.getDeathTicks() > 0) {
            bot.setDeathTicks(bot.getDeathTicks() - 1);
            return;
        }
        if (bot.getDeathTicks() == 0) {
            bot.setDeathTicks(-1);
            Player bukkit = bot.getBukkitPlayer();
            if (bukkit != null && bukkit.isDead()) {
                bukkit.spigot().respawn();
                bot.applySettings();
            }
            return;
        }
        ai.tick(bot);
        bot.tickPhysics();
    }

    /** Держит камеру вселившегося игрока привязанной к боту. */
    private void tickPossessions() {
        for (var entry : new ArrayList<>(possessionManager.getPossessions().entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            PhantomBot bot = entry.getValue();

            if (player == null || !player.isOnline()) {
                possessionManager.getPossessions().remove(entry.getKey());
                bot.setPossessedBy(null);
                continue;
            }
            if (!bot.isSpawned()) {
                possessionManager.release(player);
                continue;
            }
            // Тело бота повторяет игрока: позиция, поворот, поза.
            bot.mirror(player);
        }
    }

    // ------------------------------------------------------------------

    public BotManager getBotManager() {
        return botManager;
    }

    public PossessionManager getPossessionManager() {
        return possessionManager;
    }

    public BotGui getGui() {
        return gui;
    }

    public ChatInput getChatInput() {
        return chatInput;
    }

    public BotAI getAi() {
        return ai;
    }

    public BotStorage getStorage() {
        return storage;
    }
}
