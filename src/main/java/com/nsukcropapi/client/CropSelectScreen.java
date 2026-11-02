package com.nsukcropapi.client;

import client.cn.kafei.simukraft.client.ui.SimuKraftUiTheme;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.nsukcropapi.api.CropRegistry;
import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.config.CropApiConfig;
import com.nsukcropapi.nsuk.CropBridges;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenRequestPacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import common.cn.kafei.simukraft.network.farmland.FarmlandBoxSetCropPacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 统一作物选择界面：按作物来源模组分组。
 *
 * <p>分组默认折叠规则：原版、农夫乐事、森罗物语：厨房、沉浸农艺永不折叠；
 * 只有单种作物的附属分组也不折叠；其余分组默认折叠。</p>
 *
 * <p>排序：原版 → 农夫乐事系 → 森罗物语：厨房系 → 沉浸农艺 / Let's Do 系 → 其他。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class CropSelectScreen {

    private static final int ROW_HEIGHT = 24;
    private static final int ICON_SIZE = 18;
    private static final int HEADER_HEIGHT = 14;
    private static final int COLOR_TEXT = -1;
    private static final int COLOR_DIM = -4342339;
    private static final int COLOR_SECTION = -658016;
    private static final String KEY_VANILLA = "vanilla";

    /** 默认展开（不折叠）的基础来源：各大类的代表模组。 */
    private static final Set<String> ALWAYS_EXPANDED = Set.of(
            "farmersdelight", "kaleidoscope_cookery", "farm_and_charm");

    /** 名字里不含 "delight" 的农夫乐事系附属。 */
    private static final Set<String> DELIGHT_ADDONS = Set.of(
            "bountifulharvest", "farmers_spell", "youkaisfeasts", "fruits_delight",
            "cuisinedelight", "barbequesdelight", "compatdelight", "refineddelight",
            "mynethersdelight", "ends_delight");

    /** Let's Do 系（沉浸农艺的姊妹模组）。 */
    private static final Set<String> LETSDO_MODS = Set.of(
            "brewery", "vinery", "candlelight", "meadow", "herbalbrews");

    private static final Map<String, String> MOD_NAMES = Map.ofEntries(
            Map.entry("minecraft", "原版"),
            Map.entry("farmersdelight", "农夫乐事"),
            Map.entry("kaleidoscope_cookery", "森罗物语：厨房"),
            Map.entry("kaleidoscope_chinesefood", "森罗厨房：国味"),
            Map.entry("kaleidoscope_grilling", "森罗：炙烤"),
            Map.entry("kaleidoscope_dim_wine", "森罗物语：次元酒窖"),
            Map.entry("kaleidoscope_twilight", "森罗物语：暮色"),
            Map.entry("kaleidoscope_orchard", "森罗物语：果园"),
            Map.entry("kaleidoscope_tavern", "森罗物语：酒馆"),
            Map.entry("kaleidoscope_end", "森罗物语：末地"),
            Map.entry("kaleidoscope_nether", "森罗物语：下界"),
            Map.entry("kaleidoscope_sculk", "森罗物语：幽匿"),
            Map.entry("brewery", "酿酒"),
            Map.entry("herbalbrews", "草本茶"),
            Map.entry("farm_and_charm", "沉浸农艺"),
            Map.entry("vinery", "葡园酒香"),
            Map.entry("candlelight", "烛光晚餐"),
            Map.entry("meadow", "草甸"),
            Map.entry("ends_delight", "末地乐事"),
            Map.entry("mynethersdelight", "下界乐事"),
            Map.entry("cuisinedelight", "料理乐事"),
            Map.entry("dumplings_delight", "饺子乐事"),
            Map.entry("barbequesdelight", "烧烤乐事"),
            Map.entry("refineddelight", "精致存储：乐事"),
            Map.entry("compatdelight", "兼容乐事"),
            Map.entry("croptopia", "作物盛景"),
            Map.entry("culturaldelights", "文化乐事"),
            Map.entry("expandeddelight", "扩充乐事"),
            Map.entry("pineapple_delight", "凤梨乐事"),
            Map.entry("rusticdelight", "乡村乐事"),
            Map.entry("trailandtales_delight", "樱途旅事"),
            Map.entry("ubesdelight", "香芋乐事"),
            Map.entry("veggiesdelight", "蔬菜乐事"),
            Map.entry("youkaisfeasts", "妖怪盛宴"),
            Map.entry("immortalers_delight", "千古乐事"),
            Map.entry("gan_delight_reborn", "赣味乐事")
    );

    private CropSelectScreen() {
    }

    public static void open(FarmlandBoxOpenResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> minecraft.setScreen(new CropScreen(createUi(packet), Component.empty())));
        }
    }

    private static ModularUI createUi(FarmlandBoxOpenResponsePacket packet) {
        int screenWidth = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());

        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100.0F);
            layout.heightPercent(100.0F);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.paddingAll(8.0F);
        });
        root.addChild(SimuKraftUiTheme.createShellPanel(screenWidth, screenHeight));
        root.addChild(topButton("gui.button.back", () -> back(packet.boxPos())));

        UIElement panel = new UIElement().layout(layout -> {
            layout.widthPercent(90.0F);
            layout.maxWidth(320.0F);
            layout.height((float) Math.min(236, screenHeight - 48));
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.paddingAll(10.0F);
            layout.gapAll(6.0F);
        }).addClass("simukraft_panel");

        panel.addChild(label(Component.translatable("gui.simukraft.farmland_box.select_crop_title"), Horizontal.CENTER, COLOR_TEXT, 16));
        panel.addChild(label(Component.translatable("gui.nsukcropapi.select_crop.hint"), Horizontal.CENTER, COLOR_DIM, 12));

        UIElement list = new UIElement().layout(layout -> {
            layout.widthPercent(100.0F);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(4.0F);
        });

        ListState state = new ListState(packet, list, CropBridges.catalogs());
        panel.addChild(searchBox(state));

        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        scroller.layout(layout -> {
            layout.widthPercent(100.0F);
            layout.flex(1.0F);
            layout.minHeight(80.0F);
        });
        scroller.addScrollViewChild(list);
        panel.addChild(scroller);

        state.rebuild();
        root.addChild(panel);
        return new ModularUI(SimuKraftUiTheme.createUi(root)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static UIElement searchBox(ListState state) {
        UIElement wrap = new UIElement().layout(layout -> {
            layout.widthPercent(100.0F);
            layout.height(22.0F);
            layout.minHeight(22.0F);
            layout.maxHeight(22.0F);
            layout.flexShrink(0.0F);
        }).style(style -> style.backgroundTexture(new GuiTextureGroup(
                new ColorRectTexture(-15461352), new ColorBorderTexture(1, -7697808))));

        TextField field = new TextField();
        field.setAnyString();
        field.setTextResponder(state::setQuery);
        field.layout(layout -> {
            layout.widthPercent(100.0F);
            layout.height(22.0F);
            layout.paddingLeft(6.0F);
            layout.paddingRight(6.0F);
            layout.paddingTop(5.0F);
            layout.paddingBottom(5.0F);
        });
        field.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        field.textFieldStyle(style -> style
                .fontSize(9.0F)
                .textColor(COLOR_TEXT)
                .cursorColor(COLOR_TEXT)
                .textShadow(false)
                .placeholder(Component.translatable("gui.nsukcropapi.select_crop.search"))
                .focusOverlay(IGuiTexture.EMPTY));
        wrap.addChild(field);
        return wrap;
    }

    private static UIElement cropRow(BlockPos boxPos, String cropId, ItemStack icon, Component name, boolean selected) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100.0F);
            layout.height((float) ROW_HEIGHT);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(6.0F);
        });
        row.addChild(new UIElement().layout(layout -> {
            layout.width((float) ICON_SIZE);
            layout.height((float) ICON_SIZE);
            layout.flexShrink(0.0F);
        }).style(style -> style.backgroundTexture(new ItemStackTexture(new ItemStack[]{icon}))));

        Button button = new Button();
        Component text = selected
                ? Component.translatable("gui.simukraft.farmland_box.crop_selected", name)
                : name;
        button.setText(text);
        button.setOnClick(event -> select(boxPos, cropId));
        button.layout(layout -> {
            layout.height((float) ROW_HEIGHT);
            layout.flex(1.0F);
        });
        row.addChild(button);
        return row;
    }

    private static UIElement sectionTitle(Component text) {
        return label(text, Horizontal.LEFT, COLOR_SECTION, HEADER_HEIGHT);
    }

    private static UIElement foldHeader(Component title, boolean expanded, Runnable onClick) {
        Button header = new Button().noText();
        header.buttonStyle(style -> style.baseTexture(IGuiTexture.EMPTY)
                .hoverTexture(new ColorRectTexture(586544544))
                .pressedTexture(IGuiTexture.EMPTY));
        header.setOnClick(event -> onClick.run());
        header.layout(layout -> {
            layout.widthPercent(100.0F);
            layout.height((float) HEADER_HEIGHT);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
        });
        header.addChild(label(
                Component.translatable(expanded
                        ? "gui.nsukcropapi.select_crop.group_expanded"
                        : "gui.nsukcropapi.select_crop.group_collapsed", title),
                Horizontal.LEFT, COLOR_SECTION, HEADER_HEIGHT));
        return header;
    }

    private static Button topButton(String key, Runnable action) {
        Button button = new Button();
        button.setText(Component.translatable(key));
        button.setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(5.0F);
            layout.top(5.0F);
            layout.width(50.0F);
            layout.height(22.0F);
        });
        return button;
    }

    private static Label label(Component text, Horizontal horizontal, int color, int height) {
        Label label = new Label();
        label.setText(text);
        label.layout(layout -> {
            layout.widthPercent(100.0F);
            layout.height((float) height);
        });
        label.textStyle(style -> style.textColor(color).textShadow(true)
                .textAlignHorizontal(horizontal).textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static void select(BlockPos boxPos, String cropId) {
        PacketDistributor.sendToServer(new FarmlandBoxSetCropPacket(boxPos, cropId), new CustomPacketPayload[0]);
    }

    private static void back(BlockPos boxPos) {
        PacketDistributor.sendToServer(new FarmlandBoxOpenRequestPacket(boxPos), new CustomPacketPayload[0]);
    }

    private static boolean matchesVanilla(String query, FarmCrop crop) {
        if (query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return crop.id().contains(needle)
                || Component.translatable(crop.translationKey()).getString().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean matchesSpec(String query, CropSpec spec) {
        if (query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return spec.id().toLowerCase(Locale.ROOT).contains(needle)
                || Component.translatable(spec.displayNameKey()).getString().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean matchesBridged(String query, String id, String displayName) {
        if (query.isBlank()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return id.toLowerCase(Locale.ROOT).contains(needle)
                || displayName.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String modName(String modId) {
        String mapped = MOD_NAMES.get(modId);
        if (mapped != null) {
            return mapped;
        }
        try {
            return ModList.get().getModContainerById(modId)
                    .map(container -> container.getModInfo().getDisplayName())
                    .filter(name -> name != null && !name.isBlank())
                    .orElse(modId);
        } catch (Throwable t) {
            return modId;
        }
    }

    /** 分组大类：1 原版；2 农夫乐事系；3 森罗物语：厨房系；4 沉浸农艺 / Let's Do 系；5 其他。 */
    private static int category(String modId) {
        if (modId == null || modId.isBlank()) {
            return 5;
        }
        String id = modId.toLowerCase(Locale.ROOT);
        if (id.startsWith("kaleidoscope_")) {
            return 3;
        }
        if ("farmersdelight".equals(id) || id.contains("delight") || DELIGHT_ADDONS.contains(id)) {
            return 2;
        }
        if ("farm_and_charm".equals(id) || id.startsWith("letsdo") || LETSDO_MODS.contains(id)) {
            return 4;
        }
        return 5;
    }

    private static boolean isBaseSource(String modId) {
        return modId != null && ALWAYS_EXPANDED.contains(modId.toLowerCase(Locale.ROOT));
    }

    /** 是否给折叠头：基础来源与单作物分组永不折叠，其余默认折叠。 */
    private static boolean foldable(String source, int rowCount) {
        return !isBaseSource(source) && rowCount > 1;
    }

    /** 分组排序：大类 → 大类代表模组优先 → 名称。 */
    private static int sourceOrder(String left, String right) {
        int byCategory = Integer.compare(category(left), category(right));
        if (byCategory != 0) {
            return byCategory;
        }
        boolean leftBase = isBaseSource(left);
        boolean rightBase = isBaseSource(right);
        if (leftBase != rightBase) {
            return leftBase ? -1 : 1;
        }
        return modName(left).compareTo(modName(right));
    }

    private record Row(String id, ItemStack icon, Component name) {
    }

    private record Section(String key, Component title, List<Row> rows, boolean foldable) {
    }

    private static final class ListState {

        private final FarmlandBoxOpenResponsePacket packet;
        private final UIElement list;
        private final List<CropBridges.Catalog> catalogs;
        private final Set<String> open = new HashSet<>();
        private String query = "";

        private ListState(FarmlandBoxOpenResponsePacket packet, UIElement list, List<CropBridges.Catalog> catalogs) {
            this.packet = packet;
            this.list = list;
            this.catalogs = catalogs;
        }

        private void setQuery(String text) {
            String next = text == null ? "" : text.trim();
            if (!next.equals(this.query)) {
                this.query = next;
                this.rebuild();
            }
        }

        private boolean isOpen(Section section) {
            if (!section.foldable()) {
                return true;
            }
            if (!this.query.isBlank() || this.open.contains(section.key())) {
                return true;
            }
            // 当前选中的作物所在分组默认展开
            String selected = this.packet.cropId();
            for (Row row : section.rows()) {
                if (row.id().equals(selected)) {
                    return true;
                }
            }
            return false;
        }

        private void rebuild() {
            this.list.clearAllChildren();
            boolean any = false;
            for (Section section : this.buildSections()) {
                if (section.rows().isEmpty()) {
                    continue;
                }
                any = true;
                boolean open = this.isOpen(section);
                if (section.foldable()) {
                    this.list.addChild(foldHeader(section.title(), open, () -> {
                        if (!this.open.remove(section.key())) {
                            this.open.add(section.key());
                        }
                        this.rebuild();
                    }));
                } else {
                    this.list.addChild(sectionTitle(section.title()));
                }
                if (open) {
                    for (Row row : section.rows()) {
                        this.list.addChild(cropRow(
                                this.packet.boxPos(),
                                row.id(),
                                row.icon(),
                                row.name(),
                                row.id().equals(this.packet.cropId())
                        ));
                    }
                }
            }
            if (!any) {
                String key = this.query.isBlank()
                        ? "gui.nsukcropapi.select_crop.empty"
                        : "gui.nsukcropapi.select_crop.no_match";
                this.list.addChild(label(Component.translatable(key), Horizontal.LEFT, COLOR_DIM, 14));
            }
        }

        private List<Section> buildSections() {
            List<Section> sections = new ArrayList<>();

            List<Row> vanilla = new ArrayList<>();
            Set<String> overriddenVanilla = new HashSet<>();
            for (FarmCrop crop : FarmCrop.values()) {
                // 附属用同一个 id 注册了替代实现（例如茶风的西瓜）时，直接把这个实现放进原版分组，
                // 避免出现两个同名选项，也避免多出一个空的「原版」标题。
                CropSpec override = CropRegistry.byId(crop.id());
                if (override != null) {
                    overriddenVanilla.add(crop.id());
                    if (!override.workOrDefault().hiddenFromUi(override) && matchesSpec(this.query, override)) {
                        vanilla.add(new Row(override.id(), new ItemStack(override.icon()),
                                Component.translatable(override.displayNameKey())));
                    }
                    continue;
                }
                if (matchesVanilla(this.query, crop)) {
                    vanilla.add(new Row(crop.id(), new ItemStack(crop.seed()),
                            Component.translatable(crop.translationKey())));
                }
            }
            if (!vanilla.isEmpty()) {
                sections.add(new Section(KEY_VANILLA,
                        Component.translatable("gui.nsukcropapi.select_crop.group_vanilla"), vanilla, false));
            }

            Map<String, List<Row>> registered = new LinkedHashMap<>();
            Map<String, String> groupKeys = new LinkedHashMap<>();
            for (CropSpec spec : CropRegistry.all()) {
                if (overriddenVanilla.contains(spec.id())) {
                    continue;
                }
                // 来源开关：关掉的来源不进选择界面；已种着该作物的农田盒不受影响。
                if (!CropApiConfig.isSourceEnabled(spec.sourceModId())) {
                    continue;
                }
                if (spec.workOrDefault().hiddenFromUi(spec)) {
                    continue;
                }
                if (!matchesSpec(this.query, spec)) {
                    continue;
                }
                String source = spec.sourceModId() == null || spec.sourceModId().isBlank()
                        ? "unknown"
                        : spec.sourceModId();
                registered.computeIfAbsent(source, key -> new ArrayList<>())
                        .add(new Row(spec.id(), new ItemStack(spec.icon()), Component.translatable(spec.displayNameKey())));
                if (spec.groupNameKey() != null && !spec.groupNameKey().isBlank()) {
                    groupKeys.putIfAbsent(source, spec.groupNameKey());
                }
            }
            Map<String, List<Row>> bridged = new LinkedHashMap<>();
            Set<String> seen = new HashSet<>();
            for (CropBridges.Catalog catalog : this.catalogs) {
                for (CropBridges.Entry entry : catalog.entries()) {
                    if (!seen.add(entry.id()) || CropRegistry.byId(entry.id()) != null) {
                        continue;
                    }
                    String displayName = Component.translatable(entry.seed().getDescriptionId()).getString();
                    if (!matchesBridged(this.query, entry.id(), displayName)) {
                        continue;
                    }
                    String source = entry.sourceModId();
                    if (source == null || source.isBlank()) {
                        source = catalog.modId();
                    }
                    bridged.computeIfAbsent(source, key -> new ArrayList<>())
                            .add(new Row(entry.id(), new ItemStack(entry.seed()),
                                    Component.translatable(entry.seed().getDescriptionId())));
                }
            }

            // 新 API 注册的作物与老附属桥接的作物合并到同一来源分组，再统一排序
            Map<String, List<Row>> merged = new LinkedHashMap<>();
            Map<String, Component> titles = new LinkedHashMap<>();
            for (Map.Entry<String, List<Row>> entry : registered.entrySet()) {
                merged.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).addAll(entry.getValue());
                if (groupKeys.containsKey(entry.getKey())) {
                    titles.put(entry.getKey(), Component.translatable(groupKeys.get(entry.getKey())));
                }
            }
            for (Map.Entry<String, List<Row>> entry : bridged.entrySet()) {
                merged.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).addAll(entry.getValue());
                titles.putIfAbsent(entry.getKey(), Component.literal(modName(entry.getKey())));
            }

            List<String> sources = new ArrayList<>(merged.keySet());
            sources.sort(CropSelectScreen::sourceOrder);
            for (String source : sources) {
                List<Row> rows = merged.get(source);
                Component title = titles.getOrDefault(source, Component.literal(modName(source)));
                sections.add(new Section("src:" + source, title, rows, foldable(source, rows.size())));
            }
            return sections;
        }
    }

    private static final class CropScreen extends ModularUIScreen {
        private CropScreen(ModularUI modularUI, Component title) {
            super(modularUI, title);
        }
    }
}
