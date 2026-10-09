package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.common.ai.HuskyGoal;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;

public class HuskyPersonality extends Personality {
    @Override public String getId() { return "husky"; }
    @Override public boolean canAttack() { return false; }

    @Override
    public void applyAttributes(Wolf wolf) {
        setAttr(wolf, Attributes.MAX_HEALTH, 26.0D);
        setAttr(wolf, Attributes.MOVEMENT_SPEED, 0.38D);
    }

    @Override
    public void applyAI(Wolf wolf) {
        addGoalIfAbsent(wolf.goalSelector, 2, new HuskyGoal(wolf));
    }
}
