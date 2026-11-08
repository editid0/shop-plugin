package dev.editid.simpleshop.gui.layout;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** All menu layouts from menus.yml. Missing keys fall back to the defaults bundled in the jar. */
public final class Layouts {

    private final MenuLayout main, category, trade, confirm, sell;
    private final ConfigurationSection root;

    private Layouts(ConfigurationSection root) {
        this.root = root;
        this.main = new MenuLayout(section(root, "main"), 4);
        this.category = new MenuLayout(section(root, "category"), 6);
        this.trade = new MenuLayout(section(root, "trade"), 3);
        this.confirm = new MenuLayout(section(root, "confirm"), 3);
        this.sell = new MenuLayout(section(root, "sell"), 6);
    }

    public static Layouts load(File file, InputStream defaults) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (defaults != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        return new Layouts(yaml);
    }

    private static ConfigurationSection section(ConfigurationSection root, String path) {
        ConfigurationSection sec = root.getConfigurationSection(path);
        return sec != null ? sec : new MemoryConfiguration();
    }

    public MenuLayout main() { return main; }
    public MenuLayout category() { return category; }
    public MenuLayout trade() { return trade; }
    public MenuLayout confirm() { return confirm; }
    public MenuLayout sell() { return sell; }

    /** Any other text from menus.yml, e.g. "editor.title". */
    public String text(String path, String def) {
        return root.getString(path, def);
    }
}
