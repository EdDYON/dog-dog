package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Comparator;
import java.util.WeakHashMap;

public class TankPersonality extends Personality {
    private static final int BRACE_TICKS = 50;
    private static final int FATIGUE_TICKS = 120;
    private static final int GUARD_CHECK_INTERVAL = 20;

    private static class TankState {
        int braceTicks = 0;
        int fatigueTicks = 0;
        int protectCooldown = 0;
        float absorbedLoad = 0.0f;
    }

    private final WeakHashMap<Wolf, TankState> states = new WeakHashMap<>();

    private TankState getState(Wolf wolf) {
        return states.computeIfAbsent(wolf, ignored -> new TankState());
    }

    @Override public String getId() { return "tank"; }
    @Override public boolean canAttack() { return true; }

    @Override
    public void applyAttributes(Wolf wolf) {
        super.applyAttributes(wolf);
        setAttr(wolf, Attributes.MAX_HEALTH, 42.0);
        setAttr(wolf, Attributes.ARMOR, 12.0);
        setAttr(wolf, Attributes.ARMOR_TOUGHNESS, 3.0);
        setAttr(wolf, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        setAttr(wolf, Attributes.MOVEMENT_SPEED, 0.22);
        setAttr(wolf, Attributes.ATTACK_DAMAGE, 3.0);
        if (wolf.getHealth() > wolf.getMaxHealth()) {
            wolf.setHealth(wolf.getMaxHealth());
        }
    }

    @Override
    public void onTick(Wolf wolf) {
        if (wolf.level().isClientSide) {
            return;
        }

        TankState state = getState(wolf);
        if (state.protectCooldown > 0) {
            state.protectCooldown--;
        }
        if (state.braceTicks > 0) {
            state.braceTicks--;
            wolf.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 0, false, false));
            if (wolf.tickCount % 10 == 0 && wolf.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
            }
        }

        if (state.fatigueTicks > 0) {
            state.fatigueTicks--;
            wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 1, false, false));
            wolf.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 0, false, false));
        } else if (wolf.tickCount % 20 == 0 && state.absorbedLoad > 0.0f) {
            state.absorbedLoad = Math.max(0.0f, state.absorbedLoad - 2.0f);
        }

        if (!wolf.isOrderedToSit() || wolf.tickCount % GUARD_CHECK_INTERVAL != 0) {
            return;
        }

        if (!(wolf.getOwner() instanceof Player owner) || owner.distanceToSqr(wolf) > 49.0) {
            return;
        }

        Monster nearestThreat = wolf.level().getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(6.0)).stream()
                .filter(Monster::isAlive)
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .orElse(null);

        if (nearestThreat == null) {
            return;
        }

        nearestThreat.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
        nearestThreat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
        wolf.setTarget(nearestThreat);
        wolf.getLookControl().setLookAt(nearestThreat, 30.0F, 30.0F);
        state.braceTicks = Math.max(state.braceTicks, 20);

        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        if (data.getAffinity() >= 100) {
            owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, false, false));
        }

        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 0.5f, 0.9f);
    }

    @Override
    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
        if (event.isCanceled() || wolf.distanceToSqr(player) > 100.0) {
            return;
        }

        TankState state = getState(wolf);
        if (state.protectCooldown > 0) {
            return;
        }

        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        boolean guardMode = wolf.isOrderedToSit() && wolf.distanceToSqr(player) <= 49.0;
        float ratio = guardMode ? 0.45f : 0.3f;
        if (data.getAffinity() >= 100) {
            ratio += 0.15f;
        }
        if (state.fatigueTicks > 0) {
            ratio -= 0.15f;
        }
        ratio = Mth.clamp(ratio, 0.15f, 0.65f);

        float redirectCap = Math.max(0.0f, wolf.getHealth() - 1.0f);
        float redirected = Math.min(event.getAmount() * ratio, redirectCap);
        if (redirected <= 0.0f) {
            return;
        }

        event.setAmount(Math.max(0.0f, event.getAmount() - redirected));
        wolf.hurt(wolf.damageSources().generic(), redirected * (guardMode ? 0.72f : 0.9f));

        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        if (attacker != null && attacker != player) {
            wolf.setTarget(attacker);
            wolf.getLookControl().setLookAt(attacker, 30.0F, 30.0F);
            if (!guardMode) {
                wolf.getNavigation().moveTo(attacker, 1.1D);
            }
        }

        state.braceTicks = Math.max(state.braceTicks, BRACE_TICKS);
        state.protectCooldown = guardMode ? 8 : 14;
        state.absorbedLoad += redirected;

        float overloadThreshold = data.getAffinity() >= 100 ? 24.0f : 18.0f;
        if (state.absorbedLoad >= overloadThreshold) {
            state.absorbedLoad = 0.0f;
            state.fatigueTicks = FATIGUE_TICKS;
        }

        if (wolf.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT, player.getX(), player.getEyeY(), player.getZ(), 5, 0.2, 0.2, 0.2, 0.0);
            serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, wolf.getX(), wolf.getY() + 0.7, wolf.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        }
        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL, 0.9f, guardMode ? 0.7f : 0.85f);

        float futureHealth = player.getHealth() - event.getAmount();
        if (data.getAffinity() >= 100 && futureHealth <= player.getMaxHealth() * 0.25f) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, 0, false, false));
            wolf.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 1, false, false));
        }
    }

    @Override
    public void onWolfDamaged(LivingIncomingDamageEvent event, Wolf wolf) {
        TankState state = getState(wolf);
        float multiplier = wolf.isOrderedToSit() ? 0.7f : 0.85f;
        if (state.braceTicks > 0) {
            multiplier -= 0.15f;
        }
        if (state.fatigueTicks > 0) {
            multiplier += 0.15f;
        }
        event.setAmount(event.getAmount() * Mth.clamp(multiplier, 0.45f, 1.0f));
    }
}
