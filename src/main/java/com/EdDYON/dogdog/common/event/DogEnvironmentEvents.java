package com.EdDYON.dogdog.common.event;

import com.EdDYON.dogdog.DogDog;
import com.EdDYON.dogdog.common.ai.DiggerGoal;
import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;

@EventBusSubscriber(modid = DogDog.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DogEnvironmentEvents {

    private static boolean isProtectedBlock(BlockState state) {
        return state.is(Blocks.RED_SHULKER_BOX)
                || state.is(Blocks.FIRE_CORAL_BLOCK)
                || state.is(Blocks.RED_CONCRETE_POWDER)
                || state.is(Blocks.NETHER_WART_BLOCK)
                || state.is(Blocks.REDSTONE_WIRE);
    }

    private static boolean isNearCriticalDog(net.minecraft.world.level.Level level, BlockPos pos) {
        return level.getEntitiesOfClass(Wolf.class, new AABB(pos).inflate(6.0)).stream()
                .filter(Wolf::isTame)
                .anyMatch(wolf -> {
                    if (!wolf.hasData(ModAttachmentTypes.PET_DATA)) {
                        return false;
                    }
                    PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
                    return data.isCritical() || DogUtils.isStone(wolf);
                });
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer().level().isClientSide) {
            return;
        }

        if (DiggerGoal.isProtectedHeistPortalBlock(event.getPlayer().level(), event.getPos())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.literal("[寻宝专家] 这扇伪装门还在运作，先别碰。"), true);
            return;
        }

        if (isProtectedBlock(event.getState()) && isNearCriticalDog(event.getPlayer().level(), event.getPos())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.literal("[血迹] 这些痕迹暂时碰不得。"), true);
        }
    }

    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            return;
        }

        if (DiggerGoal.isProtectedHeistPortalBlock(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(Component.literal("[寻宝专家] 门里的空间还在翻涌，先别乱动。"), true);
            return;
        }

        if (isProtectedBlock(event.getLevel().getBlockState(event.getPos())) && isNearCriticalDog(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(Component.literal("[血迹] 这是狗狗留下的痕迹。"), true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) {
            return;
        }

        List<BlockPos> affected = event.getAffectedBlocks();
        affected.removeIf(pos ->
                DiggerGoal.isProtectedHeistPortalBlock(event.getLevel(), pos)
                        || (isProtectedBlock(event.getLevel().getBlockState(pos)) && isNearCriticalDog(event.getLevel(), pos))
        );
    }
}
