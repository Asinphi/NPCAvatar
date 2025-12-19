package io.asinphi.npcavatar.networking.payload;

import io.asinphi.npcavatar.networking.ModPackets;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record C2SRequestEntityBlobPayload(int entityId) implements CustomPacketPayload {

    public static final StreamCodec<ByteBuf, C2SRequestEntityBlobPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(C2SRequestEntityBlobPayload::new, C2SRequestEntityBlobPayload::entityId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ModPackets.C2S_REQUEST_ENTITY_BLOB;
    }
}
