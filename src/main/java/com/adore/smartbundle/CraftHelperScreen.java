package com.adore.smartbundle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 220;
    private static final int RECIPE_PER_PAGE = 4;

    private int panelX;
    private int panelY;
    private int currentPage = 0;
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
    }

    // ──────────────────────────────────────────────
    //  按钮 & UI
    // ──────────────────────────────────────────────

    private void refreshButtons() {
        clearWidgets();

        int startIndex = currentPage * RECIPE_PER_PAGE;
        int endIndex = Math.min(startIndex + RECIPE_PER_PAGE, sortedRecipes.size());

        for (int i = startIndex; i < endIndex; i++) {
            RecipeHolder<CraftingRecipe> holder = sortedRecipes.get(i);
            CraftingRecipe recipe = holder.value();
            int recipeY = panelY + 10 + (i - startIndex) * 50;
            boolean canCraft = canCraftRecipe(recipe);

            addRenderableWidget(Button.builder(
                    Component.literal(canCraft ? "▸" : "✗"),
                    btn -> {
                        if (canCraftRecipe(recipe)) {
                            PacketDistributor.sendToServer(new CraftRecipePacket(holder.id()));
                            // 合成后刷新状态
                            refreshInventoryCache();
                            sortRecipes();
                            refreshButtons();
                        }
                    }
                )
                .bounds(panelX + PANEL_WIDTH - 55, recipeY + 6, 20, 20)
                .build()
            );
        }

        // 翻页按钮
        int totalPages = (sortedRecipes.size() + RECIPE_PER_PAGE - 1) / RECIPE_PER_PAGE;
        if (totalPages > 1) {
            if (currentPage > 0) {
                addRenderableWidget(Button.builder(
                        Component.literal("◀"),
                        btn -> {
                            currentPage--;
                            refreshButtons();
                        }
                    )
                    .bounds(panelX + 10, panelY + PANEL_HEIGHT - 30, 30, 20)
                    .build()
                );
            }
            if (currentPage + 1 < totalPages) {
                addRenderableWidget(Button.builder(
                        Component.literal("▶"),
                        btn -> {
                            currentPage++;
                            refreshButtons();
                        }
                    )
                    .bounds(panelX + PANEL_WIDTH - 60, panelY + PANEL_HEIGHT - 30, 30, 20)
                    .build()
                );
            }
        }

        // 刷新按钮 ↻ — 重新扫描背包的可合成状态
        addRenderableWidget(Button.builder(
                Component.literal("↻"),
                btn -> {
                    refreshInventoryCache();
                    sortRecipes();
                    currentPage = 0;
                    refreshButtons();
                }
            )
            .bounds(panelX + PANEL_WIDTH / 2 - 12, panelY + PANEL_HEIGHT - 30, 24, 20)
            .build()
        );

        // 关闭按钮
        addRenderableWidget(Button.builder(
                Component.literal("✕"),
                btn -> onClose()
            )
            .bounds(panelX + PANEL_WIDTH - 22, panelY - 5, 20, 20)
            .build()
        );
    }

    // ──────────────────────────────────────────────
    //  可合成检测
    // ──────────────────────────────────────────────

    private boolean canCraftRecipe(CraftingRecipe recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        ItemStack[] ingredients = getIngredientStacks(recipe);

        // 统计背包中各物品总量
        Map<ItemStack, Integer> available = new HashMap<>();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                boolean merged = false;
                for (var entry : available.entrySet()) {
                    if (ItemStack.isSameItemSameComponents(entry.getKey(), stack)) {
                        available.put(entry.getKey(), entry.getValue() + stack.getCount());
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    available.put(stack.copy(), stack.getCount());
                }
            }
        }

        // 逐一检查每种材料是否足够
        boolean[] used = new boolean[available.size()];
        for (ItemStack needed : ingredients) {
            if (needed.isEmpty()) continue;
            boolean found = false;
            int idx = 0;
            for (var entry : available.entrySet()) {
                if (!used[idx]
                    && ItemStack.isSameItemSameComponents(entry.getKey(), needed)
                    && entry.getValue() >= needed.getCount()) {
                    used[idx] = true;
                    found = true;
                    break;
                }
                idx++;
            }
            if (!found) return false;
        }
        return true;
    }

    private ItemStack[] getIngredientStacks(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            List<Ingredient> ingredients = shaped.getIngredients();
            ItemStack[] stacks = new ItemStack[ingredients.size()];
            for (int i = 0; i < ingredients.size(); i++) {
                Ingredient ing = ingredients.get(i);
                ItemStack[] matching = ing.getItems();
                stacks[i] = matching.length > 0 ? matching[0].copy() : ItemStack.EMPTY;
            }
            return stacks;
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<Ingredient> ingredients = shapeless.getIngredients();
            ItemStack[] stacks = new ItemStack[ingredients.size()];
            for (int i = 0; i < ingredients.size(); i++) {
                Ingredient ing = ingredients.get(i);
                ItemStack[] matching = ing.getItems();
                stacks[i] = matching.length > 0 ? matching[0].copy() : ItemStack.EMPTY;
            }
            return stacks;
        }
        return new ItemStack[0];
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
        guiGraphics.drawString(font, summary, panelX + PANEL_WIDTH - font.width(summary) - 8, panelY + 2, 0x888888, false);

        // 页码
        int totalPages = (sortedRecipes.size() + RECIPE_PER_PAGE - 1) / RECIPE_PER_PAGE;
        if (totalPages > 1) {
            String pageStr = (currentPage + 1) + "/" + totalPages;
            guiGraphics.drawString(font, pageStr, panelX + PANEL_WIDTH / 2 - font.width(pageStr) / 2, panelY + PANEL_HEIGHT - 28, 0xAAAAAA, false);
        }

        // 每个配方的条目
        int startIndex = currentPage * RECIPE_PER_PAGE;
        int endIndex = Math.min(startIndex + RECIPE_PER_PAGE, sortedRecipes.size());

        for (int i = startIndex; i < endIndex; i++) {
            RecipeHolder<CraftingRecipe> holder = sortedRecipes.get(i);
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(mc.level.registryAccess());

            int recipeY = panelY + 10 + (i - startIndex) * 50;
            boolean canCraft = canCraftRecipe(recipe);

            // 产物图标
            guiGraphics.renderItem(result, panelX + 8, recipeY + 4);
            guiGraphics.renderItemDecorations(font, result, panelX + 8, recipeY + 4);

            // 产物名称
            String resultName = result.getHoverName().getString();
            if (font.width(resultName) > 85) {
                resultName = font.ellipsize(FormattedText.of(resultName), 85).getString();
            }
            guiGraphics.drawString(font, resultName, panelX + 30, recipeY + 4, canCraft ? 0xFFFFFF : 0x888888, false);
            guiGraphics.drawString(font, "×" + result.getCount(), panelX + 30, recipeY + 16, 0xAAAAAA, false);

            // 材料图标（最多显示 5 种）
            ItemStack[] ingredients = getIngredientStacks(recipe);
            int ingX = panelX + 90;
            for (int j = 0; j < Math.min(ingredients.length, 5); j++) {
                if (!ingredients[j].isEmpty()) {
                    guiGraphics.renderItem(ingredients[j], ingX + j * 16, recipeY + 6);
                    // 缺失的材料上画红色半透明遮罩
                    if (!hasItemInInventory(ingredients[j])) {
                        guiGraphics.fill(ingX + j * 16, recipeY + 6, ingX + j * 16 + 16, recipeY + 6 + 16, 0x33FF0000);
                    }
                }
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
        if (keyCode == 256) { // ESC 关闭
            onClose();
            return true;
        }
        if (keyCode == 71) { // G 键关闭面板
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
