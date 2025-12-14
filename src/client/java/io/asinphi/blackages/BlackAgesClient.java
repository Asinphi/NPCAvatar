package io.asinphi.blackages;

import io.asinphi.blackages.networking.ModPackets;
import io.asinphi.blackages.networking.ModPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class BlackAgesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
        ClientAvatarApplier.init();
        ClientNetworking.init();
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ClientAppliedTracker.clearAll());
    }
}