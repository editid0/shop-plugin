package dev.editid.simpleshop;

import dev.editid.simpleshop.command.ShopCommands;
import dev.editid.simpleshop.config.FileUpdater;
import dev.editid.simpleshop.economy.Economies;
import dev.editid.simpleshop.economy.EconomyProvider;
import dev.editid.simpleshop.gui.Menu;
import dev.editid.simpleshop.gui.MenuListener;
import dev.editid.simpleshop.gui.SellMenu;
import dev.editid.simpleshop.gui.layout.Layouts;
import dev.editid.simpleshop.integration.PlaceholderHook;
import dev.editid.simpleshop.log.TransactionLog;
import dev.editid.simpleshop.market.Market;
import dev.editid.simpleshop.shop.ShopRegistry;
import dev.editid.simpleshop.shop.ShopService;
import dev.editid.simpleshop.util.Messages;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleShopPlugin extends JavaPlugin {

    private volatile Settings settings;
    private volatile Messages messages;
    private volatile ShopRegistry shop;
    private volatile Layouts layouts;
    private EconomyProvider economy = EconomyProvider.NONE;
    private ShopService service;
    private Market market;
    private TransactionLog transactions;

    @Override
    public void onEnable() {
        loadFiles();

        economy = Economies.detect(getLogger());
        service = new ShopService(this);
        market = new Market(this::settings, new File(getDataFolder(), "data/market.yml"), getLogger());
        market.load();
        transactions = new TransactionLog(new File(getDataFolder(), "logs"), getLogger(),
                () -> settings.logToFile(), () -> settings.logMemory());

        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        ShopCommands commands = new ShopCommands(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> commands.register(event.registrar()));

        // Prices drift back to base every minute; market state is saved every 5 minutes.
        Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> market.decay(60_000), 1, 1, TimeUnit.MINUTES);
        Bukkit.getAsyncScheduler().runAtFixedRate(this, task -> market.save(), 5, 5, TimeUnit.MINUTES);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) PlaceholderHook.register(this);

        // Economy plugins may register with Vault after we enable; report once the server is up.
        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> {
            if (economy.isAvailable()) getLogger().info("Hooked into economy: " + economy.name());
            else getLogger().warning("No economy found yet. Buying/selling is disabled until an economy plugin registers with Vault.");
        }, 1L);

        getLogger().info("Loaded " + shop.categories().size() + " categories with " + shop.itemCount() + " items.");
    }

    @Override
    public void onDisable() {
        // Don't lose items left in an open sell box when the server stops.
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
                    if (menu instanceof SellMenu sell) sell.returnItems();
                    player.closeInventory();
                }
            } catch (Throwable t) {
                getLogger().warning("Could not close shop menu for " + player.getName() + ": " + t.getMessage());
            }
        }
        if (market != null) market.save();
        if (transactions != null) transactions.close();
    }

    private void loadFiles() {
        // Creates missing files and adds new settings/default items from this version.
        new FileUpdater(getDataFolder(), this::getResource, getLogger()).run();
        reloadConfig();
        settings = Settings.from(getConfig());
        messages = new Messages(getConfig().getConfigurationSection("messages"));
        layouts = Layouts.load(new File(getDataFolder(), "menus.yml"), getResource("menus.yml"));
        reloadShop();
    }

    /** Re-reads shop.yml only (used by the editor). */
    public void reloadShop() {
        shop = ShopRegistry.load(new File(getDataFolder(), "shop.yml"), getLogger());
    }

    /** Reloads all files and closes open shop menus (sell boxes are processed normally). */
    public void reload() {
        loadFiles();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(this, task -> {
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) player.closeInventory();
            }, null);
        }
    }

    public Settings settings() { return settings; }
    public Messages messages() { return messages; }
    public ShopRegistry shop() { return shop; }
    public Layouts layouts() { return layouts; }
    public EconomyProvider economy() { return economy; }
    public ShopService service() { return service; }
    public Market market() { return market; }
    public TransactionLog transactions() { return transactions; }
}
