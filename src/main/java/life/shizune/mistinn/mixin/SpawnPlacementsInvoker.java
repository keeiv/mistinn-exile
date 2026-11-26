package life.shizune.mistinn.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the mod say where its own creatures may spawn of themselves, as the game does for its own. */
@Mixin(SpawnPlacements.class)
public interface SpawnPlacementsInvoker {
	@Invoker("register")
	static <T extends Mob> void mistinn$register(final EntityType<T> type, final SpawnPlacementType placement, final Heightmap.Types heightmap,
		final SpawnPlacements.SpawnPredicate<T> predicate) {
		throw new AssertionError();
	}
}
