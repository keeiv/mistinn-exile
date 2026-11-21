package life.shizune.mistinn.achievement;

import life.shizune.mistinn.registry.MistinnAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Reads and records a hunter's achievements. The record is saved, kept through death and sent to its owner. */
public final class AchievementService {
	/** 打倒熄燈人: the first lighthouse keeper has fallen with this hunter near. */
	public static final String SNUFFER = "snuffer";

	private AchievementService() {
	}

	public static Achievements get(final Player player) {
		Achievements achievements = player.getAttached(MistinnAttachments.ACHIEVEMENTS);
		return achievements != null ? achievements : Achievements.EMPTY;
	}

	public static boolean has(final Player player, final String id) {
		return get(player).has(id);
	}

	/** Records an achievement; returns true the first time. */
	public static boolean grant(final ServerPlayer player, final String id) {
		Achievements achievements = get(player);
		if (achievements.has(id)) {
			return false;
		}
		player.setAttached(MistinnAttachments.ACHIEVEMENTS, achievements.with(id));
		return true;
	}

	/** Test hook: forgets every achievement. */
	public static void clear(final ServerPlayer player) {
		player.removeAttached(MistinnAttachments.ACHIEVEMENTS);
	}
}
