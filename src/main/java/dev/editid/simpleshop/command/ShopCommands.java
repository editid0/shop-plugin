package dev.editid.simpleshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.gui.ItemListMenu;
import dev.editid.simpleshop.gui.MainMenu;
import dev.editid.simpleshop.gui.Menu;
import dev.editid.simpleshop.gui.SellMenu;
import dev.editid.simpleshop.log.TransactionLog;
import dev.editid.simpleshop.shop.Category;
import dev.editid.simpleshop.shop.ShopItem;
import dev.editid.simpleshop.shop.ShopService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static dev.editid.simpleshop.util.Messages.p;

/**
 * /shop [category] | search &lt;query&gt; | worth [all] | edit | log [player] | resetprices [item] | reload
 * /sell [hand|all]
 * /worth [all]
 */
public final class ShopCommands {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final SimpleShopPlugin plugin;

    public ShopCommands(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean has(CommandSourceStack src, String permission) {
        return src.getSender().hasPermission(permission);
    }

    public void register(Commands commands) {
        SuggestionProvider<CommandSourceStack> categories = (ctx, builder) -> {
            String input = builder.getRemainingLowerCase();
            for (String id : plugin.shop().categories().keySet()) if (id.startsWith(input)) builder.suggest(id);
            return builder.buildFuture();
        };
        SuggestionProvider<CommandSourceStack> items = (ctx, builder) -> {
            String input = builder.getRemainingLowerCase();
            plugin.shop().categories().values().stream()
                    .flatMap(c -> c.items().stream())
                    .map(i -> i.id().toLowerCase(Locale.ROOT))
                    .distinct()
                    .filter(id -> id.startsWith(input))
                    .limit(100)
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };

        commands.register(Commands.literal("shop")
                        .requires(src -> has(src, "simpleshop.use"))
                        .executes(ctx -> openMenu(ctx, player -> new MainMenu(plugin, player)))
                        .then(Commands.literal("search")
                                .then(Commands.argument("query", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String query = StringArgumentType.getString(ctx, "query");
                                            return runAsPlayer(ctx, player -> ItemListMenu.openSearch(plugin, player, query));
                                        })))
                        .then(Commands.literal("worth")
                                .requires(src -> has(src, "simpleshop.worth"))
                                .executes(ctx -> runAsPlayer(ctx, player -> worth(player, false)))
                                .then(Commands.literal("all").executes(ctx -> runAsPlayer(ctx, player -> worth(player, true)))))
                        .then(Commands.literal("edit")
                                .requires(src -> has(src, "simpleshop.edit"))
                                .executes(ctx -> openMenu(ctx, player -> new MainMenu(plugin, player, true))))
                        .then(Commands.literal("log")
                                .requires(src -> has(src, "simpleshop.log"))
                                .executes(ctx -> log(ctx.getSource().getSender(), null))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> log(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player")))))
                        .then(Commands.literal("resetprices")
                                .requires(src -> has(src, "simpleshop.admin"))
                                .executes(ctx -> {
                                    plugin.market().resetAll();
                                    plugin.messages().send(ctx.getSource().getSender(), "market-reset", p("target", "all items"));
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests(items)
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "item");
                                            ShopItem item = plugin.shop().byId(id);
                                            if (item == null) {
                                                plugin.messages().send(ctx.getSource().getSender(), "item-unavailable");
                                                return 0;
                                            }
                                            plugin.market().reset(item.id());
                                            plugin.messages().send(ctx.getSource().getSender(), "market-reset", p("target", item.plainName()));
                                            return Command.SINGLE_SUCCESS;
                                        })))
                        .then(Commands.literal("reload")
                                .requires(src -> has(src, "simpleshop.admin"))
                                .executes(this::reload))
                        .then(Commands.argument("category", StringArgumentType.word())
                                .suggests(categories)
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "category");
                                    Category category = plugin.shop().category(id);
                                    if (category == null) {
                                        plugin.messages().send(ctx.getSource().getSender(), "unknown-category", p("category", id));
                                        return 0;
                                    }
                                    return openMenu(ctx, player -> ItemListMenu.category(plugin, player, category, 0, false));
                                }))
                        .build(),
                "Open the item shop",
                List.of("store"));

        commands.register(Commands.literal("sell")
                        .requires(src -> has(src, "simpleshop.sell"))
                        .executes(ctx -> openMenu(ctx, player -> new SellMenu(plugin, player)))
                        .then(Commands.literal("hand").executes(ctx -> runAsPlayer(ctx, this::sellHand)))
                        .then(Commands.literal("all").executes(ctx -> runAsPlayer(ctx, player -> plugin.service().sellInventory(player))))
                        .build(),
                "Sell items to the shop",
                List.of());

        commands.register(Commands.literal("worth")
                        .requires(src -> has(src, "simpleshop.worth"))
                        .executes(ctx -> runAsPlayer(ctx, player -> worth(player, false)))
                        .then(Commands.literal("all").executes(ctx -> runAsPlayer(ctx, player -> worth(player, true))))
                        .build(),
                "Check what your items sell for",
                List.of());
    }

    private Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (ctx.getSource().getExecutor() instanceof Player player) return player;
        if (sender instanceof Player player) return player;
        plugin.messages().send(sender, "players-only");
        return null;
    }

    private int openMenu(CommandContext<CommandSourceStack> ctx, Function<Player, Menu> factory) {
        return runAsPlayer(ctx, player -> factory.apply(player).open());
    }

    /** Runs on the player's own thread: required on Folia, harmless on Paper. */
    private int runAsPlayer(CommandContext<CommandSourceStack> ctx, Consumer<Player> action) {
        Player player = player(ctx);
        if (player == null) return 0;
        player.getScheduler().run(plugin, task -> action.accept(player), null);
        return Command.SINGLE_SUCCESS;
    }

    private void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        ShopItem item = hand.isEmpty() ? null : plugin.shop().sellable(hand.getType());
        if (item == null || !ShopService.isPlain(hand, hand.getType())) {
            plugin.messages().send(player, "not-sellable");
            plugin.service().fail(player);
            return;
        }
        plugin.service().sell(player, item, hand.getAmount(), Double.NaN);
    }

    private void worth(Player player, boolean all) {
        ShopService service = plugin.service();
        ShopService.Worth worth = service.worth(player, all);
        if (worth.amount() == 0) {
            plugin.messages().send(player, all ? "nothing-to-sell" : "worth-none");
            return;
        }
        if (all) {
            plugin.messages().send(player, "worth-all", p("amount", worth.amount()), p("price", service.money(worth.total())));
        } else {
            ShopItem item = plugin.shop().sellable(player.getInventory().getItemInMainHand().getType());
            plugin.messages().send(player, "worth-hand",
                    p("amount", worth.amount()),
                    p("item", item == null ? "" : item.plainName()),
                    p("price", service.money(worth.total())),
                    p("unit", service.money(item == null ? 0 : plugin.market().unitSell(item))));
        }
    }

    private int log(CommandSender sender, String player) {
        List<TransactionLog.Entry> entries = plugin.transactions().recent(player, 10);
        if (entries.isEmpty()) {
            plugin.messages().send(sender, "log-empty");
            return Command.SINGLE_SUCCESS;
        }
        plugin.messages().send(sender, "log-header", p("filter", player == null ? "" : " for " + player));
        for (TransactionLog.Entry e : entries.reversed()) {
            ShopItem item = plugin.shop().byId(e.item());
            plugin.messages().send(sender, "log-entry",
                    p("time", LOG_TIME.format(Instant.ofEpochMilli(e.time()))),
                    p("player", e.player()),
                    p("type", e.type().name()),
                    p("amount", e.amount()),
                    p("item", item == null ? e.item() : item.plainName()),
                    p("price", plugin.service().money(e.total())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        plugin.reload();
        plugin.messages().send(ctx.getSource().getSender(), "reloaded",
                p("categories", plugin.shop().categories().size()),
                p("items", plugin.shop().itemCount()));
        return Command.SINGLE_SUCCESS;
    }
}
