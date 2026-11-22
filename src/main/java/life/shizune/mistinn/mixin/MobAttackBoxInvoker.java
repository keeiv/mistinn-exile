package life.shizune.mistinn.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's melee reach box is protected; a released windup measures its lunge with the same box. */
@Mixin(Mob.class)
public interface MobAttackBoxInvoker {
	@Invoker("getAttackBoundingBox")
	net.minecraft.world.phys.AABB mistinn$attackBox(double reach);
}
