package life.shizune.mistinn.client.music;

import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.Music;
import org.jspecify.annotations.Nullable;

/**
 * 燈守戰的音樂: while the hunter fights a keeper, the game's music is the fight's. It starts in
 * the lamp room, or anywhere near once the keeper has been wounded; below half health (the dark
 * half) it changes to the more frantic track. When the fight is over, by either side's fall or
 * by leaving, the track stops at once instead of playing out.
 */
public final class KeeperMusic {
	/** Both tracks loop with no pause between plays, and cut in over whatever was playing. */
	public static final Music FIGHT = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_KEEPER), 0, 0, true);
	public static final Music DARK = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_KEEPER_DARK), 0, 0, true);

	/** Close enough to be in the keeper's lamp room: across it, and on its floor. */
	private static final double ROOM = 14.0;
	private static final double ROOM_HEIGHT = 6.0;
	/** Once the keeper is wounded, the fight is on this far out (its health bar's range). */
	private static final double FIGHT_RANGE = 32.0;

	private static @Nullable Music playing;

	private KeeperMusic() {
	}

	/** The music for the fight in progress, or null when there is none. */
	public static @Nullable Music current() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !player.isAlive()) {
			return null;
		}
		Snuffer keeper = null;
		double best = Double.MAX_VALUE;
		for (Snuffer candidate : mc.level.getEntitiesOfClass(Snuffer.class, player.getBoundingBox().inflate(FIGHT_RANGE))) {
			double distance = candidate.distanceToSqr(player);
			if (candidate.isAlive() && distance < best) {
				keeper = candidate;
				best = distance;
			}
		}
		if (keeper == null) {
			return null;
		}
		double dx = keeper.getX() - player.getX();
		double dz = keeper.getZ() - player.getZ();
		boolean inRoom = dx * dx + dz * dz <= ROOM * ROOM && Math.abs(keeper.getY() - player.getY()) <= ROOM_HEIGHT;
		boolean wounded = keeper.getHealth() < keeper.getMaxHealth();
		if (!inRoom && !wounded) {
			return null;
		}
		return keeper.getHealth() <= keeper.getMaxHealth() / 2.0F ? DARK : FIGHT;
	}

	/** Every client tick: when the fight ends, its track stops rather than playing to its end. */
	public static void tick(final Minecraft mc) {
		Music now = current();
		if (playing != null && now == null) {
			mc.getMusicManager().stopPlaying(playing);
		}
		playing = now;
	}
}
