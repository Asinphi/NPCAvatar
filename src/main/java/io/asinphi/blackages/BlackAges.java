package io.asinphi.blackages;

import io.asinphi.blackages.attachment.ModAttachments;
import io.asinphi.blackages.command.ServerAvatarCommands;
import io.asinphi.blackages.config.Lang;
import io.asinphi.blackages.networking.ModPayloads;
import io.asinphi.blackages.networking.ServerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlackAges implements ModInitializer {
	public static final String MOD_ID = "black-ages";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
        ModAttachments.init();
        ModPayloads.init();
        ServerNetworking.init();
        Lang.init();
        CommandRegistrationCallback.EVENT.register(
                (dispatcher,
                 registryAccess, environment) ->
                        ServerAvatarCommands.register(dispatcher)
        );
	}

    public static void debug(String msg, Object... args) {
        LOGGER.info("[DEBUG] " + msg, args);
    }
}