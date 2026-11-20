package life.shizune.mistinn.story;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.world.Builder;
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

	/** How near counts as having reached a place, in blocks. */
	private static final double STATION_REACH = 7.0;
	private static final double LIGHTHOUSE_REACH = 28.0;
	private static final double LAMP_ROOM_REACH = 7.0;
	/** How far from spawn the guaranteed drop station stands. */
	private static final int STATION_MIN = 150;
	private static final int STATION_MAX = 240;
	/** Ticks after first joining before the opening card, so the world has drawn. */
	private static final int OPENING_DELAY = 60;

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
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Snuffer keeper) {
				keeperDown(keeper);
			}
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
		if (active(server) && !player.hasAttached(PROGRESS)) {
			player.setAttached(PROGRESS, StoryProgress.START);
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
					advance(player, StoryStep.STATION);
				}
			}
			case STATION -> {
				if (places != null && near(player, places.station(), STATION_REACH)) {
					advance(player, StoryStep.RADIO);
				}
			}
			case LIGHTHOUSE -> {
				if (places != null && near(player, places.lighthouse(), LIGHTHOUSE_REACH)) {
					advance(player, StoryStep.BELL);
				}
			}
			case LAMP_ROOM -> {
				if (places != null && near(player, places.lampRoom(), LAMP_ROOM_REACH) && player.getY() >= places.lampRoom().getY() - 0.5) {
					advance(player, StoryStep.SNUFFER);
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
			case RADIO, DELIVER -> Optional.of(places.radio());
			case LIGHTHOUSE, BELL -> Optional.of(places.lighthouse());
			case LAMP_ROOM, SNUFFER -> Optional.of(places.lampRoom());
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
		advance(player, StoryStep.LIGHTHOUSE);
		play(player, StoryScript.FIRST_CALL, -1, StoryScript.LIGHTHOUSE.id());
		return true;
	}

	/** The salt block was put into a station's pod. Returns whether the story took it. */
	public static boolean deliver(final ServerPlayer player) {
		StoryProgress progress = get(player);
		if (!active(player.level().getServer()) || progress == null || progress.step() != StoryStep.DELIVER) {
			return false;
		}
		advance(player, StoryStep.WAIT);
		play(player, StoryScript.NO_ANSWER, -1, StoryScript.UNANSWERED.id());
		return true;
	}

	/** The hunter rested at a golden bell. */
	public static void rested(final ServerPlayer player) {
		StoryProgress progress = get(player);
		if (progress != null && progress.step() == StoryStep.BELL) {
			advance(player, StoryStep.LAMP_ROOM);
		}
	}

	/** A keeper fell: everyone near it who was after it sees its last moments, then moves on. */
	private static void keeperDown(final Snuffer keeper) {
		if (!(keeper.level() instanceof ServerLevel level) || !active(level.getServer())) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			StoryProgress progress = get(player);
			if (progress == null || player.distanceToSqr(keeper) > 64.0 * 64.0) {
				continue;
			}
			int step = progress.step().ordinal();
			// a hunter who climbed straight up (or found another lighthouse) still counts
			if (step >= StoryStep.LIGHTHOUSE.ordinal() && step <= StoryStep.SNUFFER.ordinal()) {
				advance(player, StoryStep.DELIVER);
				play(player, StoryScript.SNUFFER_END, keeper.getId(), "");
			}
		}
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
