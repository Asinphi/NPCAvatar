package io.asinphi.npcavatar.networking.payload;

import io.asinphi.npcavatar.networking.ModPackets;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2CEntityBlobPayload(int entityId, byte[] compressedNbt) implements CustomPacketPayload {

    public static final StreamCodec<ByteBuf, S2CEntityBlobPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    S2CEntityBlobPayload::entityId,
                    ByteBufCodecs.BYTE_ARRAY,
                    S2CEntityBlobPayload::compressedNbt,
                    S2CEntityBlobPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ModPackets.S2C_ENTITY_BLOB;
    }
}
