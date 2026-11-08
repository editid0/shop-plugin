package dev.editid.simpleshop.integration;

import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.gui.ItemListMenu;
import dev.editid.simpleshop.shop.ShopItem;
import dev.editid.simpleshop.shop.ShopService;
import dev.editid.simpleshop.util.Text;
import java.util.Locale;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI placeholders. ITEM is a material name (e.g. DIAMOND) or command item id.
 * <ul>
 *   <li>%simpleshop_buy_ITEM% / %simpleshop_buy_raw_ITEM%: current buy price (formatted / number)</li>
 *   <li>%simpleshop_sell_ITEM% / %simpleshop_sell_raw_ITEM%: current sell price</li>
 *   <li>%simpleshop_base_buy_ITEM% / %simpleshop_base_sell_ITEM%: base prices from shop.yml</li>
 *   <li>%simpleshop_multiplier_ITEM%: dynamic price multiplier, e.g. 0.85</li>
 *   <li>%simpleshop_trend_ITEM%: e.g. "▲ +12%"</li>
 *   <li>%simpleshop_worth% / %simpleshop_worth_raw%: sell value of the player's inventory</li>
 *   <li>%simpleshop_worth_hand% / %simpleshop_worth_hand_raw%: sell value of the held stack</li>
 * </ul>
 * Only loaded when PlaceholderAPI is installed.
 */
public final class ShopExpansion extends PlaceholderExpansion {

    private final SimpleShopPlugin plugin;

    public ShopExpansion(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "simpleshop";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        String p = params.toLowerCase(Locale.ROOT);
        ShopService service = plugin.service();

        if (p.startsWith("worth")) {
            if (!(offline instanceof Player player) || !player.isOnline()) return "";
            boolean hand = p.startsWith("worth_hand");
            double total = service.worth(player, !hand).total();
            return p.endsWith("_raw") ? raw(total) : service.money(total);
        }

        String[] prefixes = {"base_buy_", "base_sell_", "buy_raw_", "sell_raw_", "buy_", "sell_", "multiplier_", "trend_"};
        for (String prefix : prefixes) {
            if (!p.startsWith(prefix)) continue;
            ShopItem item = plugin.shop().byId(params.substring(prefix.length()));
            if (item == null) return "";
            return switch (prefix) {
                case "base_buy_" -> service.money(item.buyPrice());
                case "base_sell_" -> service.money(item.sellPrice());
                case "buy_raw_" -> raw(plugin.market().unitBuy(item));
                case "sell_raw_" -> raw(plugin.market().unitSell(item));
                case "buy_" -> service.money(plugin.market().unitBuy(item));
                case "sell_" -> service.money(plugin.market().unitSell(item));
                case "multiplier_" -> String.format(Locale.ROOT, "%.2f", plugin.market().multiplier(item));
                default -> LegacyComponentSerializer.legacySection().serialize(Text.parse(ItemListMenu.trend(plugin, item)));
            };
        }
        return null;
    }

    private static String raw(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
