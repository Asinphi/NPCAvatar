package io.asinphi.blackages.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.asinphi.blackages.avatar.AvatarStoreState;
import io.asinphi.blackages.avatar.EntityAvatarManager;
import io.asinphi.blackages.config.Lang;
import io.asinphi.blackages.networking.payload.S2CRequestLocalUploadPayload;
import io.asinphi.blackages.util.AvatarIdUtil;
import io.asinphi.blackages.util.PermissionUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
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
                                            return requestUpload(ctx,
                                                    StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "id"));
                                        })
                                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                                .executes(ctx -> requestUpload(ctx,
                                                        StringArgumentType.getString(ctx, "id"),
                                                        StringArgumentType.getString(ctx, "path")))
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
                                                Lang.send(ctx.getSource(), "commands.apply.console_requires_target");
                                                return 0;
                                            }

                                            ResourceLocation avatarId = AvatarIdUtil.parseWithDefaultNamespace(
                                                    StringArgumentType.getString(ctx, "avatarId")
                                            );

                                            if (!AvatarStoreState.get(player.serverLevel()).contains(avatarId)) {
                                                Lang.send(player, "commands.apply.not_uploaded",
                                                        Placeholder.unparsed("id", avatarId.getPath()));
                                                return 0;
                                            }

                                            Entity target = raycastEntity(player, MAX_DISTANCE);
                                            if (target == null) {
                                                Lang.send(player, "commands.common.no_entity_in_sight");
                                                return 0;
                                            }

                                            EntityAvatarManager.applyAvatarId(target, avatarId);

                                            Lang.send(player, "commands.apply.applied_to_one",
                                                    Placeholder.unparsed("id", avatarId.getPath()),
                                                    Placeholder.unparsed("target", target.getDisplayName().getString()));
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
                                                        Lang.send(ctx.getSource(), "commands.apply.not_uploaded",
                                                                Placeholder.unparsed("id", avatarId.getPath()));
                                                        return 0;
                                                    }

                                                    Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "target");
                                                    for (Entity e : targets) {
                                                        EntityAvatarManager.applyAvatarId(e, avatarId);
                                                    }

                                                    int count = targets.size();
                                                    Lang.send(ctx.getSource(), "commands.apply.applied_to_many",
                                                            Placeholder.unparsed("id", avatarId.getPath()),
                                                            Placeholder.unparsed("count", Integer.toString(count)));
                                                    return count;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("copy")
                                .requires(src -> {
                                    if (src.getEntity() instanceof ServerPlayer player) {
                                        return PermissionUtil.check(player, "blackages.apply", 2);
                                    }
                                    // console / command blocks / functions
                                    return src.hasPermission(2);
                                })
                                .then(Commands.argument("source", EntityArgument.entities())
                                        .executes(ctx -> {
                                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer executor)) {
                                                Lang.send(ctx.getSource(), "commands.copy.console_requires_targets");
                                                return 0;
                                            }

                                            Entity source = EntityArgument.getEntity(ctx, "source");

                                            Entity target = raycastEntity(executor, MAX_DISTANCE);
                                            if (target == null) {
                                                Lang.send(executor, "commands.common.no_entity_in_sight");
                                                return 0;
                                            }

                                            EntityAvatarManager.copyTo(source, List.of(target));

                                            Lang.send(executor, "commands.copy.copied_to_one",
                                                    Placeholder.unparsed("source", source.getDisplayName().getString()));
                                            return 1;
                                        })
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(ctx -> {
                                                    Entity source = EntityArgument.getEntity(ctx, "source");
                                                    Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");

                                                    EntityAvatarManager.copyTo(source, targets);

                                                    Lang.send(ctx.getSource(), "commands.copy.copied_to_many",
                                                            Placeholder.unparsed("source", source.getDisplayName().getString()),
                                                            Placeholder.unparsed("count", Integer.toString(targets.size())));
                                                    return targets.size();
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("copyfromplayer")
                                .requires(src ->
                                        // allow console too; only gate permission when a player is the executor
                                        !(src.getEntity() instanceof ServerPlayer p) || PermissionUtil.check(p, "blackages.apply", 2)
                                )
                                .then(Commands.argument("player", EntityArgument.player())

                                        // 1) copyfromplayer <player>  (raycast fallback, player executor only)
                                        .executes(ctx -> {
                                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer executor)) {
                                                Lang.send(ctx.getSource(), "commands.copyfromplayer.console_requires_targets");
                                                return 0;
                                            }

                                            ServerPlayer sourcePlayer = EntityArgument.getPlayer(ctx, "player");

                                            Entity target = raycastEntity(executor, MAX_DISTANCE);
                                            if (target == null) {
                                                Lang.send(executor, "commands.common.no_entity_in_sight");
                                                return 0;
                                            }

                                            EntityAvatarManager.copyTo(sourcePlayer, List.of(target));

                                            Lang.send(executor, "commands.copyfromplayer.requested_to_one",
                                                    Placeholder.unparsed("player", sourcePlayer.getName().getString()));
                                            return 1;
                                        })

                                        // 2) copyfromplayer <player> <targets>  (selectors/UUID/name; console-friendly)
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(ctx -> {
                                                    ServerPlayer sourcePlayer = EntityArgument.getPlayer(ctx, "player");
                                                    Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
                                                    if (targets.isEmpty()) {
                                                        Lang.send(ctx.getSource(), "commands.common.no_targets_matched");
                                                        return 0;
                                                    }

                                                    int count = EntityAvatarManager.copyTo(sourcePlayer, targets);

                                                    Lang.send(ctx.getSource(), "commands.copyfromplayer.requested_to_many",
                                                            Placeholder.unparsed("player", sourcePlayer.getName().getString()),
                                                            Placeholder.unparsed("count", Integer.toString(count)));
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
                                        Lang.send(src, "commands.clear.console_requires_target");
                                        return 0;
                                    }

                                    Entity target = raycastEntity(player, MAX_DISTANCE);
                                    if (target == null) {
                                        Lang.send(player, "commands.common.no_entity_in_sight");
                                        return 0;
                                    }

                                    EntityAvatarManager.clearAvatarFromEntity(target);

                                    Lang.send(player, "commands.clear.cleared_one",
                                            Placeholder.unparsed("target", target.getDisplayName().getString()));
                                    return 1;
                                })

                                // 2) clear <target>
                                .then(Commands.argument("target", EntityArgument.entities())
                                        .executes(ctx -> {
                                            Collection<? extends Entity> targets =
                                                    EntityArgument.getEntities(ctx, "target");

                                            int count = 0;
                                            for (Entity e : targets) {
                                                EntityAvatarManager.clearAvatarFromEntity(e);
                                                count++;
                                            }

                                            Lang.send(ctx.getSource(), "commands.clear.cleared_many",
                                                    Placeholder.unparsed("count", Integer.toString(count)));
                                            return count;
                                        })
                                )
                        )
        );
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
            Lang.send(player, "commands.upload.invalid_id",
                    Placeholder.unparsed("id", rawId));
            return 0;
        }

        // Ask the client to load rawPath relative to figura/avatars (or absolute if they provide it)
        ServerPlayNetworking.send(player, new S2CRequestLocalUploadPayload(avatarId, rawPath));

        Lang.send(player, "commands.upload.requested",
                Placeholder.unparsed("id", avatarId.getPath()),
                Placeholder.unparsed("path", rawPath));
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
