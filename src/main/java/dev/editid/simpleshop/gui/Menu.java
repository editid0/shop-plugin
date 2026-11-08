package dev.editid.simpleshop.gui;

import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.gui.layout.ButtonSpec;
import dev.editid.simpleshop.gui.layout.MenuLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all shop GUIs. The holder identifies our inventories in {@link MenuListener}.
 * Subclasses draw in {@link #render()} and attach click handlers to slots with {@link #set}.
 */
public abstract class Menu implements InventoryHolder {

    protected final SimpleShopPlugin plugin;
    protected final Player player;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(SimpleShopPlugin plugin, Player player, int rows, Component title) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, Math.clamp(rows, 1, 6) * 9, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player player() {
        return player;
    }

    /** Draws the menu contents. */
    protected abstract void render();

    /** Called for clicks in the top (menu) inventory. The event is already cancelled. */
    protected void onClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) action.accept(event);
    }

    /** Called for clicks in the player's own inventory while this (non-editable) menu is open. */
    protected void onPlayerInventoryClick(InventoryClickEvent event) {
    }

    protected void onClose() {
    }

    /** Whether players may move items in/out of this menu (only the sell box). */
    protected boolean isEditable() {
        return false;
    }

    public void open() {
        redraw();
        player.openInventory(inventory);
    }

    /** Redraws; reopens the menu if the player isn't looking at it (e.g. after a dialog). */
    public void refresh() {
        if (player.getOpenInventory().getTopInventory() == inventory) redraw();
        else open();
    }

    private void redraw() {
        inventory.clear();
        actions.clear();
        render();
    }

    /** Opens another menu on the player's scheduler (safe on Paper and Folia). */
    public void openNext(Menu next) {
        player.getScheduler().run(plugin, task -> next.open(), null);
    }

    protected void closeNext() {
        player.getScheduler().run(plugin, task -> player.closeInventory(), null);
    }

    // ------------------------------------------------------------------ drawing helpers

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    /** Places a configured button if it has a valid slot. */
    protected void place(ButtonSpec spec, Consumer<InventoryClickEvent> action, TagResolver... placeholders) {
        if (spec.visibleIn(inventory.getSize())) set(spec.slot(), spec.build(placeholders), action);
    }

    protected void fill(MenuLayout layout) {
        Material material = layout.filler();
        if (material == null) return;
        ItemStack filler = ItemStack.of(material);
        filler.editMeta(meta -> meta.setHideTooltip(true));
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, filler);
        }
    }

    /** Click sound plus open another menu. */
    protected void go(Menu next) {
        plugin.service().click(player);
        openNext(next);
    }
}
