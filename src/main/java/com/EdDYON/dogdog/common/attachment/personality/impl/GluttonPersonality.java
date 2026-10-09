package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.config.DogDialogs;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Comparator;
import java.util.WeakHashMap;

public class GluttonPersonality extends Personality {
    private static final int FOOD_SCAN_RANGE = 6;
    private static final int MAX_FULLNESS = 160;
    private static final int FULLNESS_DECAY_INTERVAL = 40;
    private static final int FRENZY_THRESHOLD = 80;
    private static final int SHARE_THRESHOLD = 120;
    private static final int FRENZY_TICKS = 100;
    private static final int SHARE_INTERVAL = 40;

    private static class GluttonState {
        int fullness = 0;
        int chewCooldown = 0;
        int frenzyTicks = 0;
        int shareCooldown = 0;
        int lineCooldown = 0;
    }

    private final WeakHashMap<Wolf, GluttonState> states = new WeakHashMap<>();

    private GluttonState getState(Wolf wolf) {
        return states.computeIfAbsent(wolf, ignored -> new GluttonState());
    }

    @Override public String getId() { return "glutton"; }

    @Override
    public void applyAttributes(Wolf wolf) {
        super.applyAttributes(wolf);
        setAttr(wolf, Attributes.MAX_HEALTH, 44.0);
        setAttr(wolf, Attributes.MOVEMENT_SPEED, 0.32);
        setAttr(wolf, Attributes.ATTACK_DAMAGE, 5.0);
        if (wolf.getHealth() > wolf.getMaxHealth()) {
            wolf.setHealth(wolf.getMaxHealth());
        }
    }

    @Override
    public void onTick(Wolf wolf) {
        if (wolf.level().isClientSide) {
            return;
        }

        GluttonState state = getState(wolf);
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        ServerLevel serverLevel = (ServerLevel) wolf.level();
        Player owner = wolf.getOwner() instanceof Player player ? player : null;

        if (state.chewCooldown > 0) {
            state.chewCooldown--;
        }
        if (state.shareCooldown > 0) {
            state.shareCooldown--;
        }
        if (state.lineCooldown > 0) {
            state.lineCooldown--;
        }
        if (state.frenzyTicks > 0) {
            state.frenzyTicks--;
            wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10, 1, false, false));
            wolf.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 0, false, false));
            if (wolf.tickCount % 10 == 0) {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            }
        }

        if (state.fullness > 0 && state.frenzyTicks == 0 && wolf.tickCount % FULLNESS_DECAY_INTERVAL == 0) {
            state.fullness = Math.max(0, state.fullness - 1);
        }

        ItemEntity foodTarget = findNearestFood(wolf);
        wolf.setIsInterested(foodTarget != null || isOwnerHoldingFood(owner, wolf));

        if (foodTarget != null) {
            if (!wolf.isOrderedToSit() && wolf.getTarget() == null) {
                wolf.getNavigation().moveTo(foodTarget, state.frenzyTicks > 0 ? 1.25D : 1.1D);
            }

            if (wolf.distanceToSqr(foodTarget) <= 3.24 && state.chewCooldown == 0) {
                eatFoodEntity(wolf, foodTarget, state, data, serverLevel);
            }
        }

        if (owner != null && state.fullness >= SHARE_THRESHOLD && wolf.distanceToSqr(owner) <= 16.0 && state.shareCooldown == 0) {
            owner.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1, 0, false, false));
            owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 40, 0, false, false));
            if (data.getAffinity() >= 100) {
                owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 0, false, false));
                if (owner instanceof ServerPlayer sp) {
                    DogUtils.grantAdvancement(sp, "glutton_feast");
                }
            }
            state.shareCooldown = SHARE_INTERVAL;
            serverLevel.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.0, owner.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            if (state.lineCooldown == 0) {
                owner.displayClientMessage(net.minecraft.network.chat.Component.literal("§6[贪吃鬼] " + DogDialogs.getGluttonShare(wolf.getRandom())), true);
                state.lineCooldown = 200;
            }
        }
    }

    @Override
    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
        if (event.isCanceled() || wolf.distanceToSqr(player) > 100.0) {
            return;
        }

        GluttonState state = getState(wolf);
        if (state.fullness >= SHARE_THRESHOLD) {
            event.setAmount(event.getAmount() * 0.82f);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 0, false, false));
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            if (state.fullness >= FRENZY_THRESHOLD) {
                startFrenzy(wolf, state, (ServerLevel) wolf.level());
            }
            wolf.setTarget(attacker);
            wolf.getNavigation().moveTo(attacker, state.frenzyTicks > 0 ? 1.35D : 1.2D);
        }
    }

    @Override
    public void onWolfDamaged(LivingIncomingDamageEvent event, Wolf wolf) {
        GluttonState state = getState(wolf);
        if (state.fullness >= SHARE_THRESHOLD) {
            event.setAmount(event.getAmount() * 0.78f);
        } else if (state.fullness >= FRENZY_THRESHOLD) {
            event.setAmount(event.getAmount() * 0.9f);
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker && state.fullness >= FRENZY_THRESHOLD) {
            startFrenzy(wolf, state, (ServerLevel) wolf.level());
            wolf.setTarget(attacker);
        }
    }

    @Override
    public void onDamageDealt(LivingIncomingDamageEvent event, Wolf wolf) {
        GluttonState state = getState(wolf);
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);

        if (state.fullness >= FRENZY_THRESHOLD) {
            float bonus = state.frenzyTicks > 0 ? 3.0f : 1.5f;
            event.setAmount(event.getAmount() + bonus);
            wolf.heal(Math.min(4.0f, event.getAmount() * (state.frenzyTicks > 0 ? 0.3f : 0.15f)));
            state.fullness = Math.max(0, state.fullness - (state.frenzyTicks > 0 ? 10 : 4));
        }

        if (event.getEntity().getHealth() - event.getAmount() <= 0 && data.getAffinity() < 100) {
            data.increaseAffinity(1);
        }
    }

    private void eatFoodEntity(Wolf wolf, ItemEntity itemEntity, GluttonState state, PetData data, ServerLevel serverLevel) {
        ItemStack stack = itemEntity.getItem();
        FoodProperties food = getFood(stack, wolf);
        if (food == null) {
            return;
        }

        wolf.heal(food.nutrition() * 1.8f);
        state.fullness = Math.min(MAX_FULLNESS, state.fullness + food.nutrition() * 5);
        state.chewCooldown = 20;
        stack.shrink(1);
        if (stack.isEmpty()) {
            itemEntity.discard();
        }

        if (data.getAffinity() < 100 && state.fullness >= SHARE_THRESHOLD) {
            data.increaseAffinity(1);
        }

        if (state.fullness >= FRENZY_THRESHOLD) {
            wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false));
        }

        serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL, wolf.getX(), wolf.getY() + 0.6, wolf.getZ(), 4, 0.15, 0.1, 0.15, 0.0);
        serverLevel.sendParticles(ParticleTypes.HEART, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 2, 0.15, 0.1, 0.15, 0.0);
        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0F, 0.9F);
    }

    private void startFrenzy(Wolf wolf, GluttonState state, ServerLevel serverLevel) {
        if (state.frenzyTicks > 0) {
            return;
        }

        state.frenzyTicks = FRENZY_TICKS;
        wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, FRENZY_TICKS, 1, false, false));
        wolf.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, FRENZY_TICKS, 0, false, false));
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 6, 0.25, 0.25, 0.25, 0.0);
        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.NEUTRAL, 0.9F, 0.85F);
    }

    private ItemEntity findNearestFood(Wolf wolf) {
        return wolf.level().getEntitiesOfClass(ItemEntity.class, wolf.getBoundingBox().inflate(FOOD_SCAN_RANGE)).stream()
                .filter(ItemEntity::isAlive)
                .filter(item -> !item.hasPickUpDelay())
                .filter(item -> getFood(item.getItem(), wolf) != null)
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
    }

    private boolean isOwnerHoldingFood(Player owner, Wolf wolf) {
        return owner != null && (getFood(owner.getMainHandItem(), wolf) != null || getFood(owner.getOffhandItem(), wolf) != null);
    }

    private FoodProperties getFood(ItemStack stack, Wolf wolf) {
        FoodProperties food = stack.getFoodProperties(wolf);
        if (food == null || stack.is(Items.ROTTEN_FLESH)) {
            return null;
        }
        return food;
    }
}
