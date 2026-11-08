package dev.editid.simpleshop.gui;

import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.editor.ShopEditor;
import dev.editid.simpleshop.gui.layout.ButtonSpec;
import dev.editid.simpleshop.gui.layout.MenuLayout;
import dev.editid.simpleshop.market.Market;
import dev.editid.simpleshop.shop.Category;
import dev.editid.simpleshop.shop.ShopItem;
import dev.editid.simpleshop.shop.ShopService;
import dev.editid.simpleshop.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import static dev.editid.simpleshop.util.Messages.mm;
import static dev.editid.simpleshop.util.Messages.p;

/** Paged item list: one category, or search results. */
public final class ItemListMenu extends Menu {

    private final Category category; // null for search results
    private final String query;      // null for categories
    private final List<ShopItem> items;
    private final boolean edit;
    private final int perPage;
    private final int page;
    private final int pages;

    private ItemListMenu(SimpleShopPlugin plugin, Player player, String title, Category category, String query,
                         List<ShopItem> items, int page, boolean edit) {
        super(plugin, player, Math.max(2, plugin.layouts().category().rows()), Text.parse(title,
                mm("category", category == null ? "" : category.displayName()), p("query", query == null ? "" : query)));
        this.category = category;
        this.query = query;
        this.items = items;
        this.edit = edit;
        this.perPage = getInventory().getSize() - 9;
        this.pages = Math.max(1, (items.size() + perPage - 1) / perPage);
        this.page = Math.clamp(page, 0, pages - 1);
    }

    public static ItemListMenu category(SimpleShopPlugin plugin, Player player, Category category, int page, boolean edit) {
        String title = edit ? plugin.layouts().text("editor.category-title", "<dark_red>Edit: <category>") : "<category>";
        return new ItemListMenu(plugin, player, title, category, null, category.items(), page, edit);
    }

    public static ItemListMenu search(SimpleShopPlugin plugin, Player player, String query, int page) {
        String title = plugin.layouts().text("search.title", "<dark_gray>Search: <query>");
        return new ItemListMenu(plugin, player, title, null, query, plugin.shop().search(query), page, false);
    }

    /** Opens search results, or reports no matches and returns to the main menu. */
    public static void openSearch(SimpleShopPlugin plugin, Player player, String query) {
        ItemListMenu menu = search(plugin, player, query, 0);
        if (menu.items.isEmpty()) {
            plugin.messages().send(player, "search-none", p("query", query));
            plugin.service().fail(player);
            new MainMenu(plugin, player).open();
        } else {
            menu.open();
        }
    }

    /** A fresh copy of this menu, picking up reloads and edits. */
    private Menu copy(int newPage) {
        if (category == null) return search(plugin, player, query, newPage);
        Category fresh = plugin.shop().category(category.id());
        return fresh == null ? new MainMenu(plugin, player, edit) : category(plugin, player, fresh, newPage, edit);
    }

    private Supplier<Menu> self() {
        return () -> copy(page);
    }

    @Override
    protected void render() {
        MenuLayout layout = plugin.layouts().category();
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < items.size(); i++) {
            ShopItem item = items.get(start + i);
            set(i, display(layout, item), e -> click(item, e));
        }

        TagResolver balance = p("balance", plugin.service().money(plugin.service().balance(player)));
        TagResolver pages = p("pages", this.pages);
        place(layout.button("back"), e -> go(new MainMenu(plugin, player, edit)), balance);
        if (page > 0) place(layout.button("previous"), e -> go(copy(page - 1)), p("page", page), pages);
        if (page < this.pages - 1) place(layout.button("next"), e -> go(copy(page + 1)), p("page", page + 2), pages);
        place(layout.button("balance"), null, balance);
        place(layout.button("close"), e -> closeNext());
        if (edit) place(layout.button("editor-hint"), null);
        fill(layout);
    }

    private ItemStack display(MenuLayout layout, ShopItem item) {
        ShopService service = plugin.service();
        Market market = plugin.market();
        int stack = item.stackSize();

        List<String> lore = new ArrayList<>(item.lore());
        lore.addAll(layout.lines(item.canBuy() ? "item.buy" : "item.no-buy"));
        if (!item.isCommand()) lore.addAll(layout.lines(item.canSell() ? "item.sell" : "item.no-sell"));
        if (market.isDynamic(item)) lore.addAll(layout.lines("item.market"));
        if (edit) {
            lore.addAll(layout.lines("item.editor"));
        } else if (item.isCommand()) {
            if (item.canBuy()) lore.addAll(layout.lines("item.actions-command"));
        } else {
            if (item.canBuy()) lore.addAll(layout.lines("item.actions-buy"));
            if (item.canSell()) lore.addAll(layout.lines("item.actions-sell"));
        }

        return ButtonSpec.item(item.material(), 1, layout.string("item.name", "<white><item>"), lore,
                mm("item", item.displayName()),
                p("buy", service.money(market.unitBuy(item))),
                p("buy_stack", service.money(market.quoteBuy(item, stack))),
                p("sell", service.money(market.unitSell(item))),
                p("sell_stack", service.money(market.quoteSell(item, stack))),
                p("stack", stack),
                p("owned", service.owned(player, item)),
                mm("trend", trend(plugin, item)));
    }

    /** MiniMessage text describing how far the price is from its base. */
    public static String trend(SimpleShopPlugin plugin, ShopItem item) {
        MenuLayout layout = plugin.layouts().category();
        long percent = Math.round((plugin.market().multiplier(item) - 1.0) * 100.0);
        if (percent == 0) return layout.string("trend.stable", "<gray>● base price");
        String template = layout.string(percent > 0 ? "trend.up" : "trend.down", percent > 0 ? "<red>▲ +<percent>%" : "<green>▼ -<percent>%");
        return template.replace("<percent>", Long.toString(Math.abs(percent)));
    }

    private void click(ShopItem item, InventoryClickEvent event) {
        if (edit) {
            plugin.service().click(player);
            if (event.getClick().isShiftClick() && event.getClick().isRightClick()) ShopEditor.openRemove(plugin, player, item, self());
            else ShopEditor.openEdit(plugin, player, item, self());
            return;
        }
        if (item.isCommand()) {
            if (!item.canBuy()) return;
            if (item.amountLimit() == 1) Trades.execute(plugin, player, item, true, 1, this);
            else go(new TradeMenu(plugin, player, item, true, self()));
            return;
        }
        switch (event.getClick()) {
            case LEFT -> { if (item.canBuy()) go(new TradeMenu(plugin, player, item, true, self())); }
            case SHIFT_LEFT -> { if (item.canBuy()) Trades.execute(plugin, player, item, true, item.stackSize(), this); }
            case RIGHT -> { if (item.canSell()) go(new TradeMenu(plugin, player, item, false, self())); }
            case SHIFT_RIGHT -> { if (item.canSell()) Trades.execute(plugin, player, item, false, -1, this); }
            default -> {
            }
        }
    }

    @Override
    protected void onPlayerInventoryClick(InventoryClickEvent event) {
        if (!edit || category == null) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.isEmpty()) return;
        plugin.service().click(player);
        ShopEditor.openAdd(plugin, player, category, clicked.getType(), self());
    }
}
