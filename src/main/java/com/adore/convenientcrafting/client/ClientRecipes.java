package com.adore.convenientcrafting.client;

import java.util.Collection;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeHolder;

/** A version-independent snapshot; replacing it also invalidates cached recipe indexes. */
public final class ClientRecipes {
    private static ClientRecipes snapshot = new ClientRecipes(null, List.of());
    private final Object source;
    private final Collection<RecipeHolder<?>> recipes;

    private ClientRecipes(Object source, Collection<RecipeHolder<?>> recipes) {
        this.source = source;
        this.recipes = List.copyOf(recipes);
    }

    public static ClientRecipes get() {
        //? if <1.21.2 {
        var level = Minecraft.getInstance().level;
        if (level != null && snapshot.source != level.getRecipeManager()) {
            snapshot = new ClientRecipes(level.getRecipeManager(), level.getRecipeManager().getRecipes());
        }
        //?}
        return snapshot;
    }

    public Collection<RecipeHolder<?>> getRecipes() {
        return recipes;
    }

    public static void replace(Object source, Collection<RecipeHolder<?>> recipes) {
        snapshot = new ClientRecipes(source, recipes);
    }

    public static void clear() {
        //? if >=1.21.2 && <1.21.4 {
        /*LegacyRecipeCatalog.reset();
        *///?}
        snapshot = new ClientRecipes(null, List.of());
    }
}
