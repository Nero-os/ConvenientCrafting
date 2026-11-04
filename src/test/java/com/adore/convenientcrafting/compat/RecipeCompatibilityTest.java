package com.adore.convenientcrafting.compat;

import com.adore.convenientcrafting.item.CategorizedBagItem;
import com.adore.convenientcrafting.recipe.RecipeSupport;
import com.adore.convenientcrafting.registry.ModItems;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual loaded vanilla/mod datapacks in a NeoForge server for each target. */
@ExtendWith(EphemeralTestServerProvider.class)
class RecipeCompatibilityTest {
    @Test
    void shapedIngredientsKeepTheirMultiplicity(MinecraftServer server) {
        var holder = recipe(server, "minecraft:diamond_block");
        var ingredients = RecipeSupport.getNonEmptyIngredients(holder.value());
        assertEquals(9, ingredients.size(), "Nine diamonds must not collapse into one ingredient");
        assertTrue(ingredients.stream().allMatch(ingredient -> ingredient.test(new ItemStack(Items.DIAMOND))));
        ItemStack result = RecipeCompat.result(holder.value(), server.registryAccess());
        assertTrue(result.is(Items.DIAMOND_BLOCK));
        assertEquals(1, result.getCount());
    }

    @Test
    void shapelessResultsKeepTheirCountAndTagIngredients(MinecraftServer server) {
        var holder = recipe(server, "minecraft:oak_planks");
        ItemStack result = RecipeCompat.result(holder.value(), server.registryAccess());
        assertTrue(result.is(Items.OAK_PLANKS));
        assertEquals(4, result.getCount());
        var ingredients = RecipeSupport.getNonEmptyIngredients(holder.value());
        assertEquals(1, ingredients.size());
        assertTrue(ingredients.getFirst().test(new ItemStack(Items.OAK_LOG)));
        assertTrue(RecipeCompat.items(ingredients.getFirst()).length > 0);
        assertEquals(ResourceLocation.parse("minecraft:oak_planks"), RecipeCompat.id(holder));
    }

    @Test
    void bagsAndTheirConditionalRecipesLoad(MinecraftServer server) {
        assertInstanceOf(CategorizedBagItem.class, ModItems.SEED_BAG.get());
        assertInstanceOf(CategorizedBagItem.class, ModItems.DYE_BAG.get());
        assertInstanceOf(CategorizedBagItem.class, ModItems.MINERAL_BAG.get());
        for (String name : new String[] {"seed_bag", "dye_bag", "mineral_bag"}) {
            for (String suffix : new String[] {"", "_from_bundle"}) {
                var holder = recipe(server, "convenientcrafting:" + name + suffix);
                ItemStack result = RecipeCompat.result(holder.value(), server.registryAccess());
                assertEquals("convenientcrafting:" + name,
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
                assertFalse(RecipeSupport.getNonEmptyIngredients(holder.value()).isEmpty());
            }
        }
    }

    @Test
    void craftingRemaindersAndBagNestingRulesSurviveThePort(MinecraftServer server) {
        assertTrue(ItemCompat.craftingRemainder(new ItemStack(Items.MILK_BUCKET)).is(Items.BUCKET));
        assertTrue(ItemCompat.craftingRemainder(new ItemStack(Items.DIAMOND)).isEmpty());
        ItemStack bag = ModItems.SEED_BAG.toStack();
        assertFalse(ItemCompat.canFitInsideContainer(bag), "Bags must not be nestable");
        var item = (CategorizedBagItem) bag.getItem();
        assertEquals(8, item.insert(bag, new ItemStack(Items.WHEAT_SEEDS, 8)));
        assertEquals(8, CategorizedBagItem.getContents(bag).getFirst().getCount());
        assertFalse(item.accepts(new ItemStack(Items.DIAMOND)));
    }

    @Test
    void workstationInputsAndOutputsSurviveThePort(MinecraftServer server) {
        var stonecutting = recipe(server, "minecraft:stone_slab_from_stone_stonecutting").value();
        ItemStack slabs = RecipeCompat.result(stonecutting, server.registryAccess());
        assertTrue(slabs.is(Items.STONE_SLAB));
        assertEquals(2, slabs.getCount());
        assertEquals(1, RecipeSupport.getNonEmptyIngredients(stonecutting).size());

        var smithing = (net.minecraft.world.item.crafting.SmithingRecipe)
                recipe(server, "minecraft:netherite_sword_smithing").value();
        assertTrue(RecipeCompat.isTemplateIngredient(smithing, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)));
        assertTrue(RecipeCompat.isBaseIngredient(smithing, new ItemStack(Items.DIAMOND_SWORD)));
        assertTrue(RecipeCompat.isAdditionIngredient(smithing, new ItemStack(Items.NETHERITE_INGOT)));
        assertFalse(RecipeCompat.isAdditionIngredient(smithing, new ItemStack(Items.DIAMOND)));
        ItemStack sword = smithing.assemble(new net.minecraft.world.item.crafting.SmithingRecipeInput(
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.NETHERITE_INGOT)), server.registryAccess());
        assertTrue(sword.is(Items.NETHERITE_SWORD));
    }

    //? if >=1.21.4 {
    /*@Test
    void fullWorkstationRecipesAreRequestedForClientSync(MinecraftServer server) {
        var event = new net.neoforged.neoforge.event.OnDatapackSyncEvent(server.getPlayerList(), null);
        // Ephemeral servers load datapacks without an overworld; do not fire unrelated
        // NeoForge world-dependent listeners with a synthetic event.
        com.adore.convenientcrafting.recipe.RecipeSynchronization.onDatapackSync(event);
        assertTrue(event.getRecipeTypesToSend().contains(net.minecraft.world.item.crafting.RecipeType.CRAFTING));
        assertTrue(event.getRecipeTypesToSend().contains(net.minecraft.world.item.crafting.RecipeType.SMITHING));
        assertTrue(event.getRecipeTypesToSend().contains(net.minecraft.world.item.crafting.RecipeType.STONECUTTING));
    }
    *///?}

    private static RecipeHolder<?> recipe(MinecraftServer server, String id) {
        return RecipeCompat.byId(server.getRecipeManager(), ResourceLocation.parse(id))
                .orElseThrow(() -> new AssertionError("Recipe did not load: " + id));
    }
}
