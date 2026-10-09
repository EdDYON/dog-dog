package com.EdDYON.dogdog.common.event;

import com.EdDYON.dogdog.DogDog;
import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.PetPersonality;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.config.DogDialogs;
import com.EdDYON.dogdog.common.entity.ai.DogHomeGoal;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public class DogTickEvents {

    @SubscribeEvent
    public static void onTame(AnimalTameEvent event) {
        if (event.getAnimal() instanceof Wolf wolf) {
            PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
            if (data.getPersonality() == PetPersonality.NONE) {
                data.setPersonality(PetPersonality.getRandom());
                Personality logic = DogUtils.getLogic(wolf);
                if (logic != null) {
                    logic.applyAttributes(wolf);
                    logic.applyAI(wolf);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Wolf wolf && wolf.isTame()) {
            Personality logic = DogUtils.getLogic(wolf);
            if (logic != null) {
                logic.applyAttributes(wolf);
                logic.applyAI(wolf);
            }
            wolf.goalSelector.addGoal(5, new DogHomeGoal(wolf));
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Wolf wolf) || !wolf.isTame()) return;

        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);

        if (DogUtils.isStone(wolf)) {
            wolf.setNoAi(true);
            wolf.setInvulnerable(true);
            wolf.setOrderedToSit(true);
            return;
        }

        if (data.isCritical()) {
            if (!wolf.level().isClientSide) {
                ServerLevel sl = (ServerLevel) wolf.level();

                wolf.setOrderedToSit(true);
                wolf.getNavigation().stop();
                if (wolf.getOwner() != null) {
                    wolf.getLookControl().setLookAt(wolf.getOwner(), 30.0F, 30.0F);
                }


                data.tickCritical();
                int ticks = data.getCriticalTicks();

                if (ticks < 1200) {
                    int seconds = Math.max(0, (1200 - ticks) / 20);
                    String whisper = (wolf.tickCount % 60 == 0) ? " " + DogDialogs.getCriticalWhisper(wolf.getRandom()) : "";

                    wolf.setCustomName(Component.literal("§c§l救助倒计时: " + seconds + "s" + whisper));
                    wolf.setCustomNameVisible(true);

                    if (wolf.tickCount % 3 == 0) {
                        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                                wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 5, 0.2, 0.1, 0.2, 0.05);
                    }
                } else if (ticks == 1200) {
                    data.tickCritical();
                    wolf.setNoAi(true);
                    wolf.setInvulnerable(true);

                    String stoneSub = DogDialogs.getStone(wolf.getRandom());
                    wolf.setCustomName(Component.literal("§8[石化] 逝去的伙伴"));
                    wolf.setCustomNameVisible(true);

                    if (wolf.getOwner() instanceof ServerPlayer sp) {
                        sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§8§l永远的守护")));
                        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(stoneSub)));
                        sp.sendSystemMessage(Component.literal("§8§l[悲剧] §7它永远化作了一座雕像：" + stoneSub));
                    }
                    wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.5f, 0.5f);
                } else {
                    wolf.setOrderedToSit(true);
                    wolf.setNoAi(true);
                    wolf.setInvulnerable(true);
                }
            }
            return;
        }

        if (wolf.level().isClientSide) return;

        Personality p = DogUtils.getLogic(wolf);
        if (p != null) p.onTick(wolf);
        DogUtils.updateHealthName(wolf);

        if (data.getAffinity() >= 50 && wolf.tickCount % 100 == 0) {
            if (wolf.getLastHurtByMobTimestamp() + 200 < wolf.tickCount && wolf.getHealth() < wolf.getMaxHealth()) {
                wolf.heal(1.0f);
                ((ServerLevel)wolf.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, wolf.getX(), wolf.getY()+0.5, wolf.getZ(), 1, 0.2, 0.2, 0.2, 0);
            }
        }

        if (wolf.tickCount % 40 == 0) {
            boolean isCozy = wolf.level().getBlockState(wolf.blockPosition()).is(net.minecraft.tags.BlockTags.WOOL_CARPETS) ||
                    wolf.level().getBlockState(wolf.blockPosition().below()).is(net.minecraft.tags.BlockTags.WOOL);
            if (isCozy && wolf.getHealth() < wolf.getMaxHealth()) {
                wolf.heal(1.0f);
                ((ServerLevel)wolf.level()).sendParticles(ParticleTypes.NOTE, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 1, 0.2, 0.2, 0.2, 0);
            }
        }

        if (data.isHomeMode()) {
            BlockPos home = data.getHomePos();
            if (home != null) {
                wolf.restrictTo(home, 32);
                wolf.goalSelector.getAvailableGoals().stream()
                        .map(WrappedGoal::getGoal)
                        .filter(g -> g instanceof FollowOwnerGoal)
                        .findFirst()
                        .ifPresent(wolf.goalSelector::removeGoal);

                if (wolf.distanceToSqr(Vec3.atCenterOf(home)) > 1024) {
                    wolf.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
                    wolf.getNavigation().stop();
                }
            }
        } else {
            wolf.clearRestriction();
            boolean hasFollow = wolf.goalSelector.getAvailableGoals().stream()
                    .anyMatch(g -> g.getGoal() instanceof FollowOwnerGoal);
            if (!hasFollow) {
                wolf.goalSelector.addGoal(6, new FollowOwnerGoal(wolf, 1.0D, 10.0F, 2.0F));
            }

            Player owner = wolf.getOwner() instanceof Player player ? player : null;
            if (owner != null && !wolf.isOrderedToSit() && wolf.getTarget() == null) {
                double distSqr = wolf.distanceToSqr(owner);
                if (data.getPersonality() != PetPersonality.CLINGY && distSqr < 4.0) {
                    Vec3 away = wolf.position().subtract(owner.position()).normalize().scale(0.05);
                    wolf.setDeltaMovement(wolf.getDeltaMovement().add(away));
                }
            }
        }
    }
}
