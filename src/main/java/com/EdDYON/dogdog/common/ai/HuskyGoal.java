package com.EdDYON.dogdog.common.ai;

import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.config.DogDialogs;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.util.DogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HuskyGoal extends Goal {
    private enum MischiefType {
        CAKE,
        FABRIC,
        BED,
        SNOW
    }

    private static class TargetChoice {
        final MischiefType type;
        final BlockPos pos;

        TargetChoice(MischiefType type, BlockPos pos) {
            this.type = type;
            this.pos = pos;
        }
    }

    private final Wolf wolf;
    private MischiefType targetType;
    private BlockPos targetPos;
    private Vec3 zoomPos;
    private ItemStack souvenir = ItemStack.EMPTY;
    private int state = 0;
    private int timer = 0;
    private int zoomBursts = 0;
    private long nextMischiefTick = 0L;

    public HuskyGoal(Wolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (state != 0 || wolf.level().isClientSide || wolf.isOrderedToSit() || wolf.getTarget() != null) {
            return false;
        }
        if (wolf.tickCount < nextMischiefTick || wolf.getRandom().nextInt(8) != 0) {
            return false;
        }

        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        Player owner = wolf.getOwner() instanceof Player player ? player : null;
        if (owner == null && !data.isHomeMode()) {
            return false;
        }
        if (owner != null && !data.isHomeMode() && wolf.distanceToSqr(owner) > 196.0) {
            return false;
        }

        TargetChoice choice = findTarget(data);
        if (choice == null) {
            return false;
        }

        targetType = choice.type;
        targetPos = choice.pos;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return state != 0;
    }

    @Override
    public void start() {
        state = 1;
        timer = 160;
        zoomBursts = 1 + wolf.getRandom().nextInt(2);
        zoomPos = null;
        souvenir = ItemStack.EMPTY;
        wolf.setIsInterested(true);
    }

    @Override
    public void tick() {
        if (!(wolf.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        switch (state) {
            case 1 -> moveToTarget(serverLevel);
            case 2 -> performMischief(serverLevel);
            case 3 -> runZoomies(serverLevel);
            case 4 -> returnToOwner(serverLevel);
            default -> reset(false);
        }
    }

    private void moveToTarget(ServerLevel serverLevel) {
        timer--;
        if (targetPos == null) {
            reset(true);
            return;
        }

        double speed = targetType == MischiefType.SNOW ? 1.35D : 1.22D;
        wolf.getLookControl().setLookAt(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, 30.0F, 30.0F);
        if (wolf.tickCount % 10 == 0) {
            wolf.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, speed);
        }
        if (wolf.tickCount % 20 == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, wolf.getX(), wolf.getY() + 0.4, wolf.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        }

        if (wolf.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5) <= 4.0 || timer <= 0) {
            wolf.getNavigation().stop();
            state = 2;
            timer = 30;
        }
    }

    private void performMischief(ServerLevel serverLevel) {
        timer--;
        BlockState stateAtTarget = targetPos != null ? wolf.level().getBlockState(targetPos) : Blocks.AIR.defaultBlockState();
        BlockState particleState = stateAtTarget.isAir() ? fallbackParticleState() : stateAtTarget;

        if (wolf.tickCount % 6 == 0) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, particleState),
                    wolf.getX(), wolf.getY() + 0.4, wolf.getZ(), 6, 0.25, 0.15, 0.25, 0.02);
        }

        if (timer == 15) {
            switch (targetType) {
                case CAKE -> doCakeMischief(serverLevel, stateAtTarget);
                case FABRIC, BED -> doFabricMischief(serverLevel, stateAtTarget);
                case SNOW -> doSnowMischief(serverLevel, stateAtTarget);
            }
        }

        if (timer <= 0) {
            chooseZoomPos();
            state = 3;
            timer = 70;
        }
    }

    private void runZoomies(ServerLevel serverLevel) {
        timer--;
        wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1, false, false));

        if (zoomPos == null) {
            chooseZoomPos();
        }
        if (zoomPos != null && wolf.tickCount % 5 == 0) {
            wolf.getNavigation().moveTo(zoomPos.x, zoomPos.y, zoomPos.z, 1.45D);
        }
        if (wolf.tickCount % 5 == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, wolf.getX(), wolf.getY() + 0.15, wolf.getZ(), 2, 0.18, 0.05, 0.18, 0.01);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, wolf.getX(), wolf.getY() + 0.6, wolf.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        }

        if (zoomPos == null
                || wolf.distanceToSqr(zoomPos.x, zoomPos.y, zoomPos.z) <= 3.0
                || timer <= 0) {
            zoomBursts--;
            if (zoomBursts > 0) {
                chooseZoomPos();
                timer = 55;
            } else {
                state = 4;
                timer = 180;
            }
        }
    }

    private void returnToOwner(ServerLevel serverLevel) {
        timer--;
        Player owner = wolf.getOwner() instanceof Player player ? player : null;

        if (owner != null) {
            wolf.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            if (wolf.tickCount % 10 == 0) {
                wolf.getNavigation().moveTo(owner, 1.25D);
            }
            if (wolf.distanceToSqr(owner) <= 9.0 || timer <= 0) {
                deliverSouvenir(serverLevel, owner);
                reset(true);
            }
        } else if (timer <= 0) {
            deliverSouvenir(serverLevel, null);
            reset(true);
        }
    }

    private void doCakeMischief(ServerLevel serverLevel, BlockState stateAtTarget) {
        if (targetPos != null && stateAtTarget.is(Blocks.CAKE) && stateAtTarget.hasProperty(CakeBlock.BITES)) {
            int bites = stateAtTarget.getValue(CakeBlock.BITES);
            if (bites < 6) {
                wolf.level().setBlock(targetPos, stateAtTarget.setValue(CakeBlock.BITES, bites + 1), 3);
            } else {
                wolf.level().destroyBlock(targetPos, false);
            }
        }

        wolf.heal(3.0f);
        souvenir = rollCakeSouvenir();
        serverLevel.sendParticles(ParticleTypes.HEART, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 3, 0.15, 0.1, 0.15, 0.0);
        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.9f, 1.1f);
    }

    private void doFabricMischief(ServerLevel serverLevel, BlockState stateAtTarget) {
        if (targetPos != null && (isFabricTarget(stateAtTarget) || isBedTarget(stateAtTarget))) {
            wolf.level().destroyBlock(targetPos, false);
        }

        souvenir = rollFabricSouvenir();
        serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL, wolf.getX(), wolf.getY() + 0.6, wolf.getZ(), 4, 0.15, 0.15, 0.15, 0.0);
        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 0.9f, 1.0f);
    }

    private void doSnowMischief(ServerLevel serverLevel, BlockState stateAtTarget) {
        if (targetPos != null && isSnowTarget(stateAtTarget)) {
            wolf.level().destroyBlock(targetPos, false);
        }

        souvenir = rollSnowSouvenir();
        wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1, false, false));
        serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 8, 0.25, 0.2, 0.25, 0.0);
        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 0.8f, 1.3f);
    }

    private void deliverSouvenir(ServerLevel serverLevel, Player owner) {
        ItemStack result = souvenir.isEmpty() ? new ItemStack(Items.STICK) : souvenir;
        wolf.spawnAtLocation(result);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, wolf.getX(), wolf.getY() + 0.8, wolf.getZ(), 4, 0.2, 0.2, 0.2, 0.0);
        wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.WOLF_AMBIENT, SoundSource.NEUTRAL, 0.8f, 1.25f);

        if (owner != null) {
            owner.displayClientMessage(net.minecraft.network.chat.Component.literal("§e[哈士奇] " + DogDialogs.getHuskyGift(wolf.getRandom())), true);
            if (owner instanceof ServerPlayer sp && (result.is(Items.CAKE) || result.is(Items.LEAD) || result.is(Items.POWDER_SNOW_BUCKET))) {
                DogUtils.grantAdvancement(sp, "husky_zoomies");
            }

            if (isMaxAffinity()) {
                owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0, false, false));
                if (targetType == MischiefType.SNOW) {
                    owner.addEffect(new MobEffectInstance(MobEffects.JUMP, 120, 0, false, false));
                }
            }
        }
    }

    private void chooseZoomPos() {
        Player owner = wolf.getOwner() instanceof Player player ? player : null;
        Vec3 center = owner != null ? owner.position() : wolf.position();

        for (int i = 0; i < 12; i++) {
            BlockPos pos = BlockPos.containing(center).offset(
                    wolf.getRandom().nextInt(12) - 6,
                    wolf.getRandom().nextInt(3) - 1,
                    wolf.getRandom().nextInt(12) - 6
            );
            if (wolf.level().isEmptyBlock(pos) && wolf.level().getBlockState(pos.below()).isSolidRender(wolf.level(), pos.below())) {
                zoomPos = Vec3.atCenterOf(pos);
                return;
            }
        }
        zoomPos = wolf.position();
    }

    private TargetChoice findTarget(PetData data) {
        BlockPos center = wolf.blockPosition();
        BlockPos bestCake = null;
        BlockPos bestFabric = null;
        BlockPos bestBed = null;
        BlockPos bestSnow = null;
        double cakeDist = Double.MAX_VALUE;
        double fabricDist = Double.MAX_VALUE;
        double bedDist = Double.MAX_VALUE;
        double snowDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -1, -6), center.offset(6, 2, 6))) {
            BlockState state = wolf.level().getBlockState(pos);
            double dist = wolf.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            if (state.is(Blocks.CAKE)) {
                if (dist < cakeDist) {
                    cakeDist = dist;
                    bestCake = pos.immutable();
                }
            } else if (isFabricTarget(state)) {
                if (dist < fabricDist) {
                    fabricDist = dist;
                    bestFabric = pos.immutable();
                }
            } else if (isBedTarget(state)) {
                if (dist < bedDist) {
                    bedDist = dist;
                    bestBed = pos.immutable();
                }
            } else if (isSnowTarget(state)) {
                if (dist < snowDist) {
                    snowDist = dist;
                    bestSnow = pos.immutable();
                }
            }
        }

        boolean maxAffinity = data.getAffinity() >= 100;
        if (maxAffinity) {
            if (bestSnow != null) return new TargetChoice(MischiefType.SNOW, bestSnow);
            if (bestCake != null) return new TargetChoice(MischiefType.CAKE, bestCake);
            if (bestFabric != null) return new TargetChoice(MischiefType.FABRIC, bestFabric);
            if (bestBed != null) return new TargetChoice(MischiefType.BED, bestBed);
        } else {
            if (bestCake != null) return new TargetChoice(MischiefType.CAKE, bestCake);
            if (bestFabric != null) return new TargetChoice(MischiefType.FABRIC, bestFabric);
            if (bestBed != null) return new TargetChoice(MischiefType.BED, bestBed);
            if (bestSnow != null) return new TargetChoice(MischiefType.SNOW, bestSnow);
        }

        if (wolf.level().getBiome(center).value().coldEnoughToSnow(center)) {
            return new TargetChoice(MischiefType.SNOW, center);
        }
        return null;
    }

    private boolean isMaxAffinity() {
        return wolf.getData(ModAttachmentTypes.PET_DATA).getAffinity() >= 100;
    }

    private boolean isFabricTarget(BlockState state) {
        return state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.WOOL);
    }

    private boolean isBedTarget(BlockState state) {
        return state.is(BlockTags.BEDS);
    }

    private boolean isSnowTarget(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
    }

    private BlockState fallbackParticleState() {
        return targetType == MischiefType.SNOW ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState();
    }

    private ItemStack rollCakeSouvenir() {
        int roll = wolf.getRandom().nextInt(isMaxAffinity() ? 6 : 5);
        return switch (roll) {
            case 0 -> new ItemStack(Items.COOKIE, 2 + wolf.getRandom().nextInt(2));
            case 1 -> new ItemStack(Items.SUGAR, 2 + wolf.getRandom().nextInt(3));
            case 2 -> new ItemStack(Items.APPLE);
            case 3 -> new ItemStack(Items.BONE);
            case 4 -> new ItemStack(Items.PUMPKIN_PIE);
            default -> new ItemStack(Items.CAKE);
        };
    }

    private ItemStack rollFabricSouvenir() {
        int roll = wolf.getRandom().nextInt(isMaxAffinity() ? 6 : 5);
        return switch (roll) {
            case 0 -> new ItemStack(Items.STRING, 2 + wolf.getRandom().nextInt(2));
            case 1 -> new ItemStack(Items.FEATHER, 1 + wolf.getRandom().nextInt(2));
            case 2 -> new ItemStack(Items.WHITE_WOOL);
            case 3 -> new ItemStack(Items.RABBIT_HIDE);
            case 4 -> new ItemStack(Items.LEATHER);
            default -> new ItemStack(Items.LEAD);
        };
    }

    private ItemStack rollSnowSouvenir() {
        int roll = wolf.getRandom().nextInt(isMaxAffinity() ? 6 : 5);
        return switch (roll) {
            case 0 -> new ItemStack(Items.SNOWBALL, 2 + wolf.getRandom().nextInt(3));
            case 1 -> new ItemStack(Items.PACKED_ICE);
            case 2 -> new ItemStack(Items.RABBIT_FOOT);
            case 3 -> new ItemStack(Items.COD);
            case 4 -> new ItemStack(Items.BLUE_ICE);
            default -> new ItemStack(Items.POWDER_SNOW_BUCKET);
        };
    }

    private void reset(boolean fullCooldown) {
        if (fullCooldown) {
            int baseCooldown = switch (targetType) {
                case CAKE -> 1800;
                case FABRIC, BED -> 2600;
                case SNOW -> 1200;
                case null -> 400;
            };
            nextMischiefTick = wolf.tickCount + baseCooldown + wolf.getRandom().nextInt(600);
        } else if (nextMischiefTick < wolf.tickCount + 200) {
            nextMischiefTick = wolf.tickCount + 200;
        }

        wolf.getNavigation().stop();
        wolf.setIsInterested(false);
        state = 0;
        timer = 0;
        zoomBursts = 0;
        zoomPos = null;
        targetPos = null;
        targetType = null;
        souvenir = ItemStack.EMPTY;
    }

    @Override
    public void stop() {
        reset(false);
    }
}
