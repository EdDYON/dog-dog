package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.entity.ai.AngelPurifyGoal;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogBonding;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class HealerPersonality extends Personality {

    private static class HealerState {
        int staticTimer = 0;
        float recordedDamage = 0;
        float healthBeforeStatic = 0;
        int overdraftTimer = 0;
        UUID ownerId = null;
        final Set<UUID> frozenMonsters = new HashSet<>();
    }

    private final WeakHashMap<Wolf, HealerState> states = new WeakHashMap<>();

    private HealerState getState(Wolf wolf) {
        return states.computeIfAbsent(wolf, k -> new HealerState());
    }

    @Override
    public String getId() {
        return "healer";
    }

    @Override
    public boolean canAttack() {
        return false;
    }

    @Override
    public void applyAttributes(Wolf wolf) {
        super.applyAttributes(wolf);
        setAttr(wolf, Attributes.MAX_HEALTH, 40.0);
        if (wolf.getHealth() > 40.0f) {
            wolf.setHealth(40.0f);
        }
    }

    @Override
    public void applyAI(Wolf wolf) {
        wolf.targetSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(g -> g instanceof NearestAttackableTargetGoal || g instanceof OwnerHurtByTargetGoal || g instanceof OwnerHurtTargetGoal)
                .toList().forEach(wolf.targetSelector::removeGoal);

        wolf.goalSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(g -> g instanceof MeleeAttackGoal)
                .toList().forEach(wolf.goalSelector::removeGoal);

        boolean hasGoal = wolf.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.getGoal() instanceof AngelPurifyGoal);
        if (!hasGoal) {
            wolf.goalSelector.addGoal(2, new AngelPurifyGoal(wolf));
        }
    }

    @Override
    public void onTick(Wolf wolf) {
        if (wolf.level().isClientSide) return;
        ServerLevel sl = (ServerLevel) wolf.level();
        HealerState state = getState(wolf);
        Player owner = resolveStaticOwner(wolf, sl, state);
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);

        if (state.overdraftTimer > 0) {
            state.overdraftTimer--;
            wolf.setOrderedToSit(true);
            if (wolf.tickCount % 40 == 0) {
                wolf.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 2, false, false));
            }
            if (state.overdraftTimer == 0) {
                wolf.setOrderedToSit(false);
                if (owner instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.literal("§b✨ 天使狗狗从神圣透支中恢复了力气。"));
                }
            }
        }

        if (owner != null && !data.isCritical() && data.getMood() >= 20 && state.overdraftTimer == 0) {
            if (wolf.distanceToSqr(owner) < 64.0 && wolf.tickCount % 40 == 0) {
                owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false));
            }
        }

        if (state.staticTimer > 0) {
            state.staticTimer--;
            handleStaticTime(wolf, owner, sl, data, state);
        }
    }

    private void handleStaticTime(Wolf wolf, Player owner, ServerLevel sl, PetData data, HealerState state) {
        if (owner != null) {
            List<Monster> victims = sl.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(15.0));
            for (Monster monster : victims) {
                state.frozenMonsters.add(monster.getUUID());
                applyStaticFreeze(monster);
            }
        }

        for (UUID id : state.frozenMonsters) {
            Entity entity = sl.getEntity(id);
            if (entity instanceof Monster monster && monster.isAlive()) {
                applyStaticFreeze(monster);
            }
        }

        if (owner != null && wolf.tickCount % 5 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, owner.getX(), owner.getY() + 1.0, owner.getZ(), 10, 1.5, 1.5, 1.5, 0.02);
            sl.sendParticles(ParticleTypes.ENCHANT, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 5, 0.5, 0.5, 0.5, 0.1);
        }

        if (state.staticTimer == 0) {
            finishStaticTime(wolf, owner, sl, data, state);
        }
    }

    private Player resolveStaticOwner(Wolf wolf, ServerLevel serverLevel, HealerState state) {
        if (wolf.getOwner() instanceof Player player) {
            return player;
        }
        if (state.ownerId != null) {
            return serverLevel.getServer().getPlayerList().getPlayer(state.ownerId);
        }
        return null;
    }

    private void applyStaticFreeze(Monster monster) {
        monster.setDeltaMovement(Vec3.ZERO);
        monster.getNavigation().stop();
        monster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 6, false, false));
        monster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 10, 4, false, false));
        monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10, 0, false, false));
    }

    private void finishStaticTime(Wolf wolf, Player owner, ServerLevel sl, PetData data, HealerState state) {
        float finalDamage = Math.min(state.recordedDamage, 100.0f);
        for (UUID id : state.frozenMonsters) {
            Entity entity = sl.getEntity(id);
            if (entity instanceof Monster monster && monster.isAlive()) {
                monster.hurt(sl.damageSources().magic(), finalDamage);
                sl.sendParticles(ParticleTypes.SONIC_BOOM, monster.getX(), monster.getY() + 1.0, monster.getZ(), 1, 0, 0, 0, 0);
            }
        }
        state.frozenMonsters.clear();

        if (owner != null) {
            owner.setHealth(Math.max(state.healthBeforeStatic, owner.getMaxHealth() * 0.3f));
            sl.playSound(null, owner.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.5f);
            sl.playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 0.1f);

            if (owner instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal("§c§l[反噬] §7因干涉时间，天使狗狗陷入了神圣透支状态"));
            }
        }

        DogBonding.heavyOverdraft(data, 15, 70);
        state.overdraftTimer = 1200;
        state.recordedDamage = 0;
        state.ownerId = null;
        wolf.setOrderedToSit(true);
    }

    @Override
    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        HealerState state = getState(wolf);

        if (state.staticTimer > 0) {
            event.setCanceled(true);
            state.recordedDamage += event.getAmount();
            return;
        }

        if (state.overdraftTimer > 0) {
            return;
        }

        if (player.getHealth() - event.getAmount() <= 1.0F && DogBonding.canUseUltimateBondSkill(data, 100, 50, 40)) {
            event.setCanceled(true);
            state.staticTimer = 140;
            state.recordedDamage = event.getAmount();
            state.healthBeforeStatic = player.getHealth();
            state.ownerId = player.getUUID();
            state.frozenMonsters.clear();
            player.setHealth(1.0F);

            if (player instanceof ServerPlayer sp) {
                // 🔥 [新增] 触发成就逻辑
                net.minecraft.resources.ResourceLocation advId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("dog_dog", "time_stop");
                net.minecraft.advancements.AdvancementHolder adv = sp.server.getAdvancements().get(advId);
                if (adv != null) {
                    for (String crit : sp.getAdvancements().getOrStartProgress(adv).getRemainingCriteria()) {
                        sp.getAdvancements().award(adv, crit);
                    }
                }

                // 维持原有的视觉效果和台词
                sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6§l因果律：绝对静止")));
                sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§e天使锁定了这一秒的结局...")));
                ServerLevel sl = sp.serverLevel();
                sl.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0f, 2.0f);
                sl.playSound(null, player.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0f, 0.5f);
            }
        }
    }
}
