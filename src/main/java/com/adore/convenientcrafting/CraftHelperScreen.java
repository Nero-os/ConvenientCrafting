package com.adore.convenientcrafting;

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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 合成助手界面。
 *
 * <p>界面会列出当前世界注册的工作台和锻造台配方，并支持搜索、仅显示可合成配方、
 * 按可合成状态和玩家已有产物数量排序，以及向服务端发送一键合成请求。</p>
 */
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
    private List<RecipeHolder<?>> allRecipes = new ArrayList<>();
    private List<RecipeHolder<?>> sortedRecipes = new ArrayList<>();

    /**
     * 玩家背包快照缓存，用于排序评分。
     *
     * <p>键为物品注册名，值为当前背包中该物品的总数量。</p>
     */
    private Map<String, Integer> cachedInventoryCounts = new HashMap<>();

    /**
     * 创建合成助手界面。
     */
    protected CraftHelperScreen() {
        super(Component.translatable("gui.convenientcrafting.craft_helper"));
    }

    /**
     * 初始化界面位置、配方列表、背包缓存和排序结果。
     */
    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        loadRecipes();
        refreshInventoryCache();
        sortRecipes();
        refreshButtons();
    }

    /**
     * 加载当前世界中的所有工作台合成配方。
     *
     * <p>该方法从客户端世界的配方管理器读取 {@link RecipeType#CRAFTING} 和
     * {@link RecipeType#SMITHING} 配方。
     * 通过数据包或 datapack 注册的模组配方也会出现在这里；使用 {@code seen} 去重，
     * 避免相同配方 ID 被重复加入。</p>
     */
    private void loadRecipes() {
        allRecipes.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Set<ResourceLocation> seen = new HashSet<>();
        var recipeManager = mc.level.getRecipeManager();

        // 获取所有已注册的 CRAFTING 类型配方。
        for (RecipeHolder<CraftingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(mc.level.registryAccess());
            if (!result.isEmpty() && seen.add(holder.id())) {
                allRecipes.add(holder);
            }
        }

        // 获取所有已注册的 SMITHING 类型配方，先支持锻造台的一键合成。
        for (RecipeHolder<SmithingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.SMITHING)) {
            SmithingRecipe recipe = holder.value();
            ItemStack result = getRecipeResult(recipe);
            if (!result.isEmpty() && seen.add(holder.id())) {
                allRecipes.add(holder);
            }
        }
    }

    /**
     * 刷新玩家背包物品数量缓存。
     *
     * <p>排序时需要知道玩家已经拥有多少个配方产物。每次点击刷新或合成后，
     * 都会重建一次缓存，避免排序依据停留在旧背包状态。</p>
     */
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
     * 构建用于背包数量缓存的物品键。
     *
     * @param stack 要生成键的物品堆
     * @return 物品注册名字符串；注册名不存在时返回降级键
     */
    private static String buildItemKey(ItemStack stack) {
        ResourceLocation loc = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return loc != null ? loc.toString() : "unknown:" + stack.getItem();
    }

    /**
     * 根据当前筛选条件和排序规则刷新配方列表。
     *
     * <p>排序分三层：先把可合成的配方排在前面；再把玩家背包中已有产物更多的配方排在前面；
     * 最后使用产物名称做稳定兜底排序。可合成状态会预先缓存，避免排序比较器反复扫描背包。</p>
     */
    private void sortRecipes() {
        // 预先计算每个配方当前是否能合成
        Map<ResourceLocation, Boolean> craftableCache = new HashMap<>();
        for (RecipeHolder<?> holder : allRecipes) {
            craftableCache.put(holder.id(), canCraftRecipe(holder.value()));
        }

        sortedRecipes = allRecipes.stream()
                .filter(holder -> matchesFilters(holder, craftableCache))
                .sorted((a, b) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level == null) return 0;

                    ItemStack resultA = getRecipeResult(a.value());
                    ItemStack resultB = getRecipeResult(b.value());

                    boolean canCraftA = craftableCache.getOrDefault(a.id(), false);
                    boolean canCraftB = craftableCache.getOrDefault(b.id(), false);

                    // 规则 1：可合成配方优先展示，玩家打开面板后能立刻看到可执行操作。
                    if (canCraftA != canCraftB) {
                        return canCraftA ? -1 : 1;
                    }

                    // 规则 2：已有产物越多，说明玩家更可能在补充同类物品，因此按数量降序。
                    int countA = cachedInventoryCounts.getOrDefault(buildItemKey(resultA), 0);
                    int countB = cachedInventoryCounts.getOrDefault(buildItemKey(resultB), 0);
                    if (countA != countB) {
                        return Integer.compare(countB, countA);
                    }

                    // 规则 3：前两项都相同则按名称排序，保证列表顺序稳定。
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

    /**
     * 判断配方是否符合当前筛选条件。
     *
     * @param holder 配方持有者
     * @param craftableCache 可合成状态缓存
     * @return 通过筛选时返回 {@code true}
     */
    private boolean matchesFilters(RecipeHolder<?> holder, Map<ResourceLocation, Boolean> craftableCache) {
        if (onlyCraftable && !craftableCache.getOrDefault(holder.id(), false)) {
            return false;
        }

        String query = searchText.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        ItemStack result = getRecipeResult(holder.value());
        String itemName = result.getHoverName().getString().toLowerCase(Locale.ROOT);
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(result.getItem());
        String itemId = itemKey != null ? itemKey.toString().toLowerCase(Locale.ROOT) : "";
        return itemName.contains(query) || itemId.contains(query);
    }

    /**
     * 刷新界面按钮。
     *
     * <p>当前面板按钮由渲染方法手绘，因此这里主要清理原生小部件列表。</p>
     */
    private void refreshButtons() {
        clearWidgets();
    }

    /**
     * 判断当前玩家背包是否能合成指定配方。
     *
     * <p>算法和服务端校验保持一致：先复制背包中所有非空物品堆，再逐个材料槽尝试匹配。
     * 每次匹配成功都会从副本中扣 1 个物品，确保同一份材料不会被多个配方槽重复使用。</p>
     *
     * @param recipe 要检查的配方
     * @return 背包材料足够时返回 {@code true}
     */
    private boolean canCraftRecipe(Recipe<?> recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        if (recipe instanceof CraftingRecipe craftingRecipe) {
            return hasIngredients(getIngredients(craftingRecipe));
        }

        if (recipe instanceof SmithingRecipe smithingRecipe) {
            SmithingMatch match = findSmithingMatch(smithingRecipe);
            if (match == null || mc.level == null) return false;

            SmithingRecipeInput input = new SmithingRecipeInput(match.template(), match.base(), match.addition());
            return smithingRecipe.matches(input, mc.level) && !smithingRecipe.assemble(input, mc.level.registryAccess()).isEmpty();
        }

        return false;
    }

    /**
     * 判断当前玩家背包是否满足一组合成材料。
     *
     * @param ingredients 要检查的材料列表
     * @return 材料都能匹配时返回 {@code true}
     */
    private boolean hasIngredients(List<Ingredient> ingredients) {
        if (ingredients.isEmpty()) return false;

        List<ItemStack> available = getInventorySnapshot();
        for (Ingredient ingredient : ingredients) {
            ItemStack matched = takeFirstMatching(available, ingredient::test);
            if (matched.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 提取合成配方中的非空材料。
     *
     * @param recipe 要解析的合成配方
     * @return 去除空槽后的材料列表
     */
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

    /**
     * 仅在材料包含可匹配物品时加入列表。
     *
     * @param ingredients 目标材料列表
     * @param ingredient 待加入的材料
     */
    private static void addNonEmptyIngredient(List<Ingredient> ingredients, Ingredient ingredient) {
        if (ingredient.getItems().length > 0) {
            ingredients.add(ingredient);
        }
    }

    /**
     * 获取用于界面展示的材料图标。
     *
     * @param recipe 要展示的合成配方
     * @return 每个材料槽的第一个可匹配物品堆
     */
    private ItemStack[] getIngredientStacks(Recipe<?> recipe) {
        if (recipe instanceof SmithingRecipe smithingRecipe) {
            SmithingMatch match = findSmithingMatch(smithingRecipe);
            if (match != null) {
                return new ItemStack[] {
                        match.template(),
                        match.base(),
                        match.addition()
                };
            }

            return new ItemStack[] {
                    findFirstRegisteredItem(smithingRecipe::isTemplateIngredient),
                    findFirstRegisteredItem(smithingRecipe::isBaseIngredient),
                    findFirstRegisteredItem(smithingRecipe::isAdditionIngredient)
            };
        }

        if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
            return new ItemStack[0];
        }

        List<Ingredient> ingredients = getIngredients(craftingRecipe);
        ItemStack[] stacks = new ItemStack[ingredients.size()];
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack[] matching = ingredients.get(i).getItems();
            stacks[i] = matching.length > 0 ? matching[0].copy() : ItemStack.EMPTY;
        }
        return stacks;
    }

    /**
     * 获取配方的展示产物。
     *
     * @param recipe 要展示的配方
     * @return 用于列表展示和搜索的产物
     */
    private ItemStack getRecipeResult(Recipe<?> recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return ItemStack.EMPTY;

        if (recipe instanceof SmithingRecipe smithingRecipe) {
            SmithingMatch match = findSmithingMatch(smithingRecipe);
            if (match != null) {
                SmithingRecipeInput input = new SmithingRecipeInput(match.template(), match.base(), match.addition());
                ItemStack assembled = smithingRecipe.assemble(input, mc.level.registryAccess());
                if (!assembled.isEmpty()) {
                    return assembled;
                }
            }
        }

        return recipe.getResultItem(mc.level.registryAccess());
    }

    /**
     * 从玩家背包中寻找锻造台配方的三槽输入。
     *
     * @param recipe 锻造台配方
     * @return 匹配到的输入；未匹配时返回 {@code null}
     */
    private SmithingMatch findSmithingMatch(SmithingRecipe recipe) {
        List<ItemStack> available = getInventorySnapshot();

        ItemStack template = takeFirstMatching(available, recipe::isTemplateIngredient);
        if (template.isEmpty()) return null;

        ItemStack base = takeFirstMatching(available, recipe::isBaseIngredient);
        if (base.isEmpty()) return null;

        ItemStack addition = takeFirstMatching(available, recipe::isAdditionIngredient);
        if (addition.isEmpty()) return null;

        return new SmithingMatch(template, base, addition);
    }

    /**
     * 获取玩家背包和当前打开容器的可变快照。
     *
     * <p>快照顺序固定为玩家背包优先、当前容器其次，用来保持“优先使用背包材料”的行为。</p>
     *
     * @return 可用材料的非空物品堆副本列表
     */
    private List<ItemStack> getInventorySnapshot() {
        List<ItemStack> available = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return available;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                // 使用副本参与匹配，避免客户端预览阶段直接修改玩家背包。
                available.add(stack.copy());
            }
        }

        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            for (var slot : mc.player.containerMenu.slots) {
                ItemStack stack = slot.getItem();
                if (slot.container != mc.player.getInventory() && !stack.isEmpty() && !slot.isFake() && slot.mayPickup(mc.player) && slot.mayPlace(stack)) {
                    available.add(stack.copy());
                }
            }
        }

        return available;
    }

    /**
     * 从可变背包快照中取出第一个满足条件的物品。
     *
     * @param available 可变背包快照
     * @param matcher 材料匹配规则
     * @return 匹配到的单个物品副本；未匹配时返回 {@link ItemStack#EMPTY}
     */
    private ItemStack takeFirstMatching(List<ItemStack> available, Predicate<ItemStack> matcher) {
        for (ItemStack stack : available) {
            if (!stack.isEmpty() && matcher.test(stack)) {
                // 命中一个材料槽后扣减 1 个副本数量，防止同一个物品被多个材料槽重复占用。
                ItemStack matched = stack.copyWithCount(1);
                stack.shrink(1);
                return matched;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 从注册表中找一个可以代表材料槽的展示物品。
     *
     * @param matcher 材料匹配规则
     * @return 第一个匹配的物品堆；没有匹配项时返回 {@link ItemStack#EMPTY}
     */
    private ItemStack findFirstRegisteredItem(Predicate<ItemStack> matcher) {
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (matcher.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 锻造台三槽输入的实际匹配结果。
     *
     * @param template 模板槽物品
     * @param base 基础槽物品
     * @param addition 追加材料槽物品
     */
    private record SmithingMatch(ItemStack template, ItemStack base, ItemStack addition) {
    }

    /**
     * 渲染合成助手面板。
     *
     * @param guiGraphics 图形绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param partialTick 局部帧时间
     */
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
        for (RecipeHolder<?> holder : sortedRecipes) {
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
            RecipeHolder<?> holder = sortedRecipes.get(i);
            Recipe<?> recipe = holder.value();
            ItemStack result = getRecipeResult(recipe);

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

    /**
     * 绘制“仅可合成”筛选框和搜索输入框。
     *
     * @param guiGraphics 图形绘制上下文
     */
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

    /**
     * 绘制分页、刷新和关闭按钮。
     *
     * @param guiGraphics 图形绘制上下文
     */
    private void drawNavigationButtons(GuiGraphics guiGraphics) {
        int totalPages = getTotalPages();
        if (totalPages > 1) {
            drawPanelButton(guiGraphics, panelX + 16, panelY + PANEL_HEIGHT - 34, 34, 20, "<", currentPage > 0);
            drawPanelButton(guiGraphics, panelX + PANEL_WIDTH - 50, panelY + PANEL_HEIGHT - 34, 34, 20, ">", currentPage + 1 < totalPages);
        }

        drawPanelButton(guiGraphics, panelX + PANEL_WIDTH / 2 - 12, panelY + PANEL_HEIGHT - 30, 24, 18, "R", true);
        drawPanelButton(guiGraphics, panelX + PANEL_WIDTH - 22, panelY - 5, 20, 20, "X", true);
    }

    /**
     * 绘制手写风格的面板按钮。
     *
     * @param guiGraphics 图形绘制上下文
     * @param x 按钮左上角 X 坐标
     * @param y 按钮左上角 Y 坐标
     * @param buttonWidth 按钮宽度
     * @param buttonHeight 按钮高度
     * @param label 按钮显示文本
     * @param active 按钮是否可用
     */
    private void drawPanelButton(GuiGraphics guiGraphics, int x, int y, int buttonWidth, int buttonHeight, String label, boolean active) {
        int borderColor = active ? 0xFF4F6CFF : 0xFF343434;
        int fillColor = active ? 0xFF2A2E38 : 0xFF1A1A1A;
        int textColor = active ? 0xFFFFFFFF : 0xFF777777;

        guiGraphics.fill(x, y, x + buttonWidth, y + buttonHeight, 0xFF080808);
        guiGraphics.fill(x + 1, y + 1, x + buttonWidth - 1, y + buttonHeight - 1, borderColor);
        guiGraphics.fill(x + 2, y + 2, x + buttonWidth - 2, y + buttonHeight - 2, fillColor);
        guiGraphics.drawString(font, label, x + (buttonWidth - font.width(label)) / 2, y + (buttonHeight - 8) / 2, textColor, false);
    }

    /**
     * 处理鼠标点击事件。
     *
     * <p>左键用于切换筛选、翻页、刷新、关闭和发起合成；右键点击搜索框时会清空搜索内容。</p>
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按钮编号
     * @return 事件已处理时返回 {@code true}
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int controlsY = panelY + CONTROLS_Y_OFFSET;
        int searchX = panelX + 76;
        int searchWidth = PANEL_WIDTH - 116;

        if (button == 1 && isInside(mouseX, mouseY, searchX, controlsY + 1, searchWidth, 18)) {
            searchFocused = true;
            if (!searchText.isEmpty()) {
                searchText = "";
                currentPage = 0;
                sortRecipes();
                refreshButtons();
            }
            return true;
        }

        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (isInside(mouseX, mouseY, panelX + 10, controlsY + 3, 64, 14)) {
            onlyCraftable = !onlyCraftable;
            currentPage = 0;
            sortRecipes();
            refreshButtons();
            return true;
        }

        if (isInside(mouseX, mouseY, searchX, controlsY + 1, searchWidth, 18)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        int totalPages = getTotalPages();
        if (totalPages > 1 && isInside(mouseX, mouseY, panelX + 16, panelY + PANEL_HEIGHT - 34, 34, 20) && turnPage(-1)) {
            return true;
        }
        if (totalPages > 1 && isInside(mouseX, mouseY, panelX + PANEL_WIDTH - 50, panelY + PANEL_HEIGHT - 34, 34, 20) && turnPage(1)) {
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
            RecipeHolder<?> holder = sortedRecipes.get(i);
            Recipe<?> recipe = holder.value();
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

    /**
     * 处理鼠标滚轮翻页。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param scrollX 横向滚动量
     * @param scrollY 纵向滚动量
     * @return 成功翻页时返回 {@code true}
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0.0D && turnPage(-1)) {
            return true;
        }
        if (scrollY < 0.0D && turnPage(1)) {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * 按方向翻页。
     *
     * @param direction 翻页方向，负数上一页，正数下一页
     * @return 页码发生变化时返回 {@code true}
     */
    private boolean turnPage(int direction) {
        int totalPages = getTotalPages();
        if (totalPages <= 1) {
            return false;
        }

        int nextPage = Math.max(0, Math.min(currentPage + Integer.signum(direction), totalPages - 1));
        if (nextPage == currentPage) {
            return false;
        }

        currentPage = nextPage;
        refreshButtons();
        return true;
    }

    /**
     * 获取当前筛选结果的总页数。
     *
     * @return 总页数
     */
    private int getTotalPages() {
        return (sortedRecipes.size() + RECIPE_PER_PAGE - 1) / RECIPE_PER_PAGE;
    }

    /**
     * 根据列表行号计算配方条目的 Y 坐标。
     *
     * @param rowIndex 当前页中的行号
     * @return 条目顶部 Y 坐标
     */
    private int getRecipeY(int rowIndex) {
        return panelY + LIST_Y_OFFSET + rowIndex * ROW_HEIGHT;
    }

    /**
     * 判断鼠标坐标是否位于指定矩形内。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param x 矩形左上角 X 坐标
     * @param y 矩形左上角 Y 坐标
     * @param buttonWidth 矩形宽度
     * @param buttonHeight 矩形高度
     * @return 位于矩形内时返回 {@code true}
     */
    private static boolean isInside(double mouseX, double mouseY, int x, int y, int buttonWidth, int buttonHeight) {
        return mouseX >= x && mouseX < x + buttonWidth && mouseY >= y && mouseY < y + buttonHeight;
    }

    /**
     * 判断玩家背包或当前打开容器中是否有足够数量的指定材料。
     *
     * @param needed 需要展示或检查的材料
     * @return 可用来源中同物品同组件的数量足够时返回 {@code true}
     */
    private boolean hasItemInInventory(ItemStack needed) {
        int needCount = needed.getCount();
        for (ItemStack stack : getInventorySnapshot()) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, needed)) {
                needCount -= stack.getCount();
                if (needCount <= 0) return true;
            }
        }
        return false;
    }

    /**
     * 合成助手界面不会暂停游戏。
     *
     * @return 始终返回 {@code false}
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 关闭合成助手，并同步关闭打开它时保留的容器菜单。
     */
    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.closeContainer();
        }

        super.onClose();
    }

    /**
     * 处理键盘按键事件。
     *
     * @param keyCode 按键码
     * @param scanCode 扫描码
     * @param modifiers 修饰键掩码
     * @return 事件已处理时返回 {@code true}
     */
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

    /**
     * 处理搜索框字符输入。
     *
     * @param codePoint 输入字符
     * @param modifiers 修饰键掩码
     * @return 事件已处理时返回 {@code true}
     */
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
