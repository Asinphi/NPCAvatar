package io.asinphi.blackages.networking;

import io.asinphi.blackages.networking.payload.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class ModPayloads {

    public static void init() {
        // C2S
        PayloadTypeRegistry.playC2S().register(ModPackets.C2S_UPLOAD_AVATAR, C2SUploadAvatarPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ModPackets.C2S_REQUEST_AVATAR, C2SRequestAvatarPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ModPackets.C2S_PLAYER_AVATAR_BLOB, C2SPlayerAvatarBlobPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ModPackets.C2S_REQUEST_ENTITY_BLOB, C2SRequestEntityBlobPayload.STREAM_CODEC);

        // S2C
        PayloadTypeRegistry.playS2C().register(ModPackets.S2C_REQUEST_LOCAL_UPLOAD, S2CRequestLocalUploadPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ModPackets.S2C_AVATAR_DATA, S2CAvatarDataPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ModPackets.S2C_AVATAR_INVALIDATE, S2CAvatarInvalidatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ModPackets.S2C_UPLOAD_RESULT, S2CUploadResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ModPackets.S2C_REQUEST_PLAYER_AVATAR, S2CRequestPlayerAvatarPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ModPackets.S2C_ENTITY_BLOB, S2CEntityBlobPayload.STREAM_CODEC);
    }

    private ModPayloads() {}
}