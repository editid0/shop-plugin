package dev.editid.simpleshop.economy;

import java.util.logging.Logger;

/** Picks the best available economy integration. */
public final class Economies {

    private Economies() {
    }

    public static EconomyProvider detect(Logger logger) {
        if (classExists("net.milkbowl.vault.economy.Economy")) {
            logger.info("Vault API found, economy will be resolved through Vault.");
            return new VaultEconomyProvider();
        }
        logger.warning("Vault/VaultUnlocked not found. Install Vault (or VaultUnlocked on Folia) plus an economy plugin to enable buying and selling.");
        return EconomyProvider.NONE;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, Economies.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
