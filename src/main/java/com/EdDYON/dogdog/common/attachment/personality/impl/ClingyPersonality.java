package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Comparator;
import java.util.WeakHashMap;

public class ClingyPersonality extends Personality {
    private static final int CLING_RANGE_SQR = 25;
    private static final int SNUGGLE_RANGE_SQR = 4;
    private static final int ANXIETY_RANGE_SQR = 64;
    private static final int TELEPORT_RANGE_SQR = 576;
    private static final int PROTECT_COOLDOWN = 40;
    private static final int COMFORT_COOLDOWN = 160;
    private static final int COMFORT_DURATION = 100;
    private static final int COMFORT_CLING_TICKS = 40;

    private static class ClingyState {
        int anxietyTicks = 0;
        int protectCooldown = 0;
        int clingTicks = 0;
        int comfortCooldown = 0;
        int comfortTicks = 0;
    }

    private final WeakHashMap<Wolf, ClingyState> states = new WeakHashMap<>();

    private ClingyState getState(Wolf wolf) {
        return states.computeIfAbsent(wolf, ignored -> new ClingyState());
    }

    @Override public String getId() { return "clingy"; }

    @Override
    public void applyAttributes(Wolf wolf) {
        super.applyAttributes(wolf);
        setAttr(wolf, Attributes.MAX_HEALTH, 24.0);
        setAttr(wolf, Attributes.MOVEMENT_SPEED, 0.34);
        setAttr(wolf, Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    public void onTick(Wolf wolf) {
        if (wolf.level().isClientSide) {
            return;
        }

        ClingyState state = getState(wolf);
        if (state.protectCooldown > 0) {
            state.protectCooldown--;
        }
        if (state.comfortCooldown > 0) {
            state.comfortCooldown--;
        }

        if (!(wolf.getOwner() instanceof Player owner)) {
            state.comfortTicks = 0;
            wolf.setIsInterested(false);
            return;
        }

        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        double distSqr = wolf.distanceToSqr(owner);
        ServerLevel serverLevel = (ServerLevel) wolf.level();
        boolean threatNearby = findThreat(wolf, owner) != null;

        if (state.comfortTicks > 0) {
            if (shouldEndComfort(owner, distSqr, threatNearby) || wolf.isOrderedToSit()) {
                endComfort(state, wolf, distSqr <= CLING_RANGE_SQR);
            } else {
                maintainComfort(wolf, owner, data, state, distSqr, serverLevel);
                return;
            }
        }

        wolf.setIsInterested(distSqr <= CLING_RANGE_SQR);

        if (distSqr <= CLING_RANGE_SQR) {
            state.clingTicks = Math.min(200, state.clingTicks + 2);
            state.anxietyTicks = Math.max(0, state.anxietyTicks - 4);
            applyCloseBuffs(wolf, owner, data, distSqr, serverLevel);

            if (canStartComfort(wolf, owner, data, state, distSqr, threatNearby)) {
                startComfort(wolf, owner, state, serverLevel);
                return;
            }
        } else {
            state.clingTicks = Math.max(0, state.clingTicks - 3);
        }

        if (distSqr > ANXIETY_RANGE_SQR && !data.isHomeMode()) {
            state.anxietyTicks = Math.min(200, state.anxietyTicks + 2);
            wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, state.anxietyTicks >= 120 ? 1 : 0, false, false));
            if (serverLevel.getGameTime() % 20L == 0) {
                serverLevel.sendParticles(ParticleTypes.CLOUD, wolf.getX(), wolf.getY() + 0.4, wolf.getZ(), 2, 0.15, 0.15, 0.15, 0.0);
            }

            if (distSqr > CLING_RANGE_SQR) {
                wolf.getNavigation().moveTo(owner, state.anxietyTicks >= 120 ? 1.55D : 1.35D);
            }

            if (distSqr > TELEPORT_RANGE_SQR && serverLevel.getGameTime() % 20L == 0) {
                teleportNearOwner(wolf, owner);
            }
        } else {
            state.anxietyTicks = Math.max(0, state.anxietyTicks - 2);
        }
    }

    @Override
    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
        if (event.isCanceled()) {
            return;
        }

        ClingyState state = getState(wolf);
        if (state.protectCooldown > 0) {
            return;
        }
        if (state.comfortTicks == 0 && wolf.distanceToSqr(player) > ANXIETY_RANGE_SQR) {
            return;
        }

        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : findThreat(wolf, player);

        float reduction = data.getAffinity() >= 100 ? 0.78f : 0.88f;
        event.setAmount(event.getAmount() * reduction);

        if (attacker != null && attacker != player) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
            if (event.getSource().getDirectEntity() == attacker) {
                attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
            }
            wolf.setTarget(attacker);
            wolf.getNavigation().moveTo(attacker, 1.4D);
        }

        if (data.getAffinity() >= 100) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 80, 0, false, false));
            clearMinorDebuff(player, MobEffects.MOVEMENT_SLOWDOWN);
            clearMinorDebuff(player, MobEffects.DIG_SLOWDOWN);
        }

        state.protectCooldown = PROTECT_COOLDOWN;
        state.comfortCooldown = COMFORT_COOLDOWN;
        state.comfortTicks = 0;
        state.clingTicks = Math.max(0, state.clingTicks - 40);
        wolf.setIsInterested(true);

        if (wolf.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART, player.getX(), player.getEyeY(), player.getZ(), 4, 0.2, 0.2, 0.2, 0.0);
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, wolf.getX(), wolf.getY() + 0.6, wolf.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        }
        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.NEUTRAL, 0.8f, 1.3f);
    }

    @Override
    public void onWolfDamaged(LivingIncomingDamageEvent event, Wolf wolf) {
        ClingyState state = getState(wolf);
        state.comfortTicks = 0;

        if (!(wolf.getOwner() instanceof Player owner)) {
            return;
        }

        if (state.anxietyTicks >= 100 && wolf.distanceToSqr(owner) > ANXIETY_RANGE_SQR) {
            event.setAmount(event.getAmount() * 1.15f);
        } else if (wolf.distanceToSqr(owner) <= CLING_RANGE_SQR) {
            event.setAmount(event.getAmount() * 0.9f);
        }
    }

    private void applyCloseBuffs(Wolf wolf, Player owner, PetData data, double distSqr, ServerLevel serverLevel) {
        if (serverLevel.getGameTime() % 20L != 0) {
            return;
        }

        owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, 0, false, false));
        owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, false, false));

        if (distSqr <= SNUGGLE_RANGE_SQR) {
            owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 40, 0, false, false));
        }

        serverLevel.sendParticles(ParticleTypes.HEART, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
    }

    private boolean canStartComfort(Wolf wolf, Player owner, PetData data, ClingyState state, double distSqr, boolean threatNearby) {
        if (data.getAffinity() < 100 || data.isHomeMode() || state.comfortCooldown > 0 || state.anxietyTicks > 0 || distSqr > SNUGGLE_RANGE_SQR) {
            return false;
        }
        if (!owner.isShiftKeyDown() || state.clingTicks < COMFORT_CLING_TICKS) {
            return false;
        }
        if (wolf.isOrderedToSit() || wolf.getTarget() != null || owner.isSprinting() || owner.isInWaterOrBubble() || !owner.onGround()) {
            return false;
        }
        if (owner.getDeltaMovement().horizontalDistanceSqr() > 0.01 || threatNearby) {
            return false;
        }
        return true;
    }

    private boolean shouldEndComfort(Player owner, double distSqr, boolean threatNearby) {
        return owner.isSprinting()
                || owner.isInWaterOrBubble()
                || owner.isFallFlying()
                || owner.getDeltaMovement().horizontalDistanceSqr() > 0.08
                || threatNearby
                || distSqr > CLING_RANGE_SQR;
    }

    private void startComfort(Wolf wolf, Player owner, ClingyState state, ServerLevel serverLevel) {
        state.comfortTicks = COMFORT_DURATION;
        state.comfortCooldown = COMFORT_COOLDOWN;
        state.clingTicks = 0;
        wolf.setIsInterested(true);
        wolf.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        wolf.getNavigation().moveTo(owner, 1.15D);
        serverLevel.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.0, owner.getZ(), 6, 0.35, 0.2, 0.35, 0.0);
        serverLevel.sendParticles(ParticleTypes.HEART, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 4, 0.2, 0.15, 0.2, 0.0);
        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.WOLF_AMBIENT, SoundSource.NEUTRAL, 0.6f, 1.4f);
    }

    private void maintainComfort(Wolf wolf, Player owner, PetData data, ClingyState state, double distSqr, ServerLevel serverLevel) {
        state.comfortTicks--;
        wolf.setIsInterested(true);
        wolf.getLookControl().setLookAt(owner, 30.0F, 30.0F);

        if (distSqr > 2.25) {
            wolf.getNavigation().moveTo(owner, 1.18D);
        } else {
            wolf.getNavigation().stop();
        }

        if (serverLevel.getGameTime() % 10L == 0) {
            serverLevel.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.0, owner.getZ(), 2, 0.25, 0.15, 0.25, 0.0);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 1, 0.15, 0.1, 0.15, 0.0);
        }

        if (serverLevel.getGameTime() % 20L == 0) {
            owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, 0, false, false));
            owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 40, 0, false, false));
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 0, false, false));
            owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false));
            wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, false, false));
        }
    }

    private void teleportNearOwner(Wolf wolf, Player owner) {
        Vec3 offset = new Vec3((wolf.getId() & 1) == 0 ? -0.9 : 0.9, 0.0, -0.6).yRot((float) (-owner.getYRot() * Math.PI / 180.0));
        Vec3 destination = owner.position().add(offset);
        wolf.teleportTo(destination.x, owner.getY(), destination.z);
    }

    private Monster findThreat(Wolf wolf, Player owner) {
        return wolf.level().getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(6.0)).stream()
                .filter(Entity::isAlive)
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .orElse(null);
    }

    private void endComfort(ClingyState state, Wolf wolf, boolean stayInterested) {
        state.comfortTicks = 0;
        wolf.setIsInterested(stayInterested);
    }

    private void clearMinorDebuff(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        if (player.hasEffect(effect)) {
            player.removeEffect(effect);
        }
    }
}
