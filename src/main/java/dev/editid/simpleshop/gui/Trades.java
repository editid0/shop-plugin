package dev.editid.simpleshop.gui;

import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.market.Market;
import dev.editid.simpleshop.shop.ShopItem;
import dev.editid.simpleshop.shop.ShopService;
import org.bukkit.entity.Player;

/** Starts a buy/sell from a menu, routing through the confirm screen when needed. */
final class Trades {

    private Trades() {
    }

    /**
     * @param amount  units, or -1 for max (buy) / all (sell)
     * @param current menu to refresh afterwards, or return to from the confirm screen
     */
    static void execute(SimpleShopPlugin plugin, Player player, ShopItem item, boolean buying, int amount, Menu current) {
        ShopService service = plugin.service();
        Market market = plugin.market();

        int resolved = amount;
        if (amount < 0) resolved = buying ? service.maxBuyAmount(player, item) : service.owned(player, item);

        // Not possible as requested: let the service perform it so it reports the exact reason.
        boolean possible = resolved > 0 && (buying
                ? resolved <= service.maxBuyAmount(player, item)
                : resolved <= service.owned(player, item));
        if (!possible) {
            if (buying) service.buy(player, item, amount < 0 ? -1 : resolved, Double.NaN);
            else service.sell(player, item, amount < 0 ? -1 : resolved, Double.NaN);
            current.refresh();
            return;
        }

        double total = buying ? market.quoteBuy(item, resolved) : market.quoteSell(item, resolved);
        if (service.needsConfirm(item, total)) {
            service.click(player);
            current.openNext(new ConfirmMenu(plugin, player, item, buying, resolved, total, current));
            return;
        }
        if (buying) service.buy(player, item, resolved, Double.NaN);
        else service.sell(player, item, resolved, Double.NaN);
        current.refresh();
    }
}
