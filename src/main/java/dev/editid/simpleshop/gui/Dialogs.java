package dev.editid.simpleshop.gui;

import dev.editid.simpleshop.SimpleShopPlugin;
import dev.editid.simpleshop.util.Text;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/** Native Minecraft dialogs (text boxes) for search, custom amounts and the price editor. */
public final class Dialogs {

    private static final ClickCallback.Options OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(10))
            .build();

    private Dialogs() {
    }

    private static String text(SimpleShopPlugin plugin, String key, String def) {
        return plugin.layouts().text("dialogs." + key, def);
    }

    /**
     * Shows a yes/no dialog. Callbacks run on the player's thread.
     *
     * @param onCancel run when Cancel is clicked (escape just closes the dialog)
     */
    public static void show(SimpleShopPlugin plugin, Player player, String title, List<? extends DialogBody> body,
                            List<? extends DialogInput> inputs, String yesKey,
                            Consumer<DialogResponseView> onYes, Runnable onCancel, TagResolver... placeholders) {
        ActionButton yes = ActionButton.builder(Text.parse(text(plugin, yesKey, "<green>Confirm")))
                .width(150)
                .action(DialogAction.customClick((response, audience) ->
                        player.getScheduler().run(plugin, task -> onYes.accept(response), null), OPTIONS))
                .build();
        ActionButton no = ActionButton.builder(Text.parse(text(plugin, "cancel", "<red>Cancel")))
                .width(150)
                .action(DialogAction.customClick((response, audience) -> {
                    if (onCancel != null) player.getScheduler().run(plugin, task -> onCancel.run(), null);
                }, OPTIONS))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Text.parse(title, placeholders))
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(yes, no)));

        player.closeInventory();
        player.showDialog(dialog);
    }

    /** Single text box. */
    public static void prompt(SimpleShopPlugin plugin, Player player, String titleKey, String labelKey, String initial,
                              Consumer<String> onSubmit, Runnable onCancel, TagResolver... placeholders) {
        DialogInput input = DialogInput.text("value", Text.parse(text(plugin, labelKey, "Value"), placeholders))
                .initial(initial == null ? "" : initial)
                .maxLength(64)
                .width(250)
                .build();
        show(plugin, player, text(plugin, titleKey, "Input"), List.of(), List.of(input), "confirm", response -> {
            String value = response.getText("value");
            onSubmit.accept(value == null ? "" : value.trim());
        }, onCancel, placeholders);
    }

    public static DialogBody message(SimpleShopPlugin plugin, String key, String def) {
        return DialogBody.plainMessage(Text.parse(text(plugin, key, def)));
    }

    public static String label(SimpleShopPlugin plugin, String key, String def) {
        return text(plugin, key, def);
    }
}
