package com.adore.convenientcrafting.network;

import com.adore.convenientcrafting.ConvenientCrafting;
import com.adore.convenientcrafting.client.screen.CraftHelperScreen;
import com.adore.convenientcrafting.recipe.unlock.ClientRecipeUnlocks;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端同步玩家已解锁配方类型到客户端的数据包。
 *
 * @param unlockedRecipeTypeIds 已解锁配方类型 ID 列表
 * @param enabledAdditionalRecipeTypeIds 服务端额外启用的配方类型 ID 列表
 */
public record RecipeUnlockSyncPacket(
        List<String> unlockedRecipeTypeIds,
        List<String> enabledBuiltinRecipeTypeIds,
        List<String> enabledAdditionalRecipeTypeIds
) implements CustomPacketPayload {
    /**
     * 数据包类型标识。
     */
    public static final Type<RecipeUnlockSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ConvenientCrafting.MODID, "recipe_unlock_sync")
    );

    /**
     * 已解锁配方类型同步包编解码器。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeUnlockSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8, 256),
                    RecipeUnlockSyncPacket::unlockedRecipeTypeIds,
                    ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8, 256),
                    RecipeUnlockSyncPacket::enabledBuiltinRecipeTypeIds,
                    ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8, 256),
                    RecipeUnlockSyncPacket::enabledAdditionalRecipeTypeIds,
                    RecipeUnlockSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理解锁状态同步。
     *
     * @param message 同步数据
     * @param context 数据包上下文
     */
    public static void handleClient(RecipeUnlockSyncPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            int unlockRevisionBefore = ClientRecipeUnlocks.getRevision();
            ClientRecipeUnlocks.setUnlockedRecipeTypes(message.unlockedRecipeTypeIds());
            ClientRecipeUnlocks.setEnabledBuiltinRecipeTypes(message.enabledBuiltinRecipeTypeIds());
            ClientRecipeUnlocks.setEnabledAdditionalRecipeTypes(message.enabledAdditionalRecipeTypeIds());
            if (ClientRecipeUnlocks.getRevision() != unlockRevisionBefore) {
                CraftHelperScreen.preloadRecipeIndex();
            }
        });
    }
}
