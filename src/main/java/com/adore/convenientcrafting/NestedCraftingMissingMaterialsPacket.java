package com.adore.convenientcrafting;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端递归合成预检失败后，发送给客户端的缺失材料树数据包。
 *
 * <p>这里不只传最终缺少的物品清单，而是传一组已经按深度展开的树形行。
 * 客户端可以直接按顺序绘制，显示“目标物品 -> 中间材料 -> 缺失原材料”的关系。</p>
 *
 * @param rows 缺失材料树的扁平行列表，顺序即展示顺序
 */
public record NestedCraftingMissingMaterialsPacket(List<MissingMaterialRow> rows) implements CustomPacketPayload {
    /**
     * 数据包类型标识。
     */
    public static final Type<NestedCraftingMissingMaterialsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ConvenientCrafting.MODID, "nested_crafting_missing_materials")
    );

    /**
     * 缺失材料树数据包的编解码器。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, NestedCraftingMissingMaterialsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, MissingMaterialRow.STREAM_CODEC, 256),
                    NestedCraftingMissingMaterialsPacket::rows,
                    NestedCraftingMissingMaterialsPacket::new
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
     * 在客户端线程打开缺失材料树形弹窗。
     *
     * @param message 服务端发送的缺失材料树
     * @param context 数据包处理上下文
     */
    public static void handleClient(NestedCraftingMissingMaterialsPacket message, IPayloadContext context) {
        context.enqueueWork(() -> MissingNestedCraftingMaterialsScreen.open(message.rows()));
    }

    /**
     * 缺失材料树中的一行。
     *
     * @param stack 要展示的物品
     * @param missing {@code true} 表示这是最终缺少、无法继续递归合成的材料
     * @param depth 当前行在树中的缩进深度，0 为目标物品
     */
    public record MissingMaterialRow(ItemStack stack, boolean missing, int depth) {
        /**
         * 单行树节点的编解码器。
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, MissingMaterialRow> STREAM_CODEC =
                StreamCodec.composite(
                        ItemStack.OPTIONAL_STREAM_CODEC,
                        MissingMaterialRow::stack,
                        ByteBufCodecs.BOOL,
                        MissingMaterialRow::missing,
                        ByteBufCodecs.VAR_INT,
                        MissingMaterialRow::depth,
                        MissingMaterialRow::new
                );
    }
}
