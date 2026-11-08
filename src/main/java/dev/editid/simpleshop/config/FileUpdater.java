package dev.editid.simpleshop.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Keeps the plugin's files up to date across versions. Runs on every start and reload:
 * <ul>
 *   <li>creates missing files from the jar</li>
 *   <li>migrates 1.0.0 menu settings from config.yml into menus.yml</li>
 *   <li>adds new settings (with their comments) to config.yml and menus.yml, keeping existing values</li>
 *   <li>adds new default categories/items to shop.yml. A tracker in data/shop-defaults.yml remembers
 *       which defaults were already offered, so items an admin deleted are never re-added.</li>
 * </ul>
 * Every file is backed up to backups/ before it is changed.
 */
public final class FileUpdater {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String TRACKER = "data/shop-defaults.yml";

    private final File folder;
    private final Function<String, InputStream> resources;
    private final Logger logger;

    public FileUpdater(File folder, Function<String, InputStream> resources, Logger logger) {
        this.folder = folder;
        this.resources = resources;
        this.logger = logger;
    }

    public void run() {
        folder.mkdirs();
        boolean menusExisted = file("menus.yml").exists();
        boolean shopExisted = file("shop.yml").exists();
        for (String name : List.of("config.yml", "menus.yml", "shop.yml")) createIfMissing(name);

        migrateLegacyMenuSettings(menusExisted);
        mergeSettings("config.yml");
        mergeSettings("menus.yml");
        addNewShopDefaults(shopExisted);
    }

    // ------------------------------------------------------------------ helpers

    private File file(String name) {
        return new File(folder, name);
    }

    private YamlConfiguration bundled(String name) {
        InputStream in = resources.apply(name);
        if (in == null) return new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }

    private void createIfMissing(String name) {
        File target = file(name);
        if (target.exists()) return;
        try (InputStream in = resources.apply(name)) {
            if (in != null) Files.copy(in, target.toPath());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not create " + name, e);
        }
    }

    private void backup(String name) {
        File source = file(name);
        if (!source.exists()) return;
        File dir = new File(folder, "backups");
        dir.mkdirs();
        String base = name.endsWith(".yml") ? name.substring(0, name.length() - 4) : name;
        File target = new File(dir, base + "-" + STAMP.format(LocalDateTime.now()) + ".yml");
        try {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not back up " + name, e);
        }
    }

    private boolean save(YamlConfiguration yaml, String name) {
        try {
            yaml.save(file(name));
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + name, e);
            return false;
        }
    }

    private static String summary(List<String> keys) {
        if (keys.size() <= 6) return String.join(", ", keys);
        return String.join(", ", keys.subList(0, 6)) + " and " + (keys.size() - 6) + " more";
    }

    /** Recursively copies a section, including comments. */
    private static void copySection(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            if (from.isConfigurationSection(key)) {
                copySection(from.getConfigurationSection(key), to.createSection(key));
            } else {
                to.set(key, from.get(key));
            }
            to.setComments(key, from.getComments(key));
            to.setInlineComments(key, from.getInlineComments(key));
        }
    }

    // ------------------------------------------------------------------ 1.0.0 -> 1.1.0

    /** 1.0.0 kept menu settings in config.yml ('menu.*' and 'amounts'); they now live in menus.yml. */
    private void migrateLegacyMenuSettings(boolean menusExisted) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file("config.yml"));
        if (!config.isConfigurationSection("menu") && !config.contains("amounts")) return;

        YamlConfiguration menus = YamlConfiguration.loadConfiguration(file("menus.yml"));
        List<String> moved = new ArrayList<>();
        // Only carry values over into a menus.yml we just created, so we never overwrite newer edits.
        if (!menusExisted) {
            moveValue(config, "menu.main-title", menus, "main.title", moved);
            moveValue(config, "menu.main-rows", menus, "main.rows", moved);
            moveValue(config, "menu.sell-title", menus, "sell.title", moved);
            moveValue(config, "amounts", menus, "trade.amounts", moved);
            if (config.isString("menu.filler")) {
                String filler = config.getString("menu.filler");
                for (String menu : List.of("main", "category", "trade", "confirm")) menus.set(menu + ".filler", filler);
                moved.add("menu.filler");
            }
        }

        backup("config.yml");
        config.set("menu", null);
        config.set("amounts", null);
        save(config, "config.yml");
        if (!moved.isEmpty()) {
            backup("menus.yml");
            save(menus, "menus.yml");
        }
        logger.info("Moved old menu settings from config.yml to menus.yml" + (moved.isEmpty() ? "." : ": " + summary(moved)));
    }

    private static void moveValue(YamlConfiguration from, String fromPath, YamlConfiguration to, String toPath, List<String> moved) {
        if (!from.contains(fromPath)) return;
        to.set(toPath, from.get(fromPath));
        moved.add(fromPath);
    }

    // ------------------------------------------------------------------ settings files

    /** Adds settings that exist in the bundled file but not in the user's, and warns about unused ones. */
    private void mergeSettings(String name) {
        YamlConfiguration defaults = bundled(name);
        YamlConfiguration user = YamlConfiguration.loadConfiguration(file(name));

        List<String> added = new ArrayList<>();
        merge(defaults, user, "", added);

        int bundledVersion = defaults.getInt("config-version", 0);
        boolean versionChanged = user.getInt("config-version", 0) < bundledVersion;
        if (versionChanged) user.set("config-version", bundledVersion);

        List<String> unused = new ArrayList<>();
        for (String key : user.getKeys(true)) {
            if (!user.isConfigurationSection(key) && !defaults.contains(key)) unused.add(key);
        }
        if (!unused.isEmpty()) {
            logger.warning(name + " has settings that do nothing (typo or removed option?): " + summary(unused));
        }

        if (added.isEmpty() && !versionChanged) return;
        backup(name);
        if (save(user, name) && !added.isEmpty()) {
            logger.info("Added " + added.size() + " new setting(s) to " + name + ": " + summary(added));
        }
    }

    private static void merge(ConfigurationSection defaults, ConfigurationSection user, String prefix, List<String> added) {
        for (String key : defaults.getKeys(false)) {
            String path = prefix + key;
            if (defaults.isConfigurationSection(key)) {
                if (user.contains(key) && !user.isConfigurationSection(key)) continue; // user replaced it with a value
                ConfigurationSection target = user.getConfigurationSection(key);
                if (target == null) {
                    target = user.createSection(key);
                    user.setComments(key, defaults.getComments(key));
                }
                merge(defaults.getConfigurationSection(key), target, path + ".", added);
            } else if (!user.contains(key)) {
                user.set(key, defaults.get(key));
                user.setComments(key, defaults.getComments(key));
                user.setInlineComments(key, defaults.getInlineComments(key));
                added.add(path);
            }
        }
    }

    // ------------------------------------------------------------------ shop.yml

    private void addNewShopDefaults(boolean shopExisted) {
        YamlConfiguration defaults = bundled("shop.yml");
        ConfigurationSection defCategories = defaults.getConfigurationSection("categories");
        if (defCategories == null) return;

        // Every default category ("cat") and item ("cat/ITEM") in this version.
        Set<String> current = new LinkedHashSet<>();
        for (String cat : defCategories.getKeys(false)) {
            current.add(cat);
            ConfigurationSection items = defCategories.getConfigurationSection(cat + ".items");
            if (items != null) for (String item : items.getKeys(false)) current.add(cat + "/" + item);
        }

        File trackerFile = file(TRACKER);
        YamlConfiguration tracker = YamlConfiguration.loadConfiguration(trackerFile);
        Set<String> known = new LinkedHashSet<>(tracker.getStringList("known"));
        YamlConfiguration user = YamlConfiguration.loadConfiguration(file("shop.yml"));
        List<String> added = new ArrayList<>();

        if (!shopExisted) {
            // Fresh install: shop.yml was just created with every default.
            known.addAll(current);
        } else if (!trackerFile.exists()) {
            // Upgrade from a version without the tracker: we can't tell deleted items from new ones,
            // so only add whole categories that are missing, then start tracking.
            for (String key : current) {
                if (!key.contains("/") && !user.isConfigurationSection("categories." + key)) {
                    copySection(defCategories.getConfigurationSection(key), user.createSection("categories." + key));
                    added.add(key);
                }
            }
            known.addAll(current);
        } else {
            for (String key : current) {
                if (known.contains(key)) continue;
                known.add(key);
                int slash = key.indexOf('/');
                if (slash < 0) {
                    if (!user.isConfigurationSection("categories." + key)) {
                        copySection(defCategories.getConfigurationSection(key), user.createSection("categories." + key));
                        added.add(key);
                    }
                } else {
                    String cat = key.substring(0, slash), item = key.substring(slash + 1);
                    // Only add to categories the admin still has.
                    ConfigurationSection userCat = user.getConfigurationSection("categories." + cat);
                    if (userCat == null) continue;
                    ConfigurationSection items = userCat.getConfigurationSection("items");
                    if (items == null) items = userCat.createSection("items");
                    if (items.contains(item)) continue;
                    ConfigurationSection from = defCategories.getConfigurationSection(cat + ".items." + item);
                    if (from != null) copySection(from, items.createSection(item));
                    else items.set(item, defCategories.get(cat + ".items." + item));
                    added.add(cat + "/" + item);
                }
            }
        }

        if (!added.isEmpty()) {
            backup("shop.yml");
            if (save(user, "shop.yml")) {
                logger.info("Added " + added.size() + " new default shop entr" + (added.size() == 1 ? "y" : "ies") + " to shop.yml: " + summary(added));
            }
        }

        if (!known.equals(new LinkedHashSet<>(tracker.getStringList("known")))) {
            tracker.options().setHeader(List.of(
                    "Default shop entries SimpleShop has already offered. Entries listed here are never",
                    "re-added to shop.yml, so deleting an item keeps it deleted. Don't edit."));
            tracker.set("known", new ArrayList<>(known));
            trackerFile.getParentFile().mkdirs();
            try {
                tracker.save(trackerFile);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not save " + TRACKER, e);
            }
        }
    }
}
