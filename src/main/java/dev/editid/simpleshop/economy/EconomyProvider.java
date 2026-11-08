package dev.editid.simpleshop.economy;

import org.bukkit.OfflinePlayer;

/**
 * Minimal economy abstraction so the rest of the plugin never touches a specific economy API.
 * New integrations (e.g. a direct plugin hook) only need to implement this.
 */
public interface EconomyProvider {

    /** @return true if a backing economy is currently usable */
    boolean isAvailable();

    /** @return name of the backing economy plugin, for logging */
    String name();

    double balance(OfflinePlayer player);

    /** @return null on success, otherwise a human readable error */
    String withdraw(OfflinePlayer player, double amount);

    /** @return null on success, otherwise a human readable error */
    String deposit(OfflinePlayer player, double amount);

    /** @return formatted amount, or null to use the plugin's own currency format */
    String format(double amount);

    /** Fallback used when no economy plugin is installed. */
    EconomyProvider NONE = new EconomyProvider() {
        @Override public boolean isAvailable() { return false; }
        @Override public String name() { return "none"; }
        @Override public double balance(OfflinePlayer player) { return 0; }
        @Override public String withdraw(OfflinePlayer player, double amount) { return "no economy"; }
        @Override public String deposit(OfflinePlayer player, double amount) { return "no economy"; }
        @Override public String format(double amount) { return null; }
    };
}
