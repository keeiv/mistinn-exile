package life.shizune.mistinn.client.music;

import life.shizune.mistinn.keeper.Keeper;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.Music;
import org.jspecify.annotations.Nullable;

/**
 * 燈守戰的音樂: while the hunter fights a keeper, the game's music is the fight's. It starts in
 * the lamp room, or anywhere near once the keeper has been wounded. When the lights go out,
 * a short bell cue leads into the dark half. Leaving the fight stops its music.
 */
public final class KeeperMusic {
	/** Both tracks loop with no pause between plays, and cut in over whatever was playing. */
	public static final Music FIGHT = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_KEEPER), 0, 0, true);
	public static final Music DARK = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_KEEPER_DARK), 0, 0, true);
	public static final Music TURN = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_KEEPER_TURN), 0, 0, true);

	/** Close enough to be in the keeper's lamp room: across it, and on its floor. */
	private static final double ROOM = 14.0;
	private static final double ROOM_HEIGHT = 6.0;
	/** Once the keeper is wounded, the fight is on this far out (its health bar's range). */
	private static final double FIGHT_RANGE = 32.0;

	/** Let the bell ring out before the dark track enters. */
	private static final int TURN_TICKS = 24;
	private static int keeperId = -1;
	private static boolean wasDark;
	private static int turnTicks;

	private KeeperMusic() {
	}

	/** The music for the fight in progress, or null when there is none. */
	public static @Nullable Music current() {
		Keeper keeper = nearbyFight(Minecraft.getInstance());
		if (keeper == null) {
			return null;
		}
		if (!isDark(keeper)) {
			return FIGHT;
		}
		return keeper.getId() == keeperId && (!wasDark || turnTicks > 0) ? TURN : DARK;
	}

	private static @Nullable Keeper nearbyFight(final Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !player.isAlive()) {
			return null;
		}
		Keeper keeper = null;
		double best = Double.MAX_VALUE;
		for (Keeper candidate : mc.level.getEntitiesOfClass(Keeper.class, player.getBoundingBox().inflate(FIGHT_RANGE))) {
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
		return keeper;
	}

	private static boolean isDark(final Keeper keeper) {
		return keeper.getHealth() <= keeper.getMaxHealth() / 2.0F;
	}

	/** Track the change of phase only for the keeper the listener was already fighting. */
	public static void tick(final Minecraft mc) {
		Keeper keeper = nearbyFight(mc);
		if (keeper == null) {
			if (keeperId != -1) {
				mc.getMusicManager().stopPlaying(FIGHT);
				mc.getMusicManager().stopPlaying(TURN);
				mc.getMusicManager().stopPlaying(DARK);
				keeperId = -1;
				turnTicks = 0;
			}
			return;
		}
		boolean dark = isDark(keeper);
		if (!dark || keeperId != keeper.getId()) {
			turnTicks = 0;
		} else if (!wasDark) {
			turnTicks = TURN_TICKS;
		} else if (turnTicks > 0) {
			turnTicks--;
		}
		keeperId = keeper.getId();
		wasDark = dark;
	}
}
