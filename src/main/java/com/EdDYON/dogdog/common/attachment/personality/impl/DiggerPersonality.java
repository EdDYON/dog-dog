package com.EdDYON.dogdog.common.attachment.personality.impl;

import com.EdDYON.dogdog.common.ai.DiggerGoal;
import com.EdDYON.dogdog.common.attachment.personality.Personality;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;

public class DiggerPersonality extends Personality {

    @Override
    public String getId() {
        return "digger";
    }

    @Override
    public boolean canAttack() {
        return false;
    }

    @Override
    public void applyAttributes(Wolf wolf) {
        super.applyAttributes(wolf);
        setAttr(wolf, Attributes.MAX_HEALTH, 40.0D);
        setAttr(wolf, Attributes.ATTACK_DAMAGE, 4.0D);
        setAttr(wolf, Attributes.MOVEMENT_SPEED, 0.3D);
        if (wolf.getHealth() > wolf.getMaxHealth()) {
            wolf.setHealth(wolf.getMaxHealth());
        }
    }

    @Override
    public void applyAI(Wolf wolf) {
        addGoalIfAbsent(wolf.goalSelector, 2, new DiggerGoal(wolf));
    }
}
