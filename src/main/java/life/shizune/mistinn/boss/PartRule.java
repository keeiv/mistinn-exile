package life.shizune.mistinn.boss;

import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Which part of a hunter a blow lands on. */
@FunctionalInterface
public interface PartRule {
	/** Roughly where a rule aims, for the planner to weigh what landing is worth. */
	enum Aim {
		BODY,
		HEAD,
		LEGS
	}

	BodyService.Hit pick(Mob boss, Player player, RandomSource random);

	default Aim aim() {
		return Aim.BODY;
	}

	static PartRule always(final BodyService.Hit hit) {
		Aim aim = hit.legs() ? Aim.LEGS : hit.part() == BodyPart.HEAD ? Aim.HEAD : Aim.BODY;
		return new PartRule() {
			@Override
			public BodyService.Hit pick(final Mob boss, final Player player, final RandomSource random) {
				return hit;
			}

			@Override
			public Aim aim() {
				return aim;
			}
		};
	}
}
