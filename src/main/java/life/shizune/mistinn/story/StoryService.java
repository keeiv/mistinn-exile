package life.shizune.mistinn.story;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.keeper.RustWheel;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.keeper.SunkenBell;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.world.BellTower;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.MistInn;
import life.shizune.mistinn.world.SaltWorks;
import life.shizune.mistinn.world.Structures;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The story on the server: each hunter's objective, what completes it, and what is shown when it
 * does (story cards, radio calls, the scene over a fallen keeper). Only story worlds have any of
 * it; in free exploration this service does nothing.
 */
public final class StoryService {
	/** Each hunter's progress, sent to that hunter for the objective HUD, minimap and journal. */
	public static final AttachmentType<StoryProgress> PROGRESS = AttachmentRegistry.<StoryProgress>builder()
		.persistent(StoryProgress.CODEC)
		.syncWith(StoryProgress.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("story"));

	/** The guaranteed places, kept on the overworld once built. */
	public static final AttachmentType<StoryPlaces> PLACES = AttachmentRegistry.<StoryPlaces>builder()
		.persistent(StoryPlaces.CODEC)
		.buildAndRegister(Mistinn.id("story_places"));

	/** The places of chapters 2 and 3, kept on the overworld once built. */
	public static final AttachmentType<StoryRoad> ROAD = AttachmentRegistry.<StoryRoad>builder()
		.persistent(StoryRoad.CODEC)
		.buildAndRegister(Mistinn.id("story_road"));

	/** The places of chapter 4, kept on the overworld once built. */
	public static final AttachmentType<StoryWorks> WORKS = AttachmentRegistry.<StoryWorks>builder()
		.persistent(StoryWorks.CODEC)
		.buildAndRegister(Mistinn.id("story_works"));

	/** The Mist Inn of chapter 5, kept on the overworld once built. */
	public static final AttachmentType<StoryInn> INN = AttachmentRegistry.<StoryInn>builder()
		.persistent(StoryInn.CODEC)
		.buildAndRegister(Mistinn.id("story_inn"));

	/** How the story of this world ended, once it has: the same for every hunter in it. */
	private static final AttachmentType<StoryEnding> ENDING = AttachmentRegistry.<StoryEnding>builder()
		.persistent(StoryEnding.CODEC)
		.buildAndRegister(Mistinn.id("story_ending"));

	/** How far the team has come: the story is the team's, every hunter on the same objective. */
	private static final AttachmentType<StoryStep> TEAM_STEP = AttachmentRegistry.<StoryStep>builder()
		.persistent(Codec.STRING.xmap(StoryStep::byId, step -> step.id))
		.buildAndRegister(Mistinn.id("story_team_step"));

	/** The last shared objective completed in the story world. */
	private static final AttachmentType<StoryStep> CHAPTER_PROGRESS = AttachmentRegistry.<StoryStep>builder()
		.persistent(Codec.STRING.xmap(StoryStep::byId, step -> step.id))
		.buildAndRegister(Mistinn.id("story_chapter_progress"));

	/** How near counts as having reached a place, in blocks. */
	private static final double STATION_REACH = 7.0;
	private static final double LIGHTHOUSE_REACH = 28.0;
	private static final double LAMP_ROOM_REACH = 7.0;
	private static final double CAIRN_REACH = 6.0;
	private static final double BELL_STATION_REACH = 28.0;
	private static final double BELFRY_REACH = 7.0;
	private static final double POD_REACH = 8.0;
	private static final double WORKS_REACH = 28.0;
	/** Inside the wall of the wheel's yard, a little in from it. */
	private static final double YARD_REACH = SaltWorks.YARD - 1.0;
	/** How far inland from the drop station the salt works of the story is first looked for. */
	private static final int WORKS_INLAND = 260;
	/** Where along the way from the fog-bell station to the salt works, as a share of it, the answering pod comes down. */
	private static final double POD_AT = 0.35;
	/** Where along the way from the salt works to the fog-bell station, as a share of it, the Mist Inn stands. */
	private static final double INN_AT = 0.3;
	/** How far from the drop station the fog-bell station of the story is first looked for, on the far side from the lighthouse. */
	private static final int ROAD_LENGTH = 360;
	/** The fog-bell station stands at least this far from the lighthouse. */
	private static final int APART = 96;
	/** Where along the road, as a share of it, its three cairns stand. */
	private static final double[] CAIRN_AT = {0.3, 0.55, 0.8};
	/** The station's bell, as rung at every drop: the night toll and the answer are the same sound. */
	private static final float DROP_BELL_PITCH = 0.6F;
	/** How far from spawn the guaranteed drop station stands. */
	private static final int STATION_MIN = 150;
	private static final int STATION_MAX = 240;
	/** Ticks after first joining before the opening card, so the world has drawn. */
	private static final int OPENING_DELAY = 60;
	/** A teammate this near sees a scene played where it happened; further off they hear its lines. */
	private static final double SCENE_REACH = 64.0;
	/** The hunters of the team: everyone in the world. A seam for tests, whose second hunter is not in the player list. */
	public static java.util.function.Function<MinecraftServer, List<ServerPlayer>> hunters = server -> server.getPlayerList().getPlayers();
	/** A card for a teammate who is fighting waits until they are not; at most this long. */
	private static final int CARD_WAIT = 3600;
	/** Between one waiting card and the next. */
	private static final int CARD_GAP = 160;

	private StoryService() {
	}

	// ── Payloads ──────────────────────────────────────────────────

	/** Shows a full-screen story card. */
	public record CardPayload(String card) implements CustomPacketPayload {
		public static final Type<CardPayload> TYPE = new Type<>(Mistinn.id("story_card"));
		public static final StreamCodec<ByteBuf, CardPayload> CODEC = ByteBufCodecs.STRING_UTF8.map(CardPayload::new, CardPayload::card);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Plays a sequence of lines as subtitles. With a {@code focus} entity (not -1) it is an
	 * in-world scene: letterboxed, the view held on that entity. A card may follow it.
	 */
	public record LinesPayload(String sequence, int focus, String cardAfter) implements CustomPacketPayload {
		public static final Type<LinesPayload> TYPE = new Type<>(Mistinn.id("story_lines"));
		public static final StreamCodec<ByteBuf, LinesPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, LinesPayload::sequence,
			ByteBufCodecs.VAR_INT, LinesPayload::focus,
			ByteBufCodecs.STRING_UTF8, LinesPayload::cardAfter,
			LinesPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(CardPayload.TYPE, CardPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LinesPayload.TYPE, LinesPayload.CODEC);
		// after the world mode is settled (WorldMode registers its own SERVER_STARTED first)
		ServerLifecycleEvents.SERVER_STARTED.register(StoryService::ensurePlaces);
		ServerPlayerEvents.JOIN.register(StoryService::join);
		ServerTickEvents.END_SERVER_TICK.register(StoryService::tick);
		// nothing still to come carries over into another world
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LATER.clear();
			WAITING.clear();
			LAST_CARD.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Snuffer keeper) {
				keeperDown(keeper);
			} else if (entity instanceof SunkenBell bell) {
				bellDown(bell);
			} else if (entity instanceof RustWheel wheel) {
				wheelDown(wheel);
			}
		});
		// the great bell of the story's belfry, rung by hand at the end of chapter 3
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer hunter) || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (ring(hunter, hit.getBlockPos()) || burn(hunter, hit.getBlockPos(), hunter.getItemInHand(hand))) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	public static boolean active(final @Nullable MinecraftServer server) {
		return server != null && WorldMode.of(server) == WorldMode.STORY;
	}

	public static @Nullable StoryProgress get(final ServerPlayer player) {
		return player.getAttached(PROGRESS);
	}

	private static @Nullable StoryPlaces places(final MinecraftServer server) {
		return server.overworld().getAttached(PLACES);
	}

	// ── Progress ──────────────────────────────────────────────────

	private static void join(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (!active(server)) {
			return;
		}
		StoryProgress progress = get(player);
		if (progress == null) {
			player.setAttached(PROGRESS, StoryProgress.START);
		} else if (progress.step() == StoryStep.WAIT && !completed(server, StoryStep.RING)) {
			// waiting at the end of chapter 1 since before chapter 2 was written: the road opens now
			complete(server, StoryStep.DELIVER);
			ensureRoad(server);
			advance(player, StoryStep.LISTEN);
		} else if (progress.step() == StoryStep.WAIT && !completed(server, StoryStep.RUST_WHEEL)) {
			// waiting at the end of chapter 3 since before chapter 4 was written: the station answers now
			complete(server, StoryStep.DELIVER);
			ensureWorks(server);
			advance(player, StoryStep.POD);
		} else if (progress.step() == StoryStep.WAIT) {
			// waiting at the end of chapter 4 since before chapter 5 was written: the station is to be heard from now
			ensureInn(server);
			advance(player, StoryStep.CALL);
		} else if (progress.step().ordinal() > StoryStep.DELIVER.ordinal()) {
			complete(server, StoryStep.DELIVER);
		} else if (progress.step() == StoryStep.DELIVER) {
			complete(server, StoryStep.SNUFFER);
		}
		catchUp(player);
	}

	/**
	 * A hunter arrives where the team has gone on without them: they are brought to the team's
	 * objective. In a world from before the story was shared, the hunter furthest along sets it.
	 */
	public static void catchUp(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		StoryProgress progress = get(player);
		if (progress == null) {
			return;
		}
		StoryStep team = server.overworld().getAttached(TEAM_STEP);
		if (team == null || team.ordinal() <= progress.step().ordinal()) {
			server.overworld().setAttached(TEAM_STEP, progress.step());
			// this hunter is the furthest along: whoever is here and behind comes up to them
			for (ServerPlayer mate : hunters.apply(server)) {
				if (mate != player && get(mate) != null && get(mate).step().ordinal() < progress.step().ordinal()) {
					catchUp(mate);
				}
			}
			return;
		}
		// an ending reached while they were away comes to them as it came to the others, by the choice's own path
		StoryStep to = team == StoryStep.ENDED ? StoryStep.CHOOSE : team;
		if (to.ordinal() <= progress.step().ordinal()) {
			return;
		}
		advance(player, to);
		player.sendSystemMessage(Component.translatable("message.mistinn.story.caught_up",
			Component.translatable("story.mistinn.step." + to.id)));
	}

	/**
	 * Moves the hunter who did it on to {@code next}, and the rest of the team with them: one
	 * objective for all. Returns the teammates it moved (those who were behind {@code next}).
	 */
	private static List<ServerPlayer> advanceTeam(final ServerPlayer actor, final StoryStep next) {
		MinecraftServer server = actor.level().getServer();
		advance(actor, next);
		StoryStep team = server.overworld().getAttached(TEAM_STEP);
		if (team == null || team.ordinal() < next.ordinal()) {
			server.overworld().setAttached(TEAM_STEP, next);
		}
		List<ServerPlayer> moved = new ArrayList<>();
		for (ServerPlayer mate : hunters.apply(server)) {
			StoryProgress progress = get(mate);
			if (mate == actor || progress == null || progress.step().ordinal() >= next.ordinal()) {
				continue;
			}
			advance(mate, next);
			moved.add(mate);
		}
		return moved;
	}

	/** Whether a teammate is near enough to the one who did it to see the scene where it happened. */
	private static boolean nearby(final ServerPlayer mate, final ServerPlayer actor) {
		return mate.level() == actor.level() && mate.distanceToSqr(actor) <= SCENE_REACH * SCENE_REACH;
	}

	/**
	 * Plays a sequence for the one who did it and for the teammates moved with them: those near see
	 * it as it is (the scene and the card after it), those far off hear the lines, and their card
	 * waits until they are out of a fight.
	 */
	private static void play(final ServerPlayer actor, final List<ServerPlayer> team, final String sequence, final int focus, final String cardAfter) {
		play(actor, sequence, focus, cardAfter);
		for (ServerPlayer mate : team) {
			if (nearby(mate, actor)) {
				play(mate, sequence, focus, cardAfter);
			} else {
				play(mate, sequence, -1, "");
				if (!cardAfter.isEmpty()) {
					cardWhenCalm(mate, cardAfter);
				}
			}
		}
	}

	/** Shows a card to the one who did it, and to the teammates moved with them (later, for one who is fighting far off). */
	private static void card(final ServerPlayer actor, final List<ServerPlayer> team, final String card) {
		ServerPlayNetworking.send(actor, new CardPayload(card));
		for (ServerPlayer mate : team) {
			if (nearby(mate, actor)) {
				ServerPlayNetworking.send(mate, new CardPayload(card));
			} else {
				cardWhenCalm(mate, card);
			}
		}
	}

	/** A card waiting for a hunter: which, and since when. */
	private record Waiting(String card, long since) {
	}

	private static final Map<UUID, ArrayDeque<Waiting>> WAITING = new HashMap<>();
	private static final Map<UUID, Long> LAST_CARD = new HashMap<>();

	/** How many cards wait for a hunter, for tests. */
	public static int waiting(final ServerPlayer player) {
		ArrayDeque<Waiting> queue = WAITING.get(player.getUUID());
		return queue == null ? 0 : queue.size();
	}

	/** Where the team is in the story, or null before anyone has a place in it. */
	public static @Nullable StoryStep teamStep(final MinecraftServer server) {
		return server.overworld().getAttached(TEAM_STEP);
	}

	private static void cardWhenCalm(final ServerPlayer player, final String card) {
		WAITING.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>()).add(new Waiting(card, player.level().getGameTime()));
	}

	/** Cards held back for hunters who were fighting: shown once they are not (or after a long while regardless). */
	private static void waitingCards(final MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : hunters.apply(server)) {
			ArrayDeque<Waiting> queue = WAITING.get(player.getUUID());
			if (queue == null || queue.isEmpty() || now - LAST_CARD.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < CARD_GAP) {
				continue;
			}
			Waiting next = queue.peek();
			if (calm(player) || now - next.since() > CARD_WAIT) {
				queue.poll();
				LAST_CARD.put(player.getUUID(), now);
				ServerPlayNetworking.send(player, new CardPayload(next.card()));
			}
		}
	}

	/** Whether a hunter can be shown a card without it costing them: up, unhurt for a while, nothing after them. */
	private static boolean calm(final ServerPlayer player) {
		if (!player.isAlive() || life.shizune.mistinn.team.DownService.isDown(player) || player.hurtTime > 0
			|| player.tickCount - player.getLastHurtByMobTimestamp() < 100) {
			return false;
		}
		return player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(16.0),
			mob -> mob.isAlive() && mob.getTarget() == player).isEmpty();
	}

	private static boolean completed(final MinecraftServer server, final StoryStep step) {
		StoryStep last = server.overworld().getAttached(CHAPTER_PROGRESS);
		return last != null && last.ordinal() >= step.ordinal();
	}

	private static void complete(final MinecraftServer server, final StoryStep step) {
		if (!completed(server, step)) {
			server.overworld().setAttached(CHAPTER_PROGRESS, step);
		}
	}

	/** A trainee has elected to begin the story in this world, with players already online. */
	public static void beginAfterTraining(final MinecraftServer server) {
		ensurePlaces(server);
		server.getPlayerList().getPlayers().forEach(StoryService::join);
	}

	private static void tick(final MinecraftServer server) {
		if (!active(server)) {
			return;
		}
		later();
		waitingCards(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			StoryProgress progress = get(player);
			if (progress == null) {
				continue;
			}
			if (!progress.opened() && player.tickCount >= OPENING_DELAY) {
				player.setAttached(PROGRESS, progress.open());
				ServerPlayNetworking.send(player, new CardPayload(StoryScript.DROP.id()));
				continue;
			}
			if (server.getTickCount() % 10 == 0) {
				check(server, player, progress);
			}
		}
	}

	/** The objectives completed by being somewhere or having something. */
	private static void check(final MinecraftServer server, final ServerPlayer player, final StoryProgress progress) {
		StoryPlaces places = places(server);
		switch (progress.step()) {
			case SALT -> {
				if (SaltService.get(player) >= StoryStep.SALT_GOAL) {
					advanceTeam(player, StoryStep.STATION);
				}
			}
			case STATION -> {
				if (places != null && near(player, places.station(), STATION_REACH)) {
					advanceTeam(player, StoryStep.RADIO);
				}
			}
			case LIGHTHOUSE -> {
				if (places != null && near(player, places.lighthouse(), LIGHTHOUSE_REACH)) {
					advanceTeam(player, StoryStep.BELL);
				}
			}
			case LAMP_ROOM -> {
				if (places != null && near(player, places.lampRoom(), LAMP_ROOM_REACH) && player.getY() >= places.lampRoom().getY() - 0.5) {
					advanceTeam(player, StoryStep.SNUFFER);
				}
			}
			case SNUFFER -> {
				if (completed(server, StoryStep.SNUFFER)) {
					advance(player, StoryStep.DELIVER);
				}
			}
			case DELIVER -> {
				if (player.isAlive() && completed(server, StoryStep.DELIVER)) {
					finishDelivery(player);
				}
			}
			case LISTEN -> {
				StoryRoad road = ensureRoad(server);
				if (road != null && nightOutside(player)) {
					heardTheBell(player, road);
				}
			}
			case CAIRN_1, CAIRN_2, CAIRN_3 -> {
				StoryRoad road = road(server);
				int cairn = StoryStep.CAIRNS.indexOf(progress.step());
				if (road != null && cairn < road.cairns().size() && near(player, road.cairns().get(cairn), CAIRN_REACH)) {
					foundCairn(player, cairn);
				}
			}
			case BELL_STATION -> {
				StoryRoad road = road(server);
				if (road != null && near(player, road.bellStation(), BELL_STATION_REACH)) {
					card(player, advanceTeam(player, StoryStep.BELL_REST), StoryScript.BELL_STATION.id());
				}
			}
			case BELFRY -> {
				StoryRoad road = road(server);
				if (road != null && near(player, road.belfry(), BELFRY_REACH) && player.getY() >= road.belfry().getY() - 0.5) {
					advanceTeam(player, StoryStep.SUNKEN_BELL);
				}
			}
			case SUNKEN_BELL -> {
				// it fell to someone else while this hunter was elsewhere
				if (completed(server, StoryStep.SUNKEN_BELL)) {
					advance(player, StoryStep.RING);
				}
			}
			case RING -> {
				if (completed(server, StoryStep.RING)) {
					ensureWorks(server);
					advance(player, StoryStep.POD);
					ServerPlayNetworking.send(player, new CardPayload(StoryScript.ANSWER.id()));
				}
			}
			case POD -> {
				StoryWorks works = ensureWorks(server);
				if (works != null && near(player, works.pod(), POD_REACH)) {
					play(player, advanceTeam(player, StoryStep.SALT_WORKS), StoryScript.SUPPLY_CALL, -1, StoryScript.SALT_WORKS.id());
				}
			}
			case SALT_WORKS -> {
				StoryWorks works = works(server);
				if (works != null && near(player, works.works(), WORKS_REACH)) {
					advanceTeam(player, StoryStep.WORKS_BELL);
				}
			}
			case YARD -> {
				StoryWorks works = works(server);
				if (works != null && near(player, works.works(), YARD_REACH) && Math.abs(player.getY() - works.works().getY()) <= 4.0) {
					advanceTeam(player, StoryStep.RUST_WHEEL);
				}
			}
			case RUST_WHEEL -> {
				// it stopped for someone else while this hunter was elsewhere
				if (completed(server, StoryStep.RUST_WHEEL)) {
					ensureInn(server);
					advance(player, StoryStep.CALL);
					ServerPlayNetworking.send(player, new CardPayload(StoryScript.STOPPED.id()));
				}
			}
			case CHOOSE -> {
				// another hunter chose for this world while this one was elsewhere: the same ending, the card alone
				StoryEnding ending = ending(server);
				if (ending != null) {
					advance(player, StoryStep.ENDED);
					life.shizune.mistinn.achievement.AchievementService.grant(player, achievement(ending));
					life.shizune.mistinn.ledger.LedgerService.tell(player, "ending." + ending.name().toLowerCase(java.util.Locale.ROOT), true);
					ServerPlayNetworking.send(player, new CardPayload(card(ending).id()));
				}
			}
			default -> {
			}
		}
	}

	private static boolean near(final ServerPlayer player, final BlockPos place, final double reach) {
		double dx = player.getX() - (place.getX() + 0.5);
		double dz = player.getZ() - (place.getZ() + 0.5);
		return player.level() == player.level().getServer().overworld() && dx * dx + dz * dz <= reach * reach;
	}

	/** Moves a hunter on to {@code next}, pointing the minimap at its place. */
	public static void advance(final ServerPlayer player, final StoryStep next) {
		StoryProgress progress = get(player);
		if (progress == null) {
			return;
		}
		player.setAttached(PROGRESS, progress.at(next, target(player.level().getServer(), next)));
	}

	private static Optional<BlockPos> target(final MinecraftServer server, final StoryStep step) {
		StoryPlaces places = places(server);
		if (places == null) {
			return Optional.empty();
		}
		return switch (step) {
			case STATION -> Optional.of(places.station());
			case RADIO, DELIVER, CALL -> Optional.of(places.radio());
			case LIGHTHOUSE, BELL -> Optional.of(places.lighthouse());
			case LAMP_ROOM, SNUFFER -> Optional.of(places.lampRoom());
			default -> roadTarget(server, step);
		};
	}

	private static Optional<BlockPos> roadTarget(final MinecraftServer server, final StoryStep step) {
		StoryRoad road = road(server);
		if (road == null) {
			return Optional.empty();
		}
		int cairn = StoryStep.CAIRNS.indexOf(step);
		if (cairn >= 0) {
			return cairn < road.cairns().size() ? Optional.of(road.cairns().get(cairn)) : Optional.of(road.bellStation());
		}
		return switch (step) {
			case BELL_STATION, BELL_REST -> Optional.of(road.bellStation());
			case BELFRY, SUNKEN_BELL, RING -> Optional.of(road.belfry());
			default -> worksTarget(server, step);
		};
	}

	private static Optional<BlockPos> worksTarget(final MinecraftServer server, final StoryStep step) {
		StoryWorks works = works(server);
		if (works == null) {
			return Optional.empty();
		}
		return switch (step) {
			case POD -> Optional.of(works.pod());
			case SALT_WORKS, YARD, RUST_WHEEL -> Optional.of(works.works());
			case WORKS_BELL -> Optional.of(works.works().offset(SaltWorks.BELL));
			case CHOOSE -> inn(server) == null ? Optional.empty() : Optional.of(inn(server).inn());
			default -> Optional.empty();
		};
	}

	/** Sends a sequence of lines (and the card after it) and records it for the journal. */
	private static void play(final ServerPlayer player, final String sequence, final int focus, final String cardAfter) {
		StoryProgress progress = get(player);
		if (progress != null) {
			player.setAttached(PROGRESS, progress.hear(sequence));
		}
		ServerPlayNetworking.send(player, new LinesPayload(sequence, focus, cardAfter));
	}

	// ── What completes the other objectives ──────────────────────────

	/** A working radio was used. Before the first call, this is the call. Returns whether it was. */
	public static boolean radioMended(final ServerPlayer player) {
		StoryProgress progress = get(player);
		if (!active(player.level().getServer()) || progress == null || progress.step().ordinal() > StoryStep.RADIO.ordinal()) {
			return false;
		}
		play(player, advanceTeam(player, StoryStep.LIGHTHOUSE), StoryScript.FIRST_CALL, -1, StoryScript.LIGHTHOUSE.id());
		return true;
	}

	/** The salt block was put into a station's pod. Returns whether the story took it. */
	public static boolean deliver(final ServerPlayer player) {
		StoryProgress progress = get(player);
		MinecraftServer server = player.level().getServer();
		if (active(server) && progress != null && progress.step() == StoryStep.CHOOSE) {
			return end(player, StoryEnding.ANCHOR);
		}
		if (!active(server) || progress == null || progress.step() != StoryStep.DELIVER) {
			return false;
		}
		// One keeper leaves one salt block; a late hunter gets the same radio call without paying again.
		boolean first = !completed(server, StoryStep.DELIVER);
		complete(server, StoryStep.DELIVER);
		finishDelivery(player);
		return first;
	}

	private static void finishDelivery(final ServerPlayer player) {
		// nothing answers; the road is laid out now, for the night the bell is heard
		ensureRoad(player.level().getServer());
		play(player, advanceTeam(player, StoryStep.LISTEN), StoryScript.NO_ANSWER, -1, StoryScript.UNANSWERED.id());
	}

	/** The hunter rested at a golden bell. */
	public static void rested(final ServerPlayer player) {
		StoryProgress progress = get(player);
		if (progress != null && progress.step() == StoryStep.BELL) {
			advanceTeam(player, StoryStep.LAMP_ROOM);
		} else if (progress != null && progress.step() == StoryStep.BELL_REST) {
			advanceTeam(player, StoryStep.BELFRY);
		} else if (progress != null && progress.step() == StoryStep.WORKS_BELL) {
			advanceTeam(player, StoryStep.YARD);
		}
	}

	/** A keeper fell: everyone near it who was after it sees its last moments, then moves on. */
	private static void keeperDown(final Snuffer keeper) {
		if (!(keeper.level() instanceof ServerLevel level) || !active(level.getServer())) {
			return;
		}
		StoryPlaces places = places(level.getServer());
		if (level == level.getServer().overworld() && places != null
			&& keeper.distanceToSqr(Vec3.atCenterOf(places.lampRoom())) <= 48.0 * 48.0) {
			complete(level.getServer(), StoryStep.SNUFFER);
		}
		for (ServerPlayer player : level.players()) {
			StoryProgress progress = get(player);
			if (progress == null || player.distanceToSqr(keeper) > 64.0 * 64.0) {
				continue;
			}
			int step = progress.step().ordinal();
			// a hunter who climbed straight up (or found another lighthouse) still counts
			if (step >= StoryStep.LIGHTHOUSE.ordinal() && step <= StoryStep.SNUFFER.ordinal()) {
				play(player, advanceTeam(player, StoryStep.DELIVER), StoryScript.SNUFFER_END, keeper.getId(), "");
			}
		}
	}

	// ── Chapters 2 and 3: the salt road and the Sunken Bell ────────────

	private static @Nullable StoryRoad road(final MinecraftServer server) {
		return server.overworld().getAttached(ROAD);
	}

	/** Out under the night sky in the overworld, where a bell from the sea is heard. */
	private static boolean nightOutside(final ServerPlayer player) {
		ServerLevel level = player.level();
		return level == level.getServer().overworld() && level.isDarkOutside() && level.canSeeSky(player.blockPosition().above());
	}

	/**
	 * The bell from the sea, three times, the station's own sound at every drop, heard from the
	 * way the road goes. Then the chapter opens on the road of salt.
	 */
	private static void heardTheBell(final ServerPlayer player, final StoryRoad road) {
		Vec3 toward = Vec3.atCenterOf(road.bellStation()).subtract(player.position()).multiply(1.0, 0.0, 1.0);
		Vec3 from = player.position().add(toward.lengthSqr() < 1.0 ? new Vec3(40.0, 0.0, 0.0) : toward.normalize().scale(40.0)).add(0.0, 12.0, 0.0);
		for (int toll = 0; toll < 3; toll++) {
			later(toll * 34, () -> player.level().playSound(null, from.x, from.y, from.z, SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 4.0F, DROP_BELL_PITCH));
		}
		play(player, advanceTeam(player, StoryStep.CAIRN_1), StoryScript.NIGHT_TOLL, -1, StoryScript.SALT_ROAD.id());
	}

	/** A cairn of the road: the tag under its stone, what the hunter before wrote, and the next. */
	private static void foundCairn(final ServerPlayer player, final int cairn) {
		int number = StoryScript.TAG_NUMBERS.get(cairn);
		String sequence = StoryScript.TAGS.get(cairn);
		ItemStack tag = new ItemStack(MistinnItems.HUNTER_TAG);
		tag.set(DataComponents.ITEM_NAME, Component.translatable("item.mistinn.hunter_tag.numbered", number));
		java.util.List<Component> lore = new java.util.ArrayList<>();
		for (StoryScript.Line line : StoryScript.sequence(sequence)) {
			lore.add(Component.translatable(line.key()).withStyle(s -> s.withColor(0x9A968E).withItalic(true)));
		}
		tag.set(DataComponents.LORE, new ItemLore(lore));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.6F, 1.3F);
		boolean last = cairn + 1 >= StoryStep.CAIRNS.size();
		List<ServerPlayer> team = advanceTeam(player, last ? StoryStep.BELL_STATION : StoryStep.CAIRNS.get(cairn + 1));
		// every hunter of the team keeps a copy of the tag and what it says
		for (ServerPlayer hunter : concat(player, team)) {
			ItemStack copy = tag.copy();
			if (!hunter.getInventory().add(copy)) {
				hunter.drop(copy, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		// the last cairn stands by the sea: the road ends there, and so does chapter 2
		play(player, team, sequence, -1, last ? StoryScript.ROAD_END.id() : "");
	}

	/** The Sunken Bell fell: everyone near it who was after it hears its last words, then is told to ring the bell. */
	private static void bellDown(final SunkenBell bell) {
		if (!(bell.level() instanceof ServerLevel level) || !active(level.getServer())) {
			return;
		}
		StoryRoad road = road(level.getServer());
		if (level == level.getServer().overworld() && road != null && bell.distanceToSqr(Vec3.atCenterOf(road.belfry())) <= 48.0 * 48.0) {
			complete(level.getServer(), StoryStep.SUNKEN_BELL);
		}
		for (ServerPlayer player : level.players()) {
			StoryProgress progress = get(player);
			if (progress == null || player.distanceToSqr(bell) > 64.0 * 64.0) {
				continue;
			}
			int step = progress.step().ordinal();
			if (step >= StoryStep.BELL_STATION.ordinal() && step <= StoryStep.SUNKEN_BELL.ordinal()) {
				play(player, advanceTeam(player, StoryStep.RING), StoryScript.SUNKEN_BELL_END, bell.getId(), "");
			}
		}
	}

	/** Whether {@code pos} is part of the great bell hanging over the story's belfry floor. */
	public static boolean greatBell(final StoryRoad road, final BlockPos pos) {
		int dx = pos.getX() - road.belfry().getX();
		int dy = pos.getY() - road.belfry().getY();
		int dz = pos.getZ() - road.belfry().getZ();
		return Math.abs(dx) <= 3 && Math.abs(dz) <= 3 && dy >= BellTower.BELL_LIP - BellTower.DECK - 2 && dy <= BellTower.BELL_LIP - BellTower.DECK + 3;
	}

	/**
	 * The hunter rings the great bell. It answers from beyond the fog: the station's bell, three
	 * times, as at a drop. Returns whether the story took the click.
	 */
	public static boolean ring(final ServerPlayer player, final BlockPos pos) {
		MinecraftServer server = player.level().getServer();
		StoryProgress progress = get(player);
		StoryRoad road = server == null ? null : road(server);
		if (!active(server) || progress == null || progress.step() != StoryStep.RING || road == null || !greatBell(road, pos)
			|| player.level() != server.overworld()) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 bell = Vec3.atCenterOf(pos);
		level.playSound(null, bell.x, bell.y, bell.z, MistinnSounds.SUNKEN_BELL_RING, SoundSource.BLOCKS, 4.0F, 0.8F);
		complete(server, StoryStep.RING);
		// the answer: a pod, already on its way down inland
		ensureWorks(server);
		List<ServerPlayer> team = advanceTeam(player, StoryStep.POD);
		// no one ordered it: the hunter says, to themself, why they rang
		play(player, team, StoryScript.RANG, -1, "");
		// long after, far out over the fog and high up, three tolls
		Vec3 away = bell.subtract(Vec3.atCenterOf(road.bellStation())).multiply(1.0, 0.0, 1.0);
		Vec3 far = player.position().add(away.lengthSqr() < 1.0 ? new Vec3(0.0, 0.0, 48.0) : away.normalize().scale(48.0)).add(0.0, 30.0, 0.0);
		for (int toll = 0; toll < 3; toll++) {
			later(90 + toll * 34, () -> level.playSound(null, far.x, far.y, far.z, SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 3.0F, DROP_BELL_PITCH));
		}
		later(90 + 3 * 34 + 30, () -> card(player, team, StoryScript.ANSWER.id()));
		return true;
	}

	/** Things the story does a little later: tolls spaced out, a card after them. */
	private record Later(int tick, Runnable run) {
	}

	private static final java.util.List<Later> LATER = new java.util.ArrayList<>();
	private static int now;

	private static void later(final int ticks, final Runnable run) {
		LATER.add(new Later(now + ticks, run));
	}

	private static void later() {
		now++;
		for (int i = LATER.size() - 1; i >= 0; i--) {
			if (LATER.get(i).tick() <= now) {
				LATER.remove(i).run().run();
			}
		}
	}

	/**
	 * The salt road, laid out the first time the story reaches chapter 2: a fog-bell station on a
	 * reef along the coast, out the other way from the lighthouse, and three salt cairns on the
	 * way to it from the drop station. Returns null outside a story world.
	 */
	public static @Nullable StoryRoad ensureRoad(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		StoryRoad built = level.getAttached(ROAD);
		StoryPlaces places = places(server);
		if (built != null || places == null || !active(server)) {
			return built;
		}
		RandomSource random = RandomSource.create(level.getSeed() ^ 0x526F6164L);
		BlockPos from = places.station();
		double ax = from.getX() - places.lighthouse().getX();
		double az = from.getZ() - places.lighthouse().getZ();
		double length = Math.sqrt(ax * ax + az * az);
		double angle = length < 1.0 ? 0.0 : Math.atan2(az, ax);
		BlockPos tower = null;
		for (int attempt = 0; attempt < 6 && tower == null; attempt++) {
			// straight away from the lighthouse first, then turning a little each way, then further out
			double turn = (attempt % 3 == 0 ? 0.0 : attempt % 3 == 1 ? 1.0 : -1.0) * Math.PI / 4.0;
			int reach = ROAD_LENGTH + attempt / 3 * 200;
			BlockPos aim = from.offset((int) Math.round(Math.cos(angle + turn) * reach), 0, (int) Math.round(Math.sin(angle + turn) * reach));
			BlockPos found = findReef(level, aim);
			if (found.distSqr(places.lighthouse()) > (double) APART * APART) {
				tower = found;
			}
		}
		if (tower == null) {
			tower = findReef(level, from.offset((int) Math.round(Math.cos(angle) * ROAD_LENGTH), 0, (int) Math.round(Math.sin(angle) * ROAD_LENGTH)));
		}
		BlockPos belfry = BellTower.build(new Builder(level, tower, random));
		java.util.List<BlockPos> cairns = new java.util.ArrayList<>();
		for (double share : CAIRN_AT) {
			int x = (int) Math.round(from.getX() + (tower.getX() - from.getX()) * share);
			int z = (int) Math.round(from.getZ() + (tower.getZ() - from.getZ()) * share);
			BlockPos floor = dryGround(level, x, z, from);
			Structures.saltCairn(new Builder(level, floor, random));
			cairns.add(floor);
		}
		StoryRoad road = new StoryRoad(cairns, tower, belfry);
		level.setAttached(ROAD, road);
		Mistinn.LOGGER.info("Story road: cairns {}, fog-bell station {}", cairns, tower);
		return road;
	}

	/**
	 * A reef off the coast nearest {@code aim}, as the fog-bell stations stand: the sea floor one
	 * to seven blocks down, the tower built from the sea's surface. Failing that, the shore itself.
	 */
	private static BlockPos findReef(final ServerLevel level, final BlockPos aim) {
		var found = level.findClosestBiome3d(biome -> biome.is(BiomeTags.IS_BEACH), aim, 3200, 32, 64);
		BlockPos centre = found == null ? aim : found.getFirst();
		int sea = level.getSeaLevel();
		for (int r = 4; r <= 48; r += 4) {
			for (int dx = -r; dx <= r; dx += 4) {
				for (int dz = -r; dz <= r; dz += 4) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = centre.getX() + dx;
					int z = centre.getZ() + dz;
					level.getChunk(x >> 4, z >> 4);
					int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
					if (floor <= sea - 1 && floor >= sea - 7) {
						return new BlockPos(x, sea, z);
					}
				}
			}
		}
		BlockPos shore = ground(level, centre.getX(), centre.getZ());
		return new BlockPos(shore.getX(), Math.max(sea, shore.getY()), shore.getZ());
	}

	/** Dry ground at (x, z), or the nearest dry ground back toward {@code home} if that is water. */
	private static BlockPos dryGround(final ServerLevel level, final int x, final int z, final BlockPos home) {
		BlockPos floor = ground(level, x, z);
		double dx = home.getX() - x;
		double dz = home.getZ() - z;
		double length = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		for (int step = 4; step <= 64 && !level.getBlockState(floor).getFluidState().isEmpty(); step += 4) {
			floor = ground(level, x + (int) Math.round(dx / length * step), z + (int) Math.round(dz / length * step));
		}
		return floor;
	}

	// ── Chapter 4: the salt works ─────────────────────────────────────

	private static @Nullable StoryWorks works(final MinecraftServer server) {
		return server.overworld().getAttached(WORKS);
	}

	/** The Rust Wheel stopped: everyone near it who was after it hears the man at the wheel, then the chapter ends. */
	private static void wheelDown(final RustWheel wheel) {
		if (!(wheel.level() instanceof ServerLevel level) || !active(level.getServer())) {
			return;
		}
		StoryWorks works = works(level.getServer());
		if (level == level.getServer().overworld() && works != null && wheel.distanceToSqr(Vec3.atCenterOf(works.works())) <= 48.0 * 48.0) {
			complete(level.getServer(), StoryStep.RUST_WHEEL);
		}
		for (ServerPlayer player : level.players()) {
			StoryProgress progress = get(player);
			if (progress == null || player.distanceToSqr(wheel) > 64.0 * 64.0) {
				continue;
			}
			int step = progress.step().ordinal();
			// a hunter who went straight in (or found another salt works) still counts
			if (step >= StoryStep.SALT_WORKS.ordinal() && step <= StoryStep.RUST_WHEEL.ordinal()) {
				ensureInn(level.getServer());
				play(player, advanceTeam(player, StoryStep.CALL), StoryScript.RUST_WHEEL_END, wheel.getId(), StoryScript.STOPPED.id());
			}
		}
	}

	/**
	 * The places of chapter 4, built when the great bell is first rung: the salt works on level ground inland of the
	 * coast the lighthouse and the fog-bell station stand on, and the pod the station sends down in answer, on the way
	 * there from the fog-bell station. Returns null outside a story world or before the road was laid out.
	 */
	public static @Nullable StoryWorks ensureWorks(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		StoryWorks built = level.getAttached(WORKS);
		StoryPlaces places = places(server);
		StoryRoad road = road(server);
		if (built != null || places == null || road == null || !active(server)) {
			return built;
		}
		RandomSource random = RandomSource.create(level.getSeed() ^ 0x576F726BL);
		BlockPos from = places.station();
		// square to the line of the coast, from the lighthouse to the fog-bell station, on the drop station's side
		double cx = road.bellStation().getX() - places.lighthouse().getX();
		double cz = road.bellStation().getZ() - places.lighthouse().getZ();
		double nx = -cz;
		double nz = cx;
		if (nx * (from.getX() - places.lighthouse().getX()) + nz * (from.getZ() - places.lighthouse().getZ()) < 0.0) {
			nx = -nx;
			nz = -nz;
		}
		double length = Math.sqrt(nx * nx + nz * nz);
		double angle = length < 1.0 ? random.nextDouble() * Math.PI * 2.0 : Math.atan2(nz, nx);
		BlockPos aim = from.offset((int) Math.round(Math.cos(angle) * WORKS_INLAND), 0, (int) Math.round(Math.sin(angle) * WORKS_INLAND));
		BlockPos site = findWorksGround(level, aim, angle, places, road);
		BlockPos middle = SaltWorks.build(new Builder(level, site, random));
		int px = (int) Math.round(road.bellStation().getX() + (site.getX() - road.bellStation().getX()) * POD_AT);
		int pz = (int) Math.round(road.bellStation().getZ() + (site.getZ() - road.bellStation().getZ()) * POD_AT);
		BlockPos pod = dryGround(level, px, pz, site);
		Structures.supplyPod(new Builder(level, pod, random));
		StoryWorks works = new StoryWorks(pod, middle);
		level.setAttached(WORKS, works);
		Mistinn.LOGGER.info("Story salt works: pod {}, works {}", pod, middle);
		return works;
	}

	/**
	 * Level, dry, open ground near {@code aim} wide enough for the salt works, well away from the lighthouse and the
	 * fog-bell station; failing open ground, level ground; failing that, the first place tried.
	 */
	private static BlockPos findWorksGround(final ServerLevel level, final BlockPos aim, final double angle, final StoryPlaces places, final StoryRoad road) {
		BlockPos fallback = null;
		BlockPos levelOnly = null;
		for (int ring = 0; ring < 3; ring++) {
			int radius = ring * 48;
			int around = ring == 0 ? 1 : 8;
			for (int i = 0; i < around; i++) {
				double a = angle + i * Math.PI * 2.0 / around;
				BlockPos floor = ground(level, aim.getX() + (int) Math.round(Math.cos(a) * radius), aim.getZ() + (int) Math.round(Math.sin(a) * radius));
				if (fallback == null) {
					fallback = floor;
				}
				if (floor.distSqr(places.lighthouse()) <= (double) APART * APART || floor.distSqr(road.bellStation()) <= (double) APART * APART
					|| !dryLand(level, floor) || !wideAndLevel(level, floor)) {
					continue;
				}
				if (open(level, floor)) {
					return floor;
				}
				if (levelOnly == null) {
					levelOnly = floor;
				}
			}
		}
		return levelOnly != null ? levelOnly : fallback;
	}

	/** Whether the ground round {@code floor} is within three blocks of it across the salt works' whole yard. */
	private static boolean wideAndLevel(final ServerLevel level, final BlockPos floor) {
		int reach = SaltWorks.YARD;
		// the boiling house and the pans reach further out past the gate, to the south
		int[] across = {-reach, 0, reach, reach + 8};
		for (int dx = -reach; dx <= reach; dx += reach) {
			for (int dz : across) {
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, floor.getX() + dx, floor.getZ() + dz) - 1;
				if (Math.abs(y - floor.getY()) > 3) {
					return false;
				}
			}
		}
		return true;
	}

	/** Whether no trees stand over the middle and the corners of the yard. */
	private static boolean open(final ServerLevel level, final BlockPos floor) {
		int reach = SaltWorks.YARD;
		for (int dx = -reach; dx <= reach; dx += reach) {
			for (int dz = -reach; dz <= reach; dz += reach) {
				int x = floor.getX() + dx;
				int z = floor.getZ() + dz;
				if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) != level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)) {
					return false;
				}
			}
		}
		return true;
	}

	// ── Chapter 5: the anchor ─────────────────────────────────────────

	private static @Nullable StoryInn inn(final MinecraftServer server) {
		return server.overworld().getAttached(INN);
	}

	/** How the story of this world ended, or null while it has not. */
	public static @Nullable StoryEnding ending(final MinecraftServer server) {
		return server.overworld().getAttached(ENDING);
	}

	/**
	 * At a radio in chapter 5: Rust Anchor asks for the salt, and the hunter whose tag lay under the second cairn cuts in
	 * and asks for it not to go. Returns whether the call was this.
	 */
	public static boolean radioCall(final ServerPlayer player) {
		StoryProgress progress = get(player);
		MinecraftServer server = player.level().getServer();
		if (!active(server) || progress == null || progress.step() != StoryStep.CALL) {
			return false;
		}
		ensureInn(server);
		play(player, advanceTeam(player, StoryStep.CHOOSE), StoryScript.STATION_CALL, -1, StoryScript.THE_CALL.id());
		return true;
	}

	/**
	 * The Rust God's salt thrown into the Mist Inn's fire. Returns whether the story took it; the salt is spent here when
	 * it does.
	 */
	public static boolean burn(final ServerPlayer player, final BlockPos pos, final ItemStack stack) {
		MinecraftServer server = player.level().getServer();
		StoryProgress progress = get(player);
		StoryInn inn = server == null ? null : inn(server);
		if (!active(server) || progress == null || progress.step() != StoryStep.CHOOSE || inn == null || !pos.equals(inn.hearth())
			|| !stack.is(MistinnItems.RUST_GOD_SALT) || player.level() != server.overworld() || ending(server) != null) {
			return false;
		}
		stack.consume(1, player);
		return end(player, StoryEnding.INN);
	}

	/** The story of this world ends, the way {@code ending} goes, for the hunter who chose it. */
	private static boolean end(final ServerPlayer player, final StoryEnding ending) {
		MinecraftServer server = player.level().getServer();
		if (ending(server) != null) {
			return false;
		}
		ServerLevel level = server.overworld();
		level.setAttached(ENDING, ending);
		List<ServerPlayer> team = advanceTeam(player, StoryStep.ENDED);
		for (ServerPlayer hunter : concat(player, team)) {
			life.shizune.mistinn.achievement.AchievementService.grant(hunter, achievement(ending));
			life.shizune.mistinn.ledger.LedgerService.tell(hunter, "ending." + ending.name().toLowerCase(java.util.Locale.ROOT), true);
		}
		if (ending == StoryEnding.INN) {
			// the fire takes the salt and burns blue, white smoke off it
			StoryInn inn = inn(server);
			level.setBlockAndUpdate(inn.hearth(), net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE.defaultBlockState());
			level.playSound(null, inn.hearth(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 0.6F);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, inn.hearth().getX() + 0.5, inn.hearth().getY() + 0.8, inn.hearth().getZ() + 0.5,
				30, 0.2, 0.3, 0.2, 0.02);
			play(player, team, StoryScript.INN_END, -1, StoryScript.INN.id());
			// that night, far beyond the fog and high up, the anchor's chain groans a long time, and parts
			Vec3 far = player.position().add(0.0, 40.0, 64.0);
			for (int groan = 0; groan < 4; groan++) {
				later(120 + groan * 22, () -> level.playSound(null, far.x, far.y, far.z, SoundEvents.CHAIN_PLACE, SoundSource.AMBIENT, 4.0F, 0.4F));
			}
			later(220, () -> level.playSound(null, far.x, far.y, far.z, SoundEvents.CHAIN_BREAK, SoundSource.AMBIENT, 4.0F, 0.3F));
		} else {
			// the anchor is hungry: the salt they carry goes up with the rest
			for (ServerPlayer hunter : concat(player, team)) {
				confiscate(hunter);
			}
			play(player, team, StoryScript.ANCHOR_END, -1, StoryScript.ANCHOR.id());
			// far off over the fog, the station's bell, three times: another pod is going down
			Vec3 far = player.position().add(0.0, 30.0, -48.0);
			for (int toll = 0; toll < 3; toll++) {
				later(150 + toll * 34, () -> level.playSound(null, far.x, far.y, far.z, SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 3.0F, DROP_BELL_PITCH));
			}
		}
		return true;
	}

	/** Takes every bit of salt a hunter carries, for the anchor. */
	public static int confiscate(final ServerPlayer hunter) {
		int taken = 0;
		var inventory = hunter.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			net.minecraft.world.item.ItemStack stack = inventory.getItem(i);
			if (stack.is(MistinnItems.SALT) || stack.is(MistinnItems.SALT_BOMB) || stack.is(MistinnItems.RUST_GOD_SALT)) {
				taken += stack.getCount();
				inventory.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			}
		}
		return taken;
	}

	private static List<ServerPlayer> concat(final ServerPlayer first, final List<ServerPlayer> rest) {
		List<ServerPlayer> all = new ArrayList<>();
		all.add(first);
		all.addAll(rest);
		return all;
	}

	private static String achievement(final StoryEnding ending) {
		return ending == StoryEnding.INN ? life.shizune.mistinn.achievement.AchievementService.ENDING_INN
			: life.shizune.mistinn.achievement.AchievementService.ENDING_ANCHOR;
	}

	private static StoryScript.Card card(final StoryEnding ending) {
		return ending == StoryEnding.INN ? StoryScript.INN : StoryScript.ANCHOR;
	}

	/**
	 * The Mist Inn, built the first time a story world reaches chapter 5: on level, dry ground a third of the way from the
	 * salt works toward the fog-bell station. Returns null outside a story world or before the salt works stand.
	 */
	public static @Nullable StoryInn ensureInn(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		StoryInn built = level.getAttached(INN);
		StoryWorks works = works(server);
		StoryRoad road = road(server);
		if (built != null || works == null || road == null || !active(server)) {
			return built;
		}
		RandomSource random = RandomSource.create(level.getSeed() ^ 0x496E6E4CL);
		BlockPos from = works.works();
		BlockPos to = road.bellStation();
		int x = (int) Math.round(from.getX() + (to.getX() - from.getX()) * INN_AT);
		int z = (int) Math.round(from.getZ() + (to.getZ() - from.getZ()) * INN_AT);
		BlockPos site = findInnGround(level, x, z, from);
		BlockPos hearth = MistInn.build(new Builder(level, site, random));
		StoryInn inn = new StoryInn(site.offset(MistInn.DOOR), hearth);
		level.setAttached(INN, inn);
		Mistinn.LOGGER.info("Story Mist Inn: {}", site);
		return inn;
	}

	/** Level, dry ground for the inn near (x, z): the first found out to forty blocks, else what is there. */
	private static BlockPos findInnGround(final ServerLevel level, final int x, final int z, final BlockPos home) {
		for (int ring = 0; ring < 3; ring++) {
			int radius = ring * 20;
			int around = ring == 0 ? 1 : 8;
			for (int i = 0; i < around; i++) {
				double a = i * Math.PI * 2.0 / around;
				BlockPos floor = ground(level, x + (int) Math.round(Math.cos(a) * radius), z + (int) Math.round(Math.sin(a) * radius));
				if (dryLand(level, floor) && even(level, floor, 7, 2)) {
					return floor;
				}
			}
		}
		return dryGround(level, x, z, home);
	}

	/** Whether the ground out to {@code reach} round {@code floor} is within {@code slope} blocks of it. */
	private static boolean even(final ServerLevel level, final BlockPos floor, final int reach, final int slope) {
		for (int dx = -reach; dx <= reach; dx += reach) {
			for (int dz = -reach; dz <= reach; dz += reach) {
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, floor.getX() + dx, floor.getZ() + dz) - 1;
				if (Math.abs(y - floor.getY()) > slope) {
					return false;
				}
			}
		}
		return true;
	}

	// ── The guaranteed places ────────────────────────────────────────

	/** In a new story world, builds the drop station and the lighthouse the story leads to. */
	private static void ensurePlaces(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!active(server) || level.hasAttached(PLACES)) {
			return;
		}
		BlockPos spawn = level.getRespawnData().pos();
		RandomSource random = RandomSource.create(level.getSeed() ^ 0x53746F7279L);

		BlockPos stationFloor = findStationGround(level, spawn, random);
		Builder station = new Builder(level, stationFloor, random);
		station.clear(-6, 1, -6, 6, 12, 6);
		Structures.station(station, Structures.Station.INTACT);
		BlockPos radio = stationFloor.offset(Structures.RADIO);

		BlockPos lighthouseFloor = findCoast(level, stationFloor);
		Builder tower = new Builder(level, lighthouseFloor, random);
		BlockPos lampRoom = Structures.lighthouse(tower);

		level.setAttached(PLACES, new StoryPlaces(stationFloor, radio, lighthouseFloor, lampRoom));
		Mistinn.LOGGER.info("Story places: drop station {}, lighthouse {}", stationFloor, lighthouseFloor);
	}

	/** Level, dry land a walk from spawn; failing that, the first place tried. */
	private static BlockPos findStationGround(final ServerLevel level, final BlockPos spawn, final RandomSource random) {
		float start = random.nextFloat() * (float) (Math.PI * 2.0);
		BlockPos fallback = null;
		for (int ring = 0; ring < 3; ring++) {
			int radius = STATION_MIN + (STATION_MAX - STATION_MIN) * ring / 2;
			for (int i = 0; i < 12; i++) {
				double angle = start + i * Math.PI * 2.0 / 12.0;
				int x = spawn.getX() + (int) Math.round(Math.cos(angle) * radius);
				int z = spawn.getZ() + (int) Math.round(Math.sin(angle) * radius);
				BlockPos floor = ground(level, x, z);
				if (fallback == null) {
					fallback = floor;
				}
				if (dryLand(level, floor) && level(level, floor, 2)) {
					return floor;
				}
			}
		}
		return fallback;
	}

	/** The nearest beach from {@code from}, on dry ground; or open ground further out if none. */
	private static BlockPos findCoast(final ServerLevel level, final BlockPos from) {
		var found = level.findClosestBiome3d(biome -> biome.is(BiomeTags.IS_BEACH), from, 3200, 32, 64);
		BlockPos centre = found == null ? from.offset(320, 0, 0) : found.getFirst();
		for (int r = 0; r <= 24; r += 4) {
			for (int dx = -r; dx <= r; dx += 4) {
				for (int dz = -r; dz <= r; dz += 4) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos floor = ground(level, centre.getX() + dx, centre.getZ() + dz);
					if (level.getBlockState(floor).getFluidState().isEmpty()) {
						return floor;
					}
				}
			}
		}
		return ground(level, centre.getX(), centre.getZ());
	}

	/** The top solid block at (x, z), generating the chunk if needed. */
	private static BlockPos ground(final ServerLevel level, final int x, final int z) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		return new BlockPos(x, Math.max(y, level.getSeaLevel()), z);
	}

	private static boolean dryLand(final ServerLevel level, final BlockPos floor) {
		Holder<Biome> biome = level.getBiome(floor);
		return level.getBlockState(floor).getFluidState().isEmpty()
			&& !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH);
	}

	private static boolean level(final ServerLevel level, final BlockPos floor, final int slope) {
		int[][] corners = {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
		for (int[] c : corners) {
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, floor.getX() + c[0], floor.getZ() + c[1]) - 1;
			if (Math.abs(y - floor.getY()) > slope) {
				return false;
			}
		}
		return true;
	}
}
