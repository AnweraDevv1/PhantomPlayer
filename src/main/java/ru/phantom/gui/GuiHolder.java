package ru.phantom.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.phantom.core.PhantomBot;

/** Маркер меню плагина, чтобы отличать наши инвентари от чужих. */
public class GuiHolder implements InventoryHolder {

    /** Тип открытого экрана. */
    public enum Type {
        BOT_LIST,
        BOT_MAIN,
        BOT_APPEARANCE,
        BOT_BEHAVIOR,
        BOT_STATS,
        BOT_REALISM
    }

    private final Type type;
    private final PhantomBot bot;
    private Inventory inventory;

    public GuiHolder(Type type, PhantomBot bot) {
        this.type = type;
        this.bot = bot;
    }

    public Type getType() {
        return type;
    }

    public PhantomBot getBot() {
        return bot;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
