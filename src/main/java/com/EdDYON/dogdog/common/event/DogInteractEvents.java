package com.EdDYON.dogdog.common.event;

import com.EdDYON.dogdog.DogDog;
import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = DogDog.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DogInteractEvents {

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getLevel().isClientSide) return;
        if (!(event.getTarget() instanceof Wolf wolf)) return;

        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);

        if (DogUtils.isStone(wolf)) {
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.literal("§8⚠️ 它已经彻底石化，无法被救回了..."));
            }
            event.setCanceled(true);
            return;
        }

        if (wolf.isNoAi() && !data.isCritical()) {
            event.setCanceled(true);
            return;
        }

        boolean handled = DogUtils.handleInteraction(wolf, player, stack, data);
        if (handled) {
            DogUtils.updateHealthName(wolf);
            event.setCanceled(true);
        }
    }
}