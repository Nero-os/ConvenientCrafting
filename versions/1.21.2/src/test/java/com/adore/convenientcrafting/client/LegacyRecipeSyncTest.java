package com.adore.convenientcrafting.client;

import com.adore.convenientcrafting.compat.RecipeCompat;
import com.adore.convenientcrafting.network.LegacyRecipeSyncPacket;

import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class LegacyRecipeSyncTest {
    @AfterEach
    void clearCatalogue() {
        ClientRecipes.clear();
    }

    @Test
    void fullRecipeCodecPreservesIdentifiersIngredientsAndResults(MinecraftServer server) {
        var packet = single(server, "minecraft:oak_planks");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            LegacyRecipeSyncPacket.STREAM_CODEC.encode(buffer, packet);
            var decoded = LegacyRecipeSyncPacket.STREAM_CODEC.decode(buffer);
            assertEquals(0, decoded.batchIndex());
            assertTrue(decoded.lastBatch());
            assertFalse(buffer.isReadable());
            // Wire decoding succeeds with no registries/tags. Recipe decoding happens later.
            assertTrue(decoded.data().length <= LegacyRecipeSyncPacket.MAX_BATCH_BYTES);
            var previous = ClientRecipes.get();
            LegacyRecipeCatalog.accept(decoded);
            assertSame(previous, ClientRecipes.get());
            assertTrue(LegacyRecipeCatalog.publishAfterTags(server.registryAccess()));
            var holder = ClientRecipes.get().getRecipes().iterator().next();
            assertEquals(ResourceLocation.parse("minecraft:oak_planks"), RecipeCompat.id(holder));
            assertEquals(1, RecipeCompat.ingredients(holder.value()).size());
            assertTrue(RecipeCompat.ingredients(holder.value()).getFirst().test(new net.minecraft.world.item.ItemStack(Items.OAK_LOG)));
            assertTrue(RecipeCompat.result(holder.value(), server.registryAccess()).is(Items.OAK_PLANKS));
        } finally {
            buffer.release();
        }
    }

    @Test
    void customIngredientComponentChecksRemainIntact(MinecraftServer server) {
        var name = net.minecraft.network.chat.Component.literal("Required ingredient name");
        var ingredient = net.neoforged.neoforge.common.crafting.DataComponentIngredient.of(
                false, net.minecraft.core.component.DataComponents.CUSTOM_NAME, name, Items.DIAMOND);
        var recipe = new net.minecraft.world.item.crafting.ShapelessRecipe("",
                net.minecraft.world.item.crafting.CraftingBookCategory.MISC,
                new net.minecraft.world.item.ItemStack(Items.EMERALD), List.of(ingredient));
        var holder = new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE, ResourceLocation.parse("convenientcrafting:component_test")), recipe);
        for (var batch : LegacyRecipeSyncPacket.batches(List.of(holder), server.registryAccess())) {
            LegacyRecipeCatalog.accept(batch);
        }
        assertTrue(LegacyRecipeCatalog.publishAfterTags(server.registryAccess()));
        var synced = RecipeCompat.ingredients(ClientRecipes.get().getRecipes().iterator().next().value()).getFirst();
        var matching = new net.minecraft.world.item.ItemStack(Items.DIAMOND);
        matching.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        assertTrue(synced.test(matching));
        assertFalse(synced.test(new net.minecraft.world.item.ItemStack(Items.DIAMOND)),
                "Sync must preserve component-sensitive custom ingredients");
    }

    @Test
    void largeCatalogueWaitsForAllBatchesAndFreshTags(MinecraftServer server) {
        var holder = recipe(server, "minecraft:oak_planks");
        var original = ClientRecipes.get();
        // Exceeds the old branch's single-packet 32,768-recipe catalogue limit.
        var recipes = Collections.<RecipeHolder<?>>nCopies(33_000, holder);
        var batches = LegacyRecipeSyncPacket.batches(recipes, server.registryAccess());
        for (var batch : batches) {
            LegacyRecipeCatalog.accept(batch);
            assertSame(original, ClientRecipes.get(), "Do not expose a partial catalogue or stale tags");
            if (!batch.lastBatch()) {
                assertFalse(LegacyRecipeCatalog.publishAfterTags(server.registryAccess()));
            }
        }
        assertTrue(LegacyRecipeCatalog.publishAfterTags(server.registryAccess()));
        assertEquals(recipes.size(), ClientRecipes.get().getRecipes().size());
        assertTrue(ClientRecipes.get().getRecipes().stream()
                .allMatch(entry -> RecipeCompat.id(entry).equals(RecipeCompat.id(holder))));
        assertFalse(LegacyRecipeCatalog.publishAfterTags(server.registryAccess()), "One tag update must publish at most once");
    }

    @Test
    void sameSizeReloadReplacesSnapshotAndEmptyReloadClearsIt(MinecraftServer server) {
        LegacyRecipeCatalog.accept(single(server, "minecraft:oak_planks"));
        assertTrue(LegacyRecipeCatalog.publishAfterTags(server.registryAccess()));
        var beforeReload = ClientRecipes.get();
        LegacyRecipeCatalog.accept(single(server, "minecraft:diamond_block"));
        assertTrue(LegacyRecipeCatalog.publishAfterTags(server.registryAccess()));
        assertNotSame(beforeReload, ClientRecipes.get());
        assertEquals(ResourceLocation.parse("minecraft:diamond_block"), RecipeCompat.id(ClientRecipes.get().getRecipes().iterator().next()));

        LegacyRecipeCatalog.accept(LegacyRecipeSyncPacket.batches(List.of(), server.registryAccess()).getFirst());
        assertTrue(LegacyRecipeCatalog.publishAfterTags(server.registryAccess()));
        assertTrue(ClientRecipes.get().getRecipes().isEmpty());
    }

    @Test
    void missingBatchAndDisconnectDiscardUnfinishedCatalogues(MinecraftServer server) {
        var packet = single(server, "minecraft:oak_planks");
        var original = ClientRecipes.get();
        LegacyRecipeCatalog.accept(new LegacyRecipeSyncPacket(0, false, packet.data()));
        LegacyRecipeCatalog.accept(new LegacyRecipeSyncPacket(2, true, packet.data()));
        assertFalse(LegacyRecipeCatalog.publishAfterTags(server.registryAccess()));
        assertSame(original, ClientRecipes.get());

        LegacyRecipeCatalog.accept(packet);
        ClientRecipes.clear();
        assertFalse(LegacyRecipeCatalog.publishAfterTags(server.registryAccess()));
        assertTrue(ClientRecipes.get().getRecipes().isEmpty());
    }

    private static LegacyRecipeSyncPacket single(MinecraftServer server, String id) {
        return LegacyRecipeSyncPacket.batches(List.of(recipe(server, id)), server.registryAccess()).getFirst();
    }

    private static RecipeHolder<?> recipe(MinecraftServer server, String id) {
        return RecipeCompat.byId(server.getRecipeManager(), ResourceLocation.parse(id)).orElseThrow();
    }
}
