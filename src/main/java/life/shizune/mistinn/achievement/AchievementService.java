package life.shizune.mistinn.achievement;

import io.netty.buffer.ByteBuf;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.Stat;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Reads and records a hunter's achievements. The record is saved, kept through death and sent
 * to its owner; the first time an achievement is earned it pays its salt and the hunter is told.
 */
public final class AchievementService {
	/** 打倒熄燈人: the first lighthouse keeper has fallen with this hunter near. */
	public static final String SNUFFER = "snuffer";
	/** 鹽商: salt carried at once. */
	public static final int RICH = 5000;
	/** 百兵: marks kept in the same record, one per weapon branch a creature was brought down with. */
	private static final String KILL = "kill:";

	private AchievementService() {
	}

	public static Achievements get(final Player player) {
		Achievements achievements = player.getAttached(MistinnAttachments.ACHIEVEMENTS);
		return achievements != null ? achievements : Achievements.EMPTY;
	}

	public static boolean has(final Player player, final String id) {
		return get(player).has(id);
	}

	public static boolean has(final Player player, final Achievement achievement) {
		return has(player, achievement.id);
	}

	/** Achievements earned, of those the guide lists. */
	public static int earned(final Player player) {
		int count = 0;
		for (Achievement achievement : Achievement.values()) {
			if (has(player, achievement)) {
				count++;
			}
		}
		return count;
	}

	/** Records an achievement (or a mark toward one); returns true the first time. */
	public static boolean grant(final ServerPlayer player, final String id) {
		Achievements achievements = get(player);
		if (achievements.has(id)) {
			return false;
		}
		player.setAttached(MistinnAttachments.ACHIEVEMENTS, achievements.with(id));
		Achievement achievement = Achievement.byId(id);
		if (achievement != null) {
			if (achievement.reward > 0) {
				SaltService.add(player, achievement.reward);
			}
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.ACHIEVEMENT, SoundSource.PLAYERS, 0.8F, 1.0F);
			if (ServerPlayNetworking.canSend(player, EarnedPayload.TYPE)) {
				ServerPlayNetworking.send(player, new EarnedPayload(id));
			}
		}
		return true;
	}

	public static boolean grant(final ServerPlayer player, final Achievement achievement) {
		return grant(player, achievement.id);
	}

	// ── What earns them ───────────────────────────────────────────

	/** A creature brought down by this hunter, with what was in their hand. */
	public static void killed(final ServerPlayer player, final boolean hostile) {
		if (!hostile) {
			return;
		}
		grant(player, Achievement.FIRST_KILL);
		WeaponStyle style = WeaponStyle.of(player.getMainHandItem());
		if (style == null) {
			return;
		}
		grant(player, KILL + style.id);
		for (WeaponStyle each : WeaponStyle.values()) {
			if (!has(player, KILL + each.id)) {
				return;
			}
		}
		grant(player, Achievement.SIX_BRANCHES);
	}

	/** After a skill node is learned. */
	public static void learned(final ServerPlayer player) {
		grant(player, Achievement.FIRST_SKILL);
		boolean all = true;
		for (Stat stat : Stat.values()) {
			int value = SkillService.stat(player, stat);
			if (value >= 5) {
				grant(player, Achievement.STAT_FIVE);
			}
			all &= value >= 1;
		}
		if (all) {
			grant(player, Achievement.ALL_STATS);
		}
	}

	/** After the hunter's salt changes. */
	public static void salt(final ServerPlayer player) {
		if (SaltService.get(player) >= RICH) {
			grant(player, Achievement.RICH);
		}
	}

	/** Test hook: forgets every achievement. */
	public static void clear(final ServerPlayer player) {
		player.removeAttached(MistinnAttachments.ACHIEVEMENTS);
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(EarnedPayload.TYPE, EarnedPayload.CODEC);
	}

	/** Tells a hunter an achievement was just earned, so the notice slides in. */
	public record EarnedPayload(String id) implements CustomPacketPayload {
		public static final Type<EarnedPayload> TYPE = new Type<>(Mistinn.id("achievement_earned"));
		public static final StreamCodec<ByteBuf, EarnedPayload> CODEC = ByteBufCodecs.stringUtf8(64).map(EarnedPayload::new, EarnedPayload::id);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
