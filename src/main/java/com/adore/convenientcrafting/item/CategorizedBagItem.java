package com.adore.convenientcrafting.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * 按物品标签限制内容类型的九格收纳袋物品。
 *
 * <p>每个收纳袋本身在玩家背包中只占一格，内部通过原版 {@link DataComponents#CONTAINER}
 * 组件保存至多 9 组物品。具体可收纳的物品由构造时传入的 {@link TagKey} 决定，例如种子袋
 * 使用 {@code c:seeds}，染料袋使用 {@code c:dyes}。</p>
 *
 * <p>该类只负责收纳袋自身的存取逻辑。拾取物品时自动装袋、便捷合成读取袋内材料、整理背包时
 * 优先装袋等跨系统行为分别由对应事件和业务逻辑调用这里提供的 {@link #accepts(ItemStack)}、
 * {@link #insert(ItemStack, ItemStack)}、{@link #getContents(ItemStack)} 与
 * {@link #setContents(ItemStack, NonNullList)} 完成。</p>
 */
public class CategorizedBagItem extends Item {
    /**
     * 收纳袋固定提供 9 个内部槽位。
     */
    public static final int SLOT_COUNT = 9;

    /**
     * 物品耐久条位置复用为槽位占用进度条时使用的颜色。
     */
    private static final int BAR_COLOR = Mth.color(0.35F, 0.75F, 0.45F);

    /**
     * 允许被放入当前收纳袋的物品标签。
     */
    private final TagKey<Item> acceptedItems;

    /**
     * 创建一个按标签限制内容的收纳袋。
     *
     * @param properties 物品基础属性
     * @param acceptedItems 允许放入收纳袋的物品标签
     */
    public CategorizedBagItem(Properties properties, TagKey<Item> acceptedItems) {
        super(properties);
        this.acceptedItems = acceptedItems;
    }

    /**
     * 判断一个物品堆是否可以放入当前收纳袋。
     *
     * <p>除了标签匹配外，还会遵守原版/NeoForge 的容器物品安全规则，
     * 避免把不能嵌套进容器物品的特殊物品塞入袋内。</p>
     *
     * @param stack 待检查的物品堆
     * @return 可以放入当前收纳袋时返回 {@code true}
     */
    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && stack.canFitInsideContainerItems() && stack.is(acceptedItems);
    }

    /**
     * 尝试把一组物品插入指定收纳袋。
     *
     * <p>插入会先合并到已有的相同物品堆，再填充空槽。该方法会直接缩减
     * {@code incoming} 的数量，并在成功插入任意数量后写回收纳袋内容。</p>
     *
     * @param bagStack 当前收纳袋物品堆，数量必须为 1
     * @param incoming 待插入的物品堆，会被原地缩减
     * @return 实际插入的物品数量
     */
    public int insert(ItemStack bagStack, ItemStack incoming) {
        if (bagStack.getCount() != 1 || !accepts(incoming)) {
            return 0;
        }

        NonNullList<ItemStack> contents = getContents(bagStack);
        int before = incoming.getCount();
        mergeIntoExistingSlots(contents, incoming);
        fillEmptySlots(contents, incoming);
        int inserted = before - incoming.getCount();
        if (inserted > 0) {
            setContents(bagStack, contents);
        }
        return inserted;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        if (stack.getCount() != 1 || action != ClickAction.SECONDARY) {
            return false;
        }

        ItemStack slotStack = slot.getItem();
        if (slotStack.isEmpty()) {
            // 收纳袋右键空槽时，取出袋内最靠前的一整组物品。
            ItemStack removed = removeFirstStack(stack);
            if (!removed.isEmpty()) {
                playRemoveOneSound(player);
                ItemStack remainder = slot.safeInsert(removed);
                if (!remainder.isEmpty()) {
                    insert(stack, remainder);
                }
            }
            return true;
        }

        if (!accepts(slotStack) || !slot.mayPickup(player)) {
            return false;
        }

        // 先计算可插入数量，再从目标槽安全取出，避免取出后发现袋子装不下。
        int insertable = getInsertableAmount(stack, slotStack);
        if (insertable <= 0) {
            return true;
        }

        ItemStack taken = slot.safeTake(slotStack.getCount(), insertable, player);
        if (!taken.isEmpty() && insert(stack, taken) > 0) {
            playInsertSound(player);
        }
        if (!taken.isEmpty()) {
            slot.safeInsert(taken);
        }
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(
            ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (stack.getCount() != 1 || action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }

        if (other.isEmpty()) {
            // 光标为空时，从袋内取出一整组物品放到光标上。
            ItemStack removed = removeFirstStack(stack);
            if (!removed.isEmpty()) {
                playRemoveOneSound(player);
                access.set(removed);
            }
            return true;
        }

        if (!accepts(other)) {
            return false;
        }

        if (insert(stack, other) > 0) {
            playInsertSound(player);
        }
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getStoredItemCount(stack) > 0;
    }

    /**
     * 使用原版物品条显示收纳袋的盈满程度。
     *
     * <p>每个内部槽位按自身最大堆叠数折算为一格容量，因此 64 个种子会填满
     * 一个九分之一容量槽，而不是和 1 个种子显示相同的进度。</p>
     */
    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.min(1 + Mth.floor(getFullnessDisplay(stack) * (MAX_BAR_WIDTH - 1)), MAX_BAR_WIDTH);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    /**
     * 计算收纳袋的填充比例，返回值范围为 {@code 0.0F} 到 {@code 1.0F}。
     *
     * @param bagStack 收纳袋物品堆
     * @return 当前内容占总容量的比例
     */
    public static float getFullnessDisplay(ItemStack bagStack) {
        float usedSlots = 0.0F;
        for (ItemStack stack : getContents(bagStack)) {
            if (!stack.isEmpty()) {
                usedSlots += (float)stack.getCount() / (float)stack.getMaxStackSize();
            }
        }
        return Mth.clamp(usedSlots / SLOT_COUNT, 0.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(
                "item.convenientcrafting.bag.slots",
                getUsedSlotCount(stack),
                SLOT_COUNT).withStyle(ChatFormatting.GRAY));
    }

    /**
     * 禁止收纳袋被放入其他容器物品，避免袋子套袋子造成复杂嵌套。
     */
    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return false;
    }

    /**
     * 兼容仍调用旧版非物品堆敏感 API 的逻辑，同样禁止袋子套袋子。
     */
    @Override
    @Deprecated
    public boolean canFitInsideContainerItems() {
        return false;
    }

    /**
     * 读取收纳袋的 9 格内部内容。
     *
     * <p>返回值是固定长度为 {@link #SLOT_COUNT} 的可变副本，调用者修改后需要通过
     * {@link #setContents(ItemStack, NonNullList)} 写回。</p>
     *
     * @param bagStack 收纳袋物品堆
     * @return 固定 9 格的袋内物品副本
     */
    public static NonNullList<ItemStack> getContents(ItemStack bagStack) {
        NonNullList<ItemStack> contents = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        bagStack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(contents);
        return contents;
    }

    /**
     * 写回收纳袋的内部内容。
     *
     * @param bagStack 收纳袋物品堆
     * @param contents 要保存的袋内物品列表，最多保留至最后一个非空槽
     */
    public static void setContents(ItemStack bagStack, NonNullList<ItemStack> contents) {
        bagStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
    }

    /**
     * 统计已经被占用的内部槽位数量。
     */
    private static int getUsedSlotCount(ItemStack bagStack) {
        int count = 0;
        for (ItemStack stack : getContents(bagStack)) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计收纳袋内实际存放的物品总数，用于判断是否需要显示盈满进度条。
     */
    private static int getStoredItemCount(ItemStack bagStack) {
        int count = 0;
        for (ItemStack stack : getContents(bagStack)) {
            if (!stack.isEmpty()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 从袋内移除并返回第一组非空物品。
     */
    private static ItemStack removeFirstStack(ItemStack bagStack) {
        NonNullList<ItemStack> contents = getContents(bagStack);
        for (int i = 0; i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (!stack.isEmpty()) {
                contents.set(i, ItemStack.EMPTY);
                setContents(bagStack, contents);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 计算指定物品堆还能向收纳袋插入多少个物品。
     */
    private static int getInsertableAmount(ItemStack bagStack, ItemStack incoming) {
        NonNullList<ItemStack> contents = getContents(bagStack);
        int amount = 0;
        for (ItemStack stored : contents) {
            if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, incoming)) {
                amount += Math.max(0, stored.getMaxStackSize() - stored.getCount());
            }
        }
        for (ItemStack stored : contents) {
            if (stored.isEmpty()) {
                amount += incoming.getMaxStackSize();
            }
        }
        return Math.min(amount, incoming.getCount());
    }

    /**
     * 优先把物品合并进袋内已有的相同物品堆。
     */
    private static void mergeIntoExistingSlots(NonNullList<ItemStack> contents, ItemStack incoming) {
        for (ItemStack stored : contents) {
            if (incoming.isEmpty()) {
                return;
            }
            if (!stored.isEmpty() && ItemStack.isSameItemSameComponents(stored, incoming)) {
                int moved = Math.min(incoming.getCount(), stored.getMaxStackSize() - stored.getCount());
                if (moved > 0) {
                    incoming.shrink(moved);
                    stored.grow(moved);
                }
            }
        }
    }

    /**
     * 将无法继续合并的物品放入空槽。
     */
    private static void fillEmptySlots(NonNullList<ItemStack> contents, ItemStack incoming) {
        for (int i = 0; i < contents.size(); i++) {
            if (incoming.isEmpty()) {
                return;
            }
            if (contents.get(i).isEmpty()) {
                contents.set(i, incoming.split(Math.min(incoming.getCount(), incoming.getMaxStackSize())));
            }
        }
    }

    /**
     * 播放从袋中取出物品的音效。
     */
    private static void playRemoveOneSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    /**
     * 播放向袋中放入物品的音效。
     */
    private static void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }
}
