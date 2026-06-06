package com.adore.convenientcrafting;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

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

        var holder = optional.get();
        if (!(holder.value() instanceof CraftingRecipe recipe)) return;

        ItemStack result = recipe.getResultItem(server.registryAccess());
        if (result.isEmpty()) return;

        Inventory inventory = player.getInventory();

        // Check if player has enough ingredients
        if (!hasIngredients(inventory, recipe)) return;

        // Check if there's enough space in inventory for the result
        if (!hasSpaceForResult(inventory, result)) return;

        // Consume ingredients
        consumeIngredients(inventory, recipe);

        // Give result
        inventory.add(result.copy());

        // Broadcast changes
        player.containerMenu.broadcastChanges();
    }

    /**
     * 判断玩家背包是否包含配方所需的全部材料。
     *
     * <p>这里使用背包物品的副本进行“模拟消耗”：每匹配到一个材料就从副本中扣 1 个。
     * 这样可以正确处理多个材料槽都接受同一种物品的情况，同时不会在检查阶段修改真实背包。</p>
     *
     * @param inventory 玩家背包
     * @param recipe 要检查的合成配方
     * @return 材料充足时返回 {@code true}
     */
    private static boolean hasIngredients(Inventory inventory, CraftingRecipe recipe) {
        List<Ingredient> ingredients = getIngredients(recipe);
        if (ingredients.isEmpty()) return false;

        // 构建可变的库存快照，后续 shrink 操作只会影响副本。
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                available.add(stack.copy());
            }
        }

        // 逐个材料槽寻找可用物品；找到后立即扣减副本，防止同一个物品被重复匹配。
        for (Ingredient ingredient : ingredients) {
            boolean found = false;
            for (ItemStack avail : available) {
                if (avail.isEmpty()) continue;
                if (ingredient.test(avail)) {
                    avail.shrink(1);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /**
     * 判断背包是否可以容纳配方产物。
     *
     * @param inventory 玩家背包
     * @param result 配方产物
     * @return 现有堆叠或空槽足够容纳产物时返回 {@code true}
     */
    private static boolean hasSpaceForResult(Inventory inventory, ItemStack result) {
        int remaining = result.getCount();
        int maxStack = result.getMaxStackSize();

        // 先统计可合并到现有同类堆叠中的空间。
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, result)) {
                int space = maxStack - stack.getCount();
                if (space > 0) {
                    remaining -= Math.min(remaining, space);
                    if (remaining <= 0) return true;
                }
            }
        }

        // 现有堆叠不够时，再把空槽按完整最大堆叠容量计入。
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                remaining -= maxStack;
                if (remaining <= 0) return true;
            }
        }

        return remaining <= 0;
    }

    /**
     * 从真实背包中扣除配方材料。
     *
     * <p>调用前应先通过 {@link #hasIngredients(Inventory, CraftingRecipe)} 验证，
     * 因此这里按材料列表逐项扣除第一个匹配物品即可。</p>
     *
     * @param inventory 玩家背包
     * @param recipe 要消耗材料的配方
     */
    private static void consumeIngredients(Inventory inventory, CraftingRecipe recipe) {
        List<Ingredient> ingredients = getIngredients(recipe);

        for (Ingredient ingredient : ingredients) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) continue;
                if (ingredient.test(stack)) {
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        inventory.setItem(i, ItemStack.EMPTY);
                    }
                    break;
                }
            }
        }
    }

    /**
     * 提取有序或无序合成配方中的非空材料。
     *
     * @param recipe 要解析的合成配方
     * @return 去除空占位后的材料列表
     */
    private static List<Ingredient> getIngredients(CraftingRecipe recipe) {
        List<Ingredient> result = new ArrayList<>();

        if (recipe instanceof ShapedRecipe shaped) {
            for (Ingredient ing : shaped.getIngredients()) {
                addNonEmptyIngredient(result, ing);
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            for (Ingredient ing : shapeless.getIngredients()) {
                addNonEmptyIngredient(result, ing);
            }
        }

        return result;
    }

    /**
     * 仅在材料至少有一个可匹配物品时加入列表。
     *
     * @param ingredients 目标材料列表
     * @param ingredient 待加入的材料
     */
    private static void addNonEmptyIngredient(List<Ingredient> ingredients, Ingredient ingredient) {
        if (ingredient.getItems().length > 0) {
            ingredients.add(ingredient);
        }
    }
}
