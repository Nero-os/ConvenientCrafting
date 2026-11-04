package com.adore.convenientcrafting.recipe;

import com.adore.convenientcrafting.recipe.unlock.RecipeUnlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Requests full recipes through NeoForge after vanilla switched to display-only sync. */
public final class RecipeSynchronization {
    private RecipeSynchronization() {}

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        //? if >=1.21.4 {
        /*event.sendRecipes(RecipeUnlocks.getUnlockableRecipeTypeIds().stream()
                .map(BuiltInRegistries.RECIPE_TYPE::getOptional)
                .flatMap(java.util.Optional::stream)
                .toList());
        *///?} elif >=1.21.2 {
        /*var players = event.getRelevantPlayers().toList();
        if (players.isEmpty()) {
            return;
        }
        var types = new java.util.HashSet<>(RecipeUnlocks.getUnlockableRecipeTypeIds());
        var server = event.getPlayerList().getServer();
        var recipes = server.getRecipeManager().getRecipes().stream()
                .filter(holder -> types.contains(RecipeSupport.getRecipeTypeId(holder.value())))
                .toList();
        for (var batch : com.adore.convenientcrafting.network.LegacyRecipeSyncPacket.batches(recipes, server.registryAccess())) {
            for (var player : players) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, batch);
            }
        }
        *///?}
    }
}
