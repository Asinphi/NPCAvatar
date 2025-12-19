package io.asinphi.npcavatar.networking.payload;

import io.asinphi.npcavatar.networking.ModPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2CUploadResultPayload(boolean ok, String message) implements CustomPacketPayload {

    public static final StreamCodec<FriendlyByteBuf, S2CUploadResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, S2CUploadResultPayload::ok,
                    ByteBufCodecs.STRING_UTF8, S2CUploadResultPayload::message,
                    S2CUploadResultPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ModPackets.S2C_UPLOAD_RESULT;
    }
}
