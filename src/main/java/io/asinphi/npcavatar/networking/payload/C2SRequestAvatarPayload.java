package io.asinphi.npcavatar.networking.payload;

import io.asinphi.npcavatar.networking.ModPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SRequestAvatarPayload(ResourceLocation avatarId) implements CustomPacketPayload {

    public static final StreamCodec<FriendlyByteBuf, C2SRequestAvatarPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(ResourceLocation.CODEC), C2SRequestAvatarPayload::avatarId,
                    C2SRequestAvatarPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ModPackets.C2S_REQUEST_AVATAR;
    }
}
