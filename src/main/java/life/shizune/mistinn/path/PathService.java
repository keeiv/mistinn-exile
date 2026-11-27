package life.shizune.mistinn.path;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.Siege;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * 憧憬的屍之道: a world played as one of the crystal tillers of a Terra highland, who has heard
 * that past the fog there is a sea. The tillers' elders say the lattice is the world's end.
 * <p>
 * The way out is walked one tiller after another: when the one walking it falls, the next of the
 * settlement takes up their longing and goes on from the homestead, with nothing but bread and a
 * stone spear (what the fallen carried waits at their grave). The settlement has twelve; when all
 * twelve have fallen the way is cut, and the world can only be watched.
 */
public final class PathService {
	/** How many the settlement has to walk the way. */
	public static final int PEOPLE = 12;
	/** Crystal grit the prologue asks for. */
	public static final int GRIT = 6;
	/** How far past a highland's edge the sea must be, to count as out. */
	public static final int BEYOND = 200;

	/** The way's objectives, in order: each in a chapter. */
	public enum Step {
		GRIT("grit", 0), LIGHT("light", 1), RUIN("ruin", 2), SIEGE("siege", 3), ELDER("elder", 4), SEA("sea", 5), ENDED("ended", 5), CUT("cut", 5);

		public final String id;
		public final int chapter;

		Step(final String id, final int chapter) {
			this.id = id;
			this.chapter = chapter;
		}

		public String key() {
			return "path.mistinn.step." + this.id;
		}
	}

	/**
	 * The way of this world: the highland it starts from, who has fallen on it (by their names'
	 * numbers), who walks it now, how far it has come, the night the tide was last called on the
	 * homestead, and whose strength the walker took up at a memorial (-1: none yet).
	 */
	public record Line(int domain, List<Integer> fallen, int walker, int step, long tide, int legacy) {
		public static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Line::domain),
			Codec.INT.listOf().optionalFieldOf("fallen", List.of()).forGetter(Line::fallen),
			Codec.INT.optionalFieldOf("walker", 0).forGetter(Line::walker),
			Codec.INT.optionalFieldOf("step", 0).forGetter(Line::step),
			Codec.LONG.optionalFieldOf("tide", -1L).forGetter(Line::tide),
			Codec.INT.optionalFieldOf("legacy", -1).forGetter(Line::legacy)
		).apply(i, Line::new));

		public Line(final int domain, final List<Integer> fallen, final int walker, final int step, final long tide) {
			this(domain, fallen, walker, step, tide, -1);
		}

		public Step stage() {
			return Step.values()[Math.min(this.step, Step.values().length - 1)];
		}

		public boolean over() {
			return this.stage() == Step.ENDED || this.stage() == Step.CUT;
		}

		Line at(final Step next) {
			return new Line(this.domain, this.fallen, this.walker, next.ordinal(), this.tide, this.legacy);
		}

		Line from(final int home) {
			return new Line(home, this.fallen, this.walker, this.step, this.tide, this.legacy);
		}

		Line called(final long night) {
			return new Line(this.domain, this.fallen, this.walker, this.step, night, this.legacy);
		}

		Line lost(final List<Integer> all, final int next, final Step stage) {
			// a new walker has taken up no one's strength yet
			return new Line(this.domain, List.copyOf(all), next, stage.ordinal(), this.tide, -1);
		}

		Line taking(final int fallenWalker) {
			return new Line(this.domain, this.fallen, this.walker, this.step, this.tide, fallenWalker);
		}
	}

	/**
	 * What a hunter's client is told of the way: its step, how many have fallen, whose turn it is,
	 * the walker's strength and flaw, and the strength taken up at a memorial (-1: none).
	 */
	public record View(int step, int fallen, int walker, int strength, int flaw, int legacy) {
		public static final StreamCodec<io.netty.buffer.ByteBuf, View> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, View::step, ByteBufCodecs.VAR_INT, View::fallen, ByteBufCodecs.VAR_INT, View::walker,
			ByteBufCodecs.VAR_INT, View::strength, ByteBufCodecs.VAR_INT, View::flaw, ByteBufCodecs.INT, View::legacy, View::new);

		public Step stage() {
			return Step.values()[Math.min(this.step, Step.values().length - 1)];
		}
	}

	/** The way's end, for a hunter's screen: seen (the sea) or cut, the fallen in the order they fell, and the one who walked it last. */
	public record EndPayload(boolean sea, List<Integer> fallen, int walker) implements CustomPacketPayload {
		public static final Type<EndPayload> TYPE = new Type<>(Mistinn.id("path_end"));
		public static final StreamCodec<io.netty.buffer.ByteBuf, EndPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, EndPayload::sea, ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), EndPayload::fallen, ByteBufCodecs.VAR_INT, EndPayload::walker,
			EndPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final AttachmentType<Line> LINE = AttachmentRegistry.<Line>builder()
		.persistent(Line.CODEC)
		.buildAndRegister(Mistinn.id("path_line"));
	public static final AttachmentType<View> VIEW = AttachmentRegistry.<View>builder()
		.syncWith(View.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("path_view"));
	/** On a hunter: brought to the homestead already (kept through death: a successor comes to it by being born there). */
	public static final AttachmentType<Boolean> PLACED = AttachmentRegistry.<Boolean>builder()
		.persistent(Codec.BOOL)
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("path_placed"));
	/** On a hunter: shown the way's end already (one away when it came is shown it on their return). */
	public static final AttachmentType<Boolean> SAW_END = AttachmentRegistry.<Boolean>builder()
		.persistent(Codec.BOOL)
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("path_saw_end"));
	/** Ticks at the least between one step of the way and the next, so chapters come one at a time. */
	public static final int PACE = 200;
	/** When the way last moved on (this server's run), and whether a way with nowhere to start was said. */
	private static long movedAt = -PACE;
	private static boolean stranded;

	private PathService() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(EndPayload.TYPE, EndPayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(PathService::begin);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(handler.getPlayer()));
		// kneeling at a memorial (sneaking, using it) takes up the fallen one's strength
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer hunter) || !hunter.isShiftKeyDown() || hand != net.minecraft.world.InteractionHand.MAIN_HAND
				|| !active(hunter.level().getServer())
				|| !(level.getBlockEntity(hit.getBlockPos()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			int fallen = memorialOf(sign);
			return fallen >= 0 && inherit(hunter, fallen) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0 && active(server)) {
				tick(server);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			MinecraftServer server = newPlayer.level().getServer();
			if (!active(server)) {
				return;
			}
			if (!alive && oldPlayer.getAttachedOrElse(PLACED, false)) {
				fallen(newPlayer);
			} else if (!alive) {
				// fell before ever reaching the homestead (on the way down to it): brought out over it again
				Line waiting = line(server);
				if (waiting != null) {
					bringOver(newPlayer, waiting);
				}
			}
			Line line = line(server);
			if (line != null) {
				show(newPlayer, line);
			}
		});
	}

	public static boolean active(final @Nullable MinecraftServer server) {
		return server != null && WorldMode.of(server) == WorldMode.PATH;
	}

	/**
	 * Whether the crown elder of a highland keeps to its sleep: the home highland's, until the way
	 * comes to it (a tiller fresh from the fields would not live through its first swoop).
	 */
	public static boolean holdsElder(final MinecraftServer server, final int domain) {
		Line line = active(server) ? line(server) : null;
		return line != null && line.domain() == domain && line.stage().ordinal() < Step.ELDER.ordinal();
	}

	/** Whether this hunter walks the way (on either side: their client is told). */
	public static boolean walking(final Player player) {
		if (player.level().isClientSide()) {
			return player.hasAttached(VIEW);
		}
		return active(player.level().getServer());
	}

	public static @Nullable Line line(final MinecraftServer server) {
		return server.overworld().getAttached(LINE);
	}

	private static void set(final MinecraftServer server, final Line line) {
		server.overworld().setAttached(LINE, line);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			show(player, line);
		}
	}

	private static void show(final ServerPlayer player, final Line line) {
		long seed = PathTraits.seed(player, line);
		int legacy = line.legacy() >= 0 ? PathTraits.strength(seed, line.legacy()).ordinal() : -1;
		player.setAttached(VIEW, new View(line.step(), line.fallen().size(), line.walker(), PathTraits.strength(seed, line.walker()).ordinal(),
			PathTraits.flaw(seed, line.walker()).ordinal(), legacy));
		// who the hunter is now: their strength, flaw and any strength taken up (nothing, once the way is cut)
		if (line.stage() == Step.CUT) {
			PathTraits.strip(player);
		} else {
			PathTraits.apply(player, line);
		}
	}

	/**
	 * A walker kneels at the memorial of one who fell before them and takes up that one's strength:
	 * once in a walker's life, and not a strength they already have. False when the way is not
	 * walked here or the name is not one of the fallen.
	 */
	public static boolean inherit(final ServerPlayer hunter, final int fallenWalker) {
		MinecraftServer server = hunter.level().getServer();
		Line line = active(server) ? line(server) : null;
		if (line == null || line.over() || !line.fallen().contains(fallenWalker)) {
			return false;
		}
		long seed = PathTraits.seed(hunter, line);
		PathTraits.Strength theirs = PathTraits.strength(seed, fallenWalker);
		if (line.legacy() >= 0) {
			hunter.sendSystemMessage(Component.translatable("path.mistinn.inherit.already", name(line.legacy()),
				PathTraits.strength(seed, line.legacy()).title()).withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		if (theirs == PathTraits.strength(seed, line.walker())) {
			hunter.sendSystemMessage(Component.translatable("path.mistinn.inherit.same", name(fallenWalker), theirs.title())
				.withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		set(server, line.taking(fallenWalker));
		ServerLevel level = hunter.level();
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, hunter.getX(), hunter.getY() + 1.0, hunter.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, hunter.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		for (ServerPlayer each : server.getPlayerList().getPlayers()) {
			each.sendSystemMessage(Component.translatable("path.mistinn.inherit", name(line.walker()), name(fallenWalker), theirs.title())
				.withStyle(ChatFormatting.AQUA));
		}
		return true;
	}

	/** Whose memorial a sign is: the fallen walker its name belongs to, or -1. */
	public static int memorialOf(final net.minecraft.world.level.block.entity.SignBlockEntity sign) {
		var lines = sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false);
		if (lines.size() > 1 && lines.get(1).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents words
			&& words.getKey().startsWith("path.mistinn.name.")) {
			try {
				return Integer.parseInt(words.getKey().substring("path.mistinn.name.".length()));
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		return -1;
	}

	/** The world starts: the way begins at the highland nearest the world's spawn. */
	private static void begin(final MinecraftServer server) {
		movedAt = -PACE;
		stranded = false;
		if (!active(server) || line(server) != null) {
			return;
		}
		Domain home = nearest(server, -1);
		if (home == null) {
			Mistinn.LOGGER.warn("A path world with no Terra highland to start from");
			return;
		}
		server.overworld().setAttached(LINE, new Line(home.id(), List.of(), 0, Step.GRIT.ordinal(), -1L));
		Mistinn.LOGGER.info("The path begins at highland {}", home.id());
	}

	/** The highland nearest the world's spawn whose settlement could stand (passing over one already found unable to). */
	private static @Nullable Domain nearest(final MinecraftServer server, final int passing) {
		BlockPos spawn = server.overworld().getRespawnData().pos();
		List<Integer> skipped = Settlements.ledger(server).skipped();
		return DomainService.of(server, Domain.Kind.TERRA).stream()
			.filter(d -> d.id() != passing && !skipped.contains(d.id()))
			.min(Comparator.comparingDouble(d -> d.distance(spawn.getX(), spawn.getZ()))).orElse(null);
	}

	/** A hunter arrives: once, they are brought out to the highland, to wait for the settlement to stand. */
	private static void joined(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Line line = active(server) ? line(server) : null;
		if (line == null) {
			return;
		}
		show(player, line);
		if (!player.getAttachedOrElse(PLACED, false)) {
			bringOver(player, line);
		}
	}

	/** Out over the highland where the settlement will rise, drifting down. */
	private static void bringOver(final ServerPlayer player, final Line line) {
		MinecraftServer server = player.level().getServer();
		Domain home = DomainService.get(server, line.domain());
		if (home == null) {
			return;
		}
		ServerLevel level = server.overworld();
		BlockPos site = Settlements.site(home);
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, site.getX(), site.getZ());
		player.teleportTo(level, site.getX() + 0.5, Math.max(top, level.getSeaLevel()) + 30.0, site.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 4, false, false));
	}

	private static void tick(final MinecraftServer server) {
		Line line = line(server);
		if (line == null) {
			return;
		}
		if (!line.over()) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isAlive() && player.getAttachedOrElse(PLACED, false)) {
					PathTraits.darkness(player, line);
				}
			}
		}
		ServerLevel level = server.overworld();
		List<ServerPlayer> everyone = server.getPlayerList().getPlayers();
		Settlements.Settlement home = Settlements.of(server, line.domain());
		if (home == null && Settlements.ledger(server).skipped().contains(line.domain())) {
			// something already stood where the settlement would: the way starts from the next highland
			Domain next = nearest(server, line.domain());
			if (next == null) {
				if (!stranded) {
					stranded = true;
					Mistinn.LOGGER.warn("The path has no highland left whose settlement could stand");
				}
				return;
			}
			line = line.from(next.id());
			set(server, line);
			for (ServerPlayer player : everyone) {
				if (!player.getAttachedOrElse(PLACED, false)) {
					bringOver(player, line);
				}
			}
			return;
		}
		for (ServerPlayer player : everyone) {
			if (line.stage() == Step.CUT) {
				// the way was cut: whoever comes can only watch, as everyone does
				player.setAttached(PLACED, true);
				if (player.gameMode() == GameType.SURVIVAL || player.gameMode() == GameType.ADVENTURE) {
					player.setGameMode(GameType.SPECTATOR);
				}
			} else if (!player.getAttachedOrElse(PLACED, false) && home != null && !player.isSpectator() && player.isAlive()) {
				// the settlement stands: the hunter is one of its people, by the hub
				place(player, home);
				player.setAttached(PLACED, true);
				kit(player);
				if (!line.over()) {
					chapter(player, line.stage(), line, life.shizune.mistinn.story.StoryScript.PATH_FIELDS.id());
				}
			}
			if (line.over() && !player.getAttachedOrElse(SAW_END, false)) {
				// away (or not yet come) when the way ended: shown how it ended
				player.setAttached(SAW_END, true);
				ServerPlayNetworking.send(player, new EndPayload(line.stage() == Step.ENDED, line.fallen(), line.walker()));
			}
		}
		Step stage = line.stage();
		if (line.over()) {
			return;
		}
		long now = level.getGameTime();
		boolean paced = now - movedAt >= PACE || now < movedAt;
		for (ServerPlayer player : everyone) {
			if (!paced || player.isSpectator() || !player.isAlive() || !player.getAttachedOrElse(PLACED, false) || !done(server, line, home, player, stage)) {
				continue;
			}
			Step next = Step.values()[stage.ordinal() + 1];
			Line advanced = line.at(next);
			set(server, advanced);
			movedAt = now;
			for (ServerPlayer each : everyone) {
				if (next == Step.ENDED) {
					seaReached(each, advanced);
				} else {
					// out of the broken tower comes the ancestors' tablet
					chapter(each, next, advanced, next == Step.SIEGE ? life.shizune.mistinn.story.StoryScript.PATH_RECORD.id() : null);
				}
			}
			return;
		}
		// the tide comes for the homestead every night the way waits on it, while someone is there to hold it
		long night = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		if (stage == Step.SIEGE && home != null && home.lit() && line.tide() != night && Siege.night(level) && !Siege.watch(server, home.domain()).on()
			&& level.getNearestPlayer(home.hub().getX() + 0.5, home.hub().getY(), home.hub().getZ() + 0.5, Siege.NEAR, p -> !p.isSpectator()) != null) {
			set(server, line.called(night));
			Siege.begin(level, home, Siege.WARNING);
		}
	}

	/** Whether a hunter has done what the way asks now. */
	private static boolean done(final MinecraftServer server, final Line line, final Settlements.@Nullable Settlement home, final ServerPlayer player,
		final Step stage) {
		return switch (stage) {
			case GRIT -> count(player, MistinnItems.CRYSTAL_DUST) >= GRIT;
			case LIGHT -> home != null && home.lit();
			case RUIN -> AchievementService.has(player, Achievement.RUIN_OPENED);
			case SIEGE -> AchievementService.has(player, Achievement.SIEGE_HELD);
			case ELDER -> AchievementService.has(player, Achievement.ELDER_SLAIN);
			case SEA -> atSea(server, line, player);
			case ENDED, CUT -> false;
		};
	}

	/** Out past the highland, at the sea's edge. */
	public static boolean atSea(final MinecraftServer server, final Line line, final ServerPlayer player) {
		Domain home = DomainService.get(server, line.domain());
		if (home == null || player.level().dimension() != Level.OVERWORLD) {
			return false;
		}
		var biome = player.level().getBiome(player.blockPosition());
		return home.distance(player.getX(), player.getZ()) > home.radius() + BEYOND && (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH));
	}

	/** How many of an item a hunter has about them, in hand, pockets, rig or pack. */
	static int count(final ServerPlayer player, final Item item) {
		int count = player.getInventory().countItem(item);
		GridInventory grid = GridService.get(player);
		for (Area area : GridService.ORDER) {
			for (Placed placed : grid.items(area)) {
				if (placed.stack().is(item)) {
					count += placed.stack().getCount();
				}
			}
		}
		return count;
	}

	private static void place(final ServerPlayer player, final Settlements.Settlement home) {
		BlockPos at = home.hub().offset(2, 0, 0);
		player.teleportTo(player.level().getServer().overworld(), at.getX() + 0.5, home.deck() + 1.0, at.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F,
			true);
	}

	/** What a tiller sets out with: bread, and the stone spear they tend the fields with. */
	private static void kit(final ServerPlayer player) {
		for (ItemStack stack : List.of(new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.THRUST).get(WeaponMaterial.STONE)), new ItemStack(Items.BREAD, 6))) {
			ItemStack giving = stack.copy();
			if (!player.getInventory().add(giving)) {
				GridService.insert(player, giving);
			}
		}
	}

	/** How many things the settlement's people say to the one walking the way, and of those, the ones that remember the last to fall. */
	public static final int KIN = 6;
	private static final java.util.Set<Integer> REMEMBERING = java.util.Set.of(3, 4);

	/**
	 * What one of the settlement's people says to a hunter walking the way: they are kin, and the
	 * way is the settlement's. Nothing when the world is not walking it.
	 */
	public static @Nullable Component kin(final ServerPlayer player, final net.minecraft.util.RandomSource random) {
		Line line = active(player.level().getServer()) ? line(player.level().getServer()) : null;
		if (line == null || line.stage() == Step.CUT) {
			return null;
		}
		if (line.stage() == Step.ENDED) {
			return Component.translatable("path.mistinn.kin.ended", name(line.walker()));
		}
		int said = random.nextInt(KIN);
		if (line.fallen().isEmpty() && REMEMBERING.contains(said)) {
			// no one has fallen yet to remember
			said = 0;
		}
		Component last = line.fallen().isEmpty() ? Component.empty() : name(line.fallen().getLast());
		return Component.translatable("path.mistinn.kin." + said, name(line.walker()), last);
	}

	/** The name of the one walking the way now, or of one who did. */
	public static Component name(final int walker) {
		return Component.translatable("path.mistinn.name." + Math.floorMod(walker, PEOPLE));
	}

	/** A chapter opens: its title, and a few lines of what it is. */
	private static void chapter(final ServerPlayer player, final Step step, final Line line, final @Nullable String card) {
		if (card != null) {
			// a story card in place of the title: it covers the screen, and the panel shows the chapter after
			ServerPlayNetworking.send(player, new life.shizune.mistinn.story.StoryService.CardPayload(card));
		} else {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(15, 70, 30));
			player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("path.mistinn.title." + step.chapter)));
			player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(step.key())));
		}
		for (int i = 0; i < 2; i++) {
			player.sendSystemMessage(Component.translatable("path.mistinn.tell." + step.id + "." + i, name(line.walker())).withStyle(ChatFormatting.GRAY,
				ChatFormatting.ITALIC));
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** The one walking the way has fallen: the next of the settlement takes it up, or, the twelve gone, it is cut. */
	private static void fallen(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Line line = line(server);
		if (line == null || line.over()) {
			Settlements.Settlement home = line == null || line.stage() != Step.ENDED ? null : Settlements.of(server, line.domain());
			if (home != null) {
				place(player, home);
			}
			return;
		}
		List<Integer> fallen = new ArrayList<>(line.fallen());
		fallen.add(line.walker());
		// the way, laid in stone at the homestead: one more memorial on it
		Settlements.Settlement homestead = Settlements.of(server, line.domain());
		Domain highland = DomainService.get(server, line.domain());
		if (homestead != null && highland != null) {
			PathMemorials.raise(server.overworld(), homestead, highland, fallen.size() - 1, line.walker());
		}
		Component lost = name(line.walker());
		if (fallen.size() >= PEOPLE) {
			Line cut = line.lost(fallen, line.walker(), Step.CUT);
			set(server, cut);
			for (ServerPlayer each : server.getPlayerList().getPlayers()) {
				each.setGameMode(GameType.SPECTATOR);
				each.connection.send(new ClientboundSetTitlesAnimationPacket(20, 100, 40));
				each.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("path.mistinn.cut")));
				each.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("path.mistinn.cut.sub", lost)));
				each.sendSystemMessage(roll(cut).withStyle(ChatFormatting.GRAY));
				each.setAttached(SAW_END, true);
				AchievementService.grant(each, Achievement.TWELVE_SLAIN);
				ServerPlayNetworking.send(each, new EndPayload(false, cut.fallen(), cut.walker()));
			}
			return;
		}
		Line next = line.lost(fallen, line.walker() + 1, line.stage());
		set(server, next);
		Settlements.Settlement home = Settlements.of(server, line.domain());
		if (home != null) {
			place(player, home);
		}
		kit(player);
		for (ServerPlayer each : server.getPlayerList().getPlayers()) {
			each.connection.send(new ClientboundSetTitlesAnimationPacket(15, 70, 30));
			each.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("path.mistinn.fell", lost)));
			each.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("path.mistinn.takes_up", name(next.walker()))));
			each.sendSystemMessage(Component.translatable("path.mistinn.fell.line", lost, name(next.walker()), fallen.size(), PEOPLE).withStyle(ChatFormatting.GRAY));
		}
	}

	/** The sea, at last: the way's end, and the names of those it was walked over. */
	private static void seaReached(final ServerPlayer player, final Line line) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(30, 120, 50));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("path.mistinn.sea")));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("path.mistinn.sea.sub", line.fallen().size())));
		player.sendSystemMessage(Component.translatable("path.mistinn.sea.line", name(line.walker())).withStyle(ChatFormatting.AQUA));
		if (!line.fallen().isEmpty()) {
			player.sendSystemMessage(roll(line).withStyle(ChatFormatting.GRAY));
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.7F);
		if (!player.isSpectator()) {
			AchievementService.grant(player, Achievement.SEA_SEEN);
			Achievement honour = seaHonour(line);
			if (AchievementService.grant(player, honour) && honour == Achievement.THE_LAND) {
				// how many became the land the way was walked on
				player.sendSystemMessage(land(line.fallen().size()).copy().withStyle(ChatFormatting.GOLD));
			}
		}
		player.setAttached(SAW_END, true);
		ServerPlayNetworking.send(player, new EndPayload(true, line.fallen(), line.walker()));
	}

	/** What reaching the sea is remembered as: with no one fallen, 息壤 (the earth that grows of itself); else 大地, the fallen become the land. */
	public static Achievement seaHonour(final Line line) {
		return line.fallen().isEmpty() ? Achievement.XIRANG : Achievement.THE_LAND;
	}

	/** 大地, in words that weigh how many it took: one, a few, half the settlement, nearly all of it. */
	public static Component land(final int fallen) {
		String weight = fallen <= 1 ? "one" : fallen <= 4 ? "few" : fallen <= 8 ? "half" : "most";
		return Component.translatable("achievement.mistinn.the_land.desc." + weight, fallen);
	}

	/** The names of those who fell on the way, in the order they fell. */
	private static MutableComponent roll(final Line line) {
		MutableComponent roll = Component.translatable("path.mistinn.roll");
		for (int i = 0; i < line.fallen().size(); i++) {
			roll.append(i == 0 ? " " : "、").append(name(line.fallen().get(i)));
		}
		return roll;
	}
}
