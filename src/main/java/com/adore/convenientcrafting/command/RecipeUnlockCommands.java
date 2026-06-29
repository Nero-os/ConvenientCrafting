package com.adore.convenientcrafting.command;

import com.adore.convenientcrafting.config.Config;
import com.adore.convenientcrafting.recipe.RecipeSupport;
import com.adore.convenientcrafting.recipe.adapter.RecipeTypeAdapters;
import com.adore.convenientcrafting.recipe.unlock.RecipeUnlocks;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RecipeUnlockCommands {
    private static final int MAX_RESULT_DIAGNOSTICS = 20;

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
                        .then(Commands.literal("recipeTypes")
                                .executes(this::recipeTypes))
                        .then(Commands.literal("diagnoseType")
                                .then(Commands.argument("recipe_type", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                getRecipeTypeSuggestions().stream().map(ResourceLocation::toString),
                                                builder))
                                        .executes(this::diagnoseType)))
                        .then(Commands.literal("diagnoseHeld")
                                .executes(this::diagnoseHeld))
                        .then(Commands.literal("diagnoseResult")
                                .then(Commands.argument("item_id", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString),
                                                builder))
                                        .executes(this::diagnoseResult)))
                        .then(Commands.literal("diagnoseRecipe")
                                .then(Commands.argument("recipe_id", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                context.getSource().getServer().getRecipeManager().getRecipes().stream()
                                                        .map(holder -> holder.id().toString()),
                                                builder))
                                        .executes(this::diagnoseRecipe)))
        );
    }

    private int unlockAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        List<ResourceLocation> recipeTypeIds = RecipeUnlocks.getUnlockableRecipeTypeIds();
        if (recipeTypeIds.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.convenientcrafting.unlock.none"));
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
                () -> Component.translatable("commands.convenientcrafting.unlock.all.success", typeCount, totalChangedCount, targetCount),
                true
        );
        return targetCount;
    }

    private int unlockType(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
                () -> Component.translatable("commands.convenientcrafting.unlock.type.success", recipeTypeId.toString(), targetCount, totalChangedCount),
                true
        );
        return targetCount;
    }

    private int recipeTypes(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        Map<ResourceLocation, List<ResourceLocation>> unlockRules = RecipeUnlocks.getUnlockRulesSnapshot();
        Map<ResourceLocation, Integer> recipeCounts = countRecipesByType(source.getServer());
        List<ResourceLocation> typeIds = getConfiguredRecipeTypeIds();

        send(source, "commands.convenientcrafting.diagnostics.recipe_types.header", typeIds.size());
        for (ResourceLocation typeId : typeIds) {
            send(source,
                    "commands.convenientcrafting.diagnostics.recipe_types.entry",
                    typeId.toString(),
                    yesNo(RecipeSupport.isBuiltInRecipeType(typeId)),
                    yesNo(isTypeConfigured(typeId)),
                    yesNo(unlockRules.containsKey(typeId)),
                    playerUnlockedText(player, typeId),
                    recipeCounts.getOrDefault(typeId, 0));
        }
        return typeIds.size();
    }

    private int diagnoseType(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ResourceLocation typeId = ResourceLocationArgument.getId(context, "recipe_type");
        ServerPlayer player = source.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        Map<ResourceLocation, List<ResourceLocation>> unlockRules = RecipeUnlocks.getUnlockRulesSnapshot();
        RecipeTypeDiagnostics diagnostics = diagnoseType(source.getServer(), typeId);

        send(source, "commands.convenientcrafting.diagnostics.type.header", typeId.toString());
        send(source, "commands.convenientcrafting.diagnostics.type.registered", yesNo(isRecipeTypeRegistered(typeId)));
        send(source, "commands.convenientcrafting.diagnostics.type.built_in", yesNo(RecipeSupport.isBuiltInRecipeType(typeId)));
        send(source, "commands.convenientcrafting.diagnostics.type.configured", yesNo(isTypeConfigured(typeId)));
        send(source, "commands.convenientcrafting.diagnostics.type.unlock_rule", formatUnlockItems(unlockRules.get(typeId)));
        send(source, "commands.convenientcrafting.diagnostics.type.player_unlocked", playerUnlockedText(player, typeId));
        send(source, "commands.convenientcrafting.diagnostics.type.recipe_count", diagnostics.recipeCount());
        send(source, "commands.convenientcrafting.diagnostics.type.simple_count", diagnostics.simpleCompatibleCount());
        send(source, "commands.convenientcrafting.diagnostics.type.note", supportNote(typeId));
        return diagnostics.recipeCount();
    }

    private int diagnoseHeld(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.convenientcrafting.diagnostics.held.empty"));
            return 0;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        return diagnoseResult(context.getSource(), itemId, "commands.convenientcrafting.diagnostics.held.header");
    }

    private int diagnoseResult(CommandContext<CommandSourceStack> context) {
        ResourceLocation itemId = ResourceLocationArgument.getId(context, "item_id");
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(itemId);
        if (item.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.convenientcrafting.diagnostics.result.unknown_item", itemId.toString()));
            return 0;
        }

        return diagnoseResult(context.getSource(), itemId, "commands.convenientcrafting.diagnostics.result.header");
    }

    private int diagnoseResult(CommandSourceStack source, ResourceLocation itemId, String headerKey) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        List<RecipeHolder<?>> matches = findRecipesByResult(source.getServer(), itemId);
        send(source, headerKey, itemId.toString());
        send(source, "commands.convenientcrafting.diagnostics.result.count", matches.size());

        HolderLookup.Provider registries = source.getServer().registryAccess();
        matches.stream()
                .limit(MAX_RESULT_DIAGNOSTICS)
                .forEach(holder -> sendRecipeSummary(source, holder, registries, player));
        if (matches.size() > MAX_RESULT_DIAGNOSTICS) {
            send(source, "commands.convenientcrafting.diagnostics.result.truncated", MAX_RESULT_DIAGNOSTICS, matches.size());
        }
        return matches.size();
    }

    private int diagnoseRecipe(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ResourceLocation recipeId = ResourceLocationArgument.getId(context, "recipe_id");
        Optional<RecipeHolder<?>> holder = source.getServer().getRecipeManager().getRecipes().stream()
                .filter(candidate -> candidate.id().equals(recipeId))
                .findFirst();

        if (holder.isEmpty()) {
            source.sendFailure(Component.translatable("commands.convenientcrafting.diagnostics.recipe.not_found", recipeId.toString()));
            return 0;
        }

        ServerPlayer player = source.getEntity() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        HolderLookup.Provider registries = source.getServer().registryAccess();
        Recipe<?> recipe = holder.get().value();
        ResourceLocation typeId = RecipeSupport.getRecipeTypeId(recipe);
        ItemStack result = recipe.getResultItem(registries);

        send(source, "commands.convenientcrafting.diagnostics.recipe.header", holder.get().id().toString());
        send(source, "commands.convenientcrafting.diagnostics.recipe.type", formatId(typeId));
        send(source, "commands.convenientcrafting.diagnostics.recipe.registered_type", yesNo(typeId != null && isRecipeTypeRegistered(typeId)));
        send(source, "commands.convenientcrafting.diagnostics.recipe.built_in", yesNo(typeId != null && RecipeSupport.isBuiltInRecipeType(typeId)));
        send(source, "commands.convenientcrafting.diagnostics.recipe.configured", yesNo(typeId != null && isTypeConfigured(typeId)));
        send(source, "commands.convenientcrafting.diagnostics.recipe.player_unlocked", typeId == null ? "no" : playerUnlockedText(player, typeId));
        send(source, "commands.convenientcrafting.diagnostics.recipe.result", formatItemStack(result));
        send(source, "commands.convenientcrafting.diagnostics.recipe.ingredient_count", RecipeSupport.getNonEmptyIngredients(recipe).size());
        send(source, "commands.convenientcrafting.diagnostics.recipe.simple", yesNo(isSimpleCompatible(recipe, registries)));
        send(source, "commands.convenientcrafting.diagnostics.recipe.supported_display", yesNo(isSupportedForDisplay(player, recipe, registries)));
        return 1;
    }

    private static void sendRecipeSummary(CommandSourceStack source, RecipeHolder<?> holder, HolderLookup.Provider registries, ServerPlayer player) {
        Recipe<?> recipe = holder.value();
        ResourceLocation typeId = RecipeSupport.getRecipeTypeId(recipe);
        ItemStack result = recipe.getResultItem(registries);
        send(source,
                "commands.convenientcrafting.diagnostics.result.entry",
                holder.id().toString(),
                formatId(typeId),
                yesNo(typeId != null && isTypeConfigured(typeId)),
                typeId == null ? "no" : playerUnlockedText(player, typeId),
                yesNo(!result.isEmpty()),
                RecipeSupport.getNonEmptyIngredients(recipe).size(),
                yesNo(isSimpleCompatible(recipe, registries)));
    }

    private static List<ResourceLocation> getConfiguredRecipeTypeIds() {
        Set<ResourceLocation> typeIds = new LinkedHashSet<>();
        parseTypeList(Config.ENABLED_BUILTIN_RECIPE_TYPES.get()).forEach(typeIds::add);
        parseTypeList(Config.ADDITIONAL_RECIPE_TYPES.get()).forEach(typeIds::add);
        getConfiguredUnlockRuleTypeIds().forEach(typeIds::add);
        return typeIds.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    private static Set<ResourceLocation> getConfiguredUnlockRuleTypeIds() {
        Set<ResourceLocation> typeIds = new LinkedHashSet<>();
        for (String rule : Config.RECIPE_TYPE_UNLOCK_ITEMS.get()) {
            String[] parts = rule.split("=", 2);
            if (parts.length == 2) {
                ResourceLocation typeId = ResourceLocation.tryParse(parts[0].trim());
                if (typeId != null) {
                    typeIds.add(typeId);
                }
            }
        }
        return typeIds;
    }

    private static List<ResourceLocation> getRecipeTypeSuggestions() {
        Set<ResourceLocation> suggestions = new LinkedHashSet<>();
        suggestions.addAll(BuiltInRegistries.RECIPE_TYPE.keySet());
        parseTypeList(Config.ADDITIONAL_RECIPE_TYPES.get()).forEach(suggestions::add);
        getConfiguredUnlockRuleTypeIds().forEach(suggestions::add);
        return suggestions.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    private static List<ResourceLocation> parseTypeList(List<? extends String> values) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (String value : values) {
            ResourceLocation id = ResourceLocation.tryParse(value.trim());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static Map<ResourceLocation, Integer> countRecipesByType(MinecraftServer server) {
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation typeId = RecipeSupport.getRecipeTypeId(holder.value());
            if (typeId != null) {
                counts.merge(typeId, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static RecipeTypeDiagnostics diagnoseType(MinecraftServer server, ResourceLocation typeId) {
        HolderLookup.Provider registries = server.registryAccess();
        int recipeCount = 0;
        int simpleCompatibleCount = 0;
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (typeId.equals(RecipeSupport.getRecipeTypeId(holder.value()))) {
                recipeCount++;
                if (isSimpleCompatible(holder.value(), registries)) {
                    simpleCompatibleCount++;
                }
            }
        }
        return new RecipeTypeDiagnostics(recipeCount, simpleCompatibleCount);
    }

    private static List<RecipeHolder<?>> findRecipesByResult(MinecraftServer server, ResourceLocation itemId) {
        HolderLookup.Provider registries = server.registryAccess();
        return server.getRecipeManager().getRecipes().stream()
                .filter(holder -> itemId.equals(BuiltInRegistries.ITEM.getKey(holder.value().getResultItem(registries).getItem())))
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }

    private static boolean isSimpleCompatible(Recipe<?> recipe, HolderLookup.Provider registries) {
        return !recipe.getResultItem(registries).isEmpty()
                && !RecipeSupport.getNonEmptyIngredients(recipe).isEmpty();
    }

    private static boolean isSupportedForDisplay(ServerPlayer player, Recipe<?> recipe, HolderLookup.Provider registries) {
        boolean builtInSupported = RecipeSupport.isBuiltInSupported(recipe)
                && !recipe.getResultItem(registries).isEmpty();
        if (player == null) {
            return builtInSupported
                    || RecipeSupport.isConfiguredSimpleRecipe(recipe, registries);
        }

        return RecipeSupport.isUnlockedFor(player, recipe)
                && (builtInSupported
                || RecipeSupport.isConfiguredSimpleRecipeFor(player, recipe, registries));
    }

    private static boolean isTypeConfigured(ResourceLocation typeId) {
        if (typeId == null) {
            return false;
        }

        String normalized = normalize(typeId);
        return Config.ENABLED_BUILTIN_RECIPE_TYPES.get().stream()
                .map(RecipeUnlockCommands::normalize)
                .anyMatch(normalized::equals)
                || Config.ADDITIONAL_RECIPE_TYPES.get().stream()
                .map(RecipeUnlockCommands::normalize)
                .anyMatch(normalized::equals)
                || getConfiguredUnlockRuleTypeIds().stream()
                .map(RecipeUnlockCommands::normalize)
                .anyMatch(normalized::equals);
    }

    private static boolean isRecipeTypeRegistered(ResourceLocation typeId) {
        return BuiltInRegistries.RECIPE_TYPE.containsKey(typeId);
    }

    private static String playerUnlockedText(ServerPlayer player, ResourceLocation typeId) {
        return player == null ? "not player" : yesNo(RecipeUnlocks.isUnlocked(player, typeId));
    }

    private static String supportNote(ResourceLocation typeId) {
        if (!RecipeSupport.isBuiltInRecipeType(typeId)) {
            return "additional recipe types use simple recipe compatibility: non-empty result and non-empty ingredients";
        }
        if (RecipeTypeAdapters.BREWING.equals(typeId)) {
            return "built-in brewing support is handled by Convenient Crafting brewing logic, not regular RecipeManager recipes";
        }
        return "built-in workstation support uses the existing Convenient Crafting adapter logic";
    }

    private static String formatUnlockItems(List<ResourceLocation> unlockItems) {
        if (unlockItems == null || unlockItems.isEmpty()) {
            return "none";
        }
        return unlockItems.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    private static String formatItemStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String id = itemId != null ? itemId.toString() : "unknown";
        return id + " x" + stack.getCount();
    }

    private static String formatId(ResourceLocation id) {
        return id == null ? "unknown" : id.toString();
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String normalize(ResourceLocation id) {
        return id.toString().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private static void send(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable(key, args), false);
    }

    private record RecipeTypeDiagnostics(int recipeCount, int simpleCompatibleCount) {
    }
}
