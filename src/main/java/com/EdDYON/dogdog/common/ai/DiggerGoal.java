package com.EdDYON.dogdog.common.ai;

import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.registry.ModItems;
import com.EdDYON.dogdog.common.util.DogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DiggerGoal extends Goal {
    private static final double SCAN_RANGE = 10.0D;
    private static final double REACH_DISTANCE_SQR = 4.0D;
    private static final int CHASE_TIMEOUT = 180;
    private static final int DIG_SITE_TIMEOUT = 100;
    private static final int DIGGING_TICKS = 60;
    private static final int RETURN_TIMEOUT = 200;
    private static final int HEIST_PREPARE_TICKS = 90;
    private static final int HEIST_MIN_AWAY_TICKS = 140;
    private static final int HEIST_AWAY_RANDOM_TICKS = 80;
    private static final int HEIST_RETURN_REVEAL_TICKS = 24;
    private static final int PANIC_TICKS = 180;
    private static final int DIG_RADIUS = 6;
    private static final int REPATH_INTERVAL = 8;
    private static final int STUCK_LIMIT = 45;
    private static final int OWNER_HINT_COOLDOWN = 100;
    private static final int SURFACE_DIG_MIN_STOPS = 2;
    private static final int SURFACE_DIG_MAX_STOPS = 4;
    private static final int SURFACE_DIG_MEMORY = 6;
    private static final double SURFACE_DIG_MIN_DISTANCE_SQR = 9.0D;
    private static final double HEIST_PORTAL_MIN_DISTANCE_SQR = 6.25D;
    private static final Set<String> PROTECTED_HEIST_PORTAL_BLOCKS = ConcurrentHashMap.newKeySet();

    private enum TriggerType { NONE, STICK, GOLDEN_BONE }
    private enum Phase { IDLE, CHASE_TRIGGER, MOVE_TO_DIG_SITE, DIGGING, RETURNING, HEIST_PREPARE, HEIST_AWAY, HEIST_RETURN_REVEAL, PANIC }

    private final Wolf wolf;
    private TriggerType triggerType = TriggerType.NONE;
    private Phase phase = Phase.IDLE;
    private ItemEntity targetItem;
    private BlockPos digSite;
    private ItemStack carriedLoot = ItemStack.EMPTY;
    private final List<UUID> guardIds = new ArrayList<>();
    private final List<BlockPos> recentDigSites = new ArrayList<>();
    private int phaseTicks;
    private int noProgressTicks;
    private int heistType;
    private int plannedSurfaceDigs;
    private int completedSurfaceDigs;
    private double lastDistanceSqr = Double.MAX_VALUE;
    private boolean heistSuccess;
    private long lastOwnerHintTime = -OWNER_HINT_COOLDOWN;
    private final List<BlockPos> heistPortalBlocks = new ArrayList<>();
    private BlockPos heistPortalStand;
    private Direction heistPortalFacing = Direction.NORTH;
    private Phase pendingHeistPhase = Phase.IDLE;
    private int pendingHeistDuration;

    public DiggerGoal(Wolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static boolean isProtectedHeistPortalBlock(Level level, BlockPos pos) {
        return PROTECTED_HEIST_PORTAL_BLOCKS.contains(getPortalProtectionKey(level, pos));
    }

    private static String getPortalProtectionKey(Level level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.asLong();
    }

    @Override
    public boolean canUse() {
        if (this.wolf.level().isClientSide || !this.wolf.isAlive() || this.wolf.isOrderedToSit() || this.wolf.isNoAi() || this.phase != Phase.IDLE || this.wolf.getTarget() != null) {
            return false;
        }

        PetData data = this.wolf.getData(ModAttachmentTypes.PET_DATA);
        long now = this.wolf.level().getGameTime();
        ItemEntity nearestStick = null;
        ItemEntity nearestGoldenBone = null;
        double nearestStickDistance = Double.MAX_VALUE;
        double nearestGoldenBoneDistance = Double.MAX_VALUE;
        boolean sawBlockedGoldenBone = false;

        for (ItemEntity item : this.wolf.level().getEntitiesOfClass(ItemEntity.class, this.wolf.getBoundingBox().inflate(SCAN_RANGE))) {
            if (!isTriggerItem(item)) continue;
            double distanceSqr = this.wolf.distanceToSqr(item);
            if (item.getItem().is(ModItems.GOLDEN_BONE.get())) {
                if (!canStartHeist(data, now)) {
                    sawBlockedGoldenBone = true;
                    continue;
                }
                if (distanceSqr < nearestGoldenBoneDistance) {
                    nearestGoldenBoneDistance = distanceSqr;
                    nearestGoldenBone = item;
                }
            } else if (distanceSqr < nearestStickDistance) {
                nearestStickDistance = distanceSqr;
                nearestStick = item;
            }
        }

        if (nearestGoldenBone != null) {
            this.triggerType = TriggerType.GOLDEN_BONE;
            this.targetItem = nearestGoldenBone;
            return true;
        }
        if (nearestStick != null) {
            this.triggerType = TriggerType.STICK;
            this.targetItem = nearestStick;
            return true;
        }
        if (sawBlockedGoldenBone) maybeSendBlockedHeistHint(data, now);
        return false;
    }

    @Override public boolean canContinueToUse() { return this.phase != Phase.IDLE && this.wolf.isAlive() && !this.wolf.isRemoved(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public boolean isInterruptable() { return false; }

    @Override
    public void start() {
        this.digSite = null;
        this.carriedLoot = ItemStack.EMPTY;
        this.guardIds.clear();
        this.recentDigSites.clear();
        this.heistType = 0;
        this.plannedSurfaceDigs = 0;
        this.completedSurfaceDigs = 0;
        this.heistSuccess = false;
        beginPhase(Phase.CHASE_TRIGGER, CHASE_TIMEOUT);
        this.wolf.setIsInterested(true);
    }

    @Override public void stop() { resetState(); }

    @Override
    public void tick() {
        if (this.phase == Phase.IDLE || this.wolf.level().isClientSide) return;
        PetData data = this.wolf.getData(ModAttachmentTypes.PET_DATA);
        if (this.wolf.isOrderedToSit() || this.wolf.isNoAi() || data.isCritical()) {
            resetState();
            return;
        }

        switch (this.phase) {
            case CHASE_TRIGGER -> tickTriggerChase();
            case MOVE_TO_DIG_SITE -> tickMoveToDigSite();
            case DIGGING -> tickDigging();
            case RETURNING -> tickReturning();
            case HEIST_PREPARE -> tickHeistPrepare();
            case HEIST_AWAY -> tickHeistAway();
            case HEIST_RETURN_REVEAL -> tickHeistReturnReveal();
            case PANIC -> tickPanic();
            default -> { }
        }
    }

    private void tickTriggerChase() {
        if (!isExpectedTargetAlive()) {
            resetState();
            return;
        }

        this.phaseTicks--;
        this.wolf.getLookControl().setLookAt(this.targetItem, 30.0F, 30.0F);
        navigateTo(this.targetItem.position(), this.triggerType == TriggerType.GOLDEN_BONE ? 1.28D : 1.18D);
        trackProgress(this.wolf.distanceToSqr(this.targetItem));

        if (this.triggerType == TriggerType.STICK && this.wolf.level() instanceof ServerLevel serverLevel && this.wolf.tickCount % 10 == 0) {
            spawnSearchTrailParticles(serverLevel, this.wolf.position());
        }

        if (this.wolf.distanceToSqr(this.targetItem) <= REACH_DISTANCE_SQR) {
            consumeTriggerAndAdvance();
            return;
        }

        if (this.phaseTicks <= 0 || this.noProgressTicks >= STUCK_LIMIT) resetState();
    }

    private void tickMoveToDigSite() {
        if (this.digSite == null || !isValidDigSite(this.digSite)) {
            this.digSite = pickNextDigSite();
            if (this.digSite == null) this.digSite = this.wolf.blockPosition();
        }

        this.phaseTicks--;
        Vec3 digCenter = Vec3.atBottomCenterOf(this.digSite);
        this.wolf.getLookControl().setLookAt(digCenter.x, digCenter.y, digCenter.z, 20.0F, 20.0F);
        navigateTo(digCenter, 1.08D);
        trackProgress(this.wolf.distanceToSqr(digCenter.x, digCenter.y, digCenter.z));

        if (this.triggerType == TriggerType.STICK && this.wolf.level() instanceof ServerLevel serverLevel && this.wolf.tickCount % 9 == 0) {
            spawnSearchTrailParticles(serverLevel, digCenter);
        }

        if (this.wolf.distanceToSqr(digCenter.x, digCenter.y, digCenter.z) <= 2.25D || this.phaseTicks <= 0 || this.noProgressTicks >= STUCK_LIMIT) {
            this.wolf.getNavigation().stop();
            beginPhase(Phase.DIGGING, DIGGING_TICKS);
        }
    }

    private void tickDigging() {
        this.phaseTicks--;
        this.wolf.getNavigation().stop();
        if (this.wolf.level() instanceof ServerLevel serverLevel && this.wolf.tickCount % 4 == 0) {
            spawnDiggingParticles(serverLevel);
        }
        if (this.phaseTicks <= 0) finishSurfaceDig();
    }

    private void tickReturning() {
        if (this.carriedLoot.isEmpty()) {
            resetState();
            return;
        }

        this.phaseTicks--;
        Entity owner = this.wolf.getOwner();
        double speed = this.triggerType == TriggerType.GOLDEN_BONE ? 1.26D : 1.16D;
        if (owner != null && owner.level() == this.wolf.level()) {
            this.wolf.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            navigateTo(owner.position(), speed);
            if (this.wolf.distanceToSqr(owner) <= 9.0D) {
                deliverLoot();
                return;
            }
        }
        if (this.phaseTicks <= 0 || owner == null || owner.level() != this.wolf.level()) deliverLoot();
    }

    private void tickHeistPrepare() {
        if (!(this.wolf.level() instanceof ServerLevel serverLevel)) {
            resetState();
            return;
        }

        this.phaseTicks--;

        if (this.heistPortalStand == null && !createHeistPortalNear(this.wolf.position(), true)) {
            if (this.phaseTicks <= 0) {
                this.wolf.setInvisible(true);
                this.wolf.setInvulnerable(true);
                beginPhase(Phase.HEIST_AWAY, HEIST_MIN_AWAY_TICKS + this.wolf.getRandom().nextInt(HEIST_AWAY_RANDOM_TICKS + 1));
            }
            return;
        }

        Vec3 portalCenter = getHeistPortalCenter();
        this.wolf.getLookControl().setLookAt(portalCenter.x, portalCenter.y + 0.4D, portalCenter.z, 30.0F, 30.0F);
        navigateTo(portalCenter, 1.18D);
        trackProgress(this.wolf.distanceToSqr(portalCenter.x, portalCenter.y, portalCenter.z));

        if (this.wolf.tickCount % 4 == 0) {
            spawnHeistPortalAmbient(serverLevel, portalCenter, false);
        }
        if (this.phaseTicks % 14 == 0) {
            serverLevel.playSound(null, this.heistPortalStand, SoundEvents.PORTAL_AMBIENT, SoundSource.NEUTRAL, 0.20F, getPortalPitch());
        }

        if (this.wolf.distanceToSqr(portalCenter.x, portalCenter.y, portalCenter.z) <= 1.8D || this.phaseTicks <= 0) {
            this.wolf.getNavigation().stop();
            spawnHeistPortalBurst(serverLevel, portalCenter, false);
            clearHeistPortal();
            this.wolf.setInvisible(true);
            this.wolf.setInvulnerable(true);
            beginPhase(Phase.HEIST_AWAY, HEIST_MIN_AWAY_TICKS + this.wolf.getRandom().nextInt(HEIST_AWAY_RANDOM_TICKS + 1));
        }
    }

    private void tickHeistAway() {
        this.phaseTicks--;
        this.wolf.getNavigation().stop();

        if (this.wolf.level() instanceof ServerLevel serverLevel && this.wolf.tickCount % 20 == 0) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, this.wolf.getX(), this.wolf.getY() + 0.5D, this.wolf.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.12D);
        }

        if (this.phaseTicks == 40) {
            Player owner = getOwnerPlayer();
            if (owner != null) owner.displayClientMessage(Component.literal("§e[寻宝专家] 周围的空间波动越来越明显，狗狗像是快要叼着战利品回来了。"), true);
        }

        if (this.phaseTicks <= 0) finishHeist();
    }

    private void tickHeistReturnReveal() {
        if (!(this.wolf.level() instanceof ServerLevel serverLevel)) {
            beginPhase(this.pendingHeistPhase, this.pendingHeistDuration);
            return;
        }

        this.phaseTicks--;
        this.wolf.getNavigation().stop();

        if (this.heistPortalStand != null) {
            Vec3 portalCenter = getHeistPortalCenter();
            this.wolf.getLookControl().setLookAt(portalCenter.x, portalCenter.y + 0.4D, portalCenter.z, 20.0F, 20.0F);
            if (this.wolf.tickCount % 4 == 0) {
                spawnHeistPortalAmbient(serverLevel, portalCenter, true);
            }
        }

        if (this.phaseTicks <= 0) {
            clearHeistPortal();
            this.wolf.setInvulnerable(false);
            beginPhase(this.pendingHeistPhase, this.pendingHeistDuration);
            this.pendingHeistPhase = Phase.IDLE;
            this.pendingHeistDuration = 0;
        }
    }

    private void tickPanic() {
        if (!(this.wolf.level() instanceof ServerLevel serverLevel)) {
            beginPhase(Phase.RETURNING, RETURN_TIMEOUT);
            return;
        }

        this.phaseTicks--;
        List<Monster> guards = getActiveGuards(serverLevel);
        if (guards.isEmpty() || this.phaseTicks <= 0) {
            this.guardIds.clear();
            beginPhase(Phase.RETURNING, RETURN_TIMEOUT);
            return;
        }

        Monster closest = guards.stream().min(Comparator.comparingDouble(this.wolf::distanceToSqr)).orElse(null);
        if (closest == null) {
            this.guardIds.clear();
            beginPhase(Phase.RETURNING, RETURN_TIMEOUT);
            return;
        }

        Vec3 escapeTarget = getPanicEscapeTarget(closest);
        this.wolf.getLookControl().setLookAt(closest, 30.0F, 30.0F);
        navigateTo(escapeTarget, 1.42D);
        if (this.wolf.tickCount % 8 == 0) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, this.wolf.getX(), this.wolf.getY() + 0.4D, this.wolf.getZ(), 3, 0.12D, 0.08D, 0.12D, 0.0D);
        }
    }

    private void consumeTriggerAndAdvance() {
        if (!consumeOneFromTarget()) {
            resetState();
            return;
        }

        this.wolf.level().playSound(null, this.wolf.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.45F, 1.1F);
        this.wolf.getNavigation().stop();
        this.wolf.setIsInterested(false);

        if (this.triggerType == TriggerType.GOLDEN_BONE) {
            this.heistType = this.wolf.getRandom().nextInt(5);
            this.heistSuccess = false;
            this.guardIds.clear();
            this.carriedLoot = ItemStack.EMPTY;
            this.pendingHeistPhase = Phase.IDLE;
            this.pendingHeistDuration = 0;
            createHeistPortalNear(this.wolf.position(), true);
            broadcastHeistStart();
            beginPhase(Phase.HEIST_PREPARE, HEIST_PREPARE_TICKS);
            return;
        }

        startSurfaceDigRun();
    }

    private void startSurfaceDigRun() {
        this.recentDigSites.clear();
        this.carriedLoot = ItemStack.EMPTY;
        this.completedSurfaceDigs = 0;
        this.plannedSurfaceDigs = getPlannedSurfaceDigCount();
        this.digSite = pickNextDigSite();

        Player owner = getOwnerPlayer();
        if (owner != null) {
            owner.displayClientMessage(Component.literal("§6[寻宝专家] 狗狗叼起木棍后开始在附近来回闻嗅，像是准备连挖 " + this.plannedSurfaceDigs + " 处可疑土层。"), true);
        }

        if (this.digSite == null) {
            this.digSite = this.wolf.blockPosition();
            beginPhase(Phase.DIGGING, DIGGING_TICKS);
        } else {
            beginPhase(Phase.MOVE_TO_DIG_SITE, DIG_SITE_TIMEOUT + 20);
        }
    }

    private void finishSurfaceDig() {
        boolean finalDig = this.completedSurfaceDigs + 1 >= this.plannedSurfaceDigs;
        if (this.digSite != null) rememberDigSite(this.digSite);
        this.completedSurfaceDigs++;
        if (this.wolf.level() instanceof ServerLevel serverLevel) spawnSurfaceResolveParticles(serverLevel, finalDig);

        if (!finalDig) {
            BlockPos nextSite = pickNextDigSite();
            if (nextSite != null) {
                this.digSite = nextSite;
                Player owner = getOwnerPlayer();
                if (owner != null) owner.displayClientMessage(Component.literal("§e[寻宝专家] 这一铲还差点意思，狗狗甩甩爪子又换了个位置继续刨。"), true);
                beginPhase(Phase.MOVE_TO_DIG_SITE, DIG_SITE_TIMEOUT + 20);
                return;
            }
        }

        this.carriedLoot = rollSurfaceLoot(this.plannedSurfaceDigs);
        Player owner = getOwnerPlayer();
        if (owner != null) owner.displayClientMessage(Component.literal("§6[寻宝专家] 狗狗终于挖到点像样的东西，正一路小跑往你这边叼回来。"), true);
        beginPhase(Phase.RETURNING, RETURN_TIMEOUT + Math.max(0, this.completedSurfaceDigs - 1) * 20);
    }

    private void finishHeist() {
        if (!(this.wolf.level() instanceof ServerLevel serverLevel)) {
            resetState();
            return;
        }

        Entity owner = this.wolf.getOwner();
        Vec3 center = owner != null && owner.level() == this.wolf.level() ? owner.position() : this.wolf.position();
        BlockPos returnPos = findStandablePosNear(center, 6, 3, 24);
        if (returnPos != null) {
            createHeistPortalNear(Vec3.atBottomCenterOf(returnPos), false);
        } else {
            clearHeistPortal();
        }

        if (this.heistPortalStand != null) {
            Vec3 portalCenter = getHeistPortalCenter();
            this.wolf.teleportTo(portalCenter.x, this.heistPortalStand.getY(), portalCenter.z);
        } else if (returnPos != null) {
            this.wolf.teleportTo(returnPos.getX() + 0.5D, returnPos.getY(), returnPos.getZ() + 0.5D);
        }

        this.wolf.setInvisible(false);
        Vec3 returnCenter = this.heistPortalStand != null ? getHeistPortalCenter() : this.wolf.position();
        spawnHeistPortalBurst(serverLevel, returnCenter, true);
        serverLevel.playSound(null, this.wolf.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.8F, getPortalPitch());

        this.heistSuccess = rollHeistSuccess();
        if (this.heistSuccess) {
            this.carriedLoot = getHeistLoot(this.heistType);
            broadcastHeistSuccess();
            this.pendingHeistPhase = Phase.RETURNING;
            this.pendingHeistDuration = RETURN_TIMEOUT + 40;
            beginPhase(Phase.HEIST_RETURN_REVEAL, HEIST_RETURN_REVEAL_TICKS);
            return;
        }

        this.carriedLoot = getPanicLoot(this.heistType);
        this.wolf.setHealth(Math.max(6.0F, this.wolf.getHealth() - 6.0F));
        this.wolf.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, PANIC_TICKS, 1, false, false));
        this.wolf.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0, false, false));
        broadcastHeistFailure();
        spawnHeistGuards(serverLevel);
        this.pendingHeistPhase = this.guardIds.isEmpty() ? Phase.RETURNING : Phase.PANIC;
        this.pendingHeistDuration = this.guardIds.isEmpty() ? RETURN_TIMEOUT : PANIC_TICKS;
        beginPhase(Phase.HEIST_RETURN_REVEAL, HEIST_RETURN_REVEAL_TICKS);
    }

    private void deliverLoot() {
        if (!this.carriedLoot.isEmpty()) this.wolf.spawnAtLocation(this.carriedLoot.copy());
        this.wolf.level().playSound(null, this.wolf.blockPosition(), SoundEvents.WOLF_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.15F);
        if (this.wolf.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(this.triggerType == TriggerType.GOLDEN_BONE ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.HAPPY_VILLAGER, this.wolf.getX(), this.wolf.getY() + 0.8D, this.wolf.getZ(), this.triggerType == TriggerType.GOLDEN_BONE ? 12 : 8, 0.2D, 0.15D, 0.2D, 0.0D);
        }

        PetData data = this.wolf.getData(ModAttachmentTypes.PET_DATA);
        if (this.triggerType == TriggerType.GOLDEN_BONE) {
            if (this.heistSuccess && this.wolf.getOwner() instanceof ServerPlayer serverPlayer) DogUtils.grantAdvancement(serverPlayer, "heist_success");
            if (this.heistSuccess && data.getAffinity() < 100) data.increaseAffinity(2);
            applyHeistCooldown(this.heistSuccess);
        } else if (data.getAffinity() < 100) {
            data.increaseAffinity(this.completedSurfaceDigs >= 3 ? 2 : 1);
        }

        resetState();
    }

    private boolean isTriggerItem(ItemEntity item) {
        if (item == null || !item.isAlive() || item.hasPickUpDelay()) return false;
        ItemStack stack = item.getItem();
        return !stack.isEmpty() && (stack.is(Items.STICK) || stack.is(ModItems.GOLDEN_BONE.get()));
    }

    private boolean isExpectedTargetAlive() {
        if (this.targetItem == null || !this.targetItem.isAlive() || this.targetItem.hasPickUpDelay()) return false;
        ItemStack stack = this.targetItem.getItem();
        return switch (this.triggerType) {
            case STICK -> stack.is(Items.STICK);
            case GOLDEN_BONE -> stack.is(ModItems.GOLDEN_BONE.get());
            default -> false;
        };
    }

    private boolean consumeOneFromTarget() {
        if (!isExpectedTargetAlive()) return false;
        ItemStack stack = this.targetItem.getItem();
        stack.shrink(1);
        if (stack.isEmpty()) this.targetItem.discard();
        this.targetItem = null;
        this.noProgressTicks = 0;
        this.lastDistanceSqr = Double.MAX_VALUE;
        return true;
    }

    private boolean canStartHeist(PetData data, long now) {
        return data != null && data.getAffinity() >= 100 && now >= data.getDiggerHeistCooldownUntil();
    }

    private void maybeSendBlockedHeistHint(PetData data, long now) {
        if (now - this.lastOwnerHintTime < OWNER_HINT_COOLDOWN) return;
        this.lastOwnerHintTime = now;
        Player owner = getOwnerPlayer();
        if (owner == null) return;
        if (data.getAffinity() < 100) {
            owner.displayClientMessage(Component.literal("§7[寻宝专家] 狗狗盯着黄金骨头看了好一会儿，但你们的亲密度还没拉满，它还不敢去赌这种远门。"), true);
        } else {
            owner.displayClientMessage(Component.literal("§7[寻宝专家] 狗狗闻了闻黄金骨头，又悄悄缩了回来。上次那趟太惊险，它还想再缓一缓。"), true);
        }
        this.wolf.playSound(SoundEvents.WOLF_WHINE, 0.8F, 1.0F);
    }

    private void beginPhase(Phase nextPhase, int duration) {
        this.phase = nextPhase;
        this.phaseTicks = duration;
        this.noProgressTicks = 0;
        this.lastDistanceSqr = Double.MAX_VALUE;
    }

    private void trackProgress(double distanceSqr) {
        if (distanceSqr + 0.2D < this.lastDistanceSqr) this.noProgressTicks = 0;
        else this.noProgressTicks++;
        this.lastDistanceSqr = distanceSqr;
    }

    private void navigateTo(Vec3 target, double speed) {
        if (this.wolf.tickCount % REPATH_INTERVAL == 0 || this.wolf.getNavigation().isDone()) {
            this.wolf.getNavigation().moveTo(target.x, target.y, target.z, speed);
        }
    }

    private int getPlannedSurfaceDigCount() {
        PetData data = this.wolf.getData(ModAttachmentTypes.PET_DATA);
        int range = (data.isAwakened() || data.getAffinity() >= 80) ? 3 : 2;
        return Math.min(SURFACE_DIG_MAX_STOPS, SURFACE_DIG_MIN_STOPS + this.wolf.getRandom().nextInt(range));
    }

    private BlockPos pickNextDigSite() {
        BlockPos origin = this.wolf.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int dx = -DIG_RADIUS; dx <= DIG_RADIUS; dx++) {
            for (int dz = -DIG_RADIUS; dz <= DIG_RADIUS; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (!isValidDigSite(candidate) || isNearRecentDigSite(candidate)) continue;
                    double recentScore = getNearestRecentDigDistanceSqr(candidate);
                    double score = origin.distSqr(candidate) - Math.min(recentScore, 36.0D) * 0.4D + this.wolf.getRandom().nextDouble() * 0.25D;
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }

    private void rememberDigSite(BlockPos pos) {
        if (pos == null) return;
        this.recentDigSites.add(pos.immutable());
        while (this.recentDigSites.size() > SURFACE_DIG_MEMORY) this.recentDigSites.remove(0);
    }

    private boolean isNearRecentDigSite(BlockPos candidate) {
        return this.recentDigSites.stream().anyMatch(old -> candidate.distSqr(old) < SURFACE_DIG_MIN_DISTANCE_SQR);
    }

    private double getNearestRecentDigDistanceSqr(BlockPos candidate) {
        double nearest = Double.MAX_VALUE;
        for (BlockPos used : this.recentDigSites) nearest = Math.min(nearest, candidate.distSqr(used));
        return nearest;
    }

    private void spawnSearchTrailParticles(ServerLevel serverLevel, Vec3 center) {
        serverLevel.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.45D, center.z, 3, 0.12D, 0.04D, 0.12D, 0.0D);
        if (this.wolf.tickCount % 18 == 0) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 0.55D, center.z, 1, 0.08D, 0.04D, 0.08D, 0.0D);
        }
    }

    private void spawnDiggingParticles(ServerLevel serverLevel) {
        BlockPos particlePos = this.digSite != null ? this.digSite.below() : this.wolf.blockPosition().below();
        BlockState state = serverLevel.getBlockState(particlePos);
        if (!state.isAir()) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), this.wolf.getX(), this.wolf.getY() + 0.25D, this.wolf.getZ(), 8, 0.2D, 0.1D, 0.2D, 0.01D);
        }
        if (this.phaseTicks <= 20) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, this.wolf.getX(), this.wolf.getY() + 0.4D, this.wolf.getZ(), 2, 0.14D, 0.06D, 0.14D, 0.0D);
        }
        serverLevel.playSound(null, this.wolf.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.NEUTRAL, 0.45F, 0.95F + this.wolf.getRandom().nextFloat() * 0.15F);
    }

    private void spawnSurfaceResolveParticles(ServerLevel serverLevel, boolean finalDig) {
        BlockPos particlePos = this.digSite != null ? this.digSite.below() : this.wolf.blockPosition().below();
        BlockState state = serverLevel.getBlockState(particlePos);
        if (!state.isAir()) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), this.wolf.getX(), this.wolf.getY() + 0.28D, this.wolf.getZ(), finalDig ? 16 : 10, 0.24D, 0.12D, 0.24D, 0.02D);
        }
        serverLevel.sendParticles(finalDig ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.CLOUD, this.wolf.getX(), this.wolf.getY() + 0.65D, this.wolf.getZ(), finalDig ? 8 : 4, 0.16D, 0.08D, 0.16D, 0.0D);
        if (finalDig) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT, this.wolf.getX(), this.wolf.getY() + 0.7D, this.wolf.getZ(), 6, 0.18D, 0.1D, 0.18D, 0.01D);
            serverLevel.playSound(null, this.wolf.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 1.2F);
        }
    }

    private boolean isValidDigSite(BlockPos pos) {
        if (pos == null || !this.wolf.level().isEmptyBlock(pos) || !this.wolf.level().isEmptyBlock(pos.above())) return false;
        BlockPos groundPos = pos.below();
        BlockState ground = this.wolf.level().getBlockState(groundPos);
        return ground.isFaceSturdy(this.wolf.level(), groundPos, Direction.UP) && isDiggableGround(ground);
    }

    private boolean isDiggableGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MUD) || state.is(Blocks.CLAY) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.MOSS_BLOCK);
    }

    private BlockPos findStandablePosNear(Vec3 center, int horizontalRadius, int verticalRadius, int attempts) {
        BlockPos fallback = BlockPos.containing(center);
        for (int i = 0; i < attempts; i++) {
            BlockPos candidate = BlockPos.containing(center).offset(this.wolf.getRandom().nextInt(horizontalRadius * 2 + 1) - horizontalRadius, this.wolf.getRandom().nextInt(verticalRadius * 2 + 1) - verticalRadius, this.wolf.getRandom().nextInt(horizontalRadius * 2 + 1) - horizontalRadius);
            if (this.wolf.level().isEmptyBlock(candidate) && this.wolf.level().isEmptyBlock(candidate.above()) && this.wolf.level().getBlockState(candidate.below()).isFaceSturdy(this.wolf.level(), candidate.below(), Direction.UP)) return candidate;
        }
        if (this.wolf.level().isEmptyBlock(fallback) && this.wolf.level().getBlockState(fallback.below()).isFaceSturdy(this.wolf.level(), fallback.below(), Direction.UP)) return fallback;
        return null;
    }

    private boolean createHeistPortalNear(Vec3 center, boolean keepDistanceFromWolf) {
        if (!(this.wolf.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        clearHeistPortal();
        BlockPos origin = BlockPos.containing(center);

        for (int radius = 0; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos stand = origin.offset(dx, dy, dz);
                        if (keepDistanceFromWolf && stand.distSqr(this.wolf.blockPosition()) < HEIST_PORTAL_MIN_DISTANCE_SQR) {
                            continue;
                        }

                        Direction preferred = getPortalFacingFor(center, stand);
                        if (canPlacePortalAt(stand, preferred)) {
                            placeHeistPortal(serverLevel, stand, preferred);
                            return true;
                        }

                        for (Direction direction : Direction.Plane.HORIZONTAL) {
                            if (direction != preferred && canPlacePortalAt(stand, direction)) {
                                placeHeistPortal(serverLevel, stand, direction);
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private Direction getPortalFacingFor(Vec3 center, BlockPos stand) {
        double dx = center.x - (stand.getX() + 0.5D);
        double dz = center.z - (stand.getZ() + 0.5D);
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private boolean canPlacePortalAt(BlockPos stand, Direction facing) {
        if (!this.wolf.level().isEmptyBlock(stand) || !this.wolf.level().isEmptyBlock(stand.above())) {
            return false;
        }
        BlockPos below = stand.below();
        if (!this.wolf.level().getBlockState(below).isFaceSturdy(this.wolf.level(), below, Direction.UP)) {
            return false;
        }

        for (BlockPos framePos : getPortalFrameBlocks(stand, facing)) {
            if (!this.wolf.level().isEmptyBlock(framePos)) {
                return false;
            }
        }
        return true;
    }

    private List<BlockPos> getPortalFrameBlocks(BlockPos stand, Direction facing) {
        Direction side = facing.getClockWise();
        Direction otherSide = side.getOpposite();
        List<BlockPos> blocks = new ArrayList<>(7);
        blocks.add(stand.relative(side));
        blocks.add(stand.relative(side).above());
        blocks.add(stand.relative(side).above(2));
        blocks.add(stand.relative(otherSide));
        blocks.add(stand.relative(otherSide).above());
        blocks.add(stand.relative(otherSide).above(2));
        blocks.add(stand.above(2));
        return blocks;
    }

    private void placeHeistPortal(ServerLevel serverLevel, BlockPos stand, Direction facing) {
        this.heistPortalStand = stand.immutable();
        this.heistPortalFacing = facing;
        this.heistPortalBlocks.clear();

        BlockState glass = getPortalGlassState();
        for (BlockPos framePos : getPortalFrameBlocks(stand, facing)) {
            serverLevel.setBlock(framePos, glass, 3);
            this.heistPortalBlocks.add(framePos.immutable());
            PROTECTED_HEIST_PORTAL_BLOCKS.add(getPortalProtectionKey(serverLevel, framePos));
        }
    }

    private void clearHeistPortal() {
        if (this.wolf.level() instanceof ServerLevel serverLevel) {
            for (BlockPos framePos : this.heistPortalBlocks) {
                if (!serverLevel.isLoaded(framePos)) {
                    continue;
                }
                if (!serverLevel.getBlockState(framePos).isAir()) {
                    serverLevel.setBlock(framePos, Blocks.AIR.defaultBlockState(), 3);
                }
                PROTECTED_HEIST_PORTAL_BLOCKS.remove(getPortalProtectionKey(serverLevel, framePos));
            }
        }
        this.heistPortalBlocks.clear();
        this.heistPortalStand = null;
        this.heistPortalFacing = Direction.NORTH;
    }

    private Vec3 getHeistPortalCenter() {
        return this.heistPortalStand == null
                ? this.wolf.position()
                : Vec3.atBottomCenterOf(this.heistPortalStand);
    }

    private BlockState getPortalGlassState() {
        return switch (this.heistType) {
            case 0 -> Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
            case 1 -> Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState();
            case 2 -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            case 3 -> Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
            default -> Blocks.MAGENTA_STAINED_GLASS.defaultBlockState();
        };
    }

    private float getPortalPitch() {
        return switch (this.heistType) {
            case 0 -> 1.28F;
            case 1 -> 0.92F;
            case 2 -> 0.78F;
            case 3 -> 0.64F;
            default -> 1.45F;
        };
    }

    private void spawnHeistPortalAmbient(ServerLevel serverLevel, Vec3 center, boolean arrival) {
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, getPortalGlassState()), center.x, center.y + 1.0D, center.z, arrival ? 4 : 6, 0.45D, 0.65D, 0.45D, 0.02D);

        switch (this.heistType) {
            case 0 -> {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 0.8D, center.z, arrival ? 3 : 5, 0.18D, 0.45D, 0.18D, 0.0D);
                serverLevel.sendParticles(ParticleTypes.ENCHANT, center.x, center.y + 0.9D, center.z, 4, 0.22D, 0.55D, 0.22D, 0.01D);
            }
            case 1 -> {
                serverLevel.sendParticles(ParticleTypes.ENCHANT, center.x, center.y + 0.85D, center.z, 5, 0.2D, 0.6D, 0.2D, 0.01D);
                serverLevel.sendParticles(ParticleTypes.CRIT, center.x, center.y + 1.0D, center.z, arrival ? 2 : 4, 0.18D, 0.5D, 0.18D, 0.0D);
            }
            case 2 -> {
                serverLevel.sendParticles(ParticleTypes.BUBBLE, center.x, center.y + 0.8D, center.z, arrival ? 4 : 6, 0.18D, 0.55D, 0.18D, 0.02D);
                serverLevel.sendParticles(ParticleTypes.SPLASH, center.x, center.y + 0.7D, center.z, 3, 0.2D, 0.4D, 0.2D, 0.01D);
            }
            case 3 -> {
                serverLevel.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.8D, center.z, arrival ? 4 : 6, 0.2D, 0.55D, 0.2D, 0.02D);
                serverLevel.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 0.95D, center.z, 4, 0.22D, 0.6D, 0.22D, 0.01D);
            }
            default -> {
                serverLevel.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 0.9D, center.z, arrival ? 6 : 9, 0.2D, 0.65D, 0.2D, 0.06D);
                serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y + 0.95D, center.z, 3, 0.2D, 0.45D, 0.2D, 0.01D);
            }
        }
    }

    private void spawnHeistPortalBurst(ServerLevel serverLevel, Vec3 center, boolean arrival) {
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, getPortalGlassState()), center.x, center.y + 1.0D, center.z, arrival ? 22 : 18, 0.5D, 0.8D, 0.5D, 0.05D);

        switch (this.heistType) {
            case 0 -> {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 0.9D, center.z, 10, 0.28D, 0.7D, 0.28D, 0.0D);
                serverLevel.sendParticles(ParticleTypes.ENCHANT, center.x, center.y + 0.95D, center.z, 12, 0.25D, 0.75D, 0.25D, 0.02D);
            }
            case 1 -> {
                serverLevel.sendParticles(ParticleTypes.ENCHANT, center.x, center.y + 0.95D, center.z, 12, 0.24D, 0.75D, 0.24D, 0.02D);
                serverLevel.sendParticles(ParticleTypes.CRIT, center.x, center.y + 0.9D, center.z, 8, 0.22D, 0.6D, 0.22D, 0.01D);
            }
            case 2 -> {
                serverLevel.sendParticles(ParticleTypes.BUBBLE, center.x, center.y + 0.85D, center.z, 12, 0.24D, 0.7D, 0.24D, 0.03D);
                serverLevel.sendParticles(ParticleTypes.SPLASH, center.x, center.y + 0.75D, center.z, 10, 0.28D, 0.55D, 0.28D, 0.02D);
            }
            case 3 -> {
                serverLevel.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.9D, center.z, 12, 0.24D, 0.7D, 0.24D, 0.03D);
                serverLevel.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 0.95D, center.z, 12, 0.28D, 0.8D, 0.28D, 0.02D);
            }
            default -> {
                serverLevel.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 0.9D, center.z, 18, 0.24D, 0.75D, 0.24D, 0.08D);
                serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y + 0.95D, center.z, 10, 0.28D, 0.7D, 0.28D, 0.02D);
            }
        }
    }

    private Vec3 getPanicEscapeTarget(Monster closestGuard) {
        Vec3 away = this.wolf.position().subtract(closestGuard.position());
        if (away.lengthSqr() < 1.0E-4D) away = new Vec3(this.wolf.getRandom().nextDouble() - 0.5D, 0.0D, this.wolf.getRandom().nextDouble() - 0.5D);
        Vec3 escape = this.wolf.position().add(away.normalize().scale(8.0D));
        Entity owner = this.wolf.getOwner();
        if (owner != null && owner.level() == this.wolf.level()) {
            Vec3 towardOwner = owner.position().subtract(this.wolf.position());
            if (towardOwner.lengthSqr() > 1.0D) escape = escape.add(towardOwner.normalize().scale(3.0D));
        }
        return new Vec3(escape.x, this.wolf.getY(), escape.z);
    }

    private void broadcastHeistStart() {
        Player owner = getOwnerPlayer();
        if (owner != null) owner.displayClientMessage(Component.literal(getHeistStartMessage()), true);
    }

    private void broadcastHeistSuccess() {
        Player owner = getOwnerPlayer();
        if (owner != null) owner.sendSystemMessage(Component.literal(getHeistSuccessMessage()));
    }

    private void broadcastHeistFailure() {
        Player owner = getOwnerPlayer();
        if (owner != null) owner.sendSystemMessage(Component.literal(getHeistFailureMessage()));
    }

    private String getHeistStartMessage() {
        return switch (this.heistType) {
            case 0 -> "§e[寻宝专家] 狗狗叼着黄金骨头绕了两圈，像是打算去附近村子里做一笔小买卖。";
            case 1 -> "§6[寻宝专家] 狗狗耳朵一竖，悄悄朝林地府邸的方向摸过去了。";
            case 2 -> "§b[寻宝专家] 狗狗一头扎进潮湿的气味里，像是盯上了海底神殿。";
            case 3 -> "§c[寻宝专家] 狗狗嗅到了地狱里的金属味，叼着黄金骨头就往空间裂缝里钻。";
            default -> "§5[寻宝专家] 狗狗的毛都立起来了，它像是准备去偷一件不该存在的东西。";
        };
    }

    private String getHeistSuccessMessage() {
        return switch (this.heistType) {
            case 0 -> "§e[寻宝专家] 狗狗从村里晃了一圈回来，嘴里叼着一堆换来的好东西。";
            case 1 -> "§a[寻宝专家] 完美潜入。它把林地府邸里最值钱的玩意儿顺了回来。";
            case 2 -> "§a[寻宝专家] 深海得手。狗狗把海底神殿里的珍贵战利品拖了回来。";
            case 3 -> "§a[寻宝专家] 这一趟地狱没白去，猪灵们压箱底的宝贝都被它叼回来了。";
            default -> "§d[寻宝专家] 这次像是钻进了世界缝隙，狗狗带回来的东西明显不太正常。";
        };
    }

    private String getHeistFailureMessage() {
        return switch (this.heistType) {
            case 1 -> "§c[寻宝专家] 狗狗在府邸里翻箱倒柜时被发现了，追兵已经冲出来了。";
            case 2 -> "§c[寻宝专家] 海底神殿那边炸锅了，狗狗正被一群水里的家伙撵着跑。";
            case 3 -> "§4[寻宝专家] 地狱那边翻车了，狗狗把猪灵彻底惹毛了。";
            case 4 -> "§4[寻宝专家] 空间裂缝里的东西追出来了，快掩护狗狗撤回来。";
            default -> "§e[寻宝专家] 狗狗灰头土脸地逃了回来，虽然没完全空手，但明显被吓得不轻。";
        };
    }

    private boolean rollHeistSuccess() {
        float chance = switch (this.heistType) {
            case 0 -> 1.0F;
            case 1 -> 0.66F;
            case 2 -> 0.60F;
            case 3 -> 0.55F;
            default -> 0.50F;
        };
        PetData data = this.wolf.getData(ModAttachmentTypes.PET_DATA);
        if (data.isAwakened()) chance += 0.1F;
        return this.wolf.getRandom().nextFloat() < Math.min(chance, 0.95F);
    }

    private void spawnHeistGuards(ServerLevel serverLevel) {
        int guardCount = switch (this.heistType) {
            case 4 -> 3;
            case 1, 2, 3 -> 2;
            default -> 0;
        };
        for (int i = 0; i < guardCount; i++) {
            Monster guard = createGuard(serverLevel);
            if (guard == null) continue;
            BlockPos spawnPos = findStandablePosNear(this.wolf.position(), 6, 3, 20);
            if (spawnPos == null) spawnPos = this.wolf.blockPosition();
            guard.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, this.wolf.getRandom().nextFloat() * 360.0F, 0.0F);
            serverLevel.addFreshEntity(guard);
            guard.setTarget(this.wolf);
            this.guardIds.add(guard.getUUID());
        }
    }

    private Monster createGuard(ServerLevel serverLevel) {
        return switch (this.heistType) {
            case 1 -> this.wolf.getRandom().nextBoolean() ? EntityType.VINDICATOR.create(serverLevel) : EntityType.PILLAGER.create(serverLevel);
            case 2 -> {
                Monster drowned = EntityType.DROWNED.create(serverLevel);
                if (drowned != null && this.wolf.getRandom().nextBoolean()) drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
                yield drowned;
            }
            case 3 -> this.wolf.getRandom().nextBoolean() ? EntityType.PIGLIN_BRUTE.create(serverLevel) : EntityType.BLAZE.create(serverLevel);
            case 4 -> switch (this.wolf.getRandom().nextInt(5)) {
                case 0 -> EntityType.CREEPER.create(serverLevel);
                case 1 -> EntityType.WITCH.create(serverLevel);
                case 2 -> EntityType.VINDICATOR.create(serverLevel);
                case 3 -> EntityType.CAVE_SPIDER.create(serverLevel);
                default -> EntityType.HUSK.create(serverLevel);
            };
            default -> null;
        };
    }

    private List<Monster> getActiveGuards(ServerLevel serverLevel) {
        return this.guardIds.stream().map(serverLevel::getEntity).filter(Monster.class::isInstance).map(Monster.class::cast).filter(Entity::isAlive).toList();
    }

    private void applyHeistCooldown(boolean success) {
        PetData data = this.wolf.getData(ModAttachmentTypes.PET_DATA);
        if (data == null) return;
        float cooldownChance = success ? 0.35F : 0.85F;
        if (this.wolf.getRandom().nextFloat() >= cooldownChance) return;
        data.setDiggerHeistCooldownUntil(this.wolf.level().getGameTime() + (success ? 3600L : 6000L));
        Player owner = getOwnerPlayer();
        if (owner == null) return;
        owner.sendSystemMessage(Component.literal(success ? "§7[寻宝专家] 这一趟赚到了，但狗狗还想缓一缓，短时间内不会再接这种大活。" : "§7[寻宝专家] 刚才那阵追杀把狗狗吓到了，接下来一段时间它不会再碰这种远征。"));
    }

    private ItemStack rollSurfaceLoot(int digCount) {
        PetData data = this.wolf.getData(ModAttachmentTypes.PET_DATA);
        int bonus = Math.max(0, digCount - 2) + (data.isAwakened() ? 1 : 0);
        int roll = this.wolf.getRandom().nextInt(18 + bonus * 2);
        return switch (roll) {
            case 0 -> new ItemStack(Items.DANDELION, 1 + bonus);
            case 1 -> new ItemStack(Items.POPPY, 1 + bonus);
            case 2 -> new ItemStack(Items.APPLE);
            case 3 -> new ItemStack(Items.SWEET_BERRIES, 2 + bonus);
            case 4 -> new ItemStack(Items.FLINT, 2 + bonus);
            case 5 -> new ItemStack(Items.FEATHER, 2 + bonus);
            case 6 -> new ItemStack(Items.CLAY_BALL, 3 + bonus);
            case 7 -> new ItemStack(Items.BONE, 1 + bonus / 2);
            case 8 -> new ItemStack(Items.COAL, 2 + bonus);
            case 9 -> new ItemStack(Items.COPPER_INGOT, 1 + bonus);
            case 10 -> new ItemStack(Items.IRON_NUGGET, 4 + bonus * 2);
            case 11 -> new ItemStack(Items.MOSS_BLOCK);
            case 12 -> new ItemStack(Items.SEA_PICKLE, 1 + bonus);
            case 13 -> new ItemStack(Items.KELP, 3 + bonus);
            case 14 -> new ItemStack(Items.PRISMARINE_SHARD, 2 + bonus);
            case 15 -> new ItemStack(Items.GOLD_NUGGET, 3 + bonus * 2);
            case 16 -> new ItemStack(Items.AMETHYST_SHARD, 1 + bonus);
            case 17 -> new ItemStack(Items.EMERALD, 1 + Math.max(0, bonus - 1));
            default -> new ItemStack(Items.STICK, 2 + bonus);
        };
    }

    private ItemStack getHeistLoot(int type) {
        int roll = this.wolf.getRandom().nextInt(6);
        return switch (type) {
            case 0 -> roll < 2 ? new ItemStack(Items.EMERALD, 24) : (roll < 4 ? new ItemStack(Items.BELL) : new ItemStack(Items.DIAMOND, 3));
            case 1 -> roll < 2 ? new ItemStack(Items.TOTEM_OF_UNDYING) : (roll < 4 ? new ItemStack(Items.ENCHANTED_GOLDEN_APPLE) : new ItemStack(Items.EMERALD_BLOCK));
            case 2 -> roll < 2 ? new ItemStack(Items.HEART_OF_THE_SEA) : (roll < 4 ? new ItemStack(Items.SPONGE, 4) : new ItemStack(Items.TRIDENT));
            case 3 -> roll < 2 ? new ItemStack(Items.NETHERITE_SCRAP) : (roll < 4 ? new ItemStack(Items.MUSIC_DISC_PIGSTEP) : new ItemStack(Items.GILDED_BLACKSTONE, 8));
            default -> switch (roll) {
                case 0 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
                case 1 -> new ItemStack(Items.NETHERITE_INGOT);
                case 2 -> new ItemStack(Items.BEACON);
                case 3 -> new ItemStack(Items.DRAGON_BREATH, 6);
                case 4 -> new ItemStack(Items.END_CRYSTAL);
                default -> new ItemStack(Items.DIAMOND_BLOCK);
            };
        };
    }

    private ItemStack getPanicLoot(int type) {
        if (this.wolf.getRandom().nextFloat() < 0.35F) return getHeistLoot(type);
        return switch (type) {
            case 1 -> new ItemStack(Items.DARK_OAK_LOG, 2);
            case 2 -> new ItemStack(Items.KELP, 6);
            case 3 -> new ItemStack(Items.NETHERRACK, 6);
            case 4 -> new ItemStack(Items.REDSTONE, 8);
            default -> new ItemStack(Items.DIRT, 6);
        };
    }

    private Player getOwnerPlayer() {
        return this.wolf.getOwner() instanceof Player player ? player : null;
    }

    private void resetState() {
        clearHeistPortal();
        this.phase = Phase.IDLE;
        this.triggerType = TriggerType.NONE;
        this.targetItem = null;
        this.digSite = null;
        this.carriedLoot = ItemStack.EMPTY;
        this.guardIds.clear();
        this.recentDigSites.clear();
        this.plannedSurfaceDigs = 0;
        this.completedSurfaceDigs = 0;
        this.phaseTicks = 0;
        this.noProgressTicks = 0;
        this.heistType = 0;
        this.lastDistanceSqr = Double.MAX_VALUE;
        this.heistSuccess = false;
        this.pendingHeistPhase = Phase.IDLE;
        this.pendingHeistDuration = 0;
        this.wolf.getNavigation().stop();
        this.wolf.setInvisible(false);
        this.wolf.setInvulnerable(false);
        this.wolf.setTarget(null);
        this.wolf.setIsInterested(false);
    }
}
