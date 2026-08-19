package ru.phantom.gui;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import ru.phantom.PhantomPlugin;
import ru.phantom.config.BotSettings;
import ru.phantom.core.PhantomBot;
import ru.phantom.util.Msg;

/** Обрабатывает клики во всех меню плагина. */
public class GuiListener implements Listener {

    private final PhantomPlugin plugin;

    public GuiListener(PhantomPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        int slot = event.getSlot();
        ClickType click = event.getClick();
        PhantomBot bot = holder.getBot();

        switch (holder.getType()) {
            case BOT_LIST -> handleList(player, event, slot, click);
            case BOT_MAIN -> handleMain(player, bot, slot, click);
            case BOT_APPEARANCE -> handleAppearance(player, bot, slot, click);
            case BOT_BEHAVIOR -> handleBehavior(player, bot, slot, click);
            case BOT_STATS -> handleStats(player, bot, slot, click);
            case BOT_REALISM -> handleRealism(player, bot, slot, click);
        }
    }

    // ------------------------------------------------------------------

    private void handleList(Player player, InventoryClickEvent event, int slot, ClickType click) {
        int size = event.getInventory().getSize();
        int base = size - 9;

        if (slot == base + 4) {
            player.closeInventory();
            plugin.getChatInput().request(player, ChatInput.Kind.CREATE_BOT, null,
                    "<green>Напиши ник нового бота в чат <gray>(или <white>отмена</white>)");
            return;
        }
        if (slot == base + 8) {
            player.closeInventory();
            return;
        }
        if (slot >= base) {
            return;
        }

        var item = event.getCurrentItem();
        if (item == null || item.getType() != org.bukkit.Material.PLAYER_HEAD) {
            return;
        }
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        PhantomBot bot = plugin.getBotManager().byName(name.trim());
        if (bot == null) {
            return;
        }

        if (click.isShiftClick()) {
            Location loc = bot.getLocation();
            if (loc != null) {
                player.teleport(loc);
                Msg.send(player, "<green>Телепорт к <white>" + bot.getName());
            }
            player.closeInventory();
        } else if (click.isRightClick()) {
            player.closeInventory();
            String error = plugin.getPossessionManager().possess(player, bot);
            if (error != null) {
                Msg.send(player, "<red>" + error);
            }
        } else {
            plugin.getGui().openMain(player, bot);
        }
    }

    private void handleMain(Player player, PhantomBot bot, int slot, ClickType click) {
        if (bot == null) {
            return;
        }
        switch (slot) {
            case 19 -> plugin.getGui().openAppearance(player, bot);
            case 21 -> plugin.getGui().openBehavior(player, bot);
            case 23 -> plugin.getGui().openStats(player, bot);
            case 25 -> plugin.getGui().openRealism(player, bot);
            case 37 -> {
                player.closeInventory();
                String error = plugin.getPossessionManager().possess(player, bot);
                if (error != null) {
                    Msg.send(player, "<red>" + error);
                }
            }
            case 39 -> {
                Player bukkit = bot.getBukkitPlayer();
                if (bukkit != null) {
                    player.openInventory(bukkit.getInventory());
                }
            }
            case 41 -> {
                Location botLoc = bot.getLocation();
                if (click.isRightClick()) {
                    bot.teleport(player.getLocation());
                    Msg.send(player, "<green>Бот призван к тебе.");
                } else if (botLoc != null) {
                    player.teleport(botLoc);
                    Msg.send(player, "<green>Телепорт к боту.");
                }
                player.closeInventory();
            }
            case 43 -> {
                player.closeInventory();
                plugin.getChatInput().request(player, ChatInput.Kind.BOT_CHAT, bot,
                        "<yellow>Напиши сообщение, которое скажет бот <gray>(или <white>отмена</white>)");
            }
            case 49 -> {
                if (click.isShiftClick()) {
                    String name = bot.getName();
                    plugin.getBotManager().remove(bot);
                    Msg.send(player, "<red>Бот <white>" + name + "</white> удалён.");
                    plugin.getGui().openList(player);
                } else {
                    Msg.send(player, "<yellow>Для удаления зажми <white>Shift</white> и кликни ещё раз.");
                }
            }
            case 45 -> plugin.getGui().openList(player);
            default -> {
            }
        }
    }

    private void handleAppearance(Player player, PhantomBot bot, int slot, ClickType click) {
        if (bot == null) {
            return;
        }
        BotSettings settings = bot.getSettings();
        switch (slot) {
            case 11 -> {
                if (click.isRightClick()) {
                    plugin.getBotManager().loadSkinAsync(bot, player.getName());
                    Msg.send(player, "<green>Ставлю боту твой скин...");
                    player.closeInventory();
                } else {
                    player.closeInventory();
                    plugin.getChatInput().request(player, ChatInput.Kind.SKIN, bot,
                            "<aqua>Напиши ник игрока, чей скин поставить <gray>(или <white>отмена</white>)");
                }
            }
            case 13 -> {
                settings.setNameTagVisible(!settings.isNameTagVisible());
                Player bukkit = bot.getBukkitPlayer();
                if (bukkit != null) {
                    // Прячем тег имени через скрытие сущности от других игроков-наблюдателей
                    bukkit.setCustomNameVisible(settings.isNameTagVisible());
                }
                plugin.getGui().openAppearance(player, bot);
            }
            case 15 -> {
                settings.setTabVisible(!settings.isTabVisible());
                bot.updateTabVisibility();
                plugin.getGui().openAppearance(player, bot);
            }
            case 21 -> {
                settings.setGlowing(!settings.isGlowing());
                bot.applySettings();
                plugin.getGui().openAppearance(player, bot);
            }
            case 23 -> {
                settings.setCollidable(!settings.isCollidable());
                bot.applySettings();
                plugin.getGui().openAppearance(player, bot);
            }
            case 31 -> {
                settings.setSneaking(!settings.isSneaking());
                Player bukkit = bot.getBukkitPlayer();
                if (bukkit != null) {
                    bukkit.setSneaking(settings.isSneaking());
                }
                bot.refreshMetadata();
                plugin.getGui().openAppearance(player, bot);
            }
            case 36 -> plugin.getGui().openMain(player, bot);
            default -> {
            }
        }
    }

    private void handleBehavior(Player player, PhantomBot bot, int slot, ClickType click) {
        if (bot == null) {
            return;
        }
        BotSettings settings = bot.getSettings();
        switch (slot) {
            case 11 -> {
                settings.setAiMode(settings.getAiMode().next());
                plugin.getGui().openBehavior(player, bot);
            }
            case 13 -> {
                settings.setTargetPolicy(settings.getTargetPolicy().next());
                plugin.getGui().openBehavior(player, bot);
            }
            case 15 -> {
                settings.setLookAtNearest(!settings.isLookAtNearest());
                plugin.getGui().openBehavior(player, bot);
            }
            case 20 -> {
                settings.setFollowDistance(settings.getFollowDistance() + (click.isRightClick() ? -1 : 1));
                plugin.getGui().openBehavior(player, bot);
            }
            case 22 -> {
                settings.setAttackRadius(settings.getAttackRadius() + (click.isRightClick() ? -1 : 1));
                plugin.getGui().openBehavior(player, bot);
            }
            case 24 -> {
                settings.setAttackCooldown(settings.getAttackCooldown() + (click.isRightClick() ? -2 : 2));
                plugin.getGui().openBehavior(player, bot);
            }
            case 31 -> {
                Location loc = bot.getLocation();
                if (loc != null) {
                    bot.setGuardPoint(loc.clone());
                    Msg.send(player, "<green>Точка охраны назначена.");
                }
                plugin.getGui().openBehavior(player, bot);
            }
            case 36 -> plugin.getGui().openMain(player, bot);
            default -> {
            }
        }
    }

    private void handleStats(Player player, PhantomBot bot, int slot, ClickType click) {
        if (bot == null) {
            return;
        }
        BotSettings settings = bot.getSettings();
        Player bukkit = bot.getBukkitPlayer();

        switch (slot) {
            case 11 -> {
                if (click.isShiftClick()) {
                    if (bukkit != null) {
                        bukkit.setHealth(Math.min(settings.getMaxHealth(), bukkit.getMaxHealth()));
                        Msg.send(player, "<green>Бот вылечен.");
                    }
                } else {
                    settings.setMaxHealth(settings.getMaxHealth() + (click.isRightClick() ? -2 : 2));
                    bot.applySettings();
                }
                plugin.getGui().openStats(player, bot);
            }
            case 13 -> {
                settings.setWalkSpeed(settings.getWalkSpeed() + (click.isRightClick() ? -0.02f : 0.02f));
                bot.applySettings();
                plugin.getGui().openStats(player, bot);
            }
            case 15 -> {
                GameMode[] modes = GameMode.values();
                settings.setGameMode(modes[(settings.getGameMode().ordinal() + 1) % modes.length]);
                bot.applySettings();
                plugin.getGui().openStats(player, bot);
            }
            case 20 -> {
                settings.setInvulnerable(!settings.isInvulnerable());
                bot.applySettings();
                plugin.getGui().openStats(player, bot);
            }
            case 22 -> {
                if (click.isRightClick()) {
                    int delta = click.isShiftClick() ? -20 : 20;
                    settings.setAutoRespawnDelay(settings.getAutoRespawnDelay() + delta);
                } else {
                    settings.setAutoRespawn(!settings.isAutoRespawn());
                }
                plugin.getGui().openStats(player, bot);
            }
            case 24 -> {
                settings.setDropInventoryOnDeath(!settings.isDropInventoryOnDeath());
                plugin.getGui().openStats(player, bot);
            }
            case 31 -> {
                settings.setMaxHealth(player.getMaxHealth());
                settings.setWalkSpeed(player.getWalkSpeed());
                settings.setGameMode(player.getGameMode());
                bot.applySettings();
                Msg.send(player, "<green>Статы скопированы с тебя.");
                plugin.getGui().openStats(player, bot);
            }
            case 36 -> plugin.getGui().openMain(player, bot);
            default -> {
            }
        }
    }

    private void handleRealism(Player player, PhantomBot bot, int slot, ClickType click) {
        if (bot == null) {
            return;
        }
        BotSettings settings = bot.getSettings();
        switch (slot) {
            case 11 -> {
                settings.setHunger(!settings.isHunger());
                plugin.getGui().openRealism(player, bot);
            }
            case 13 -> {
                settings.setTakeFireDamage(!settings.isTakeFireDamage());
                plugin.getGui().openRealism(player, bot);
            }
            case 15 -> {
                settings.setDrowning(!settings.isDrowning());
                plugin.getGui().openRealism(player, bot);
            }
            case 20 -> {
                settings.setIdleHeadMovement(!settings.isIdleHeadMovement());
                plugin.getGui().openRealism(player, bot);
            }
            case 22 -> {
                settings.setIdleSwing(!settings.isIdleSwing());
                plugin.getGui().openRealism(player, bot);
            }
            case 24 -> {
                settings.setChatEnabled(!settings.isChatEnabled());
                plugin.getGui().openRealism(player, bot);
            }
            case 36 -> plugin.getGui().openMain(player, bot);
            default -> {
            }
        }
    }
}
