package dev.editid.simpleshop.shop;

import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.economy.EconomyProvider;
import dev.editid.simpleshop.log.TransactionLog;
import dev.editid.simpleshop.market.Market;
import dev.editid.simpleshop.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import static dev.editid.simpleshop.util.Messages.p;

/**
 * All buy/sell logic. Every method that touches a player must be called on that player's
 * thread (the main thread on Paper, the player's region thread on Folia).
 */
public final class ShopService {

    /** Hotbar + main inventory; armor and offhand are never touched. */
    private static final int STORAGE_SLOTS = 36;
    /** Tolerance when comparing a confirmed price with the current price. */
    private static final double PRICE_EPSILON = 0.005;

    private static final Sound SUCCESS = Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.PLAYER, 0.6f, 1.2f);
    private static final Sound FAIL = Sound.sound(Key.key("block.note_block.bass"), Sound.Source.PLAYER, 0.8f, 0.6f);
    private static final Sound CLICK = Sound.sound(Key.key("ui.button.click"), Sound.Source.PLAYER, 0.4f, 1.0f);

    /** Result of {@link #worth}. */
    public record Worth(int amount, double total) {
    }

    private final SimpleShopPlugin plugin;

    public ShopService(SimpleShopPlugin plugin) {
        this.plugin = plugin;
    }

    private EconomyProvider economy() {
        return plugin.economy();
    }

    private Market market() {
        return plugin.market();
    }

    // ------------------------------------------------------------------ formatting & prices

    public String money(double amount) {
        amount = Text.round(amount);
        if (plugin.settings().useEconomyFormat() && economy().isAvailable()) {
            String formatted = economy().format(amount);
            if (formatted != null && !formatted.isBlank()) return formatted;
        }
        return plugin.settings().currencyFormat().replace("%s", String.format("%,.2f", amount));
    }

    public double balance(Player player) {
        return economy().isAvailable() ? economy().balance(player) : 0;
    }

    /** Whether a transaction of this size should go through the confirm screen. */
    public boolean needsConfirm(ShopItem item, double total) {
        var s = plugin.settings();
        if (!s.confirmEnabled()) return false;
        return total >= s.confirmAbove() || (item.isCommand() && s.confirmCommandItems());
    }

    // ------------------------------------------------------------------ inventory helpers

    /** True for a stack of exactly this material with no custom name, enchantments, damage, ... */
    public static boolean isPlain(ItemStack stack, Material material) {
        return stack != null && stack.getType() == material && stack.isSimilar(ItemStack.of(material));
    }

    /** How many more of this material fit into the player's main inventory. */
    public int freeSpace(Player player, Material material) {
        int max = ItemStack.of(material).getMaxStackSize();
        PlayerInventory inv = player.getInventory();
        int space = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) space += max;
            else if (isPlain(stack, material)) space += Math.max(0, max - stack.getAmount());
        }
        return space;
    }

    /** Plain items of this material in the main inventory. */
    public int count(Player player, Material material) {
        PlayerInventory inv = player.getInventory();
        int count = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (isPlain(stack, material)) count += stack.getAmount();
        }
        return count;
    }

    public int owned(Player player, ShopItem item) {
        return item.isCommand() ? 0 : count(player, item.material());
    }

    private void remove(Player player, Material material, int amount) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < STORAGE_SLOTS && amount > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!isPlain(stack, material)) continue;
            int take = Math.min(amount, stack.getAmount());
            amount -= take;
            if (take == stack.getAmount()) inv.setItem(i, null);
            else stack.setAmount(stack.getAmount() - take);
        }
    }

    /** Gives items, dropping anything that doesn't fit at the player's feet. */
    public void give(Player player, Material material, int amount) {
        int max = ItemStack.of(material).getMaxStackSize();
        List<ItemStack> stacks = new ArrayList<>();
        while (amount > 0) {
            int n = Math.min(max, amount);
            stacks.add(ItemStack.of(material, n));
            amount -= n;
        }
        give(player, stacks);
    }

    public void give(Player player, List<ItemStack> stacks) {
        if (stacks.isEmpty()) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stacks.toArray(ItemStack[]::new));
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItem(player.getLocation(), stack, item -> item.setOwner(player.getUniqueId()));
        }
    }

    // ------------------------------------------------------------------ buying

    /** Most of this item the player can buy right now (money, space and per-item limit). */
    public int maxBuyAmount(Player player, ShopItem item) {
        int cap = item.amountLimit();
        if (!item.isCommand()) cap = Math.min(cap, freeSpace(player, item.material()));
        return market().maxAffordable(item, balance(player), cap);
    }

    private boolean checkEconomy(Player player) {
        if (economy().isAvailable()) return true;
        plugin.messages().send(player, "no-economy");
        return fail(player);
    }

    /**
     * @param amount   amount to buy, or -1 for as many as possible
     * @param maxTotal the price the player confirmed, or NaN if no confirmation was shown
     */
    public boolean buy(Player player, ShopItem item, int amount, double maxTotal) {
        item = plugin.shop().refresh(item);
        if (item == null) {
            plugin.messages().send(player, "item-unavailable");
            return fail(player);
        }
        if (!player.hasPermission("simpleshop.buy") || (item.permission() != null && !player.hasPermission(item.permission()))) {
            plugin.messages().send(player, "no-permission");
            return fail(player);
        }
        if (!item.canBuy()) {
            plugin.messages().send(player, "not-buyable");
            return fail(player);
        }
        if (!checkEconomy(player)) return false;

        int space = item.isCommand() ? Integer.MAX_VALUE : freeSpace(player, item.material());
        double balance = balance(player);
        if (amount < 0) {
            amount = maxBuyAmount(player, item);
            if (amount <= 0) {
                if (space <= 0) plugin.messages().send(player, "not-enough-space", p("space", 0));
                else plugin.messages().send(player, "not-enough-money",
                        p("price", money(market().unitBuy(item))), p("balance", money(balance)));
                return fail(player);
            }
        }
        if (amount < 1 || amount > item.amountLimit()) {
            plugin.messages().send(player, "amount-limit", p("max", item.amountLimit()));
            return fail(player);
        }
        if (space < amount) {
            plugin.messages().send(player, "not-enough-space", p("space", space));
            return fail(player);
        }

        double price = market().quoteBuy(item, amount);
        if (!Double.isNaN(maxTotal) && price > maxTotal + PRICE_EPSILON) {
            plugin.messages().send(player, "price-changed", p("price", money(price)));
            return fail(player);
        }
        if (balance + 1e-9 < price) {
            plugin.messages().send(player, "not-enough-money", p("price", money(price)), p("balance", money(balance)));
            return fail(player);
        }
        String error = economy().withdraw(player, price);
        if (error != null) {
            plugin.messages().send(player, "transaction-failed", p("reason", error));
            return fail(player);
        }

        market().recordBuy(item, amount);
        if (item.isCommand()) {
            runCommands(player, item, amount);
            plugin.transactions().record(player.getUniqueId(), player.getName(), TransactionLog.Type.COMMAND, item.id(), amount, price);
            if (amount == 1) plugin.messages().send(player, "bought-command", p("item", item.plainName()), p("price", money(price)));
            else plugin.messages().send(player, "bought", p("amount", amount), p("item", item.plainName()), p("price", money(price)));
        } else {
            give(player, item.material(), amount);
            plugin.transactions().record(player.getUniqueId(), player.getName(), TransactionLog.Type.BUY, item.id(), amount, price);
            plugin.messages().send(player, "bought", p("amount", amount), p("item", item.plainName()), p("price", money(price)));
        }
        return success(player);
    }

    private void runCommands(Player player, ShopItem item, int amount) {
        List<String> commands = new ArrayList<>(item.commands().size());
        for (String command : item.commands()) {
            String c = command.replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{amount}", Integer.toString(amount));
            commands.add(c.startsWith("/") ? c.substring(1) : c);
        }
        // Console commands must run on the global region thread on Folia (main thread on Paper).
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            for (String command : commands) {
                try {
                    if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                        plugin.getLogger().warning("Command item " + item.id() + ": unknown command '" + command + "'");
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Command item " + item.id() + ": '" + command + "' failed: " + e.getMessage());
                }
            }
        });
    }

    // ------------------------------------------------------------------ selling

    /**
     * @param amount   amount to sell, or -1 for everything of this type
     * @param minTotal the payout the player confirmed, or NaN if no confirmation was shown
     */
    public boolean sell(Player player, ShopItem item, int amount, double minTotal) {
        item = plugin.shop().refresh(item);
        if (item == null) {
            plugin.messages().send(player, "item-unavailable");
            return fail(player);
        }
        if (!player.hasPermission("simpleshop.sell")) {
            plugin.messages().send(player, "no-permission");
            return fail(player);
        }
        if (!item.canSell()) {
            plugin.messages().send(player, "not-sellable");
            return fail(player);
        }
        if (!checkEconomy(player)) return false;

        int have = count(player, item.material());
        if (amount < 0) amount = have;
        if (amount <= 0 || have < amount) {
            plugin.messages().send(player, "not-enough-items", p("have", have), p("item", item.plainName()));
            return fail(player);
        }

        double price = market().quoteSell(item, amount);
        if (!Double.isNaN(minTotal) && price < minTotal - PRICE_EPSILON) {
            plugin.messages().send(player, "price-changed", p("price", money(price)));
            return fail(player);
        }
        remove(player, item.material(), amount);
        String error = economy().deposit(player, price);
        if (error != null) {
            give(player, item.material(), amount); // refund the items
            plugin.messages().send(player, "transaction-failed", p("reason", error));
            return fail(player);
        }
        market().recordSell(item, amount);
        plugin.transactions().record(player.getUniqueId(), player.getName(), TransactionLog.Type.SELL, item.id(), amount, price);
        plugin.messages().send(player, "sold", p("amount", amount), p("item", item.plainName()), p("price", money(price)));
        return success(player);
    }

    /** Groups sellable stacks by shop item; anything else goes to {@code rejected} (if not null). */
    private Map<ShopItem, Integer> group(Iterable<ItemStack> stacks, List<ItemStack> accepted, List<ItemStack> rejected) {
        Map<ShopItem, Integer> amounts = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            ShopItem item = plugin.shop().sellable(stack.getType());
            if (item != null && isPlain(stack, stack.getType())) {
                amounts.merge(item, stack.getAmount(), Integer::sum);
                if (accepted != null) accepted.add(stack);
            } else if (rejected != null) {
                rejected.add(stack);
            }
        }
        return amounts;
    }

    private double quoteAll(Map<ShopItem, Integer> amounts) {
        double total = 0;
        for (var e : amounts.entrySet()) total += market().quoteSell(e.getKey(), e.getValue());
        return Text.round(total);
    }

    private void recordAll(Player player, Map<ShopItem, Integer> amounts) {
        for (var e : amounts.entrySet()) {
            ShopItem item = e.getKey();
            double price = market().quoteSell(item, e.getValue());
            market().recordSell(item, e.getValue());
            plugin.transactions().record(player.getUniqueId(), player.getName(), TransactionLog.Type.SELL, item.id(), e.getValue(), price);
        }
    }

    /** /sell all: sells every plain sellable item in the main inventory. */
    public boolean sellInventory(Player player) {
        if (!player.hasPermission("simpleshop.sell")) {
            plugin.messages().send(player, "no-permission");
            return fail(player);
        }
        if (!checkEconomy(player)) return false;

        PlayerInventory inv = player.getInventory();
        List<ItemStack> taken = new ArrayList<>();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            if (plugin.shop().sellable(stack.getType()) == null || !isPlain(stack, stack.getType())) continue;
            taken.add(stack.clone());
            inv.setItem(i, null);
        }
        Map<ShopItem, Integer> amounts = group(taken, null, null);
        int count = amounts.values().stream().mapToInt(Integer::intValue).sum();
        if (count == 0) {
            plugin.messages().send(player, "nothing-to-sell");
            return fail(player);
        }
        double total = quoteAll(amounts);
        String error = economy().deposit(player, total);
        if (error != null) {
            give(player, taken);
            plugin.messages().send(player, "transaction-failed", p("reason", error));
            return fail(player);
        }
        recordAll(player, amounts);
        plugin.messages().send(player, "sold-multiple", p("amount", count), p("price", money(total)));
        return success(player);
    }

    /** Sells the contents of the sell box; anything unsellable (or everything, on failure) is handed back. */
    public void sellContents(Player player, ItemStack[] contents) {
        List<ItemStack> sellable = new ArrayList<>();
        List<ItemStack> unsellable = new ArrayList<>();
        Map<ShopItem, Integer> amounts = group(List.of(contents).stream().filter(s -> s != null).toList(), sellable, unsellable);
        int count = amounts.values().stream().mapToInt(Integer::intValue).sum();

        if (count > 0) {
            if (!player.hasPermission("simpleshop.sell")) {
                plugin.messages().send(player, "no-permission");
                give(player, sellable);
                fail(player);
            } else if (!economy().isAvailable()) {
                plugin.messages().send(player, "no-economy");
                give(player, sellable);
                fail(player);
            } else {
                double total = quoteAll(amounts);
                String error = economy().deposit(player, total);
                if (error != null) {
                    give(player, sellable);
                    plugin.messages().send(player, "transaction-failed", p("reason", error));
                    fail(player);
                } else {
                    recordAll(player, amounts);
                    plugin.messages().send(player, "sold-multiple", p("amount", count), p("price", money(total)));
                    success(player);
                }
            }
        }
        if (!unsellable.isEmpty()) {
            give(player, unsellable);
            plugin.messages().send(player, "items-returned",
                    p("amount", unsellable.stream().mapToInt(ItemStack::getAmount).sum()));
        }
    }

    // ------------------------------------------------------------------ worth

    /** Sell value of the held stack, or of the whole main inventory. */
    public Worth worth(Player player, boolean wholeInventory) {
        List<ItemStack> stacks = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        if (wholeInventory) {
            for (int i = 0; i < STORAGE_SLOTS; i++) stacks.add(inv.getItem(i));
        } else {
            stacks.add(inv.getItemInMainHand());
        }
        Map<ShopItem, Integer> amounts = group(stacks.stream().filter(s -> s != null).toList(), null, null);
        int count = amounts.values().stream().mapToInt(Integer::intValue).sum();
        return new Worth(count, quoteAll(amounts));
    }

    // ------------------------------------------------------------------ sounds

    public void click(Player player) {
        if (plugin.settings().sounds()) player.playSound(CLICK, Sound.Emitter.self());
    }

    public boolean success(Player player) {
        if (plugin.settings().sounds()) player.playSound(SUCCESS, Sound.Emitter.self());
        return true;
    }

    public boolean fail(Player player) {
        if (plugin.settings().sounds()) player.playSound(FAIL, Sound.Emitter.self());
        return false;
    }
}
