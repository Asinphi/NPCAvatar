package io.asinphi.npcavatar.networking.payload;

import io.asinphi.npcavatar.networking.ModPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CAvatarDataPayload(ResourceLocation avatarId, byte[] compressedNbtOrEmpty) implements CustomPacketPayload {

    public static final StreamCodec<FriendlyByteBuf, S2CAvatarDataPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(ResourceLocation.CODEC), S2CAvatarDataPayload::avatarId,
                    ByteBufCodecs.BYTE_ARRAY, S2CAvatarDataPayload::compressedNbtOrEmpty,
                    S2CAvatarDataPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ModPackets.S2C_AVATAR_DATA;
    }
}
