package com.adore.convenientcrafting.recipe.unlock;

import com.adore.convenientcrafting.ConvenientCrafting;
import com.adore.convenientcrafting.config.Config;
import com.adore.convenientcrafting.network.RecipeUnlockSyncPacket;
import com.adore.convenientcrafting.recipe.BrewingRecipeSupport;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 管理玩家已解锁的便捷合成配方类型。
 */
public final class RecipeUnlocks {
    private static final String DATA_KEY = ConvenientCrafting.MODID + ".recipe_unlocks";
    private static final String UNLOCKED_TYPES_KEY = "UnlockedRecipeTypes";

    public RecipeUnlocks() {
    }

    public static boolean unlockRecipeType(ServerPlayer player, ResourceLocation recipeTypeId) {
        Set<String> unlockedTypes = getUnlockedTypes(player);
        boolean changed = unlockedTypes.add(normalize(recipeTypeId));
        if (changed) {
            saveUnlockedTypes(player, unlockedTypes);
            syncToClient(player);
        }
        return changed;
    }

    public static int unlockRecipeTypes(ServerPlayer player, Collection<ResourceLocation> recipeTypeIds) {
        Set<String> unlockedTypes = getUnlockedTypes(player);
        int changedCount = 0;
        for (ResourceLocation recipeTypeId : recipeTypeIds) {
            if (unlockedTypes.add(normalize(recipeTypeId))) {
                changedCount++;
            }
        }

        if (changedCount > 0) {
            saveUnlockedTypes(player, unlockedTypes);
            syncToClient(player);
        }
        return changedCount;
    }

    public static List<ResourceLocation> getUnlockableRecipeTypeIds() {
        Set<ResourceLocation> recipeTypeIds = new LinkedHashSet<>();
        getEnabledBuiltinRecipeTypeIds().stream()
                .map(ResourceLocation::tryParse)
                .filter(id -> id != null)
                .forEach(recipeTypeIds::add);
        getEnabledAdditionalRecipeTypeIds().stream()
                .map(ResourceLocation::tryParse)
                .filter(id -> id != null)
                .forEach(recipeTypeIds::add);
        recipeTypeIds.addAll(getUnlockRules().keySet());
        return List.copyOf(recipeTypeIds);
    }

    /**
     * 判断玩家是否已经解锁指定配方类型。
     *
     * @param player 玩家
     * @param recipeTypeId 配方类型 ID
     * @return 已解锁时返回 {@code true}
     */
    public static boolean isUnlocked(Player player, ResourceLocation recipeTypeId) {
        return getUnlockedTypes(player).contains(normalize(recipeTypeId));
    }

    /**
     * 扫描玩家背包，发现工作台类解锁物品后永久解锁对应配方类型。
     *
     * @param player 玩家
     */
    public static void refreshUnlocks(Player player) {
        refreshUnlocks(player, false);
    }

    /**
     * 鎵弿鐜╁鑳屽寘锛屽彂鐜板伐浣滃彴绫昏В閿佺墿鍝佸悗姘镐箙瑙ｉ攣瀵瑰簲閰嶆柟绫诲瀷銆?     *
     * @param player 鐜╁
     * @param notify 鏄惁鍦ㄨВ閿佹柊閰嶆柟绫诲瀷鏃跺彂閫佽亰澶╂彁绀?
     */
    public static void refreshUnlocks(Player player, boolean notify) {
        Map<ResourceLocation, List<ResourceLocation>> unlockRules = getUnlockRules();
        Set<String> unlockedTypes = getUnlockedTypes(player);
        boolean changed = false;

        for (Map.Entry<ResourceLocation, List<ResourceLocation>> entry : unlockRules.entrySet()) {
            String recipeTypeId = normalize(entry.getKey());
            if (unlockedTypes.contains(recipeTypeId)) {
                continue;
            }

            ItemStack unlockItem = findFirstUnlockItem(player, entry.getValue());
            if (!unlockItem.isEmpty()) {
                unlockedTypes.add(recipeTypeId);
                changed = true;
                if (notify) {
                    sendUnlockMessage(player, entry.getKey(), unlockItem);
                }
            }
        }

        if (changed) {
            saveUnlockedTypes(player, unlockedTypes);
            if (player instanceof ServerPlayer serverPlayer) {
                syncToClient(serverPlayer);
            }
        }
    }

    /**
     * 玩家登录后立即刷新一次解锁状态。
     *
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshUnlocks(player, false);
            syncToClient(player);
        }
    }

    /**
     * 玩家死亡或跨维度克隆时复制已解锁配方类型。
     *
     * @param event 玩家克隆事件
     */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        copyUnlocks(event.getOriginal(), event.getEntity());
        refreshUnlocks(event.getEntity(), false);
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToClient(player);
        }
    }

    /**
     * 低频扫描玩家背包，用于捕获新获得的工作台物品。
     *
     * @param event 玩家 tick 事件
     */
    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            ItemStack original = event.getOriginalStack();
            ItemStack current = event.getCurrentStack();
            if (!original.isEmpty() && current.getCount() < original.getCount()) {
                unlockFromItem(player, original, true);
            }
        }
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            unlockFromItem(player, event.getCrafting(), true);
        }
    }

    public static void unlockFromItem(Player player, ItemStack stack, boolean notify) {
        if (stack.isEmpty()) {
            return;
        }

        ResourceLocation itemId = getItemId(stack.getItem());
        if (itemId == null) {
            return;
        }

        Map<ResourceLocation, List<ResourceLocation>> unlockRules = getUnlockRules();
        Set<String> unlockedTypes = getUnlockedTypes(player);
        boolean changed = false;

        for (Map.Entry<ResourceLocation, List<ResourceLocation>> entry : unlockRules.entrySet()) {
            String recipeTypeId = normalize(entry.getKey());
            if (!unlockedTypes.contains(recipeTypeId) && entry.getValue().contains(itemId)) {
                unlockedTypes.add(recipeTypeId);
                changed = true;
                if (notify) {
                    sendUnlockMessage(player, entry.getKey(), stack);
                }
            }
        }

        if (changed) {
            saveUnlockedTypes(player, unlockedTypes);
            if (player instanceof ServerPlayer serverPlayer) {
                syncToClient(serverPlayer);
            }
        }
    }

    /**
     * 获取完整解锁规则，包含内置默认规则和配置追加规则。
     *
     * @return 配方类型到解锁物品列表的映射
     */
    private static Map<ResourceLocation, List<ResourceLocation>> getUnlockRules() {
        Map<ResourceLocation, List<ResourceLocation>> rules = new HashMap<>();
        putBuiltinRule(rules, "minecraft:crafting", List.of("minecraft:crafting_table"));
        putBuiltinRule(rules, "minecraft:smithing", List.of("minecraft:smithing_table"));
        putBuiltinRule(rules, "minecraft:brewing", List.of("minecraft:brewing_stand"));

        for (String rule : Config.RECIPE_TYPE_UNLOCK_ITEMS.get()) {
            String[] parts = rule.split("=", 2);
            if (parts.length != 2) {
                continue;
            }

            ResourceLocation recipeTypeId = ResourceLocation.tryParse(parts[0].trim());
            if (recipeTypeId == null) {
                continue;
            }

            List<ResourceLocation> unlockItems = new ArrayList<>();
            for (String itemId : parts[1].split(",")) {
                ResourceLocation unlockItem = ResourceLocation.tryParse(itemId.trim());
                if (unlockItem != null) {
                    unlockItems.add(unlockItem);
                }
            }

            if (!unlockItems.isEmpty()) {
                rules.computeIfAbsent(recipeTypeId, ignored -> new ArrayList<>()).addAll(unlockItems);
            }
        }

        return rules;
    }

    private static void putRule(Map<ResourceLocation, List<ResourceLocation>> rules, String recipeTypeId, List<String> itemIds) {
        ResourceLocation typeId = ResourceLocation.parse(recipeTypeId);
        List<ResourceLocation> unlockItems = rules.computeIfAbsent(typeId, ignored -> new ArrayList<>());
        itemIds.stream().map(ResourceLocation::parse).forEach(unlockItems::add);
    }

    private static void putBuiltinRule(Map<ResourceLocation, List<ResourceLocation>> rules, String recipeTypeId, List<String> itemIds) {
        ResourceLocation typeId = ResourceLocation.parse(recipeTypeId);
        if (isBuiltinRecipeTypeEnabled(typeId)) {
            putRule(rules, recipeTypeId, itemIds);
        }
    }

    private static ItemStack findFirstUnlockItem(Player player, List<ResourceLocation> unlockItemIds) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            ResourceLocation itemId = getItemId(stack.getItem());
            if (itemId != null && unlockItemIds.contains(itemId)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ResourceLocation getItemId(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
    }

    private static void sendUnlockMessage(Player player, ResourceLocation recipeTypeId, ItemStack unlockItem) {
        MutableComponent message = Component.literal("[便捷合成] ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("已解锁 ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(getRecipeTypeDisplayName(recipeTypeId))
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("！获得 ")
                        .withStyle(ChatFormatting.GRAY))
                .append(unlockItem.getHoverName().copy()
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" 后，可在便捷合成面板中使用对应配方。")
                        .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(message);
    }

    private static String getRecipeTypeDisplayName(ResourceLocation recipeTypeId) {
        String normalized = normalize(recipeTypeId);
        return switch (normalized) {
            case "minecraft:crafting" -> "工作台配方";
            case "minecraft:smithing" -> "锻造台配方";
            default -> recipeTypeId + " 配方";
        };
    }

    private static void copyUnlocks(Player original, Player target) {
        Set<String> unlockedTypes = getUnlockedTypes(original);
        saveUnlockedTypes(target, unlockedTypes);
    }

    private static void syncToClient(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new RecipeUnlockSyncPacket(
                new ArrayList<>(getUnlockedTypes(player)),
                getEnabledBuiltinRecipeTypeIds(),
                getEnabledAdditionalRecipeTypeIds()
        ));
    }

    public static boolean isBuiltinRecipeTypeEnabled(ResourceLocation recipeTypeId) {
        String normalized = normalize(recipeTypeId);
        return Config.ENABLED_BUILTIN_RECIPE_TYPES.get().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    private static List<String> getEnabledBuiltinRecipeTypeIds() {
        return Config.ENABLED_BUILTIN_RECIPE_TYPES.get().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
    }

    private static List<String> getEnabledAdditionalRecipeTypeIds() {
        return Config.ADDITIONAL_RECIPE_TYPES.get().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
    }

    private static Set<String> getUnlockedTypes(Player player) {
        Set<String> unlockedTypes = new HashSet<>();
        CompoundTag data = player.getPersistentData().getCompound(DATA_KEY);
        ListTag list = data.getList(UNLOCKED_TYPES_KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            unlockedTypes.add(list.getString(i));
        }
        return unlockedTypes;
    }

    private static void saveUnlockedTypes(Player player, Set<String> unlockedTypes) {
        CompoundTag data = player.getPersistentData().getCompound(DATA_KEY);
        ListTag list = new ListTag();
        unlockedTypes.stream().sorted().map(StringTag::valueOf).forEach(list::add);
        data.put(UNLOCKED_TYPES_KEY, list);
        player.getPersistentData().put(DATA_KEY, data);
    }

    private static String normalize(ResourceLocation recipeTypeId) {
        return recipeTypeId.toString().toLowerCase(Locale.ROOT);
    }
}
