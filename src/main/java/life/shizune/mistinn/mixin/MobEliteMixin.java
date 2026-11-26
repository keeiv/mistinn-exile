package life.shizune.mistinn.mixin;

import life.shizune.mistinn.elite.EliteService;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every creature's spawn passes through {@code Mob.finalizeSpawn} (each kind's own calls up to it),
 * the one place that still knows why it came into the world: those that came of themselves are
 * weighed for elites, at the tick's end, once the kind has finished with them.
 */
@Mixin(Mob.class)
public abstract class MobEliteMixin {
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void mistinn$elite(final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason reason,
		final @Nullable SpawnGroupData data, final CallbackInfoReturnable<SpawnGroupData> cir) {
		EliteService.spawned((Mob) (Object) this, reason);
	}
}
