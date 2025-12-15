package io.asinphi.blackages.networking.payload;

import io.asinphi.blackages.networking.ModPackets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SPlayerAvatarBlobPayload(int requestId, byte[] compressedAvatarNbt) implements CustomPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SPlayerAvatarBlobPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    C2SPlayerAvatarBlobPayload::requestId,
                    ByteBufCodecs.BYTE_ARRAY,
                    C2SPlayerAvatarBlobPayload::compressedAvatarNbt,
                    C2SPlayerAvatarBlobPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ModPackets.C2S_PLAYER_AVATAR_BLOB;
    }
}
