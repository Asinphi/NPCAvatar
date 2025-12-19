package io.asinphi.npcavatar;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

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
    }
}