package io.asinphi.blackages.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.asinphi.blackages.attachment.ModAttachments;
import io.asinphi.blackages.avatar.AvatarStoreState;
import io.asinphi.blackages.networking.payload.S2CRequestLocalUploadPayload;
import io.asinphi.blackages.util.AvatarIdUtil;
import io.asinphi.blackages.util.PermissionUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public final class ServerAvatarCommands {

    private static final double MAX_DISTANCE = 10.0D;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("serveravatar")
                        .then(Commands.literal("upload")
                                .requires(src ->
                                        src.getEntity() instanceof ServerPlayer player &&
                                                PermissionUtil.check(player, "blackages.upload", 2)
                                )
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(ctx -> {
                                            // Default path = raw id (namespace-less is expected)
                                            return requestUpload(ctx, StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "id"));
                                        })
                                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    return requestUpload(ctx,
                                                            StringArgumentType.getString(ctx, "id"),
                                                            StringArgumentType.getString(ctx, "path"));
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("apply")
                                .requires(src -> {
                                    if (src.getEntity() instanceof ServerPlayer player) {
                                        return PermissionUtil.check(player, "blackages.apply", 2);
                                    }
                                    // console / command blocks / functions
                                    return src.hasPermission(2);
                                })
                                .then(Commands.argument("avatarId", StringArgumentType.string())
                                        .suggests(ServerAvatarCommands::suggestUploadedAvatarIds)

                                        // 1) apply <id> (raycast) — player only
                                        .executes(ctx -> {
                                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "Console must specify a target: /serveravatar apply <id> <target>"
                                                ));
                                                return 0;
                                            }

                                            ResourceLocation avatarId = AvatarIdUtil.parseWithDefaultNamespace(
                                                    StringArgumentType.getString(ctx, "avatarId")
                                            );

                                            if (!AvatarStoreState.get(player.serverLevel()).contains(avatarId)) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "Avatar '" + avatarId.getPath() + "' is not uploaded on this server."
                                                ));
                                                return 0;
                                            }

                                            Entity target = raycastEntity(player, MAX_DISTANCE);
                                            if (target == null) {
                                                ctx.getSource().sendFailure(Component.literal("No entity in sight."));
                                                return 0;
                                            }

                                            target.setAttached(ModAttachments.AVATAR_ID, avatarId);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Applied '" + avatarId.getPath() + "' to " + target.getDisplayName().getString()),
                                                    true
                                            );
                                            return 1;
                                        })

                                        // 2) apply <id> <target> — works from console too
                                        .then(Commands.argument("target", EntityArgument.entities())
                                                .executes(ctx -> {
                                                    ResourceLocation avatarId = AvatarIdUtil.parseWithDefaultNamespace(
                                                            StringArgumentType.getString(ctx, "avatarId")
                                                    );

                                                    // Pick a level to check the store against. For non-player sources,
                                                    // use the command source's level (usually overworld), or better: your global overworld store.
                                                    ServerLevel level = ctx.getSource().getLevel();

                                                    if (!AvatarStoreState.get(level).contains(avatarId)) {
                                                        ctx.getSource().sendFailure(Component.literal(
                                                                "Avatar '" + avatarId.getPath() + "' is not uploaded on this server."
                                                        ));
                                                        return 0;
                                                    }

                                                    Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "target");
                                                    for (Entity e : targets) {
                                                        e.setAttached(ModAttachments.AVATAR_ID, avatarId);
                                                    }

                                                    int count = targets.size();
                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("Applied '" + avatarId.getPath() + "' to " + count + " entity(ies)."),
                                                            true
                                                    );
                                                    return count;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("clear")
                                .requires(src -> {
                                    if (src.getEntity() instanceof ServerPlayer player) {
                                        return PermissionUtil.check(player, "blackages.apply", 2);
                                    }
                                    // console / command blocks / functions
                                    return src.hasPermission(2);
                                })

                                // 1) clear   (raycast fallback if player)
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    Entity executor = src.getEntity();

                                    if (!(executor instanceof ServerPlayer player)) {
                                        src.sendFailure(Component.literal("Console must specify a target."));
                                        return 0;
                                    }

                                    Entity target = raycastEntity(player, MAX_DISTANCE);
                                    if (target == null) {
                                        src.sendFailure(Component.literal("No entity in sight."));
                                        return 0;
                                    }

                                    target.setAttached(ModAttachments.AVATAR_ID, ModAttachments.NONE);

                                    src.sendSuccess(
                                            () -> Component.literal("Cleared avatar from " + target.getDisplayName().getString()),
                                            true
                                    );
                                    return 1;
                                })

                                // 2) clear <target>
                                .then(Commands.argument("target", EntityArgument.entities())
                                        .executes(ctx -> {
                                            Collection<? extends Entity> targets =
                                                    EntityArgument.getEntities(ctx, "target");

                                            int count = 0;
                                            for (Entity e : targets) {
                                                e.setAttached(ModAttachments.AVATAR_ID, ModAttachments.NONE);
                                                count++;
                                            }

                                            int finalCount = count;
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Cleared avatar from " + finalCount + " entity(ies)."),
                                                    true
                                            );
                                            return count;
                                        })
                                )
        ));
    }

    private static CompletableFuture<Suggestions> suggestUploadedAvatarIds(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder
    ) {
        // If no player (e.g., console), just no suggestions.
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return Suggestions.empty();
        }

        var ids = AvatarStoreState.get(player.serverLevel()).getIds();

        // Suggest without requiring "blackages:" prefix
        for (ResourceLocation id : ids) {
            if (!"blackages".equals(id.getNamespace())) continue;

            builder.suggest(id.getPath()); // show only the path part
        }

        return builder.buildFuture();
    }

    private static int requestUpload(CommandContext<CommandSourceStack> ctx, String rawId, String rawPath) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        final ResourceLocation avatarId;
        try {
            avatarId = AvatarIdUtil.parseWithDefaultNamespace(rawId);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Invalid avatar id: " + rawId));
            return 0;
        }

        // Ask the client to load rawPath relative to figura/avatars (or absolute if they provide it)
        ServerPlayNetworking.send(player, new S2CRequestLocalUploadPayload(avatarId, rawPath));

        ctx.getSource().sendSuccess(
                () -> Component.literal("Requested client upload of '" + avatarId + "' from path '" + rawPath + "'."),
                false
        );
        return 1;
    }

    /**
     * Simple entity raycast from player view.
     */
    private static Entity raycastEntity(ServerPlayer player, double maxDistance) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(maxDistance));

        EntityHitResult result = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player.level(),
                player,
                start,
                end,
                player.getBoundingBox().expandTowards(look.scale(maxDistance)).inflate(1.0D),
                e -> !e.isSpectator() && e.isPickable()
        );

        return result != null ? result.getEntity() : null;
    }

    private ServerAvatarCommands() {}
}
