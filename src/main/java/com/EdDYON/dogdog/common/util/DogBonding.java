package com.EdDYON.dogdog.common.util;

import com.EdDYON.dogdog.common.attachment.PetData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

public final class DogBonding {
    private static final String LAST_PET_TICK_TAG = "DogBondLastPetTick";
    private static final long PET_COOLDOWN = 60L;

    private DogBonding() {
    }

    public static void tickMoodAndStrain(Wolf wolf, PetData data) {
        if (!(wolf.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() % 40L != 0L) {
            return;
        }

        Player owner = wolf.getOwner() instanceof Player player ? player : null;
        boolean nearOwner = owner != null && wolf.distanceToSqr(owner) <= 64.0D;
        boolean veryFarFromOwner = owner != null && wolf.distanceToSqr(owner) >= 400.0D;
        boolean cozy = wolf.level().getBlockState(wolf.blockPosition()).is(net.minecraft.tags.BlockTags.WOOL_CARPETS)
                || wolf.level().getBlockState(wolf.blockPosition().below()).is(net.minecraft.tags.BlockTags.WOOL)
                || wolf.level().getBlockState(wolf.blockPosition().below()).is(Blocks.MOSS_BLOCK);
        boolean recentlyHurt = wolf.getLastHurtByMobTimestamp() + 40 >= wolf.tickCount;

        if (nearOwner) {
            data.increaseMood(data.getAffinity() >= 60 ? 2 : 1);
        } else if (veryFarFromOwner && !data.isHomeMode()) {
            data.decreaseMood(2);
        }

        if (cozy) {
            data.increaseMood(1);
            data.decreaseStrain(2);
        } else if (data.getStrain() > 0) {
            data.decreaseStrain(1);
        }

        if (recentlyHurt) {
            data.decreaseMood(2);
        }
    }

    public static boolean rewardPetting(Wolf wolf, PetData data) {
        long now = wolf.level().getGameTime();
        long nextAllowed = wolf.getPersistentData().getLong(LAST_PET_TICK_TAG);
        if (now < nextAllowed) {
            data.increaseMood(1);
            return false;
        }

        wolf.getPersistentData().putLong(LAST_PET_TICK_TAG, now + PET_COOLDOWN);
        data.increaseMood(4);
        if (data.getAffinity() < 80) {
            data.increaseAffinity(1);
            return true;
        }
        return false;
    }

    public static void rewardMeal(PetData data, int affinityGain, int moodGain) {
        if (data.getAffinity() < 100) {
            data.increaseAffinity(affinityGain);
        }
        data.increaseMood(moodGain);
        data.decreaseStrain(Math.max(1, moodGain / 2));
    }

    public static void rewardSharedVictory(PetData data, int affinityGain, int moodGain) {
        if (data.getAffinity() < 100) {
            data.increaseAffinity(affinityGain);
        }
        data.increaseMood(moodGain);
    }

    public static boolean canUseUltimateBondSkill(PetData data, int minAffinity, int minMood, int maxStrain) {
        return data.getAffinity() >= minAffinity && data.getMood() >= minMood && data.getStrain() <= maxStrain;
    }

    public static void spendResolve(PetData data, int moodCost, int strainGain) {
        data.decreaseMood(moodCost);
        data.increaseStrain(strainGain);
    }

    public static void heavyOverdraft(PetData data, int moodFloor, int strainGain) {
        data.setMood(Math.min(data.getMood(), moodFloor));
        data.increaseStrain(strainGain);
    }

    public static String getBondStageKey(PetData data) {
        int affinity = data.getAffinity();
        if (affinity >= 100) return "message.dog_dog.panel_bond_soul";
        if (affinity >= 80) return "message.dog_dog.panel_bond_deep";
        if (affinity >= 60) return "message.dog_dog.panel_bond_sync";
        if (affinity >= 40) return "message.dog_dog.panel_bond_close";
        if (affinity >= 20) return "message.dog_dog.panel_bond_familiar";
        return "message.dog_dog.panel_bond_stranger";
    }
}
