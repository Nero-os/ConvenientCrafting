package com.adore.convenientcrafting.client.screen;

import com.adore.convenientcrafting.network.CraftRecipePacket;
import com.adore.convenientcrafting.item.CategorizedBagItem;
import com.adore.convenientcrafting.recipe.BrewingRecipeSupport;
import com.adore.convenientcrafting.recipe.RecipeSupport;
import com.adore.convenientcrafting.recipe.unlock.ClientRecipeUnlocks;

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
import net.minecraft.tags.TagKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.function.Predicate;

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
    private static final int RECIPE_LOAD_BATCH_SIZE = 100;
    private static final int BACKGROUND_RECIPE_LOAD_BATCH_SIZE = 100;
    private static final int CRAFTABILITY_REFRESH_BATCH_SIZE = 12;
    private static final int SORT_CRAFTABILITY_WARMUP_RECIPES_PER_GROUP = 3;
    private static final int POST_CRAFT_REFRESH_TIMEOUT_TICKS = 10;
    private static final int KEY_ENTER = 257;
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_LEFT = 263;
    private static final int KEY_HOME = 268;
    private static final int KEY_END = 269;
    private static Object cachedRecipeManager;
    private static int cachedRecipeCount = -1;
    private static int cachedUnlockRevision = -1;
    private static List<RecipeGroup> cachedRecipeGroups = List.of();
    private static CraftHelperScreen backgroundIndexBuilder;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int recipesPerPage = DEFAULT_RECIPE_PER_PAGE;
    private int craftButtonXOffset;
    private int currentPage = 0;
    private boolean onlyCraftable = false;
    private boolean searchFocused = false;
    private boolean searchAllSelected = false;
    private String searchText = "";
    private int searchCursor = 0;
    private int searchViewStart = 0;
    private ItemStack hoveredRecipeStack = ItemStack.EMPTY;
    private final Deque<String> searchHistory = new ArrayDeque<>();
    private List<RecipeGroup> allRecipeGroups = new ArrayList<>();
    private List<RecipeGroup> viewRecipeGroups = new ArrayList<>();
    private List<RecipeGroup> sortedRecipeGroups = new ArrayList<>();
    private int filteredRecipeCount;
    private int filteredCraftableGroupCount;
    private final List<ItemStack> cachedAvailableMaterials = new ArrayList<>();
    private final Map<ResourceLocation, Boolean> cachedCraftableRecipes = new HashMap<>();
    private final List<RecipeHolder<?>> pendingRecipeLoad = new ArrayList<>();
    private final Set<String> pendingDuplicateRecipes = new HashSet<>();
    private final Map<String, List<RecipeEntry>> pendingGroupedRecipes = new LinkedHashMap<>();
    private final List<RecipeEntry> pendingCraftabilityRefresh = new ArrayList<>();
    private final boolean backgroundIndexBuild;
    private Object loadingRecipeManager;
    private int loadingRecipeCount = -1;
    private int loadingUnlockRevision = -1;
    private int loadedRecipeCount;
    private int totalRecipeLoadCount;
    private boolean recipesLoading;
    private boolean waitingForBackgroundIndex;
    private boolean creativeTabsBuilt;
    private int pendingPostCraftRefreshTicks;
    private int pendingPostCraftInventoryFingerprint;

    /**
     * 玩家背包快照缓存，用于排序评分。
     *
     * <p>键为物品注册名，值为当前背包中该物品的总数量。</p>
     */
    private Map<String, Integer> cachedInventoryCounts = new HashMap<>();

    /**
     * 创建合成助手界面。
     */
    public CraftHelperScreen() {
        this(false);
    }

    private CraftHelperScreen(boolean backgroundIndexBuild) {
        super(Component.translatable("gui.convenientcrafting.craft_helper"));
        this.backgroundIndexBuild = backgroundIndexBuild;
    }

    public static void preloadRecipeIndex() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || isRecipeIndexCacheValid(mc)) {
            return;
        }
        if (backgroundIndexBuilder != null && backgroundIndexBuilder.isLoadingForCurrentRecipeIndex(mc)) {
            return;
        }

        backgroundIndexBuilder = new CraftHelperScreen(true);
        backgroundIndexBuilder.startRecipeLoading();
    }

    public static void tickRecipeIndexPreload() {
        if (backgroundIndexBuilder == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            backgroundIndexBuilder = null;
            return;
        }

        backgroundIndexBuilder.continueRecipeLoading();
        if (!backgroundIndexBuilder.recipesLoading) {
            backgroundIndexBuilder = null;
        }
    }

    public static void stopBackgroundRecipeWork() {
        backgroundIndexBuilder = null;
    }

    public static void clearRecipeIndexCache() {
        stopBackgroundRecipeWork();
        cachedRecipeManager = null;
        cachedRecipeCount = -1;
        cachedUnlockRevision = -1;
        cachedRecipeGroups = List.of();
    }

    /**
     * 初始化界面位置、配方列表、背包缓存和排序结果。
     */
    @Override
    protected void init() {
        updateLayout();

        refreshInventoryCache();
        startRecipeLoading();
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
        allRecipeGroups = new ArrayList<>();

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

    private void startRecipeLoading() {
        allRecipeGroups = new ArrayList<>();
        viewRecipeGroups = new ArrayList<>();
        sortedRecipeGroups = new ArrayList<>();
        cachedCraftableRecipes.clear();
        pendingRecipeLoad.clear();
        pendingDuplicateRecipes.clear();
        pendingGroupedRecipes.clear();
        pendingCraftabilityRefresh.clear();
        loadedRecipeCount = 0;
        totalRecipeLoadCount = 0;
        recipesLoading = false;
        waitingForBackgroundIndex = false;
        creativeTabsBuilt = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (tryUseCachedRecipeIndex(mc)) {
            return;
        }

        if (!backgroundIndexBuild && backgroundIndexBuilder != null && backgroundIndexBuilder.isLoadingForCurrentRecipeIndex(mc)) {
            followBackgroundIndexBuilder();
            return;
        }

        pendingRecipeLoad.addAll(mc.level.getRecipeManager().getRecipes());
        totalRecipeLoadCount = pendingRecipeLoad.size();
        loadingRecipeManager = mc.level.getRecipeManager();
        loadingRecipeCount = totalRecipeLoadCount;
        loadingUnlockRevision = ClientRecipeUnlocks.getRevision();
        recipesLoading = true;
    }

    private boolean tryUseCachedRecipeIndex(Minecraft mc) {
        if (!isRecipeIndexCacheValid(mc)) {
            return false;
        }

        allRecipeGroups = new ArrayList<>(cachedRecipeGroups);
        recipesLoading = false;
        loadedRecipeCount = totalRecipeLoadCount = cachedRecipeCount;
        initializeRecipeViewQuickly();
        startCraftabilityRefresh();
        refreshButtons();
        return true;
    }

    private static boolean isRecipeIndexCacheValid(Minecraft mc) {
        var recipeManager = mc.level.getRecipeManager();
        return cachedRecipeManager == recipeManager
                && cachedRecipeCount == recipeManager.getRecipes().size()
                && cachedUnlockRevision == ClientRecipeUnlocks.getRevision();
    }

    @Override
    public void tick() {
        super.tick();
        if (!backgroundIndexBuild && waitingForBackgroundIndex) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && backgroundIndexBuilder != null && backgroundIndexBuilder.isLoadingForCurrentRecipeIndex(mc)) {
                tickRecipeIndexPreload();
                if (tryUseCachedRecipeIndex(mc)) {
                    waitingForBackgroundIndex = false;
                    return;
                }
                if (backgroundIndexBuilder != null && backgroundIndexBuilder.isLoadingForCurrentRecipeIndex(mc)) {
                    followBackgroundIndexBuilder();
                    return;
                }
            }

            waitingForBackgroundIndex = false;
            if (mc.level != null) {
                startRecipeLoading();
                return;
            }
        }
        continueRecipeLoading();
        continueCraftabilityRefresh();
        refreshAfterServerInventorySync();
    }

    private void schedulePostCraftRefresh() {
        pendingPostCraftRefreshTicks = POST_CRAFT_REFRESH_TIMEOUT_TICKS;
        pendingPostCraftInventoryFingerprint = computeInventoryFingerprint();
    }

    private void refreshAfterServerInventorySync() {
        if (pendingPostCraftRefreshTicks <= 0 || recipesLoading || waitingForBackgroundIndex) {
            return;
        }

        boolean inventoryChanged = computeInventoryFingerprint() != pendingPostCraftInventoryFingerprint;
        pendingPostCraftRefreshTicks--;
        if (!inventoryChanged && pendingPostCraftRefreshTicks > 0) {
            return;
        }

        pendingPostCraftRefreshTicks = 0;
        pendingPostCraftInventoryFingerprint = 0;
        refreshInventoryCache();
        cachedCraftableRecipes.clear();
        sortRecipes();
        refreshButtons();
    }

    private void continueRecipeLoading() {
        if (!recipesLoading) {
            return;
        }
        if (waitingForBackgroundIndex) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            recipesLoading = false;
            return;
        }

        int loadBatchSize = backgroundIndexBuild ? BACKGROUND_RECIPE_LOAD_BATCH_SIZE : RECIPE_LOAD_BATCH_SIZE;
        int processed = 0;
        while (processed < loadBatchSize && !pendingRecipeLoad.isEmpty()) {
            processRecipeForLoading(pendingRecipeLoad.remove(pendingRecipeLoad.size() - 1));
            processed++;
            loadedRecipeCount++;
        }

        if (pendingRecipeLoad.isEmpty()) {
            addBrewingRecipes(pendingGroupedRecipes, pendingDuplicateRecipes);
            pendingGroupedRecipes.forEach((key, recipes) -> allRecipeGroups.add(new RecipeGroup(key, recipes)));
            pendingGroupedRecipes.clear();
            pendingDuplicateRecipes.clear();
            recipesLoading = false;
            storeRecipeIndexCache(mc);
            if (backgroundIndexBuild) {
                return;
            }

            initializeRecipeViewQuickly();
            startCraftabilityRefresh();
            refreshButtons();
        }
    }

    private boolean isLoadingForCurrentRecipeIndex(Minecraft mc) {
        return recipesLoading
                && loadingRecipeManager == mc.level.getRecipeManager()
                && loadingRecipeCount == mc.level.getRecipeManager().getRecipes().size()
                && loadingUnlockRevision == ClientRecipeUnlocks.getRevision();
    }

    private void followBackgroundIndexBuilder() {
        waitingForBackgroundIndex = true;
        loadedRecipeCount = backgroundIndexBuilder.loadedRecipeCount;
        totalRecipeLoadCount = backgroundIndexBuilder.totalRecipeLoadCount;
        recipesLoading = backgroundIndexBuilder.recipesLoading;
        loadingRecipeManager = backgroundIndexBuilder.loadingRecipeManager;
        loadingRecipeCount = backgroundIndexBuilder.loadingRecipeCount;
        loadingUnlockRevision = backgroundIndexBuilder.loadingUnlockRevision;
    }

    private void storeRecipeIndexCache(Minecraft mc) {
        var recipeManager = mc.level.getRecipeManager();
        cachedRecipeManager = recipeManager;
        cachedRecipeCount = recipeManager.getRecipes().size();
        cachedUnlockRevision = ClientRecipeUnlocks.getRevision();
        cachedRecipeGroups = copyRecipeGroups(allRecipeGroups);
    }

    private static List<RecipeGroup> copyRecipeGroups(List<RecipeGroup> source) {
        List<RecipeGroup> copy = new ArrayList<>(source.size());
        for (RecipeGroup group : source) {
            copy.add(new RecipeGroup(group.resultKey(), List.copyOf(group.recipes())));
        }
        return copy;
    }

    private void processRecipeForLoading(RecipeHolder<?> holder) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Recipe<?> recipe = holder.value();
        if (!isRecipeVisible(recipe)) {
            return;
        }

        String duplicateKey = RecipeSupport.buildDuplicateKey(recipe, mc.level.registryAccess());
        if (!pendingDuplicateRecipes.add(duplicateKey)) {
            return;
        }

        ItemStack result = getRecipeResult(recipe);
        pendingGroupedRecipes.computeIfAbsent(buildResultGroupKey(result), ignored -> new ArrayList<>())
                .add(RecipeEntry.fromRecipe(holder));
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
        cachedAvailableMaterials.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String key = buildItemKey(stack);
                cachedInventoryCounts.merge(key, stack.getCount(), Integer::sum);
                addContainedBagCounts(stack);
                addAvailableStack(cachedAvailableMaterials, stack);
            }
        }

        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            for (var slot : mc.player.containerMenu.slots) {
                ItemStack stack = slot.getItem();
                if (slot.container != mc.player.getInventory() && !stack.isEmpty() && !slot.isFake() && slot.mayPickup(mc.player) && slot.mayPlace(stack)) {
                    addAvailableStack(cachedAvailableMaterials, stack);
                }
            }
        }
    }

    private void addContainedBagCounts(ItemStack stack) {
        if (stack.getItem() instanceof CategorizedBagItem) {
            for (ItemStack contained : CategorizedBagItem.getContents(stack)) {
                if (!contained.isEmpty()) {
                    cachedInventoryCounts.merge(buildItemKey(contained), contained.getCount(), Integer::sum);
                }
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
        sortRecipes(true);
    }

    private void sortRecipes(boolean refreshCraftability) {
        if (refreshCraftability) {
            startCraftabilityRefresh();
        }

        rebuildRecipeView();
        clampCurrentPage();
        materializeCurrentPage();
        refreshCurrentPageCraftability();
    }

    private void initializeRecipeViewQuickly() {
        rebuildRecipeView();
        clampCurrentPage();
        materializeCurrentPage();
        refreshCurrentPageCraftability();
    }

    private void rebuildRecipeView() {
        warmUpCraftabilityForSorting();

        List<RecipeGroup> nextView = new ArrayList<>();
        int nextCraftableCount = 0;
        SearchQuery searchQuery = parseSearchQuery(searchText.trim().toLowerCase(Locale.ROOT));
        for (RecipeGroup group : allRecipeGroups) {
            if (matchesFilters(group, cachedCraftableRecipes, searchQuery)) {
                nextView.add(group);
                if (canCraftAnyRecipe(group, cachedCraftableRecipes)) {
                    nextCraftableCount++;
                }
            }
        }

        nextView.sort(this::compareRecipeGroups);
        viewRecipeGroups = nextView;
        filteredRecipeCount = viewRecipeGroups.size();
        filteredCraftableGroupCount = nextCraftableCount;
    }

    private void warmUpCraftabilityForSorting() {
        if (isCreativeMode()) {
            return;
        }

        for (RecipeGroup group : allRecipeGroups) {
            int checked = 0;
            for (RecipeEntry entry : group.recipes()) {
                if (cachedCraftableRecipes.containsKey(entry.id())) {
                    if (Boolean.TRUE.equals(cachedCraftableRecipes.get(entry.id()))) {
                        break;
                    }
                } else {
                    cachedCraftableRecipes.put(entry.id(), canCraftEntry(entry));
                }

                checked++;
                if (checked >= SORT_CRAFTABILITY_WARMUP_RECIPES_PER_GROUP) {
                    break;
                }
            }
        }
    }

    private int computeInventoryFingerprint() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }

        int fingerprint = 1;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            fingerprint = 31 * fingerprint + stackFingerprint(i, mc.player.getInventory().getItem(i));
        }

        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            for (int i = 0; i < mc.player.containerMenu.slots.size(); i++) {
                var slot = mc.player.containerMenu.slots.get(i);
                if (slot.container != mc.player.getInventory() && !slot.isFake() && slot.mayPickup(mc.player)) {
                    fingerprint = 31 * fingerprint + stackFingerprint(i, slot.getItem());
                }
            }
        }
        return fingerprint;
    }

    private int stackFingerprint(int slotIndex, ItemStack stack) {
        if (stack.isEmpty()) {
            return slotIndex;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        int fingerprint = slotIndex;
        fingerprint = 31 * fingerprint + (itemId != null ? itemId.hashCode() : stack.getItem().hashCode());
        fingerprint = 31 * fingerprint + stack.getCount();
        fingerprint = 31 * fingerprint + stack.getComponentsPatch().hashCode();
        return fingerprint;
    }

    private int countCraftableGroupsInView() {
        int count = 0;
        for (RecipeGroup group : viewRecipeGroups) {
            if (canCraftAnyRecipe(group, cachedCraftableRecipes)) {
                count++;
            }
        }
        return count;
    }

    private void clampCurrentPage() {
        int totalPages = getTotalPages();
        if (totalPages == 0) {
            currentPage = 0;
        } else if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }
    }

    private void materializeCurrentPage() {
        int pageStart = currentPage * recipesPerPage;
        int pageEnd = pageStart + recipesPerPage;
        if (viewRecipeGroups.isEmpty() || pageStart >= viewRecipeGroups.size() || pageEnd <= 0) {
            sortedRecipeGroups = List.of();
            return;
        }

        sortedRecipeGroups = new ArrayList<>(viewRecipeGroups.subList(pageStart, Math.min(pageEnd, viewRecipeGroups.size())));
    }

    private int compareRecipeGroups(RecipeGroup a, RecipeGroup b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;

        ItemStack resultA = getEntryResult(getFirstRecipe(a));
        ItemStack resultB = getEntryResult(getFirstRecipe(b));

        boolean canCraftA = canCraftAnyRecipe(a, cachedCraftableRecipes);
        boolean canCraftB = canCraftAnyRecipe(b, cachedCraftableRecipes);
        if (canCraftA != canCraftB) {
            return canCraftA ? -1 : 1;
        }

        int countA = cachedInventoryCounts.getOrDefault(buildItemKey(resultA), 0);
        int countB = cachedInventoryCounts.getOrDefault(buildItemKey(resultB), 0);
        if (countA != countB) {
            return Integer.compare(countB, countA);
        }

        String nameA = resultA.getHoverName().getString();
        String nameB = resultB.getHoverName().getString();
        return nameA.compareToIgnoreCase(nameB);
    }

    private void startCraftabilityRefresh() {
        pendingCraftabilityRefresh.clear();
        for (RecipeGroup group : allRecipeGroups) {
            pendingCraftabilityRefresh.addAll(group.recipes());
        }
    }

    private void continueCraftabilityRefresh() {
        if (pendingCraftabilityRefresh.isEmpty() || recipesLoading) {
            return;
        }

        int processed = 0;
        while (processed < CRAFTABILITY_REFRESH_BATCH_SIZE && !pendingCraftabilityRefresh.isEmpty()) {
            RecipeEntry entry = pendingCraftabilityRefresh.remove(pendingCraftabilityRefresh.size() - 1);
            cachedCraftableRecipes.put(entry.id(), canCraftEntry(entry));
            processed++;
        }

        if (pendingCraftabilityRefresh.isEmpty()) {
            filteredCraftableGroupCount = countCraftableGroupsInView();
            refreshButtons();
        }
    }

    private void refreshCurrentPageCraftability() {
        if (sortedRecipeGroups.isEmpty()) {
            return;
        }

        for (RecipeGroup group : sortedRecipeGroups) {
            for (RecipeEntry entry : group.recipes()) {
                cachedCraftableRecipes.put(entry.id(), canCraftEntry(entry));
            }
        }
    }

    /**
     * 判断配方是否符合当前筛选条件。
     *
     * @param group 配方分组
     * @param craftableCache 可合成状态缓存
     * @return 通过筛选时返回 {@code true}
     */
    private boolean matchesFilters(RecipeGroup group, Map<ResourceLocation, Boolean> craftableCache, SearchQuery searchQuery) {
        if (onlyCraftable && !canCraftAnyRecipe(group, craftableCache)) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        return searchQuery.matches(new SearchContext(group));
    }

    private SearchQuery parseSearchQuery(String query) {
        List<SearchGroup> groups = new ArrayList<>();
        for (String groupText : splitSearchGroups(query)) {
            SearchGroup group = parseSearchGroup(groupText);
            if (!group.isEmpty()) {
                groups.add(group);
            }
        }
        return new SearchQuery(groups);
    }

    private List<String> splitSearchGroups(String query) {
        List<String> groups = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < query.length(); i++) {
            char character = query.charAt(i);
            if (character == '"') {
                quoted = !quoted;
                current.append(character);
            } else if (character == '|' && !quoted) {
                groups.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        groups.add(current.toString());
        return groups;
    }

    private SearchGroup parseSearchGroup(String groupText) {
        SearchGroup group = new SearchGroup();
        for (String token : splitSearchTerms(groupText)) {
            SearchTerm term = parseSearchTerm(token);
            if (term != null) {
                group.add(term);
            }
        }
        return group;
    }

    private List<String> splitSearchTerms(String groupText) {
        List<String> terms = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < groupText.length(); i++) {
            char character = groupText.charAt(i);
            if (character == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(character) && !quoted) {
                addSearchTerm(terms, current);
            } else {
                current.append(character);
            }
        }
        addSearchTerm(terms, current);
        return terms;
    }

    private void addSearchTerm(List<String> terms, StringBuilder current) {
        String term = current.toString().trim();
        if (!term.isEmpty()) {
            terms.add(term);
        }
        current.setLength(0);
    }

    private SearchTerm parseSearchTerm(String token) {
        boolean excluded = token.startsWith("-") && token.length() > 1;
        String value = excluded ? token.substring(1) : token;
        SearchTarget target = SearchTarget.DEFAULT;
        if (value.length() > 1) {
            target = switch (value.charAt(0)) {
                case '@' -> SearchTarget.MOD;
                case '#' -> SearchTarget.TOOLTIP;
                case '$' -> SearchTarget.TAG;
                case '%' -> SearchTarget.CREATIVE_TAB;
                case '~' -> SearchTarget.INGREDIENT;
                default -> SearchTarget.DEFAULT;
            };
            if (target != SearchTarget.DEFAULT) {
                value = value.substring(1);
            }
        }

        value = value.trim();
        return value.isEmpty() ? null : new SearchTerm(target, value, excluded);
    }

    private void ensureCreativeTabsBuilt() {
        if (creativeTabsBuilt) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        CreativeModeTabs.tryRebuildTabContents(
                mc.level.enabledFeatures(),
                mc.options.operatorItemsTab().get(),
                mc.level.registryAccess()
        );
        creativeTabsBuilt = true;
    }

    private enum SearchTarget {
        DEFAULT,
        MOD,
        TOOLTIP,
        TAG,
        CREATIVE_TAB,
        INGREDIENT
    }

    private record SearchTerm(SearchTarget target, String value, boolean excluded) {
    }

    private class SearchQuery {
        private final List<SearchGroup> groups;

        private SearchQuery(List<SearchGroup> groups) {
            this.groups = groups;
        }

        private boolean matches(SearchContext context) {
            if (groups.isEmpty()) {
                return true;
            }
            for (SearchGroup group : groups) {
                if (group.matches(context)) {
                    return true;
                }
            }
            return false;
        }
    }

    private class SearchGroup {
        private final List<SearchTerm> included = new ArrayList<>();
        private final List<SearchTerm> excluded = new ArrayList<>();

        private void add(SearchTerm term) {
            if (term.excluded()) {
                excluded.add(term);
            } else {
                included.add(term);
            }
        }

        private boolean isEmpty() {
            return included.isEmpty() && excluded.isEmpty();
        }

        private boolean matches(SearchContext context) {
            for (SearchTerm term : excluded) {
                if (context.matches(term)) {
                    return false;
                }
            }
            for (SearchTerm term : included) {
                if (!context.matches(term)) {
                    return false;
                }
            }
            return true;
        }
    }

    private class SearchContext {
        private final ItemStack stack;
        private final RecipeGroup group;
        private final ResourceLocation itemKey;
        private final String itemName;
        private final String itemId;
        private String tooltipText;
        private String tagText;
        private String creativeTabText;
        private String ingredientText;

        private SearchContext(RecipeGroup group) {
            this.group = group;
            this.stack = getEntryResult(getFirstRecipe(group));
            this.itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
            this.itemName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            this.itemId = itemKey != null ? itemKey.toString().toLowerCase(Locale.ROOT) : "";
        }

        private boolean matches(SearchTerm term) {
            String value = term.value();
            return switch (term.target()) {
                case DEFAULT -> itemName.contains(value) || itemId.contains(value);
                case MOD -> matchesMod(value);
                case TOOLTIP -> getTooltipText().contains(value);
                case TAG -> getTagText().contains(value);
                case CREATIVE_TAB -> getCreativeTabText().contains(value);
                case INGREDIENT -> getIngredientText().contains(value);
            };
        }

        private boolean matchesMod(String value) {
            if (itemKey == null) {
                return false;
            }

            String namespace = itemKey.getNamespace().toLowerCase(Locale.ROOT);
            if (namespace.contains(value)) {
                return true;
            }

            return ModList.get().getModContainerById(namespace)
                    .map(container -> container.getModInfo().getDisplayName().toLowerCase(Locale.ROOT).contains(value))
                    .orElse(false);
        }

        private String getTooltipText() {
            if (tooltipText == null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) {
                    tooltipText = "";
                } else {
                    StringBuilder text = new StringBuilder();
                    for (Component line : stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
                        text.append(line.getString().toLowerCase(Locale.ROOT)).append('\n');
                    }
                    tooltipText = text.toString();
                }
            }
            return tooltipText;
        }

        private String getTagText() {
            if (tagText == null) {
                StringBuilder text = new StringBuilder();
                stack.getTags()
                        .map(TagKey::location)
                        .forEach(location -> text.append(location).append(' ').append(location.getPath()).append(' '));
                tagText = text.toString().toLowerCase(Locale.ROOT);
            }
            return tagText;
        }

        private String getCreativeTabText() {
            if (creativeTabText == null) {
                ensureCreativeTabsBuilt();
                StringBuilder text = new StringBuilder();
                for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                    if (tab.contains(stack)) {
                        ResourceLocation tabKey = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
                        if (tabKey != null) {
                            text.append(tabKey).append(' ').append(tabKey.getPath()).append(' ');
                        }
                        text.append(tab.getDisplayName().getString()).append(' ');
                    }
                }
                creativeTabText = text.toString().toLowerCase(Locale.ROOT);
            }
            return creativeTabText;
        }

        private String getIngredientText() {
            if (ingredientText == null) {
                StringBuilder text = new StringBuilder();
                for (RecipeEntry entry : group.recipes()) {
                    for (DisplayIngredient ingredient : getIngredientStacks(entry)) {
                        appendSearchableStackText(text, ingredient.stack());
                    }
                }
                ingredientText = text.toString().toLowerCase(Locale.ROOT);
            }
            return ingredientText;
        }

        private void appendSearchableStackText(StringBuilder text, ItemStack searchableStack) {
            if (searchableStack.isEmpty()) {
                return;
            }

            ResourceLocation key = BuiltInRegistries.ITEM.getKey(searchableStack.getItem());
            text.append(searchableStack.getHoverName().getString()).append(' ');
            if (key != null) {
                text.append(key).append(' ').append(key.getPath()).append(' ');
            }
            searchableStack.getTags()
                    .map(TagKey::location)
                    .forEach(location -> text.append(location).append(' ').append(location.getPath()).append(' '));
        }
    }

    private boolean canCraftAnyRecipe(RecipeGroup group) {
        for (RecipeEntry entry : group.recipes()) {
            if (isCraftableCached(entry)) {
                return true;
            }
        }
        return false;
    }

    private boolean canCraftAnyRecipe(RecipeGroup group, Map<ResourceLocation, Boolean> craftableCache) {
        if (isCreativeMode()) {
            return !group.recipes().isEmpty();
        }
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

        for (RecipeEntry entry : group.recipes()) {
            if (isCraftableCached(entry)) {
                return entry;
            }
        }

        int activeIndex = (int) ((System.currentTimeMillis() / RECIPE_VARIANT_INTERVAL_MS) % group.recipes().size());
        return group.recipes().get(activeIndex);
    }

    private void refreshButtons() {
        clearWidgets();
    }

    private boolean isCraftableCached(RecipeEntry entry) {
        return entry != null && (isCreativeMode() || cachedCraftableRecipes.getOrDefault(entry.id(), false));
    }

    private boolean canCraftEntry(RecipeEntry entry) {
        if (entry == null) return false;
        if (isCreativeMode()) {
            return true;
        }
        if (entry.isBrewing()) {
            return findBrewingMatch(entry.brewing()) != null;
        }
        return canCraftRecipe(entry.recipe());
    }

    private boolean isCreativeMode() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getAbilities().instabuild;
    }

    private boolean canAttemptNestedCrafting(RecipeEntry entry) {
        return entry != null && (entry.isBrewing() || entry.recipe() != null) && !getEntryResult(entry).isEmpty();
    }

    private boolean isNestedCraftingRequested() {
        return hasAltDown();
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

        if (recipe instanceof StonecutterRecipe stonecutterRecipe) {
            ItemStack inputStack = findSingleInputMatch(stonecutterRecipe);
            if (inputStack.isEmpty() || mc.level == null) return false;

            SingleRecipeInput input = new SingleRecipeInput(inputStack);
            return stonecutterRecipe.matches(input, mc.level) && !stonecutterRecipe.assemble(input, mc.level.registryAccess()).isEmpty();
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
        if (entry == null) {
            return new DisplayIngredient[0];
        }

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

        if (recipe instanceof StonecutterRecipe stonecutterRecipe) {
            return getSingleInputIngredientStack(stonecutterRecipe);
        }

        if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
            return getSimpleIngredientStacks(recipe);
        }

        return getSimpleIngredientStacks(craftingRecipe);
    }

    private DisplayIngredient[] getSimpleIngredientStacks(Recipe<?> recipe) {
        List<Ingredient> ingredients = RecipeSupport.getNonEmptyIngredients(recipe);
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
        if (entry == null) {
            return 0;
        }
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

        if (recipe instanceof StonecutterRecipe stonecutterRecipe) {
            ItemStack inputStack = findSingleInputMatch(stonecutterRecipe);
            if (!inputStack.isEmpty()) {
                ItemStack assembled = stonecutterRecipe.assemble(new SingleRecipeInput(inputStack), mc.level.registryAccess());
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

        return RecipeSupport.isConfiguredSimpleRecipeFor(mc.player, recipe, mc.level.registryAccess());
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
                || !RecipeSupport.isConfiguredSimpleRecipeFor(mc.player, recipe, mc.level.registryAccess())) {
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

    private ItemStack findSingleInputMatch(Recipe<?> recipe) {
        List<Ingredient> ingredients = RecipeSupport.getNonEmptyIngredients(recipe);
        if (ingredients.size() != 1) {
            return ItemStack.EMPTY;
        }

        return takeFirstMatching(getInventorySnapshot(), ingredients.getFirst()::test);
    }

    private DisplayIngredient[] getSingleInputIngredientStack(Recipe<?> recipe) {
        List<Ingredient> ingredients = RecipeSupport.getNonEmptyIngredients(recipe);
        if (ingredients.size() != 1) {
            return new DisplayIngredient[0];
        }

        Ingredient ingredient = ingredients.getFirst();
        List<ItemStack> available = getInventorySnapshot();
        return new DisplayIngredient[] {
                getDisplayIngredient(available, ingredient::test, getCyclingIngredientItem(ingredient))
        };
    }

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

    /**
     * 获取玩家背包和当前打开容器的可变快照。
     *
     * <p>快照顺序固定为玩家背包优先、当前容器其次，用来保持“优先使用背包材料”的行为。</p>
     *
     * @return 可用材料的非空物品堆副本列表
     */
    private List<ItemStack> getInventorySnapshot() {
        List<ItemStack> available = new ArrayList<>();
        if (!cachedAvailableMaterials.isEmpty()) {
            for (ItemStack stack : cachedAvailableMaterials) {
                available.add(stack.copy());
            }
            return available;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return available;

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                // 使用副本参与匹配，避免客户端预览阶段直接修改玩家背包。
                addAvailableStack(available, stack);
            }
        }

        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            for (var slot : mc.player.containerMenu.slots) {
                ItemStack stack = slot.getItem();
                if (slot.container != mc.player.getInventory() && !stack.isEmpty() && !slot.isFake() && slot.mayPickup(mc.player) && slot.mayPlace(stack)) {
                    addAvailableStack(available, stack);
                }
            }
        }

        return available;
    }

    private static void addAvailableStack(List<ItemStack> available, ItemStack stack) {
        available.add(stack.copy());
        if (stack.getItem() instanceof CategorizedBagItem) {
            for (ItemStack contained : CategorizedBagItem.getContents(stack)) {
                if (!contained.isEmpty()) {
                    available.add(contained.copy());
                }
            }
        }
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.fill(panelX, panelY - PANEL_TOP_EXTRA, panelX + panelWidth, panelY + panelHeight, 0xC0101010);
        guiGraphics.fill(panelX, panelY - PANEL_TOP_EXTRA, panelX + panelWidth, panelY - PANEL_TOP_EXTRA + 2, 0xFF4444FF);
        guiGraphics.fill(panelX, panelY + panelHeight - 2, panelX + panelWidth, panelY + panelHeight, 0xFF4444FF);
        guiGraphics.drawString(font, title, panelX + 8, panelY + 2, 0xFFFFFF, false);

        String summary = "可合成: " + filteredCraftableGroupCount + "/" + filteredRecipeCount;
        guiGraphics.drawString(font, summary, panelX + panelWidth - font.width(summary) - 34, panelY + 2, 0x888888, false);

        drawFilterControls(guiGraphics);

        int totalPages = getTotalPages();
        if (totalPages > 1) {
            String pageStr = (currentPage + 1) + "/" + totalPages;
            guiGraphics.drawString(font, pageStr, panelX + panelWidth / 2 - font.width(pageStr) / 2, panelY + panelHeight - NAVIGATION_HEIGHT, 0xAAAAAA, false);
        }

        int startIndex = 0;
        int endIndex = sortedRecipeGroups.size();
        ItemStack hoveredIngredient = ItemStack.EMPTY;
        ItemStack hoveredResult = ItemStack.EMPTY;
        int ingredientXOffset = getPageIngredientXOffset(startIndex, endIndex);
        int ingredientWidth = getIngredientPanelWidth(ingredientXOffset);

        if (sortedRecipeGroups.isEmpty()) {
            String emptyText = recipesLoading
                    ? "加载配方中... " + loadedRecipeCount + "/" + totalRecipeLoadCount
                    : "没有匹配的配方";
            guiGraphics.drawString(font, emptyText, panelX + panelWidth / 2 - font.width(emptyText) / 2, panelY + LIST_Y_OFFSET + 52, 0xFFAAAAAA, false);
        }

        for (int i = startIndex; i < endIndex; i++) {
            RecipeGroup group = sortedRecipeGroups.get(i);
            RecipeEntry entry = getActiveRecipe(group);
            ItemStack result = getEntryResult(entry);

            int recipeY = getRecipeY(i);
            boolean canCraft = isCraftableCached(entry);
            boolean canAttemptNested = canAttemptNestedCrafting(entry);
            boolean nestedRequested = isNestedCraftingRequested();
            boolean canClickCraft = canCraft || nestedRequested && canAttemptNested;
            String craftButtonLabel = nestedRequested && canAttemptNested && !canCraft ? "→" : canClickCraft ? ">" : "X";

            if (!result.isEmpty() && isInside(mouseX, mouseY, panelX + RESULT_X_OFFSET, recipeY + 4, INGREDIENT_ICON_SIZE, INGREDIENT_ICON_SIZE)) {
                hoveredResult = result;
            }
            guiGraphics.renderItem(result, panelX + RESULT_X_OFFSET, recipeY + 4);
            guiGraphics.renderItemDecorations(font, result, panelX + RESULT_X_OFFSET, recipeY + 4);

            String resultName = result.getHoverName().getString();
            int resultNameWidth = ingredientXOffset - NAME_X_OFFSET - 10;
            if (font.width(resultName) > resultNameWidth) {
                resultName = font.ellipsize(FormattedText.of(resultName), resultNameWidth).getString();
            }
            guiGraphics.drawString(font, resultName, panelX + NAME_X_OFFSET, recipeY + 4, canCraft ? 0xFFFFFF : 0x888888, false);
            guiGraphics.drawString(font, "x" + result.getCount(), panelX + NAME_X_OFFSET, recipeY + 16, 0xAAAAAA, false);

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
                    if (!ingredient.available()) {
                        guiGraphics.fill(itemX, recipeY + 6, itemX + INGREDIENT_ICON_SIZE, recipeY + 6 + INGREDIENT_ICON_SIZE, 0x33FF0000);
                    }
                }
            }
            guiGraphics.disableScissor();

            drawPanelButton(guiGraphics, panelX + craftButtonXOffset, recipeY + 6, 22, 20, craftButtonLabel, canClickCraft);
        }

        drawNavigationButtons(guiGraphics);
        hoveredRecipeStack = !hoveredIngredient.isEmpty() ? hoveredIngredient : hoveredResult;
        renderRecipeTooltip(guiGraphics, hoveredIngredient, hoveredResult, mouseX, mouseY);
    }

    private void renderRecipeTooltip(GuiGraphics guiGraphics, ItemStack ingredient, ItemStack result, int mouseX, int mouseY) {
        if (!ingredient.isEmpty()) {
            guiGraphics.renderTooltip(font, ingredient, mouseX, mouseY);
        } else if (!result.isEmpty()) {
            guiGraphics.renderTooltip(font, result, mouseX, mouseY);
        }
    }

    private ItemStack getRecipeStackAt(double mouseX, double mouseY) {
        int startIndex = 0;
        int endIndex = sortedRecipeGroups.size();
        int ingredientXOffset = getPageIngredientXOffset(startIndex, endIndex);
        int ingredientWidth = getIngredientPanelWidth(ingredientXOffset);

        for (int i = startIndex; i < endIndex; i++) {
            RecipeGroup group = sortedRecipeGroups.get(i);
            RecipeEntry entry = getActiveRecipe(group);
            ItemStack result = getEntryResult(entry);
            int recipeY = getRecipeY(i);

            if (!result.isEmpty() && isInside(mouseX, mouseY, panelX + RESULT_X_OFFSET, recipeY + 4, INGREDIENT_ICON_SIZE, INGREDIENT_ICON_SIZE)) {
                return result;
            }

            DisplayIngredient[] ingredients = getIngredientStacks(entry);
            int ingX = panelX + ingredientXOffset;
            int ingredientOffset = getIngredientScrollOffset(ingredients.length, getMaxIngredientCount(group), (int) mouseX, (int) mouseY, ingX, recipeY, ingredientWidth);
            for (int j = 0; j < ingredients.length; j++) {
                DisplayIngredient ingredient = ingredients[j];
                int itemX = ingX + j * INGREDIENT_ICON_SIZE - ingredientOffset;
                if (ingredient.stack().isEmpty() || itemX <= ingX - INGREDIENT_ICON_SIZE || itemX >= ingX + ingredientWidth) {
                    continue;
                }

                int visibleLeft = Math.max(itemX, ingX);
                int visibleRight = Math.min(itemX + INGREDIENT_ICON_SIZE, ingX + ingredientWidth);
                if (visibleLeft < visibleRight && isInside(mouseX, mouseY, visibleLeft, recipeY + 6, visibleRight - visibleLeft, INGREDIENT_ICON_SIZE)) {
                    return ingredient.stack();
                }
            }
        }

        return ItemStack.EMPTY;
    }

    private boolean searchRecipesForStack(ItemStack stack) {
        return searchForStack(stack, false);
    }

    private boolean searchUsesForStack(ItemStack stack) {
        return searchForStack(stack, true);
    }

    private boolean searchForStack(ItemStack stack, boolean uses) {
        if (stack.isEmpty()) {
            return false;
        }

        pushSearchHistory();
        updateSearchText((uses ? "~" : "") + formatSearchTerm(stack.getHoverName().getString()));
        searchFocused = false;
        searchAllSelected = false;
        return true;
    }

    private void pushSearchHistory() {
        if (searchHistory.isEmpty() || !Objects.equals(searchHistory.peek(), searchText)) {
            searchHistory.push(searchText);
        }
    }

    private boolean goBackSearchHistory() {
        if (searchHistory.isEmpty()) {
            return false;
        }

        updateSearchText(searchHistory.pop());
        searchFocused = false;
        searchAllSelected = false;
        return true;
    }

    private String formatSearchTerm(String value) {
        String cleaned = value.replace("\"", "").trim();
        if (cleaned.indexOf(' ') >= 0) {
            return "\"" + cleaned + "\"";
        }
        return cleaned;
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

        int textWidth = searchWidth - 10;
        if (searchText.isEmpty() && !searchFocused) {
            guiGraphics.drawString(font, "搜索物品", searchX + 5, controlsY + 6, 0xFF777777, false);
        } else {
            String visibleText = getVisibleSearchText(textWidth);
            if (searchFocused && searchAllSelected && !visibleText.isEmpty()) {
                int selectionWidth = Math.min(font.width(visibleText), textWidth);
                guiGraphics.fill(searchX + 4, controlsY + 5, searchX + 5 + selectionWidth, controlsY + 15, 0xAA4F6CFF);
            }
            guiGraphics.drawString(font, visibleText, searchX + 5, controlsY + 6, 0xFFFFFFFF, false);
            if (searchFocused && !searchAllSelected && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int visibleCursor = Math.max(searchViewStart, Math.min(searchCursor, searchViewStart + visibleText.length()));
                int cursorX = searchX + 5 + font.width(searchText.substring(searchViewStart, visibleCursor));
                guiGraphics.fill(cursorX, controlsY + 5, cursorX + 1, controlsY + 15, 0xFFFFFFFF);
            }
        }
    }

    /**
     * 绘制分页、刷新和关闭按钮。
     *
     * @param guiGraphics 图形绘制上下文
     */
    private String getVisibleSearchText(int textWidth) {
        ensureSearchCursorVisible(Math.max(1, textWidth));
        String visibleText = searchText.substring(searchViewStart);
        if (font.width(visibleText) > textWidth) {
            visibleText = font.plainSubstrByWidth(visibleText, textWidth);
        }
        return visibleText;
    }

    private void ensureSearchCursorVisible(int textWidth) {
        searchCursor = clampSearchIndex(searchCursor);
        searchViewStart = clampSearchIndex(searchViewStart);
        if (searchViewStart > searchCursor) {
            searchViewStart = searchCursor;
        }

        while (searchViewStart < searchCursor && font.width(searchText.substring(searchViewStart, searchCursor)) > textWidth) {
            searchViewStart = nextSearchIndex(searchViewStart);
        }

        while (searchViewStart > 0) {
            int previousStart = previousSearchIndex(searchViewStart);
            if (font.width(searchText.substring(previousStart, searchCursor)) > textWidth) {
                break;
            }
            searchViewStart = previousStart;
        }
    }

    private void setSearchCursorFromMouse(double mouseX, int searchX, int textWidth) {
        int localX = Math.max(0, (int) Math.round(mouseX - searchX - 5));
        String visibleText = getVisibleSearchText(textWidth);
        int visibleEnd = searchViewStart + visibleText.length();
        int nextCursor = searchViewStart;
        int previousWidth = 0;

        while (nextCursor < visibleEnd) {
            int next = nextSearchIndex(nextCursor);
            int nextWidth = font.width(searchText.substring(searchViewStart, next));
            int midpoint = previousWidth + (nextWidth - previousWidth) / 2;
            if (localX < midpoint) {
                break;
            }
            nextCursor = next;
            previousWidth = nextWidth;
        }

        setSearchCursor(nextCursor);
    }

    private void setSearchCursor(int cursor) {
        searchCursor = clampSearchIndex(cursor);
        searchAllSelected = false;
        ensureSearchCursorVisible(getSearchTextWidth());
    }

    private int getSearchTextWidth() {
        int searchWidth = Math.max(48, panelWidth - 116);
        return Math.max(1, searchWidth - 10);
    }

    private int clampSearchIndex(int index) {
        return Math.max(0, Math.min(index, searchText.length()));
    }

    private int previousSearchIndex(int index) {
        int previous = clampSearchIndex(index);
        if (previous <= 0) {
            return 0;
        }

        previous--;
        if (previous > 0
                && Character.isLowSurrogate(searchText.charAt(previous))
                && Character.isHighSurrogate(searchText.charAt(previous - 1))) {
            previous--;
        }
        return previous;
    }

    private int nextSearchIndex(int index) {
        int next = clampSearchIndex(index);
        if (next >= searchText.length()) {
            return searchText.length();
        }

        if (next + 1 < searchText.length()
                && Character.isHighSurrogate(searchText.charAt(next))
                && Character.isLowSurrogate(searchText.charAt(next + 1))) {
            return next + 2;
        }
        return next + 1;
    }

    private int previousSearchWordIndex() {
        int cursor = clampSearchIndex(searchCursor);
        while (cursor > 0 && Character.isWhitespace(searchText.codePointBefore(cursor))) {
            cursor = previousSearchIndex(cursor);
        }
        while (cursor > 0 && !Character.isWhitespace(searchText.codePointBefore(cursor))) {
            cursor = previousSearchIndex(cursor);
        }
        return cursor;
    }

    private int nextSearchWordIndex() {
        int cursor = clampSearchIndex(searchCursor);
        while (cursor < searchText.length() && Character.isWhitespace(searchText.codePointAt(cursor))) {
            cursor = nextSearchIndex(cursor);
        }
        while (cursor < searchText.length() && !Character.isWhitespace(searchText.codePointAt(cursor))) {
            cursor = nextSearchIndex(cursor);
        }
        return cursor;
    }

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
            searchAllSelected = false;
            if (!searchText.isEmpty()) {
                updateSearchText("");
            } else {
                setSearchCursor(0);
            }
            return true;
        }

        if (button == 0 || button == 1) {
            ItemStack clickedStack = getRecipeStackAt(mouseX, mouseY);
            if (!clickedStack.isEmpty()) {
                return button == 0 ? searchRecipesForStack(clickedStack) : searchUsesForStack(clickedStack);
            }
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
            searchAllSelected = false;
            setSearchCursorFromMouse(mouseX, searchX, searchWidth - 10);
            return true;
        }
        searchFocused = false;
        searchAllSelected = false;

        int totalPages = getTotalPages();
        if (totalPages > 1 && isInside(mouseX, mouseY, panelX + 16, panelY + panelHeight - 34, 34, 20) && turnPage(-1)) {
            return true;
        }
        if (totalPages > 1 && isInside(mouseX, mouseY, panelX + panelWidth - 50, panelY + panelHeight - 34, 34, 20) && turnPage(1)) {
            return true;
        }
        if (isInside(mouseX, mouseY, panelX + panelWidth / 2 - 12, panelY + panelHeight - 30, 24, 18)) {
            currentPage = 0;
            refreshInventoryCache();
            startRecipeLoading();
            searchHistory.clear();
            refreshButtons();
            return true;
        }
        if (isInside(mouseX, mouseY, panelX + panelWidth - 22, panelY - 5, 20, 20)) {
            onClose();
            return true;
        }

        int startIndex = 0;
        int endIndex = sortedRecipeGroups.size();
        for (int i = startIndex; i < endIndex; i++) {
            RecipeGroup group = sortedRecipeGroups.get(i);
            RecipeEntry entry = getActiveRecipe(group);
            int recipeY = getRecipeY(i);
            boolean nestedRequested = isNestedCraftingRequested();

            if (isInside(mouseX, mouseY, panelX + craftButtonXOffset, recipeY + 6, 22, 20)
                    && (isCraftableCached(entry) || nestedRequested && canAttemptNestedCrafting(entry))) {
                playButtonClickSound();
                int craftCount = hasShiftDown() ? CraftRecipePacket.MAX_BATCH_CRAFTS : 1;
                PacketDistributor.sendToServer(new CraftRecipePacket(entry.id(), craftCount, nestedRequested));
                schedulePostCraftRefresh();
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
        materializeCurrentPage();
        refreshCurrentPageCraftability();
        refreshButtons();
        return true;
    }

    /**
     * 获取当前筛选结果的总页数。
     *
     * @return 总页数
     */
    private int getTotalPages() {
        return (filteredRecipeCount + recipesPerPage - 1) / recipesPerPage;
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
        searchHistory.clear();

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
            if (Screen.isSelectAll(keyCode)) {
                searchAllSelected = !searchText.isEmpty();
                searchCursor = searchText.length();
                ensureSearchCursorVisible(getSearchTextWidth());
                return true;
            }
            if (Screen.isCopy(keyCode)) {
                if (searchAllSelected && !searchText.isEmpty()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(searchText);
                }
                return true;
            }
            if (Screen.isCut(keyCode)) {
                if (searchAllSelected && !searchText.isEmpty()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(searchText);
                    updateSearchText("");
                }
                return true;
            }
            if (Screen.isPaste(keyCode)) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (!clipboard.isEmpty()) {
                    replaceSelectionOrInsertSearchText(sanitizeSearchInput(clipboard));
                }
                return true;
            }
            if (keyCode == KEY_LEFT) {
                setSearchCursor(Screen.hasControlDown() ? previousSearchWordIndex() : previousSearchIndex(searchCursor));
                return true;
            }
            if (keyCode == KEY_RIGHT) {
                setSearchCursor(Screen.hasControlDown() ? nextSearchWordIndex() : nextSearchIndex(searchCursor));
                return true;
            }
            if (keyCode == KEY_HOME) {
                setSearchCursor(0);
                return true;
            }
            if (keyCode == KEY_END) {
                setSearchCursor(searchText.length());
                return true;
            }
            if (keyCode == KEY_BACKSPACE) {
                deleteSearchTextBeforeCursor(Screen.hasControlDown());
                return true;
            }
            if (keyCode == KEY_DELETE) {
                deleteSearchTextAfterCursor(Screen.hasControlDown());
                return true;
            }
            if (keyCode == KEY_ENTER || keyCode == KEY_ESCAPE) {
                searchFocused = false;
                searchAllSelected = false;
                return true;
            }
        }

        if (keyCode == KEY_BACKSPACE && goBackSearchHistory()) {
            return true;
        }

        if (!hoveredRecipeStack.isEmpty()) {
            if (keyCode == 82) { // R
                return searchRecipesForStack(hoveredRecipeStack);
            }
            if (keyCode == 85) { // U
                return searchUsesForStack(hoveredRecipeStack);
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

        replaceSelectionOrInsertSearchText(String.valueOf(codePoint));
        return true;
    }

    private void replaceSelectionOrInsertSearchText(String text) {
        if (text.isEmpty()) {
            return;
        }

        if (searchAllSelected) {
            updateSearchText(text, text.length());
            return;
        }

        int cursor = clampSearchIndex(searchCursor);
        updateSearchText(searchText.substring(0, cursor) + text + searchText.substring(cursor), cursor + text.length());
    }

    private void deleteSearchTextBeforeCursor(boolean deleteWord) {
        if (searchAllSelected) {
            updateSearchText("");
            return;
        }

        int cursor = clampSearchIndex(searchCursor);
        if (cursor <= 0) {
            return;
        }

        int deleteFrom = deleteWord ? previousSearchWordIndex() : previousSearchIndex(cursor);
        updateSearchText(searchText.substring(0, deleteFrom) + searchText.substring(cursor), deleteFrom);
    }

    private void deleteSearchTextAfterCursor(boolean deleteWord) {
        if (searchAllSelected) {
            updateSearchText("");
            return;
        }

        int cursor = clampSearchIndex(searchCursor);
        if (cursor >= searchText.length()) {
            return;
        }

        int deleteTo = deleteWord ? nextSearchWordIndex() : nextSearchIndex(cursor);
        updateSearchText(searchText.substring(0, cursor) + searchText.substring(deleteTo), cursor);
    }

    private String sanitizeSearchInput(String text) {
        StringBuilder sanitized = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (!Character.isISOControl(character)) {
                sanitized.append(character);
            }
        }
        return sanitized.toString();
    }

    private void updateSearchText(String text) {
        updateSearchText(text, text.length());
    }

    private void updateSearchText(String text, int cursor) {
        searchText = text;
        searchCursor = clampSearchIndex(cursor);
        searchViewStart = Math.min(clampSearchIndex(searchViewStart), searchCursor);
        searchAllSelected = false;
        currentPage = 0;
        sortRecipes();
        refreshButtons();
        ensureSearchCursorVisible(getSearchTextWidth());
    }
}
