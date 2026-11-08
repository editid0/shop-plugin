package dev.editid.simpleshop.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault bridge. Works with Vault and VaultUnlocked, and therefore with any economy that
 * registers through them (EssentialsX, CMI, TheNewEconomy, XConomy, CoinsEngine, ...).
 *
 * <p>The provider is looked up on every call, so economies that register late, or get
 * swapped at runtime, are picked up automatically. Only loaded when the Vault API is present.</p>
 */
final class VaultEconomyProvider implements EconomyProvider {

    private Economy economy() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    @Override
    public boolean isAvailable() {
        Economy eco = economy();
        return eco != null && eco.isEnabled();
    }

    @Override
    public String name() {
        Economy eco = economy();
        return eco == null ? "Vault (no economy registered)" : eco.getName() + " (via Vault)";
    }

    @Override
    public double balance(OfflinePlayer player) {
        Economy eco = economy();
        return eco == null ? 0 : eco.getBalance(player);
    }

    @Override
    public String withdraw(OfflinePlayer player, double amount) {
        Economy eco = economy();
        if (eco == null) return "no economy";
        if (!eco.has(player, amount)) return "insufficient funds";
        return result(eco.withdrawPlayer(player, amount));
    }

    @Override
    public String deposit(OfflinePlayer player, double amount) {
        Economy eco = economy();
        if (eco == null) return "no economy";
        return result(eco.depositPlayer(player, amount));
    }

    @Override
    public String format(double amount) {
        Economy eco = economy();
        return eco == null ? null : eco.format(amount);
    }

    private static String result(EconomyResponse response) {
        if (response == null) return "economy returned no response";
        if (response.transactionSuccess()) return null;
        return response.errorMessage == null || response.errorMessage.isBlank() ? "unknown error" : response.errorMessage;
    }
}
