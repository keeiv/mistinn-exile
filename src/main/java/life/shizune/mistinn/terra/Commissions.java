package life.shizune.mistinn.terra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 泰拉的委託: what the tillers of a woken homestead ask of the hunters. Each homestead keeps a board of three, filled
 * again each morning; a hunter takes one at a time from the homestead's keeper (its elder, or whoever of them is left),
 * and does it: brings down so many of a creature, brings back so much of something, or holds the homestead through a
 * crystal tide. Done, it pays, and the tillers trust the hunters a little more: the trust is the world's, and opens the
 * keepers' better wares (and, later, the expeditions). Only in story and free worlds: on the tillers' way the elder is
 * the walker's own, and the training ground has no tillers of its own to ask.
 * <p>
 * Everything a hunter asks for is decided here, on the server: the keeper must be alive, near, of a woken homestead and
 * bear them no grudge; the commission must be on that homestead's board; what is handed in is counted and taken here.
 */
public final class Commissions {
	/** How many commissions a board holds, how long one may be held, and how near the keeper a hunter must stand. */
	public static final int BOARD = 3;
	public static final long HELD = 3L * 24000L;
	public static final double REACH = 6.0;
	/** How near a hunt's kill a teammate on the same hunt shares it. */
	public static final double SHARE = 24.0;
	/** Trust that opens the keepers' better wares. */
	public static final int WARES = 2;

	/** What a commission asks. */
	public enum Kind {
		/** Bring down so many of a creature. */
		HUNT,
		/** Bring back so much of something, to the keeper who asked. */
		DELIVER,
		/** Hold the homestead through a crystal tide; the next night it comes. */
		GUARD,
		/** Go to one of the forebears' grounds and get out alive; always on the board once the tillers trust the hunters enough. */
		EXPEDITION;

		public final String id = this.name().toLowerCase(Locale.ROOT);

		static Kind byId(final String id) {
			return valueOf(id.toUpperCase(Locale.ROOT));
		}
	}

	/** One commission: what kind, what of (a creature's or an item's id; none for a guard), how many, and its pay. */
	public record Commission(String id, Kind kind, String what, int count, int salt, int cores) {
		public static final Codec<Commission> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Commission::id),
			Codec.STRING.xmap(Kind::byId, kind -> kind.id).fieldOf("kind").forGetter(Commission::kind),
			Codec.STRING.fieldOf("what").forGetter(Commission::what),
			Codec.INT.fieldOf("count").forGetter(Commission::count),
			Codec.INT.fieldOf("salt").forGetter(Commission::salt),
			Codec.INT.optionalFieldOf("cores", 0).forGetter(Commission::cores)
		).apply(i, Commission::new));
		public static final StreamCodec<ByteBuf, Commission> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Commission::id,
			ByteBufCodecs.STRING_UTF8.map(Kind::byId, kind -> kind.id), Commission::kind,
			ByteBufCodecs.STRING_UTF8, Commission::what,
			ByteBufCodecs.VAR_INT, Commission::count,
			ByteBufCodecs.VAR_INT, Commission::salt,
			ByteBufCodecs.VAR_INT, Commission::cores,
			Commission::new);

		/** The translation key of what it asks: {@code commission.mistinn.<kind>}, filled with how many and of what. */
		public String key() {
			return "commission.mistinn." + this.kind.id;
		}

		/** The name of what it is about: a creature's, an item's, or none. */
		public Component subject() {
			Identifier id = Identifier.tryParse(this.what);
			if (id == null || this.what.isEmpty()) {
				return Component.empty();
			}
			if (this.kind == Kind.HUNT) {
				return BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityType::getDescription).orElse(Component.literal(this.what));
			}
			if (this.kind == Kind.EXPEDITION) {
				return life.shizune.mistinn.expedition.Expedition.name(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
			}
			return BuiltInRegistries.ITEM.getOptional(id).map(item -> item.getName(item.getDefaultInstance())).orElse(Component.literal(this.what));
		}

		/** What it asks, in words: "Bring down 3 crystal drakes", "Bring back 12 crystal dust", "Hold the homestead through a crystal tide". */
		public Component describe() {
			return Component.translatable(this.key(), this.count, this.subject());
		}
	}

	/** One homestead's board: the commissions on it, and the day it was last filled. */
	public record Board(int domain, List<Commission> offers, long day) {
		public static final Codec<Board> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Board::domain),
			Commission.CODEC.listOf().fieldOf("offers").forGetter(Board::offers),
			Codec.LONG.fieldOf("day").forGetter(Board::day)
		).apply(i, Board::new));
	}

	/** The world's commissions: every homestead's board, the trust its tillers have in the hunters, and a count for ids. */
	public record State(List<Board> boards, int trust, int issued) {
		public static final State EMPTY = new State(List.of(), 0, 0);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Board.CODEC.listOf().fieldOf("boards").forGetter(State::boards),
			Codec.INT.fieldOf("trust").forGetter(State::trust),
			Codec.INT.optionalFieldOf("issued", 0).forGetter(State::issued)
		).apply(i, State::new));
	}

	/** What a hunter has taken on: the commission, the homestead it came from, how far along, and the time it runs out. */
	public record Taken(Commission commission, int domain, int done, long until) {
		public static final Codec<Taken> CODEC = RecordCodecBuilder.create(i -> i.group(
			Commission.CODEC.fieldOf("commission").forGetter(Taken::commission),
			Codec.INT.fieldOf("domain").forGetter(Taken::domain),
			Codec.INT.fieldOf("done").forGetter(Taken::done),
			Codec.LONG.fieldOf("until").forGetter(Taken::until)
		).apply(i, Taken::new));
		public static final StreamCodec<ByteBuf, Taken> STREAM_CODEC = StreamCodec.composite(
			Commission.STREAM_CODEC, Taken::commission,
			ByteBufCodecs.VAR_INT, Taken::domain,
			ByteBufCodecs.VAR_INT, Taken::done,
			ByteBufCodecs.VAR_LONG, Taken::until,
			Taken::new);

		Taken with(final int more) {
			return new Taken(this.commission, this.domain, Math.min(this.commission.count(), this.done + more), this.until);
		}
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.<State>builder()
		.persistent(State.CODEC)
		.buildAndRegister(Mistinn.id("commissions"));
	/** The hunter's commission: kept through death and across sessions, and told to their own client for the HUD. */
	public static final AttachmentType<Taken> TAKEN = AttachmentRegistry.<Taken>builder()
		.persistent(Taken.CODEC)
		.copyOnDeath()
		.syncWith(Taken.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("commission"));

	/** What a hunter asking the keeper is told. */
	public enum Result {
		TAKEN, DONE, HANDED, ABANDONED, NO_KEEPER, NOT_HERE, BUSY, GONE, NOTHING_TO_HAND, NONE;

		public String key() {
			return "message.mistinn.commission." + this.name().toLowerCase(Locale.ROOT);
		}
	}

	private Commissions() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				expire(server);
			}
		});
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(BoardPayload.TYPE, BoardPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(AskPayload.TYPE, AskPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(AskPayload.TYPE,
			(payload, context) -> ask(context.player(), payload));
	}

	// ── the board, on the hunter's screen ─────────────────────────

	/** The keeper's board, sent to the hunter who asked: who keeps it, what is on it, the trust, and whether it trades too. */
	public record BoardPayload(int keeper, int domain, List<Commission> offers, int trust, boolean trades) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
		public static final Type<BoardPayload> TYPE = new Type<>(Mistinn.id("commission_board"));
		public static final StreamCodec<ByteBuf, BoardPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BoardPayload::keeper,
			ByteBufCodecs.VAR_INT, BoardPayload::domain,
			Commission.STREAM_CODEC.apply(ByteBufCodecs.list(BOARD + 1 + life.shizune.mistinn.expedition.Grounds.ALL.size())), BoardPayload::offers,
			ByteBufCodecs.VAR_INT, BoardPayload::trust,
			ByteBufCodecs.BOOL, BoardPayload::trades,
			BoardPayload::new);

		@Override
		public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** What the hunter asks of the keeper from the screen: take one, let theirs go, hand in, or trade. */
	public record AskPayload(int keeper, String action, String commission) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
		public static final Type<AskPayload> TYPE = new Type<>(Mistinn.id("commission_ask"));
		public static final StreamCodec<ByteBuf, AskPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AskPayload::keeper,
			ByteBufCodecs.stringUtf8(16), AskPayload::action,
			ByteBufCodecs.stringUtf8(32), AskPayload::commission,
			AskPayload::new);

		@Override
		public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Shows the hunter the keeper's board (as they right-click it, and after each thing asked). */
	public static void open(final ServerPlayer hunter, final Tiller keeper) {
		Settlements.Settlement home = asking(hunter, keeper);
		if (home == null) {
			return;
		}
		MinecraftServer server = hunter.level().getServer();
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(hunter, new BoardPayload(keeper.getId(), home.domain(),
			offers(server, home.domain()), trust(server), keeper.role() == Tiller.Role.ELDER));
	}

	/** What the screen asked, decided here as if asked in person. */
	private static void ask(final ServerPlayer hunter, final AskPayload payload) {
		if (!(hunter.level().getEntity(payload.keeper()) instanceof Tiller keeper)) {
			return;
		}
		Result result = switch (payload.action()) {
			case "take" -> take(hunter, keeper, payload.commission());
			case "abandon" -> abandon(hunter);
			case "hand" -> handIn(hunter, keeper);
			case "sell" -> {
				if (asking(hunter, keeper) != null) {
					int salt = life.shizune.mistinn.expedition.Salvage.sell(hunter);
					hunter.sendSystemMessage(Component.translatable(salt > 0 ? "message.mistinn.salvage.sold" : "message.mistinn.salvage.none", salt)
						.withStyle(salt > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
				}
				yield null;
			}
			case "depart" -> {
				if (asking(hunter, keeper) != null) {
					depart(hunter);
				}
				yield null;
			}
			case "trade" -> {
				if (asking(hunter, keeper) != null && keeper.role() == Tiller.Role.ELDER) {
					keeper.trade(hunter);
				}
				yield null;
			}
			default -> null;
		};
		if (result == null) {
			return;
		}
		if (result != Result.DONE) {
			hunter.sendSystemMessage(Component.translatable(result.key()).withStyle(ChatFormatting.GRAY), true);
		}
		open(hunter, keeper);
	}

	// ── the world's state ─────────────────────────────────────────

	public static State state(final MinecraftServer server) {
		return server.overworld().getAttachedOrElse(STATE, State.EMPTY);
	}

	public static int trust(final MinecraftServer server) {
		return state(server).trust();
	}

	private static void set(final MinecraftServer server, final State state) {
		server.overworld().setAttached(STATE, state);
	}

	/** Whether commissions are asked in this world at all. */
	public static boolean asked(final MinecraftServer server) {
		WorldMode mode = WorldMode.of(server);
		return mode == WorldMode.STORY || mode == WorldMode.FREE;
	}

	/** A homestead's board as it stands today: filled up to three if a morning has passed since it last was. */
	public static Board board(final MinecraftServer server, final int domain) {
		State state = state(server);
		long today = Math.floorDiv(server.overworld().getOverworldClockTime(), 24000L);
		Board board = state.boards().stream().filter(b -> b.domain() == domain).findFirst().orElse(new Board(domain, List.of(), Long.MIN_VALUE));
		if (board.day() == today) {
			// what was taken today is put back tomorrow
			return board;
		}
		List<Commission> offers = new ArrayList<>(board.offers());
		int issued = state.issued();
		RandomSource random = server.overworld().getRandom();
		while (offers.size() < BOARD) {
			issued++;
			offers.add(draw(random, state.trust(), "c" + issued, offers.stream().anyMatch(c -> c.kind() == Kind.GUARD)));
		}
		Board filled = new Board(domain, List.copyOf(offers), today);
		putBoard(server, new State(state.boards(), state.trust(), issued), filled);
		return filled;
	}

	/** Trust that opens each of the forebears' grounds to expeditions. */
	private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Integer> OPENS = Map.of(
		life.shizune.mistinn.expedition.Grounds.DAM, 1);
	/** What an expedition pays: salt and cores. */
	public static final int EXPEDITION_SALT = 600;
	public static final int EXPEDITION_CORES = 2;

	/** The expeditions the tillers trust the hunters with now: one for each ground opened, and ready. */
	public static List<Commission> expeditions(final MinecraftServer server) {
		List<Commission> open = new ArrayList<>();
		int trust = trust(server);
		for (net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> ground : life.shizune.mistinn.expedition.Grounds.ALL) {
			if (trust >= OPENS.getOrDefault(ground, Integer.MAX_VALUE) && life.shizune.mistinn.expedition.Expedition.layout(ground) != null) {
				open.add(new Commission("x_" + ground.identifier().getPath(), Kind.EXPEDITION, ground.identifier().toString(), 1,
					EXPEDITION_SALT, EXPEDITION_CORES));
			}
		}
		return open;
	}

	/** Everything a homestead's keeper offers today: its board, and the expeditions, which stay on it. */
	public static List<Commission> offers(final MinecraftServer server, final int domain) {
		List<Commission> all = new ArrayList<>(board(server, domain).offers());
		all.addAll(expeditions(server));
		return all;
	}

	/** Puts these commissions on a homestead's board for today, in place of what was there (tests, and the server's own use). */
	public static void post(final MinecraftServer server, final int domain, final List<Commission> offers) {
		long today = Math.floorDiv(server.overworld().getOverworldClockTime(), 24000L);
		putBoard(server, state(server), new Board(domain, List.copyOf(offers), today));
	}

	private static void putBoard(final MinecraftServer server, final State state, final Board board) {
		List<Board> boards = new ArrayList<>(state.boards());
		boards.removeIf(b -> b.domain() == board.domain());
		boards.add(board);
		set(server, new State(List.copyOf(boards), state.trust(), state.issued()));
	}

	/** A new commission, as the tillers' trust allows; at most one guard on a board. */
	static Commission draw(final RandomSource random, final int trust, final String id, final boolean guarded) {
		float roll = random.nextFloat();
		if (!guarded && roll < 0.2F) {
			return new Commission(id, Kind.GUARD, "", 1, 400, 1);
		}
		if (roll < 0.62F) {
			int pick = random.nextInt(trust >= 3 ? 4 : 3);
			return switch (pick) {
				case 0 -> hunt(id, "mistinn:crystal_lizard", 4 + random.nextInt(3), 40, 0);
				case 1 -> hunt(id, "mistinn:crystal_drake", 2 + random.nextInt(2), 120, 1);
				case 2 -> hunt(id, "mistinn:rift_hound", 4 + random.nextInt(3), 50, 0);
				default -> hunt(id, "mistinn:crystal_elder", 1, 800, 2);
			};
		}
		int pick = random.nextInt(trust >= WARES ? 3 : 2);
		return switch (pick) {
			case 0 -> deliver(id, "mistinn:crystal_dust", 10 + random.nextInt(7), 10);
			case 1 -> deliver(id, "mistinn:rift_shard", 3 + random.nextInt(3), 60);
			default -> deliver(id, "mistinn:crystal_core", 1 + random.nextInt(2), 300);
		};
	}

	private static Commission hunt(final String id, final String what, final int count, final int each, final int cores) {
		return new Commission(id, Kind.HUNT, what, count, count * each, cores);
	}

	private static Commission deliver(final String id, final String what, final int count, final int each) {
		return new Commission(id, Kind.DELIVER, what, count, count * each, 0);
	}

	// ── the keeper ────────────────────────────────────────────────

	/** How far from its hub a homestead's tillers are looked for (they keep to their fields and houses, and rest at night). */
	private static final double SETTLEMENT = 72.0;

	/**
	 * Who keeps a woken homestead's board: its elder while it lives, else a warden, else any of them still there. Only the
	 * homestead's own tillers, wherever about it they are at the hour.
	 */
	public static @Nullable Tiller keeper(final ServerLevel level, final Settlements.Settlement home) {
		List<Tiller> there = level.getEntitiesOfClass(Tiller.class, new net.minecraft.world.phys.AABB(home.hub()).inflate(SETTLEMENT),
			tiller -> tiller.isAlive() && ownHome(tiller, home));
		for (Tiller.Role role : new Tiller.Role[] {Tiller.Role.ELDER, Tiller.Role.WARDEN}) {
			for (Tiller each : there) {
				if (each.role() == role) {
					return each;
				}
			}
		}
		return there.isEmpty() ? null : there.getFirst();
	}

	private static boolean ownHome(final Tiller tiller, final Settlements.Settlement home) {
		Settlements.Settlement own = TillerRoutine.home(tiller);
		return own != null && own.domain() == home.domain();
	}

	/** The woken homestead this tiller keeps the board of, or null: it is not its keeper, or there is nothing to keep. */
	public static Settlements.@Nullable Settlement keeps(final Tiller tiller) {
		if (!(tiller.level() instanceof ServerLevel level) || !asked(level.getServer())) {
			return null;
		}
		Settlements.Settlement home = TillerRoutine.home(tiller);
		return home != null && home.lit() && keeper(level, home) == tiller ? home : null;
	}

	/** Whether this hunter may ask this tiller of commissions now: its keeper, near, alive, and no grudge held against them. */
	private static Settlements.@Nullable Settlement asking(final ServerPlayer hunter, final Tiller keeper) {
		Settlements.Settlement home = keeps(keeper);
		if (home == null || !keeper.isAlive() || keeper.level() != hunter.level() || keeper.distanceTo(hunter) > REACH || keeper.holdsAgainst(hunter)) {
			return null;
		}
		return home;
	}

	// ── what a hunter asks ────────────────────────────────────────

	/** The hunter takes the commission with this id from the keeper's board. */
	public static Result take(final ServerPlayer hunter, final Tiller keeper, final String id) {
		Settlements.Settlement home = asking(hunter, keeper);
		if (home == null) {
			return Result.NO_KEEPER;
		}
		if (hunter.hasAttached(TAKEN)) {
			return Result.BUSY;
		}
		MinecraftServer server = hunter.level().getServer();
		Board board = board(server, home.domain());
		Commission chosen = board.offers().stream().filter(c -> c.id().equals(id)).findFirst()
			.orElse(expeditions(server).stream().filter(c -> c.id().equals(id)).findFirst().orElse(null));
		if (chosen == null) {
			return Result.GONE;
		}
		if (chosen.kind() != Kind.EXPEDITION) {
			List<Commission> left = new ArrayList<>(board.offers());
			left.remove(chosen);
			putBoard(server, state(server), new Board(home.domain(), List.copyOf(left), board.day()));
		}
		hunter.setAttached(TAKEN, new Taken(chosen, home.domain(), 0, hunter.level().getGameTime() + HELD));
		return Result.TAKEN;
	}

	/** The hunter sets out on the expedition they hold, from the hub they stand at; told why not, if they cannot. */
	public static void depart(final ServerPlayer hunter) {
		Taken taken = hunter.getAttached(TAKEN);
		if (taken == null || taken.commission().kind() != Kind.EXPEDITION) {
			hunter.sendSystemMessage(Component.translatable(Result.NONE.key()).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> ground = net.minecraft.resources.ResourceKey.create(
			net.minecraft.core.registries.Registries.DIMENSION, Identifier.parse(taken.commission().what()));
		life.shizune.mistinn.expedition.Expedition.Result result = life.shizune.mistinn.expedition.Expedition.muster(hunter, ground);
		if (result != life.shizune.mistinn.expedition.Expedition.Result.MUSTERING) {
			hunter.sendSystemMessage(Component.translatable("message.mistinn.expedition.refused." + result.name().toLowerCase(Locale.ROOT))
				.withStyle(ChatFormatting.GRAY), true);
		}
	}

	/** A hunter got out of a ground alive: an expedition there they hold is done. */
	public static void extracted(final ServerPlayer hunter, final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> ground) {
		Taken taken = hunter.getAttached(TAKEN);
		if (taken != null && taken.commission().kind() == Kind.EXPEDITION && taken.commission().what().equals(ground.identifier().toString())) {
			done(hunter, taken.with(1));
		}
	}

	/** For tests: the tillers' trust in the hunters, set. */
	public static void trust(final MinecraftServer server, final int trust) {
		State state = state(server);
		set(server, new State(state.boards(), trust, state.issued()));
	}

	/** The hunter lets their commission go: nothing is lost but the commission. */
	public static Result abandon(final ServerPlayer hunter) {
		if (!hunter.hasAttached(TAKEN)) {
			return Result.NONE;
		}
		hunter.removeAttached(TAKEN);
		return Result.ABANDONED;
	}

	/** The hunter hands the keeper who asked what they have of it, from the hand they hold it in. */
	public static Result handIn(final ServerPlayer hunter, final Tiller keeper) {
		Settlements.Settlement home = asking(hunter, keeper);
		Taken taken = hunter.getAttached(TAKEN);
		if (home == null) {
			return Result.NO_KEEPER;
		}
		if (taken == null || taken.commission().kind() != Kind.DELIVER) {
			return Result.NONE;
		}
		if (taken.domain() != home.domain()) {
			return Result.NOT_HERE;
		}
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(taken.commission().what()));
		int wanted = taken.commission().count() - taken.done();
		int given = 0;
		for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
			ItemStack held = hunter.getItemInHand(hand);
			if (held.is(item) && given < wanted) {
				int part = Math.min(held.getCount(), wanted - given);
				held.shrink(part);
				given += part;
			}
		}
		if (given == 0) {
			return Result.NOTHING_TO_HAND;
		}
		Taken now = taken.with(given);
		hunter.setAttached(TAKEN, now);
		if (now.done() >= now.commission().count()) {
			done(hunter, now);
			return Result.DONE;
		}
		return Result.HANDED;
	}

	// ── what does it ──────────────────────────────────────────────

	/** A creature brought down: it counts for the hunter's hunt, and for each teammate near on the same hunt. */
	public static void killed(final ServerPlayer killer, final LivingEntity creature) {
		String what = EntityType.getKey(creature.getType()).toString();
		for (ServerPlayer player : killer.level().getServer().getPlayerList().getPlayers()) {
			boolean near = player == killer || player.level() == creature.level() && !player.isSpectator() && player.distanceTo(creature) <= SHARE;
			Taken taken = player.getAttached(TAKEN);
			if (near && taken != null && taken.commission().kind() == Kind.HUNT && taken.commission().what().equals(what)) {
				progress(player, taken.with(1));
			}
		}
	}

	/** A crystal tide held at a homestead: the guard of it, done, for each hunter there who took it. */
	public static void held(final ServerPlayer player, final int domain) {
		Taken taken = player.getAttached(TAKEN);
		if (taken != null && taken.commission().kind() == Kind.GUARD && taken.domain() == domain) {
			progress(player, taken.with(1));
		}
	}

	/** Whether a hunter near a homestead at nightfall has taken its guard: then the tide comes that night. */
	public static boolean guarding(final List<ServerPlayer> near, final int domain) {
		return near.stream().anyMatch(player -> {
			Taken taken = player.getAttached(TAKEN);
			return taken != null && taken.commission().kind() == Kind.GUARD && taken.domain() == domain;
		});
	}

	private static void progress(final ServerPlayer player, final Taken now) {
		player.setAttached(TAKEN, now);
		if (now.done() >= now.commission().count()) {
			done(player, now);
		} else {
			player.sendSystemMessage(Component.translatable("message.mistinn.commission.progress", now.commission().describe(), now.done(),
				now.commission().count()).withStyle(ChatFormatting.AQUA), true);
		}
	}

	/** Done: the pay, the tillers' trust one more, and the commission off the hunter's hands. */
	private static void done(final ServerPlayer player, final Taken taken) {
		player.removeAttached(TAKEN);
		Commission commission = taken.commission();
		SaltService.add(player, commission.salt());
		life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.Achievement.COMMISSION_DONE);
		if (commission.cores() > 0) {
			ItemStack cores = new ItemStack(MistinnItems.CRYSTAL_CORE, commission.cores());
			if (!player.getInventory().add(cores)) {
				player.spawnAtLocation(player.level(), cores);
			}
		}
		MinecraftServer server = player.level().getServer();
		State state = state(server);
		set(server, new State(state.boards(), state.trust() + 1, state.issued()));
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.sendSystemMessage(Component.translatable("message.mistinn.commission.done", commission.describe(), commission.salt())
			.withStyle(ChatFormatting.GOLD));
	}

	/** Commissions held past their time are let go, with a word. */
	private static void expire(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Taken taken = player.getAttached(TAKEN);
			if (taken != null && player.level().getGameTime() > taken.until()) {
				player.removeAttached(TAKEN);
				player.sendSystemMessage(Component.translatable("message.mistinn.commission.expired", taken.commission().describe())
					.withStyle(ChatFormatting.GRAY));
			}
		}
	}
}
