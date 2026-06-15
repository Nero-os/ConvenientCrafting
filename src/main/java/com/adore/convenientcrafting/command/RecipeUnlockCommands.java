package com.adore.convenientcrafting.command;

import com.adore.convenientcrafting.recipe.unlock.RecipeUnlocks;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;

public final class RecipeUnlockCommands {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("convenientcrafting")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.literal("all")
                                                .executes(this::unlockAll))
                                        .then(Commands.argument("recipe_type", ResourceLocationArgument.id())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        RecipeUnlocks.getUnlockableRecipeTypeIds().stream()
                                                                .map(ResourceLocation::toString),
                                                        builder))
                                                .executes(this::unlockType))))
        );
    }

    private int unlockAll(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        List<ResourceLocation> recipeTypeIds = RecipeUnlocks.getUnlockableRecipeTypeIds();
        if (recipeTypeIds.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No Convenient Crafting recipe types are configured to unlock."));
            return 0;
        }

        int changedCount = 0;
        for (ServerPlayer target : targets) {
            changedCount += RecipeUnlocks.unlockRecipeTypes(target, recipeTypeIds);
        }

        int typeCount = recipeTypeIds.size();
        int totalChangedCount = changedCount;
        int targetCount = targets.size();
        context.getSource().sendSuccess(
                () -> Component.literal("Unlocked all Convenient Crafting recipe types ("
                        + typeCount + " known, " + totalChangedCount + " changed) for "
                        + targetCount + " player(s)."),
                true
        );
        return targetCount;
    }

    private int unlockType(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        ResourceLocation recipeTypeId = ResourceLocationArgument.getId(context, "recipe_type");

        int changedCount = 0;
        for (ServerPlayer target : targets) {
            if (RecipeUnlocks.unlockRecipeType(target, recipeTypeId)) {
                changedCount++;
            }
        }

        int totalChangedCount = changedCount;
        int targetCount = targets.size();
        context.getSource().sendSuccess(
                () -> Component.literal("Unlocked " + recipeTypeId + " for "
                        + targetCount + " player(s), " + totalChangedCount + " changed."),
                true
        );
        return targetCount;
    }
}
