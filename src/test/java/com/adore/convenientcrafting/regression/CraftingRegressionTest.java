package com.adore.convenientcrafting.regression;

import com.adore.convenientcrafting.config.Config;
import com.adore.convenientcrafting.compat.RecipeCompat;
import com.adore.convenientcrafting.recipe.IngredientMatcher;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import com.adore.convenientcrafting.inventory.InventorySorter;
import com.adore.convenientcrafting.item.CategorizedBagItem;
import com.adore.convenientcrafting.network.CraftRecipePacket;
import com.adore.convenientcrafting.recipe.RecipeSupport;
import com.adore.convenientcrafting.recipe.unlock.ClientRecipeUnlocks;
import com.adore.convenientcrafting.registry.ModItems;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage against loaded datapacks and real inventory/bag components on every target. */
@ExtendWith(EphemeralTestServerProvider.class)
class CraftingRegressionTest {
    private List<? extends String> defaultUnlocked, additional;
    private boolean useBags;

    @BeforeEach
    void prepare(MinecraftServer server) {
        defaultUnlocked = Config.DEFAULT_UNLOCKED_RECIPE_TYPES.get();
        additional = Config.ADDITIONAL_RECIPE_TYPES.get();
        useBags = Config.USE_BAG_CONTENTS_FOR_CRAFTING.get();
        Config.DEFAULT_UNLOCKED_RECIPE_TYPES.set(List.of("minecraft:crafting"));
        Config.ADDITIONAL_RECIPE_TYPES.set(List.of());
        Config.USE_BAG_CONTENTS_FOR_CRAFTING.set(true);
    }

    @AfterEach
    void restore() {
        Config.DEFAULT_UNLOCKED_RECIPE_TYPES.set(defaultUnlocked);
        Config.ADDITIONAL_RECIPE_TYPES.set(additional);
        Config.USE_BAG_CONTENTS_FOR_CRAFTING.set(useBags);
        ClientRecipeUnlocks.clear();
    }

    @Test
    void altSortMustConserveFullInventoryAndBagContents(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < 35; i++) {
            ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
            sword.set(DataComponents.CUSTOM_NAME, Component.literal("distinct-tool-" + i));
            inventory.setItem(i, sword);
        }
        ItemStack bag = ModItems.SEED_BAG.toStack();
        NonNullList<ItemStack> contents = NonNullList.withSize(CategorizedBagItem.SLOT_COUNT, ItemStack.EMPTY);
        contents.set(0, new ItemStack(Items.WHEAT_SEEDS, 64));
        CategorizedBagItem.setContents(bag, contents);
        inventory.setItem(35, bag);
        Map<Item, Integer> before = totals(inventory);
        InventorySorter.sortInventory(inventory, player, true);
        assertEquals(before, totals(inventory), "Alt sort must preserve every item, including the bag");
    }

    @Test
    void ordinaryCraftFailureMustNotConsumeTheBagOrItsContents(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        ItemStack bag = ModItems.SEED_BAG.toStack();
        NonNullList<ItemStack> contents = NonNullList.withSize(CategorizedBagItem.SLOT_COUNT, ItemStack.EMPTY);
        contents.set(0, new ItemStack(Items.WHEAT_SEEDS));
        CategorizedBagItem.setContents(bag, contents);
        player.getInventory().setItem(0, bag);
        var recipe = recipe(new ItemStack(Items.DIAMOND), Ingredient.of(ModItems.SEED_BAG.get()), Ingredient.of(Items.WHEAT_SEEDS));
        Map<Item,Integer> before = totals(player.getInventory());
        assertNull(build(player, recipe), "A bag and its own contents cannot both be consumed");
        assertEquals(before, totals(player.getInventory()));
        // A separate loose seed makes the same recipe valid.
        player.getInventory().setItem(1, new ItemStack(Items.WHEAT_SEEDS));
        assertNotNull(build(player, recipe));
    }

    @Test
    void helperMustFindTheSameValidIngredientAssignmentAsVanilla(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS));
        player.getInventory().setItem(1, new ItemStack(Items.BIRCH_PLANKS));
        var recipe = recipe(new ItemStack(Items.STICK), Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS), Ingredient.of(Items.OAK_PLANKS));
        assertTrue(recipe.matches(CraftingInput.of(2, 1, List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS))), null));
        assertNotNull(build(player, recipe), "The flexible slot must use birch and reserve oak for the oak-only slot");
        assertTrue(simulateNested(player, recipe), "Nested planning must also reassign flexible ingredients");
    }

    @Test
    void helperMustRespectCraftingRecipeMatches(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS));
        //? if >=26.1 {
        /*ShapelessRecipe recipe = new ShapelessRecipe(new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.DIAMOND)),
        *///?} else {
        ShapelessRecipe recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND),
        //?}
                //? if >=1.21.2 {
                /*List.of(Ingredient.of(Items.OAK_PLANKS))) {
                *///?} else {
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_PLANKS))) {
                //?}
            @Override public boolean matches(CraftingInput input, Level level) { return false; }
        };
        assertFalse(recipe.matches(CraftingInput.of(1, 1, List.of(new ItemStack(Items.OAK_PLANKS))), null));
        assertNull(build(player, recipe), "A recipe that rejects its input must not produce a craft plan");
        assertFalse(simulateNested(player, recipe), "Nested planning must respect matches too");
    }

    @Test
    void removedAdditionalTypeMustStayDisabledEvenWhenPreviouslyUnlocked(MinecraftServer server) {
        ClientRecipeUnlocks.setUnlockedRecipeTypes(List.of("minecraft:smelting"));
        ClientRecipeUnlocks.setEnabledAdditionalRecipeTypes(List.of());
        Recipe<?> smelting = RecipeCompat.byId(server.getRecipeManager(), net.minecraft.resources.ResourceLocation.parse("minecraft:iron_ingot_from_smelting_iron_ore")).orElseThrow().value();
        assertTrue(Config.ADDITIONAL_RECIPE_TYPES.get().isEmpty());
        assertFalse(RecipeSupport.isConfiguredSimpleRecipeFor(null, smelting, server.registryAccess()),
                "An old unlock must not override removal from enabled additional types");
    }

    @Test
    void ordinaryCraftCompletesWithCorrectInputsAndOutputs(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
        player.getInventory().setItem(1, new ItemStack(Items.BIRCH_PLANKS));
        Object plan = build(player, recipe(new ItemStack(Items.STICK, 4), Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS), Ingredient.of(Items.OAK_PLANKS)));
        assertNotNull(plan);
        Method finish = CraftRecipePacket.class.getDeclaredMethod("finishCrafting", ServerPlayer.class, plan.getClass());
        finish.setAccessible(true);
        assertEquals(true, finish.invoke(null, player, plan));
        Map<Item,Integer> counts = totals(player.getInventory());
        assertEquals(4, counts.get(Items.STICK));
        assertEquals(2, counts.getOrDefault(Items.OAK_PLANKS, 0) + counts.getOrDefault(Items.BIRCH_PLANKS, 0));
    }

    @Test
    void fullInventoryCannotConsumeBagContentsWhenOutputHasNoRoom(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        for (int i = 0; i < 35; i++) player.getInventory().setItem(i, new ItemStack(Items.WOODEN_SWORD));
        ItemStack bag = ModItems.SEED_BAG.toStack();
        ((CategorizedBagItem) bag.getItem()).insert(bag, new ItemStack(Items.WHEAT_SEEDS));
        player.getInventory().setItem(35, bag);
        Object plan = build(player, recipe(new ItemStack(Items.DIAMOND), Ingredient.of(Items.WHEAT_SEEDS)));
        assertNotNull(plan);
        Map<Item,Integer> before = totals(player.getInventory());
        Method finish = CraftRecipePacket.class.getDeclaredMethod("finishCrafting", ServerPlayer.class, plan.getClass());
        finish.setAccessible(true);
        assertEquals(false, finish.invoke(null, player, plan), "Empty armor/offhand slots cannot store output");
        assertEquals(before, totals(player.getInventory()));
    }

    @Test
    void staleMaterialCannotPartiallyConsumeAnEarlierSlot(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS));
        player.getInventory().setItem(1, new ItemStack(Items.BIRCH_PLANKS));
        Object plan = build(player, recipe(new ItemStack(Items.STICK), Ingredient.of(Items.OAK_PLANKS), Ingredient.of(Items.BIRCH_PLANKS)));
        assertNotNull(plan);
        player.getInventory().setItem(1, ItemStack.EMPTY);
        Map<Item,Integer> before = totals(player.getInventory());
        Method finish = CraftRecipePacket.class.getDeclaredMethod("finishCrafting", ServerPlayer.class, plan.getClass());
        finish.setAccessible(true);
        assertEquals(false, finish.invoke(null, player, plan));
        assertEquals(before, totals(player.getInventory()));
    }

    @Test
    void menuWriteFailureRestoresBothInventoryAndContainer(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS));
        SimpleContainer container = new SimpleContainer(new ItemStack(Items.BIRCH_PLANKS));
        player.containerMenu = new AbstractContainerMenu(null, 0) {
            { addSlot(new Slot(container, 0, 0, 0) {
                private boolean fail = true;
                @Override public void set(ItemStack stack) {
                    if (fail) { fail = false; throw new IllegalStateException("injected slot failure"); }
                    super.set(stack);
                }
            }); }
            @Override public boolean stillValid(Player ignored) { return true; }
            @Override public ItemStack quickMoveStack(Player ignored, int index) { return ItemStack.EMPTY; }
        };
        Object plan = build(player, recipe(new ItemStack(Items.STICK), Ingredient.of(Items.OAK_PLANKS), Ingredient.of(Items.BIRCH_PLANKS)));
        assertNotNull(plan);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> consume(player, plan));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(player.getInventory().getItem(0).is(Items.OAK_PLANKS));
        assertTrue(container.getItem(0).is(Items.BIRCH_PLANKS));
    }

    @Test
    void multipleIngredientsFromOneBagDeductExactlyOnceEach(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        ItemStack bag = ModItems.SEED_BAG.toStack();
        ((CategorizedBagItem) bag.getItem()).insert(bag, new ItemStack(Items.WHEAT_SEEDS, 3));
        player.getInventory().setItem(0, bag);
        Object plan = build(player, recipe(new ItemStack(Items.DIAMOND), Ingredient.of(Items.WHEAT_SEEDS), Ingredient.of(Items.WHEAT_SEEDS)));
        assertNotNull(plan);
        assertTrue(consume(player, plan));
        assertEquals(1, CategorizedBagItem.getContents(player.getInventory().getItem(0)).stream().mapToInt(ItemStack::getCount).sum());
        assertTrue(player.getInventory().getItem(0).is(ModItems.SEED_BAG.get()));
    }

    @Test
    void matchingCanChooseASecondBagInsteadOfConsumingItsOwnContents(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        ItemStack full = ModItems.SEED_BAG.toStack();
        ((CategorizedBagItem) full.getItem()).insert(full, new ItemStack(Items.WHEAT_SEEDS));
        player.getInventory().setItem(0, full);
        player.getInventory().setItem(1, ModItems.SEED_BAG.toStack());
        Object plan = build(player, recipe(new ItemStack(Items.DIAMOND), Ingredient.of(ModItems.SEED_BAG.get()), Ingredient.of(Items.WHEAT_SEEDS)));
        assertNotNull(plan, "A valid alternate bag must be found rather than rejecting all bag contents");
        assertTrue(consume(player, plan));
        assertEquals(Map.of(ModItems.SEED_BAG.get(), 1), totals(player.getInventory()));
    }

    @Test
    void craftingUsesAssembledComponentsAndRecipeSpecificRemainders(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        ItemStack input = new ItemStack(Items.MILK_BUCKET);
        input.set(DataComponents.CUSTOM_NAME, Component.literal("inherited name"));
        player.getInventory().setItem(0, input);
        int[] assemblyCalls = {0};
        //? if >=26.1 {
        /*ShapelessRecipe dynamic = new ShapelessRecipe(new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.DIAMOND)),
        *///?} else {
        ShapelessRecipe dynamic = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.DIAMOND),
        //?}
                //? if >=1.21.2 {
                /*List.of(Ingredient.of(Items.MILK_BUCKET))) {
                *///?} else {
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.MILK_BUCKET))) {
                //?}
            @Override
            //? if >=26.1 {
            /*public ItemStack assemble(CraftingInput input) {
            *///?} else {
            public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            //?}
                assemblyCalls[0]++;
                ItemStack result = new ItemStack(Items.EMERALD, 2);
                result.set(DataComponents.CUSTOM_NAME, input.getItem(0).get(DataComponents.CUSTOM_NAME));
                return result;
            }
            @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
                return NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.BOWL));
            }
        };
        Object plan = build(player, dynamic);
        assertNotNull(plan);
        List<ItemStack> outputs = results(plan);
        assertEquals(2, outputs.size(), "Do not add a second, static bucket remainder");
        assertTrue(outputs.get(0).is(Items.EMERALD));
        assertEquals(2, outputs.get(0).getCount());
        assertEquals(input.get(DataComponents.CUSTOM_NAME), outputs.get(0).get(DataComponents.CUSTOM_NAME));
        assertTrue(outputs.get(1).is(Items.BOWL));
        assertEquals(1, assemblyCalls[0]);
        assertTrue(simulateNested(player, dynamic));
        assertEquals(2, assemblyCalls[0], "Nested planning must assemble the real input too");
    }

    @Test
    void shapedRecipeKeepsHolesAndTwoByTwoDimensions(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        player.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
        player.getInventory().setItem(1, new ItemStack(Items.STICK));
        //? if >=26.1 {
        /*ShapedRecipe shaped = new ShapedRecipe(new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                ShapedRecipePattern.of(Map.of('P', Ingredient.of(Items.OAK_PLANKS), 'S', Ingredient.of(Items.STICK)), "P ", "PS"),
                net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.WOODEN_AXE)));
        *///?} else {
        ShapedRecipe shaped = new ShapedRecipe("", CraftingBookCategory.MISC,
                ShapedRecipePattern.of(Map.of('P', Ingredient.of(Items.OAK_PLANKS), 'S', Ingredient.of(Items.STICK)), "P ", "PS"),
                new ItemStack(Items.WOODEN_AXE));
        //?}
        Object plan = build(player, shaped);
        assertNotNull(plan);
        assertTrue(results(plan).getFirst().is(Items.WOODEN_AXE));
        var input = RecipeCompat.craftingInput(shaped, List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.STICK)));
        assertEquals(2, input.width());
        assertEquals(2, input.height());
        assertTrue(input.getItem(1).isEmpty());
    }

    @Test
    void vanillaCakeKeepsThreeBucketRemainders(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        player.getInventory().setItem(0, new ItemStack(Items.MILK_BUCKET));
        player.getInventory().setItem(1, new ItemStack(Items.MILK_BUCKET));
        player.getInventory().setItem(2, new ItemStack(Items.MILK_BUCKET));
        player.getInventory().setItem(3, new ItemStack(Items.SUGAR, 2));
        player.getInventory().setItem(4, new ItemStack(Items.EGG));
        player.getInventory().setItem(5, new ItemStack(Items.WHEAT, 3));
        Object plan = build(player, RecipeCompat.byId(server.getRecipeManager(), net.minecraft.resources.ResourceLocation.parse("minecraft:cake")).orElseThrow().value());
        assertNotNull(plan);
        assertEquals(3, results(plan).stream().filter(stack -> stack.is(Items.BUCKET)).mapToInt(ItemStack::getCount).sum());
        assertTrue(results(plan).getFirst().is(Items.CAKE));
    }

    @Test
    void fullInventoryCompactsMineralsInsideBagWithoutLosingTools(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        for (int i = 0; i < 35; i++) {
            ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
            sword.set(DataComponents.CUSTOM_NAME, Component.literal("tool-" + i));
            player.getInventory().setItem(i, sword);
        }
        ItemStack bag = ModItems.MINERAL_BAG.toStack();
        ((CategorizedBagItem) bag.getItem()).insert(bag, new ItemStack(Items.IRON_INGOT, 64));
        player.getInventory().setItem(35, bag);
        InventorySorter.sortInventory(player.getInventory(), player, true);
        Map<Item,Integer> counts = totals(player.getInventory());
        assertEquals(35, counts.get(Items.WOODEN_SWORD));
        assertEquals(1, counts.get(ModItems.MINERAL_BAG.get()));
        assertEquals(7, counts.get(Items.IRON_BLOCK));
        assertEquals(1, counts.get(Items.IRON_INGOT));
    }

    @Test
    void unpackedOverflowFallsBackWithoutLosingUnexpectedBagContents(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        for (int i = 0; i < 35; i++) player.getInventory().setItem(i, new ItemStack(Items.WOODEN_SWORD));
        ItemStack bag = ModItems.SEED_BAG.toStack();
        NonNullList<ItemStack> contents = NonNullList.withSize(CategorizedBagItem.SLOT_COUNT, ItemStack.EMPTY);
        contents.set(0, new ItemStack(Items.DIAMOND));
        CategorizedBagItem.setContents(bag, contents);
        player.getInventory().setItem(35, bag);
        Map<Item,Integer> before = totals(player.getInventory());
        InventorySorter.sortInventory(player.getInventory(), player, true);
        assertEquals(before, totals(player.getInventory()));
    }

    @Test
    void ingredientMatchingHonorsStackCapacityAndReassignsFlexibleSlots(MinecraftServer server) {
        var ingredients = List.of(Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS), Ingredient.of(Items.OAK_PLANKS), Ingredient.of(Items.OAK_PLANKS));
        assertEquals(3, IngredientMatcher.match(ingredients, List.of(new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.BIRCH_PLANKS))).length);
        assertEquals(0, IngredientMatcher.match(ingredients, List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.BIRCH_PLANKS))).length);
        assertEquals(0, IngredientMatcher.match(ingredients, List.of(new ItemStack(Items.BIRCH_PLANKS, 64))).length);
    }

    @Test
    void matcherAgreesWithExhaustiveAssignmentsForOverlappingIngredients(MinecraftServer server) {
        Random random = new Random(45217);
        Item[] types = {Items.OAK_PLANKS, Items.BIRCH_PLANKS, Items.SPRUCE_PLANKS};
        for (int trial = 0; trial < 300; trial++) {
            List<Ingredient> ingredients = new ArrayList<>();
            for (int i = 0, n = 1 + random.nextInt(6); i < n; i++) {
                List<Item> options = new ArrayList<>();
                for (Item type : types) if (random.nextBoolean()) options.add(type);
                if (options.isEmpty()) options.add(types[random.nextInt(types.length)]);
                ingredients.add(Ingredient.of(options.toArray(Item[]::new)));
            }
            List<ItemStack> materials = Arrays.stream(types).map(item -> new ItemStack(item, random.nextInt(3))).toList();
            boolean expected = exhaustive(ingredients, materials.stream().map(ItemStack::copy).toList(), 0);
            assertEquals(expected, IngredientMatcher.match(ingredients, materials).length == ingredients.size(), "trial " + trial);
        }
    }

    @Test
    void serverAndClientRequireEnablementIndependentlyOfUnlocks(MinecraftServer server) throws Exception {
        Recipe<?> smelting = RecipeCompat.byId(server.getRecipeManager(), net.minecraft.resources.ResourceLocation.parse("minecraft:iron_ingot_from_smelting_iron_ore")).orElseThrow().value();
        ServerPlayer player = inventoryOnlyPlayer(server);
        Config.DEFAULT_UNLOCKED_RECIPE_TYPES.set(List.of("minecraft:crafting", "minecraft:smelting"));
        assertFalse(RecipeSupport.isConfiguredSimpleRecipeFor(player, smelting, server.registryAccess()));
        assertFalse(RecipeSupport.isUnlockedFor(player, smelting));
        Config.ADDITIONAL_RECIPE_TYPES.set(List.of("minecraft:smelting"));
        assertTrue(RecipeSupport.isConfiguredSimpleRecipeFor(player, smelting, server.registryAccess()));
        assertTrue(RecipeSupport.isUnlockedFor(player, smelting));
        ClientRecipeUnlocks.setUnlockedRecipeTypes(List.of("minecraft:smelting"));
        ClientRecipeUnlocks.setEnabledAdditionalRecipeTypes(List.of());
        assertFalse(RecipeSupport.isConfiguredSimpleRecipeFor(null, smelting, server.registryAccess()), "Local config cannot override server disablement");
        ClientRecipeUnlocks.setEnabledAdditionalRecipeTypes(List.of("minecraft:smelting"));
        assertTrue(RecipeSupport.isUnlockedFor(null, smelting));
        ClientRecipeUnlocks.setUnlockedRecipeTypes(List.of());
        assertFalse(RecipeSupport.isUnlockedFor(null, smelting));
        ClientRecipeUnlocks.clear();
        assertFalse(RecipeSupport.isConfiguredSimpleRecipeFor(null, smelting, server.registryAccess()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistedUnlocksSurviveSavingAndReading(MinecraftServer server) throws Exception {
        ServerPlayer player = inventoryOnlyPlayer(server);
        Class<?> unlocks = com.adore.convenientcrafting.recipe.unlock.RecipeUnlocks.class;
        Method save = unlocks.getDeclaredMethod("saveUnlockedTypes", Player.class, Set.class);
        Method load = unlocks.getDeclaredMethod("getUnlockedTypes", Player.class);
        save.setAccessible(true);
        load.setAccessible(true);
        Set<String> expected = Set.of("minecraft:smithing", "minecraft:stonecutting");
        save.invoke(null, player, expected);
        assertEquals(expected, (Set<String>) load.invoke(null, player));
        save.invoke(null, player, Set.of());
        assertEquals(Set.of(), load.invoke(null, player));
    }

    private static boolean exhaustive(List<Ingredient> ingredients, List<ItemStack> stacks, int next) {
        if (next == ingredients.size()) return true;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || !ingredients.get(next).test(stack)) continue;
            stack.shrink(1);
            if (exhaustive(ingredients, stacks, next + 1)) return true;
            stack.grow(1);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> results(Object plan) throws Exception {
        Method accessor = plan.getClass().getDeclaredMethod("results");
        accessor.setAccessible(true);
        return (List<ItemStack>) accessor.invoke(plan);
    }

    private static boolean consume(ServerPlayer player, Object plan) throws Exception {
        Method accessor = plan.getClass().getDeclaredMethod("ingredients"); accessor.setAccessible(true);
        Method method = CraftRecipePacket.class.getDeclaredMethod("consumeIngredients", ServerPlayer.class, List.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, player, accessor.invoke(plan));
    }

    private static ShapelessRecipe recipe(ItemStack result, Ingredient... ingredients) {
        //? if >=26.1 {
        /*return new ShapelessRecipe(new Recipe.CommonInfo(true),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(result), List.of(ingredients));
        *///?} elif >=1.21.2 {
        /*return new ShapelessRecipe("", CraftingBookCategory.MISC, result, List.of(ingredients));
        *///?} else {
        return new ShapelessRecipe("", CraftingBookCategory.MISC, result, NonNullList.of(Ingredient.EMPTY, ingredients));
        //?}
    }

    private static boolean simulateNested(ServerPlayer player, CraftingRecipe recipe) throws Exception {
        var id = net.minecraft.resources.ResourceLocation.parse("convenientcrafting:regression");
        //? if >=1.21.2 {
        /*RecipeHolder<?> holder = new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE, id), recipe);
        *///?} else {
        RecipeHolder<?> holder = new RecipeHolder<>(id, recipe);
        //?}
        Method method = CraftRecipePacket.class.getDeclaredMethod("simulateNestedCrafting", ServerPlayer.class, RecipeHolder.class);
        method.setAccessible(true);
        Object simulation = method.invoke(null, player, holder);
        Method success = simulation.getClass().getDeclaredMethod("success"); success.setAccessible(true);
        return (boolean) success.invoke(simulation);
    }

    private static Object build(ServerPlayer player, Recipe<?> recipe) throws Exception {
        Method method = CraftRecipePacket.class.getDeclaredMethod("buildCraftingResult", ServerPlayer.class, Recipe.class);
        method.setAccessible(true);
        return method.invoke(null, player, recipe);
    }

    // The ephemeral server loads datapacks without a world. This constructor-free
    // fixture supplies only the fields the inventory/crafting methods read; no live
    // world, connection, player, or saved inventory is touched.
    private static ServerPlayer inventoryOnlyPlayer(MinecraftServer server) throws Exception {
        Class<?> type = Class.forName("sun.misc.Unsafe");
        Field singleton = type.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        Method allocate = type.getMethod("allocateInstance", Class.class);
        ServerLevel level = (ServerLevel) allocate.invoke(unsafe, ServerLevel.class);
        ServerPlayer player = (ServerPlayer) allocate.invoke(unsafe, ServerPlayer.class);
        put(unsafe, level, ServerLevel.class.getDeclaredField("server"), server);
        put(unsafe, player, Entity.class.getDeclaredField("level"), level);
        put(unsafe, player, ServerPlayer.class.getDeclaredField("server"), server);
        put(unsafe, player, Player.class.getDeclaredField("inventory"), new Inventory(player
                //? if >=26.1 {
                /*, new net.minecraft.world.entity.EntityEquipment()
                *///?}
        ) {
            @Override public void placeItemBackInInventory(ItemStack stack) {
                // Exercise vanilla insertion without a network connection in this fixture.
                super.placeItemBackInInventory(stack, false);
            }
        });
        return player;
    }

    private static void put(Object unsafe, Object instance, Field field, Object value) throws Exception {
        Class<?> type = unsafe.getClass();
        long offset = (long) type.getMethod("objectFieldOffset", Field.class).invoke(unsafe, field);
        type.getMethod("putObject", Object.class, long.class, Object.class).invoke(unsafe, instance, offset, value);
    }

    private static Map<Item,Integer> totals(Inventory inventory) {
        Map<Item,Integer> result = new HashMap<>();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            result.merge(stack.getItem(), stack.getCount(), Integer::sum);
            if (stack.getItem() instanceof CategorizedBagItem) {
                for (ItemStack contained : CategorizedBagItem.getContents(stack)) {
                    if (!contained.isEmpty()) result.merge(contained.getItem(), contained.getCount(), Integer::sum);
                }
            }
        }
        return result;
    }
}
