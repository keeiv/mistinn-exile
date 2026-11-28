package life.shizune.mistinn.client.music;

import java.util.List;
import life.shizune.mistinn.expedition.Expedition;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.terra.Commissions;
import life.shizune.mistinn.terra.Settlements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.Music;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The expeditions' music: setting out (a hunter holding an expedition, at a woken homestead), out on one (in a ground),
 * and the way out closing (holding an exit for the count). Each cuts in over what was playing, and loops.
 */
public final class ExpeditionMusic {
	public static final Music MUSTER = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_MUSTER), 0, 0, true);
	public static final Music OUT = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_EXPEDITION), 0, 0, true);
	public static final Music EXTRACT = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_EXTRACT), 0, 0, true);
	/** How near a homestead's hub the setting out is heard. */
	private static final double HOME = 32.0;

	private ExpeditionMusic() {
	}

	/** The expedition's music now, or null when no part of one is going on. */
	public static @Nullable Music current() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !player.isAlive() || player.isSpectator()) {
			return null;
		}
		Expedition.Away away = player.getAttached(Expedition.AWAY);
		if (away != null) {
			return away.leaving() != 0L ? EXTRACT : OUT;
		}
		Commissions.Taken taken = player.getAttached(Commissions.TAKEN);
		if (taken == null || taken.commission().kind() != Commissions.Kind.EXPEDITION || mc.level.dimension() != Level.OVERWORLD) {
			return null;
		}
		List<Settlements.Settlement> homes = mc.level.getAttached(Settlements.VIEW);
		if (homes == null) {
			return null;
		}
		for (Settlements.Settlement home : homes) {
			if (home.lit() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(home.hub())) <= HOME * HOME) {
				return MUSTER;
			}
		}
		return null;
	}
}
