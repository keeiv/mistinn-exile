package life.shizune.mistinn.client.music;

import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.terra.CrystalElder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.Music;
import org.jspecify.annotations.Nullable;

/**
 * 晶冠之戰: while a hunter fights a crown elder, the game's music is the fight's: once the elder
 * has come at them (stooping, or fighting on the ground, close by) and for half a minute after, or
 * anywhere near once it is wounded. An elder only circling overhead does not start it.
 */
public final class ElderMusic {
	/** The fight's track: loops with no pause, and cuts in over the highland's music. */
	public static final Music FIGHT = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_ELDER), 0, 0, true);
	/** Close enough that the fight is on, and how far out it stays on once the elder is wounded. */
	private static final double CLOSE = 40.0;
	private static final double FIGHT_RANGE = 72.0;
	/** How long the fight goes on in the music after the elder last came at the hunter. */
	private static final long REMEMBERED = 600L;

	/** When an elder last came at the hunter (long ago, to begin with). */
	private static long cameAt = -REMEMBERED * 4;

	private ElderMusic() {
	}

	/** The fight's music, or null when there is no fight with an elder. */
	public static @Nullable Music current() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !player.isAlive() || player.isSpectator()) {
			return null;
		}
		for (CrystalElder elder : mc.level.getEntitiesOfClass(CrystalElder.class, player.getBoundingBox().inflate(FIGHT_RANGE))) {
			if (!elder.isAlive()) {
				continue;
			}
			long now = mc.level.getGameTime();
			if (cameAt > now) {
				// another world's clock
				cameAt = -REMEMBERED * 4;
			}
			boolean coming = elder.swoop(1.0F) > 0.5F || elder.move() != CrystalElder.Move.NONE;
			if (coming && elder.distanceTo(player) <= CLOSE) {
				cameAt = now;
			}
			if (elder.getHealth() < elder.getMaxHealth() || now - cameAt < REMEMBERED) {
				return FIGHT;
			}
		}
		return null;
	}
}
