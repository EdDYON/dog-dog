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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Comparator;
import java.util.WeakHashMap;

public class VampirePersonality extends Personality {
    private static final int MAX_BLOOD = 120;
    private static final int FEED_RANGE = 6;
    private static final int AURA_INTERVAL = 40;

    private static class VampireState {
        int bloodReserve = 20;
        int feedCooldown = 0;
        int auraCooldown = 0;
        int lineCooldown = 0;
    }

    private final WeakHashMap<Wolf, VampireState> states = new WeakHashMap<>();

    private VampireState getState(Wolf wolf) {
        return states.computeIfAbsent(wolf, ignored -> new VampireState());
    }

    @Override public String getId() { return "vampire"; }
    @Override public boolean canAttack() { return true; }

    @Override
    public void applyAttributes(Wolf wolf) {
        super.applyAttributes(wolf);
        setAttr(wolf, Attributes.MAX_HEALTH, 24.0);
        setAttr(wolf, Attributes.MOVEMENT_SPEED, 0.34);
        setAttr(wolf, Attributes.ATTACK_DAMAGE, 5.5);
        if (wolf.getHealth() > wolf.getMaxHealth()) {
            wolf.setHealth(wolf.getMaxHealth());
        }
    }

    @Override
    public void onTick(Wolf wolf) {
        if (wolf.level().isClientSide) {
            return;
        }

        VampireState state = getState(wolf);
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        ServerLevel serverLevel = (ServerLevel) wolf.level();
        Player owner = wolf.getOwner() instanceof Player player ? player : null;
        boolean night = wolf.level().isNight();

        if (state.feedCooldown > 0) {
            state.feedCooldown--;
        }
        if (state.auraCooldown > 0) {
            state.auraCooldown--;
        }
        if (state.lineCooldown > 0) {
            state.lineCooldown--;
        }

        if (wolf.tickCount % 40 == 0) {
            if (night) {
                state.bloodReserve = Math.min(MAX_BLOOD, state.bloodReserve + 1);
            } else {
                state.bloodReserve = Math.max(0, state.bloodReserve - 1);
            }
        }

        if (night) {
            wolf.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
            if (state.bloodReserve >= 70) {
                wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false));
            }
        }

        ItemEntity meatTarget = findNearestMeat(wolf);
        wolf.setIsInterested(meatTarget != null || isOwnerHoldingMeat(owner));
        if (meatTarget != null && !wolf.isOrderedToSit() && wolf.getTarget() == null) {
            wolf.getNavigation().moveTo(meatTarget, 1.15D);
            if (wolf.distanceToSqr(meatTarget) <= 3.24 && state.feedCooldown == 0) {
                feedOnMeat(wolf, meatTarget, state, serverLevel);
            }
        }

        if (owner != null && wolf.distanceToSqr(owner) <= 16.0 && state.bloodReserve >= 40 && state.auraCooldown == 0) {
            shareNightAura(wolf, owner, data, state, serverLevel, night);
        }
    }

    @Override
    public void onDamageDealt(LivingIncomingDamageEvent event, Wolf wolf) {
        VampireState state = getState(wolf);
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        Player owner = wolf.getOwner() instanceof Player player ? player : null;
        boolean night = wolf.level().isNight();

        float dealt = event.getAmount();
        wolf.heal(Math.min(4.0f, dealt * (night ? 0.4f : 0.28f)));
        state.bloodReserve = Math.min(MAX_BLOOD, state.bloodReserve + (night ? 10 : 6));

        if (night || state.bloodReserve >= 80) {
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false));
        }

        if (owner != null && owner.distanceToSqr(wolf) <= 16.0 && owner.getHealth() < owner.getMaxHealth() && state.bloodReserve >= 70) {
            float shared = Math.min(3.0f, dealt * (night ? 0.35f : 0.2f));
            owner.heal(shared);
            state.bloodReserve = Math.max(0, state.bloodReserve - 10);
            if (wolf.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getEyeY(), owner.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            }
            if (night && owner instanceof ServerPlayer sp) {
                DogUtils.grantAdvancement(sp, "midnight_feast");
                if (state.lineCooldown == 0) {
                    owner.displayClientMessage(net.minecraft.network.chat.Component.literal("§4[吸血伯爵] " + DogDialogs.getVampireNight(wolf.getRandom())), true);
                    state.lineCooldown = 220;
                }
            }
        }

        if (event.getEntity().getHealth() - event.getAmount() <= 0 && data.getAffinity() < 100) {
            data.increaseAffinity(1);
        }
    }

    @Override
    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
        if (event.isCanceled() || wolf.distanceToSqr(player) > 100.0) {
            return;
        }

        VampireState state = getState(wolf);
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        boolean night = wolf.level().isNight();
        int reserveCost = night ? 14 : 22;

        if (state.bloodReserve >= reserveCost) {
            float reduction = night ? 0.8f : 0.9f;
            if (data.getAffinity() >= 100) {
                reduction -= 0.05f;
            }
            event.setAmount(event.getAmount() * Math.max(0.72f, reduction));
            state.bloodReserve = Math.max(0, state.bloodReserve - reserveCost);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false));
            if (night) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 0, false, false));
            }
            if (data.getAffinity() >= 100 && player.getHealth() < player.getMaxHealth() * 0.5f) {
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 0, false, false));
            }
            if (player instanceof ServerPlayer sp && night) {
                DogUtils.grantAdvancement(sp, "midnight_feast");
            }
            if (state.lineCooldown == 0) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§4[吸血伯爵] " + DogDialogs.getVampireNight(wolf.getRandom())), true);
                state.lineCooldown = 220;
            }
            if (wolf.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getEyeY(), player.getZ(), 6, 0.25, 0.25, 0.25, 0.01);
            }
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
            if (!wolf.isOrderedToSit()) {
                wolf.setTarget(attacker);
                wolf.getNavigation().moveTo(attacker, night ? 1.3D : 1.15D);
            }
        }
    }

    @Override
    public void onWolfDamaged(LivingIncomingDamageEvent event, Wolf wolf) {
        VampireState state = getState(wolf);
        boolean night = wolf.level().isNight();

        if (state.bloodReserve >= 60) {
            event.setAmount(event.getAmount() * (night ? 0.78f : 0.88f));
        } else if (night) {
            event.setAmount(event.getAmount() * 0.92f);
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker && !wolf.isOrderedToSit()) {
            wolf.setTarget(attacker);
        }
    }

    private void shareNightAura(Wolf wolf, Player owner, PetData data, VampireState state, ServerLevel serverLevel, boolean night) {
        owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
        if (night && owner.getHealth() < owner.getMaxHealth()) {
            owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false));
        }
        if (data.getAffinity() >= 100 && state.bloodReserve >= 80 && owner.getHealth() < owner.getMaxHealth() * 0.5f) {
            owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 0, false, false));
        }

        state.bloodReserve = Math.max(0, state.bloodReserve - (night ? 4 : 6));
        state.auraCooldown = AURA_INTERVAL;
        serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
        if (night) {
            serverLevel.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getEyeY(), owner.getZ(), 1, 0.15, 0.15, 0.15, 0.0);
        }
    }

    private void feedOnMeat(Wolf wolf, ItemEntity item, VampireState state, ServerLevel serverLevel) {
        ItemStack stack = item.getItem();
        int bloodValue = getBloodValue(stack);
        if (bloodValue <= 0) {
            return;
        }

        state.bloodReserve = Math.min(MAX_BLOOD, state.bloodReserve + bloodValue);
        state.feedCooldown = 20;
        wolf.heal(Math.min(3.0f, bloodValue * 0.3f));
        stack.shrink(1);
        if (stack.isEmpty()) {
            item.discard();
        }

        serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE, wolf.getX(), wolf.getY() + 0.6, wolf.getZ(), 6, 0.15, 0.12, 0.15, 0.01);
        serverLevel.sendParticles(ParticleTypes.HEART, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 2, 0.15, 0.12, 0.15, 0.0);
        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8f, 0.85f);
    }

    private ItemEntity findNearestMeat(Wolf wolf) {
        return wolf.level().getEntitiesOfClass(ItemEntity.class, wolf.getBoundingBox().inflate(FEED_RANGE)).stream()
                .filter(ItemEntity::isAlive)
                .filter(item -> !item.hasPickUpDelay())
                .filter(item -> getBloodValue(item.getItem()) > 0)
                .min(Comparator.comparingDouble(wolf::distanceToSqr))
                .orElse(null);
    }

    private boolean isOwnerHoldingMeat(Player owner) {
        return owner != null && (getBloodValue(owner.getMainHandItem()) > 0 || getBloodValue(owner.getOffhandItem()) > 0);
    }

    private int getBloodValue(ItemStack stack) {
        if (stack.is(Items.ROTTEN_FLESH)) return 18;
        if (stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON)) return 16;
        if (stack.is(Items.CHICKEN) || stack.is(Items.RABBIT) || stack.is(Items.COD) || stack.is(Items.SALMON)) return 12;
        if (stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP) || stack.is(Items.COOKED_MUTTON)) return 14;
        if (stack.is(Items.COOKED_CHICKEN) || stack.is(Items.COOKED_RABBIT) || stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON)) return 10;
        return 0;
    }
}
