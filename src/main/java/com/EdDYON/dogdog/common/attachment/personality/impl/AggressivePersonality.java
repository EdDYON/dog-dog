package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.DogDog;
import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogBonding;
import com.EdDYON.dogdog.common.util.DogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;
import java.util.WeakHashMap;

public class AggressivePersonality extends Personality {

    public static class AggressiveMonsterTargetGoal extends NearestAttackableTargetGoal<Monster> {
        public AggressiveMonsterTargetGoal(Wolf wolf) {
            super(wolf, Monster.class, true);
        }
    }

    private static class ShuraState {
        int shuraTimer = 0;
        int lockTimer = 0;
    }

    private final WeakHashMap<Wolf, ShuraState> states = new WeakHashMap<>();

    private ShuraState getState(Wolf wolf) {
        return states.computeIfAbsent(wolf, k -> new ShuraState());
    }

    @Override
    public String getId() {
        return "aggressive";
    }

    @Override
    public boolean canAttack() {
        return true;
    }

    @Override
    public void applyAttributes(Wolf wolf) {
        super.applyAttributes(wolf);
        setAttr(wolf, Attributes.MAX_HEALTH, 25.0);
        setAttr(wolf, Attributes.ATTACK_DAMAGE, 8.0);
        setAttr(wolf, Attributes.MOVEMENT_SPEED, 0.35);
    }

    @Override
    public void applyAI(Wolf wolf) {
        addGoalIfAbsent(wolf.targetSelector, 1, new AggressiveMonsterTargetGoal(wolf));
    }

    @Override
    public void onTick(Wolf wolf) {
        if (wolf.level().isClientSide) return;
        ServerLevel sl = (ServerLevel) wolf.level();
        ShuraState state = getState(wolf);

        if (state.lockTimer > 0) {
            state.lockTimer--;
        }

        if (state.shuraTimer > 0) {
            state.shuraTimer--;
            handleShuraLogic(wolf, state, sl);
        }
    }

    private void handleShuraLogic(Wolf wolf, ShuraState state, ServerLevel sl) {
        if (wolf.tickCount % 5 == 0) {
            List<Mob> targets = sl.getEntitiesOfClass(Mob.class, wolf.getBoundingBox().inflate(12.0)).stream()
                    .filter(m -> m instanceof Enemy)
                    .toList();

            if (!targets.isEmpty()) {
                Mob target = targets.get(wolf.getRandom().nextInt(targets.size()));
                sl.sendParticles(ParticleTypes.FLASH, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 1, 0, 0, 0, 0);
                wolf.teleportTo(target.getX(), target.getY(), target.getZ());
                target.hurt(sl.damageSources().mobAttack(wolf), 12.0f);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                sl.playSound(null, wolf.blockPosition(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.0f, 1.2f);
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(), 3, 0.2, 0.2, 0.2, 0);
            }
        }

        if (state.shuraTimer == 0) {
            PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
            wolf.setHealth(1.0f);
            wolf.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 2));
            DogBonding.spendResolve(data, 35, 45);
            state.lockTimer = 7200;

            if (wolf.getOwner() instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal("§c§l[虚弱] §f杀戮结束，狗狗陷入了极度虚弱，核心机制锁定 360 秒。"));
            }
            sl.playSound(null, wolf.blockPosition(), SoundEvents.WOLF_WHINE, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    @Override
    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        ShuraState state = getState(wolf);

        if (state.shuraTimer > 0 || state.lockTimer > 0) {
            event.setCanceled(true);
            return;
        }

        float futureHealth = player.getHealth() - event.getAmount();

        if (futureHealth <= player.getMaxHealth() * 0.3f && DogBonding.canUseUltimateBondSkill(data, 100, 35, 60)) {
            List<Mob> nearbyEnemies = wolf.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8.0)).stream()
                    .filter(m -> m instanceof Enemy)
                    .toList();

            if (nearbyEnemies.size() >= 5) {
                event.setCanceled(true);
                player.setHealth(Math.max(player.getHealth(), player.getMaxHealth() * 0.3f));
                state.shuraTimer = 300;

                if (player instanceof ServerPlayer sp) {
                    ResourceLocation advId = ResourceLocation.fromNamespaceAndPath("dog_dog", "shura_mode");
                    net.minecraft.advancements.AdvancementHolder adv = sp.server.getAdvancements().get(advId);
                    if (adv != null) {
                        for (String crit : sp.getAdvancements().getOrStartProgress(adv).getRemainingCriteria()) {
                            sp.getAdvancements().award(adv, crit);
                        }
                    }

                    sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§c§l修罗：无尽杀戮")));
                    sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§f杀光它们，一个不留！")));
                    sp.level().playSound(null, wolf.blockPosition(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.5f, 0.5f);
                }
            }
        }
    }

    @Override
    public void onDamageDealt(LivingIncomingDamageEvent event, Wolf wolf) {
        LivingEntity target = event.getEntity();
        if (target.getMaxHealth() >= 30.0f) {
            float bonus = Math.min(target.getMaxHealth() * 0.05f, 10.0f);
            event.setAmount(event.getAmount() + bonus);
            wolf.level().addParticle(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 0, 0, 0);
        }

        if (target.getHealth() - event.getAmount() <= 0) {
            PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
            DogBonding.rewardSharedVictory(data, 1, 2);
            if (data.getAffinity() >= 100 && wolf.getOwner() instanceof Player owner) {
                owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
            }
        }
    }

    @Override
    public void onWolfDamaged(LivingIncomingDamageEvent event, Wolf wolf) {
        ShuraState state = getState(wolf);
        if (state.shuraTimer > 0) {
            event.setCanceled(true);
        }
    }
}
