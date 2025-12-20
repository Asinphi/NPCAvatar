package io.asinphi.npcavatar;

import io.asinphi.npcavatar.attachment.ModAttachments;
import io.asinphi.npcavatar.command.ServerAvatarCommands;
import io.asinphi.npcavatar.config.Config;
import io.asinphi.npcavatar.config.Lang;
import io.asinphi.npcavatar.networking.ModPayloads;
import io.asinphi.npcavatar.networking.ServerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NPCAvatar implements ModInitializer {
	public static final String MOD_ID = "npc-avatar";

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
        Config.load();
        CommandRegistrationCallback.EVENT.register(
                (dispatcher,
                 registryAccess, environment) ->
                        ServerAvatarCommands.register(dispatcher)
        );
	}

    public static void reload() {
        Lang.reload();
        Config.reload();
    }

    public static void debug(String msg, Object... args) {
        LOGGER.info("[DEBUG] " + msg, args);
    }
}