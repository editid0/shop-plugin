package dev.editid.simpleshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.editid.simpleshop.editor.ShopEditor;
import dev.editid.simpleshop.market.Market;
import dev.editid.simpleshop.shop.ShopItem;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MarketTest {

    private static Settings settings(boolean dynamic) {
        return new Settings(false, "$%s", false, true, 1000, true, false, 100,
                dynamic, 20000, 0.25, 3.0, 6);
    }

    private static ShopItem item(double buy, double sell) {
        return new ShopItem("ores", "DIAMOND", "DIAMOND", Material.DIAMOND, null, List.of(),
                buy, sell, List.of(), 0, true, 0, null);
    }

    private static Market market(boolean dynamic) throws Exception {
        File file = Files.createTempFile("market", ".yml").toFile();
        file.delete();
        return new Market(() -> settings(dynamic), file, Logger.getAnonymousLogger());
    }

    @Test
    void staticPricingIsLinear() throws Exception {
        Market m = market(false);
        ShopItem diamond = item(250, 75);
        assertEquals(2500, m.quoteBuy(diamond, 10));
        assertEquals(750, m.quoteSell(diamond, 10));
        m.recordSell(diamond, 1000);
        assertEquals(1.0, m.multiplier(diamond));
    }

    @Test
    void sellingLowersAndBuyingRaisesPrice() throws Exception {
        Market m = market(true);
        ShopItem diamond = item(250, 75);
        double before = m.unitSell(diamond);
        m.recordSell(diamond, 100);
        assertTrue(m.unitSell(diamond) < before);
        m.recordBuy(diamond, 300);
        assertTrue(m.unitBuy(diamond) > 250);
    }

    @Test
    void volumeHalvesPrice() throws Exception {
        Market m = market(true);
        ShopItem diamond = item(250, 75);
        // volume = 20000 / 75 = 266.67 units
        m.recordSell(diamond, 267);
        assertEquals(0.5, m.multiplier(diamond), 0.01);
    }

    @Test
    void multiplierIsClamped() throws Exception {
        Market m = market(true);
        ShopItem diamond = item(250, 75);
        m.recordSell(diamond, 100_000);
        assertEquals(0.25, m.multiplier(diamond), 1e-9);
        m.reset("DIAMOND");
        m.recordBuy(diamond, 100_000);
        assertEquals(3.0, m.multiplier(diamond), 1e-9);
    }

    @Test
    void bulkSaleMatchesSplitSales() throws Exception {
        Market a = market(true), b = market(true);
        ShopItem diamond = item(250, 75);
        double bulk = a.quoteSell(diamond, 200);
        double split = 0;
        for (int i = 0; i < 4; i++) {
            split += b.quoteSell(diamond, 50);
            b.recordSell(diamond, 50);
        }
        assertEquals(bulk, split, 0.05);
    }

    @Test
    void buyThenSellNeverProfits() throws Exception {
        Market m = market(true);
        ShopItem diamond = item(250, 75);
        for (int amount : new int[]{1, 64, 500, 2304}) {
            double cost = m.quoteBuy(diamond, amount);
            m.recordBuy(diamond, amount);
            double payout = m.quoteSell(diamond, amount);
            m.recordSell(diamond, amount);
            assertTrue(payout < cost, "amount " + amount);
        }
    }

    @Test
    void maxAffordableRespectsBalance() throws Exception {
        Market m = market(true);
        ShopItem diamond = item(250, 75);
        int n = m.maxAffordable(diamond, 10_000, 2304);
        assertTrue(m.quoteBuy(diamond, n) <= 10_000);
        assertTrue(m.quoteBuy(diamond, n + 1) > 10_000);
    }

    @Test
    void decayRecoversTowardsBase() throws Exception {
        Market m = market(true);
        ShopItem diamond = item(250, 75);
        m.recordSell(diamond, 267);
        double low = m.multiplier(diamond);
        m.decay(6 * 3_600_000L); // one half-life
        double recovered = m.multiplier(diamond);
        assertTrue(recovered > low && recovered < 1.0);
        assertEquals(Math.pow(0.5, 133.5 / 266.67), recovered, 0.01);
    }

    @Test
    void savesAndLoads() throws Exception {
        Market m = market(true);
        ShopItem diamond = item(250, 75);
        m.recordSell(diamond, 100);
        double mult = m.multiplier(diamond);
        m.save();
        m.resetAll();
        m.load();
        assertEquals(mult, m.multiplier(diamond), 1e-9);
    }

    @Test
    void parsesPrices() {
        assertEquals(12.5, ShopEditor.parsePrice("12.5"));
        assertEquals(1000.0, ShopEditor.parsePrice("$1,000"));
        assertEquals(0.0, ShopEditor.parsePrice(""));
        assertNull(ShopEditor.parsePrice("-5"));
        assertNull(ShopEditor.parsePrice("abc"));
        assertNull(ShopEditor.parsePrice("NaN"));
    }
}
