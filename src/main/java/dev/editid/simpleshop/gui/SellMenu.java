package dev.editid.simpleshop.gui;

import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A free-form box: put items in, close it, and everything sellable is sold. */
public final class SellMenu extends Menu {

    private boolean processed;

    public SellMenu(SimpleShopPlugin plugin, Player player) {
        super(plugin, player, plugin.layouts().sell().rows(),
                Text.parse(plugin.layouts().sell().string("title", "<dark_gray>Sell items")));
    }

    @Override
    protected void render() {
    }

    @Override
    protected boolean isEditable() {
        return true;
    }

    @Override
    protected void onClose() {
        if (processed) return;
        processed = true;
        ItemStack[] contents = getInventory().getContents();
        getInventory().clear();
        plugin.service().sellContents(player, contents);
    }

    /** Hands everything back without selling (used on plugin shutdown). */
    public void returnItems() {
        if (processed) return;
        processed = true;
        ItemStack[] contents = getInventory().getContents();
        getInventory().clear();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : contents) if (stack != null && !stack.isEmpty()) items.add(stack);
        plugin.service().give(player, items);
    }
}
