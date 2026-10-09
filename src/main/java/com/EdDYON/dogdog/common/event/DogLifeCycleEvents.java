package com.EdDYON.dogdog.common.event;

import com.EdDYON.dogdog.DogDog;
import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogBonding;
import com.EdDYON.dogdog.common.util.DogUtils;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = DogDog.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DogLifeCycleEvents {

    @SubscribeEvent
    public static void onTame(AnimalTameEvent event) {
        if (event.getAnimal() instanceof Wolf wolf) {
            DogUtils.ensurePersonalityAssigned(wolf);
            DogUtils.applyCurrentPersonality(wolf, true);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Wolf wolf && wolf.isTame()) {
            DogUtils.ensurePersonalityAssigned(wolf);
            DogUtils.applyCurrentPersonality(wolf, false);
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
                DogUtils.handleCriticalState(wolf, data, (ServerLevel) wolf.level());
            }
            return;
        }

        if (wolf.level().isClientSide) return;

        DogBonding.tickMoodAndStrain(wolf, data);

        Personality p = DogUtils.getLogic(wolf);
        if (p != null) p.onTick(wolf);
        DogUtils.updateHealthName(wolf);

        if (data.getAffinity() >= 40 && data.getMood() >= 35 && wolf.tickCount % 100 == 0) {
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

        DogUtils.handleMovementMode(wolf, data);
    }
}
