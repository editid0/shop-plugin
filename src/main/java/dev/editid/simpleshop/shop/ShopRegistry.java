package dev.editid.simpleshop.shop;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Immutable snapshot of shop.yml. Replaced wholesale on reload. */
public final class ShopRegistry {

    private final Map<String, Category> categories;
    private final Map<Material, ShopItem> sellLookup = new EnumMap<>(Material.class);
    private final Map<String, ShopItem> byId = new HashMap<>();
    private final int itemCount;

    private ShopRegistry(Map<String, Category> categories) {
        this.categories = Collections.unmodifiableMap(categories);
        int count = 0;
        for (Category category : categories.values()) {
            for (ShopItem item : category.items()) {
                count++;
                if (item.canSell()) sellLookup.putIfAbsent(item.material(), item);
                byId.putIfAbsent(item.id().toLowerCase(Locale.ROOT), item);
            }
        }
        this.itemCount = count;
    }

    public static ShopRegistry load(File file, Logger logger) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Category> categories = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("categories");
        if (root == null) {
            logger.warning("shop.yml has no 'categories' section; the shop is empty.");
            return new ShopRegistry(categories);
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null || !sec.getBoolean("enabled", true)) continue;
            String id = key.toLowerCase(Locale.ROOT);

            Material icon = parseMaterial(sec.getString("icon", "CHEST"), logger, "icon of category " + key);
            if (icon == null || !icon.isItem() || icon.isAir()) icon = Material.CHEST;

            List<ShopItem> items = new ArrayList<>();
            ConfigurationSection itemsSec = sec.getConfigurationSection("items");
            if (itemsSec != null) {
                for (String itemKey : itemsSec.getKeys(false)) {
                    ShopItem item = parseItem(id, itemKey, itemsSec.getConfigurationSection(itemKey), logger);
                    if (item != null) items.add(item);
                }
            }
            categories.put(id, new Category(id, key, sec.getString("name", key), icon, sec.getInt("slot", -1), List.copyOf(items)));
        }
        return new ShopRegistry(categories);
    }

    private static ShopItem parseItem(String categoryId, String key, ConfigurationSection sec, Logger logger) {
        String where = "category " + categoryId;
        List<String> commands = sec == null ? List.of() : sec.getStringList("commands");
        String materialName = sec != null && sec.isString("material") ? sec.getString("material") : key;
        Material material = parseMaterial(materialName, logger, where);
        if (material == null) return null;
        if (!material.isItem() || material.isAir()) {
            logger.warning("Skipping " + key + " in " + where + ": not an obtainable item.");
            return null;
        }

        double buy = sec == null ? 0 : sec.getDouble("buy", 0);
        double sell = sec == null ? 0 : sec.getDouble("sell", 0);
        if (buy <= 0 && (sell <= 0 || !commands.isEmpty())) {
            logger.warning("Skipping " + key + " in " + where + ": no usable buy or sell price.");
            return null;
        }
        if (commands.isEmpty() && buy > 0 && sell > buy) {
            logger.warning(key + " in " + where + " sells for more than it costs; players can loop it for infinite money!");
        }

        boolean command = !commands.isEmpty();
        return new ShopItem(
                categoryId,
                key,
                command ? key : material.name(),
                material,
                sec == null ? null : sec.getString("name"),
                sec == null ? List.of() : List.copyOf(sec.getStringList("lore")),
                buy,
                sell,
                List.copyOf(commands),
                sec == null ? 0 : sec.getInt("max-amount", 0),
                sec == null ? !command : sec.getBoolean("dynamic", !command),
                sec == null ? 0 : sec.getDouble("volume", 0),
                sec == null ? null : sec.getString("permission"));
    }

    private static Material parseMaterial(String name, Logger logger, String where) {
        Material material = name == null ? null : Material.matchMaterial(name);
        if (material == null) logger.warning("Unknown material '" + name + "' in " + where + ", skipping.");
        return material;
    }

    public Map<String, Category> categories() {
        return categories;
    }

    public Category category(String id) {
        return categories.get(id.toLowerCase(Locale.ROOT));
    }

    /** The current version of an item (after a reload or edit), or null if it was removed. */
    public ShopItem refresh(ShopItem item) {
        Category category = categories.get(item.categoryId());
        if (category == null) return null;
        for (ShopItem candidate : category.items()) {
            if (candidate.key().equals(item.key())) return candidate;
        }
        return null;
    }

    /** First sellable (non-command) entry for this material, or null. */
    public ShopItem sellable(Material material) {
        return sellLookup.get(material);
    }

    /** Lookup by id (material name or command item key), case-insensitive. */
    public ShopItem byId(String id) {
        return byId.get(id.toLowerCase(Locale.ROOT));
    }

    /** Items whose name or id contains the query, without duplicates. */
    public List<ShopItem> search(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim().replace('_', ' ');
        List<ShopItem> results = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (q.isEmpty()) return results;
        for (Category category : categories.values()) {
            for (ShopItem item : category.items()) {
                String name = item.plainName().toLowerCase(Locale.ROOT);
                String id = item.id().toLowerCase(Locale.ROOT).replace('_', ' ');
                if ((name.contains(q) || id.contains(q)) && seen.add(item.id())) results.add(item);
            }
        }
        return results;
    }

    public int itemCount() {
        return itemCount;
    }
}
