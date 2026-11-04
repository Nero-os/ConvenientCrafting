package com.adore.convenientcrafting.client;

import com.adore.convenientcrafting.network.LegacyRecipeSyncPacket;

import java.io.ByteArrayOutputStream;

import net.minecraft.core.RegistryAccess;

/** Assembles a catalogue without exposing partially received or stale-tag recipes. */
public final class LegacyRecipeCatalog {
    private static ByteArrayOutputStream pending;
    private static int nextBatch;
    private static boolean complete;

    private LegacyRecipeCatalog() {}

    public static void accept(LegacyRecipeSyncPacket batch) {
        if (batch.batchIndex() == 0) {
            pending = new ByteArrayOutputStream();
            nextBatch = 0;
            complete = false;
        }
        if (pending == null || complete || batch.batchIndex() != nextBatch) {
            reset();
            return;
        }
        pending.writeBytes(batch.data());
        nextBatch++;
        complete = batch.lastBatch();
    }

    public static boolean publishAfterTags(RegistryAccess registries) {
        if (!complete) {
            return false;
        }
        // OnDatapackSyncEvent precedes vanilla tag sync. Resolve recipe ingredients
        // only after the new tags arrive, especially during an in-world /reload.
        try {
            ClientRecipes.replace(pending, LegacyRecipeSyncPacket.decodeRecipes(pending.toByteArray(), registries));
            return true;
        } finally {
            reset();
        }
    }

    public static void reset() {
        pending = null;
        nextBatch = 0;
        complete = false;
    }
}
