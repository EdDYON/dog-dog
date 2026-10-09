package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
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

public class LazyPersonality extends Personality {
    private static final float MAX_SLEEPINESS = 100.0f;
    private static final int BURST_TICKS = 100;
    private static final int FATIGUE_TICKS = 140;

    private static class LazyState {
        float sleepiness = 0.0f;
        int burstTicks = 0;
        int fatigueTicks = 0;
        int empoweredHits = 0;
    }

    private final WeakHashMap<Wolf, LazyState> states = new WeakHashMap<>();

    private LazyState getState(Wolf wolf) {
        return states.computeIfAbsent(wolf, ignored -> new LazyState());
    }

    @Override public String getId() { return "lazy"; }

    @Override
    public void applyAttributes(Wolf wolf) {
        super.applyAttributes(wolf);
        setAttr(wolf, Attributes.MAX_HEALTH, 32.0);
        setAttr(wolf, Attributes.MOVEMENT_SPEED, 0.2);
        setAttr(wolf, Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    public void onTick(Wolf wolf) {
        if (wolf.level().isClientSide) {
            return;
        }

        LazyState state = getState(wolf);
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        Player owner = wolf.getOwner() instanceof Player player ? player : null;
        ServerLevel serverLevel = (ServerLevel) wolf.level();

        if (state.burstTicks > 0) {
            state.burstTicks--;
            wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10, 1, false, false));
            wolf.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 0, false, false));
            if (wolf.tickCount % 10 == 0) {
                serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            }

            if (wolf.getTarget() == null && owner != null) {
                Monster threat = findThreat(wolf, owner);
                if (threat != null) {
                    wolf.setTarget(threat);
                    wolf.getNavigation().moveTo(threat, 1.25D);
                }
            }

            if (state.burstTicks == 0) {
                state.fatigueTicks = FATIGUE_TICKS;
                wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.WOLF_WHINE, SoundSource.NEUTRAL, 0.8f, 0.8f);
            }
        } else if (state.fatigueTicks > 0) {
            state.fatigueTicks--;
            wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 1, false, false));
            wolf.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 0, false, false));
            if (wolf.tickCount % 30 == 0) {
                serverLevel.sendParticles(ParticleTypes.CLOUD, wolf.getX(), wolf.getY() + 0.4, wolf.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
            }
        }

        if (wolf.isOrderedToSit() && wolf.getTarget() == null && state.burstTicks == 0 && state.fatigueTicks == 0) {
            if (wolf.tickCount % 20 == 0) {
                if (wolf.getHealth() < wolf.getMaxHealth()) {
                    wolf.heal(1.5f);
                    serverLevel.sendParticles(ParticleTypes.ENCHANT, wolf.getX(), wolf.getY() + 1.2, wolf.getZ(), 3, 0.1, 0.1, 0.1, 0.0);
                }

                float gain = 4.0f;
                if (isCozy(wolf)) {
                    gain += 2.0f;
                }
                if (owner != null && owner.distanceToSqr(wolf) < 25.0) {
                    gain += 1.0f;
                }
                state.sleepiness = Math.min(MAX_SLEEPINESS, state.sleepiness + gain);

                if (state.sleepiness >= 60.0f && data.getAffinity() >= 100 && owner != null && owner.distanceToSqr(wolf) < 36.0) {
                    owner.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1, 0, false, false));
                }
            }

            if (state.sleepiness >= MAX_SLEEPINESS && wolf.tickCount % 40 == 0) {
                serverLevel.sendParticles(ParticleTypes.CLOUD, wolf.getX(), wolf.getY() + 1.0, wolf.getZ(), 3, 0.15, 0.1, 0.15, 0.0);
                serverLevel.sendParticles(ParticleTypes.NOTE, wolf.getX(), wolf.getY() + 1.2, wolf.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
        } else if (state.burstTicks == 0 && state.sleepiness > 0.0f && wolf.tickCount % 20 == 0) {
            state.sleepiness = Math.max(0.0f, state.sleepiness - 1.5f);
        }

        if (!wolf.isOrderedToSit()
                && wolf.getTarget() == null
                && state.burstTicks == 0
                && wolf.getHealth() <= wolf.getMaxHealth() * 0.4f
                && wolf.tickCount % 80 == 0
                && owner != null
                && owner.distanceToSqr(wolf) < 64.0) {
            wolf.setOrderedToSit(true);
            wolf.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0, false, false));
            serverLevel.sendParticles(ParticleTypes.CLOUD, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
        }
    }

    @Override
    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
        if (event.isCanceled() || wolf.distanceToSqr(player) > 100.0) {
            return;
        }

        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : findThreat(wolf, player);
        tryWakeBurst(wolf, player, attacker);
    }

    @Override
    public void onWolfDamaged(LivingIncomingDamageEvent event, Wolf wolf) {
        LazyState state = getState(wolf);
        if (state.burstTicks > 0) {
            event.setAmount(event.getAmount() * 0.8f);
            return;
        }

        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        Player owner = wolf.getOwner() instanceof Player player ? player : null;
        tryWakeBurst(wolf, owner, attacker);
    }

    @Override
    public void onDamageDealt(LivingIncomingDamageEvent event, Wolf wolf) {
        LazyState state = getState(wolf);
        if (state.burstTicks <= 0) {
            return;
        }

        float bonus = 2.0f;
        if (state.empoweredHits > 0) {
            bonus += 4.0f;
            state.empoweredHits--;
            event.getEntity().knockback(1.0F, wolf.getX() - event.getEntity().getX(), wolf.getZ() - event.getEntity().getZ());
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            if (wolf.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, event.getEntity().getX(), event.getEntity().getY() + 1.0, event.getEntity().getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        event.setAmount(event.getAmount() + bonus);
    }

    private void tryWakeBurst(Wolf wolf, Player owner, LivingEntity attacker) {
        LazyState state = getState(wolf);
        if (state.burstTicks > 0 || state.fatigueTicks > 0 || state.sleepiness < MAX_SLEEPINESS) {
            return;
        }

        state.sleepiness = 0.0f;
        state.burstTicks = BURST_TICKS;
        state.empoweredHits = wolf.getData(ModAttachmentTypes.PET_DATA).getAffinity() >= 100 ? 2 : 1;
        wolf.setOrderedToSit(false);
        wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BURST_TICKS, 1, false, false));
        wolf.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BURST_TICKS, 0, false, false));

        LivingEntity target = attacker;
        if (target == null && owner != null) {
            target = findThreat(wolf, owner);
        }
        if (target != null) {
            wolf.setTarget(target);
            wolf.getNavigation().moveTo(target, 1.25D);
        }

        if (wolf.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, wolf.getX(), wolf.getY() + 0.6, wolf.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
        }
        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.NEUTRAL, 1.0f, 0.7f);
    }

    private Monster findThreat(Wolf wolf, Player owner) {
        return wolf.level().getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(8.0)).stream()
                .filter(Monster::isAlive)
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .orElse(null);
    }

    private boolean isCozy(Wolf wolf) {
        return wolf.level().getBlockState(wolf.blockPosition()).is(BlockTags.WOOL_CARPETS)
                || wolf.level().getBlockState(wolf.blockPosition().below()).is(BlockTags.WOOL)
                || wolf.level().getBlockState(wolf.blockPosition().below()).is(BlockTags.BEDS);
    }
}
