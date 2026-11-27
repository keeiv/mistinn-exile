package life.shizune.mistinn.enemy;

import java.util.Map;
import java.util.WeakHashMap;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.hardship.Ferocity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * 呼喚: a creature that sets on a hunter cries out, and every creature near enough that has nothing
 * to chase hears it and comes to look, as it would at any noise: what it does on finding the
 * hunter is its own business, and walls and a quiet step count as they always do. A creature cries
 * at most once in {@link #QUIET} ticks, and one that came to a cry does not cry in turn for as long,
 * so a cry draws in the creatures about one fight and not the whole night.
 */
public final class CryService {
	/** How far a cry carries, in blocks (a Rust Anchor world hears further). */
	public static final double RADIUS = 16.0;
	/** Ticks a creature keeps quiet after crying, or after coming to a cry. */
	public static final int QUIET = 600;
	/** Cries so far (for tests). */
	public static int cries;

	private static final Map<Mob, Long> LAST = new WeakHashMap<>();

	private CryService() {
	}

	/** A creature has just set on this target: when it is a hunter, it cries out. */
	public static void spotted(final Mob mob, final LivingEntity target) {
		if (!(target instanceof ServerPlayer hunter) || !BodyService.applies(hunter) || hunter.isSpectator()) {
			return;
		}
		Long last = LAST.get(mob);
		if (last != null && mob.level().getGameTime() - last < QUIET) {
			return;
		}
		cry(mob);
	}

	/** The creature cries out now (a cry for help, breaking off, does not wait for its quiet to pass). */
	public static void cry(final Mob mob) {
		if (!(mob.level() instanceof ServerLevel level) || mob.isNoAi() || !NoiseService.listens(mob) || StaggerService.staggered(mob)
			|| ExecutionService.holdsMob(mob)) {
			return;
		}
		long now = level.getGameTime();
		LAST.put(mob, now);
		mob.playAmbientSound();
		NoiseService.hear(level, mob.position(), RADIUS * Ferocity.of(level).hearing(), heard -> {
			// those that come to a cry keep quiet when they find the hunter
			LAST.put(heard, now);
		});
		cries++;
	}
}
