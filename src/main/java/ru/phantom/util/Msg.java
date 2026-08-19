package ru.phantom.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/** Утилиты для форматирования текста (MiniMessage). */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static String prefix = "<gradient:#8A2BE2:#00E5FF><bold>Phantom</bold></gradient> <dark_gray>»</dark_gray> ";

    private Msg() {
    }

    public static void setPrefix(String value) {
        if (value != null && !value.isEmpty()) {
            prefix = value;
        }
    }

    public static Component mm(String text) {
        return MM.deserialize(text);
    }

    /** Текст для предметов GUI: без курсива по умолчанию. */
    public static Component item(String text) {
        return MM.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(item(line));
        }
        return out;
    }

    public static void send(CommandSender to, String text) {
        to.sendMessage(MM.deserialize(prefix + text));
    }

    public static void raw(CommandSender to, String text) {
        to.sendMessage(MM.deserialize(text));
    }

    /** Цветной индикатор вкл/выкл. */
    public static String toggle(boolean value) {
        return value ? "<green>ВКЛ</green>" : "<red>ВЫКЛ</red>";
    }
}
