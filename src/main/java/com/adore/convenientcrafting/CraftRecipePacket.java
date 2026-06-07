package com.adore.convenientcrafting;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;
import java.util.function.Predicate;

/**
 * 客户端请求服务端执行一次配方合成的数据包。
 *
 * @param recipeId 要合成的配方 ID
 */
public record CraftRecipePacket(ResourceLocation recipeId) implements CustomPacketPayload {

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
                craftRecipe(player, message.recipeId());
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
    private static void craftRecipe(ServerPlayer player, ResourceLocation recipeId) {
        var server = player.getServer();
        if (server == null) return;

        var optional = server.getRecipeManager().byKey(recipeId);
        if (optional.isEmpty()) return;

        Recipe<?> recipe = optional.get().value();
        CraftingResult craftingResult = buildCraftingResult(player, recipe);
        if (craftingResult == null || craftingResult.result().isEmpty()) return;

        ItemStack result = craftingResult.result();

        // Check if there's enough space in inventory for the result
        if (!hasSpaceForResult(player.getInventory(), result, craftingResult.ingredients())) return;

        // Consume ingredients
        if (!consumeIngredients(player, craftingResult.ingredients())) return;

        // Give result
        player.getInventory().add(result.copy());

        // Broadcast changes
        player.containerMenu.broadcastChanges();
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
            return result.isEmpty() ? null : new CraftingResult(result, matchedIngredients);
        }

        if (recipe instanceof SmithingRecipe smithingRecipe) {
            SmithingMatch match = findSmithingMatch(player, smithingRecipe);
            if (match == null) return null;

            SmithingRecipeInput input = new SmithingRecipeInput(match.template().stack(), match.base().stack(), match.addition().stack());
            if (!smithingRecipe.matches(input, player.level())) return null;

            ItemStack result = smithingRecipe.assemble(input, server.registryAccess());
            if (result.isEmpty()) return null;

            return new CraftingResult(result, List.of(
                    match.template(),
                    match.base(),
                    match.addition()
            ));
        }

        if (RecipeSupport.isConfiguredSimpleRecipe(recipe, server.registryAccess())) {
            ItemStack result = recipe.getResultItem(server.registryAccess()).copy();
            List<IngredientUse> matchedIngredients = matchIngredients(player, RecipeSupport.getNonEmptyIngredients(recipe));
            if (matchedIngredients.isEmpty()) return null;
            return result.isEmpty() ? null : new CraftingResult(result, matchedIngredients);
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
    private static boolean hasSpaceForResult(Inventory inventory, ItemStack result, List<IngredientUse> consumedIngredients) {
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

        int remaining = result.getCount();
        int maxStack = result.getMaxStackSize();

        // 先统计可合并到现有同类堆叠中的空间。
        for (ItemStack stack : simulatedInventory) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, result)) {
                int space = maxStack - stack.getCount();
                if (space > 0) {
                    remaining -= Math.min(remaining, space);
                    if (remaining <= 0) return true;
                }
            }
        }

        // 现有堆叠不够时，再把空槽按完整最大堆叠容量计入。
        for (ItemStack stack : simulatedInventory) {
            if (stack.isEmpty()) {
                remaining -= maxStack;
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

    /**
     * 一次实际合成需要发放的产物和扣除的材料。
     *
     * @param result 合成产物
     * @param ingredients 需要扣除的材料
     */
    private record CraftingResult(ItemStack result, List<IngredientUse> ingredients) {
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
