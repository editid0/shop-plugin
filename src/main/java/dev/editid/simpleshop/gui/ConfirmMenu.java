package dev.editid.simpleshop.gui;

import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.gui.layout.ButtonSpec;
import dev.editid.simpleshop.gui.layout.MenuLayout;
import dev.editid.simpleshop.shop.ShopItem;
import dev.editid.simpleshop.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import static dev.editid.simpleshop.util.Messages.mm;
import static dev.editid.simpleshop.util.Messages.p;

/** "Are you sure?" screen. The confirmed total is enforced, so a price change can't surprise the player. */
public final class ConfirmMenu extends Menu {

    private final ShopItem item;
    private final boolean buying;
    private final int amount;
    private final double total;
    private final Menu returnTo;

    ConfirmMenu(SimpleShopPlugin plugin, Player player, ShopItem item, boolean buying, int amount, double total, Menu returnTo) {
        super(plugin, player, plugin.layouts().confirm().rows(),
                Text.parse(plugin.layouts().confirm().string("title", "<dark_gray>Confirm <action>"), p("action", buying ? "Buy" : "Sell")));
        this.item = item;
        this.buying = buying;
        this.amount = amount;
        this.total = total;
        this.returnTo = returnTo;
    }

    @Override
    protected void render() {
        MenuLayout layout = plugin.layouts().confirm();
        TagResolver[] placeholders = {
                p("action", buying ? "Buy" : "Sell"),
                p("amount", amount),
                mm("item", item.displayName()),
                p("total", plugin.service().money(total)),
                p("balance", plugin.service().money(plugin.service().balance(player)))
        };
        place(layout.button("confirm"), e -> {
            if (buying) plugin.service().buy(player, item, amount, total);
            else plugin.service().sell(player, item, amount, total);
            openNext(returnTo);
        }, placeholders);
        ButtonSpec info = layout.button("info");
        if (info.visibleIn(getInventory().getSize())) set(info.slot(), info.build(item.material(), 1, placeholders), null);
        place(layout.button("cancel"), e -> go(returnTo), placeholders);
        fill(layout);
    }
}
