package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.config.DogDialogs;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Comparator;
import java.util.WeakHashMap;

public class AmphibianPersonality extends Personality {
    private static final double SUPPORT_RANGE_SQR = 100.0;
    private static final double FETCH_RANGE = 10.0;
    private static final int FETCH_TIMEOUT = 180;
    private static final int FETCH_COOLDOWN = 120;
    private static final int RESCUE_TICKS = 80;
    private static final int MESSAGE_COOLDOWN = 120;

    private static class AmphibianState {
        ItemEntity fetchTarget = null;
        ItemStack carriedItem = ItemStack.EMPTY;
        int fetchTimer = 0;
        int fetchCooldown = 0;
        int rescueTicks = 0;
        int messageCooldown = 0;
    }

    private final WeakHashMap<Wolf, AmphibianState> states = new WeakHashMap<>();

    private AmphibianState getState(Wolf wolf) {
        return states.computeIfAbsent(wolf, ignored -> new AmphibianState());
    }

    @Override public String getId() { return "amphibian"; }

    @Override
    public void applyAttributes(Wolf wolf) {
        super.applyAttributes(wolf);
        setAttr(wolf, Attributes.MAX_HEALTH, 28.0);
        setAttr(wolf, Attributes.MOVEMENT_SPEED, 0.35);
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

        AmphibianState state = getState(wolf);
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        Player owner = wolf.getOwner() instanceof Player player ? player : null;
        ServerLevel serverLevel = (ServerLevel) wolf.level();

        if (state.fetchCooldown > 0) {
            state.fetchCooldown--;
        }
        if (state.fetchTimer > 0) {
            state.fetchTimer--;
        }
        if (state.rescueTicks > 0) {
            state.rescueTicks--;
        }
        if (state.messageCooldown > 0) {
            state.messageCooldown--;
        }

        boolean wolfWet = isWet(wolf);
        if (wolfWet) {
            maintainWetBonuses(wolf, serverLevel);
        } else if (wolf.level().isRainingAt(wolf.blockPosition().above())) {
            wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, false, false));
        }

        if (owner != null) {
            supportOwner(wolf, owner, data, state, serverLevel, wolfWet);
        }

        if (owner != null && shouldRescue(owner)) {
            if (state.rescueTicks <= 0 && state.messageCooldown == 0) {
                owner.displayClientMessage(net.minecraft.network.chat.Component.literal("§b[两栖猎手] " + DogDialogs.getAmphibianRescue(wolf.getRandom())), true);
                state.messageCooldown = MESSAGE_COOLDOWN;
                if (owner instanceof net.minecraft.server.level.ServerPlayer sp) {
                    DogUtils.grantAdvancement(sp, "river_rescue");
                }
            }
            state.rescueTicks = Math.max(state.rescueTicks, RESCUE_TICKS);
        }

        if (state.rescueTicks > 0 && owner != null) {
            maintainRescue(wolf, owner, data, state, serverLevel);
            return;
        }

        if (!state.carriedItem.isEmpty()) {
            returnRecoveredItem(wolf, owner, data, state, serverLevel);
            return;
        }

        if (shouldFetchItems(wolf, owner, state)) {
            if (!isValidFetchTarget(state.fetchTarget)) {
                state.fetchTarget = findNearestWaterItem(wolf, owner, data);
                if (state.fetchTarget != null) {
                    state.fetchTimer = FETCH_TIMEOUT;
                }
            }

            if (state.fetchTarget != null) {
                fetchWaterItem(wolf, state, serverLevel);
            }
        } else {
            state.fetchTarget = null;
        }
    }

    @Override
    public void onDamageDealt(LivingIncomingDamageEvent event, Wolf wolf) {
        LivingEntity target = event.getEntity();
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        boolean wetFight = isWet(wolf) || isWet(target) || target.level().isRainingAt(target.blockPosition());

        if (wetFight) {
            event.setAmount(event.getAmount() + 1.5f);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false));
            if (data.getAffinity() >= 100) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
            }

            if (wolf.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 0.6, target.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            }
        }

        if (target.getHealth() - event.getAmount() <= 0 && data.getAffinity() < 100) {
            data.increaseAffinity(1);
        }
    }

    @Override
    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
        if (event.isCanceled() || wolf.distanceToSqr(player) > 144.0) {
            return;
        }

        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        AmphibianState state = getState(wolf);
        boolean ownerWet = isWet(player);
        boolean wolfWet = isWet(wolf);

        if (!ownerWet && !wolfWet) {
            return;
        }

        float reduction = data.getAffinity() >= 100 ? 0.78f : 0.88f;
        if (wolfWet) {
            reduction -= 0.05f;
        }
        event.setAmount(event.getAmount() * Math.max(0.7f, reduction));

        state.rescueTicks = Math.max(state.rescueTicks, RESCUE_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, false, false));
        if (data.getAffinity() >= 100 && ownerWet) {
            player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 30, 0, false, false));
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
            if (!wolf.isOrderedToSit()) {
                wolf.setTarget(attacker);
                wolf.getNavigation().moveTo(attacker, wolfWet ? 1.35D : 1.2D);
            }
        }

        if (wolf.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.BUBBLE, player.getX(), player.getEyeY(), player.getZ(), 6, 0.25, 0.25, 0.25, 0.02);
        }
    }

    @Override
    public void onWolfDamaged(LivingIncomingDamageEvent event, Wolf wolf) {
        AmphibianState state = getState(wolf);
        float multiplier = 1.0f;
        if (isWet(wolf)) {
            multiplier -= 0.1f;
        }
        if (state.rescueTicks > 0) {
            multiplier -= 0.08f;
        }
        event.setAmount(event.getAmount() * Math.max(0.78f, multiplier));

        if (event.getSource().getEntity() instanceof LivingEntity attacker && !wolf.isOrderedToSit()) {
            wolf.setTarget(attacker);
        }
    }

    private void maintainWetBonuses(Wolf wolf, ServerLevel serverLevel) {
        wolf.setAirSupply(wolf.getMaxAirSupply());
        wolf.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, false, false));
        wolf.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, false, false));
        if (wolf.tickCount % 40 == 0 && wolf.getHealth() < wolf.getMaxHealth()) {
            wolf.heal(1.0f);
        }
        if (wolf.tickCount % 12 == 0) {
            serverLevel.sendParticles(ParticleTypes.BUBBLE, wolf.getX(), wolf.getY() + 0.6, wolf.getZ(), 2, 0.15, 0.15, 0.15, 0.01);
        }
    }

    private void supportOwner(Wolf wolf, Player owner, PetData data, AmphibianState state, ServerLevel serverLevel, boolean wolfWet) {
        if (wolf.distanceToSqr(owner) > SUPPORT_RANGE_SQR) {
            return;
        }

        boolean ownerWet = isWet(owner);
        boolean rainOnOwner = owner.level().isRainingAt(owner.blockPosition().above());
        if (!ownerWet && !wolfWet && !rainOnOwner) {
            return;
        }

        if (ownerWet || owner.getAirSupply() < owner.getMaxAirSupply()) {
            owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, false, false));
        }
        if (ownerWet) {
            owner.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, false, false));
        }
        if (rainOnOwner && !ownerWet) {
            owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false));
        }
        if (data.getAffinity() >= 100 && ownerWet) {
            owner.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 40, 0, false, false));
        }
        if (data.getAffinity() >= 100 && ownerWet && owner.getHealth() < owner.getMaxHealth() * 0.5f) {
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 0, false, false));
        }

        if (serverLevel.getGameTime() % 20L == 0) {
            serverLevel.sendParticles(ParticleTypes.BUBBLE, owner.getX(), owner.getY() + 0.9, owner.getZ(), 2, 0.15, 0.25, 0.15, 0.01);
        }

        if (ownerWet && !wolf.isOrderedToSit() && wolf.distanceToSqr(owner) > 9.0 && wolf.getTarget() == null && state.rescueTicks == 0) {
            wolf.getNavigation().moveTo(owner, 1.2D);
        }
    }

    private boolean shouldRescue(Player owner) {
        if (!isWet(owner)) {
            return false;
        }
        return owner.getAirSupply() < owner.getMaxAirSupply() / 2
                || owner.getDeltaMovement().y < -0.08
                || owner.getHealth() < owner.getMaxHealth() * 0.45f;
    }

    private void maintainRescue(Wolf wolf, Player owner, PetData data, AmphibianState state, ServerLevel serverLevel) {
        if (!isWet(owner) || wolf.distanceToSqr(owner) > 196.0) {
            state.rescueTicks = 0;
            return;
        }

        if (!wolf.isOrderedToSit()) {
            if (wolf.distanceToSqr(owner) > 4.0) {
                wolf.getNavigation().moveTo(owner, 1.35D);
            } else if (wolf.getTarget() == null) {
                wolf.getNavigation().stop();
            }
        }

        owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, false, false));
        owner.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, false, false));
        if (data.getAffinity() >= 100) {
            owner.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 30, 0, false, false));
            owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, false, false));
        }

        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && attacker.distanceToSqr(owner) <= 81.0) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
            if (!wolf.isOrderedToSit()) {
                wolf.setTarget(attacker);
            }
        }

        if (serverLevel.getGameTime() % 8L == 0) {
            serverLevel.sendParticles(ParticleTypes.SPLASH, owner.getX(), owner.getY() + 0.4, owner.getZ(), 4, 0.2, 0.15, 0.2, 0.03);
            serverLevel.sendParticles(ParticleTypes.BUBBLE, wolf.getX(), wolf.getY() + 0.6, wolf.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
        }
    }

    private boolean shouldFetchItems(Wolf wolf, Player owner, AmphibianState state) {
        return owner != null
                && state.fetchCooldown == 0
                && state.rescueTicks == 0
                && !wolf.isOrderedToSit()
                && wolf.getTarget() == null
                && wolf.distanceToSqr(owner) <= 196.0;
    }

    private void fetchWaterItem(Wolf wolf, AmphibianState state, ServerLevel serverLevel) {
        if (!isValidFetchTarget(state.fetchTarget) || state.fetchTimer <= 0) {
            state.fetchTarget = null;
            state.fetchCooldown = 40;
            return;
        }

        wolf.setIsInterested(true);
        wolf.getLookControl().setLookAt(state.fetchTarget, 30.0F, 30.0F);
        if (wolf.tickCount % 10 == 0) {
            wolf.getNavigation().moveTo(state.fetchTarget, 1.25D);
        }
        if (wolf.tickCount % 15 == 0) {
            serverLevel.sendParticles(ParticleTypes.BUBBLE, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        }

        if (wolf.distanceToSqr(state.fetchTarget) <= 3.0) {
            state.carriedItem = state.fetchTarget.getItem().copy();
            state.fetchTarget.discard();
            state.fetchTarget = null;
            state.fetchTimer = FETCH_TIMEOUT;
            wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5f, 1.1f);
        }
    }

    private void returnRecoveredItem(Wolf wolf, Player owner, PetData data, AmphibianState state, ServerLevel serverLevel) {
        if (state.fetchTimer <= 0) {
            state.carriedItem = ItemStack.EMPTY;
            state.fetchCooldown = FETCH_COOLDOWN;
            return;
        }

        if (owner != null) {
            wolf.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            if (wolf.distanceToSqr(owner) > 9.0 && wolf.tickCount % 10 == 0) {
                wolf.getNavigation().moveTo(owner, 1.25D);
            }
        }

        if (owner == null || wolf.distanceToSqr(owner) <= 9.0 || state.fetchTimer <= 10) {
            wolf.spawnAtLocation(state.carriedItem.copy());
            if (data.getAffinity() < 100) {
                data.increaseAffinity(1);
            }
            state.carriedItem = ItemStack.EMPTY;
            state.fetchCooldown = FETCH_COOLDOWN + wolf.getRandom().nextInt(60);
            wolf.getNavigation().stop();
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 3, 0.15, 0.15, 0.15, 0.0);
            serverLevel.sendParticles(ParticleTypes.SPLASH, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 4, 0.2, 0.15, 0.2, 0.02);
            wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.WOLF_AMBIENT, SoundSource.NEUTRAL, 0.7f, 1.25f);
        }
    }

    private ItemEntity findNearestWaterItem(Wolf wolf, Player owner, PetData data) {
        double range = data.getAffinity() >= 100 ? FETCH_RANGE + 2.0 : FETCH_RANGE;
        return wolf.level().getEntitiesOfClass(ItemEntity.class, wolf.getBoundingBox().inflate(range)).stream()
                .filter(this::isValidFetchTarget)
                .min(Comparator.comparingDouble(item -> owner != null ? owner.distanceToSqr(item) : wolf.distanceToSqr(item)))
                .orElse(null);
    }

    private boolean isValidFetchTarget(ItemEntity item) {
        return item != null
                && item.isAlive()
                && !item.hasPickUpDelay()
                && !item.getItem().isEmpty()
                && isWet(item);
    }

    private boolean isWet(net.minecraft.world.entity.Entity entity) {
        return entity.isInWaterRainOrBubble() || entity.level().isRainingAt(entity.blockPosition().above());
    }
}
