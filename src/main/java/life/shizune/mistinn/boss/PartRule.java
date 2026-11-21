package life.shizune.mistinn.boss;

import life.shizune.mistinn.body.BodyService;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Which part of a hunter a blow lands on. */
@FunctionalInterface
public interface PartRule {
	BodyService.Hit pick(Mob boss, Player player, RandomSource random);

	static PartRule always(final BodyService.Hit hit) {
		return (boss, player, random) -> hit;
	}
}
