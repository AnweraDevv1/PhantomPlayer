package ru.phantom.gui;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.entity.Player;
import ru.phantom.PhantomPlugin;
import ru.phantom.config.BotSettings;
import ru.phantom.core.PhantomBot;
import ru.phantom.util.Msg;

import java.util.ArrayList;
import java.util.List;

/** Построение всех экранов меню. */
public class BotGui {

    private final PhantomPlugin plugin;

    public BotGui(PhantomPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Список ботов
    // ------------------------------------------------------------------

    public void openList(Player viewer) {
        List<PhantomBot> bots = viewer.hasPermission("phantom.admin")
                ? new ArrayList<>(plugin.getBotManager().all())
                : plugin.getBotManager().byOwner(viewer.getUniqueId());

        GuiHolder holder = new GuiHolder(GuiHolder.Type.BOT_LIST, null);
        int rows = Math.max(3, Math.min(6, (bots.size() / 9) + 2));
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
                Msg.item("<dark_gray>» <gradient:#8A2BE2:#00E5FF>Фейковые игроки</gradient>"));
        holder.setInventory(inv);

        int slot = 0;
        for (PhantomBot bot : bots) {
            if (slot >= (rows - 1) * 9) {
                break;
            }
            inv.setItem(slot++, botHead(bot));
        }

        // Нижняя панель
        int base = (rows - 1) * 9;
        fillRow(inv, base);
        inv.setItem(base + 4, item(Material.EMERALD,
                "<green><bold>Создать бота</bold>",
                "<gray>Спавнит нового фейкового игрока",
                "<gray>на твоей позиции.",
                "",
                "<yellow>▶ Клик — ввести ник в чат"));
        inv.setItem(base + 8, item(Material.BARRIER, "<red>Закрыть"));

        viewer.openInventory(inv);
    }

    private ItemStack botHead(PhantomBot bot) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        try {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(bot.getUuid()));
        } catch (Throwable ignored) {
        }
        BotSettings settings = bot.getSettings();
        Player bukkit = bot.getBukkitPlayer();

        meta.displayName(Msg.item("<aqua><bold>" + bot.getName()));
        meta.lore(Msg.lore(
                "<gray>Статус: " + (bot.isSpawned() ? "<green>активен" : "<red>не заспавнен"),
                "<gray>Здоровье: <red>" + (bukkit == null ? "?" : String.format("%.1f", bukkit.getHealth()))
                        + "<gray>/<red>" + String.format("%.1f", settings.getMaxHealth()),
                "<gray>Режим ИИ: <yellow>" + settings.getAiMode().display(),
                "<gray>Гейммод: <yellow>" + settings.getGameMode().name(),
                "<gray>Управляется: " + (bot.isPossessed() ? "<green>да" : "<dark_gray>нет"),
                "",
                "<yellow>▶ ЛКМ — открыть настройки",
                "<yellow>▶ ПКМ — вселиться",
                "<yellow>▶ Shift+ЛКМ — телепорт к боту"));
        head.setItemMeta(meta);
        return head;
    }

    // ------------------------------------------------------------------
    // Главное меню бота
    // ------------------------------------------------------------------

    public void openMain(Player viewer, PhantomBot bot) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.BOT_MAIN, bot);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Msg.item("<dark_gray>» <aqua>Бот: <white>" + bot.getName()));
        holder.setInventory(inv);
        decorate(inv);

        Player bukkit = bot.getBukkitPlayer();
        BotSettings settings = bot.getSettings();

        // Информационная голова
        inv.setItem(4, botHead(bot));

        // Разделы
        inv.setItem(19, item(Material.LEATHER_CHESTPLATE,
                "<light_purple><bold>Внешность</bold>",
                "<gray>Скин, ник, видимость в TAB,",
                "<gray>свечение, тег имени.",
                "",
                "<yellow>▶ Клик — открыть"));

        inv.setItem(21, item(Material.IRON_SWORD,
                "<red><bold>Поведение и ИИ</bold>",
                "<gray>Режим ИИ: <yellow>" + settings.getAiMode().display(),
                "<gray>Цели: <yellow>" + settings.getTargetPolicy().display(),
                "",
                "<yellow>▶ Клик — открыть"));

        inv.setItem(23, item(Material.GOLDEN_APPLE,
                "<green><bold>Характеристики</bold>",
                "<gray>Здоровье, скорость, гейммод,",
                "<gray>неуязвимость, респавн.",
                "",
                "<yellow>▶ Клик — открыть"));

        inv.setItem(25, item(Material.CLOCK,
                "<gold><bold>Реализм</bold>",
                "<gray>Голод, урон от огня, утопление,",
                "<gray>живые движения головой.",
                "",
                "<yellow>▶ Клик — открыть"));

        // Быстрые действия
        inv.setItem(37, item(Material.ENDER_EYE,
                "<aqua><bold>Вселиться</bold>",
                "<gray>Ты играешь за него полностью:",
                "<gray>ходьба, удары, стройка, чат.",
                "<gray>Выход: <white>/phantom release",
                "",
                bot.isPossessed() ? "<red>Уже занят" : "<yellow>▶ Клик — вселиться"));

        inv.setItem(39, item(Material.CHEST,
                "<gold><bold>Инвентарь бота</bold>",
                "<gray>Открыть и изменить его вещи.",
                "",
                "<yellow>▶ Клик — открыть"));

        inv.setItem(41, item(Material.ENDER_PEARL,
                "<light_purple><bold>Телепорт</bold>",
                "<gray>ЛКМ — телепорт к боту",
                "<gray>ПКМ — призвать бота к себе"));

        inv.setItem(43, item(Material.NAME_TAG,
                "<yellow><bold>Сказать в чат</bold>",
                "<gray>Бот напишет твоё сообщение",
                "<gray>от своего имени.",
                "",
                "<yellow>▶ Клик — ввести текст"));

        inv.setItem(49, item(Material.BARRIER,
                "<red><bold>Удалить бота</bold>",
                "<gray>Убирает фейкового игрока с сервера.",
                "",
                "<red>▶ Shift+ЛКМ — подтвердить"));

        inv.setItem(45, item(Material.ARROW, "<gray>◀ Назад к списку"));

        viewer.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Внешность
    // ------------------------------------------------------------------

    public void openAppearance(Player viewer, PhantomBot bot) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.BOT_APPEARANCE, bot);
        Inventory inv = Bukkit.createInventory(holder, 45,
                Msg.item("<dark_gray>» <light_purple>Внешность: <white>" + bot.getName()));
        holder.setInventory(inv);
        decorate(inv);

        BotSettings settings = bot.getSettings();

        inv.setItem(11, item(Material.PLAYER_HEAD,
                "<aqua><bold>Скин</bold>",
                "<gray>Сейчас: <white>" + settings.getSkinOwner(),
                "",
                "<yellow>▶ ЛКМ — ввести ник для скина",
                "<yellow>▶ ПКМ — взять твой скин"));

        inv.setItem(13, item(Material.NAME_TAG,
                "<aqua><bold>Имя над головой</bold>",
                "<gray>Ник: <white>" + bot.getName(),
                "<gray>Тег видим: " + Msg.toggle(settings.isNameTagVisible()),
                "",
                "<yellow>▶ Клик — переключить видимость тега"));

        inv.setItem(15, item(settings.isTabVisible() ? Material.LIME_DYE : Material.GRAY_DYE,
                "<aqua><bold>Видимость в TAB</bold>",
                "<gray>Показывать в списке игроков: " + Msg.toggle(settings.isTabVisible()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(21, item(settings.isGlowing() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                "<aqua><bold>Свечение</bold>",
                "<gray>Контур вокруг бота: " + Msg.toggle(settings.isGlowing()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(23, item(settings.isCollidable() ? Material.PISTON : Material.SLIME_BLOCK,
                "<aqua><bold>Столкновения</bold>",
                "<gray>Можно оттолкнуть бота: " + Msg.toggle(settings.isCollidable()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(31, item(settings.isSneaking() ? Material.LEATHER_BOOTS : Material.IRON_BOOTS,
                "<aqua><bold>Приседание</bold>",
                "<gray>Бот сидит: " + Msg.toggle(settings.isSneaking()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(36, item(Material.ARROW, "<gray>◀ Назад"));
        viewer.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Поведение
    // ------------------------------------------------------------------

    public void openBehavior(Player viewer, PhantomBot bot) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.BOT_BEHAVIOR, bot);
        Inventory inv = Bukkit.createInventory(holder, 45,
                Msg.item("<dark_gray>» <red>Поведение: <white>" + bot.getName()));
        holder.setInventory(inv);
        decorate(inv);

        BotSettings settings = bot.getSettings();

        List<String> modeLore = new ArrayList<>();
        modeLore.add("<gray>Текущий: <yellow>" + settings.getAiMode().display());
        modeLore.add("<dark_gray>" + settings.getAiMode().description());
        modeLore.add("");
        for (BotSettings.AiMode mode : BotSettings.AiMode.values()) {
            modeLore.add((mode == settings.getAiMode() ? "<green>▶ " : "<dark_gray>  ") + mode.display());
        }
        modeLore.add("");
        modeLore.add("<yellow>▶ Клик — следующий режим");
        inv.setItem(11, item(Material.COMPASS, "<red><bold>Режим ИИ</bold>", modeLore));

        inv.setItem(13, item(Material.IRON_SWORD,
                "<red><bold>Кого атаковать</bold>",
                "<gray>Сейчас: <yellow>" + settings.getTargetPolicy().display(),
                "<dark_gray>" + settings.getTargetPolicy().description(),
                "",
                "<yellow>▶ Клик — следующий вариант"));

        inv.setItem(15, item(Material.SPYGLASS,
                "<red><bold>Смотреть на игроков</bold>",
                "<gray>Поворачивать голову: " + Msg.toggle(settings.isLookAtNearest()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(20, item(Material.LEAD,
                "<red><bold>Дистанция следования</bold>",
                "<gray>Сейчас: <white>" + String.format("%.1f", settings.getFollowDistance()) + " блоков",
                "",
                "<yellow>▶ ЛКМ +1  <yellow>▶ ПКМ -1"));

        inv.setItem(22, item(Material.TARGET,
                "<red><bold>Радиус атаки</bold>",
                "<gray>Сейчас: <white>" + String.format("%.1f", settings.getAttackRadius()) + " блоков",
                "",
                "<yellow>▶ ЛКМ +1  <yellow>▶ ПКМ -1"));

        inv.setItem(24, item(Material.CLOCK,
                "<red><bold>Задержка удара</bold>",
                "<gray>Сейчас: <white>" + settings.getAttackCooldown() + " тиков",
                "",
                "<yellow>▶ ЛКМ +2  <yellow>▶ ПКМ -2"));

        inv.setItem(31, item(Material.SHIELD,
                "<red><bold>Точка охраны</bold>",
                "<gray>" + (bot.getGuardPoint() == null
                        ? "<dark_gray>не задана"
                        : "<white>" + fmt(bot.getGuardPoint())),
                "",
                "<yellow>▶ Клик — назначить текущую позицию бота"));

        inv.setItem(36, item(Material.ARROW, "<gray>◀ Назад"));
        viewer.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Характеристики
    // ------------------------------------------------------------------

    public void openStats(Player viewer, PhantomBot bot) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.BOT_STATS, bot);
        Inventory inv = Bukkit.createInventory(holder, 45,
                Msg.item("<dark_gray>» <green>Характеристики: <white>" + bot.getName()));
        holder.setInventory(inv);
        decorate(inv);

        BotSettings settings = bot.getSettings();
        Player bukkit = bot.getBukkitPlayer();

        inv.setItem(11, item(Material.RED_DYE,
                "<green><bold>Максимум здоровья</bold>",
                "<gray>Сейчас: <white>" + String.format("%.1f", settings.getMaxHealth()) + " HP",
                "<gray>Текущее: <white>" + (bukkit == null ? "?" : String.format("%.1f", bukkit.getHealth())),
                "",
                "<yellow>▶ ЛКМ +2  <yellow>▶ ПКМ -2",
                "<yellow>▶ Shift+ЛКМ — полностью вылечить"));

        inv.setItem(13, item(Material.FEATHER,
                "<green><bold>Скорость ходьбы</bold>",
                "<gray>Сейчас: <white>" + String.format("%.2f", settings.getWalkSpeed()),
                "<dark_gray>обычная скорость игрока = 0.20",
                "",
                "<yellow>▶ ЛКМ +0.02  <yellow>▶ ПКМ -0.02"));

        inv.setItem(15, item(gameModeIcon(settings.getGameMode()),
                "<green><bold>Игровой режим</bold>",
                "<gray>Сейчас: <yellow>" + settings.getGameMode().name(),
                "",
                "<yellow>▶ Клик — следующий режим"));

        inv.setItem(20, item(settings.isInvulnerable() ? Material.NETHERITE_CHESTPLATE : Material.LEATHER_CHESTPLATE,
                "<green><bold>Неуязвимость</bold>",
                "<gray>Бот бессмертен: " + Msg.toggle(settings.isInvulnerable()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(22, item(settings.isAutoRespawn() ? Material.TOTEM_OF_UNDYING : Material.BONE,
                "<green><bold>Авто-респавн</bold>",
                "<gray>Возрождать после смерти: " + Msg.toggle(settings.isAutoRespawn()),
                "<gray>Задержка: <white>" + (settings.getAutoRespawnDelay() / 20.0) + " сек",
                "",
                "<yellow>▶ ЛКМ — переключить",
                "<yellow>▶ ПКМ +1 сек  <yellow>▶ Shift+ПКМ -1 сек"));

        inv.setItem(24, item(settings.isDropInventoryOnDeath() ? Material.DROPPER : Material.HOPPER,
                "<green><bold>Дроп вещей</bold>",
                "<gray>Ронять инвентарь при смерти: " + Msg.toggle(settings.isDropInventoryOnDeath()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(31, item(Material.EXPERIENCE_BOTTLE,
                "<green><bold>Скопировать твои статы</bold>",
                "<gray>Бот получит твоё здоровье,",
                "<gray>скорость и игровой режим.",
                "",
                "<yellow>▶ Клик — применить"));

        inv.setItem(36, item(Material.ARROW, "<gray>◀ Назад"));
        viewer.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Реализм
    // ------------------------------------------------------------------

    public void openRealism(Player viewer, PhantomBot bot) {
        GuiHolder holder = new GuiHolder(GuiHolder.Type.BOT_REALISM, bot);
        Inventory inv = Bukkit.createInventory(holder, 45,
                Msg.item("<dark_gray>» <gold>Реализм: <white>" + bot.getName()));
        holder.setInventory(inv);
        decorate(inv);

        BotSettings settings = bot.getSettings();

        inv.setItem(11, item(Material.COOKED_BEEF,
                "<gold><bold>Голод</bold>",
                "<gray>Бот испытывает голод: " + Msg.toggle(settings.isHunger()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(13, item(Material.LAVA_BUCKET,
                "<gold><bold>Урон от огня</bold>",
                "<gray>Горит в огне и лаве: " + Msg.toggle(settings.isTakeFireDamage()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(15, item(Material.WATER_BUCKET,
                "<gold><bold>Утопление</bold>",
                "<gray>Захлёбывается под водой: " + Msg.toggle(settings.isDrowning()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(20, item(Material.OBSERVER,
                "<gold><bold>Живые движения</bold>",
                "<gray>Крутит головой в простое: " + Msg.toggle(settings.isIdleHeadMovement()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(22, item(Material.STICK,
                "<gold><bold>Случайные взмахи</bold>",
                "<gray>Иногда машет рукой: " + Msg.toggle(settings.isIdleSwing()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(24, item(Material.WRITABLE_BOOK,
                "<gold><bold>Чат</bold>",
                "<gray>Может писать в чат: " + Msg.toggle(settings.isChatEnabled()),
                "",
                "<yellow>▶ Клик — переключить"));

        inv.setItem(36, item(Material.ARROW, "<gray>◀ Назад"));
        viewer.openInventory(inv);
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    private Material gameModeIcon(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> Material.IRON_SWORD;
            case CREATIVE -> Material.BRICKS;
            case ADVENTURE -> Material.MAP;
            case SPECTATOR -> Material.ENDER_EYE;
        };
    }

    private String fmt(org.bukkit.Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private void decorate(Inventory inv) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private void fillRow(Inventory inv, int base) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = base; i < base + 9 && i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Msg.item(name));
        if (lore.length > 0) {
            meta.lore(Msg.lore(lore));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        return item(material, name, lore.toArray(new String[0]));
    }
}
