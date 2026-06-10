package com.adore.convenientcrafting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
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

    private static final int DEFAULT_PANEL_WIDTH = 250;
    private static final int DEFAULT_PANEL_HEIGHT = 246;
    private static final int SCREEN_MARGIN = 8;
    private static final int PANEL_TOP_EXTRA = 10;
    private static final int NAVIGATION_HEIGHT = 44;
    private static final int DEFAULT_RECIPE_PER_PAGE = 4;
    private static final int ROW_HEIGHT = 36;
    private static final int CONTROLS_Y_OFFSET = 18;
    private static final int LIST_Y_OFFSET = 48;
    private static final int RESULT_X_OFFSET = 10;
    private static final int NAME_X_OFFSET = 34;
    private static final int INGREDIENT_ICON_SIZE = 16;
    private static final int RESULT_NAME_INGREDIENT_GAP = 8;
    private static final int MIN_RESULT_NAME_WIDTH = 30;
    private static final int MIN_INGREDIENT_PANEL_WIDTH = INGREDIENT_ICON_SIZE * 2;
    private static final int INGREDIENT_BUTTON_GAP = 8;
    private static final int INGREDIENT_SCROLL_BASE_PIXELS_PER_SECOND = 18;
    private static final int INGREDIENT_SCROLL_SPEEDUP_PIXELS_PER_SECOND = 6;
    private static final int INGREDIENT_SCROLL_MAX_PIXELS_PER_SECOND = 72;
    private static final long INGREDIENT_ALTERNATIVE_INTERVAL_MS = 900L;
    private static final long RECIPE_VARIANT_INTERVAL_MS = 1800L;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int recipesPerPage = DEFAULT_RECIPE_PER_PAGE;
    private int craftButtonXOffset;
    private int currentPage = 0;
    private boolean onlyCraftable = false;
    private boolean searchFocused = false;
    private String searchText = "";
    private List<RecipeGroup> allRecipeGroups = new ArrayList<>();
    private List<RecipeGroup> sortedRecipeGroups = new ArrayList<>();

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
        updateLayout();

        loadRecipes();
        refreshInventoryCache();
        sortRecipes();
        refreshButtons();
    }

    private void updateLayout() {
        int availableWidth = Math.max(160, width - SCREEN_MARGIN * 2);
        panelWidth = Math.min(DEFAULT_PANEL_WIDTH, availableWidth);

        int availablePanelHeight = Math.max(96, height - SCREEN_MARGIN * 2 - PANEL_TOP_EXTRA);
        panelHeight = Math.min(DEFAULT_PANEL_HEIGHT, availablePanelHeight);

        recipesPerPage = Math.max(1, (panelHeight - LIST_Y_OFFSET - NAVIGATION_HEIGHT) / ROW_HEIGHT);
        recipesPerPage = Math.min(recipesPerPage, DEFAULT_RECIPE_PER_PAGE);

        craftButtonXOffset = panelWidth - 42;

        int visualHeight = panelHeight + PANEL_TOP_EXTRA;
        int visualTop = Math.max(0, Math.min((height - visualHeight) / 2, height - visualHeight));
        panelX = Math.max(0, (width - panelWidth) / 2);
        panelY = visualTop + PANEL_TOP_EXTRA;
    }

    /**
     * 加载当前世界中的所有工作台合成配方。
     *
     * <p>该方法从客户端世界的配方管理器读取内置支持的 {@link RecipeType#CRAFTING}、
     * {@link RecipeType#SMITHING} 配方，以及配置文件额外启用的简单配方类型。
     * 通过数据包或 datapack 注册的模组配方也会出现在这里；使用 {@code seen} 去重，
     * 避免相同配方 ID 被重复加入。</p>
     */
    private void loadRecipes() {
        allRecipeGroups.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Set<String> seenDuplicateRecipes = new HashSet<>();
        Map<String, List<RecipeEntry>> groupedRecipes = new LinkedHashMap<>();
        var recipeManager = mc.level.getRecipeManager();

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (isRecipeVisible(recipe)) {
                String duplicateKey = RecipeSupport.buildDuplicateKey(recipe, mc.level.registryAccess());
                if (!seenDuplicateRecipes.add(duplicateKey)) continue;

                ItemStack result = getRecipeResult(recipe);
                groupedRecipes.computeIfAbsent(buildResultGroupKey(result), ignored -> new ArrayList<>())
                        .add(RecipeEntry.fromRecipe(holder));
            }
        }

        addBrewingRecipes(groupedRecipes, seenDuplicateRecipes);

        groupedRecipes.forEach((key, recipes) -> allRecipeGroups.add(new RecipeGroup(key, recipes)));
    }

    private void addBrewingRecipes(Map<String, List<RecipeEntry>> groupedRecipes, Set<String> seenDuplicateRecipes) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null
                || !RecipeSupport.isBuiltInRecipeTypeEnabled(mc.player, BrewingRecipeSupport.RECIPE_TYPE_ID)
                || !ClientRecipeUnlocks.isUnlocked(BrewingRecipeSupport.RECIPE_TYPE_ID)) {
            return;
        }

        PotionBrewing potionBrewing = mc.level.potionBrewing();
        List<Item> containers = List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);
        List<ItemStack> ingredients = BuiltInRegistries.ITEM.stream()
                .map(ItemStack::new)
                .filter(potionBrewing::isIngredient)
                .toList();
        List<Holder.Reference<Potion>> potions = mc.level.registryAccess()
                .registryOrThrow(Registries.POTION)
                .holders()
                .filter(potionBrewing::isBrewablePotion)
                .toList();

        for (Item container : containers) {
            for (Holder.Reference<Potion> potion : potions) {
                ItemStack input = PotionContents.createItemStack(container, potion);
                for (ItemStack ingredient : ingredients) {
                    if (!potionBrewing.hasMix(input, ingredient)) {
                        continue;
                    }

                    ItemStack result = potionBrewing.mix(ingredient, input);
                    ResourceLocation id = BrewingRecipeSupport.buildRecipeId(input, ingredient);
                    if (id == null || result.isEmpty() || ItemStack.isSameItemSameComponents(input, result)) {
                        continue;
                    }

                    String duplicateKey = "brewing:" + id;
                    if (!seenDuplicateRecipes.add(duplicateKey)) {
                        continue;
                    }

                    BrewingRecipeEntry entry = new BrewingRecipeEntry(id, input.copy(), ingredient.copyWithCount(1), result.copy());
                    groupedRecipes.computeIfAbsent(buildResultGroupKey(result), ignored -> new ArrayList<>())
                            .add(RecipeEntry.fromBrewing(entry));
                }
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

    private static String buildResultGroupKey(ItemStack stack) {
        return buildItemKey(stack) + "x" + stack.getCount() + stack.getComponentsPatch();
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
        for (RecipeGroup group : allRecipeGroups) {
            for (RecipeEntry entry : group.recipes()) {
                craftableCache.put(entry.id(), canCraftEntry(entry));
            }
        }

        sortedRecipeGroups = allRecipeGroups.stream()
                .filter(group -> matchesFilters(group, craftableCache))
                .sorted((a, b) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level == null) return 0;

                    ItemStack resultA = getEntryResult(getFirstRecipe(a));
                    ItemStack resultB = getEntryResult(getFirstRecipe(b));

                    boolean canCraftA = canCraftAnyRecipe(a, craftableCache);
                    boolean canCraftB = canCraftAnyRecipe(b, craftableCache);

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
     * @param group 配方分组
     * @param craftableCache 可合成状态缓存
     * @return 通过筛选时返回 {@code true}
     */
    private boolean matchesFilters(RecipeGroup group, Map<ResourceLocation, Boolean> craftableCache) {
        if (onlyCraftable && !canCraftAnyRecipe(group, craftableCache)) {
            return false;
        }

        String query = searchText.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        ItemStack result = getEntryResult(getFirstRecipe(group));
        String itemName = result.getHoverName().getString().toLowerCase(Locale.ROOT);
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(result.getItem());
        String itemId = itemKey != null ? itemKey.toString().toLowerCase(Locale.ROOT) : "";
        return itemName.contains(query) || itemId.contains(query);
    }

    private boolean canCraftAnyRecipe(RecipeGroup group) {
        for (RecipeEntry entry : group.recipes()) {
            if (canCraftEntry(entry)) {
                return true;
            }
        }
        return false;
    }

    private boolean canCraftAnyRecipe(RecipeGroup group, Map<ResourceLocation, Boolean> craftableCache) {
        for (RecipeEntry entry : group.recipes()) {
            if (craftableCache.getOrDefault(entry.id(), false)) {
                return true;
            }
        }
        return false;
    }

    private RecipeEntry getFirstRecipe(RecipeGroup group) {
        return group.recipes().isEmpty() ? null : group.recipes().get(0);
    }

    private RecipeEntry getActiveRecipe(RecipeGroup group) {
        if (group.recipes().isEmpty()) {
            return null;
        }
        int activeIndex = (int) ((System.currentTimeMillis() / RECIPE_VARIANT_INTERVAL_MS) % group.recipes().size());
        return group.recipes().get(activeIndex);
    }

    /**
     * 刷新界面按钮。
     *
     * <p>当前面板按钮由渲染方法手绘，因此这里主要清理原生小部件列表。</p>
     */
    private void refreshButtons() {
        clearWidgets();
    }

    private boolean canCraftEntry(RecipeEntry entry) {
        if (entry == null) return false;
        if (entry.isBrewing()) {
            return findBrewingMatch(entry.brewing()) != null;
        }
        return canCraftRecipe(entry.recipe());
    }

    private boolean canClickCraftButton(RecipeEntry entry) {
        return canCraftEntry(entry) || canAttemptNestedCrafting(entry);
    }

    private boolean canAttemptNestedCrafting(RecipeEntry entry) {
        return hasAltDown() && entry != null && !entry.isBrewing() && entry.recipe() != null && !getEntryResult(entry).isEmpty();
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
            return hasIngredients(RecipeSupport.getNonEmptyIngredients(craftingRecipe));
        }

        if (recipe instanceof SmithingRecipe smithingRecipe) {
            SmithingMatch match = findSmithingMatch(smithingRecipe);
            if (match == null || mc.level == null) return false;

            SmithingRecipeInput input = new SmithingRecipeInput(match.template(), match.base(), match.addition());
            return smithingRecipe.matches(input, mc.level) && !smithingRecipe.assemble(input, mc.level.registryAccess()).isEmpty();
        }

        return isConfiguredSimpleRecipeCraftable(recipe);
    }

    private ItemStack getEntryResult(RecipeEntry entry) {
        if (entry == null) return ItemStack.EMPTY;
        if (entry.isBrewing()) {
            ItemStack result = entry.brewing().result().copy();
            BrewingMatch match = findBrewingMatch(entry.brewing());
            if (match != null) {
                result.setCount(match.inputs().size());
            }
            return result;
        }
        return getRecipeResult(entry.recipe());
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
     * 获取用于界面展示的材料图标。
     *
     * @param entry 要展示的配方条目
     * @return 每个材料槽的第一个可匹配物品堆
     */
    private DisplayIngredient[] getIngredientStacks(RecipeEntry entry) {
        if (entry.isBrewing()) {
            BrewingRecipeEntry brewing = entry.brewing();
            BrewingMatch match = findBrewingMatch(brewing);
            if (match != null) {
                DisplayIngredient[] ingredients = new DisplayIngredient[match.inputs().size() + 1];
                for (int i = 0; i < match.inputs().size(); i++) {
                    ingredients[i] = new DisplayIngredient(match.inputs().get(i), true);
                }
                ingredients[ingredients.length - 1] = new DisplayIngredient(match.ingredient(), true);
                return ingredients;
            }

            List<ItemStack> available = getInventorySnapshot();
            return new DisplayIngredient[] {
                    getDisplayIngredient(available, stack -> ItemStack.isSameItemSameComponents(stack, brewing.input()), brewing.input()),
                    getDisplayIngredient(available, stack -> stack.is(brewing.ingredient().getItem()), brewing.ingredient())
            };
        }

        return getIngredientStacks(entry.recipe());
    }

    private DisplayIngredient[] getIngredientStacks(Recipe<?> recipe) {
        if (recipe instanceof SmithingRecipe smithingRecipe) {
            SmithingMatch match = findSmithingMatch(smithingRecipe);
            if (match != null) {
                return new DisplayIngredient[] {
                        new DisplayIngredient(match.template(), true),
                        new DisplayIngredient(match.base(), true),
                        new DisplayIngredient(match.addition(), true)
                };
            }

            List<ItemStack> available = getInventorySnapshot();
            return new DisplayIngredient[] {
                    getDisplayIngredient(available, smithingRecipe::isTemplateIngredient),
                    getDisplayIngredient(available, smithingRecipe::isBaseIngredient),
                    getDisplayIngredient(available, smithingRecipe::isAdditionIngredient)
            };
        }

        if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
            return new DisplayIngredient[0];
        }

        List<Ingredient> ingredients = RecipeSupport.getNonEmptyIngredients(craftingRecipe);
        List<ItemStack> available = getInventorySnapshot();
        DisplayIngredient[] stacks = new DisplayIngredient[ingredients.size()];
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            stacks[i] = getDisplayIngredient(available, ingredient::test, getCyclingIngredientItem(ingredient));
        }
        return stacks;
    }

    private DisplayIngredient getDisplayIngredient(List<ItemStack> available, Predicate<ItemStack> matcher) {
        return getDisplayIngredient(available, matcher, findFirstRegisteredItem(matcher));
    }

    private DisplayIngredient getDisplayIngredient(List<ItemStack> available, Predicate<ItemStack> matcher, ItemStack fallback) {
        ItemStack matched = takeFirstMatching(available, matcher);
        if (!matched.isEmpty()) {
            return new DisplayIngredient(matched, true);
        }
        return new DisplayIngredient(fallback, false);
    }

    private ItemStack getCyclingIngredientItem(Ingredient ingredient) {
        ItemStack[] matching = ingredient.getItems();
        if (matching.length == 0) {
            return ItemStack.EMPTY;
        }
        int index = (int) ((System.currentTimeMillis() / INGREDIENT_ALTERNATIVE_INTERVAL_MS) % matching.length);
        return matching[index].copy();
    }

    private int getMaxIngredientCount(RecipeGroup group) {
        int maxCount = 0;
        for (RecipeEntry entry : group.recipes()) {
            maxCount = Math.max(maxCount, getIngredientCount(entry));
        }
        return maxCount;
    }

    private int getIngredientCount(RecipeEntry entry) {
        if (entry.isBrewing()) {
            return 4;
        }
        return getIngredientCount(entry.recipe());
    }

    private int getIngredientCount(Recipe<?> recipe) {
        if (recipe instanceof SmithingRecipe) {
            return 3;
        }
        if (recipe instanceof CraftingRecipe craftingRecipe) {
            return RecipeSupport.getNonEmptyIngredients(craftingRecipe).size();
        }
        return RecipeSupport.getNonEmptyIngredients(recipe).size();
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
     * 判断配方是否应该显示在便捷合成列表中。
     *
     * @param recipe 要检查的配方
     * @return 内置支持或配置启用的简单配方返回 {@code true}
     */
    private boolean isRecipeVisible(Recipe<?> recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !RecipeSupport.isUnlockedFor(mc.player, recipe)) return false;

        if (RecipeSupport.isBuiltInSupported(recipe)) {
            return !getRecipeResult(recipe).isEmpty();
        }

        return RecipeSupport.isConfiguredSimpleRecipe(recipe, mc.level.registryAccess());
    }

    /**
     * 判断配置启用的简单配方当前是否可合成。
     *
     * @param recipe 要检查的配方
     * @return 材料足够且产物有效时返回 {@code true}
     */
    private boolean isConfiguredSimpleRecipeCraftable(Recipe<?> recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !RecipeSupport.isUnlockedFor(mc.player, recipe)
                || !RecipeSupport.isConfiguredSimpleRecipe(recipe, mc.level.registryAccess())) {
            return false;
        }

        return hasIngredients(RecipeSupport.getNonEmptyIngredients(recipe));
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
    private BrewingMatch findBrewingMatch(BrewingRecipeEntry recipe) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        List<ItemStack> available = getInventorySnapshot();
        ItemStack ingredient = takeFirstMatching(available, stack -> stack.is(recipe.ingredient().getItem()));
        if (ingredient.isEmpty()) return null;

        PotionBrewing potionBrewing = mc.level.potionBrewing();
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ItemStack input = takeFirstMatching(available, stack -> ItemStack.isSameItemSameComponents(stack, recipe.input()));
            if (input.isEmpty()) {
                break;
            }

            if (!potionBrewing.hasMix(input, ingredient)) {
                continue;
            }

            ItemStack result = potionBrewing.mix(ingredient, input);
            if (!result.isEmpty() && ItemStack.isSameItemSameComponents(result, recipe.result())) {
                inputs.add(input);
            }
        }

        return inputs.isEmpty() ? null : new BrewingMatch(inputs, ingredient);
    }

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

    private record BrewingMatch(List<ItemStack> inputs, ItemStack ingredient) {
    }

    private record BrewingRecipeEntry(ResourceLocation id, ItemStack input, ItemStack ingredient, ItemStack result) {
    }

    private record RecipeEntry(ResourceLocation id, Recipe<?> recipe, BrewingRecipeEntry brewing) {
        private static RecipeEntry fromRecipe(RecipeHolder<?> holder) {
            return new RecipeEntry(holder.id(), holder.value(), null);
        }

        private static RecipeEntry fromBrewing(BrewingRecipeEntry brewing) {
            return new RecipeEntry(brewing.id(), null, brewing);
        }

        private boolean isBrewing() {
            return brewing != null;
        }
    }

    /**
     * 材料栏中单个槽位的展示物品，以及该槽位是否已经从可用材料快照中成功匹配。
     *
     * @param stack 展示在材料栏里的物品
     * @param available 当前背包或容器材料是否满足该材料槽
     */
    private record DisplayIngredient(ItemStack stack, boolean available) {
    }

    /**
     * 同一产物的多个不同配方。
     *
     * @param resultKey 产物分组键
     * @param recipes 该产物对应的可展示配方列表
     */
    private record RecipeGroup(String resultKey, List<RecipeEntry> recipes) {
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
        guiGraphics.fill(panelX, panelY - PANEL_TOP_EXTRA, panelX + panelWidth, panelY + panelHeight, 0xC0101010);
        guiGraphics.fill(panelX, panelY - PANEL_TOP_EXTRA, panelX + panelWidth, panelY - PANEL_TOP_EXTRA + 2, 0xFF4444FF);
        guiGraphics.fill(panelX, panelY + panelHeight - 2, panelX + panelWidth, panelY + panelHeight, 0xFF4444FF);

        // 标题
        guiGraphics.drawString(font, title, panelX + 8, panelY + 2, 0xFFFFFF, false);

        // 可合成数量统计
        int craftableCount = 0;
        for (RecipeGroup group : sortedRecipeGroups) {
            if (canCraftAnyRecipe(group)) craftableCount++;
        }
        String summary = "可合成: " + craftableCount + "/" + sortedRecipeGroups.size();
        guiGraphics.drawString(font, summary, panelX + panelWidth - font.width(summary) - 34, panelY + 2, 0x888888, false);

        drawFilterControls(guiGraphics);

        // 页码
        int totalPages = getTotalPages();
        if (totalPages > 1) {
            String pageStr = (currentPage + 1) + "/" + totalPages;
            guiGraphics.drawString(font, pageStr, panelX + panelWidth / 2 - font.width(pageStr) / 2, panelY + panelHeight - NAVIGATION_HEIGHT, 0xAAAAAA, false);
        }

        // 每个配方的条目
        int startIndex = currentPage * recipesPerPage;
        int endIndex = Math.min(startIndex + recipesPerPage, sortedRecipeGroups.size());
        ItemStack hoveredIngredient = ItemStack.EMPTY;
        ItemStack hoveredResult = ItemStack.EMPTY;
        int ingredientXOffset = getPageIngredientXOffset(startIndex, endIndex);
        int ingredientWidth = getIngredientPanelWidth(ingredientXOffset);

        if (sortedRecipeGroups.isEmpty()) {
            String emptyText = "没有匹配的配方";
            guiGraphics.drawString(font, emptyText, panelX + panelWidth / 2 - font.width(emptyText) / 2, panelY + LIST_Y_OFFSET + 52, 0xFFAAAAAA, false);
        }

        for (int i = startIndex; i < endIndex; i++) {
            RecipeGroup group = sortedRecipeGroups.get(i);
            RecipeEntry entry = getActiveRecipe(group);
            ItemStack result = getEntryResult(entry);

            int recipeY = getRecipeY(i - startIndex);
            boolean canCraft = canCraftEntry(entry);
            boolean canAttemptNested = canAttemptNestedCrafting(entry);
            boolean canClickCraft = canCraft || canAttemptNested;
            String craftButtonLabel = canAttemptNested && !canCraft ? "→" : canClickCraft ? ">" : "X";

            // 产物图标
            if (!result.isEmpty() && isInside(mouseX, mouseY, panelX + RESULT_X_OFFSET, recipeY + 4, INGREDIENT_ICON_SIZE, INGREDIENT_ICON_SIZE)) {
                hoveredResult = result;
            }
            guiGraphics.renderItem(result, panelX + RESULT_X_OFFSET, recipeY + 4);
            guiGraphics.renderItemDecorations(font, result, panelX + RESULT_X_OFFSET, recipeY + 4);

            // 产物名称
            String resultName = result.getHoverName().getString();
            int resultNameWidth = ingredientXOffset - NAME_X_OFFSET - 10;
            if (font.width(resultName) > resultNameWidth) {
                resultName = font.ellipsize(FormattedText.of(resultName), resultNameWidth).getString();
            }
            guiGraphics.drawString(font, resultName, panelX + NAME_X_OFFSET, recipeY + 4, canCraft ? 0xFFFFFF : 0x888888, false);
            guiGraphics.drawString(font, "x" + result.getCount(), panelX + NAME_X_OFFSET, recipeY + 16, 0xAAAAAA, false);

            // 材料图标
            DisplayIngredient[] ingredients = getIngredientStacks(entry);
            int ingX = panelX + ingredientXOffset;
            int ingredientOffset = getIngredientScrollOffset(ingredients.length, getMaxIngredientCount(group), mouseX, mouseY, ingX, recipeY, ingredientWidth);
            guiGraphics.enableScissor(ingX, recipeY + 6, ingX + ingredientWidth, recipeY + 6 + INGREDIENT_ICON_SIZE);
            for (int j = 0; j < ingredients.length; j++) {
                DisplayIngredient ingredient = ingredients[j];
                int itemX = ingX + j * INGREDIENT_ICON_SIZE - ingredientOffset;
                if (!ingredient.stack().isEmpty() && itemX > ingX - INGREDIENT_ICON_SIZE && itemX < ingX + ingredientWidth) {
                    int visibleLeft = Math.max(itemX, ingX);
                    int visibleRight = Math.min(itemX + INGREDIENT_ICON_SIZE, ingX + ingredientWidth);
                    if (visibleLeft < visibleRight && isInside(mouseX, mouseY, visibleLeft, recipeY + 6, visibleRight - visibleLeft, INGREDIENT_ICON_SIZE)) {
                        hoveredIngredient = ingredient.stack();
                    }

                    guiGraphics.renderItem(ingredient.stack(), itemX, recipeY + 6);
                    // 缺失的材料上画红色半透明遮罩
                    if (!ingredient.available()) {
                        guiGraphics.fill(itemX, recipeY + 6, itemX + INGREDIENT_ICON_SIZE, recipeY + 6 + INGREDIENT_ICON_SIZE, 0x33FF0000);
                    }
                }
            }
            guiGraphics.disableScissor();

            drawPanelButton(guiGraphics, panelX + craftButtonXOffset, recipeY + 6, 22, 20, craftButtonLabel, canClickCraft);
        }

        drawNavigationButtons(guiGraphics);
        renderRecipeTooltip(guiGraphics, hoveredIngredient, hoveredResult, mouseX, mouseY);
    }

    private void renderRecipeTooltip(GuiGraphics guiGraphics, ItemStack ingredient, ItemStack result, int mouseX, int mouseY) {
        if (!ingredient.isEmpty()) {
            guiGraphics.renderTooltip(font, ingredient, mouseX, mouseY);
        } else if (!result.isEmpty()) {
            guiGraphics.renderTooltip(font, result, mouseX, mouseY);
        }
    }

    private int getPageIngredientXOffset(int startIndex, int endIndex) {
        int ingredientXOffset = getPreferredIngredientXOffset("");
        for (int i = startIndex; i < endIndex; i++) {
            RecipeEntry entry = getActiveRecipe(sortedRecipeGroups.get(i));
            ItemStack result = getEntryResult(entry);
            ingredientXOffset = Math.max(ingredientXOffset, getPreferredIngredientXOffset(result.getHoverName().getString()));
        }
        return ingredientXOffset;
    }

    private int getPreferredIngredientXOffset(String resultName) {
        int preferredOffset = NAME_X_OFFSET + font.width(resultName) + RESULT_NAME_INGREDIENT_GAP;
        int minOffset = NAME_X_OFFSET + MIN_RESULT_NAME_WIDTH + RESULT_NAME_INGREDIENT_GAP;
        int maxOffset = craftButtonXOffset - INGREDIENT_BUTTON_GAP - MIN_INGREDIENT_PANEL_WIDTH;
        return Math.max(minOffset, Math.min(preferredOffset, maxOffset));
    }

    private int getIngredientPanelWidth(int ingredientXOffset) {
        return Math.max(INGREDIENT_ICON_SIZE, craftButtonXOffset - ingredientXOffset - INGREDIENT_BUTTON_GAP);
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
        int searchWidth = Math.max(48, panelWidth - 116);
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
            drawPanelButton(guiGraphics, panelX + 16, panelY + panelHeight - 34, 34, 20, "<", currentPage > 0);
            drawPanelButton(guiGraphics, panelX + panelWidth - 50, panelY + panelHeight - 34, 34, 20, ">", currentPage + 1 < totalPages);
        }

        drawPanelButton(guiGraphics, panelX + panelWidth / 2 - 12, panelY + panelHeight - 30, 24, 18, "R", true);
        drawPanelButton(guiGraphics, panelX + panelWidth - 22, panelY - 5, 20, 20, "X", true);
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
        int searchWidth = Math.max(48, panelWidth - 116);

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
        if (totalPages > 1 && isInside(mouseX, mouseY, panelX + 16, panelY + panelHeight - 34, 34, 20) && turnPage(-1)) {
            return true;
        }
        if (totalPages > 1 && isInside(mouseX, mouseY, panelX + panelWidth - 50, panelY + panelHeight - 34, 34, 20) && turnPage(1)) {
            return true;
        }
        if (isInside(mouseX, mouseY, panelX + panelWidth / 2 - 12, panelY + panelHeight - 30, 24, 18)) {
            refreshInventoryCache();
            sortRecipes();
            currentPage = 0;
            refreshButtons();
            return true;
        }
        if (isInside(mouseX, mouseY, panelX + panelWidth - 22, panelY - 5, 20, 20)) {
            onClose();
            return true;
        }

        int startIndex = currentPage * recipesPerPage;
        int endIndex = Math.min(startIndex + recipesPerPage, sortedRecipeGroups.size());
        for (int i = startIndex; i < endIndex; i++) {
            RecipeGroup group = sortedRecipeGroups.get(i);
            RecipeEntry entry = getActiveRecipe(group);
            int recipeY = getRecipeY(i - startIndex);

            if (isInside(mouseX, mouseY, panelX + craftButtonXOffset, recipeY + 6, 22, 20) && canClickCraftButton(entry)) {
                playButtonClickSound();
                int craftCount = hasShiftDown() ? CraftRecipePacket.MAX_BATCH_CRAFTS : 1;
                PacketDistributor.sendToServer(new CraftRecipePacket(entry.id(), craftCount, hasAltDown()));
                refreshInventoryCache();
                sortRecipes();
                refreshButtons();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playButtonClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
        return (sortedRecipeGroups.size() + recipesPerPage - 1) / recipesPerPage;
    }

    /**
     * 计算材料栏当前的平滑滚动像素偏移。
     *
     * <p>材料数超过可见上限并且鼠标悬停在材料栏时，完整材料列表会在裁剪窗口内左右往返滑动。
     * 溢出的材料越多，滚动速度越快，让复杂配方更快露出完整材料。</p>
     *
     * @param currentIngredientCount 当前配方的材料槽数量
     * @param maxIngredientCount 同一产物分组中最多的材料槽数量
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param ingX 材料栏左侧 X 坐标
     * @param recipeY 当前配方行顶部 Y 坐标
     * @param ingredientWidth 材料栏可见宽度
     * @return 当前材料列表向左移动的像素数
     */
    private int getIngredientScrollOffset(int currentIngredientCount, int maxIngredientCount, int mouseX, int mouseY, int ingX, int recipeY, int ingredientWidth) {
        int visibleIngredientSlots = Math.max(1, ingredientWidth / INGREDIENT_ICON_SIZE);
        int maxOffset = Math.max(0, currentIngredientCount - visibleIngredientSlots) * INGREDIENT_ICON_SIZE;
        if (maxOffset <= 0 || !isInside(mouseX, mouseY, ingX, recipeY + 4, ingredientWidth, 20)) {
            return 0;
        }

        int speed = Math.min(
                INGREDIENT_SCROLL_MAX_PIXELS_PER_SECOND,
                INGREDIENT_SCROLL_BASE_PIXELS_PER_SECOND + Math.max(0, maxIngredientCount - visibleIngredientSlots) * INGREDIENT_SCROLL_SPEEDUP_PIXELS_PER_SECOND
        );
        long cycleDurationMs = Math.max(1L, Math.round(maxOffset * 2000.0D / speed));
        double progress = (System.currentTimeMillis() % cycleDurationMs) / (double) cycleDurationMs;
        double triangle = progress <= 0.5D ? progress * 2.0D : (1.0D - progress) * 2.0D;
        return (int) Math.round(maxOffset * triangle);
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
