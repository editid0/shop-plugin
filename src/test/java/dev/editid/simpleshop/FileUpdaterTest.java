package dev.editid.simpleshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.editid.simpleshop.config.FileUpdater;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUpdaterTest {

    @TempDir
    Path dir;

    private final Map<String, String> jar = new HashMap<>();

    private FileUpdater updater() {
        return new FileUpdater(dir.toFile(), name -> {
            String text = jar.get(name);
            if (text == null) return FileUpdaterTest.class.getClassLoader().getResourceAsStream(name);
            return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        }, Logger.getAnonymousLogger());
    }

    private YamlConfiguration read(String name) {
        return YamlConfiguration.loadConfiguration(dir.resolve(name).toFile());
    }

    private void write(String name, String text) throws Exception {
        Files.createDirectories(dir.resolve(name).getParent());
        Files.writeString(dir.resolve(name), text);
    }

    @Test
    void freshInstallCreatesAllFiles() {
        updater().run();
        assertTrue(Files.exists(dir.resolve("config.yml")));
        assertTrue(Files.exists(dir.resolve("menus.yml")));
        assertTrue(Files.exists(dir.resolve("shop.yml")));
        assertTrue(Files.exists(dir.resolve("data/shop-defaults.yml")));
        assertFalse(Files.exists(dir.resolve("backups")), "nothing changed, nothing backed up");
    }

    @Test
    void upgradeFrom100MigratesAndAddsSettings() throws Exception {
        write("config.yml", """
                use-economy-format: false
                currency-format: "%s Coins"
                sounds: true
                amounts: [ 1, 2, 3 ]
                menu:
                  main-title: "<red>My Shop"
                  main-rows: 5
                  sell-title: "<red>Sell"
                  filler: BLACK_STAINED_GLASS_PANE
                messages:
                  prefix: "<red>[S] "
                """);
        write("shop.yml", """
                categories:
                  building:
                    name: "Blocks"
                    icon: STONE
                    items:
                      STONE: { buy: 5, sell: 1 }
                """);
        updater().run();

        YamlConfiguration config = read("config.yml");
        assertFalse(config.getBoolean("use-economy-format"), "user value kept");
        assertEquals("%s Coins", config.getString("currency-format"));
        assertEquals("<red>[S] ", config.getString("messages.prefix"));
        assertTrue(config.contains("dynamic-pricing.money-volume"), "new setting added");
        assertTrue(config.contains("messages.worth-hand"), "new message added");
        assertFalse(config.contains("menu"), "legacy section removed");
        assertFalse(config.contains("amounts"));
        assertEquals(2, config.getInt("config-version"));
        assertFalse(config.getComments("dynamic-pricing").isEmpty(), "comments copied");

        YamlConfiguration menus = read("menus.yml");
        assertEquals("<red>My Shop", menus.getString("main.title"));
        assertEquals(5, menus.getInt("main.rows"));
        assertEquals("<red>Sell", menus.getString("sell.title"));
        assertEquals(java.util.List.of(1, 2, 3), menus.getIntegerList("trade.amounts"));
        assertEquals("BLACK_STAINED_GLASS_PANE", menus.getString("trade.filler"));

        YamlConfiguration shop = read("shop.yml");
        assertEquals(5, shop.getDouble("categories.building.items.STONE.buy"), "user price kept");
        assertFalse(shop.contains("categories.building.items.DIRT"), "no per-item adds without tracker");
        assertNotNull(shop.getConfigurationSection("categories.ores"), "missing default category added");

        assertTrue(Files.list(dir.resolve("backups")).count() >= 2, "backups written");
    }

    @Test
    void newDefaultItemsAddedButDeletedOnesStayDeleted() throws Exception {
        jar.put("shop.yml", """
                categories:
                  building:
                    name: "Blocks"
                    icon: STONE
                    items:
                      STONE: { buy: 2, sell: 0.5 }
                      DIRT: { buy: 1, sell: 0.1 }
                """);
        updater().run(); // fresh install

        // Admin deletes DIRT and changes STONE.
        YamlConfiguration shop = read("shop.yml");
        shop.set("categories.building.items.DIRT", null);
        shop.set("categories.building.items.STONE.buy", 9);
        shop.save(dir.resolve("shop.yml").toFile());

        // New version adds SAND and a new category.
        jar.put("shop.yml", """
                categories:
                  building:
                    name: "Blocks"
                    icon: STONE
                    items:
                      STONE: { buy: 2, sell: 0.5 }
                      DIRT: { buy: 1, sell: 0.1 }
                      SAND: { buy: 2, sell: 0.5 }
                  food:
                    name: "Food"
                    icon: BREAD
                    items:
                      BREAD: { buy: 10, sell: 2.5 }
                """);
        updater().run();

        shop = read("shop.yml");
        assertEquals(9, shop.getDouble("categories.building.items.STONE.buy"), "edit kept");
        assertFalse(shop.contains("categories.building.items.DIRT"), "deleted item not re-added");
        assertEquals(2, shop.getDouble("categories.building.items.SAND.buy"), "new item added");
        assertEquals(10, shop.getDouble("categories.food.items.BREAD.buy"), "new category added");

        // Running again changes nothing.
        String before = Files.readString(dir.resolve("shop.yml"));
        updater().run();
        assertEquals(before, Files.readString(dir.resolve("shop.yml")));
    }

    @Test
    void userRemovedCategoryNotRefilled() throws Exception {
        updater().run();
        YamlConfiguration shop = read("shop.yml");
        shop.set("categories.food", null);
        shop.save(dir.resolve("shop.yml").toFile());
        updater().run();
        assertFalse(read("shop.yml").contains("categories.food"));
    }
}
