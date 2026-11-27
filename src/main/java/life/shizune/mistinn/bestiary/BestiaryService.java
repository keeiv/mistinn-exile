package life.shizune.mistinn.bestiary;

import java.util.Map;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Keeps each hunter's bestiary. A creature of the guide is met when it strikes the hunter, the
 * hunter strikes it, or it winds up a blow at them (so a lizard lying as a stone, or a corpse
 * playing dead, is not met by walking past it). One brought down counts for the hunter who did it
 * and for every hunter near enough to have shared the hunt. An old world's deeds fill in what
 * the hunter has already brought down.
 */
public final class BestiaryService {
	/** How near a hunter must be to share in a creature brought down. */
	public static final double SHARE = 24.0;
	/** Deeds that mean a creature was brought down (or met), for worlds older than the bestiary. */
	private static final Map<Achievement, EntityType<?>> DEEDS = Map.of(
		Achievement.SNUFFER, MistinnEntities.SNUFFER,
		Achievement.SUNKEN_BELL, MistinnEntities.SUNKEN_BELL,
		Achievement.RUST_WHEEL, MistinnEntities.RUST_WHEEL,
		Achievement.ELDER_SLAIN, MistinnEntities.CRYSTAL_ELDER,
		Achievement.RIFT_SEALED, MistinnEntities.RIFT_HERALD,
		Achievement.DRAKE_SHATTERED, MistinnEntities.CRYSTAL_DRAKE,
		Achievement.ALPHA_SLAIN, MistinnEntities.RIFT_HOUND,
		Achievement.PIRATE_SHIP, MistinnEntities.PIRATE
	);

	private BestiaryService() {
	}

	public static Bestiary.Record get(final Player player) {
		return player.getAttachedOrElse(MistinnAttachments.BESTIARY, Bestiary.Record.EMPTY);
	}

	/** A creature of the guide crossed the hunter's path. */
	public static void met(final ServerPlayer player, final Entity creature) {
		Bestiary.Entry entry = Bestiary.of(creature.getType());
		if (entry == null || get(player).seen(entry.id()) || player.isSpectator()) {
			return;
		}
		record(player, entry, get(player).with(entry.id(), 0));
	}

	/** A hunter brought a creature down: it counts for them, and for every hunter near enough to have shared the hunt. */
	public static void killed(final ServerPlayer killer, final LivingEntity creature) {
		Bestiary.Entry entry = Bestiary.of(creature.getType());
		if (entry == null) {
			return;
		}
		for (ServerPlayer player : killer.level().getServer().getPlayerList().getPlayers()) {
			boolean shared = player != killer && player.level() == creature.level() && !player.isSpectator()
				&& player.distanceToSqr(creature) <= SHARE * SHARE;
			if (player == killer || shared) {
				record(player, entry, get(player).with(entry.id(), 1));
			}
		}
	}

	/** Fills in what an old world's deeds say the hunter has already brought down. */
	public static void joined(final ServerPlayer player) {
		Bestiary.Record record = get(player);
		for (Map.Entry<Achievement, EntityType<?>> deed : DEEDS.entrySet()) {
			if (AchievementService.has(player, deed.getKey())) {
				record = record.atLeast(EntityType.getKey(deed.getValue()).toString(), 1);
			}
		}
		if (AchievementService.has(player, Achievement.LIZARD_TAMED)) {
			record = record.atLeast(EntityType.getKey(MistinnEntities.CRYSTAL_LIZARD).toString(), 0);
		}
		if (record != get(player)) {
			player.setAttached(MistinnAttachments.BESTIARY, record);
		}
	}

	/** Keeps the new record, and tells the hunter when a page has more on it than before. */
	private static void record(final ServerPlayer player, final Bestiary.Entry entry, final Bestiary.Record next) {
		Bestiary.Record was = get(player);
		player.setAttached(MistinnAttachments.BESTIARY, next);
		full(player, next);
		Component name = entry.type().getDescription();
		for (Bestiary.Detail detail : Bestiary.Detail.values()) {
			if (Bestiary.knows(next, entry, detail) && !Bestiary.knows(was, entry, detail)) {
				// the most there is to say, once
				player.sendOverlayMessage(Component.translatable("message.mistinn.bestiary." + latest(next, entry).name().toLowerCase(java.util.Locale.ROOT), name));
				return;
			}
		}
	}

	/** 博物: every creature the bestiary lists has crossed the hunter's path. */
	private static void full(final ServerPlayer player, final Bestiary.Record record) {
		if (Bestiary.ENTRIES.stream().allMatch(each -> record.seen(each.id()))) {
			AchievementService.grant(player, Achievement.BESTIARY_FULL);
		}
	}

	private static Bestiary.Detail latest(final Bestiary.Record record, final Bestiary.Entry entry) {
		Bestiary.Detail known = Bestiary.Detail.NAME;
		for (Bestiary.Detail detail : Bestiary.Detail.values()) {
			if (Bestiary.knows(record, entry, detail)) {
				known = detail;
			}
		}
		return known;
	}

	public static void register() {
		ServerPlayerEvents.JOIN.register(BestiaryService::joined);
		// struck, or striking: either way the two have met
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && source.getEntity() != null) {
				met(player, source.getEntity());
			} else if (source.getEntity() instanceof ServerPlayer player) {
				met(player, entity);
			}
		});
	}
}
