package com.adore.convenientcrafting.network;

import com.adore.convenientcrafting.ConvenientCrafting;
import com.adore.convenientcrafting.client.LegacyRecipeCatalog;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Full recipe sync for NeoForge 21.2, before NeoForge provided this feature. */
public record LegacyRecipeSyncPacket(int batchIndex, boolean lastBatch, byte[] data)
        implements CustomPacketPayload {
    public static final int MAX_BATCH_BYTES = 64 * 1024;
    public static final Type<LegacyRecipeSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ConvenientCrafting.MODID, "recipe_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LegacyRecipeSyncPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeVarInt(packet.batchIndex());
                buffer.writeBoolean(packet.lastBatch());
                buffer.writeByteArray(packet.data());
            }, buffer -> new LegacyRecipeSyncPacket(buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readByteArray(MAX_BATCH_BYTES)));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<RecipeHolder<?>>> RECIPES_CODEC =
            ByteBufCodecs.collection(ArrayList::new, RecipeHolder.STREAM_CODEC);

    public LegacyRecipeSyncPacket {
        if (batchIndex < 0 || data.length > MAX_BATCH_BYTES) {
            throw new IllegalArgumentException("Invalid recipe catalogue batch");
        }
        data = data.clone();
    }

    public static List<LegacyRecipeSyncPacket> batches(Collection<RecipeHolder<?>> recipes, RegistryAccess registries) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
        try {
            RECIPES_CODEC.encode(buffer, List.copyOf(recipes));
            List<LegacyRecipeSyncPacket> batches = new ArrayList<>();
            while (buffer.isReadable()) {
                byte[] data = new byte[Math.min(buffer.readableBytes(), MAX_BATCH_BYTES)];
                buffer.readBytes(data);
                batches.add(new LegacyRecipeSyncPacket(batches.size(), !buffer.isReadable(), data));
            }
            return List.copyOf(batches);
        } finally {
            buffer.release();
        }
    }

    public static List<RecipeHolder<?>> decodeRecipes(byte[] data, RegistryAccess registries) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries, ConnectionType.NEOFORGE);
        try {
            var recipes = RECIPES_CODEC.decode(buffer);
            if (buffer.isReadable()) {
                throw new IllegalArgumentException("Unexpected trailing recipe catalogue data");
            }
            return List.copyOf(recipes);
        } finally {
            buffer.release();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(LegacyRecipeSyncPacket message, IPayloadContext context) {
        // Recipe decoding must wait for vanilla tag sync; only byte chunks are
        // decoded here, so new datapack tags need not exist on the client yet.
        context.enqueueWork(() -> LegacyRecipeCatalog.accept(message));
    }
}
