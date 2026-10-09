package com.EdDYON.dogdog.common.event;

import com.EdDYON.dogdog.DogDog;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogUtils;

import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.List;

@EventBusSubscriber(modid = DogDog.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DogCombatEvents {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof Wolf wolf && wolf.isTame()) {
            if (DogUtils.isStone(wolf)) {
                event.setCanceled(true);
                return;
            }

            if (wolf.hasData(ModAttachmentTypes.PET_DATA) && wolf.getData(ModAttachmentTypes.PET_DATA).isCritical()) {
                event.setCanceled(true);
                return;
            }

            Personality logic = DogUtils.getLogic(wolf);
            if (logic != null) {
                logic.onWolfDeath(event, wolf);
            }

            if (!event.isCanceled() && DogUtils.enterCriticalRescueState(wolf)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof Wolf wolf && wolf.isTame()) {
            if (DogUtils.isStone(wolf) || (wolf.hasData(ModAttachmentTypes.PET_DATA) && wolf.getData(ModAttachmentTypes.PET_DATA).isCritical())) {
                event.setCanceled(true);
                return;
            }

            Personality logic = DogUtils.getLogic(wolf);
            if (logic != null) {
                logic.onWolfDamaged(event, wolf);
            }
        }

        if (event.getSource().getEntity() instanceof Wolf wolf && wolf.isTame()) {
            Personality logic = DogUtils.getLogic(wolf);
            if (logic != null) {
                logic.onDamageDealt(event, wolf);
            }
        }

        if (event.getEntity() instanceof Player player) {
            List<Wolf> nearbyWolves = player.level().getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(32.0)).stream()
                    .filter(w -> w.isTame() && w.isOwnedBy(player) && !DogUtils.isStone(w) && !w.getData(ModAttachmentTypes.PET_DATA).isCritical())
                    .toList();

            for (Wolf w : nearbyWolves) {
                Personality logic = DogUtils.getLogic(w);
                if (logic != null) {
                    logic.onOwnerDamaged(event, w, player);
                    if (event.isCanceled()) break;
                }
            }
        }
    }
}
