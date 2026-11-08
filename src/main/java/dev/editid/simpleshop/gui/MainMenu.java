package dev.editid.simpleshop.gui;

import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.gui.layout.ButtonSpec;
import dev.editid.simpleshop.gui.layout.MenuLayout;
import dev.editid.simpleshop.shop.Category;
import dev.editid.simpleshop.util.Text;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.Player;

import static dev.editid.simpleshop.util.Messages.mm;
import static dev.editid.simpleshop.util.Messages.p;

/** Category overview. In edit mode it is the entry point of the price editor. */
public final class MainMenu extends Menu {

    private final boolean edit;

    public MainMenu(SimpleShopPlugin plugin, Player player, boolean edit) {
        super(plugin, player, plugin.layouts().main().rows(), Text.parse(edit
                ? plugin.layouts().text("editor.title", "<dark_red>Shop Editor")
                : plugin.layouts().main().string("title", "<dark_gray>Shop")));
        this.edit = edit;
    }

    public MainMenu(SimpleShopPlugin plugin, Player player) {
        this(plugin, player, false);
    }

    @Override
    protected void render() {
        MenuLayout layout = plugin.layouts().main();
        int size = getInventory().getSize();
        ButtonSpec search = layout.button("search"), balance = layout.button("balance"), sell = layout.button("sell");

        Set<Integer> reserved = new HashSet<>();
        for (ButtonSpec spec : List.of(search, balance, sell)) if (spec.visibleIn(size)) reserved.add(spec.slot());

        // Categories with a valid explicit slot first, then auto-place the rest in free slots.
        Map<Integer, Category> slots = new LinkedHashMap<>();
        List<Category> unplaced = new ArrayList<>();
        for (Category category : plugin.shop().categories().values()) {
            int slot = category.slot();
            if (slot >= 0 && slot < size && !reserved.contains(slot) && !slots.containsKey(slot)) slots.put(slot, category);
            else unplaced.add(category);
        }
        int next = 0;
        for (Category category : unplaced) {
            while (next < size && (slots.containsKey(next) || reserved.contains(next))) next++;
            if (next >= size) {
                plugin.getLogger().warning("No room in the main menu for category " + category.id() + "; increase main.rows in menus.yml.");
                continue;
            }
            slots.put(next, category);
        }

        List<String> lore = layout.lines("category-lore");
        slots.forEach((slot, category) -> set(slot,
                ButtonSpec.item(category.icon(), 1, category.displayName(), lore,
                        mm("category", category.displayName()), p("items", category.items().size())),
                e -> go(ItemListMenu.category(plugin, player, category, 0, edit))));

        if (!edit) {
            place(search, e -> search());
            place(sell, e -> {
                if (!player.hasPermission("simpleshop.sell")) {
                    plugin.messages().send(player, "no-permission");
                    return;
                }
                go(new SellMenu(plugin, player));
            });
        }
        place(balance, null, p("balance", plugin.service().money(plugin.service().balance(player))));
        fill(layout);
    }

    private void search() {
        plugin.service().click(player);
        Dialogs.prompt(plugin, player, "search-title", "search-label", "",
                query -> {
                    if (query.isEmpty()) open();
                    else ItemListMenu.openSearch(plugin, player, query);
                },
                this::open);
    }
}
