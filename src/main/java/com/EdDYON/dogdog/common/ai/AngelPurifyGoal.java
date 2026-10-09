package com.EdDYON.dogdog.common.entity.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class AngelPurifyGoal extends Goal {

    private final Wolf wolf;
    private Player owner;
    private int checkCooldown = 0;

    public AngelPurifyGoal(Wolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.wolf.isOrderedToSit() || this.wolf.getHealth() <= 4.0F || this.wolf.isNoAi()) {
            return false;
        }

        if (!(this.wolf.getOwner() instanceof Player player)) {
            return false;
        }

        if (this.checkCooldown > 0) {
            this.checkCooldown--;
            return false;
        }
        this.checkCooldown = 20;

        if (player.hasEffect(MobEffects.POISON) || player.hasEffect(MobEffects.WITHER)) {
            this.owner = player;
            return true;
        }

        return false;
    }

    @Override
    public void start() {
        this.wolf.getNavigation().moveTo(this.owner, 1.2D);
    }

    @Override
    public void tick() {
        if (this.owner == null) return;

        this.wolf.getLookControl().setLookAt(this.owner, 10.0F, (float)this.wolf.getMaxHeadXRot());

        if (this.wolf.distanceToSqr(this.owner) > 256.0D) {
            this.wolf.getNavigation().moveTo(this.owner, 1.2D);
        }

        if (this.wolf.distanceToSqr(this.owner) < 4.0D) {
            transferEffect(MobEffects.POISON);
            transferEffect(MobEffects.WITHER);
            this.owner = null;
        }
    }

    private void transferEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        if (this.owner.hasEffect(effect)) {
            MobEffectInstance instance = this.owner.getEffect(effect);
            if (instance != null) {
                this.owner.removeEffect(effect);
                this.wolf.addEffect(new MobEffectInstance(effect, instance.getDuration(), instance.getAmplifier()));

                if (!this.wolf.level().isClientSide) {
                    ServerLevel sl = (ServerLevel) this.wolf.level();
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.wolf.getX(), this.wolf.getY() + 0.5, this.wolf.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                    this.wolf.playSound(SoundEvents.WOLF_WHINE, 1.0F, 1.5F);
                }
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.owner != null && this.owner.isAlive() &&
                (this.owner.hasEffect(MobEffects.POISON) || this.owner.hasEffect(MobEffects.WITHER)) &&
                this.wolf.getHealth() > 4.0F && !this.wolf.isOrderedToSit();
    }

    @Override
    public void stop() {
        this.owner = null;
        this.wolf.getNavigation().stop();
    }
}