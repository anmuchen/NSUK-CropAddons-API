package com.nsukcropapi.client;

import com.nsukcropapi.api.CropRegistry;
import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.config.CropApiConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「NSUK 作物设置」：所有作物附属共用的一个配置界面。
 *
 * <p>第一行是自动发现开关，其余每行是一个已注册来源（农夫乐事、森罗物语、潘马斯……）。
 * 关掉某个来源后，它的作物不再出现在农田盒的作物选择界面；已经种着该作物的农田盒不受影响。</p>
 *
 * <p>来源列表是运行时从 {@link CropRegistry} 收集的，所以新增附属不需要改这个界面。</p>
 */
public final class CropApiConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 220;
    private static final int ROW_HEIGHT = 20;

    private final Screen parent;
    private final List<Button> dynamicButtons = new ArrayList<>();
    private int scroll;
    private int panelX;
    private int panelY;

    public CropApiConfigScreen(Screen parent) {
        super(Component.translatable("gui.nsukcropapi.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;
        this.clearWidgets();
        this.dynamicButtons.clear();
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"),
                        button -> this.onClose())
                .bounds(this.panelX + PANEL_WIDTH - 88, this.panelY + PANEL_HEIGHT - 26, 76, 18)
                .build());
        this.rebuild();
    }

    private void rebuild() {
        for (Button button : this.dynamicButtons) {
            this.removeWidget(button);
        }
        this.dynamicButtons.clear();

        List<Row> rows = this.rows();
        int contentTop = this.panelY + 34;
        int contentBottom = this.panelY + PANEL_HEIGHT - 34;
        int visible = Math.max(1, (contentBottom - contentTop) / ROW_HEIGHT);
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, rows.size() - visible));

        int y = contentTop;
        for (int index = this.scroll; index < rows.size() && y + ROW_HEIGHT <= contentBottom; index++) {
            Row row = rows.get(index);
            Button toggle = Button.builder(row.right(), button -> {
                row.action().run();
                CropApiConfig.save();
                this.rebuild();
            }).bounds(this.panelX + PANEL_WIDTH - 86, y + 1, 70, 18).build();
            this.dynamicButtons.add((Button) this.addRenderableWidget(toggle));
            y += ROW_HEIGHT;
        }
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(
                Component.translatable("gui.nsukcropapi.config.auto_discovery"),
                toggleLabel(CropApiConfig.autoDiscovery()),
                () -> CropApiConfig.setAutoDiscovery(!CropApiConfig.autoDiscovery())));

        for (Map.Entry<String, String> entry : this.sources().entrySet()) {
            String modId = entry.getKey();
            boolean enabled = CropApiConfig.isSourceEnabled(modId);
            rows.add(new Row(
                    sourceName(modId, entry.getValue()),
                    toggleLabel(enabled),
                    () -> CropApiConfig.setSourceEnabled(modId, !enabled)));
        }
        return rows;
    }

    /** modid → 分组语言键；没有分组键的来源值为空串。 */
    private Map<String, String> sources() {
        Map<String, String> out = new LinkedHashMap<>();
        for (CropSpec spec : CropRegistry.all()) {
            String source = spec.sourceModId() == null || spec.sourceModId().isBlank()
                    ? "unknown"
                    : spec.sourceModId();
            String groupKey = spec.groupNameKey();
            out.computeIfAbsent(source, key -> groupKey == null ? "" : groupKey);
        }
        return out;
    }

    private static Component sourceName(String modId, String groupKey) {
        if (groupKey != null && !groupKey.isBlank() && I18n.exists(groupKey)) {
            return Component.translatable(groupKey);
        }
        return Component.literal(modId);
    }

    private static Component toggleLabel(boolean enabled) {
        return Component.translatable(enabled
                ? "gui.nsukcropapi.config.on"
                : "gui.nsukcropapi.config.off");
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, -1072689128);
        graphics.fill(this.panelX, this.panelY, this.panelX + PANEL_WIDTH, this.panelY + PANEL_HEIGHT, -266198488);
        drawBorder(graphics, this.panelX, this.panelY, PANEL_WIDTH, PANEL_HEIGHT, -2047904);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.panelY + 10, -1518454);

        int contentTop = this.panelY + 34;
        int contentBottom = this.panelY + PANEL_HEIGHT - 34;
        graphics.fill(this.panelX + 10, contentTop - 4, this.panelX + PANEL_WIDTH - 10, contentBottom, -15329764);
        drawBorder(graphics, this.panelX + 10, contentTop - 4, PANEL_WIDTH - 20,
                contentBottom - (contentTop - 4), -11908526);

        graphics.enableScissor(this.panelX + 12, contentTop, this.panelX + PANEL_WIDTH - 12, contentBottom);
        List<Row> rows = this.rows();
        int visible = Math.max(1, (contentBottom - contentTop) / ROW_HEIGHT);
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, rows.size() - visible));
        int y = contentTop;
        for (int index = this.scroll; index < rows.size() && y + ROW_HEIGHT <= contentBottom; index++) {
            graphics.drawString(this.font, rows.get(index).left(), this.panelX + 18, y + 6, -1250068, false);
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.panelX && mouseX <= this.panelX + PANEL_WIDTH
                && mouseY >= this.panelY && mouseY <= this.panelY + PANEL_HEIGHT) {
            this.scroll -= (int) Math.signum(scrollY);
            this.rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        CropApiConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private record Row(Component left, Component right, Runnable action) {
    }
}
