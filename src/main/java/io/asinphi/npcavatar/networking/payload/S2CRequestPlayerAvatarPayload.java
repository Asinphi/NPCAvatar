package io.asinphi.npcavatar.networking.payload;

import io.asinphi.npcavatar.networking.ModPackets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2CRequestPlayerAvatarPayload(int requestId) implements CustomPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRequestPlayerAvatarPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    S2CRequestPlayerAvatarPayload::requestId,
                    S2CRequestPlayerAvatarPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ModPackets.S2C_REQUEST_PLAYER_AVATAR;
    }
}
