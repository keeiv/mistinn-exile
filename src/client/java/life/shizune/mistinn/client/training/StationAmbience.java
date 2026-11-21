package life.shizune.mistinn.client.training;

import java.util.List;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.training.TrainingLesson;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The sound of a station at work, heard near where it comes from: the forge's hammer, the
 * kitchen, barrels and chains on the quay, water against the boats, blades in the drill yard,
 * static in the radio room, wind in the bell tower. Played on this client only, and silent from
 * the moment the drop begins, so the quiet after it is felt.
 */
public final class StationAmbience {
	/** Where a sound comes from, how near the hunter must be, and how often it comes (a burst of hits a few ticks apart). */
	private record Source(double x, double y, double z, double reach, SoundEvent sound, float volume, float pitchLow, float pitchHigh,
		int every, int spread, int burst) {
	}

	private static final List<Source> SOURCES = List.of(
		new Source(-52.5, 78.0, -46.5, 28.0, SoundEvents.ANVIL_USE, 0.3F, 0.9F, 1.15F, 50, 40, 3),
		new Source(-56.5, 78.0, -49.5, 18.0, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, 0.9F, 0.9F, 1.1F, 30, 40, 1),
		new Source(-42.5, 78.0, -22.5, 20.0, SoundEvents.SMOKER_SMOKE, 0.8F, 0.9F, 1.1F, 40, 60, 1),
		new Source(-38.5, 78.0, -21.5, 22.0, SoundEvents.WOOD_HIT, 0.5F, 1.1F, 1.4F, 60, 70, 2),
		new Source(12.5, 66.0, 18.5, 26.0, SoundEvents.BARREL_CLOSE, 0.6F, 0.8F, 1.0F, 70, 80, 1),
		new Source(20.5, 69.0, 14.5, 26.0, SoundEvents.CHAIN_PLACE, 0.7F, 0.7F, 0.9F, 90, 100, 2),
		new Source(25.5, 62.0, 12.5, 24.0, SoundEvents.BOAT_PADDLE_WATER, 0.5F, 0.7F, 0.9F, 60, 80, 1),
		new Source(-29.5, 63.0, 34.5, 24.0, SoundEvents.BOAT_PADDLE_WATER, 0.4F, 0.6F, 0.8F, 80, 90, 1),
		new Source(-21.5, 78.0, -55.5, 26.0, SoundEvents.PLAYER_ATTACK_SWEEP, 0.35F, 0.9F, 1.1F, 70, 90, 2),
		new Source(-17.5, 78.0, -85.5, 16.0, MistinnSounds.RADIO_STATIC, 0.35F, 0.9F, 1.1F, 80, 120, 1),
		new Source(20.5, 98.0, -99.5, 40.0, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.5F, 0.6F, 120, 160, 1));
	private static final int BURST_GAP = 7;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final long[] NEXT = new long[SOURCES.size()];
	private static final int[] LEFT = new int[SOURCES.size()];
	private static long clock;
	private static int played;

	private StationAmbience() {
	}

	/** Called every client tick with the hunter's lesson (null away from the station). */
	public static void tick(final Minecraft mc, final @Nullable TrainingLesson lesson, final boolean dropping) {
		clock++;
		if (mc.level == null || mc.player == null || lesson == null || dropping
			|| lesson == TrainingLesson.BOARD || lesson == TrainingLesson.DEPLOY) {
			return;
		}
		for (int i = 0; i < SOURCES.size(); i++) {
			Source source = SOURCES.get(i);
			if (clock < NEXT[i] || mc.player.distanceToSqr(source.x(), source.y(), source.z()) > source.reach() * source.reach()) {
				continue;
			}
			float pitch = source.pitchLow() + RANDOM.nextFloat() * (source.pitchHigh() - source.pitchLow());
			mc.level.playLocalSound(source.x(), source.y(), source.z(), source.sound(), SoundSource.AMBIENT, source.volume(), pitch, false);
			played++;
			if (LEFT[i] <= 0) {
				LEFT[i] = source.burst();
			}
			LEFT[i]--;
			NEXT[i] = clock + (LEFT[i] > 0 ? BURST_GAP : source.every() + RANDOM.nextInt(source.spread()));
		}
	}

	/** Test hook: how many sounds the station has made on this client. */
	public static int played() {
		return played;
	}
}
