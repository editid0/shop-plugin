package dev.editid.simpleshop.util;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

/** Sends prefixed MiniMessage messages from config.yml's 'messages' section. */
public final class Messages {

    private final ConfigurationSection section;
    private final String prefix;

    public Messages(ConfigurationSection section) {
        this.section = section;
        this.prefix = section == null ? "" : section.getString("prefix", "");
    }

    public void send(CommandSender to, String key, TagResolver... placeholders) {
        String raw = section == null ? null : section.getString(key);
        if (raw == null) raw = "<red>Missing message: " + key;
        if (raw.isEmpty()) return;
        to.sendMessage(Text.parse(prefix + raw, placeholders));
    }

    /** Plain-text placeholder (formatting in the value is shown literally). */
    public static TagResolver p(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    /** MiniMessage placeholder (formatting in the value is applied). */
    public static TagResolver mm(String key, String value) {
        return Placeholder.parsed(key, value);
    }
}
