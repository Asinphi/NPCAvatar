package io.asinphi.npcavatar.avatar;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public interface AvatarBlobAppliedCallback {

    /**
     * Event fired after an avatar blob is successfully applied to an entity.
     */
    Event<AvatarBlobAppliedCallback> EVENT = EventFactory.createArrayBacked(AvatarBlobAppliedCallback.class,
            (listeners) -> (target, source, blob) -> {
                for (AvatarBlobAppliedCallback listener : listeners) {
                    listener.onAvatarApplied(target, source, blob);
                }
            });

    /**
     * Called when an avatar blob is applied.
     *
     * @param target The entity that received the avatar.
     * @param source The player who provided the avatar data (the sender).
     * @param blob   The raw avatar data that was applied.
     */
    void onAvatarApplied(Entity target, ServerPlayer source, byte[] blob);
}
