package com.adore.convenientcrafting.network;

import com.adore.convenientcrafting.ConvenientCrafting;
import com.adore.convenientcrafting.recipe.BrewingRecipeSupport;
import com.adore.convenientcrafting.recipe.RecipeSupport;
import com.adore.convenientcrafting.recipe.unlock.RecipeUnlocks;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;
import java.util.function.Predicate;

/**
 * 客户端请求服务端执行一次配方合成的数据包。
 *
 * @param recipeId 要合成的配方 ID
 */
public record CraftRecipePacket(ResourceLocation recipeId, int craftCount, boolean craftNested) implements CustomPacketPayload {
    public static final int MAX_BATCH_CRAFTS = 64;

    /**
     * 数据包类型标识。
     */
    public static final Type<CraftRecipePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ConvenientCrafting.MODID, "craft_recipe")
    );

    /**
     * 配方合成请求的数据包编解码器。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftRecipePacket> STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            CraftRecipePacket::recipeId,
            ByteBufCodecs.VAR_INT,
            CraftRecipePacket::craftCount,
            ByteBufCodecs.BOOL,
            CraftRecipePacket::craftNested,
            CraftRecipePacket::new
        );

    /**
     * 获取 NeoForge 自定义数据包类型。
     *
     * @return 当前数据包类型
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 在服务端线程处理客户端的合成请求。
     *
     * @param message 客户端发来的合成请求
     * @param context 数据包处理上下文
     */
    public static void handleServer(CraftRecipePacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                craftRecipe(player, message.recipeId(), message.craftCount(), message.craftNested());
            }
        });
    }

    /**
     * 尝试为玩家合成指定配方。
     *
     * <p>该方法会按顺序验证配方是否存在、产物是否有效、材料是否足够、
     * 背包是否有空间，然后才真正扣除材料并发放产物。</p>
     *
     * @param player 发起合成请求的玩家
     * @param recipeId 要合成的配方 ID
     */
    private static void craftRecipe(ServerPlayer player, ResourceLocation recipeId, int requestedCraftCount, boolean craftNested) {
        var server = player.getServer();
        if (server == null) return;

        int craftCount = Math.min(requestedCraftCount, MAX_BATCH_CRAFTS);
        if (craftCount <= 0) return;

        if (craftNested) {
            craftNestedRecipe(player, recipeId);
            return;
        }

        boolean craftedAny = false;

        if (BrewingRecipeSupport.isBrewingRecipeId(recipeId)) {
            for (int i = 0; i < craftCount; i++) {
                if (!finishCrafting(player, buildBrewingCraftingResult(player, recipeId))) {
                    break;
                }
                craftedAny = true;
            }
            if (craftedAny) {
                player.containerMenu.broadcastChanges();
            }
            return;
        }

        var optional = server.getRecipeManager().byKey(recipeId);
        if (optional.isEmpty()) return;

        Recipe<?> recipe = optional.get().value();
        for (int i = 0; i < craftCount; i++) {
            if (!finishCrafting(player, buildCraftingResult(player, recipe))) {
                break;
            }
            craftedAny = true;
        }

        if (craftedAny) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static void craftNestedRecipe(ServerPlayer player, ResourceLocation recipeId) {
        var server = player.getServer();
        if (server == null) return;

        if (BrewingRecipeSupport.isBrewingRecipeId(recipeId)) {
            craftRecipe(player, recipeId, 1, false);
            return;
        }

        var optional = server.getRecipeManager().byKey(recipeId);
        if (optional.isEmpty()) return;

        RecipeHolder<?> targetHolder = optional.get();
        NestedCraftingSimulation simulation = simulateNestedCrafting(player, targetHolder);
        if (!simulation.success()) {
            PacketDistributor.sendToPlayer(player, new NestedCraftingMissingMaterialsPacket(buildMissingMaterialRows(player, targetHolder)));
            return;
        }

        InventoryBackup backup = InventoryBackup.capture(player);
        boolean craftedAll = true;
        for (ResourceLocation plannedRecipeId : simulation.plan()) {
            var planned = server.getRecipeManager().byKey(plannedRecipeId);
            if (planned.isEmpty() || !finishCrafting(player, buildCraftingResult(player, planned.get().value()))) {
                craftedAll = false;
                break;
            }
        }

        if (craftedAll) {
            player.containerMenu.broadcastChanges();
        } else {
            backup.restore(player);
            player.containerMenu.broadcastChanges();
        }
    }

    private static NestedCraftingSimulation simulateNestedCrafting(ServerPlayer player, RecipeHolder<?> targetHolder) {
        MaterialPool pool = MaterialPool.fromPlayer(player);
        List<ResourceLocation> plan = new ArrayList<>();
        MissingMaterials missing = new MissingMaterials();
        Set<ResourceLocation> visiting = new HashSet<>();

        boolean success = simulateRecipe(player, targetHolder, pool, plan, missing, visiting);
        if (!success) {
            return new NestedCraftingSimulation(false, List.of(), missing.toStacks());
        }

        return new NestedCraftingSimulation(true, plan, List.of());
    }

    /**
     * 为递归合成失败弹窗构建默认展开的材料树。
     *
     * <p>第一行固定为目标产物，后续行按照配方依赖逐层展开。
     * 能从玩家背包或当前容器中直接取得的材料正常显示，无法继续合成的叶子材料标记为缺失。</p>
     *
     * @param player 发起合成的玩家
     * @param targetHolder 目标配方
     * @return 可直接发送给客户端展示的树形行列表
     */
    private static List<NestedCraftingMissingMaterialsPacket.MissingMaterialRow> buildMissingMaterialRows(ServerPlayer player, RecipeHolder<?> targetHolder) {
        List<NestedCraftingMissingMaterialsPacket.MissingMaterialRow> rows = new ArrayList<>();
        MaterialPool pool = MaterialPool.fromPlayer(player);
        ItemStack target = targetHolder.value().getResultItem(player.registryAccess()).copy();
        if (!target.isEmpty()) {
            rows.add(new NestedCraftingMissingMaterialsPacket.MissingMaterialRow(target, false, 0));
        }
        buildMissingRowsForRecipe(player, targetHolder, pool, rows, new HashSet<>(), 1);
        return rows;
    }

    /**
     * 递归构建某个配方的材料树。
     *
     * <p>该方法会优先消耗已有材料；已有材料不足时，会寻找能产出该材料的工作台配方继续展开。
     * 如果某条中间配方路径也失败，则保留缺失数量最少的失败路径，方便玩家看到最接近成功的材料链。</p>
     *
     * @param player 发起合成的玩家
     * @param holder 当前正在展开的配方
     * @param pool 递归预检使用的材料池快照
     * @param rows 输出的树形行
     * @param visiting 当前递归栈中的配方 ID，用于避免套娃循环
     * @param depth 当前树深度
     * @return 当前配方是否能通过已有材料或递归合成满足
     */
    private static boolean buildMissingRowsForRecipe(
            ServerPlayer player,
            RecipeHolder<?> holder,
            MaterialPool pool,
            List<NestedCraftingMissingMaterialsPacket.MissingMaterialRow> rows,
            Set<ResourceLocation> visiting,
            int depth
    ) {
        Recipe<?> recipe = holder.value();
        if (!RecipeSupport.isUnlockedFor(player, recipe) || visiting.contains(holder.id())) {
            return false;
        }

        List<Ingredient> ingredients = RecipeSupport.getNonEmptyIngredients(recipe);
        if (ingredients.isEmpty()) {
            return false;
        }

        visiting.add(holder.id());
        boolean success = true;
        for (Ingredient ingredient : ingredients) {
            // 先尝试使用已有材料；这能让树里清楚区分“已有”和“缺失”的节点。
            ItemStack consumed = pool.consumeStack(ingredient);
            if (!consumed.isEmpty()) {
                rows.add(new NestedCraftingMissingMaterialsPacket.MissingMaterialRow(consumed, false, depth));
                continue;
            }

            MissingTreeAttempt bestFailedAttempt = null;
            // 已有材料不够时，尝试选择一个能产出该材料的中间配方继续展开。
            for (RecipeHolder<?> candidate : findNestedCraftingCandidates(player, ingredient)) {
                if (visiting.contains(candidate.id())) {
                    continue;
                }

                MaterialPool candidatePool = pool.copy();
                List<NestedCraftingMissingMaterialsPacket.MissingMaterialRow> candidateRows = new ArrayList<>();
                ItemStack candidateResult = candidate.value().getResultItem(player.registryAccess()).copy();
                if (!candidateResult.isEmpty()) {
                    candidateRows.add(new NestedCraftingMissingMaterialsPacket.MissingMaterialRow(candidateResult, false, depth));
                }

                boolean candidateSuccess = buildMissingRowsForRecipe(player, candidate, candidatePool, candidateRows, visiting, depth + 1)
                        && !candidatePool.consumeStack(ingredient).isEmpty();
                if (candidateSuccess) {
                    pool.copyFrom(candidatePool);
                    rows.addAll(candidateRows);
                    bestFailedAttempt = null;
                    break;
                }

                int missingCount = countMissingRows(candidateRows);
                if (bestFailedAttempt == null || missingCount < bestFailedAttempt.missingCount()) {
                    bestFailedAttempt = new MissingTreeAttempt(candidateRows, missingCount);
                }
            }

            if (bestFailedAttempt != null && !bestFailedAttempt.rows().isEmpty()) {
                rows.addAll(bestFailedAttempt.rows());
            } else {
                // 没有可展开的中间配方时，这个材料就是最终缺失的原材料。
                rows.add(new NestedCraftingMissingMaterialsPacket.MissingMaterialRow(getIngredientDisplayStack(ingredient), true, depth));
            }
            success = false;
        }
        visiting.remove(holder.id());

        if (success) {
            ItemStack result = recipe.getResultItem(player.registryAccess()).copy();
            pool.add(result);
        }
        return success;
    }

    /**
     * 统计树形行中缺失材料的数量，用于选择更有参考价值的失败路径。
     */
    private static int countMissingRows(List<NestedCraftingMissingMaterialsPacket.MissingMaterialRow> rows) {
        int count = 0;
        for (NestedCraftingMissingMaterialsPacket.MissingMaterialRow row : rows) {
            if (row.missing()) {
                count += Math.max(1, row.stack().getCount());
            }
        }
        return count;
    }

    /**
     * 从材料条件中选取一个代表物品用于缺失提示。
     */
    private static ItemStack getIngredientDisplayStack(Ingredient ingredient) {
        ItemStack[] options = ingredient.getItems();
        return options.length > 0 ? options[0].copyWithCount(1) : ItemStack.EMPTY;
    }

    private static boolean simulateRecipe(
            ServerPlayer player,
            RecipeHolder<?> holder,
            MaterialPool pool,
            List<ResourceLocation> plan,
            MissingMaterials missing,
            Set<ResourceLocation> visiting
    ) {
        Recipe<?> recipe = holder.value();
        if (!RecipeSupport.isUnlockedFor(player, recipe) || visiting.contains(holder.id())) {
            return false;
        }

        List<Ingredient> ingredients = RecipeSupport.getNonEmptyIngredients(recipe);
        if (ingredients.isEmpty()) return false;

        ItemStack result = recipe.getResultItem(player.registryAccess()).copy();
        if (result.isEmpty()) return false;

        visiting.add(holder.id());
        boolean success = true;
        for (Ingredient ingredient : ingredients) {
            if (!satisfyIngredient(player, ingredient, pool, plan, missing, visiting)) {
                success = false;
                break;
            }
        }
        visiting.remove(holder.id());

        if (!success) return false;

        plan.add(holder.id());
        pool.add(result);
        return true;
    }

    private static boolean satisfyIngredient(
            ServerPlayer player,
            Ingredient ingredient,
            MaterialPool pool,
            List<ResourceLocation> plan,
            MissingMaterials missing,
            Set<ResourceLocation> visiting
    ) {
        if (pool.consume(ingredient)) {
            return true;
        }

        MissingMaterials bestMissing = null;
        for (RecipeHolder<?> candidate : findNestedCraftingCandidates(player, ingredient)) {
            if (visiting.contains(candidate.id())) {
                continue;
            }

            MaterialPool candidatePool = pool.copy();
            List<ResourceLocation> candidatePlan = new ArrayList<>(plan);
            MissingMaterials candidateMissing = new MissingMaterials();

            if (simulateRecipe(player, candidate, candidatePool, candidatePlan, candidateMissing, visiting)
                    && candidatePool.consume(ingredient)) {
                pool.copyFrom(candidatePool);
                plan.clear();
                plan.addAll(candidatePlan);
                return true;
            }

            if (!candidateMissing.isEmpty()
                    && (bestMissing == null || candidateMissing.totalCount() < bestMissing.totalCount())) {
                bestMissing = candidateMissing;
            }
        }

        if (bestMissing != null) {
            missing.addAll(bestMissing);
        } else {
            missing.addIngredient(ingredient);
        }
        return false;
    }

    private static List<RecipeHolder<?>> findNestedCraftingCandidates(ServerPlayer player, Ingredient targetIngredient) {
        var server = player.getServer();
        if (server == null) return List.of();

        List<RecipeHolder<?>> candidates = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe craftingRecipe)) {
                continue;
            }

            if (!RecipeSupport.isUnlockedFor(player, craftingRecipe)) {
                continue;
            }

            ItemStack result = craftingRecipe.getResultItem(server.registryAccess());
            if (!result.isEmpty() && targetIngredient.test(result)) {
                candidates.add(holder);
            }
        }

        candidates.sort(Comparator.comparing(holder -> holder.id().toString()));
        return candidates;
    }

    private static boolean finishCrafting(ServerPlayer player, CraftingResult craftingResult) {
        if (craftingResult == null || craftingResult.results().isEmpty()) return false;

        // Check if there's enough space in inventory for the result
        if (!hasSpaceForResults(player.getInventory(), craftingResult.results(), craftingResult.ingredients())) return false;

        // Consume ingredients
        if (!consumeIngredients(player, craftingResult.ingredients())) return false;

        // Give result
        for (ItemStack result : craftingResult.results()) {
            player.getInventory().add(result.copy());
        }

        return true;
    }

    /**
     * 根据配方类型构建实际合成产物和需要消耗的材料。
     *
     * @param player 发起合成的玩家
     * @param recipe 要执行的配方
     * @return 可执行的合成结果；无法执行时返回 {@code null}
     */
    private static CraftingResult buildCraftingResult(ServerPlayer player, Recipe<?> recipe) {
        var server = player.getServer();
        if (server == null) return null;
        if (!RecipeSupport.isUnlockedFor(player, recipe)) return null;

        if (recipe instanceof CraftingRecipe craftingRecipe) {
            ItemStack result = craftingRecipe.getResultItem(server.registryAccess()).copy();
            List<Ingredient> ingredients = RecipeSupport.getNonEmptyIngredients(craftingRecipe);
            List<IngredientUse> matchedIngredients = matchIngredients(player, ingredients);
            if (matchedIngredients.isEmpty()) return null;
            return result.isEmpty() ? null : CraftingResult.single(result, matchedIngredients);
        }

        if (recipe instanceof SmithingRecipe smithingRecipe) {
            SmithingMatch match = findSmithingMatch(player, smithingRecipe);
            if (match == null) return null;

            SmithingRecipeInput input = new SmithingRecipeInput(match.template().stack(), match.base().stack(), match.addition().stack());
            if (!smithingRecipe.matches(input, player.level())) return null;

            ItemStack result = smithingRecipe.assemble(input, server.registryAccess());
            if (result.isEmpty()) return null;

            return CraftingResult.single(result, List.of(
                    match.template(),
                    match.base(),
                    match.addition()
            ));
        }

        if (RecipeSupport.isConfiguredSimpleRecipe(recipe, server.registryAccess())) {
            ItemStack result = recipe.getResultItem(server.registryAccess()).copy();
            List<IngredientUse> matchedIngredients = matchIngredients(player, RecipeSupport.getNonEmptyIngredients(recipe));
            if (matchedIngredients.isEmpty()) return null;
            return result.isEmpty() ? null : CraftingResult.single(result, matchedIngredients);
        }

        return null;
    }

    /**
     * 将配方材料匹配为背包中的实际物品。
     *
     * <p>工作台配方的 {@link Ingredient} 可能接受多个物品。这里会先加入玩家背包物品，
     * 再加入当前打开容器的物品，所以匹配天然优先使用玩家背包，不够时才使用箱子等容器。</p>
     *
     * @param player 发起合成的玩家
     * @param ingredients 配方材料列表
     * @return 实际匹配到的材料；无法全部匹配时返回空列表
     */
    private static CraftingResult buildBrewingCraftingResult(ServerPlayer player, ResourceLocation recipeId) {
        if (!RecipeUnlocks.isBuiltinRecipeTypeEnabled(BrewingRecipeSupport.RECIPE_TYPE_ID)) return null;
        if (!RecipeUnlocks.isUnlocked(player, BrewingRecipeSupport.RECIPE_TYPE_ID)) return null;

        Optional<BrewingRecipeSupport.BrewingRecipeKey> optionalKey = BrewingRecipeSupport.parseRecipeId(recipeId);
        if (optionalKey.isEmpty()) return null;

        BrewingRecipeSupport.BrewingRecipeKey key = optionalKey.get();
        ItemStack expectedInput = BrewingRecipeSupport.createPotionStack(player.registryAccess(), key.containerId(), key.potionId());
        ItemStack expectedIngredient = BrewingRecipeSupport.createIngredientStack(key.ingredientId());
        if (expectedInput.isEmpty() || expectedIngredient.isEmpty()) return null;

        PotionBrewing potionBrewing = player.level().potionBrewing();
        ItemStack expectedResult = potionBrewing.mix(expectedIngredient, expectedInput);
        if (expectedResult.isEmpty()) return null;

        List<AvailableMaterial> available = getAvailableMaterials(player);
        IngredientUse ingredient = takeFirstMatching(available, stack -> stack.is(expectedIngredient.getItem()));
        if (ingredient == null) return null;

        List<IngredientUse> inputs = new ArrayList<>();
        List<ItemStack> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            IngredientUse input = takeFirstMatching(available, stack -> ItemStack.isSameItemSameComponents(stack, expectedInput));
            if (input == null) {
                break;
            }

            if (!potionBrewing.hasMix(input.stack(), ingredient.stack())) {
                continue;
            }

            ItemStack result = potionBrewing.mix(ingredient.stack(), input.stack());
            if (!result.isEmpty() && ItemStack.isSameItemSameComponents(result, expectedResult)) {
                inputs.add(input);
                results.add(result.copyWithCount(1));
            }
        }

        if (inputs.isEmpty()) return null;

        List<IngredientUse> consumed = new ArrayList<>();
        consumed.add(ingredient);
        consumed.addAll(inputs);
        return new CraftingResult(results, consumed);
    }

    private static List<IngredientUse> matchIngredients(ServerPlayer player, List<Ingredient> ingredients) {
        if (ingredients.isEmpty()) return List.of();

        List<AvailableMaterial> available = getAvailableMaterials(player);
        List<IngredientUse> matchedIngredients = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            IngredientUse matched = takeFirstMatching(available, ingredient::test);
            if (matched == null) {
                return List.of();
            }
            matchedIngredients.add(matched);
        }

        return matchedIngredients;
    }

    /**
     * 判断背包是否可以容纳配方产物。
     *
     * @param inventory 玩家背包
     * @param result 配方产物
     * @param consumedIngredients 将要消耗的材料
     * @return 现有堆叠或空槽足够容纳产物时返回 {@code true}
     */
    private static boolean hasSpaceForResults(Inventory inventory, List<ItemStack> results, List<IngredientUse> consumedIngredients) {
        List<ItemStack> simulatedInventory = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            simulatedInventory.add(inventory.getItem(i).copy());
        }

        // 只模拟扣除来自玩家背包的材料；箱子材料不会给玩家背包释放槽位。
        for (IngredientUse ingredient : consumedIngredients) {
            if (ingredient.source().playerInventory()) {
                simulatedInventory.get(ingredient.source().slotIndex()).shrink(1);
            }
        }

        for (ItemStack result : results) {
            if (result.isEmpty() || !simulateAddResult(simulatedInventory, result)) {
                return false;
            }
        }

        return true;
    }

    private static boolean simulateAddResult(List<ItemStack> simulatedInventory, ItemStack result) {
        int remaining = result.getCount();
        int maxStack = result.getMaxStackSize();

        // 先统计可合并到现有同类堆叠中的空间。
        for (ItemStack stack : simulatedInventory) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, result)) {
                int space = maxStack - stack.getCount();
                if (space > 0) {
                    int moved = Math.min(remaining, space);
                    stack.grow(moved);
                    remaining -= moved;
                    if (remaining <= 0) return true;
                }
            }
        }

        // 现有堆叠不够时，再把空槽按完整最大堆叠容量计入。
        for (int i = 0; i < simulatedInventory.size(); i++) {
            if (simulatedInventory.get(i).isEmpty()) {
                int moved = Math.min(remaining, maxStack);
                simulatedInventory.set(i, result.copyWithCount(moved));
                remaining -= moved;
                if (remaining <= 0) return true;
            }
        }

        return remaining <= 0;
    }

    /**
     * 从真实背包中扣除配方材料。
     *
     * <p>材料匹配阶段已经记录了具体来源，所以这里按来源槽位扣除即可。</p>
     *
     * @param player 发起合成的玩家
     * @param ingredients 要消耗的材料列表
     * @return 成功扣除全部材料时返回 {@code true}
     */
    private static boolean consumeIngredients(ServerPlayer player, List<IngredientUse> ingredients) {
        for (IngredientUse ingredient : ingredients) {
            ItemStack stack = getSourceStack(player, ingredient.source());
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, ingredient.stack())) {
                return false;
            }

            stack.shrink(1);
            if (stack.isEmpty()) {
                setSourceStack(player, ingredient.source(), ItemStack.EMPTY);
            } else {
                setSourceStack(player, ingredient.source(), stack);
            }
        }

        return true;
    }

    /**
     * 从背包中寻找一组可用于锻造台配方的模板、基础物品和追加材料。
     *
     * @param player 发起合成的玩家
     * @param recipe 锻造台配方
     * @return 匹配到的三槽输入；未匹配时返回 {@code null}
     */
    private static SmithingMatch findSmithingMatch(ServerPlayer player, SmithingRecipe recipe) {
        List<AvailableMaterial> available = getAvailableMaterials(player);

        IngredientUse template = takeFirstMatching(available, recipe::isTemplateIngredient);
        if (template == null) return null;

        IngredientUse base = takeFirstMatching(available, recipe::isBaseIngredient);
        if (base == null) return null;

        IngredientUse addition = takeFirstMatching(available, recipe::isAdditionIngredient);
        if (addition == null) return null;

        return new SmithingMatch(template, base, addition);
    }

    /**
     * 构建可用材料列表，顺序为玩家背包优先、当前打开容器其次。
     *
     * @param player 发起合成的玩家
     * @return 带来源信息的可变材料快照
     */
    private static List<AvailableMaterial> getAvailableMaterials(ServerPlayer player) {
        List<AvailableMaterial> available = new ArrayList<>();
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                available.add(new AvailableMaterial(new MaterialSource(true, i), stack.copy()));
            }
        }

        if (player.containerMenu != player.inventoryMenu) {
            for (int i = 0; i < player.containerMenu.slots.size(); i++) {
                Slot slot = player.containerMenu.slots.get(i);
                ItemStack stack = slot.getItem();
                if (slot.container != inventory && !stack.isEmpty() && !slot.isFake() && slot.mayPickup(player) && slot.mayPlace(stack)) {
                    available.add(new AvailableMaterial(new MaterialSource(false, i), stack.copy()));
                }
            }
        }

        return available;
    }

    /**
     * 从可变库存快照中取出第一个满足条件的物品。
     *
     * @param available 可变库存快照
     * @param matcher 材料匹配规则
     * @return 匹配到的材料；未匹配时返回 {@code null}
     */
    private static IngredientUse takeFirstMatching(List<AvailableMaterial> available, Predicate<ItemStack> matcher) {
        for (AvailableMaterial material : available) {
            if (!material.stack().isEmpty() && matcher.test(material.stack())) {
                ItemStack matched = material.stack().copyWithCount(1);
                material.stack().shrink(1);
                return new IngredientUse(matched, material.source());
            }
        }
        return null;
    }

    /**
     * 读取材料来源对应的真实物品堆。
     *
     * @param player 发起合成的玩家
     * @param source 材料来源
     * @return 来源槽位中的物品
     */
    private static ItemStack getSourceStack(ServerPlayer player, MaterialSource source) {
        if (source.playerInventory()) {
            return player.getInventory().getItem(source.slotIndex());
        }
        return player.containerMenu.getSlot(source.slotIndex()).getItem();
    }

    /**
     * 写回材料来源对应的真实物品堆。
     *
     * @param player 发起合成的玩家
     * @param source 材料来源
     * @param stack 要写回的物品堆
     */
    private static void setSourceStack(ServerPlayer player, MaterialSource source, ItemStack stack) {
        if (source.playerInventory()) {
            player.getInventory().setItem(source.slotIndex(), stack);
        } else {
            player.containerMenu.getSlot(source.slotIndex()).set(stack);
        }
    }

    private static String stackKey(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return (itemId != null ? itemId.toString() : "unknown:" + stack.getItem()) + stack.getComponentsPatch();
    }

    private static class MaterialPool {
        private final List<ItemStack> stacks;

        private MaterialPool(List<ItemStack> stacks) {
            this.stacks = stacks;
        }

        private static MaterialPool fromPlayer(ServerPlayer player) {
            List<ItemStack> stacks = new ArrayList<>();
            for (AvailableMaterial material : getAvailableMaterials(player)) {
                stacks.add(material.stack().copy());
            }
            return new MaterialPool(stacks);
        }

        private MaterialPool copy() {
            List<ItemStack> copied = new ArrayList<>();
            for (ItemStack stack : stacks) {
                copied.add(stack.copy());
            }
            return new MaterialPool(copied);
        }

        private void copyFrom(MaterialPool other) {
            stacks.clear();
            for (ItemStack stack : other.stacks) {
                stacks.add(stack.copy());
            }
        }

        private boolean consume(Ingredient ingredient) {
            return !consumeStack(ingredient).isEmpty();
        }

        private ItemStack consumeStack(Ingredient ingredient) {
            for (ItemStack stack : stacks) {
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    ItemStack consumed = stack.copyWithCount(1);
                    stack.shrink(1);
                    return consumed;
                }
            }
            return ItemStack.EMPTY;
        }

        private void add(ItemStack stack) {
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
    }

    private static class MissingMaterials {
        private final Map<String, ItemStack> missing = new LinkedHashMap<>();

        private void addIngredient(Ingredient ingredient) {
            ItemStack[] options = ingredient.getItems();
            if (options.length > 0) {
                addStack(options[0].copyWithCount(1));
            }
        }

        private void addStack(ItemStack stack) {
            if (stack.isEmpty()) return;

            String key = stackKey(stack);
            ItemStack existing = missing.get(key);
            if (existing == null) {
                missing.put(key, stack.copy());
            } else {
                existing.grow(stack.getCount());
            }
        }

        private void addAll(MissingMaterials other) {
            for (ItemStack stack : other.missing.values()) {
                addStack(stack);
            }
        }

        private boolean isEmpty() {
            return missing.isEmpty();
        }

        private int totalCount() {
            return missing.values().stream().mapToInt(ItemStack::getCount).sum();
        }

        private List<ItemStack> toStacks() {
            return new ArrayList<>(missing.values());
        }
    }

    private record NestedCraftingSimulation(boolean success, List<ResourceLocation> plan, List<ItemStack> missingMaterials) {
    }

    private record MissingTreeAttempt(List<NestedCraftingMissingMaterialsPacket.MissingMaterialRow> rows, int missingCount) {
    }

    private record InventoryBackup(List<ItemStack> playerInventory, List<ItemStack> menuSlots) {
        private static InventoryBackup capture(ServerPlayer player) {
            List<ItemStack> playerInventory = new ArrayList<>();
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                playerInventory.add(inventory.getItem(i).copy());
            }

            List<ItemStack> menuSlots = new ArrayList<>();
            for (Slot slot : player.containerMenu.slots) {
                menuSlots.add(slot.getItem().copy());
            }

            return new InventoryBackup(playerInventory, menuSlots);
        }

        private void restore(ServerPlayer player) {
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize() && i < playerInventory.size(); i++) {
                inventory.setItem(i, playerInventory.get(i).copy());
            }

            for (int i = 0; i < player.containerMenu.slots.size() && i < menuSlots.size(); i++) {
                player.containerMenu.getSlot(i).set(menuSlots.get(i).copy());
            }
        }
    }

    /**
     * 一次实际合成需要发放的产物和扣除的材料。
     *
     * @param result 合成产物
     * @param ingredients 需要扣除的材料
     */
    private record CraftingResult(List<ItemStack> results, List<IngredientUse> ingredients) {
        private static CraftingResult single(ItemStack result, List<IngredientUse> ingredients) {
            return new CraftingResult(List.of(result), ingredients);
        }
    }

    /**
     * 一份可变材料快照及其真实来源。
     *
     * @param source 真实来源
     * @param stack 可变物品副本
     */
    private record AvailableMaterial(MaterialSource source, ItemStack stack) {
    }

    /**
     * 实际匹配到的一份材料。
     *
     * @param stack 要消耗的物品
     * @param source 真实来源
     */
    private record IngredientUse(ItemStack stack, MaterialSource source) {
    }

    /**
     * 材料来源槽位。
     *
     * @param playerInventory {@code true} 表示玩家背包，{@code false} 表示当前容器菜单槽位
     * @param slotIndex 玩家背包槽位或容器菜单槽位索引
     */
    private record MaterialSource(boolean playerInventory, int slotIndex) {
    }

    /**
     * 锻造台三槽输入的实际匹配结果。
     *
     * @param template 模板槽物品
     * @param base 基础槽物品
     * @param addition 追加材料槽物品
     */
    private record SmithingMatch(IngredientUse template, IngredientUse base, IngredientUse addition) {
    }
}
