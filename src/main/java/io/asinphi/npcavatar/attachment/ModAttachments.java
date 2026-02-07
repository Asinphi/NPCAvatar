package io.asinphi.npcavatar.attachment;

import com.mojang.serialization.Codec;
import io.asinphi.npcavatar.NPCAvatar;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;

import java.nio.ByteBuffer;

@SuppressWarnings("UnstableApiUsage")
public final class ModAttachments {
    public static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath(
            NPCAvatar.MOD_ID, "none");
    public static final int NO_BLOB_VERSION = -1;

    public static final AttachmentType<ResourceLocation> AVATAR_ID =
            AttachmentRegistry.create(ResourceLocation.fromNamespaceAndPath(
                    NPCAvatar.MOD_ID, "avatar_id"), builder -> builder
                    .initializer(() -> NONE)
                    .persistent(ResourceLocation.CODEC)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.fromCodec(ResourceLocation.CODEC),
                            AttachmentSyncPredicate.all()));

    public static final AttachmentType<Integer> AVATAR_BLOB_VERSION =
            AttachmentRegistry.create(ResourceLocation.fromNamespaceAndPath(
                    NPCAvatar.MOD_ID, "avatar_blob_version"), builder -> builder
                    .initializer(() -> NO_BLOB_VERSION)
                    .persistent(Codec.INT)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.VAR_INT,
                            AttachmentSyncPredicate.all()));

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
                    ))
                    .copyOnDeath());

    /** Call from common init to guarantee class loading & registration. */
    public static void init() {
        // no-op: referencing the class is enough to run static init
    }

    private ModAttachments() {}
}
