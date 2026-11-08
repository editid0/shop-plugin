package dev.editid.simpleshop.shop;

import dev.editid.simpleshop.Settings;
import dev.editid.simpleshop.util.Text;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;

/**
 * A single shop entry. Prices are base prices per item; 0 or less means "not available".
 *
 * @param key        the key in shop.yml (e.g. "STONE" or "vip_rank")
 * @param id         market / lookup id: material name for normal items, key for command items
 * @param name       custom MiniMessage name, or null to use the material's name
 * @param commands   console commands; if not empty this is a command item that gives no item
 * @param maxAmount  most that can be bought at once (0 = no limit)
 * @param volume     dynamic-pricing volume override in units (0 = use config default)
 * @param permission permission required to buy, or null
 */
public record ShopItem(
        String categoryId,
        String key,
        String id,
        Material material,
        String name,
        List<String> lore,
        double buyPrice,
        double sellPrice,
        List<String> commands,
        int maxAmount,
        boolean dynamic,
        double volume,
        String permission) {

    public boolean isCommand() {
        return !commands.isEmpty();
    }

    public boolean canBuy() {
        return buyPrice > 0;
    }

    public boolean canSell() {
        return sellPrice > 0 && !isCommand();
    }

    /** Most that can be bought in one transaction. */
    public int amountLimit() {
        if (isCommand()) return maxAmount <= 0 ? 1 : Math.min(maxAmount, Settings.MAX_AMOUNT);
        return maxAmount <= 0 ? Settings.MAX_AMOUNT : Math.min(maxAmount, Settings.MAX_AMOUNT);
    }

    /** MiniMessage display name. */
    public String displayName() {
        return name != null ? name : Text.prettyName(material);
    }

    /** Display name without formatting, for chat messages and logs. */
    public String plainName() {
        return name != null ? MiniMessage.miniMessage().stripTags(name) : Text.prettyName(material);
    }

    public int stackSize() {
        return isCommand() ? 1 : material.getMaxStackSize();
    }
}
