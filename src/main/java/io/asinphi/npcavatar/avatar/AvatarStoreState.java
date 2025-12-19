package io.asinphi.npcavatar.avatar;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class AvatarStoreState extends SavedData {
    private static final String NAME = "npc_avatar_store";

    private final Map<ResourceLocation, byte[]> avatars = new HashMap<>();

    public static AvatarStoreState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        AvatarStoreState::new,   // Supplier<T>
                        AvatarStoreState::load,  // BiFunction<CompoundTag, Provider, T>
                        null                     // DataFixTypes (unused)
                ),
                NAME
        );
    }

    public void put(ResourceLocation id, byte[] compressedNbt) {
        if (id == null || compressedNbt == null) return;
        avatars.put(id, compressedNbt); // overwrite semantics
        setDirty();
    }

    public byte[] get(ResourceLocation id) {
        return id == null ? null : avatars.get(id);
    }

    public Set<ResourceLocation> getIds() {
        return avatars.keySet().stream().collect(Collectors.toUnmodifiableSet());
    }

    public boolean contains(ResourceLocation id) {
        return id != null && avatars.containsKey(id);
    }

    public void remove(ResourceLocation id) {
        if (id != null && avatars.remove(id) != null) setDirty();
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();

        for (var e : avatars.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", e.getKey().toString());
            entry.putByteArray("Data", e.getValue());
            list.add(entry);
        }

        tag.put("Avatars", list);
        return tag;
    }

    public static AvatarStoreState load(CompoundTag tag, HolderLookup.Provider registries) {
        AvatarStoreState state = new AvatarStoreState();

        ListTag list = tag.getList("Avatars", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            String idStr = entry.getString("Id");
            byte[] data = entry.getByteArray("Data");
            if (data.length == 0 || idStr.isBlank()) continue;

            try {
                // idStr is already like "npcavatar:sin"
                ResourceLocation id = ResourceLocation.parse(idStr);
                state.avatars.put(id, data);
            } catch (Exception ignored) {
                // skip invalid ids
            }
        }

        return state;
    }

    private AvatarStoreState() {}
}
