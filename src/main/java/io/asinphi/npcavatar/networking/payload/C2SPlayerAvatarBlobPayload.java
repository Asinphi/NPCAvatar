package io.asinphi.npcavatar.networking.payload;

import io.asinphi.npcavatar.networking.ModPackets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

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
