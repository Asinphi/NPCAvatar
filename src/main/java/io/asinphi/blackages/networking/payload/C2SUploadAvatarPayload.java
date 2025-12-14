package io.asinphi.blackages.networking.payload;

import io.asinphi.blackages.networking.ModPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SUploadAvatarPayload(ResourceLocation avatarId, byte[] compressedNbt) implements CustomPacketPayload {

    public static final StreamCodec<FriendlyByteBuf, C2SUploadAvatarPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(ResourceLocation.CODEC), C2SUploadAvatarPayload::avatarId,
                    ByteBufCodecs.BYTE_ARRAY, C2SUploadAvatarPayload::compressedNbt,
                    C2SUploadAvatarPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ModPackets.C2S_UPLOAD_AVATAR;
    }
}
