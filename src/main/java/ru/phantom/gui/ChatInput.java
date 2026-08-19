package ru.phantom.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.phantom.PhantomPlugin;
import ru.phantom.core.BotManager;
import ru.phantom.core.PhantomBot;
import ru.phantom.util.Msg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ввод текстовых значений через чат (ник бота, скин, сообщение). */
public class ChatInput implements Listener {

    /** Что именно мы ждём от игрока. */
    public enum Kind {
        CREATE_BOT,
        SKIN,
        BOT_CHAT
    }

    private record Pending(Kind kind, PhantomBot bot) {
    }

    private final PhantomPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ChatInput(PhantomPlugin plugin) {
        this.plugin = plugin;
    }

    /** Запрашивает ввод у игрока. */
    public void request(Player player, Kind kind, PhantomBot bot, String prompt) {
        pending.put(player.getUniqueId(), new Pending(kind, bot));
        Msg.send(player, prompt);
    }

    public boolean isWaiting(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending request = pending.remove(player.getUniqueId());
        if (request == null) {
            return;
        }
        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (message.equalsIgnoreCase("отмена") || message.equalsIgnoreCase("cancel")) {
            Msg.send(player, "<gray>Отменено.");
            return;
        }

        // Возвращаемся в основной поток — работа с миром обязана быть синхронной.
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> apply(player, request, message));
    }

    private void apply(Player player, Pending request, String message) {
        switch (request.kind()) {
            case CREATE_BOT -> {
                BotManager.CreateResult result =
                        plugin.getBotManager().create(message, player.getLocation(), player);
                if (result.ok()) {
                    Msg.send(player, "<green>Бот <white>" + message + "</white> создан!");
                    plugin.getGui().openMain(player, result.bot());
                } else {
                    Msg.send(player, "<red>" + result.error());
                }
            }
            case SKIN -> {
                PhantomBot bot = request.bot();
                if (bot != null && bot.isSpawned()) {
                    plugin.getBotManager().loadSkinAsync(bot, message);
                    Msg.send(player, "<green>Загружаю скин игрока <white>" + message + "</white>...");
                }
            }
            case BOT_CHAT -> {
                PhantomBot bot = request.bot();
                if (bot != null && bot.isSpawned()) {
                    bot.chat(message);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
