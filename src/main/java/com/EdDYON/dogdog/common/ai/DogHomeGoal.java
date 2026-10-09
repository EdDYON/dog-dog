package com.EdDYON.dogdog.common.entity.ai;

import com.EdDYON.dogdog.common.attachment.PetData;
import com.EdDYON.dogdog.common.registry.ModAttachmentTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DogHomeGoal extends Goal {
    private final Wolf wolf;
    private double x, y, z;

    public DogHomeGoal(Wolf wolf) {
        this.wolf = wolf;
        // 优先级设置：占用移动(MOVE)和跳跃(JUMP)
        // 这会阻止原版的 FollowOwnerGoal (跟随主人) 运行
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!wolf.isTame()) return false;

        PetData data = wolf.getData(ModAttachmentTypes.PET_DATA);
        if (!data.isHomeMode()) return false;

        if (wolf.isOrderedToSit()) return false;

        BlockPos home = data.getHomePos();
        if (wolf.blockPosition().distSqr(home) > 1024) {
            this.x = home.getX();
            this.y = home.getY();
            this.z = home.getZ();
            return true;
        }


        if (wolf.getRandom().nextInt(100) != 0) return false;


        Vec3 target = DefaultRandomPos.getPosTowards(wolf, 15, 7, Vec3.atBottomCenterOf(home), 1.57);
        if (target == null) return false;

        if (new BlockPos((int)target.x, (int)target.y, (int)target.z).distSqr(home) > 1024) {
            return false;
        }

        this.x = target.x;
        this.y = target.y;
        this.z = target.z;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !wolf.getNavigation().isDone() && wolf.getData(ModAttachmentTypes.PET_DATA).isHomeMode();
    }

    @Override
    public void start() {
        wolf.getNavigation().moveTo(this.x, this.y, this.z, 0.5);
    }
}