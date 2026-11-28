package life.shizune.mistinn.epic;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.udasru.UdasruBuild;
import life.shizune.mistinn.udasru.UdasruCrown;
import life.shizune.mistinn.udasru.UdasruPlaces;
import life.shizune.mistinn.udasru.UdasruSites;
import life.shizune.mistinn.world.Builder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import org.jspecify.annotations.Nullable;

/**
 * 史詩成篇: the epic of hunter No. 3412, the Rust Anchor's apostle, in Udasru. A world of the epic is made with the land
 * drawn ({@link life.shizune.mistinn.udasru.Udasru}); this keeps its road. It sets the world up when it is first opened (the
 * border, the Mist Inn and its people, the spawn at its door, and the Hunter's rule that a fall costs nothing), dresses
 * each hunter as 3412 the first time they come, and moves the epic on: out of the fog, up the first bell tower, into the
 * capital, through the four great bells and the rift over the city, out along the anchor's chain to its crown and the
 * Helm, to the name the epic is written under.
 */
public final class EpicService {
	/** How far the epic has come, on the overworld, known to every client. */
	public static final AttachmentType<EpicState> STATE = AttachmentRegistry.<EpicState>builder()
		.persistent(EpicState.CODEC)
		.syncWith(EpicState.STREAM_CODEC, AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("epic"));
	/** On a hunter: dressed and armed as 3412 already (kept through death). */
	public static final AttachmentType<Boolean> DRESSED = AttachmentRegistry.<Boolean>builder()
		.persistent(Codec.BOOL)
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("epic_dressed"));
	/** Where the Mist Inn was built, on the overworld. */
	public static final AttachmentType<BlockPos> INN = AttachmentRegistry.<BlockPos>builder()
		.persistent(BlockPos.CODEC)
		.buildAndRegister(Mistinn.id("epic_inn"));

	/** The tag the Helm carries. */
	public static final String HELM_TAG = "mistinn.epic_helm";
	/** How near a hunter must come to the crown's deck for the Helm to rise. */
	private static final double HELM_RISES = 16.0;
	/** Ticks after arriving before 3412 notices the hand on them. */
	private static final int HAND_AFTER = 80;
	/** How much bigger the great beasts stand in the epic, and how much more they take to bring down. */
	public static final double BEAST_SCALE = 1.6;
	public static final double BEAST_HEALTH = 2.0;

	/** Hunters waiting to notice the hand, and when (this server's run). */
	private static final Map<UUID, Long> HAND = new HashMap<>();

	private EpicService() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(NamePayload.TYPE, NamePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AskNamePayload.TYPE, AskNamePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(NamePayload.TYPE, (payload, context) -> named(context.player(), payload.name()));
		EpicScript.register();
		EpicTravel.register();
		LegacyService.register();
		// after the world mode is settled (WorldMode registers its own SERVER_STARTED first)
		ServerLifecycleEvents.SERVER_STARTED.register(EpicService::setup);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HAND.clear());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!active(server)) {
				return;
			}
			if (server.getTickCount() % 10 == 0) {
				tick(server);
			}
			if (server.getTickCount() % 20 == 5) {
				UdasruSites.tick(server);
			}
			// the people of Udasru call out as the hunter passes, as the station's do
			if (server.getTickCount() % 5 == 2) {
				for (ServerPlayer player : server.overworld().players()) {
					life.shizune.mistinn.station.StationBarks.tick(server.overworld(), player);
				}
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer hunter && level.getBlockState(hit.getBlockPos()).is(Blocks.BELL) && active(level.getServer())) {
				rang(hunter, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(EpicService::died);
	}

	// ── What a world of the epic is ───────────────────────────────

	public static boolean active(final @Nullable MinecraftServer server) {
		return server != null && WorldMode.of(server) == WorldMode.EPIC;
	}

	public static EpicState state(final MinecraftServer server) {
		return server.overworld().getAttachedOrElse(STATE, EpicState.FRESH);
	}

	private static void set(final MinecraftServer server, final EpicState state) {
		server.overworld().setAttached(STATE, state);
	}

	/** Whether rifts may tear on their own: anywhere but the epic, and in it once the rift over the capital has been held. */
	public static boolean allowsRifts(final MinecraftServer server) {
		return !active(server) || state(server).has(EpicState.RIFTED);
	}

	/** Whether the anchor's chain lies bare to walk. */
	public static boolean chainBare(final MinecraftServer server) {
		return state(server).has(EpicState.BRIDGE);
	}

	/** Whether this hunter walks as the Rust Anchor's apostle: in the epic, they are. */
	public static boolean apostle(final net.minecraft.world.entity.player.Player player) {
		MinecraftServer server = player.level().getServer();
		return active(server);
	}

	// ── The world's first opening ─────────────────────────────────

	private static void setup(final MinecraftServer server) {
		if (!active(server)) {
			return;
		}
		ServerLevel level = server.overworld();
		level.getWorldBorder().setCenter(0.0, 0.0);
		level.getWorldBorder().setSize(UdasruPlaces.BORDER);
		if (level.hasAttached(STATE)) {
			return;
		}
		// the Hunter's rule: a fall costs nothing, and a hunter wakes where they last rang a bell
		level.getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
		level.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, server);
		BlockPos door = buildInn(level);
		level.setRespawnData(new LevelData.RespawnData(GlobalPos.of(Level.OVERWORLD, door), 0.0F, 0.0F));
		set(server, EpicState.FRESH);
		Mistinn.LOGGER.info("Epic world set up: the Mist Inn at {}", door);
	}

	/** Builds the Mist Inn in the fog and seats its people; returns where a hunter stands at its door. */
	private static BlockPos buildInn(final ServerLevel level) {
		int x = UdasruPlaces.MIST_INN.getX();
		int z = UdasruPlaces.MIST_INN.getZ();
		for (int cx = (x >> 4) - 1; cx <= (x >> 4) + 1; cx++) {
			for (int cz = (z >> 4) - 1; cz <= (z >> 4) + 1; cz++) {
				level.getChunk(cx, cz);
			}
		}
		BlockPos site = new BlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1, z);
		Builder b = new Builder(level, site, RandomSource.create(level.getSeed() ^ 0x496E6E4CL));
		life.shizune.mistinn.udasru.UdasruInn.build(b);
		UdasruBuild.person(b, StaffRole.INNKEEPER, -4, 1, 2, 90.0F, 3);
		UdasruBuild.person(b, StaffRole.XIAOLING, -3, 1, -9, 0.0F, 4);
		UdasruBuild.person(b, StaffRole.ACHAO, 2, 1, 2, 180.0F, 3);
		UdasruBuild.person(b, StaffRole.ASHA, 4, 1, 8, 0.0F, 6);
		UdasruBuild.person(b, StaffRole.INN_GRANNY, -4, 1, 8, 0.0F, 5);
		UdasruBuild.person(b, StaffRole.INN_3411, 1, 1, 10, 180.0F, 6);
		level.setAttached(INN, site);
		return site.offset(life.shizune.mistinn.udasru.UdasruInn.DOOR);
	}

	// ── A hunter arrives ──────────────────────────────────────────

	private static void joined(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (!active(server)) {
			return;
		}
		if (!player.getAttachedOrElse(DRESSED, false)) {
			player.setAttached(DRESSED, true);
			EpicKit.give(player);
			BlockPos inn = server.overworld().getAttached(INN);
			if (inn != null) {
				BlockPos in = inn.offset(life.shizune.mistinn.udasru.UdasruInn.BED).above();
				player.teleportTo(server.overworld(), in.getX() + 0.5, in.getY(), in.getZ() + 0.5, java.util.Set.of(), 180.0F, 0.0F, true);
			}
			if (state(server).at() == EpicStep.WAKE) {
				ServerPlayNetworking.send(player, new StoryService.CardPayload(EpicScript.WAKE.id()));
				HAND.put(player.getUUID(), (long) server.getTickCount() + HAND_AFTER);
			}
		}
		life.shizune.mistinn.achievement.ApostleService.consider(player);
	}

	// ── The road ──────────────────────────────────────────────────

	private static void tick(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		EpicState state = state(server);
		long now = server.getTickCount();
		HAND.entrySet().removeIf(entry -> {
			if (entry.getValue() > now) {
				return false;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				int tone = life.shizune.mistinn.ledger.LedgerService.tone(player);
				play(player, (tone & 3) != 0 ? EpicScript.HAND_KIND : (tone & 12) != 0 ? EpicScript.HAND_CRUEL : EpicScript.HAND);
			}
			return true;
		});
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			state = state(server);
			switch (state.at()) {
				case WAKE -> {
					BlockPos inn = level.getAttached(INN);
					if (inn != null && player.blockPosition().distSqr(inn) > 30 * 30) {
						advance(server, EpicStep.FOG_WALL);
					}
				}
				case FOG_WALL -> {
					if (player.getZ() < UdasruPlaces.OUT_OF_THE_FOG_Z) {
						advance(server, EpicStep.FIRST_TOWER);
						cardAll(server, EpicScript.LAND);
					}
				}
				case CAPITAL -> {
					if (Math.hypot(player.getX() - UdasruPlaces.CAPITAL.getX(), player.getZ() - UdasruPlaces.CAPITAL.getZ()) < UdasruPlaces.CAPITAL_RADIUS) {
						advance(server, EpicStep.CHRONICLER);
						cardAll(server, EpicScript.CAPITAL);
					}
				}
				case CROWN -> {
					BlockPos deck = crownDeck(level);
					if (deck != null && !state.has(EpicState.HELM_RISEN) && player.distanceToSqr(deck.getX() + 0.5, deck.getY(), deck.getZ() + 0.5) < HELM_RISES * HELM_RISES) {
						raiseHelm(server, deck);
					}
				}
				default -> {
				}
			}
		}
		state = state(server);
		// the rift over the capital: opened at the second bell, held when it closes
		if (state.at() == EpicStep.BELLS && state.bellsRung() >= 2 && !state.has(EpicState.RIFTED) && !state.has(EpicState.RIFT_OPEN)) {
			openRift(server);
		} else if (state.at() == EpicStep.DEFEND && !riftOpen(server)) {
			// back to the bells (a step back on the road, so set rather than advanced)
			EpicState held = state(server).with(EpicState.RIFT_OPEN, false).with(EpicState.RIFTED, true);
			set(server, held.at(held.bellsRung() >= 4 ? EpicStep.GREAT_BELL : EpicStep.BELLS));
			playAll(server, EpicScript.RIFT_HELD);
		}
		// the beasts stand bigger in the epic
		for (UdasruPlaces.GreatBell bell : UdasruPlaces.GreatBell.values()) {
			grow(level, bell);
		}
	}

	/** Moves the epic on to a step, and tells everyone. */
	private static void advance(final MinecraftServer server, final EpicStep next) {
		EpicState state = state(server);
		if (!state.at().before(next)) {
			return;
		}
		set(server, state.at(next));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(Component.translatable("epic.mistinn.new_step", Component.translatable(next.key())).withStyle(ChatFormatting.AQUA), true);
		}
		Mistinn.LOGGER.info("Epic: {}", next.id);
	}

	// ── Bells ─────────────────────────────────────────────────────

	/** A hunter rang a bell: a tower's, a great bell's shrine, or the capital's. */
	private static void rang(final ServerPlayer player, final BlockPos at) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = server.overworld();
		if (player.level() != level) {
			return;
		}
		EpicState state = state(server);
		for (UdasruPlaces.Tower tower : UdasruPlaces.Tower.values()) {
			// the only bell at a tower's middle is the one in its belfry
			if (Math.abs(at.getX() - tower.x) <= 2 && Math.abs(at.getZ() - tower.z) <= 2) {
				// a hunter of the epic wakes here now, at the tower's door
				player.setRespawnPosition(new ServerPlayer.RespawnConfig(new LevelData.RespawnData(GlobalPos.of(Level.OVERWORLD,
					new BlockPos(tower.x, at.getY() - UdasruBuild.SHAFT - 5, tower.z + 6)), 0.0F, 0.0F), true), false);
				if (!state.rungTower(tower.bit())) {
					set(server, state.withTower(tower.bit()));
					level.playSound(null, at, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 3.0F, 1.0F);
					if (state.at() == EpicStep.FIRST_TOWER || state.towers() == 0) {
						play(player, EpicScript.FIRST_TOWER);
						advance(server, EpicStep.CAPITAL);
					} else {
						player.sendSystemMessage(Component.translatable("epic.mistinn.tower_rung", Component.translatable("epic.mistinn.tower." + tower.id))
							.withStyle(ChatFormatting.GOLD), true);
					}
				}
				return;
			}
		}
		for (UdasruPlaces.GreatBell bell : UdasruPlaces.GreatBell.values()) {
			int[] shrine = UdasruSites.shrinePlace(bell);
			if (Math.abs(at.getX() - shrine[0]) <= 6 && Math.abs(at.getZ() - shrine[1]) <= 7) {
				if ((state.beasts() & bell.bit()) == 0) {
					player.sendSystemMessage(Component.translatable("epic.mistinn.bell_held." + bell.id).withStyle(ChatFormatting.GRAY), true);
				} else if ((state.bells() & bell.bit()) == 0) {
					set(server, state.withBell(bell.bit()));
					for (ServerPlayer everyone : server.getPlayerList().getPlayers()) {
						everyone.level().playSound(null, everyone.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 0.6F);
					}
					playAll(server, EpicScript.bellRung(bell));
					if (state(server).bellsRung() >= 4 && state(server).at() == EpicStep.BELLS) {
						advance(server, EpicStep.GREAT_BELL);
						cardAll(server, EpicScript.BELLS_ALL);
					}
				}
				return;
			}
		}
		if (Math.hypot(at.getX() - UdasruPlaces.CAPITAL.getX(), at.getZ() - UdasruPlaces.CAPITAL.getZ()) <= 7 && at.getY() > UdasruSites.CAPITAL_Y + 30) {
			if (state.at() == EpicStep.GREAT_BELL && !state.has(EpicState.GREAT_BELL)) {
				set(server, state.with(EpicState.GREAT_BELL, true).with(EpicState.BRIDGE, true));
				for (ServerPlayer everyone : server.getPlayerList().getPlayers()) {
					everyone.level().playSound(null, everyone.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.0F, 0.5F);
				}
				playAll(server, EpicScript.GREAT_BELL_RUNG);
				advance(server, EpicStep.CROWN);
				cardAll(server, EpicScript.CROWN);
			}
		}
	}

	// ── Beasts, the rift and the Helm ─────────────────────────────

	/** The beast a great bell's ground is kept by, and the type it is. */
	private static net.minecraft.world.entity.EntityType<?> beast(final UdasruPlaces.GreatBell bell) {
		return switch (bell) {
			case SALT -> MistinnEntities.SUNKEN_BELL;
			case WOODS -> MistinnEntities.SNUFFER;
			case RUST -> MistinnEntities.RUST_WHEEL;
			case CRYSTAL -> MistinnEntities.CRYSTAL_ELDER;
		};
	}

	/** Makes the beasts near a great bell's ground the epic's size, once each. */
	private static void grow(final ServerLevel level, final UdasruPlaces.GreatBell bell) {
		if (!level.isLoaded(new BlockPos(bell.x, 64, bell.z))) {
			return;
		}
		net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(bell.x - 80, level.getMinY(), bell.z - 80, bell.x + 80, level.getMaxY(), bell.z + 80);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, around, e -> e.getType() == beast(bell) && !e.entityTags().contains("mistinn.epic_grown"))) {
			living.addTag("mistinn.epic_grown");
			var scale = living.getAttribute(Attributes.SCALE);
			if (scale != null) {
				scale.setBaseValue(BEAST_SCALE);
			}
			var health = living.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(health.getBaseValue() * BEAST_HEALTH);
				living.setHealth(living.getMaxHealth());
			}
		}
	}

	private static void died(final LivingEntity entity, final DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || !active(level.getServer()) || level != level.getServer().overworld()) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (entity.entityTags().contains(HELM_TAG)) {
			set(server, state(server).with(EpicState.HELM_RISEN, false));
			playAll(server, EpicScript.HELM_DOWN);
			advance(server, EpicStep.NAME);
			cardAll(server, EpicScript.NAME_CARD);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ServerPlayNetworking.send(player, new AskNamePayload(true));
			}
			return;
		}
		for (UdasruPlaces.GreatBell bell : UdasruPlaces.GreatBell.values()) {
			if (entity.getType() == beast(bell) && Math.hypot(entity.getX() - bell.x, entity.getZ() - bell.z) < 300 && (state(server).beasts() & bell.bit()) == 0) {
				set(server, state(server).withBeast(bell.bit()));
				playAll(server, EpicScript.beastDown(bell));
				return;
			}
		}
	}

	/** The rift tears beside the capital, and the epic turns back to hold it. */
	private static void openRift(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos at = new BlockPos(UdasruPlaces.CAPITAL.getX() + 190, 0, UdasruPlaces.CAPITAL.getZ() + 40);
		life.shizune.mistinn.rift.RiftService.tearAt(level, at);
		set(server, state(server).with(EpicState.RIFT_OPEN, true));
		playAll(server, EpicScript.RIFT);
		EpicState state = state(server);
		set(server, state.at(EpicStep.DEFEND));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(Component.translatable("epic.mistinn.new_step", Component.translatable(EpicStep.DEFEND.key())).withStyle(ChatFormatting.RED), true);
		}
	}

	private static boolean riftOpen(final MinecraftServer server) {
		for (Domain domain : DomainService.of(server, Domain.Kind.RIFT)) {
			if (domain.open() && domain.distance(UdasruPlaces.CAPITAL.getX(), UdasruPlaces.CAPITAL.getZ()) < 400) {
				return true;
			}
		}
		return false;
	}

	/** Where the crown's deck is, if its chunk is loaded. */
	private static @Nullable BlockPos crownDeck(final ServerLevel level) {
		BlockPos crown = UdasruPlaces.ANCHOR_CROWN;
		if (!level.isLoaded(crown) || !UdasruSites.built(level.getServer()).contains("crown")) {
			return null;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, crown.getX() + 3, crown.getZ() + 3);
		return new BlockPos(crown.getX(), top, crown.getZ());
	}

	/** The Helm stands up on the crown's deck: hunter No. 0001, the anchor's voice, and the last to be fought. */
	private static void raiseHelm(final MinecraftServer server, final BlockPos deck) {
		ServerLevel level = server.overworld();
		var helm = MistinnEntities.RUST_APOSTLE.create(level, EntitySpawnReason.EVENT);
		if (helm == null) {
			return;
		}
		helm.addTag(HELM_TAG);
		helm.becomeHelm();
		helm.snapTo(deck.getX() + 0.5, deck.getY(), deck.getZ() - 4.5, 0.0F, 0.0F);
		level.addFreshEntity(helm);
		set(server, state(server).with(EpicState.HELM_RISEN, true));
		advance(server, EpicStep.HELM);
		playAll(server, EpicScript.HELM_RISES);
		cardAll(server, EpicScript.HELM_CARD);
	}

	// ── The name ──────────────────────────────────────────────────

	/** The longest name the epic takes. */
	public static final int NAME_LENGTH = 12;

	private static void named(final ServerPlayer player, final String given) {
		MinecraftServer server = player.level().getServer();
		if (!active(server) || state(server).at() != EpicStep.NAME) {
			return;
		}
		String name = clean(given);
		if (name.isEmpty()) {
			return;
		}
		set(server, state(server).named(name));
		advance(server, EpicStep.ENDED);
		playAll(server, EpicScript.LET_GO);
		cardAll(server, EpicScript.HOME);
		// the seal that carries their pack to another world
		for (ServerPlayer hunter : server.getPlayerList().getPlayers()) {
			net.minecraft.world.item.ItemStack seal = new net.minecraft.world.item.ItemStack(life.shizune.mistinn.registry.MistinnItems.LEGACY_SEAL);
			// into the hunter's pack as anything they pick up goes, else their pockets, else at their feet
			life.shizune.mistinn.grid.GridService.insert(hunter, seal);
			if (!seal.isEmpty() && !hunter.getInventory().add(seal)) {
				hunter.drop(seal, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		Mistinn.LOGGER.info("Epic written as {}", name);
	}

	/** A name as the epic takes it: no formatting, no control characters, at most {@link #NAME_LENGTH} characters. */
	public static String clean(final String given) {
		String plain = ChatFormatting.stripFormatting(given);
		StringBuilder out = new StringBuilder();
		(plain == null ? "" : plain).codePoints().filter(c -> c != '§' && !Character.isISOControl(c)).limit(NAME_LENGTH).forEach(out::appendCodePoint);
		return out.toString().strip();
	}

	// ── Talk ──────────────────────────────────────────────────────

	/** What one of the inn's or Udasru's people opens with: the epic's word for where it stands, or a greeting. */
	public static String opening(final ServerPlayer hunter, final StaffRole role) {
		EpicStep at = state(hunter.level().getServer()).at();
		return switch (role) {
			case INNKEEPER -> at == EpicStep.WAKE ? "wake" : at == EpicStep.ENDED ? "home" : "hello";
			case CHRONICLER -> at == EpicStep.CHRONICLER ? "saga_first" : at == EpicStep.ENDED ? "written" : "hello";
			case REGENT -> at == EpicStep.REGENT ? "plea" : at == EpicStep.ENDED ? "thanks" : "hello";
			case XIAOLING -> at == EpicStep.ENDED ? "home" : "hello";
			default -> "hello";
		};
	}

	/** A hunter heard someone out: the epic moves on where that was what it waited for. */
	public static void heard(final ServerPlayer hunter, final StaffRole role, final String topic) {
		MinecraftServer server = hunter.level().getServer();
		if (!active(server)) {
			return;
		}
		EpicStep at = state(server).at();
		if (role == StaffRole.INNKEEPER && "wake".equals(topic) && at == EpicStep.WAKE) {
			advance(server, EpicStep.FOG_WALL);
		} else if (role == StaffRole.CHRONICLER && "saga_first".equals(topic) && at == EpicStep.CHRONICLER) {
			advance(server, EpicStep.REGENT);
		} else if (role == StaffRole.REGENT && "plea".equals(topic) && at == EpicStep.REGENT) {
			advance(server, EpicStep.BELLS);
		}
	}

	// ── Telling ───────────────────────────────────────────────────

	private static void play(final ServerPlayer player, final String sequence) {
		ServerPlayNetworking.send(player, new StoryService.LinesPayload(sequence, -1, ""));
	}

	private static void playAll(final MinecraftServer server, final String sequence) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			play(player, sequence);
		}
	}

	private static void cardAll(final MinecraftServer server, final life.shizune.mistinn.story.StoryScript.Card card) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, new StoryService.CardPayload(card.id()));
		}
	}

	/** Client to server: the name the epic is written under. */
	public record NamePayload(String name) implements CustomPacketPayload {
		public static final Type<NamePayload> TYPE = new Type<>(Mistinn.id("epic_name"));
		public static final StreamCodec<ByteBuf, NamePayload> CODEC = ByteBufCodecs.stringUtf8(64).map(NamePayload::new, NamePayload::name);

		@Override
		public Type<NamePayload> type() {
			return TYPE;
		}
	}

	/** Server to client: ask for the epic's name (once the card before it has been read). */
	public record AskNamePayload(boolean ask) implements CustomPacketPayload {
		public static final Type<AskNamePayload> TYPE = new Type<>(Mistinn.id("epic_ask_name"));
		public static final StreamCodec<ByteBuf, AskNamePayload> CODEC = ByteBufCodecs.BOOL.map(AskNamePayload::new, AskNamePayload::ask);

		@Override
		public Type<AskNamePayload> type() {
			return TYPE;
		}
	}

	/** A hunter reads one of the unsent letters: it is read aloud, and counted found. */
	public static void letterRead(final ServerPlayer player, final int number) {
		MinecraftServer server = player.level().getServer();
		play(player, "epic_letter_" + number);
		if (!active(server)) {
			return;
		}
		EpicState state = state(server);
		int bit = 1 << number;
		if ((state.letters() & bit) == 0) {
			set(server, state.withLetter(bit));
			if (Integer.bitCount(state(server).letters()) >= UnsentLetterItem.LETTERS) {
				play(player, EpicScript.LETTERS_ALL);
			}
		}
	}

	/** Test hook: the epic looks at its hunters now, as it does every half second. */
	public static void look(final MinecraftServer server) {
		tick(server);
	}

	/** Test hook: a hunter rings the bell at this place, as using it would. */
	public static void ring(final ServerPlayer player, final BlockPos at) {
		rang(player, at);
	}

	/** Test hook: the name a hunter gives the epic, as the Chronicler's screen would send it. */
	public static void name(final ServerPlayer player, final String given) {
		named(player, given);
	}

	/** For tests: the steps in order. */
	public static List<EpicStep> road() {
		return List.of(EpicStep.values());
	}

	/** For tests: the crown's chain length. */
	public static int chain() {
		return UdasruCrown.CHAIN;
	}
}
