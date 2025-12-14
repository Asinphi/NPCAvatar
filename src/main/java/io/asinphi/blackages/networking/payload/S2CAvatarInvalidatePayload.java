package io.asinphi.blackages.networking.payload;

import io.asinphi.blackages.networking.ModPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CAvatarInvalidatePayload(ResourceLocation avatarId) implements CustomPacketPayload {

    public static final StreamCodec<FriendlyByteBuf, S2CAvatarInvalidatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(ResourceLocation.CODEC), S2CAvatarInvalidatePayload::avatarId,
                    S2CAvatarInvalidatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ModPackets.S2C_AVATAR_INVALIDATE;
    }
}
