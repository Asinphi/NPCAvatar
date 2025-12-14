package io.asinphi.blackages.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.model.AbstractZombieModel.class)
public abstract class AbstractZombieModelMixin<T extends net.minecraft.world.entity.monster.Monster>
        extends net.minecraft.client.model.HumanoidModel<T> {

    // Mixin needs a matching ctor signature for the superclass
    protected AbstractZombieModelMixin(net.minecraft.client.model.geom.ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void blackages$playerArms(
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (!(entity instanceof net.minecraft.world.entity.monster.Zombie)) return;
        // TODO Use Fabric data attachment instead
//        if (!entity.getTags().contains("blackages:player_arms")) return;

        float swing = limbSwing * 0.6662F;
        this.rightArm.xRot = net.minecraft.util.Mth.cos(swing + (float)Math.PI) * 2.0F * limbSwingAmount * 0.5F;
        this.leftArm.xRot  = net.minecraft.util.Mth.cos(swing)                 * 2.0F * limbSwingAmount * 0.5F;

        this.rightArm.yRot = this.leftArm.yRot = 0.0F;
        this.rightArm.zRot = this.leftArm.zRot = 0.0F;

        this.setupAttackAnimation(entity, ageInTicks);
    }
}