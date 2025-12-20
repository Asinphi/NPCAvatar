package io.asinphi.npcavatar;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class NPCAvatarClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
        ClientAvatarApplier.init();
        ClientNetworking.init();
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ClientAppliedTracker.clearAll());
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
                if (entity instanceof LocalPlayer player && player == Minecraft.getInstance().player) {
                    ClientAppliedTracker.clearAll();
                }
                ClientAvatarApplier.processEntity(entity);
            }
        );
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {

            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath(NPCAvatar.MOD_ID, "avatar_reloader");
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                ClientAppliedTracker.clearAll();
            }
        });
    }
}