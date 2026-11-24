package jp.seiyo.updater;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class TitleUpdates {
    private static final Text DEFAULT = Text.literal("星陽アップデート");
    private static CompletableFuture<Updater.Snapshot> displayed;
    private static Text label = DEFAULT;

    public static void open(Screen parent) {
        MinecraftClient.getInstance().setScreen(new UpdateScreen(parent));
    }

    public static Text label() {
        var check = Updater.check(false);
        if (check == displayed || !check.isDone()) return label;
        displayed = check;
        label = DEFAULT;
        if (!check.isCompletedExceptionally()) {
            var snapshot = check.getNow(null);
            long count = Updater.plan(snapshot, Updater.defaults(snapshot)).changes().stream()
                .filter(row -> row.problem().isEmpty()).count();
            if (count > 0) label = Text.literal("星陽アップデート (" + count + ")");
        }
        return label;
    }

    private TitleUpdates() {}
}
