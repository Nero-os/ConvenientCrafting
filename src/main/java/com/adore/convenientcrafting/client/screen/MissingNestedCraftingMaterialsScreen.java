package com.adore.convenientcrafting.client.screen;

import com.adore.convenientcrafting.network.NestedCraftingMissingMaterialsPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归合成预检失败时展示的缺失材料弹窗。
 *
 * <p>弹窗使用手绘面板和手绘确认按钮，避免原生按钮控件与自绘窗口产生层级错乱。
 * 材料内容以默认全展开的树形列表展示，红色行代表真正缺少的原材料。</p>
 */
public class MissingNestedCraftingMaterialsScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 380;
    private static final int PANEL_MIN_WIDTH = 240;
    private static final int PANEL_MAX_HEIGHT = 320;
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 48;
    private static final int FOOTER_HEIGHT = 38;
    private static final int SIDE_PADDING = 14;

    private final Screen previous;
    private final List<NestedCraftingMissingMaterialsPacket.MissingMaterialRow> rows;
    private int scrollOffset;

    /**
     * 创建缺失材料弹窗。
     *
     * @param previous 关闭弹窗后要返回的上一个界面
     * @param rows 服务端生成的树形材料行
     */
    private MissingNestedCraftingMaterialsScreen(Screen previous, List<NestedCraftingMissingMaterialsPacket.MissingMaterialRow> rows) {
        super(Component.translatable("gui.convenientcrafting.nested_missing.title"));
        this.previous = previous;
        this.rows = new ArrayList<>();
        for (NestedCraftingMissingMaterialsPacket.MissingMaterialRow row : rows) {
            if (row != null && !row.stack().isEmpty()) {
                this.rows.add(new NestedCraftingMissingMaterialsPacket.MissingMaterialRow(
                        row.stack().copy(),
                        row.missing(),
                        Math.max(0, row.depth())
                ));
            }
        }
    }

    /**
     * 打开缺失材料弹窗。
     *
     * @param rows 服务端生成的树形材料行
     */
    public static void open(List<NestedCraftingMissingMaterialsPacket.MissingMaterialRow> rows) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new MissingNestedCraftingMaterialsScreen(minecraft.screen, rows));
    }

    /**
     * 绘制弹窗、树形材料列表、滚动条、确认按钮和物品 tooltip。
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        clampScroll();

        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        int listTop = panelY + HEADER_HEIGHT;
        int listBottom = panelY + panelHeight - FOOTER_HEIGHT;
        int listHeight = Math.max(1, listBottom - listTop);

        // 先绘制完整面板，再绘制手绘按钮，避免按钮被背景或列表裁剪层盖住。
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0101010);
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFF4F6CFF);
        guiGraphics.fill(panelX + 8, listTop - 2, panelX + panelWidth - 8, listBottom + 2, 0x80202020);

        guiGraphics.drawString(font, title, panelX + SIDE_PADDING, panelY + 11, 0xFFFFFFFF, false);
        guiGraphics.drawString(font, Component.translatable("gui.convenientcrafting.nested_missing.message"), panelX + SIDE_PADDING, panelY + 27, 0xFFAAAAAA, false);

        ItemStack hoveredStack = ItemStack.EMPTY;
        guiGraphics.enableScissor(panelX + 8, listTop, panelX + panelWidth - 8, listBottom);
        for (int i = 0; i < rows.size(); i++) {
            // 列表只绘制可见行，避免材料树很长时做无意义绘制。
            int rowY = listTop + i * ROW_HEIGHT - scrollOffset;
            if (rowY <= listTop - ROW_HEIGHT || rowY >= listBottom) {
                continue;
            }

            NestedCraftingMissingMaterialsPacket.MissingMaterialRow row = rows.get(i);
            int depth = Math.min(row.depth(), 10);
            int itemX = panelX + SIDE_PADDING + depth * 14;
            int iconY = rowY + 2;
            boolean hovered = isInside(mouseX, mouseY, panelX + 8, rowY, panelWidth - 16, ROW_HEIGHT);
            if (hovered) {
                guiGraphics.fill(panelX + 9, rowY, panelX + panelWidth - 9, rowY + ROW_HEIGHT, 0x332A3350);
                hoveredStack = row.stack();
            }

            if (depth > 0) {
                // 简单画出树枝线，让用户能看出中间材料和子材料的层级关系。
                int branchX = itemX - 7;
                guiGraphics.fill(branchX, rowY + 10, itemX - 2, rowY + 11, 0xFF555555);
                guiGraphics.fill(branchX, rowY + 4, branchX + 1, rowY + 11, 0xFF555555);
            }

            guiGraphics.renderItem(row.stack(), itemX, iconY);
            guiGraphics.renderItemDecorations(font, row.stack(), itemX, iconY);

            int textX = itemX + 20;
            int textColor = row.depth() == 0 ? 0xFFFFE3A3 : row.missing() ? 0xFFFF6B6B : 0xFFE0E0E0;
            String name = row.stack().getHoverName().getString();
            if (row.missing()) {
                // 缺失的叶子节点用红点和红字标记。
                guiGraphics.fill(textX - 5, rowY + 8, textX - 3, rowY + 10, 0xFFFF5555);
            }
            int textWidth = panelX + panelWidth - 22 - textX;
            if (font.width(name) > textWidth) {
                name = font.ellipsize(FormattedText.of(name), textWidth).getString();
            }
            guiGraphics.drawString(font, name, textX, rowY + 6, textColor, false);
        }
        guiGraphics.disableScissor();

        drawScrollBar(guiGraphics, panelX, panelY, panelWidth, panelHeight, listTop, listHeight);
        drawDoneButton(guiGraphics, mouseX, mouseY, panelX, panelY, panelWidth, panelHeight);

        if (!hoveredStack.isEmpty()) {
            guiGraphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
        }
    }

    /**
     * 绘制材料树滚动条。
     */
    private void drawScrollBar(GuiGraphics guiGraphics, int panelX, int panelY, int panelWidth, int panelHeight, int listTop, int listHeight) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) {
            return;
        }

        int trackX = panelX + panelWidth - 10;
        int trackY = listTop;
        int trackHeight = listHeight;
        int thumbHeight = Math.max(18, trackHeight * trackHeight / Math.max(trackHeight, getContentHeight()));
        int thumbY = trackY + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
        guiGraphics.fill(trackX, trackY, trackX + 3, trackY + trackHeight, 0xFF252525);
        guiGraphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0xFF6F7DFF);
    }

    /**
     * 绘制手写风格的确认按钮。
     *
     * <p>按钮文字使用 {@code gui.done}，会跟随 Minecraft 当前语言显示。</p>
     */
    private void drawDoneButton(GuiGraphics guiGraphics, int mouseX, int mouseY, int panelX, int panelY, int panelWidth, int panelHeight) {
        int buttonWidth = 84;
        int buttonHeight = 20;
        int buttonX = panelX + panelWidth / 2 - buttonWidth / 2;
        int buttonY = panelY + panelHeight - 28;
        boolean hovered = isInside(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);
        int borderColor = hovered ? 0xFF6F7DFF : 0xFF4F6CFF;
        int fillColor = hovered ? 0xFF343B52 : 0xFF2A2E38;

        guiGraphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, 0xFF080808);
        guiGraphics.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + buttonHeight - 1, borderColor);
        guiGraphics.fill(buttonX + 2, buttonY + 2, buttonX + buttonWidth - 2, buttonY + buttonHeight - 2, fillColor);

        Component label = Component.translatable("gui.done");
        guiGraphics.drawString(font, label, buttonX + (buttonWidth - font.width(label)) / 2, buttonY + 6, 0xFFFFFFFF, false);
    }

    /**
     * 处理确认按钮点击。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int panelWidth = getPanelWidth();
            int panelHeight = getPanelHeight();
            int panelX = (width - panelWidth) / 2;
            int panelY = (height - panelHeight) / 2;
            int buttonWidth = 84;
            int buttonHeight = 20;
            int buttonX = panelX + panelWidth / 2 - buttonWidth / 2;
            int buttonY = panelY + panelHeight - 28;
            if (isInside(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 在材料树区域内滚动列表。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        int listTop = panelY + HEADER_HEIGHT;
        int listBottom = panelY + panelHeight - FOOTER_HEIGHT;
        if (isInside(mouseX, mouseY, panelX + 8, listTop, panelWidth - 16, listBottom - listTop)) {
            scrollOffset -= (int) Math.signum(scrollY) * ROW_HEIGHT;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * 根据游戏窗口宽度计算弹窗宽度。
     */
    private int getPanelWidth() {
        int availableWidth = Math.max(PANEL_MIN_WIDTH, width - 32);
        return Math.min(PANEL_MAX_WIDTH, availableWidth);
    }

    /**
     * 根据材料树长度和游戏窗口高度计算弹窗高度。
     */
    private int getPanelHeight() {
        int wantedHeight = HEADER_HEIGHT + FOOTER_HEIGHT + Math.max(3, rows.size()) * ROW_HEIGHT + 8;
        int availableHeight = Math.max(160, height - 48);
        return Math.min(Math.min(PANEL_MAX_HEIGHT, availableHeight), wantedHeight);
    }

    /**
     * 获取完整材料树内容高度。
     */
    private int getContentHeight() {
        return rows.size() * ROW_HEIGHT;
    }

    /**
     * 获取最大滚动距离。
     */
    private int getMaxScroll() {
        int listHeight = getPanelHeight() - HEADER_HEIGHT - FOOTER_HEIGHT;
        return Math.max(0, getContentHeight() - listHeight);
    }

    /**
     * 将滚动位置限制在合法范围内。
     */
    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
    }

    /**
     * 判断鼠标是否位于指定矩形区域内。
     */
    private static boolean isInside(double mouseX, double mouseY, int x, int y, int buttonWidth, int buttonHeight) {
        return mouseX >= x && mouseX < x + buttonWidth && mouseY >= y && mouseY < y + buttonHeight;
    }

    /**
     * 关闭弹窗并返回原界面。
     */
    @Override
    public void onClose() {
        minecraft.setScreen(previous);
    }
}
