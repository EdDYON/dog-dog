package com.EdDYON.dogdog.common.attachment.personality;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.function.Predicate;

public abstract class Personality {

    public abstract String getId();

    public boolean canAttack() {
        return true;
    }

    public void applyAttributes(Wolf wolf) {
    }

    public void applyAI(Wolf wolf) {
    }

    public void onTick(Wolf wolf) {
    }

    public void onDamageDealt(LivingIncomingDamageEvent event, Wolf wolf) {
    }

    public void onOwnerDamaged(LivingIncomingDamageEvent event, Wolf wolf, Player player) {
    }

    public void onWolfDamaged(LivingIncomingDamageEvent event, Wolf wolf) {
    }

    public void onWolfDeath(LivingDeathEvent event, Wolf wolf) {
    }

    protected void setAttr(Wolf wolf, net.minecraft.core.Holder<Attribute> attr, double value) {
        var inst = wolf.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    protected boolean hasGoal(GoalSelector selector, Class<? extends Goal> goalClass) {
        return selector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .anyMatch(goalClass::isInstance);
    }

    protected void addGoalIfAbsent(GoalSelector selector, int priority, Goal goal) {
        if (!hasGoal(selector, goal.getClass())) {
            selector.addGoal(priority, goal);
        }
    }

    protected void removeGoals(GoalSelector selector, Predicate<Goal> predicate) {
        selector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(predicate)
                .toList()
                .forEach(selector::removeGoal);
    }
}
