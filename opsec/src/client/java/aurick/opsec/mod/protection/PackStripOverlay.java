package aurick.opsec.mod.protection;

import aurick.opsec.mod.Opsec;
import aurick.opsec.mod.lang.OpsecLang;
import aurick.opsec.mod.lang.OpsecStrings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;


public class PackStripOverlay extends Screen {

    private static final Deque<Pending> queue = new ArrayDeque<>();

    private record Pending(UUID id, boolean required) {}

    public static synchronized void enqueue(UUID id, boolean required) {
        queue.offer(new Pending(id, required));
    }

    
    private static final long MIN_SHOW_INTERVAL_MS = 750L;
    private static long lastShownAt = 0L;

    public static synchronized void tryShowNext(Minecraft mc) {
        if (queue.isEmpty()) return;
        if (mc.level == null) return;
                if (mc.screen != null) return;
                long now = System.currentTimeMillis();
        if (now - lastShownAt < MIN_SHOW_INTERVAL_MS) return;
        Pending p = queue.poll();
        if (p == null) return;
        lastShownAt = now;
                mc.setScreen(new PackStripOverlay(p.id, p.required));
            }

    public static synchronized void clearQueue() {
        queue.clear();
        lastShownAt = 0L;
    }

    private final UUID packId;
    private final boolean required;

    
    private boolean userInteracted = false;

    private PackStripOverlay(UUID packId, boolean required) {
        super(OpsecLang.component(OpsecStrings.PACKSTRIP_TITLE));
        this.packId = packId;
        this.required = required;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int buttonWidth = 240;
        int buttonHeight = 20;
        int buttonSpacing = 24;

        int headerHeight = 14 * 3;
        int buttonsHeight = buttonSpacing + buttonHeight;
        int totalHeight = headerHeight + 14 + buttonsHeight;
        int startY = (this.height - totalHeight) / 2;

        int y = startY;
        addCenteredStringWidget(this.title, centerX, y);
        y += 14;
        addCenteredStringWidget(OpsecLang.component(
                required ? OpsecStrings.PACKSTRIP_REQUIRED : OpsecStrings.PACKSTRIP_OPTIONAL), centerX, y);
        y += 14;
        addCenteredStringWidget(OpsecLang.component(OpsecStrings.PACKSTRIP_STRIPPED), centerX, y);
        y += 14 + 14;

        this.addRenderableWidget(Button.builder(
                OpsecLang.component(OpsecStrings.PACKSTRIP_CONTINUE), b -> this.onClose())
                .bounds(centerX - buttonWidth / 2, y, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(
                OpsecLang.component(OpsecStrings.PACKSTRIP_LOAD_REAL), b -> loadForReal())
                .bounds(centerX - buttonWidth / 2, y + buttonSpacing, buttonWidth, buttonHeight).build());
    }

    private void addCenteredStringWidget(Component text, int centerX, int y) {
        int textWidth = this.font.width(text);
        this.addRenderableWidget(new StringWidget(centerX - textWidth / 2, y, textWidth, 10, text, this.font));
    }

    private void loadForReal() {
        userInteracted = true;
        
        
        PackStripHandler.markLoadForReal(packId);
        try {
            Minecraft.getInstance().reloadResourcePacks();
        } catch (Exception e) {
            Opsec.LOGGER.warn("[OpSec] [Load Pack For Real] reload failed: {}", e.getMessage());
        }
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        userInteracted = true;
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        
        
        if (userInteracted) return;
        enqueue(packId, required);
    }
}
