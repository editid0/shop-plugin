package dev.editid.simpleshop.gui.layout;

import dev.editid.simpleshop.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/** One configurable button from menus.yml. A negative slot hides it. */
public record ButtonSpec(int slot, Material material, String name, List<String> lore) {

    public static final ButtonSpec HIDDEN = new ButtonSpec(-1, Material.STONE, "", List.of());

    static ButtonSpec parse(ConfigurationSection sec) {
        if (sec == null) return HIDDEN;
        Material material = Material.matchMaterial(sec.getString("material", "STONE"));
        if (material == null || !material.isItem() || material.isAir()) material = Material.STONE;
        return new ButtonSpec(sec.getInt("slot", -1), material, sec.getString("name", ""), List.copyOf(sec.getStringList("lore")));
    }

    public boolean visibleIn(int size) {
        return slot >= 0 && slot < size;
    }

    public ItemStack build(TagResolver... placeholders) {
        return build(material, 1, placeholders);
    }

    /** Builds the button with a different icon (used for item-specific buttons). */
    public ItemStack build(Material icon, int amount, TagResolver... placeholders) {
        return item(icon, amount, name, lore, placeholders);
    }

    /** Creates a GUI item with MiniMessage name and lore, hiding vanilla tooltips. */
    public static ItemStack item(Material material, int amount, String name, List<String> lore, TagResolver... placeholders) {
        ItemStack item = ItemStack.of(material, Math.clamp(amount, 1, Math.max(1, material.getMaxStackSize())));
        item.editMeta(meta -> {
            meta.displayName(Text.item(name, placeholders));
            if (!lore.isEmpty()) {
                List<Component> lines = new ArrayList<>(lore.size());
                for (String line : lore) lines.add(Text.item(line, placeholders));
                meta.lore(lines);
            }
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }
}
