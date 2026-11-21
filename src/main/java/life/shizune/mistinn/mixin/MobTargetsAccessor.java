package life.shizune.mistinn.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reaches a mob's target goals, so a practice creature can be left only its ears. */
@Mixin(Mob.class)
public interface MobTargetsAccessor {
	@Accessor("targetSelector")
	GoalSelector mistinn$targetSelector();
}
