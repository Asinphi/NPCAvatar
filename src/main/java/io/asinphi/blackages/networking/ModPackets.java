package io.asinphi.blackages.networking;

import io.asinphi.blackages.networking.payload.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class ModPackets {

    public static final CustomPacketPayload.Type<C2SUploadAvatarPayload> C2S_UPLOAD_AVATAR =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("blackages", "c2s_upload_avatar")
            );

    public static final CustomPacketPayload.Type<C2SRequestAvatarPayload> C2S_REQUEST_AVATAR =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("blackages", "c2s_request_avatar")
            );

    public static final CustomPacketPayload.Type<C2SPlayerAvatarBlobPayload> C2S_PLAYER_AVATAR_BLOB =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("blackages", "c2s_player_avatar_blob")
            );

    public static final CustomPacketPayload.Type<C2SRequestEntityBlobPayload> C2S_REQUEST_ENTITY_BLOB =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("blackages", "c2s_request_entity_blob")
            );

    public static final CustomPacketPayload.Type<S2CRequestLocalUploadPayload> S2C_REQUEST_LOCAL_UPLOAD =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("blackages", "s2c_request_local_upload")
            );

    public static final CustomPacketPayload.Type<S2CAvatarDataPayload> S2C_AVATAR_DATA =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("blackages", "s2c_avatar_data")
            );

    public static final CustomPacketPayload.Type<S2CAvatarInvalidatePayload> S2C_AVATAR_INVALIDATE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("blackages", "s2c_avatar_invalidate")
            );

    public static final CustomPacketPayload.Type<S2CUploadResultPayload> S2C_UPLOAD_RESULT =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("blackages", "s2c_upload_result")
            );

    public static final CustomPacketPayload.Type<S2CRequestPlayerAvatarPayload> S2C_REQUEST_PLAYER_AVATAR =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("blackages", "s2c_request_player_avatar")
            );

    public static final CustomPacketPayload.Type<S2CEntityBlobPayload> S2C_ENTITY_BLOB =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    "blackages", "s2c_entity_blob")
            );

    private ModPackets() {}
}
