package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.DogDog;
import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.config.DogDialogs;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogBonding;
import com.EdDYON.dogdog.common.util.DogUtils;

import net.minecraft.advancements.AdvancementHolder;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class CowardPersonality extends Personality {

    public static class CowardAvoidMonsterGoal extends AvoidEntityGoal<Monster> {
        private final Wolf wolf;

        public CowardAvoidMonsterGoal(Wolf wolf) {
            super(wolf, Monster.class, 16.0F, 1.2D, 1.5D);
            this.wolf = wolf;
        }

        @Override
        public boolean canUse() {
            PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
            if (data != null && (data.isCritical() || DogUtils.isStone(wolf))) {
                return false;
            }
            return super.canUse();
        }
    }

    public static class CowardPanicGoal extends PanicGoal {
        private final Wolf wolf;

        public CowardPanicGoal(Wolf wolf) {
            super(wolf, 1.5D);
            this.wolf = wolf;
        }

        @Override
        public boolean canUse() {
            PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
            if (data != null && (data.isCritical() || DogUtils.isStone(wolf))) {
                return false;
            }
            return super.canUse();
        }
    }

    @Override
    public String getId() {
        return "coward";
    }

    @Override
    public boolean canAttack() {
        return false;
    }

    @Override
    public void applyAttributes(Wolf wolf) {
        if (wolf.getAttribute(Attributes.MAX_HEALTH) != null) {
            wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50.0D);
        }
        if (wolf.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            wolf.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0D);
        }
        if (wolf.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            wolf.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.4D);
        }
        wolf.setHealth(wolf.getMaxHealth());
    }

    @Override
    public void applyAI(Wolf wolf) {
        addGoalIfAbsent(wolf.goalSelector, 1, new CowardAvoidMonsterGoal(wolf));
        addGoalIfAbsent(wolf.goalSelector, 2, new CowardPanicGoal(wolf));
    }

    @Override
    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);

        if (player.getHealth() - event.getAmount() <= 0.0F
                && DogBonding.canUseUltimateBondSkill(data, 100, 45, 50)
                && !event.isCanceled()) {

            long currentTime = System.currentTimeMillis();
            long cooldownEnd = wolf.getPersistentData().getLong("BraveHeartCD");

            if (currentTime < cooldownEnd) {
                long lastMsg = wolf.getPersistentData().getLong("LastCDMsg");
                if (currentTime - lastMsg > 3000) {
                    if (player instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(Component.literal("§c[系统警告] 狗狗的勇气尚未恢复 (冷却中)，无法为你挡下这致命一击！"));
                    }
                    wolf.getPersistentData().putLong("LastCDMsg", currentTime);
                }
                return;
            }

            event.setCanceled(true);
            wolf.getPersistentData().putLong("BraveHeartCD", currentTime + 300000L);

            player.setHealth(4.0f);
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));

            wolf.teleportTo(player.getX(), player.getY(), player.getZ());
            wolf.setHealth(0.5f);
            wolf.setInvulnerable(true);
            wolf.setOrderedToSit(true);

            data.setCritical(true);
            DogBonding.heavyOverdraft(data, 20, 60);

            DogUtils.spawnBloodEffect(wolf);

            if (!player.level().isClientSide) {
                ServerLevel sl = (ServerLevel) player.level();
                sl.sendParticles(ParticleTypes.FLASH, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 150, 0.5, 1.0, 0.5, 0.2);
                sl.playSound(null, wolf.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
            }

            if (player instanceof ServerPlayer sp) {
                ResourceLocation advId = ResourceLocation.fromNamespaceAndPath(DogDog.MODID, "brave_heart");
                AdvancementHolder adv = sp.server.getAdvancements().get(advId);
                if (adv != null) {
                    for (String crit : sp.getAdvancements().getOrStartProgress(adv).getRemainingCriteria()) {
                        sp.getAdvancements().award(adv, crit);
                    }
                }

                String saveSub = DogDialogs.getCowardSave(wolf.getRandom());

                sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§4§l勇敢的心")));
                sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(saveSub)));
                sp.sendSystemMessage(Component.literal("§4§l[警告] §f胆小鬼替你挡下了致命伤！剩余 60 秒！"));
            }
        }
    }
}
