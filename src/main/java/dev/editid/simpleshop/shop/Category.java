package dev.editid.simpleshop.shop;

import java.util.List;
import org.bukkit.Material;

/** @param key the category's key in shop.yml (id is the lower-cased key) */
public record Category(String id, String key, String displayName, Material icon, int slot, List<ShopItem> items) {
}
