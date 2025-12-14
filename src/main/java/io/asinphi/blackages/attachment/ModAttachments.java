package io.asinphi.blackages.attachment;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;

public final class ModAttachments {
    public static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath(
            "blackages", "none");

    public static final AttachmentType<ResourceLocation> AVATAR_ID =
            AttachmentRegistry.<ResourceLocation>builder()
                    .initializer(() -> NONE)
                    .persistent(ResourceLocation.CODEC)
                    .syncWith(ByteBufCodecs.fromCodec(ResourceLocation.CODEC),
                            (entity, player) -> true)
                    .buildAndRegister(ResourceLocation.fromNamespaceAndPath(
                            "blackages", "avatar_id"));

    public static final AttachmentType<byte[]> AVATAR_BLOB =
            AttachmentRegistry.<byte[]>builder()
                    .initializer(() -> new byte[0])
                    // Persist as byte[] (stored inside entity NBT). The data-attachment API supports this via sync codec;
                    // persistence is handled by the attachment system; for "blob" we don't need a game registry codec.
                    .persistent(com.mojang.serialization.Codec.BYTE_BUFFER.xmap(
                            buf -> {
                                byte[] out = new byte[buf.remaining()];
                                buf.get(out);
                                return out;
                            },
                            ByteBuffer::wrap
                    ))
                    .syncWith(ByteBufCodecs.BYTE_ARRAY, (entity, player) -> true)
                    .buildAndRegister(ResourceLocation.fromNamespaceAndPath("blackages", "avatar_blob"));

    /** Call from common init to guarantee class loading & registration. */
    public static void init() {
        // no-op: referencing the class is enough to run static init
    }

    private ModAttachments() {}
}
