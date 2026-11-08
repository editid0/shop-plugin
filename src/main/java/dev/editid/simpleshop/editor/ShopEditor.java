package dev.editid.simpleshop.editor;

import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.gui.Dialogs;
import dev.editid.simpleshop.gui.Menu;
import dev.editid.simpleshop.shop.Category;
import dev.editid.simpleshop.shop.ShopItem;
import dev.editid.simpleshop.util.Text;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import static dev.editid.simpleshop.util.Messages.mm;
import static dev.editid.simpleshop.util.Messages.p;

/** In-game price editor: writes changes to shop.yml and reloads the shop. */
public final class ShopEditor {

    private ShopEditor() {
    }

    // ------------------------------------------------------------------ GUI flows

    /** Edit buy/sell price (and optionally reset the dynamic price) of an existing item. */
    public static void openEdit(SimpleShopPlugin plugin, Player player, ShopItem item, Supplier<Menu> reopen) {
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(priceInput(plugin, "buy", "editor-buy", "Buy price", item.buyPrice()));
        if (!item.isCommand()) inputs.add(priceInput(plugin, "sell", "editor-sell", "Sell price", item.sellPrice()));
        if (plugin.market().isDynamic(item)) {
            inputs.add(DialogInput.bool("reset", Text.parse(Dialogs.label(plugin, "editor-reset", "Reset dynamic price"))).initial(false).build());
        }
        Dialogs.show(plugin, player, Dialogs.label(plugin, "editor-title", "Edit <item>"),
                List.of(Dialogs.message(plugin, "editor-body", "Prices are per item. 0 disables buying or selling.")),
                inputs, "save",
                response -> {
                    Double buy = price(plugin, player, response, "buy");
                    Double sell = item.isCommand() ? Double.valueOf(0) : price(plugin, player, response, "sell");
                    if (buy == null || sell == null) {
                        reopen.get().open();
                        return;
                    }
                    String error = setPrices(plugin, item, buy, sell);
                    if (error != null) {
                        plugin.messages().send(player, "editor-failed", p("reason", error));
                    } else {
                        if (Boolean.TRUE.equals(response.getBoolean("reset"))) plugin.market().reset(item.id());
                        plugin.messages().send(player, "editor-saved", p("item", item.plainName()),
                                p("buy", plugin.service().money(buy)), p("sell", plugin.service().money(sell)));
                    }
                    reopen.get().open();
                },
                () -> reopen.get().open(),
                mm("item", item.displayName()));
    }

    /** Add a material to a category with prices typed in a dialog. */
    public static void openAdd(SimpleShopPlugin plugin, Player player, Category category, Material material, Supplier<Menu> reopen) {
        String name = Text.prettyName(material);
        for (ShopItem existing : category.items()) {
            if (!existing.isCommand() && existing.material() == material) {
                plugin.messages().send(player, "editor-exists", p("item", name));
                plugin.service().fail(player);
                return;
            }
        }
        List<DialogInput> inputs = List.of(
                priceInput(plugin, "buy", "editor-buy", "Buy price", 0),
                priceInput(plugin, "sell", "editor-sell", "Sell price", 0));
        Dialogs.show(plugin, player, Dialogs.label(plugin, "add-title", "Add <item>"),
                List.of(Dialogs.message(plugin, "editor-body", "Prices are per item. 0 disables buying or selling.")),
                inputs, "save",
                response -> {
                    Double buy = price(plugin, player, response, "buy");
                    Double sell = price(plugin, player, response, "sell");
                    if (buy == null || sell == null) {
                        reopen.get().open();
                        return;
                    }
                    String error = add(plugin, category, material, buy, sell);
                    if (error != null) plugin.messages().send(player, "editor-failed", p("reason", error));
                    else plugin.messages().send(player, "editor-added", p("item", name), mm("category", category.displayName()));
                    reopen.get().open();
                },
                () -> reopen.get().open(),
                p("item", name));
    }

    public static void openRemove(SimpleShopPlugin plugin, Player player, ShopItem item, Supplier<Menu> reopen) {
        Dialogs.show(plugin, player, Dialogs.label(plugin, "remove-title", "Remove <item>?"),
                List.of(Dialogs.message(plugin, "remove-body", "This deletes the item from shop.yml.")),
                List.of(), "confirm",
                response -> {
                    String error = remove(plugin, item);
                    if (error != null) plugin.messages().send(player, "editor-failed", p("reason", error));
                    else plugin.messages().send(player, "editor-removed", p("item", item.plainName()));
                    reopen.get().open();
                },
                () -> reopen.get().open(),
                mm("item", item.displayName()));
    }

    private static DialogInput priceInput(SimpleShopPlugin plugin, String key, String labelKey, String def, double initial) {
        return DialogInput.text(key, Text.parse(Dialogs.label(plugin, labelKey, def)))
                .initial(initial > 0 ? plain(initial) : "0")
                .maxLength(16)
                .width(200)
                .build();
    }

    /** Parses a price input, messaging the player and returning null when invalid. */
    private static Double price(SimpleShopPlugin plugin, Player player, DialogResponseView response, String key) {
        String raw = response.getText(key);
        Double value = parsePrice(raw);
        if (value == null) {
            plugin.messages().send(player, "editor-invalid-price", p("value", raw == null ? "" : raw));
            plugin.service().fail(player);
        }
        return value;
    }

    /** "12.5", "$1,000", "" (= 0). Null if invalid or negative. */
    public static Double parsePrice(String raw) {
        if (raw == null) return 0.0;
        String s = raw.replace("$", "").replace(",", "").replace("_", "").trim();
        if (s.isEmpty()) return 0.0;
        try {
            double v = Double.parseDouble(s);
            if (!Double.isFinite(v) || v < 0 || v > 1e12) return null;
            return Text.round(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ shop.yml changes

    private static String itemPath(SimpleShopPlugin plugin, String categoryId, String key) {
        Category category = plugin.shop().category(categoryId);
        if (category == null) return null;
        return "categories." + category.key() + ".items." + key;
    }

    public static String setPrices(SimpleShopPlugin plugin, ShopItem item, double buy, double sell) {
        if (buy <= 0 && sell <= 0) return "an item needs a buy or sell price (remove it instead)";
        if (item.isCommand() && buy <= 0) return "command items need a buy price";
        String path = itemPath(plugin, item.categoryId(), item.key());
        if (path == null) return "category no longer exists";
        return modify(plugin, yaml -> {
            ConfigurationSection sec = yaml.getConfigurationSection(path);
            if (sec == null) sec = yaml.createSection(path);
            sec.set("buy", buy > 0 ? buy : null);
            sec.set("sell", sell > 0 ? sell : null);
            return null;
        });
    }

    public static String add(SimpleShopPlugin plugin, Category category, Material material, double buy, double sell) {
        if (buy <= 0 && sell <= 0) return "an item needs a buy or sell price";
        String path = itemPath(plugin, category.id(), material.name());
        if (path == null) return "category no longer exists";
        return modify(plugin, yaml -> {
            if (yaml.isConfigurationSection(path)) return material.name() + " is already in this category";
            ConfigurationSection sec = yaml.createSection(path);
            if (buy > 0) sec.set("buy", buy);
            if (sell > 0) sec.set("sell", sell);
            return null;
        });
    }

    public static String remove(SimpleShopPlugin plugin, ShopItem item) {
        String path = itemPath(plugin, item.categoryId(), item.key());
        if (path == null) return "category no longer exists";
        return modify(plugin, yaml -> {
            yaml.set(path, null);
            return null;
        });
    }

    /** Loads shop.yml, applies a change, saves it and reloads the shop. Returns an error or null. */
    private static synchronized String modify(SimpleShopPlugin plugin, Function<YamlConfiguration, String> change) {
        File file = new File(plugin.getDataFolder(), "shop.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String error = change.apply(yaml);
        if (error != null) return error;
        try {
            yaml.save(file);
        } catch (IOException e) {
            return e.getMessage();
        }
        plugin.reloadShop();
        return null;
    }
}
