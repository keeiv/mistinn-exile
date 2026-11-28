package life.shizune.mistinn.expedition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.block.GravestoneBlockEntity;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.residue.DeathMark;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.team.DownService;
import life.shizune.mistinn.terra.HomesteadTravel;
import life.shizune.mistinn.terra.Settlements;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Terra's expeditions: a party sent from a woken homestead to one of the {@link Grounds}, to find what it can and get
 * out again.
 *
 * <p>A hunter at a homestead's hub sets out; whoever still stands by the hub when the count is done goes too. A ground
 * nobody is in is swept and stocked again before the party arrives (the creatures, what lies loose, the gravestones,
 * the chests); a ground someone is still in (online or not) is joined as it is. Each hunter has their own twenty
 * minutes there. They get out by holding one of its exits for the count, while what lives there comes at them; whoever
 * is still inside when their time is up falls, down or not. A death there goes the usual way (the gravestone stays in
 * the ground until it is swept). The forebears' work does not break: in a ground no block is broken or placed, no
 * bucket poured and no explosion takes a block, though chests, doors and levers work as anywhere.
 */
public final class Expedition {
	/** How near the hub a hunter must stand to go along. */
	public static final double REACH = 8.0;
	/** How long each hunter has in a ground. */
	public static final long LIMIT = 20L * 60L * 20L;
	/** Between two waves at an exit. */
	public static final int WAVE_EVERY = 160;
	/** The warnings, in ticks left. */
	private static final long[] WARNINGS = {5L * 60L * 20L, 60L * 20L};
	/** From setting out to going (ten seconds); tests make it shorter. */
	public static int musterTicks = 200;
	/** How long an exit must be held (thirty seconds); tests make it shorter. */
	public static int exitTicks = 600;
	/** A ground's chunks stay loaded this long after the party goes in. */
	private static final int HOLD_AFTER = 100;
	/** Damage for a hunter whose time ran out in a ground. */
	public static final ResourceKey<DamageType> LOST = ResourceKey.create(Registries.DAMAGE_TYPE, Mistinn.id("lost_afar"));
	/** Keeps a ground's chunks loaded while it is swept and stocked; never saved. */
	public static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, Mistinn.id("expedition"),
		new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));

	/**
	 * A ground's standing: whether it is built, how many times it has been stocked, who is in it now (online or not,
	 * with the game time their time is up), who left a gravestone there since it was last stocked, and who was away when
	 * it was swept, so their map mark still points at a stone that is gone.
	 */
	public record Ground(boolean built, int serial, Map<UUID, Long> inside, List<UUID> graved, List<UUID> stale) {
		public static final Ground NONE = new Ground(false, 0, Map.of(), List.of(), List.of());
		public static final Codec<Ground> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("built").forGetter(Ground::built),
			Codec.INT.fieldOf("serial").forGetter(Ground::serial),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).fieldOf("inside").forGetter(Ground::inside),
			UUIDUtil.CODEC.listOf().fieldOf("graved").forGetter(Ground::graved),
			UUIDUtil.CODEC.listOf().fieldOf("stale").forGetter(Ground::stale)
		).apply(i, Ground::new));

		Ground in(final UUID hunter, final long until) {
			Map<UUID, Long> next = new LinkedHashMap<>(this.inside);
			next.put(hunter, until);
			return new Ground(this.built, this.serial, Map.copyOf(next), this.graved, this.stale);
		}

		Ground out(final UUID hunter) {
			Map<UUID, Long> next = new LinkedHashMap<>(this.inside);
			next.remove(hunter);
			return new Ground(this.built, this.serial, Map.copyOf(next), this.graved, this.stale);
		}
	}

	/**
	 * A hunter in a ground: which, the hub they set out from (where they come back to), the game time their time is up,
	 * the stocking of the ground they came into, and when they began to hold an exit (0 when they are not).
	 */
	public record Away(ResourceKey<Level> ground, GlobalPos from, long ends, int serial, long leaving) {
		public static final Codec<Away> CODEC = RecordCodecBuilder.create(i -> i.group(
			Level.RESOURCE_KEY_CODEC.fieldOf("ground").forGetter(Away::ground),
			GlobalPos.CODEC.fieldOf("from").forGetter(Away::from),
			Codec.LONG.fieldOf("ends").forGetter(Away::ends),
			Codec.INT.fieldOf("serial").forGetter(Away::serial),
			Codec.LONG.optionalFieldOf("leaving", 0L).forGetter(Away::leaving)
		).apply(i, Away::new));
		public static final StreamCodec<ByteBuf, Away> STREAM_CODEC = StreamCodec.composite(
			ResourceKey.streamCodec(Registries.DIMENSION), Away::ground,
			GlobalPos.STREAM_CODEC, Away::from,
			ByteBufCodecs.VAR_LONG, Away::ends,
			ByteBufCodecs.VAR_INT, Away::serial,
			ByteBufCodecs.VAR_LONG, Away::leaving,
			Away::new);

		Away holding(final long since) {
			return new Away(this.ground, this.from, this.ends, this.serial, since);
		}
	}

	/** What a hunter setting out is told. */
	public enum Result {
		/** On the way: the count has begun. */
		MUSTERING,
		/** Not at a woken homestead's hub. */
		NOT_HOME,
		/** Not in the overworld, or in the training ground. */
		ELSEWHERE,
		/** Nothing lives anywhere on Peaceful: there is nothing to go for. */
		PEACEFUL,
		/** Already in a ground. */
		AWAY,
		/** Down. */
		DOWN,
		/** This ground is not ready to be sent to. */
		NOT_READY,
		/** A party is already setting out from this hub. */
		BUSY
	}

	/** A party setting out: the one who called it, where to, from which hub, and the game time it goes. */
	private record Muster(UUID leader, ResourceKey<Level> ground, GlobalPos hub, long departs, boolean waited) {
	}

	/** A ground being built: its plan, and the next step of it. */
	private static final class Build {
		private final List<Consumer<ServerLevel>> steps;
		private int next;

		Build(final List<Consumer<ServerLevel>> steps) {
			this.steps = steps;
		}
	}

	/** The waves at one exit: when the last came, and how many have. */
	private record Waves(long last, int count) {
	}

	/** Every ground's standing, on the overworld. */
	public static final AttachmentType<Map<ResourceKey<Level>, Ground>> GROUNDS = AttachmentRegistry.<Map<ResourceKey<Level>, Ground>>builder()
		.persistent(Codec.unboundedMap(Level.RESOURCE_KEY_CODEC, Ground.CODEC))
		.buildAndRegister(Mistinn.id("grounds"));
	/** The hunter's time in a ground: kept across sessions (not through death: a death there ends it), told to their own client. */
	public static final AttachmentType<Away> AWAY = AttachmentRegistry.<Away>builder()
		.persistent(Away.CODEC)
		.syncWith(Away.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("away"));

	private static final Map<ResourceKey<Level>, Layout> LAYOUTS = new HashMap<>();
	private static final List<Muster> MUSTERS = new ArrayList<>();
	private static final Map<String, Waves> WAVES = new HashMap<>();
	/** The grounds whose chunks are held loaded, and until when. */
	private static final Map<ResourceKey<Level>, Long> HELD = new HashMap<>();
	/** The grounds being built, one build each. */
	private static final Map<ResourceKey<Level>, Build> BUILDS = new LinkedHashMap<>();
	/** How long the builds may take of each tick. */
	private static final long BUDGET_NANOS = 20_000_000L;
	/** A party waiting on its ground being built looks again this often. */
	private static final int WAIT = 20;

	private Expedition() {
	}

	public static void register() {
		layout(Grounds.DAM, new DamGround());
		ServerTickEvents.END_SERVER_TICK.register(Expedition::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Away away = handler.player.getAttached(AWAY);
			if (away != null && away.leaving() != 0L) {
				handler.player.setAttached(AWAY, away.holding(0L));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				died(player);
			}
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (Grounds.is(origin) && !Grounds.is(destination)) {
				// out of a ground by any way: free to build again
				player.getAbilities().mayBuild = !player.gameMode().isBlockPlacingRestricted();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MUSTERS.clear();
			BUILDS.clear();
			WAVES.clear();
			HELD.clear();
		});
	}

	/** Sets how {@code ground} is laid out ({@code null} takes it away: it is then not sent to). */
	public static void layout(final ResourceKey<Level> ground, final @Nullable Layout layout) {
		if (layout == null) {
			LAYOUTS.remove(ground);
		} else {
			LAYOUTS.put(ground, layout);
		}
	}

	/** How {@code ground} is laid out, if it is ready. */
	public static @Nullable Layout layout(final ResourceKey<Level> ground) {
		return LAYOUTS.get(ground);
	}

	/** The name of a ground, for the words. */
	public static Component name(final ResourceKey<Level> ground) {
		return Component.translatable("ground." + ground.identifier().getNamespace() + "." + ground.identifier().getPath());
	}

	/** A ground's standing. */
	public static Ground ground(final MinecraftServer server, final ResourceKey<Level> ground) {
		return server.overworld().getAttachedOrElse(GROUNDS, Map.of()).getOrDefault(ground, Ground.NONE);
	}

	private static void set(final MinecraftServer server, final ResourceKey<Level> ground, final Ground standing) {
		Map<ResourceKey<Level>, Ground> all = new LinkedHashMap<>(server.overworld().getAttachedOrElse(GROUNDS, Map.of()));
		all.put(ground, standing);
		server.overworld().setAttached(GROUNDS, Map.copyOf(all));
	}

	// ── setting out ──────────────────────────────────────────────

	/** {@code leader} sets out for {@code ground} from the hub they stand at; whoever is still by it when the count is done goes too. */
	public static Result muster(final ServerPlayer leader, final ResourceKey<Level> ground) {
		MinecraftServer server = leader.level().getServer();
		if (WorldMode.of(server) == WorldMode.TRAINING || leader.level().dimension() != Level.OVERWORLD) {
			return Result.ELSEWHERE;
		}
		if (server.overworld().getDifficulty() == Difficulty.PEACEFUL) {
			return Result.PEACEFUL;
		}
		if (leader.hasAttached(AWAY)) {
			return Result.AWAY;
		}
		if (DownService.isDown(leader)) {
			return Result.DOWN;
		}
		Layout layout = LAYOUTS.get(ground);
		ServerLevel there = server.getLevel(ground);
		if (layout == null || there == null) {
			return Result.NOT_READY;
		}
		Settlements.Settlement home = HomesteadTravel.homeHere(server, leader);
		if (home == null) {
			return Result.NOT_HOME;
		}
		GlobalPos hub = GlobalPos.of(Level.OVERWORLD, home.hub());
		for (Muster muster : MUSTERS) {
			if (muster.hub().equals(hub)) {
				return Result.BUSY;
			}
		}
		long now = server.overworld().getGameTime();
		prepare(there, layout);
		// the ground's chunks load while the party gathers, so what is in them can be swept when it goes
		hold(there, layout, now + musterTicks + HOLD_AFTER);
		MUSTERS.add(new Muster(leader.getUUID(), ground, hub, now + musterTicks, false));
		Component word = Component.translatable("message.mistinn.expedition.muster", leader.getDisplayName(), name(ground),
			Math.max(1, musterTicks / 20)).withStyle(ChatFormatting.AQUA);
		for (ServerPlayer player : near(server.overworld(), hub.pos())) {
			player.sendSystemMessage(word);
		}
		return Result.MUSTERING;
	}

	/** The hunters standing by a hub: in its level (the same numbers in a ground are another place), alive, near enough. */
	private static List<ServerPlayer> near(final ServerLevel level, final BlockPos hub) {
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - (hub.getX() + 0.5);
			double dz = player.getZ() - (hub.getZ() + 0.5);
			if (player.isAlive() && !player.isSpectator() && dx * dx + dz * dz <= REACH * REACH && Math.abs(player.getY() - hub.getY()) < 8.0) {
				near.add(player);
			}
		}
		return near;
	}

	private static void depart(final MinecraftServer server, final Muster muster) {
		ServerLevel from = server.getLevel(muster.hub().dimension());
		ServerLevel there = server.getLevel(muster.ground());
		Layout layout = LAYOUTS.get(muster.ground());
		ServerPlayer leader = server.getPlayerList().getPlayer(muster.leader());
		if (from == null || there == null || layout == null) {
			return;
		}
		if (server.overworld().getDifficulty() == Difficulty.PEACEFUL) {
			if (leader != null) {
				leader.sendSystemMessage(Component.translatable("message.mistinn.expedition.refused.peaceful").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (BUILDS.containsKey(muster.ground())) {
			// the ground is not built yet: the party waits by the hub, and is told so once
			long now = server.overworld().getGameTime();
			hold(there, layout, now + WAIT + HOLD_AFTER);
			MUSTERS.add(new Muster(muster.leader(), muster.ground(), muster.hub(), now + WAIT, true));
			if (!muster.waited()) {
				Component word = Component.translatable("message.mistinn.expedition.preparing", name(muster.ground())).withStyle(ChatFormatting.GRAY);
				for (ServerPlayer player : near(from, muster.hub().pos())) {
					player.sendSystemMessage(word);
				}
			}
			return;
		}
		List<ServerPlayer> party = new ArrayList<>();
		for (ServerPlayer player : near(from, muster.hub().pos())) {
			if (!player.hasAttached(AWAY) && !DownService.isDown(player)) {
				party.add(player);
			}
		}
		if (party.isEmpty()) {
			if (leader != null) {
				leader.sendSystemMessage(Component.translatable("message.mistinn.expedition.nobody").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		Ground standing = ground(server, muster.ground());
		if (standing.inside().isEmpty()) {
			standing = restock(there, layout, standing);
		}
		long now = server.overworld().getGameTime();
		List<BlockPos> starts = layout.starts();
		for (int i = 0; i < party.size(); i++) {
			ServerPlayer hunter = party.get(i);
			BlockPos start = starts.get(i % starts.size());
			Away away = new Away(muster.ground(), muster.hub(), now + LIMIT, standing.serial(), 0L);
			hunter.teleportTo(there, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, Set.of(), hunter.getYRot(), 0.0F, true);
			hunter.setAttached(AWAY, away);
			standing = standing.in(hunter.getUUID(), away.ends());
			protect(hunter);
			hunter.sendSystemMessage(Component.translatable("message.mistinn.expedition.arrived", name(muster.ground()),
				LIMIT / 1200L).withStyle(ChatFormatting.AQUA));
		}
		set(server, muster.ground(), standing);
	}

	/**
	 * Sweeps a ground nobody is in and stocks it again: every creature and thing lying loose in it goes, and every
	 * gravestone; whoever left one there and is here now has their map mark taken away (the rest, when they come back).
	 */
	private static Ground restock(final ServerLevel level, final Layout layout, final Ground standing) {
		BoundingBox box = layout.bounds();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				LevelChunk chunk = level.getChunk(cx, cz);
				List<BlockPos> stones = new ArrayList<>();
				for (BlockEntity entity : chunk.getBlockEntities().values()) {
					if (entity instanceof GravestoneBlockEntity) {
						stones.add(entity.getBlockPos());
					}
				}
				for (BlockPos stone : stones) {
					level.setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
		for (Entity entity : level.getEntities((Entity) null, AABB.of(box).inflate(16.0), e -> !(e instanceof Player))) {
			entity.discard();
		}
		List<UUID> stale = new ArrayList<>(standing.stale());
		for (UUID hunter : standing.graved()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(hunter);
			if (player != null) {
				forget(player, level.dimension());
			} else if (!stale.contains(hunter)) {
				stale.add(hunter);
			}
		}
		layout.stock(level, level.getRandom());
		return new Ground(true, standing.serial() + 1, standing.inside(), List.of(), List.copyOf(stale));
	}

	/** Takes away a hunter's map mark if it points into {@code ground}: the stone it marked has been swept. */
	private static void forget(final ServerPlayer player, final ResourceKey<Level> ground) {
		DeathMark mark = player.getAttached(MistinnAttachments.DEATH_MARK);
		if (mark != null && mark.pos().dimension().equals(ground)) {
			player.removeAttached(MistinnAttachments.DEATH_MARK);
		}
	}

	/** Begins building a ground not built yet (once: a build already going goes on). */
	private static void prepare(final ServerLevel level, final Layout layout) {
		if (!ground(level.getServer(), level.dimension()).built() && !BUILDS.containsKey(level.dimension())) {
			BUILDS.put(level.dimension(), new Build(layout.plan()));
		}
	}

	/** Goes on with the builds for as much of this tick as they may take, keeping their chunks loaded meanwhile. */
	private static void build(final MinecraftServer server, final long now) {
		long until = System.nanoTime() + BUDGET_NANOS;
		for (Iterator<Map.Entry<ResourceKey<Level>, Build>> it = BUILDS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<ResourceKey<Level>, Build> entry = it.next();
			ServerLevel level = server.getLevel(entry.getKey());
			Layout layout = LAYOUTS.get(entry.getKey());
			if (level == null || layout == null) {
				it.remove();
				continue;
			}
			hold(level, layout, now + HOLD_AFTER);
			Build build = entry.getValue();
			while (build.next < build.steps.size() && System.nanoTime() < until) {
				build.steps.get(build.next).accept(level);
				build.next++;
			}
			if (build.next >= build.steps.size()) {
				Ground standing = ground(server, entry.getKey());
				set(server, entry.getKey(), new Ground(true, standing.serial(), standing.inside(), standing.graved(), standing.stale()));
				it.remove();
			}
			if (System.nanoTime() >= until) {
				return;
			}
		}
	}

	private static void hold(final ServerLevel level, final Layout layout, final long until) {
		Long held = HELD.get(level.dimension());
		if (held == null) {
			BoundingBox box = layout.bounds();
			for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
				for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(cx, cz), 1);
				}
			}
		}
		HELD.put(level.dimension(), held == null ? until : Math.max(held, until));
	}

	private static void release(final ServerLevel level, final Layout layout) {
		BoundingBox box = layout.bounds();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				level.getChunkSource().removeTicketWithRadius(TICKET, new ChunkPos(cx, cz), 1);
			}
		}
	}

	// ── in a ground ──────────────────────────────────────────────

	private static void tick(final MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!MUSTERS.isEmpty()) {
			for (Muster muster : List.copyOf(MUSTERS)) {
				if (now >= muster.departs()) {
					MUSTERS.remove(muster);
					depart(server, muster);
				}
			}
		}
		if (!HELD.isEmpty()) {
			for (Iterator<Map.Entry<ResourceKey<Level>, Long>> it = HELD.entrySet().iterator(); it.hasNext();) {
				Map.Entry<ResourceKey<Level>, Long> held = it.next();
				ServerLevel level = server.getLevel(held.getKey());
				Layout layout = LAYOUTS.get(held.getKey());
				if (now >= held.getValue() || level == null || layout == null) {
					if (level != null && layout != null) {
						release(level, layout);
					}
					it.remove();
				}
			}
		}
		if (!BUILDS.isEmpty()) {
			build(server, now);
		}
		if (server.getTickCount() % 5 == 0) {
			for (ResourceKey<Level> key : Grounds.ALL) {
				ServerLevel level = server.getLevel(key);
				if (level != null) {
					// anyone in a ground, sent or brought in some other way, builds nothing there
					level.players().forEach(Expedition::protect);
				}
			}
			for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
				Away away = player.getAttached(AWAY);
				if (away != null) {
					watch(player, away, now);
				}
			}
		}
		if (server.getTickCount() % 20 == 0) {
			letGoOfTheOffline(server, now);
		}
	}

	/** Offline hunters whose time has run out are no longer counted in: the ground can be swept again. */
	private static void letGoOfTheOffline(final MinecraftServer server, final long now) {
		for (ResourceKey<Level> key : Grounds.ALL) {
			Ground standing = ground(server, key);
			Ground next = standing;
			for (Map.Entry<UUID, Long> entry : standing.inside().entrySet()) {
				if (server.getPlayerList().getPlayer(entry.getKey()) == null && now >= entry.getValue()) {
					next = next.out(entry.getKey());
				}
			}
			if (next != standing) {
				set(server, key, next);
			}
		}
	}

	private static void watch(final ServerPlayer player, final Away away, final long now) {
		if (player.level().dimension() != away.ground()) {
			// left by some other way (a story's turn, a command): the expedition is over for them
			leave(player, away);
			player.sendSystemMessage(Component.translatable("message.mistinn.expedition.gone", name(away.ground())).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!player.isAlive()) {
			return;
		}
		protect(player);
		long left = away.ends() - now;
		for (long warning : WARNINGS) {
			if (left <= warning && left + 5L > warning) {
				player.sendSystemMessage(Component.translatable("message.mistinn.expedition.left." + warning / 1200L).withStyle(ChatFormatting.GOLD));
			}
		}
		if (left <= 0L) {
			DownService.dieOutright(player, new DamageSource(player.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(LOST)));
			return;
		}
		Layout layout = LAYOUTS.get(away.ground());
		if (layout == null) {
			return;
		}
		int at = -1;
		List<Layout.Exit> exits = layout.exits();
		for (int i = 0; i < exits.size(); i++) {
			if (exits.get(i).holds(player)) {
				at = i;
				break;
			}
		}
		if (at < 0) {
			if (away.leaving() != 0L) {
				player.setAttached(AWAY, away.holding(0L));
				player.sendSystemMessage(Component.translatable("message.mistinn.expedition.exit.broken").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (away.leaving() == 0L) {
			player.setAttached(AWAY, away.holding(now));
			player.sendSystemMessage(Component.translatable("message.mistinn.expedition.exit.start", Math.max(1, exitTicks / 20))
				.withStyle(ChatFormatting.AQUA));
		} else if (now - away.leaving() >= exitTicks) {
			extract(player, away);
			return;
		}
		String key = away.ground().identifier() + "#" + at;
		Waves waves = WAVES.get(key);
		if (waves == null || now - waves.last() > WAVE_EVERY * 2L) {
			waves = new Waves(now - WAVE_EVERY, 0);
		}
		if (now - waves.last() >= WAVE_EVERY) {
			layout.wave(player.level(), exits.get(at), waves.count(), player.level().getRandom());
			waves = new Waves(now, waves.count() + 1);
		}
		WAVES.put(key, waves);
	}

	/** Out through an exit: back to the hub they set out from (or, if it is no more, the world's spawn). */
	private static void extract(final ServerPlayer player, final Away away) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = server.getLevel(away.from().dimension());
		BlockPos from = away.from().pos();
		boolean standing = level != null && Settlements.homesteads(server).stream().anyMatch(home -> home.hub().equals(from));
		BlockPos hub = from;
		if (!standing) {
			level = server.overworld();
			hub = level.getRespawnData().pos();
		}
		leave(player, away);
		player.teleportTo(level, hub.getX() + 2.5, hub.getY() + 1.0, hub.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
		player.sendSystemMessage(Component.translatable("message.mistinn.expedition.home", name(away.ground())).withStyle(ChatFormatting.AQUA));
	}

	/** The expedition is over for this hunter: no longer away, no longer counted in, free to build again. */
	private static void leave(final ServerPlayer player, final Away away) {
		player.removeAttached(AWAY);
		MinecraftServer server = player.level().getServer();
		Ground standing = ground(server, away.ground());
		if (standing.inside().containsKey(player.getUUID())) {
			set(server, away.ground(), standing.out(player.getUUID()));
		}
		player.getAbilities().mayBuild = !player.gameMode().isBlockPlacingRestricted();
	}

	/** In a ground the forebears' work does not break: no block is broken or placed there but in creative. */
	private static void protect(final ServerPlayer player) {
		if (Grounds.is(player.level()) && !player.isCreative() && player.getAbilities().mayBuild) {
			player.getAbilities().mayBuild = false;
		}
	}

	private static void died(final ServerPlayer player) {
		Away away = player.getAttached(AWAY);
		if (away == null) {
			return;
		}
		player.removeAttached(AWAY);
		MinecraftServer server = player.level().getServer();
		Ground standing = ground(server, away.ground()).out(player.getUUID());
		if (player.level().dimension() == away.ground() && !standing.graved().contains(player.getUUID())) {
			List<UUID> graved = new ArrayList<>(standing.graved());
			graved.add(player.getUUID());
			standing = new Ground(standing.built(), standing.serial(), standing.inside(), List.copyOf(graved), standing.stale());
		}
		set(server, away.ground(), standing);
	}

	/**
	 * A hunter comes back. One whose ground was swept while they were away has their map mark into it taken. One who
	 * logged out in a ground picks up where they were, if the ground is the one they left and their time is not up; if it
	 * is up (or the ground has been swept since, which only happens once it was), they fall where they stand, as anyone
	 * still inside when the time runs out: logging out is no way out.
	 */
	private static void joined(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		for (ResourceKey<Level> key : Grounds.ALL) {
			Ground standing = ground(server, key);
			if (standing.stale().contains(player.getUUID())) {
				forget(player, key);
				List<UUID> stale = new ArrayList<>(standing.stale());
				stale.remove(player.getUUID());
				set(server, key, new Ground(standing.built(), standing.serial(), standing.inside(), standing.graved(), List.copyOf(stale)));
			}
		}
		Away away = player.getAttached(AWAY);
		if (away == null) {
			return;
		}
		if (!player.isAlive() || player.level().dimension() != away.ground()) {
			player.removeAttached(AWAY);
			Ground standing = ground(server, away.ground());
			if (standing.inside().containsKey(player.getUUID())) {
				set(server, away.ground(), standing.out(player.getUUID()));
			}
			return;
		}
		Ground standing = ground(server, away.ground());
		long now = server.overworld().getGameTime();
		if (standing.serial() != away.serial() || now >= away.ends()) {
			DownService.dieOutright(player, new DamageSource(player.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(LOST)));
			return;
		}
		if (!standing.inside().containsKey(player.getUUID())) {
			set(server, away.ground(), standing.in(player.getUUID(), away.ends()));
		}
		protect(player);
	}

	// ── for tests ────────────────────────────────────────────────

	/** For tests: the hunter's time in their ground runs out in {@code ticks}. */
	public static void hasten(final ServerPlayer player, final long ticks) {
		Away away = player.getAttached(AWAY);
		if (away != null) {
			long ends = player.level().getServer().overworld().getGameTime() + ticks;
			player.setAttached(AWAY, new Away(away.ground(), away.from(), ends, away.serial(), away.leaving()));
			Ground standing = ground(player.level().getServer(), away.ground());
			set(player.level().getServer(), away.ground(), standing.in(player.getUUID(), ends));
		}
	}

	/** For tests: builds {@code level}'s ground now, all of it at once (or only its first {@code steps} steps, built or not). */
	public static void buildNow(final ServerLevel level, final int steps) {
		Layout layout = LAYOUTS.get(level.dimension());
		List<Consumer<ServerLevel>> plan = layout.plan();
		for (int i = 0; i < Math.min(steps, plan.size()); i++) {
			plan.get(i).accept(level);
		}
		if (steps >= plan.size()) {
			BUILDS.remove(level.dimension());
			Ground standing = ground(level.getServer(), level.dimension());
			set(level.getServer(), level.dimension(), new Ground(true, standing.serial(), standing.inside(), standing.graved(), standing.stale()));
		}
	}

	/** For tests: whether a ground is being built. */
	public static boolean building(final ResourceKey<Level> ground) {
		return BUILDS.containsKey(ground);
	}

	/** For tests: forgets that a ground was built, so the next party builds it again. */
	public static void unbuild(final MinecraftServer server, final ResourceKey<Level> ground) {
		Ground standing = ground(server, ground);
		set(server, ground, new Ground(false, standing.serial(), standing.inside(), standing.graved(), standing.stale()));
	}

	/** For tests: whether a party is setting out. */
	public static boolean mustering() {
		return !MUSTERS.isEmpty();
	}

	/** For tests: the grounds whose chunks are held loaded. */
	public static Set<ResourceKey<Level>> held() {
		return new HashSet<>(HELD.keySet());
	}
}
