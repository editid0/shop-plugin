package dev.editid.simpleshop.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Routes inventory events to our menus and stops items being taken out of them. */
public final class MenuListener implements Listener {

    private static Menu menu(Inventory top) {
        return top.getHolder(false) instanceof Menu menu ? menu : null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Menu menu = menu(event.getView().getTopInventory());
        if (menu == null || menu.isEditable()) return;

        // Cancel everything (including shift-clicks and number keys from the player inventory).
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getClickedInventory() == menu.getInventory()) menu.onClick(event);
        else if (event.getClickedInventory() == event.getView().getBottomInventory()) menu.onPlayerInventoryClick(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        Menu menu = menu(event.getView().getTopInventory());
        if (menu != null && !menu.isEditable()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Menu menu = menu(event.getInventory());
        if (menu != null) menu.onClose();
    }
}
