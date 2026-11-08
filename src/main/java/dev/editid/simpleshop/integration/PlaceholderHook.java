package dev.editid.simpleshop.integration;

import dev.editid.simpleshop.SimpleShopPlugin;

/** Isolates PlaceholderAPI classes so they're only loaded when PlaceholderAPI is installed. */
public final class PlaceholderHook {

    private PlaceholderHook() {
    }

    public static void register(SimpleShopPlugin plugin) {
        if (new ShopExpansion(plugin).register()) {
            plugin.getLogger().info("Registered PlaceholderAPI placeholders (%simpleshop_...%).");
        }
    }
}
