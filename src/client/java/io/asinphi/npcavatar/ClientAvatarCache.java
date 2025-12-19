package io.asinphi.npcavatar;

import io.asinphi.npcavatar.attachment.ModAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ClientAvatarCache {

    // Tune as needed (decoded NBT cap, not compressed bytes)
    private static final long MAX_DECOMPRESSED_NBT_BYTES = 8L * 1024L * 1024L;

    private static final Map<ResourceLocation, byte[]> compressedById = new HashMap<>();
    private static final Set<ResourceLocation> requested = new HashSet<>();

    public static void put(ResourceLocation id, byte[] compressedNbt) {
        if (id == null || compressedNbt == null) return;
        compressedById.put(id, compressedNbt);
        requested.remove(id);
    }

    public static boolean has(ResourceLocation id) {
        return id != null && compressedById.containsKey(id);
    }

    public static void remove(ResourceLocation id) {
        if (id == null) return;
        compressedById.remove(id);
        requested.remove(id);
    }

    /**
     * Marks an avatar as requested exactly once until it is received (put) or removed.
     *
     * @return true if caller should send a request packet now, false otherwise.
     */
    public static boolean markRequested(ResourceLocation id) {
        if (id == null || id.equals(ModAttachments.NONE)) return false;
        if (has(id) || requested.contains(id)) return false;

        requested.add(id);
        return true;
    }

    /**
     * Clears the "requested" flag (usually you won't need this because put/remove already do it).
     */
    public static void clearRequested(ResourceLocation id) {
        if (id == null) return;
        requested.remove(id);
    }

    /**
     * Decompress cached NBT. Returns null if not present or invalid.
     */
    public static CompoundTag decompress(ResourceLocation id) {
        byte[] data = compressedById.get(id);
        if (data == null || data.length == 0) return null;

        try {
            return NbtIo.readCompressed(
                    new ByteArrayInputStream(data),
                    NbtAccounter.create(MAX_DECOMPRESSED_NBT_BYTES)
            );
        } catch (Exception e) {
            // If it fails to decode, drop it so we can re-request next time
            remove(id);
            return null;
        }
    }

    private ClientAvatarCache() {}
}
