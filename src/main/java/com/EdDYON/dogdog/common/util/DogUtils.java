package com.EdDYON.dogdog.common.util;

import com.EdDYON.dogdog.DogDog;
import com.EdDYON.dogdog.common.ai.DiggerGoal;
import com.EdDYON.dogdog.common.ai.HuskyGoal;
import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.attachment.PetPersonality;
import com.EdDYON.dogdog.common.attachment.personality.Personalities;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import com.EdDYON.dogdog.common.attachment.personality.impl.AggressivePersonality;
import com.EdDYON.dogdog.common.attachment.personality.impl.CowardPersonality;
import com.EdDYON.dogdog.common.config.DogDialogs;
import com.EdDYON.dogdog.common.entity.ai.AngelPurifyGoal;
import com.EdDYON.dogdog.common.entity.ai.DogHomeGoal;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import com.EdDYON.dogdog.common.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public class DogUtils {
    private static final int APPEARANCE_STYLE_COUNT = 7;
    private static final double AWAKENED_HEALTH_BONUS = 4.0D;
    private static final double AWAKENED_ATTACK_BONUS = 1.0D;
    private static final double AWAKENED_SPEED_BONUS = 0.02D;
    private static final ResourceLocation AWAKENED_HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(DogDog.MODID, "awakened_health");
    private static final ResourceLocation AWAKENED_ATTACK_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(DogDog.MODID, "awakened_attack");
    private static final ResourceLocation AWAKENED_SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(DogDog.MODID, "awakened_speed");

    public static Personality getLogic(Wolf wolf) {
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        return Personalities.get(data.getPersonality().getSerializedName());
    }

    public static void ensurePersonalityAssigned(Wolf wolf) {
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        if (data.getPersonality() == PetPersonality.NONE) {
            data.setPersonality(PetPersonality.getRandom());
        }
        if (data.getAppearanceStyle() < 0 || data.getAppearanceStyle() >= APPEARANCE_STYLE_COUNT) {
            data.setAppearanceStyle(chooseAppearanceStyle(wolf, data.getPersonality(), -1));
        }
        if (data.getGeneratedName().isBlank()) {
            data.setGeneratedName(DogNameGenerator.generateName(data.getPersonality(), data.getAppearanceStyle(), wolf.getRandom()));
        }
    }

    public static void applyCurrentPersonality(Wolf wolf, boolean healToFull) {
        ensurePersonalityAssigned(wolf);
        Personality logic = getLogic(wolf);
        clearPersonalityGoals(wolf);
        resetCombatGoals(wolf, logic);

        if (logic != null) {
            logic.applyAttributes(wolf);
            logic.applyAI(wolf);
        }

        applyAwakenedBlessing(wolf, logic);
        applyPersonalityVisuals(wolf);
        ensureHomeGoal(wolf);

        if (healToFull) {
            wolf.setHealth(wolf.getMaxHealth());
        } else if (wolf.getHealth() > wolf.getMaxHealth()) {
            wolf.setHealth(wolf.getMaxHealth());
        }

        updateHealthName(wolf);
    }

    private static void ensureHomeGoal(Wolf wolf) {
        if (!hasGoal(wolf.goalSelector, DogHomeGoal.class)) {
            wolf.goalSelector.addGoal(5, new DogHomeGoal(wolf));
        }
    }

    private static void clearPersonalityGoals(Wolf wolf) {
        removeGoals(wolf.goalSelector, goal ->
                goal instanceof DiggerGoal
                        || goal instanceof HuskyGoal
                        || goal instanceof AngelPurifyGoal
                        || goal instanceof CowardPersonality.CowardAvoidMonsterGoal
                        || goal instanceof CowardPersonality.CowardPanicGoal);
    }

    private static void resetCombatGoals(Wolf wolf, Personality logic) {
        removeGoals(wolf.goalSelector, MeleeAttackGoal.class::isInstance);
        removeGoals(wolf.targetSelector, OwnerHurtByTargetGoal.class::isInstance);
        removeGoals(wolf.targetSelector, OwnerHurtTargetGoal.class::isInstance);
        removeGoals(wolf.targetSelector, AggressivePersonality.AggressiveMonsterTargetGoal.class::isInstance);

        if (logic != null && logic.canAttack()) {
            if (!hasGoal(wolf.goalSelector, MeleeAttackGoal.class)) {
                wolf.goalSelector.addGoal(4, new MeleeAttackGoal(wolf, 1.2D, true));
            }
            if (!hasGoal(wolf.targetSelector, OwnerHurtByTargetGoal.class)) {
                wolf.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(wolf));
            }
            if (!hasGoal(wolf.targetSelector, OwnerHurtTargetGoal.class)) {
                wolf.targetSelector.addGoal(2, new OwnerHurtTargetGoal(wolf));
            }
        } else {
            wolf.setTarget(null);
        }
    }

    private static boolean hasGoal(GoalSelector selector, Class<? extends Goal> goalClass) {
        return selector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .anyMatch(goalClass::isInstance);
    }

    private static void removeGoals(GoalSelector selector, Predicate<Goal> predicate) {
        selector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(predicate)
                .toList()
                .forEach(selector::removeGoal);
    }

    private static void applyPersonalityVisuals(Wolf wolf) {
        if (wolf.level().isClientSide) {
            return;
        }

        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        if (data.getAppearanceStyle() < 0 || data.getAppearanceStyle() >= APPEARANCE_STYLE_COUNT) {
            data.setAppearanceStyle(chooseAppearanceStyle(wolf, data.getPersonality(), -1));
        }

        ResourceKey<WolfVariant> variantKey = getPersonalityVariantKey(data.getPersonality(), data.getAppearanceStyle());
        if (variantKey == null) {
            return;
        }

        Registry<WolfVariant> registry = wolf.registryAccess().registryOrThrow(Registries.WOLF_VARIANT);
        registry.getHolder(variantKey).ifPresent(wolf::setVariant);
    }

    private static ResourceKey<WolfVariant> getPersonalityVariantKey(PetPersonality personality, int appearanceStyle) {
        if (personality == PetPersonality.NONE) {
            return null;
        }

        int clampedStyle = Math.max(0, Math.min(APPEARANCE_STYLE_COUNT - 1, appearanceStyle));
        String variantName = clampedStyle == 0
                ? personality.getSerializedName()
                : personality.getSerializedName() + "_" + clampedStyle;

        return ResourceKey.create(
                Registries.WOLF_VARIANT,
                ResourceLocation.fromNamespaceAndPath(DogDog.MODID, variantName)
        );
    }

    private static void applyAwakenedBlessing(Wolf wolf, Personality logic) {
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        boolean awakened = data.isAwakened();

        syncAwakenedModifier(wolf.getAttribute(Attributes.MAX_HEALTH), AWAKENED_HEALTH_MODIFIER_ID, AWAKENED_HEALTH_BONUS, awakened);
        syncAwakenedModifier(wolf.getAttribute(Attributes.MOVEMENT_SPEED), AWAKENED_SPEED_MODIFIER_ID, AWAKENED_SPEED_BONUS, awakened);
        syncAwakenedModifier(
                wolf.getAttribute(Attributes.ATTACK_DAMAGE),
                AWAKENED_ATTACK_MODIFIER_ID,
                AWAKENED_ATTACK_BONUS,
                awakened && logic != null && logic.canAttack()
        );
    }

    private static void syncAwakenedModifier(AttributeInstance instance, ResourceLocation modifierId, double amount, boolean enabled) {
        if (instance == null) {
            return;
        }
        if (enabled) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(modifierId, amount, AttributeModifier.Operation.ADD_VALUE));
        } else {
            instance.removeModifier(modifierId);
        }
    }

    private static int chooseAppearanceStyle(Wolf wolf, PetPersonality personality, int excludedStyle) {
        int[] weights = getAppearanceWeights(personality);
        int totalWeight = 0;

        for (int style = 0; style < APPEARANCE_STYLE_COUNT; style++) {
            if (style == excludedStyle) {
                continue;
            }
            totalWeight += weights[Math.min(style, weights.length - 1)];
        }

        if (totalWeight <= 0) {
            if (excludedStyle >= 0 && APPEARANCE_STYLE_COUNT > 1) {
                return (excludedStyle + 1) % APPEARANCE_STYLE_COUNT;
            }
            return 0;
        }

        int roll = wolf.getRandom().nextInt(totalWeight);
        for (int style = 0; style < APPEARANCE_STYLE_COUNT; style++) {
            if (style == excludedStyle) {
                continue;
            }

            roll -= weights[Math.min(style, weights.length - 1)];
            if (roll < 0) {
                return style;
            }
        }

        return 0;
    }

    private static int[] getAppearanceWeights(PetPersonality personality) {
        return switch (personality) {
            case AGGRESSIVE -> new int[]{24, 20, 18, 14, 12, 9, 5};
            case TANK -> new int[]{26, 18, 16, 12, 16, 8, 4};
            case ASSASSIN -> new int[]{12, 18, 14, 16, 20, 10, 10};
            case VAMPIRE -> new int[]{10, 16, 12, 14, 20, 14, 12};
            case SENTRY -> new int[]{20, 22, 18, 14, 12, 8, 6};
            case HEALER -> new int[]{14, 24, 20, 18, 18, 7, 3};
            case COWARD -> new int[]{24, 20, 18, 16, 10, 8, 4};
            case AMPHIBIAN -> new int[]{16, 18, 22, 14, 16, 10, 4};
            case HUSKY -> new int[]{18, 22, 16, 18, 14, 8, 4};
            case GLUTTON -> new int[]{22, 20, 18, 14, 12, 10, 4};
            case DIGGER -> new int[]{20, 24, 18, 16, 10, 12, 4};
            case LAZY -> new int[]{24, 18, 18, 14, 12, 8, 3};
            case CLINGY -> new int[]{20, 18, 22, 16, 14, 8, 4};
            case NONE -> new int[]{30, 22, 16, 12, 10, 6, 2};
        };
    }

    private static void rerollAppearanceStyle(Wolf wolf, PetData data) {
        data.setAppearanceStyle(chooseAppearanceStyle(wolf, data.getPersonality(), Math.max(0, data.getAppearanceStyle())));
    }

    public static boolean isStone(Wolf wolf) {
        if (wolf.hasData(ModAttachmentTypes.PET_DATA) && wolf.getData(ModAttachmentTypes.PET_DATA).isPetrified()) {
            return true;
        }
        return wolf.hasCustomName() && wolf.getCustomName().getString().contains("[石化]");
    }

    public static String getDogName(Wolf wolf) {
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        ensurePersonalityAssigned(wolf);
        return data.hasPlayerGivenName() ? data.getPlayerGivenName() : data.getGeneratedName();
    }

    private static String sanitizePlayerGivenName(Component component) {
        String raw = component == null ? "" : component.getString().replace('\n', ' ').replace('\r', ' ').trim();
        if (raw.isBlank()) {
            return "";
        }
        return raw.length() > 16 ? raw.substring(0, 16).trim() : raw;
    }

    public static void clearRealBlood(Wolf wolf) {
        if (wolf.level().isClientSide) return;
        ServerLevel sl = (ServerLevel) wolf.level();
        BlockPos center = wolf.blockPosition();

        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = sl.getBlockState(pos);

                    if (state.is(Blocks.RED_CONCRETE_POWDER) ||
                            state.is(Blocks.RED_CONCRETE) ||
                            state.is(Blocks.NETHER_WART_BLOCK) ||
                            state.is(Blocks.RED_SHULKER_BOX)) {

                        sl.removeBlockEntity(pos);

                        BlockState replacement = Blocks.DIRT.defaultBlockState();
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            BlockState neighbor = sl.getBlockState(pos.relative(dir));
                            if (neighbor.isSolidRender(sl, pos.relative(dir)) &&
                                    !neighbor.is(Blocks.RED_CONCRETE_POWDER) &&
                                    !neighbor.is(Blocks.RED_CONCRETE) &&
                                    !neighbor.is(Blocks.NETHER_WART_BLOCK) &&
                                    !neighbor.is(Blocks.RED_SHULKER_BOX)) {
                                replacement = neighbor;
                                break;
                            }
                        }
                        sl.setBlock(pos, replacement, 3);

                    } else if (state.is(Blocks.REDSTONE_WIRE)) {
                        sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    public static void spawnBloodEffect(Wolf wolf) {
        ServerLevel sl = (ServerLevel) wolf.level();
        BlockPos center = wolf.blockPosition();
        RandomSource random = wolf.getRandom();
        BlockState[] bloodBlocks = new BlockState[]{
                Blocks.RED_CONCRETE_POWDER.defaultBlockState(),
                Blocks.RED_CONCRETE.defaultBlockState(),
                Blocks.NETHER_WART_BLOCK.defaultBlockState(),
                Blocks.RED_SHULKER_BOX.defaultBlockState()
        };

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= 3.5 && random.nextFloat() < (1.0f - distance / 4.5f)) {
                    for (int y = -1; y <= 0; y++) {
                        BlockPos groundPos = center.offset(x, y, z);
                        BlockState groundState = sl.getBlockState(groundPos);
                        BlockPos posAbove = groundPos.above();
                        BlockState stateAbove = sl.getBlockState(posAbove);

                        if (groundState.isSolidRender(sl, groundPos) && !stateAbove.isSolidRender(sl, posAbove)) {
                            if (groundState.getDestroySpeed(sl, groundPos) >= 0) {
                                if (random.nextFloat() < 0.7f) {
                                    sl.setBlockAndUpdate(groundPos, bloodBlocks[random.nextInt(bloodBlocks.length)]);
                                } else if (stateAbove.canBeReplaced() || stateAbove.isAir()) {
                                    sl.setBlockAndUpdate(posAbove, Blocks.REDSTONE_WIRE.defaultBlockState());
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    public static void updateHealthName(Wolf wolf) {
        if (!wolf.isTame() || wolf.isNoAi()) return;
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);

        if (data.isCritical() || isStone(wolf)) return;
        if (wolf.tickCount % 20 != 0 && wolf.getLastHurtByMobTimestamp() != wolf.tickCount) return;

        if (data.getPersonality() != PetPersonality.NONE) {
            String dogName = getDogName(wolf);
            String awakenedMark = data.isAwakened() ? " ✦" : "";
            String homeMark = data.isHomeMode() ? " ⌂" : "";
            MutableComponent customName = Component.literal(dogName + awakenedMark + homeMark);
            wolf.setCustomName(customName);
            wolf.setCustomNameVisible(true);
        }
    }

    public static void showDogProfile(Player player, Wolf wolf, PetData data) {
        String name = getDogName(wolf);
        String personality = Component.translatable("personality.dog_dog." + data.getPersonality().getSerializedName()).getString();
        String affinity = Integer.toString(data.getAffinity());
        String bondStage = Component.translatable(DogBonding.getBondStageKey(data)).getString();
        String mood = Integer.toString(data.getMood());
        String strain = Integer.toString(data.getStrain());
        String health = String.format("%.0f/%.0f", wolf.getHealth(), wolf.getMaxHealth());
        String awakened = Component.translatable(data.isAwakened()
                ? "message.dog_dog.panel_awakened_yes"
                : "message.dog_dog.panel_awakened_no").getString();
        String mode = Component.translatable(data.isHomeMode()
                ? "message.dog_dog.panel_mode_home"
                : "message.dog_dog.panel_mode_follow").getString();

        player.sendSystemMessage(Component.translatable("message.dog_dog.panel_title"));
        player.sendSystemMessage(Component.translatable("message.dog_dog.panel_name", name));
        player.sendSystemMessage(Component.translatable("message.dog_dog.panel_personality_value", personality));
        player.sendSystemMessage(Component.translatable("message.dog_dog.panel_trust_value", affinity));
        player.sendSystemMessage(Component.translatable("message.dog_dog.panel_bond_value", bondStage));
        player.sendSystemMessage(Component.translatable("message.dog_dog.panel_mood_value", mood));
        player.sendSystemMessage(Component.translatable("message.dog_dog.panel_strain_value", strain));
        player.sendSystemMessage(Component.translatable("message.dog_dog.panel_health_value", health));
        player.sendSystemMessage(Component.translatable("message.dog_dog.panel_awakened_value", awakened));
        player.sendSystemMessage(Component.translatable("message.dog_dog.panel_mode_value", mode));
    }

    public static void checkAndTriggerMaxAffinity(Wolf wolf, Player player, int oldAffinity) {
        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        if (oldAffinity < 100 && data.getAffinity() >= 100) {
            if (player instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetTitleTextPacket(
                        Component.literal("灵魂羁绊").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                ));
                sp.connection.send(new ClientboundSetSubtitleTextPacket(
                        Component.literal(DogDialogs.getAffinityMax(wolf.getRandom())).withStyle(ChatFormatting.LIGHT_PURPLE)
                ));
                sp.level().playSound(null, wolf.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0f, 1.0f);
            }
        }
    }

    public static boolean enterCriticalRescueState(Wolf wolf) {
        if (wolf.level().isClientSide || !wolf.hasData(ModAttachmentTypes.PET_DATA)) {
            return false;
        }

        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        if (data.isCritical() || data.isPetrified()) {
            return false;
        }

        data.setCritical(true);
        data.setPetrified(false);
        DogBonding.heavyOverdraft(data, 20, 40);

        wolf.setHealth(Math.max(0.5F, Math.min(2.0F, wolf.getMaxHealth() * 0.1F)));
        wolf.setTarget(null);
        wolf.getNavigation().stop();
        wolf.setInvulnerable(true);
        wolf.setSilent(false);
        wolf.setNoAi(false);
        wolf.setOrderedToSit(true);

        spawnBloodEffect(wolf);

        if (wolf.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLASH, wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, wolf.getX(), wolf.getY() + 0.6, wolf.getZ(), 30, 0.25, 0.35, 0.25, 0.02);
            serverLevel.playSound(null, wolf.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.7f, 0.9f);
        }

        if (wolf.getOwner() instanceof ServerPlayer sp) {
            String dogName = getDogName(wolf);
            sp.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal(dogName + " 倒下了").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
            ));
            sp.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("快用金苹果把它带回来").withStyle(ChatFormatting.GOLD)
            ));
            sp.sendSystemMessage(Component.literal(dogName + " 还留着一口气，快去救它。").withStyle(ChatFormatting.YELLOW));
        }

        return true;
    }

    public static void grantAdvancement(ServerPlayer player, String advancementPath) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DogDog.MODID, advancementPath);
        AdvancementHolder advancement = player.server.getAdvancements().get(id);
        if (advancement != null) {
            for (String criterion : advancement.value().criteria().keySet()) {
                player.getAdvancements().award(advancement, criterion);
            }
        }
    }

    public static void handleCriticalState(Wolf wolf, PetData data, ServerLevel sl) {
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
            wolf.setCustomName(Component.literal(getDogName(wolf) + " · 救助倒计时 " + seconds + "s" + whisper));
            wolf.setCustomNameVisible(true);

            if (wolf.tickCount % 3 == 0) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                        wolf.getX(), wolf.getY() + 0.5, wolf.getZ(), 5, 0.2, 0.1, 0.2, 0.05);
            }
        } else if (ticks == 1200) {
            data.setCritical(false);
            data.setPetrified(true);
            wolf.setNoAi(true);
            wolf.setInvulnerable(true);
            wolf.setSilent(true);

            clearRealBlood(wolf);

            String stoneSub = DogDialogs.getStone(wolf.getRandom());
            wolf.setCustomName(Component.literal(getDogName(wolf) + " [石化]"));
            wolf.setCustomNameVisible(true);

            if (wolf.getOwner() instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("永远的守护")));
                sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(stoneSub)));
                sp.sendSystemMessage(Component.literal("[悲剧] " + getDogName(wolf) + " 化作了一座沉默的石像。 " + stoneSub));
            }
            wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.5f, 0.5f);
        } else {
            wolf.setOrderedToSit(true);
            wolf.setNoAi(true);
            wolf.setInvulnerable(true);
            wolf.setSilent(true);
        }
    }

    public static void handleMovementMode(Wolf wolf, PetData data) {
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
                if (distSqr < 4.0 && data.getPersonality() != PetPersonality.DIGGER) {
                    Vec3 away = wolf.position().subtract(owner.position()).normalize().scale(0.05);
                    wolf.setDeltaMovement(wolf.getDeltaMovement().add(away));
                }
            }
        }
    }

    public static boolean handleInteraction(Wolf wolf, Player player, ItemStack stack, PetData data) {
        if (data.isCritical() || isStone(wolf)) {
            if (stack.is(Items.GOLDEN_APPLE)) {
                data.setCritical(false);
                data.setPetrified(false);
                wolf.setInvulnerable(false);
                wolf.setSilent(false);
                wolf.setNoAi(false);
                wolf.setOrderedToSit(false);
                wolf.setCustomName(null);
                data.setAffinity(20);
                data.setMood(45);
                data.setStrain(10);
                wolf.heal(10.0f);
                if (!player.getAbilities().instabuild) stack.shrink(1);

                clearRealBlood(wolf);
                applyCurrentPersonality(wolf, false);

                if (player instanceof ServerPlayer sp) {
                    String recSub = DogDialogs.getRecover(wolf.getRandom());
                    sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("重获新生")));
                    sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(recSub)));
                }

                player.level().playSound(null, wolf.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.8f, 1.2f);
                return true;
            } else {
                player.displayClientMessage(Component.literal("只有金苹果才能把它从边缘拉回来。"), true);
                return true;
            }
        }

        if (stack.is(Items.NAME_TAG) && stack.has(DataComponents.CUSTOM_NAME) && wolf.isOwnedBy(player)) {
            String renamed = sanitizePlayerGivenName(stack.getHoverName());
            if (!renamed.isBlank()) {
                data.setPlayerGivenName(renamed);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.sendSystemMessage(Component.translatable("message.dog_dog.name_tag.renamed", renamed));
                updateHealthName(wolf);
                return true;
            }
        }

        if (stack.is(ModItems.GOLDEN_BONE.get())) {
            if (player.isShiftKeyDown()) {
                rerollAppearanceStyle(wolf, data);
                applyCurrentPersonality(wolf, false);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.sendSystemMessage(Component.translatable("message.dog_dog.golden_bone.appearance_rerolled"));
                return true;
            }

            if (data.isAwakened()) {
                player.displayClientMessage(Component.translatable("message.dog_dog.golden_bone.already_awakened"), true);
                return true;
            }

            if (data.getAffinity() < 100) {
                player.displayClientMessage(Component.translatable("message.dog_dog.golden_bone.need_max_affinity"), true);
                return true;
            }

            data.setAwakened(true);
            data.increaseMood(10);
            data.decreaseStrain(10);
            applyCurrentPersonality(wolf, true);
            wolf.setTarget(null);
            wolf.getNavigation().stop();
            wolf.setOrderedToSit(false);

            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.sendSystemMessage(Component.translatable("message.dog_dog.golden_bone.awakened"));
            player.level().playSound(null, wolf.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.1F);
            return true;
        }

        if (stack.is(Items.GOLDEN_APPLE)) {
            int oldAffinity = data.getAffinity();
            DogBonding.rewardMeal(data, 6, 12);
            player.displayClientMessage(Component.translatable("message.dog_dog.affinity_bond_food"), true);
            wolf.heal(wolf.getMaxHealth());
            if (!player.getAbilities().instabuild) stack.shrink(1);
            wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0F, 1.0F);
            checkAndTriggerMaxAffinity(wolf, player, oldAffinity);
            return true;
        }

        if (wolf.isFood(stack)) {
            int oldAffinity = data.getAffinity();
            DogBonding.rewardMeal(data, 1, 5);
            wolf.heal(4.0f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0F, 1.0F);
            checkAndTriggerMaxAffinity(wolf, player, oldAffinity);
            return true;
        }

        if (stack.isEmpty()) {
            if (player.isShiftKeyDown()) {
                boolean newMode = !data.isHomeMode();
                data.setHomeMode(newMode);
                if (newMode) data.setHomePos(wolf.blockPosition());
                wolf.setOrderedToSit(false);
                player.displayClientMessage(Component.literal(newMode ? "[住家模式]" : "[跟随模式]"), true);
                showDogProfile(player, wolf, data);
                return true;
            } else {
                int oldAffinity = data.getAffinity();
                boolean gainedAffinity = DogBonding.rewardPetting(wolf, data);
                if (gainedAffinity) {
                    checkAndTriggerMaxAffinity(wolf, player, oldAffinity);
                }
                wolf.heal(1.0f);
                ((ServerLevel) wolf.level()).sendParticles(ParticleTypes.HEART, wolf.getX(), wolf.getEyeY(), wolf.getZ(), 3, 0.2, 0.2, 0.2, 0);
                wolf.setOrderedToSit(!wolf.isOrderedToSit());
                showDogProfile(player, wolf, data);
                return true;
            }
        }

        return false;
    }
}
