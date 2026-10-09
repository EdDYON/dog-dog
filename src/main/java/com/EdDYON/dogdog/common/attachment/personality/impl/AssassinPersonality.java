package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.config.DogDialogs;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Comparator;
import java.util.WeakHashMap;

public class AssassinPersonality extends Personality {
    private static final int EXPOSED_TICKS = 80;
    private static final int CHAIN_WINDOW = 90;
    private static final int SHADOW_MARK_COOLDOWN = 40;
    private static final int LINE_COOLDOWN = 180;
    private static final double GUIDE_RANGE_SQR = 81.0;
    private static final double THREAT_RANGE = 12.0;

    private static class AssassinState {
        int exposedTicks = 0;
        int chainKillWindow = 0;
        int shadowMarkCooldown = 0;
        int lineCooldown = 0;
    }

    private final WeakHashMap<Wolf, AssassinState> states = new WeakHashMap<>();

    private AssassinState getState(Wolf wolf) {
        return states.computeIfAbsent(wolf, ignored -> new AssassinState());
    }

    @Override public String getId() { return "assassin"; }
    @Override public boolean canAttack() { return true; }

    @Override
    public void applyAttributes(Wolf wolf) {
        super.applyAttributes(wolf);
        setAttr(wolf, Attributes.MAX_HEALTH, 18.0);
        setAttr(wolf, Attributes.MOVEMENT_SPEED, 0.39);
        setAttr(wolf, Attributes.ATTACK_DAMAGE, 4.5);
        if (wolf.getHealth() > wolf.getMaxHealth()) {
            wolf.setHealth(wolf.getMaxHealth());
        }
    }

    @Override
    public void onTick(Wolf wolf) {
        if (wolf.level().isClientSide) {
            return;
        }

        AssassinState state = getState(wolf);
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        ServerLevel serverLevel = (ServerLevel) wolf.level();
        Player owner = wolf.getOwner() instanceof Player player ? player : null;

        if (state.exposedTicks > 0) {
            state.exposedTicks--;
        }
        if (state.chainKillWindow > 0) {
            state.chainKillWindow--;
        }
        if (state.shadowMarkCooldown > 0) {
            state.shadowMarkCooldown--;
        }
        if (state.lineCooldown > 0) {
            state.lineCooldown--;
        }

        boolean ownerSneaking = owner != null && owner.isShiftKeyDown();
        boolean stealthArea = isStealthArea(wolf, ownerSneaking);

        if (owner != null && ownerSneaking && wolf.distanceToSqr(owner) <= GUIDE_RANGE_SQR) {
            owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 80, 0, false, false));
            if (data.getAffinity() >= 100) {
                owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false));
            }
            shadowFollowOwner(wolf, owner);
        }

        if (shouldEnterStealth(wolf, state, stealthArea)) {
            wolf.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
            if (wolf.tickCount % 12 == 0) {
                serverLevel.sendParticles(ParticleTypes.SMOKE, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 1, 0.08, 0.08, 0.08, 0.0);
            }
        } else if (state.exposedTicks > 0) {
            wolf.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 0, false, false));
            if (wolf.tickCount % 12 == 0) {
                serverLevel.sendParticles(ParticleTypes.SMOKE, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 2, 0.12, 0.08, 0.12, 0.0);
            }
        }

        if (owner != null && ownerSneaking && wolf.getTarget() == null && state.exposedTicks == 0 && state.shadowMarkCooldown == 0) {
            Monster threat = findShadowMarkedThreat(wolf, owner);
            if (threat != null) {
                threat.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
                if (data.getAffinity() >= 100) {
                    threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
                }
                state.shadowMarkCooldown = SHADOW_MARK_COOLDOWN;
                if (state.lineCooldown == 0) {
                    owner.displayClientMessage(Component.literal("§5[幽灵刺客] " + DogDialogs.getAssassinShadow(wolf.getRandom())), true);
                    state.lineCooldown = LINE_COOLDOWN;
                }
            }
        }
    }

    @Override
    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
        if (event.isCanceled() || wolf.distanceToSqr(player) > 100.0) {
            return;
        }

        AssassinState state = getState(wolf);
        boolean hidden = wolf.hasEffect(MobEffects.INVISIBILITY) && state.exposedTicks == 0;

        if (hidden) {
            event.setAmount(event.getAmount() * 0.9f);
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            attacker.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
            if (hidden) {
                attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0, false, false));
            }
            wolf.setTarget(attacker);
            if (!wolf.isOrderedToSit()) {
                wolf.getNavigation().moveTo(attacker, hidden ? 1.35D : 1.2D);
            }
        }
    }

    @Override
    public void onWolfDamaged(LivingIncomingDamageEvent event, Wolf wolf) {
        AssassinState state = getState(wolf);
        boolean hidden = wolf.hasEffect(MobEffects.INVISIBILITY) && state.exposedTicks == 0;

        if (hidden) {
            event.setAmount(event.getAmount() * 0.82f);
            breakStealth(wolf, state, false);
        } else if (state.exposedTicks > 0) {
            event.setAmount(event.getAmount() * 1.08f);
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker && !wolf.isOrderedToSit()) {
            wolf.setTarget(attacker);
        }
    }

    @Override
    public void onDamageDealt(LivingIncomingDamageEvent event, Wolf wolf) {
        AssassinState state = getState(wolf);
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        LivingEntity target = event.getEntity();
        Player owner = wolf.getOwner() instanceof Player player ? player : null;
        boolean stealthStrike = wolf.hasEffect(MobEffects.INVISIBILITY) && state.exposedTicks == 0;
        boolean night = wolf.level().isNight();

        if (stealthStrike) {
            float multiplier = night ? 2.8f : 2.25f;
            if (state.chainKillWindow > 0) {
                multiplier += 0.35f;
            }
            event.setAmount(event.getAmount() * multiplier);
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
            if (data.getAffinity() >= 100) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
            }
            breakStealth(wolf, state, true);
            if (wolf.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.8, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
            wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.NEUTRAL, 1.0F, 0.9F);
        } else if (state.chainKillWindow > 0) {
            event.setAmount(event.getAmount() + 1.0f);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false));
        }

        boolean isKill = target.getHealth() - event.getAmount() <= 0;
        if (isKill) {
            if (data.getAffinity() < 100) {
                data.increaseAffinity(1);
            }
            state.chainKillWindow = CHAIN_WINDOW;
            wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, false, false));

            if (data.getAffinity() >= 100 || night) {
                state.exposedTicks = 0;
                wolf.removeEffect(MobEffects.WEAKNESS);
                wolf.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, false, false));
                wolf.setTarget(null);
                if (stealthStrike && night && owner instanceof ServerPlayer sp) {
                    DogUtils.grantAdvancement(sp, "one_hit_fade");
                }
            }

            if (owner != null && state.lineCooldown == 0) {
                owner.displayClientMessage(Component.literal("§5[幽灵刺客] " + DogDialogs.getAssassinShadow(wolf.getRandom())), true);
                state.lineCooldown = LINE_COOLDOWN;
            }
        }
    }

    private boolean shouldEnterStealth(Wolf wolf, AssassinState state, boolean stealthArea) {
        if (wolf.isOrderedToSit() || wolf.getTarget() != null || state.exposedTicks > 0) {
            return false;
        }
        return stealthArea || state.chainKillWindow > 0;
    }

    private boolean isStealthArea(Wolf wolf, boolean ownerSneaking) {
        return ownerSneaking
                || wolf.level().isNight()
                || wolf.level().getMaxLocalRawBrightness(wolf.blockPosition()) <= 7;
    }

    private void shadowFollowOwner(Wolf wolf, Player owner) {
        if (wolf.isOrderedToSit() || wolf.getTarget() != null) {
            return;
        }

        Vec3 offset = new Vec3((wolf.getId() & 1) == 0 ? -1.2 : 1.2, 0.0, -0.8)
                .yRot((float) (-owner.getYRot() * Math.PI / 180.0));
        Vec3 destination = owner.position().add(offset);
        if (wolf.distanceToSqr(destination) > 3.0) {
            wolf.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.15D);
        } else {
            wolf.getNavigation().stop();
        }
    }

    private Monster findShadowMarkedThreat(Wolf wolf, Player owner) {
        return owner.level().getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(THREAT_RANGE, 4.0, THREAT_RANGE)).stream()
                .filter(Monster::isAlive)
                .filter(monster -> owner.hasLineOfSight(monster) || wolf.hasLineOfSight(monster) || owner.distanceToSqr(monster) <= 16.0)
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .orElse(null);
    }

    private void breakStealth(Wolf wolf, AssassinState state, boolean afterStrike) {
        wolf.removeEffect(MobEffects.INVISIBILITY);
        state.exposedTicks = afterStrike ? EXPOSED_TICKS : EXPOSED_TICKS / 2;
        wolf.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, afterStrike ? 60 : 40, 0, false, false));
    }
}
