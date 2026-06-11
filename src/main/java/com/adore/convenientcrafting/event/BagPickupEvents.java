package com.adore.convenientcrafting.event;

import java.util.UUID;

import com.adore.convenientcrafting.item.CategorizedBagItem;

import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * 收纳袋自动拾取事件处理器。
 *
 * <p>当玩家碰到地上的物品实体时，本类会在原版背包拾取逻辑执行前尝试把该物品放入玩家背包中
 * 已有的匹配收纳袋。只有袋子无法继续收纳的剩余数量才会交给原版逻辑进入普通背包槽位。</p>
 */
public class BagPickupEvents {
    /**
     * 在物品实体进入原版背包拾取流程前，优先尝试装入玩家已有收纳袋。
     *
     * @param event 物品实体拾取前置事件
     */
    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        ItemEntity itemEntity = event.getItemEntity();
        UUID target = itemEntity.getTarget();
        if (itemEntity.hasPickUpDelay() || target != null && !target.equals(player.getUUID())) {
            return;
        }

        ItemStack pickedStack = itemEntity.getItem();
        if (pickedStack.isEmpty()) {
            return;
        }

        Item pickedItem = pickedStack.getItem();
        int before = pickedStack.getCount();
        insertIntoInventoryBags(player.getInventory(), pickedStack);
        int inserted = before - pickedStack.getCount();
        if (inserted <= 0) {
            return;
        }

        player.take(itemEntity, inserted);
        player.awardStat(Stats.ITEM_PICKED_UP.get(pickedItem), inserted);
        player.getInventory().setChanged();
        if (pickedStack.isEmpty()) {
            // 全部装袋后阻止原版继续尝试拾取这个已经被清空的物品实体。
            itemEntity.discard();
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    /**
     * 按玩家背包顺序扫描收纳袋并尝试插入拾取到的物品。
     *
     * <p>主背包和快捷栏优先于副手，确保常规背包里的袋子先获得物品。</p>
     *
     * @param inventory 玩家背包
     * @param pickedStack 正在拾取的物品堆，会被原地缩减
     */
    private static void insertIntoInventoryBags(Inventory inventory, ItemStack pickedStack) {
        for (ItemStack inventoryStack : inventory.items) {
            insertIntoBag(inventoryStack, pickedStack);
            if (pickedStack.isEmpty()) {
                return;
            }
        }
        for (ItemStack inventoryStack : inventory.offhand) {
            insertIntoBag(inventoryStack, pickedStack);
            if (pickedStack.isEmpty()) {
                return;
            }
        }
    }

    /**
     * 尝试把拾取到的物品插入单个背包槽中的收纳袋。
     *
     * @param inventoryStack 背包槽里的物品堆
     * @param pickedStack 正在拾取的物品堆，会被原地缩减
     */
    private static void insertIntoBag(ItemStack inventoryStack, ItemStack pickedStack) {
        if (inventoryStack.getItem() instanceof CategorizedBagItem bagItem && bagItem.accepts(pickedStack)) {
            int inserted = bagItem.insert(inventoryStack, pickedStack);
            if (inserted > 0) {
                inventoryStack.setPopTime(5);
            }
        }
    }
}
