package io.asinphi.blackages.networking.payload;

import io.asinphi.blackages.networking.ModPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CRequestLocalUploadPayload(ResourceLocation avatarId, String relativePath) implements CustomPacketPayload {

    public static final StreamCodec<FriendlyByteBuf, S2CRequestLocalUploadPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.fromCodec(ResourceLocation.CODEC), S2CRequestLocalUploadPayload::avatarId,
                    ByteBufCodecs.STRING_UTF8, S2CRequestLocalUploadPayload::relativePath,
                    S2CRequestLocalUploadPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ModPackets.S2C_REQUEST_LOCAL_UPLOAD;
    }
}
