package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.PetPersonality;
import com.EdDYON.dogdog.common.attachment.personality.Personalities;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.config.DogDialogs;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;

public class SentryPersonality extends Personality {
    private static final int SCAN_INTERVAL = 20;
    private static final int MARK_DURATION = 80;
    private static final int ALERT_TICKS = 100;
    private static final int BARK_COOLDOWN = 100;
    private static final int SUPPORT_INTERVAL = 40;
    private static final double CALM_BUFF_RANGE_SQR = 144.0;
    private static final double OWNER_PROTECT_RANGE_SQR = 144.0;

    private static class SentryState {
        int alertTicks = 0;
        int barkCooldown = 0;
        int supportCooldown = 0;
    }

    private final WeakHashMap<Wolf, SentryState> states = new WeakHashMap<>();

    private SentryState getState(Wolf wolf) {
        return states.computeIfAbsent(wolf, ignored -> new SentryState());
    }

    @Override public String getId() { return "sentry"; }
    @Override public boolean canAttack() { return true; }

    @Override
    public void applyAttributes(Wolf wolf) {
        super.applyAttributes(wolf);
        setAttr(wolf, Attributes.MAX_HEALTH, 30.0);
        setAttr(wolf, Attributes.MOVEMENT_SPEED, 0.33);
        setAttr(wolf, Attributes.ATTACK_DAMAGE, 4.5);
    }

    @Override
    public void onTick(Wolf wolf) {
        if (wolf.level().isClientSide) {
            return;
        }

        SentryState state = getState(wolf);
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        Player owner = wolf.getOwner() instanceof Player player ? player : null;
        ServerLevel serverLevel = (ServerLevel) wolf.level();
        boolean guardMode = wolf.isOrderedToSit() || data.isHomeMode();

        if (state.alertTicks > 0) {
            state.alertTicks--;
        }
        if (state.barkCooldown > 0) {
            state.barkCooldown--;
        }
        if (state.supportCooldown > 0) {
            state.supportCooldown--;
        }

        if (state.alertTicks > 0) {
            wolf.setIsInterested(true);
            if (!wolf.isOrderedToSit()) {
                wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 15, 0, false, false));
            }
            if (wolf.tickCount % 10 == 0) {
                serverLevel.sendParticles(ParticleTypes.END_ROD, wolf.getX(), wolf.getY() + 0.9, wolf.getZ(), 1, 0.15, 0.15, 0.15, 0.0);
            }
        } else {
            wolf.setIsInterested(owner != null && wolf.distanceToSqr(owner) <= CALM_BUFF_RANGE_SQR);
        }

        if (wolf.tickCount % SCAN_INTERVAL != 0) {
            return;
        }

        Monster threat = findThreat(wolf, owner, guardMode);
        if (threat != null) {
            state.alertTicks = ALERT_TICKS;
            markThreat(threat, data, guardMode);
            rallyPack(wolf, threat, owner, guardMode);
            alertOwner(wolf, owner, threat, state, guardMode);

            if (!wolf.isOrderedToSit()) {
                wolf.setTarget(threat);
                wolf.getNavigation().moveTo(threat, state.alertTicks > 0 ? 1.25D : 1.1D);
            }
            return;
        }

        applyCalmWatchBuff(wolf, owner, data, serverLevel, guardMode, state);
    }

    @Override
    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
        if (event.isCanceled() || wolf.distanceToSqr(player) > OWNER_PROTECT_RANGE_SQR) {
            return;
        }

        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        SentryState state = getState(wolf);
        boolean guardMode = wolf.isOrderedToSit() || data.isHomeMode();

        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : findThreat(wolf, player, true);
        if (attacker != null) {
            markThreat(attacker, data, true);
            rallyPack(wolf, attacker, player, guardMode);
            if (!wolf.isOrderedToSit()) {
                wolf.setTarget(attacker);
                wolf.getNavigation().moveTo(attacker, 1.28D);
            }
        }

        state.alertTicks = Math.max(state.alertTicks, ALERT_TICKS);
        if (state.barkCooldown == 0) {
            alertOwner(wolf, player, attacker, state, guardMode);
        }

        float reduction = guardMode ? 0.86f : 0.92f;
        if (attacker != null && attacker.hasEffect(MobEffects.GLOWING)) {
            reduction -= 0.05f;
        }
        if (data.getAffinity() >= 100) {
            reduction -= 0.04f;
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, false));
        }
        event.setAmount(event.getAmount() * Math.max(0.72f, reduction));

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, false, false));
        if (wolf.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getEyeY(), player.getZ(), 4, 0.2, 0.25, 0.2, 0.01);
        }
    }

    @Override
    public void onWolfDamaged(LivingIncomingDamageEvent event, Wolf wolf) {
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        SentryState state = getState(wolf);
        boolean guardMode = wolf.isOrderedToSit() || data.isHomeMode();

        if (guardMode) {
            event.setAmount(event.getAmount() * 0.88f);
        } else if (state.alertTicks > 0) {
            event.setAmount(event.getAmount() * 0.93f);
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            markThreat(attacker, data, guardMode);
            state.alertTicks = Math.max(state.alertTicks, ALERT_TICKS);
            Player owner = wolf.getOwner() instanceof Player player ? player : null;
            rallyPack(wolf, attacker, owner, guardMode);
            if (!wolf.isOrderedToSit()) {
                wolf.setTarget(attacker);
            }
        }
    }

    @Override
    public void onDamageDealt(LivingIncomingDamageEvent event, Wolf wolf) {
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        LivingEntity target = event.getEntity();

        if (target.hasEffect(MobEffects.GLOWING)) {
            event.setAmount(event.getAmount() + 1.5f);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, false));
        }
        if (data.getAffinity() >= 100 && target.hasEffect(MobEffects.WEAKNESS)) {
            event.setAmount(event.getAmount() + 0.5f);
        }

        if (target.getHealth() - event.getAmount() <= 0 && data.getAffinity() < 100) {
            data.increaseAffinity(1);
        }
    }

    private void applyCalmWatchBuff(Wolf wolf, Player owner, PetData data, ServerLevel serverLevel, boolean guardMode, SentryState state) {
        if (owner == null || wolf.distanceToSqr(owner) > CALM_BUFF_RANGE_SQR || state.supportCooldown > 0) {
            return;
        }

        boolean darkArea = owner.level().isNight() || owner.level().getMaxLocalRawBrightness(owner.blockPosition()) <= 7;
        if (!guardMode && !darkArea) {
            return;
        }

        owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
        if (data.getAffinity() >= 100 && guardMode) {
            owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, false, false));
        }

        state.supportCooldown = SUPPORT_INTERVAL;
        serverLevel.sendParticles(ParticleTypes.END_ROD, wolf.getX(), wolf.getY() + 1.0, wolf.getZ(), 2, 0.15, 0.2, 0.15, 0.0);
    }

    private Monster findThreat(Wolf wolf, Player owner, boolean guardMode) {
        double range = guardMode ? 20.0 : 14.0;
        List<Monster> enemies = owner != null && owner.distanceToSqr(wolf) <= 256.0
                ? owner.level().getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(range, 4.0, range))
                : wolf.level().getEntitiesOfClass(Monster.class, wolf.getBoundingBox().inflate(range, 4.0, range));

        if (enemies.isEmpty()) {
            return null;
        }

        return enemies.stream()
                .filter(Monster::isAlive)
                .filter(monster -> wolf.hasLineOfSight(monster) || (owner != null && owner.hasLineOfSight(monster)) || wolf.distanceToSqr(monster) <= 16.0)
                .min(Comparator.comparingDouble(monster -> threatScore(wolf, owner, monster)))
                .orElse(null);
    }

    private double threatScore(Wolf wolf, Player owner, Monster monster) {
        double score = owner != null ? owner.distanceToSqr(monster) : wolf.distanceToSqr(monster);
        if (monster.getType() == EntityType.CREEPER) {
            score -= 18.0;
        } else if (monster.getType() == EntityType.SKELETON || monster.getType() == EntityType.STRAY) {
            score -= 10.0;
        } else if (monster.getType() == EntityType.WITCH) {
            score -= 8.0;
        }
        return score;
    }

    private void markThreat(LivingEntity target, PetData data, boolean guardMode) {
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_DURATION, 0, false, false));
        if (guardMode || data.getAffinity() >= 50) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0, false, false));
        }
        if (data.getAffinity() >= 100) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
        }
    }

    private void rallyPack(Wolf wolf, LivingEntity threat, Player owner, boolean guardMode) {
        List<Wolf> allies = wolf.level().getEntitiesOfClass(Wolf.class, wolf.getBoundingBox().inflate(18.0, 8.0, 18.0));
        for (Wolf ally : allies) {
            if (!ally.isTame() || owner == null || !ally.isOwnedBy(owner)) {
                continue;
            }

            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 0, false, false));
            if (guardMode && ally.isOrderedToSit()) {
                continue;
            }

            PetPersonality type = ally.getData(ModAttachmentTypes.PET_DATA).getPersonality();
            Personality personality = Personalities.get(type.getSerializedName());
            if (personality != null && personality.canAttack()) {
                ally.setTarget(threat);
            }
        }
    }

    private void alertOwner(Wolf wolf, Player owner, LivingEntity threat, SentryState state, boolean guardMode) {
        state.barkCooldown = BARK_COOLDOWN;
        wolf.level().playSound(null, wolf.blockPosition(), guardMode ? SoundEvents.WOLF_HOWL : SoundEvents.WOLF_GROWL, SoundSource.NEUTRAL, 0.9F, guardMode ? 1.15F : 1.0F);

        if (owner == null) {
            return;
        }

        String threatName = threat != null ? threat.getName().getString() : "危险";
        owner.displayClientMessage(Component.literal("§6[警卫] " + DogDialogs.getSentryAlert(wolf.getRandom()) + " 目标：" + threatName), true);
        if (guardMode && owner.level().isNight() && owner instanceof net.minecraft.server.level.ServerPlayer sp) {
            DogUtils.grantAdvancement(sp, "night_watch");
        }
    }
}
