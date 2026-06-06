package com.adore.smartbundle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

public class CraftHelperScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 270;
    private static final int RECIPE_PER_PAGE = 4;
    private static final int ROW_HEIGHT = 42;
    private static final int CONTROLS_Y_OFFSET = 18;
    private static final int LIST_Y_OFFSET = 48;
    private static final int RESULT_X_OFFSET = 10;
    private static final int NAME_X_OFFSET = 34;
    private static final int INGREDIENT_X_OFFSET = 146;
    private static final int CRAFT_BUTTON_X_OFFSET = PANEL_WIDTH - 42;
    private static final int MAX_VISIBLE_INGREDIENTS = 4;

    private int panelX;
    private int panelY;
    private int currentPage = 0;
    private boolean onlyCraftable = false;
    private boolean searchFocused = false;
    private String searchText = "";
    private List<RecipeHolder<CraftingRecipe>> allRecipes = new ArrayList<>();
    private List<RecipeHolder<CraftingRecipe>> sortedRecipes = new ArrayList<>();

    // 玩家背包快照缓存，用于排序评分
    private Map<String, Integer> cachedInventoryCounts = new HashMap<>();

    protected CraftHelperScreen() {
        super(Component.translatable("gui.smartbundle.craft_helper"));
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        loadRecipes();
        refreshInventoryCache();
        sortRecipes();
        refreshButtons();
    }

    // ──────────────────────────────────────────────
    //  配方加载（全量扫描，JEI 兼容）
    // ──────────────────────────────────────────────

    private void loadRecipes() {
        allRecipes.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Set<ResourceLocation> seen = new HashSet<>();
        var recipeManager = mc.level.getRecipeManager();

        // 获取所有已注册的 CRAFTING 类型配方
        // JEI 和其他模组通过 datapack 添加的合成配方也会出现在这里
        for (RecipeHolder<CraftingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(mc.level.registryAccess());
            if (!result.isEmpty() && seen.add(holder.id())) {
                allRecipes.add(holder);
            }
        }
    }

    // ──────────────────────────────────────────────
    //  智能排序
    // ──────────────────────────────────────────────

    private void refreshInventoryCache() {
        cachedInventoryCounts.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String key = buildItemKey(stack);
                cachedInventoryCounts.merge(key, stack.getCount(), Integer::sum);
            }
        }
    }

    /**
     * 用物品注册名 + 组件标签作为唯一标识键，替代 1.21.2+ 的 ItemStack.keyOf()
     */
    private static String buildItemKey(ItemStack stack) {
        ResourceLocation loc = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return loc != null ? loc.toString() : "unknown:" + stack.getItem();
    }

    private void sortRecipes() {
        // 预先计算每个配方当前是否能合成
        Map<ResourceLocation, Boolean> craftableCache = new HashMap<>();
        for (RecipeHolder<CraftingRecipe> holder : allRecipes) {
            craftableCache.put(holder.id(), canCraftRecipe(holder.value()));
        }

        sortedRecipes = allRecipes.stream()
                .filter(holder -> matchesFilters(holder, craftableCache))
                .sorted((a, b) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level == null) return 0;

                    ItemStack resultA = a.value().getResultItem(mc.level.registryAccess());
                    ItemStack resultB = b.value().getResultItem(mc.level.registryAccess());

                    boolean canCraftA = craftableCache.getOrDefault(a.id(), false);
                    boolean canCraftB = craftableCache.getOrDefault(b.id(), false);

                    // 规则1：可合成的排前面
                    if (canCraftA != canCraftB) {
                        return canCraftA ? -1 : 1;
                    }

                    // 规则2：玩家已有产物的排前面，按数量降序
                    int countA = cachedInventoryCounts.getOrDefault(buildItemKey(resultA), 0);
                    int countB = cachedInventoryCounts.getOrDefault(buildItemKey(resultB), 0);
                    if (countA != countB) {
                        return Integer.compare(countB, countA);
                    }

                    // 规则3：字母序保底
                    String nameA = resultA.getHoverName().getString();
                    String nameB = resultB.getHoverName().getString();
                    return nameA.compareToIgnoreCase(nameB);
                })
                .collect(Collectors.toList());

        int totalPages = getTotalPages();
        if (totalPages == 0) {
            currentPage = 0;
        } else if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }
    }

    private boolean matchesFilters(RecipeHolder<CraftingRecipe> holder, Map<ResourceLocation, Boolean> craftableCache) {
        if (onlyCraftable && !craftableCache.getOrDefault(holder.id(), false)) {
            return false;
        }

        String query = searchText.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        ItemStack result = holder.value().getResultItem(mc.level.registryAccess());
        String itemName = result.getHoverName().getString().toLowerCase(Locale.ROOT);
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(result.getItem());
        String itemId = itemKey != null ? itemKey.toString().toLowerCase(Locale.ROOT) : "";
        return itemName.contains(query) || itemId.contains(query);
    }

    // ──────────────────────────────────────────────
    //  按钮 & UI
    // ──────────────────────────────────────────────

    private void refreshButtons() {
        clearWidgets();
    }

    // ──────────────────────────────────────────────
    //  可合成检测
    // ──────────────────────────────────────────────

    private boolean canCraftRecipe(CraftingRecipe recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        List<Ingredient> ingredients = getIngredients(recipe);
        if (ingredients.isEmpty()) return false;

        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                available.add(stack.copy());
            }
        }

        for (Ingredient ingredient : ingredients) {
            boolean found = false;
            for (ItemStack stack : available) {
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    stack.shrink(1);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private List<Ingredient> getIngredients(CraftingRecipe recipe) {
        List<Ingredient> ingredients = new ArrayList<>();

        if (recipe instanceof ShapedRecipe shaped) {
            for (Ingredient ingredient : shaped.getIngredients()) {
                addNonEmptyIngredient(ingredients, ingredient);
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            for (Ingredient ingredient : shapeless.getIngredients()) {
                addNonEmptyIngredient(ingredients, ingredient);
            }
        }

        return ingredients;
    }

    private static void addNonEmptyIngredient(List<Ingredient> ingredients, Ingredient ingredient) {
        if (ingredient.getItems().length > 0) {
            ingredients.add(ingredient);
        }
    }

    private ItemStack[] getIngredientStacks(CraftingRecipe recipe) {
        List<Ingredient> ingredients = getIngredients(recipe);
        ItemStack[] stacks = new ItemStack[ingredients.size()];
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack[] matching = ingredients.get(i).getItems();
            stacks[i] = matching.length > 0 ? matching[0].copy() : ItemStack.EMPTY;
        }
        return stacks;
    }

    // ──────────────────────────────────────────────
    //  渲染
    // ──────────────────────────────────────────────

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 面板背景
        guiGraphics.fill(panelX, panelY - 10, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xC0101010);
        guiGraphics.fill(panelX, panelY - 10, panelX + PANEL_WIDTH, panelY - 8, 0xFF4444FF);
        guiGraphics.fill(panelX, panelY + PANEL_HEIGHT - 2, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF4444FF);

        // 标题
        guiGraphics.drawString(font, title, panelX + 8, panelY + 2, 0xFFFFFF, false);

        // 可合成数量统计
        int craftableCount = 0;
        Minecraft mc = Minecraft.getInstance();
        for (RecipeHolder<CraftingRecipe> holder : sortedRecipes) {
            if (canCraftRecipe(holder.value())) craftableCount++;
        }
        String summary = "可合成: " + craftableCount + "/" + sortedRecipes.size();
        guiGraphics.drawString(font, summary, panelX + PANEL_WIDTH - font.width(summary) - 34, panelY + 2, 0x888888, false);

        drawFilterControls(guiGraphics);

        // 页码
        int totalPages = (sortedRecipes.size() + RECIPE_PER_PAGE - 1) / RECIPE_PER_PAGE;
        if (totalPages > 1) {
            String pageStr = (currentPage + 1) + "/" + totalPages;
            guiGraphics.drawString(font, pageStr, panelX + PANEL_WIDTH / 2 - font.width(pageStr) / 2, panelY + PANEL_HEIGHT - 44, 0xAAAAAA, false);
        }

        // 每个配方的条目
        int startIndex = currentPage * RECIPE_PER_PAGE;
        int endIndex = Math.min(startIndex + RECIPE_PER_PAGE, sortedRecipes.size());

        if (sortedRecipes.isEmpty()) {
            String emptyText = "没有匹配的配方";
            guiGraphics.drawString(font, emptyText, panelX + PANEL_WIDTH / 2 - font.width(emptyText) / 2, panelY + LIST_Y_OFFSET + 52, 0xFFAAAAAA, false);
        }

        for (int i = startIndex; i < endIndex; i++) {
            RecipeHolder<CraftingRecipe> holder = sortedRecipes.get(i);
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(mc.level.registryAccess());

            int recipeY = getRecipeY(i - startIndex);
            boolean canCraft = canCraftRecipe(recipe);

            // 产物图标
            guiGraphics.renderItem(result, panelX + RESULT_X_OFFSET, recipeY + 4);
            guiGraphics.renderItemDecorations(font, result, panelX + RESULT_X_OFFSET, recipeY + 4);

            // 产物名称
            String resultName = result.getHoverName().getString();
            int resultNameWidth = INGREDIENT_X_OFFSET - NAME_X_OFFSET - 10;
            if (font.width(resultName) > resultNameWidth) {
                resultName = font.ellipsize(FormattedText.of(resultName), resultNameWidth).getString();
            }
            guiGraphics.drawString(font, resultName, panelX + NAME_X_OFFSET, recipeY + 4, canCraft ? 0xFFFFFF : 0x888888, false);
            guiGraphics.drawString(font, "x" + result.getCount(), panelX + NAME_X_OFFSET, recipeY + 16, 0xAAAAAA, false);

            // 材料图标（最多显示 4 种）
            ItemStack[] ingredients = getIngredientStacks(recipe);
            int ingX = panelX + INGREDIENT_X_OFFSET;
            for (int j = 0; j < Math.min(ingredients.length, MAX_VISIBLE_INGREDIENTS); j++) {
                if (!ingredients[j].isEmpty()) {
                    guiGraphics.renderItem(ingredients[j], ingX + j * 16, recipeY + 6);
                    // 缺失的材料上画红色半透明遮罩
                    if (!hasItemInInventory(ingredients[j])) {
                        guiGraphics.fill(ingX + j * 16, recipeY + 6, ingX + j * 16 + 16, recipeY + 6 + 16, 0x33FF0000);
                    }
                }
            }

            drawPanelButton(guiGraphics, panelX + CRAFT_BUTTON_X_OFFSET, recipeY + 6, 22, 20, canCraft ? ">" : "X", canCraft);
        }

        drawNavigationButtons(guiGraphics);
    }

    private void drawFilterControls(GuiGraphics guiGraphics) {
        int checkboxX = panelX + 10;
        int controlsY = panelY + CONTROLS_Y_OFFSET;
        int checkboxSize = 12;
        int searchX = panelX + 76;
        int searchWidth = PANEL_WIDTH - 116;
        int searchHeight = 18;

        guiGraphics.fill(checkboxX, controlsY + 3, checkboxX + checkboxSize, controlsY + 3 + checkboxSize, 0xFF080808);
        guiGraphics.fill(checkboxX + 1, controlsY + 4, checkboxX + checkboxSize - 1, controlsY + 2 + checkboxSize, onlyCraftable ? 0xFF4F6CFF : 0xFF2A2E38);
        if (onlyCraftable) {
            guiGraphics.drawString(font, "v", checkboxX + 3, controlsY + 4, 0xFFFFFFFF, false);
        }
        guiGraphics.drawString(font, "可合成", checkboxX + checkboxSize + 5, controlsY + 5, 0xFFE0E0E0, false);

        int searchBorder = searchFocused ? 0xFF4F6CFF : 0xFF343434;
        guiGraphics.fill(searchX, controlsY + 1, searchX + searchWidth, controlsY + 1 + searchHeight, 0xFF080808);
        guiGraphics.fill(searchX + 1, controlsY + 2, searchX + searchWidth - 1, controlsY + searchHeight, searchBorder);
        guiGraphics.fill(searchX + 2, controlsY + 3, searchX + searchWidth - 2, controlsY + searchHeight - 1, 0xFF151820);

        String visibleText = searchText;
        int textWidth = searchWidth - 10;
        if (visibleText.isEmpty() && !searchFocused) {
            guiGraphics.drawString(font, "搜索物品", searchX + 5, controlsY + 6, 0xFF777777, false);
        } else {
            if (font.width(visibleText) > textWidth) {
                visibleText = font.plainSubstrByWidth(visibleText, textWidth);
            }
            guiGraphics.drawString(font, visibleText, searchX + 5, controlsY + 6, 0xFFFFFFFF, false);
            if (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int cursorX = searchX + 5 + font.width(visibleText);
                guiGraphics.fill(cursorX, controlsY + 5, cursorX + 1, controlsY + 15, 0xFFFFFFFF);
            }
        }
    }

    private void drawNavigationButtons(GuiGraphics guiGraphics) {
        int totalPages = getTotalPages();
        if (totalPages > 1) {
            drawPanelButton(guiGraphics, panelX + 16, panelY + PANEL_HEIGHT - 34, 34, 20, "<", currentPage > 0);
            drawPanelButton(guiGraphics, panelX + PANEL_WIDTH - 50, panelY + PANEL_HEIGHT - 34, 34, 20, ">", currentPage + 1 < totalPages);
        }

        drawPanelButton(guiGraphics, panelX + PANEL_WIDTH / 2 - 12, panelY + PANEL_HEIGHT - 30, 24, 18, "R", true);
        drawPanelButton(guiGraphics, panelX + PANEL_WIDTH - 22, panelY - 5, 20, 20, "X", true);
    }

    private void drawPanelButton(GuiGraphics guiGraphics, int x, int y, int buttonWidth, int buttonHeight, String label, boolean active) {
        int borderColor = active ? 0xFF4F6CFF : 0xFF343434;
        int fillColor = active ? 0xFF2A2E38 : 0xFF1A1A1A;
        int textColor = active ? 0xFFFFFFFF : 0xFF777777;

        guiGraphics.fill(x, y, x + buttonWidth, y + buttonHeight, 0xFF080808);
        guiGraphics.fill(x + 1, y + 1, x + buttonWidth - 1, y + buttonHeight - 1, borderColor);
        guiGraphics.fill(x + 2, y + 2, x + buttonWidth - 2, y + buttonHeight - 2, fillColor);
        guiGraphics.drawString(font, label, x + (buttonWidth - font.width(label)) / 2, y + (buttonHeight - 8) / 2, textColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int controlsY = panelY + CONTROLS_Y_OFFSET;
        if (isInside(mouseX, mouseY, panelX + 10, controlsY + 3, 64, 14)) {
            onlyCraftable = !onlyCraftable;
            currentPage = 0;
            sortRecipes();
            refreshButtons();
            return true;
        }

        int searchX = panelX + 76;
        int searchWidth = PANEL_WIDTH - 116;
        if (isInside(mouseX, mouseY, searchX, controlsY + 1, searchWidth, 18)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        int totalPages = getTotalPages();
        if (totalPages > 1 && currentPage > 0 && isInside(mouseX, mouseY, panelX + 16, panelY + PANEL_HEIGHT - 34, 34, 20)) {
            currentPage--;
            refreshButtons();
            return true;
        }
        if (totalPages > 1 && currentPage + 1 < totalPages && isInside(mouseX, mouseY, panelX + PANEL_WIDTH - 50, panelY + PANEL_HEIGHT - 34, 34, 20)) {
            currentPage++;
            refreshButtons();
            return true;
        }
        if (isInside(mouseX, mouseY, panelX + PANEL_WIDTH / 2 - 12, panelY + PANEL_HEIGHT - 30, 24, 18)) {
            refreshInventoryCache();
            sortRecipes();
            currentPage = 0;
            refreshButtons();
            return true;
        }
        if (isInside(mouseX, mouseY, panelX + PANEL_WIDTH - 22, panelY - 5, 20, 20)) {
            onClose();
            return true;
        }

        int startIndex = currentPage * RECIPE_PER_PAGE;
        int endIndex = Math.min(startIndex + RECIPE_PER_PAGE, sortedRecipes.size());
        for (int i = startIndex; i < endIndex; i++) {
            RecipeHolder<CraftingRecipe> holder = sortedRecipes.get(i);
            CraftingRecipe recipe = holder.value();
            int recipeY = getRecipeY(i - startIndex);

            if (isInside(mouseX, mouseY, panelX + CRAFT_BUTTON_X_OFFSET, recipeY + 6, 22, 20) && canCraftRecipe(recipe)) {
                PacketDistributor.sendToServer(new CraftRecipePacket(holder.id()));
                refreshInventoryCache();
                sortRecipes();
                refreshButtons();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int getTotalPages() {
        return (sortedRecipes.size() + RECIPE_PER_PAGE - 1) / RECIPE_PER_PAGE;
    }

    private int getRecipeY(int rowIndex) {
        return panelY + LIST_Y_OFFSET + rowIndex * ROW_HEIGHT;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int buttonWidth, int buttonHeight) {
        return mouseX >= x && mouseX < x + buttonWidth && mouseY >= y && mouseY < y + buttonHeight;
    }

    private boolean hasItemInInventory(ItemStack needed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        int needCount = needed.getCount();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, needed)) {
                needCount -= stack.getCount();
                if (needCount <= 0) return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            if (keyCode == 259 && !searchText.isEmpty()) { // Backspace
                searchText = searchText.substring(0, searchText.length() - 1);
                currentPage = 0;
                sortRecipes();
                refreshButtons();
                return true;
            }
            if (keyCode == 261 && !searchText.isEmpty()) { // Delete
                searchText = "";
                currentPage = 0;
                sortRecipes();
                refreshButtons();
                return true;
            }
            if (keyCode == 257 || keyCode == 256) { // Enter or ESC
                searchFocused = false;
                return true;
            }
        }

        if (keyCode == 256) { // ESC 关闭
            onClose();
            return true;
        }
        if (keyCode == 71 && !searchFocused) { // G 键关闭面板
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!searchFocused || Character.isISOControl(codePoint)) {
            return super.charTyped(codePoint, modifiers);
        }

        searchText += codePoint;
        currentPage = 0;
        sortRecipes();
        refreshButtons();
        return true;
    }
}
