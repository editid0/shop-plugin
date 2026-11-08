package dev.editid.simpleshop;

import org.bukkit.configuration.file.FileConfiguration;

/** Parsed config.yml values. */
public record Settings(
        boolean useEconomyFormat,
        String currencyFormat,
        boolean sounds,
        boolean confirmEnabled,
        double confirmAbove,
        boolean confirmCommandItems,
        boolean logToFile,
        int logMemory,
        boolean dynamicEnabled,
        double moneyVolume,
        double minMultiplier,
        double maxMultiplier,
        double recoveryHalfLifeHours) {

    /** Largest single transaction: a full inventory of 64-stacks. */
    public static final int MAX_AMOUNT = 36 * 64;

    public static Settings from(FileConfiguration config) {
        String format = config.getString("currency-format", "$%s");
        if (!format.contains("%s")) format = format + "%s";
        double min = Math.max(0.01, config.getDouble("dynamic-pricing.min-multiplier", 0.25));
        double max = Math.max(1.0, config.getDouble("dynamic-pricing.max-multiplier", 3.0));

        return new Settings(
                config.getBoolean("use-economy-format", true),
                format,
                config.getBoolean("sounds", true),
                config.getBoolean("confirm.enabled", true),
                Math.max(0, config.getDouble("confirm.above", 1000)),
                config.getBoolean("confirm.command-items", true),
                config.getBoolean("logging.file", true),
                Math.max(0, config.getInt("logging.memory", 1000)),
                config.getBoolean("dynamic-pricing.enabled", true),
                Math.max(1, config.getDouble("dynamic-pricing.money-volume", 20000)),
                Math.min(min, 1.0),
                max,
                Math.max(0.01, config.getDouble("dynamic-pricing.recovery-half-life-hours", 6)));
    }
}
