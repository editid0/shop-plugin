package dev.editid.simpleshop.gui.layout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** Title, size, filler and buttons of one menu, plus access to its extra settings. */
public final class MenuLayout {

    private final ConfigurationSection section;
    private final Map<String, ButtonSpec> buttons = new HashMap<>();
    private final int rows;
    private final Material filler;

    MenuLayout(ConfigurationSection section, int defaultRows) {
        this.section = section;
        this.rows = Math.clamp(section.getInt("rows", defaultRows), 1, 6);
        Material fill = Material.matchMaterial(section.getString("filler", "AIR"));
        this.filler = fill == null || !fill.isItem() || fill.isAir() ? null : fill;
        ConfigurationSection buttonSec = section.getConfigurationSection("buttons");
        if (buttonSec != null) {
            for (String key : buttonSec.getKeys(false)) buttons.put(key, ButtonSpec.parse(buttonSec.getConfigurationSection(key)));
        }
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return rows * 9;
    }

    /** Filler material, or null for empty slots. */
    public Material filler() {
        return filler;
    }

    public ButtonSpec button(String name) {
        return buttons.getOrDefault(name, ButtonSpec.HIDDEN);
    }

    public String string(String path, String def) {
        return section.getString(path, def);
    }

    public List<String> lines(String path) {
        return section.getStringList(path);
    }

    public List<Integer> ints(String path) {
        return section.getIntegerList(path);
    }

    public ConfigurationSection section(String path) {
        return section.getConfigurationSection(path);
    }
}
