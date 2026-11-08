package dev.editid.simpleshop.market;

import dev.editid.simpleshop.Settings;
import dev.editid.simpleshop.shop.ShopItem;
import dev.editid.simpleshop.util.Text;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Supply and demand pricing.
 *
 * <p>Each item tracks a "net sold" counter: selling adds, buying subtracts. The price multiplier is
 * {@code 0.5 ^ (net / volume)} clamped to [min, max], so selling one volume's worth halves the price
 * and buying one volume's worth doubles it. Transactions are priced unit by unit along that curve,
 * which makes splitting a sale into many small ones pointless. The counter decays towards zero over
 * time so prices recover. Buy and sell prices share the multiplier, so buy &gt; sell always holds.</p>
 *
 * <p>Thread-safe: on Folia, transactions on different region threads may hit the same item.</p>
 */
public final class Market {

    private final Map<String, Double> net = new ConcurrentHashMap<>();
    private final Supplier<Settings> settings;
    private final File file;
    private final Logger logger;

    public Market(Supplier<Settings> settings, File file, Logger logger) {
        this.settings = settings;
        this.file = file;
        this.logger = logger;
    }

    private boolean active(ShopItem item) {
        return settings.get().dynamicEnabled() && item.dynamic();
    }

    /** Units that must be sold (net) to halve the price. */
    private double volume(ShopItem item) {
        if (item.volume() > 0) return item.volume();
        double reference = item.sellPrice() > 0 ? item.sellPrice() : item.buyPrice();
        return Math.max(1, settings.get().moneyVolume() / Math.max(0.01, reference));
    }

    private double multiplierAt(ShopItem item, double netSold) {
        Settings s = settings.get();
        double m = Math.pow(0.5, netSold / volume(item));
        return Math.clamp(m, s.minMultiplier(), s.maxMultiplier());
    }

    private double netSold(ShopItem item) {
        return net.getOrDefault(item.id(), 0.0);
    }

    /** Current price multiplier (1.0 = base price). */
    public double multiplier(ShopItem item) {
        return active(item) ? multiplierAt(item, netSold(item)) : 1.0;
    }

    public boolean isDynamic(ShopItem item) {
        return active(item);
    }

    public double unitBuy(ShopItem item) {
        return Text.round(item.buyPrice() * multiplier(item));
    }

    public double unitSell(ShopItem item) {
        return Text.round(item.sellPrice() * multiplier(item));
    }

    /** Total cost of buying {@code amount} right now. */
    public double quoteBuy(ShopItem item, int amount) {
        if (!active(item)) return Text.round(item.buyPrice() * amount);
        double n = netSold(item);
        double total = 0;
        for (int i = 0; i < amount; i++) total += item.buyPrice() * multiplierAt(item, n - i);
        return Text.round(total);
    }

    /** Total payout for selling {@code amount} right now. */
    public double quoteSell(ShopItem item, int amount) {
        if (!active(item)) return Text.round(item.sellPrice() * amount);
        double n = netSold(item);
        double total = 0;
        for (int i = 0; i < amount; i++) total += item.sellPrice() * multiplierAt(item, n + i);
        return Text.round(total);
    }

    /** Most units affordable with {@code balance}, up to {@code cap}. */
    public int maxAffordable(ShopItem item, double balance, int cap) {
        if (item.buyPrice() <= 0) return 0;
        if (!active(item)) return (int) Math.min(cap, Math.floor(balance / item.buyPrice() + 1e-9));
        double n = netSold(item);
        double total = 0;
        int count = 0;
        while (count < cap) {
            double next = total + item.buyPrice() * multiplierAt(item, n - count);
            if (Text.round(next) > balance + 1e-9) break;
            total = next;
            count++;
        }
        return count;
    }

    public void recordBuy(ShopItem item, int amount) {
        if (active(item)) net.merge(item.id(), (double) -amount, Double::sum);
    }

    public void recordSell(ShopItem item, int amount) {
        if (active(item)) net.merge(item.id(), (double) amount, Double::sum);
    }

    public void reset(String id) {
        net.remove(id);
    }

    public void resetAll() {
        net.clear();
    }

    /** Moves every price towards its base. Call periodically with the elapsed time. */
    public void decay(long elapsedMillis) {
        double hours = elapsedMillis / 3_600_000.0;
        double factor = Math.pow(0.5, hours / settings.get().recoveryHalfLifeHours());
        net.replaceAll((id, value) -> value * factor);
        net.values().removeIf(value -> Math.abs(value) < 0.5);
    }

    // ------------------------------------------------------------------ persistence

    public void load() {
        net.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) net.put(key, yaml.getDouble(key));
    }

    public void save() {
        Map<String, Double> snapshot = new HashMap<>(net);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(java.util.List.of(
                "SimpleShop dynamic pricing state: net units sold per item (negative = net bought).",
                "Safe to delete to reset all prices; edit only while the server is stopped."));
        snapshot.forEach(yaml::set);
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save market data", e);
        }
    }
}
