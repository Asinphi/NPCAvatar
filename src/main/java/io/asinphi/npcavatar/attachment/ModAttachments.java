package io.asinphi.npcavatar.attachment;

import com.mojang.serialization.Codec;
import io.asinphi.npcavatar.NPCAvatar;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;

@SuppressWarnings("UnstableApiUsage")
public final class ModAttachments {
    public static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath(
            NPCAvatar.MOD_ID, "none");
    public static final ResourceLocation BLOB_MARKER_ID =
            ResourceLocation.fromNamespaceAndPath(NPCAvatar.MOD_ID, "blob");

    public static final AttachmentType<ResourceLocation> AVATAR_ID =
            AttachmentRegistry.create(ResourceLocation.fromNamespaceAndPath(
                    NPCAvatar.MOD_ID, "avatar_id"), builder -> builder
                    .initializer(() -> NONE)
                    .persistent(ResourceLocation.CODEC)
                    .syncWith(ByteBufCodecs.fromCodec(ResourceLocation.CODEC),
                            (entity, player) -> true));

    public static final AttachmentType<Boolean> HAS_AVATAR_BLOB =
            AttachmentRegistry.create(ResourceLocation.fromNamespaceAndPath(
                    NPCAvatar.MOD_ID, "has_avatar_blob"), builder -> builder
                    .initializer(() -> false)
                    .persistent(Codec.BOOL)
                    .syncWith(ByteBufCodecs.BOOL, (entity, player) -> true));

    public static final AttachmentType<byte[]> AVATAR_BLOB =
            AttachmentRegistry.create(ResourceLocation.fromNamespaceAndPath(
                    NPCAvatar.MOD_ID, "avatar_blob"), builder -> builder
                    .initializer(() -> new byte[0])
                    .persistent(com.mojang.serialization.Codec.BYTE_BUFFER.xmap(
                            buf -> {
                                byte[] out = new byte[buf.remaining()];
                                buf.get(out);
                                return out;
                            },
                            ByteBuffer::wrap
                    )));

    /** Call from common init to guarantee class loading & registration. */
    public static void init() {
        // no-op: referencing the class is enough to run static init
    }

    private ModAttachments() {}
}
