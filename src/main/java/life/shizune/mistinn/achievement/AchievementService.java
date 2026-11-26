package life.shizune.mistinn.achievement;

import java.util.List;
import java.util.function.Predicate;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.Stat;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Reads and records a hunter's achievements. They are the game's own advancements, on a tab of their own
 * ({@code data/mistinn/advancement/hunt}): the game keeps them with the hunter, through death and across sessions,
 * shows them on the advancements screen and announces each with its own toast. Nothing triggers them from data; the
 * mod grants each when it happens, and the first time one is earned it pays its salt.
 */
public final class AchievementService {
	/** 打倒熄燈人: the first lighthouse keeper has fallen with this hunter near. */
	public static final String SNUFFER = "snuffer";
	/** 打倒沉鐘: the second keeper has fallen with this hunter near. */
	public static final String SUNKEN_BELL = "sunken_bell";
	/** 打倒鏽輪: the third keeper has fallen with this hunter near. */
	public static final String RUST_WHEEL = "rust_wheel";
	/** 鏽錨使徒: the hidden one ({@link ApostleService}). */
	public static final String APOSTLE = "apostle";
	/** 霧海奪船: a pirate ship taken, its captain falling with the hunter near. */
	public static final String PIRATE_SHIP = "pirate_ship";
	/** 船隊剋星: the third pirate ship taken in a world, with the hunter near. */
	public static final String FLEET_BREAKER = "fleet_breaker";
	/** 守住海岸: a landing party fought off. */
	public static final String SHORE_HELD = "shore_held";
	/** 神射手: a pirate brought down by a gunshot. */
	public static final String SHARPSHOOTER = "sharpshooter";
	/** 精英獵手: an elite brought down. */
	public static final String ELITE_SLAIN = "elite_slain";
	/** 三重威脅: an elite of three affixes brought down. */
	public static final String ELITE_TRIPLE = "elite_triple";
	/** 熬過霧潮: there when a fog tide rose, and standing at dawn without having fallen in it. */
	public static final String TIDE = "tide";
	/** 賞金獵人: a posted bounty brought down. */
	public static final String BOUNTY = "bounty";
	/** 縫合天空: under a sky rift when it is sealed. */
	public static final String RIFT_SEALED = "rift_sealed";
	/** 碎晶: a crystal drake brought down. */
	public static final String DRAKE_SHATTERED = "drake_shattered";
	/** 家園: a settlement's hub woken. */
	public static final String HOMESTEAD = "homestead";
	/** The story ended: the salt sent up (錨), or thrown into the Mist Inn's fire (霧宿). */
	public static final String ENDING_ANCHOR = "ending_anchor";
	public static final String ENDING_INN = "ending_inn";
	/** 鹽商: salt carried at once. */
	public static final int RICH = 5000;
	/** 百兵: one mark per weapon branch a creature was brought down with; each is a criterion of that advancement. */
	private static final String KILL = "kill:";
	/** The folder of the tab, under the mod's namespace. */
	private static final String TAB = "hunt/";
	/** The tab's root: every hunter has it from the moment they arrive, so the tab is there before anything is earned. */
	public static final Identifier ROOT = Mistinn.id(TAB + "root");

	/**
	 * Set by the client: whether the local hunter has an achievement or mark, read from the advancements the server
	 * sent. Here, where no client class may be named, it answers no.
	 */
	public static Predicate<String> clientHas = id -> false;

	private AchievementService() {
	}

	/** Where an achievement or mark is kept: an advancement, and one of its criteria or all of them. */
	public record Target(Identifier advancement, @Nullable String criterion) {
	}

	/** Where {@code id} (an achievement's id, or a mark toward one) is kept. */
	public static Target target(final String id) {
		if (id.startsWith(KILL)) {
			return new Target(Mistinn.id(TAB + Achievement.SIX_BRANCHES.id), id.substring(KILL.length()));
		}
		return new Target(Mistinn.id(TAB + id), null);
	}

	private static @Nullable AdvancementHolder holder(final ServerPlayer player, final Identifier id) {
		return player.level().getServer().getAdvancements().get(id);
	}

	public static boolean has(final Player player, final String id) {
		if (!(player instanceof ServerPlayer hunter)) {
			return clientHas.test(id);
		}
		Target target = target(id);
		AdvancementHolder holder = holder(hunter, target.advancement());
		if (holder == null) {
			return false;
		}
		AdvancementProgress progress = hunter.getAdvancements().getOrStartProgress(holder);
		if (target.criterion() == null) {
			return progress.isDone();
		}
		CriterionProgress criterion = progress.getCriterion(target.criterion());
		return criterion != null && criterion.isDone();
	}

	public static boolean has(final Player player, final Achievement achievement) {
		return has(player, achievement.id);
	}

	/** Achievements earned, of those the tab lists. */
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
		return grant(player, id, true);
	}

	public static boolean grant(final ServerPlayer player, final Achievement achievement) {
		return grant(player, achievement.id);
	}

	/**
	 * Records an achievement or mark. When that completes an achievement for the first time and {@code reward} is set,
	 * its salt is paid and its chime rung; the game shows the toast either way, except before a hunter's first tick in
	 * a session, when what they already have is sent without one.
	 */
	private static boolean grant(final ServerPlayer player, final String id, final boolean reward) {
		Target target = target(id);
		AdvancementHolder holder = holder(player, target.advancement());
		if (holder == null) {
			return false;
		}
		PlayerAdvancements advancements = player.getAdvancements();
		boolean was = advancements.getOrStartProgress(holder).isDone();
		boolean changed = false;
		if (target.criterion() == null) {
			for (String criterion : List.copyOf(holder.value().criteria().keySet())) {
				changed |= advancements.award(holder, criterion);
			}
		} else if (holder.value().criteria().containsKey(target.criterion())) {
			changed = advancements.award(holder, target.criterion());
		}
		if (!changed) {
			return false;
		}
		Achievement achievement = Achievement.byId(target.advancement().getPath().substring(TAB.length()));
		if (reward && achievement != null && !was && advancements.getOrStartProgress(holder).isDone()) {
			if (achievement.reward > 0) {
				SaltService.add(player, achievement.reward);
			}
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.ACHIEVEMENT, SoundSource.PLAYERS, 0.8F, 1.0F);
			announce(player, holder);
			if (achievement == Achievement.APOSTLE) {
				ApostleService.anointed(player);
			} else if (achievement == Achievement.SNUFFER || achievement == Achievement.SUNKEN_BELL || achievement == Achievement.RUST_WHEEL) {
				ApostleService.consider(player);
			}
		}
		return true;
	}

	/**
	 * Tells everyone in the world in the chat, as the game does for its own advancements: who, and
	 * which (its description on the name's tooltip). The game rule that silences advancement messages
	 * silences these too.
	 */
	private static void announce(final ServerPlayer player, final AdvancementHolder holder) {
		net.minecraft.server.MinecraftServer server = player.level().getServer();
		if (server == null || !player.level().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SHOW_ADVANCEMENT_MESSAGES)) {
			return;
		}
		holder.value().display().ifPresent(display -> server.getPlayerList().broadcastSystemMessage(display.type().createAnnouncement(holder, player), false));
	}

	/**
	 * A hunter has arrived in a world: they get the tab's root, and a record kept the old way (before achievements
	 * were advancements) is moved over once, without paying its salt again.
	 */
	public static void joined(final ServerPlayer player) {
		AdvancementHolder root = holder(player, ROOT);
		if (root != null) {
			for (String criterion : List.copyOf(root.value().criteria().keySet())) {
				player.getAdvancements().award(root, criterion);
			}
		}
		Achievements old = player.getAttached(MistinnAttachments.ACHIEVEMENTS);
		if (old != null) {
			for (String id : old.earned()) {
				grant(player, id, false);
			}
			player.removeAttached(MistinnAttachments.ACHIEVEMENTS);
		}
		ApostleService.consider(player);
	}

	// ── What earns them ───────────────────────────────────────────

	/** A creature brought down by this hunter, with what was in their hand. */
	public static void killed(final ServerPlayer player, final boolean hostile) {
		if (!hostile) {
			return;
		}
		grant(player, Achievement.FIRST_KILL);
		// a heavy blow lands ticks after it is let go: the kill is the weapon's that struck, not the one in hand now
		WeaponStyle landing = life.shizune.mistinn.weapon.HeavyService.landing();
		WeaponStyle style = landing != null ? landing : WeaponStyle.of(player.getMainHandItem());
		if (style != null) {
			// the last branch's mark completes the achievement by itself
			grant(player, KILL + style.id);
		}
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

	/** Forgets every achievement and mark (the root stays): leaving the training ground, and tests. */
	public static void clear(final ServerPlayer player) {
		PlayerAdvancements advancements = player.getAdvancements();
		for (AdvancementHolder holder : player.level().getServer().getAdvancements().getAllAdvancements()) {
			Identifier id = holder.id();
			if (!id.getNamespace().equals(Mistinn.MOD_ID) || !id.getPath().startsWith(TAB) || id.equals(ROOT)) {
				continue;
			}
			List<String> done = new java.util.ArrayList<>();
			advancements.getOrStartProgress(holder).getCompletedCriteria().forEach(done::add);
			for (String criterion : done) {
				advancements.revoke(holder, criterion);
			}
		}
		player.removeAttached(MistinnAttachments.ACHIEVEMENTS);
	}
}
