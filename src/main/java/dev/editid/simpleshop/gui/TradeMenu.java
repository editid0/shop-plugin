package dev.editid.simpleshop.gui;

import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.gui.layout.ButtonSpec;
import dev.editid.simpleshop.gui.layout.MenuLayout;
import dev.editid.simpleshop.market.Market;
import dev.editid.simpleshop.shop.ShopItem;
import dev.editid.simpleshop.shop.ShopService;
import dev.editid.simpleshop.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import static dev.editid.simpleshop.util.Messages.mm;
import static dev.editid.simpleshop.util.Messages.p;

/** Choose how many of one item to buy or sell. */
public final class TradeMenu extends Menu {

    private final ShopItem original;
    private final boolean buying;
    private final Supplier<Menu> back;

    public TradeMenu(SimpleShopPlugin plugin, Player player, ShopItem item, boolean buying, Supplier<Menu> back) {
        super(plugin, player, plugin.layouts().trade().rows(), Text.parse(
                plugin.layouts().trade().string(buying ? "title-buy" : "title-sell", buying ? "Buy <item>" : "Sell <item>"),
                mm("item", item.displayName())));
        this.original = item;
        this.buying = buying;
        this.back = back;
    }

    @Override
    protected void render() {
        ShopItem refreshed = plugin.shop().refresh(original);
        ShopItem item = refreshed != null ? refreshed : original;
        MenuLayout layout = plugin.layouts().trade();
        ShopService service = plugin.service();
        Market market = plugin.market();

        double balance = service.balance(player);
        int owned = service.owned(player, item);
        TagResolver itemName = mm("item", item.displayName());
        TagResolver ownedP = p("owned", owned);
        TagResolver balanceP = p("balance", service.money(balance));
        TagResolver price = p("price", service.money(buying ? market.unitBuy(item) : market.unitSell(item)));

        ButtonSpec info = layout.button("info");
        if (info.visibleIn(getInventory().getSize())) {
            set(info.slot(), info.build(item.material(), 1, itemName, ownedP, balanceP, price), null);
        }

        // Amount buttons
        List<Integer> amounts = layout.ints("amounts");
        List<Integer> slots = layout.ints("amount-slots");
        String section = buying ? "amount-buy" : "amount-sell";
        for (int i = 0; i < Math.min(amounts.size(), slots.size()); i++) {
            int amount = amounts.get(i);
            if (amount < 1 || (buying && amount > item.amountLimit())) continue;
            double total = buying ? market.quoteBuy(item, amount) : market.quoteSell(item, amount);
            List<String> lore = new ArrayList<>(layout.lines(section + ".lore"));
            if (buying && balance + 1e-9 < total) lore.addFirst(layout.string("warning-afford", "<red>You can't afford this"));
            if (!buying && owned < amount) lore.addFirst(layout.string("warning-items", "<red>You only have <owned>"));
            set(slots.get(i), ButtonSpec.item(item.material(), amount, layout.string(section + ".name", "<amount>x"), lore,
                            p("amount", amount), p("total", service.money(total)), itemName, ownedP, balanceP),
                    e -> Trades.execute(plugin, player, item, buying, amount, this));
        }

        // Max / all
        if (buying) {
            place(layout.button("max-buy"), e -> Trades.execute(plugin, player, item, true, -1, this), itemName, balanceP);
        } else {
            place(layout.button("max-sell"), e -> Trades.execute(plugin, player, item, false, -1, this),
                    itemName, ownedP, p("total", service.money(market.quoteSell(item, owned))));
        }

        // Custom amount
        int max = buying ? item.amountLimit() : owned;
        place(layout.button("custom"), e -> {
            plugin.service().click(player);
            Dialogs.prompt(plugin, player, "amount-title", buying ? "amount-label-buy" : "amount-label-sell", "",
                    input -> {
                        int amount = parseAmount(input);
                        if (amount < 1 || amount > Math.max(1, max)) {
                            plugin.messages().send(player, "invalid-amount", p("max", Math.max(1, max)));
                            plugin.service().fail(player);
                            refresh();
                            return;
                        }
                        Trades.execute(plugin, player, item, buying, amount, this);
                    },
                    this::refresh,
                    itemName, p("max", max));
        }, itemName);

        place(layout.button("back"), e -> go(back.get()));
        if (buying && item.canSell()) place(layout.button("switch-to-sell"), e -> go(new TradeMenu(plugin, player, item, false, back)));
        if (!buying && item.canBuy()) place(layout.button("switch-to-buy"), e -> go(new TradeMenu(plugin, player, item, true, back)));
        place(layout.button("close"), e -> closeNext());
        fill(layout);
    }

    private static int parseAmount(String input) {
        try {
            return Integer.parseInt(input.replace(",", "").replace("_", "").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
