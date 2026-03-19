package com.adore.smartbundle;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SortInventoryPacket(boolean isContainer) implements CustomPacketPayload {
    public static final Type<SortInventoryPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(SmartBundle.MODID, "sort_inventory")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SortInventoryPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SortInventoryPacket::isContainer,
            SortInventoryPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SortInventoryPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (message.isContainer()) {
                    AbstractContainerMenu menu = player.containerMenu;
                    for (int i = 0; i < menu.slots.size(); i++) {
                        var slot = menu.slots.get(i);
                        if (slot != null && !slot.getItem().isEmpty()) {
                            InventorySorter.sortContainer(new SlotListContainer(menu));
                            break;
                        }
                    }
                } else {
                    Inventory inventory = player.getInventory();
                    InventorySorter.sortInventory(inventory);
                }

                player.containerMenu.broadcastChanges();
            }
        });
    }

    private static class SlotListContainer implements net.minecraft.world.Container {
        private final AbstractContainerMenu menu;

        public SlotListContainer(AbstractContainerMenu menu) {
            this.menu = menu;
        }

        @Override
        public int getContainerSize() {
            return menu.slots.size();
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public net.minecraft.world.item.ItemStack getItem(int slot) {
            if (slot >= 0 && slot < menu.slots.size()) {
                var s = menu.slots.get(slot);
                return s != null ? s.getItem() : net.minecraft.world.item.ItemStack.EMPTY;
            }
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) {
            if (slot >= 0 && slot < menu.slots.size()) {
                var s = menu.slots.get(slot);
                if (s != null) {
                    return s.remove(amount);
                }
            }
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) {
            if (slot >= 0 && slot < menu.slots.size()) {
                var s = menu.slots.get(slot);
                if (s != null) {
                    return s.remove(s.getItem().getCount());
                }
            }
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, net.minecraft.world.item.ItemStack stack) {
            if (slot >= 0 && slot < menu.slots.size()) {
                var s = menu.slots.get(slot);
                if (s != null) {
                    s.set(stack);
                }
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }

        @Override
        public void clearContent() {
        }
    }
}
