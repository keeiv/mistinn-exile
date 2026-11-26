package life.shizune.mistinn.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.bounty.BountyService;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * 空裂領域: now and then a rift tears open in the sky, a long walk from the hunters. Under it the
 * ground is seared and the sky turns vermilion. When a hunter comes close, what is inside falls
 * out in three waves, the last led by the rift's herald; when all of them are down the rift seals,
 * and the ground it scarred stays as it is, its own creatures walking it at night. Left alone, an
 * open rift widens every dawn and lets things out. A world holds only so many scars; when it is
 * full, a new rift tears an old scar open again.
 */
public final class RiftService {
	/** No rift before this day of the world. */
	public static final long FIRST_DAY = 4;
	/** How far an opened rift reaches, how much it widens each dawn left alone, and the most it ever reaches. */
	public static final int RADIUS = 40;
	public static final int WIDEN = 8;
	public static final int WIDEST = 64;
	/** How many scars a world holds before rifts only tear old ones open again. */
	public static final int SCARS = 12;
	/** How far from every hunter it tears open, and how near at least one of them. */
	public static final int NEAR = 96;
	public static final int FAR = 192;
	/** A hunter this near the middle brings the guardians down; with none this near, they go back up. */
	public static final double FIGHT = 48.0;
	public static final double LEAVE = 96.0;
	/** How long the guardians wait for a hunter who has walked off before going back. */
	public static final long PATIENCE = 1200L;
	public static final int WAVES = 3;
	/** What sealing it pays every hunter there, and what the herald's killer gets besides. */
	public static final int REWARD = 600;
	public static final int HERALD_BONUS = 400;
	/** How long after the sealing the rift is seen closing. */
	public static final long CLOSING = 100L;
	/** How high over the middle the rift hangs. */
	public static final int HEIGHT = 110;
	/** How often an open rift left alone lets a walker out, and how many it lets wander. */
	private static final long RELEASE = 1200L;
	private static final int RELEASED = 6;
	private static final DustParticleOptions BEAM = new DustParticleOptions(0xFF5A2A, 2.0F);

	/** The fight under a rift: which rift, which wave, who is down from it, when the next comes, and since when nobody has been near. */
	public record Battle(int domain, int wave, List<UUID> guardians, long next, long lonely) {
		public static final Codec<Battle> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Battle::domain),
			Codec.INT.fieldOf("wave").forGetter(Battle::wave),
			UUIDUtil.CODEC.listOf().fieldOf("guardians").forGetter(Battle::guardians),
			Codec.LONG.fieldOf("next").forGetter(Battle::next),
			Codec.LONG.fieldOf("lonely").forGetter(Battle::lonely)
		).apply(i, Battle::new));
	}

	/** The day last weighed for a rift, the moment of that day it is weighed, and the last day the open rifts widened. */
	public record Weighing(long day, long at, long widened) {
		public static final Weighing NONE = new Weighing(-1L, 0L, -1L);
		public static final Codec<Weighing> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Weighing::day),
			Codec.LONG.fieldOf("at").forGetter(Weighing::at),
			Codec.LONG.fieldOf("widened").forGetter(Weighing::widened)
		).apply(i, Weighing::new));
	}

	public static final AttachmentType<Battle> BATTLE = AttachmentRegistry.<Battle>builder()
		.persistent(Battle.CODEC)
		.buildAndRegister(Mistinn.id("rift_battle"));
	public static final AttachmentType<Weighing> WEIGHING = AttachmentRegistry.<Weighing>builder()
		.persistent(Weighing.CODEC)
		.buildAndRegister(Mistinn.id("rift_weighing"));
	/** On a creature that came down out of a rift to guard it. */
	public static final AttachmentType<Integer> GUARDS = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.buildAndRegister(Mistinn.id("rift_guards"));

	private static final boolean AUTOMATED = System.getProperty("fabric.client.gametest") != null;
	/** For tests: the next weighing tears a rift open, whatever the day, the hour and the odds. */
	public static boolean certain;

	private RiftService() {
	}

	public static void register() {
		DomainService.shaper(Domain.Kind.RIFT, new RiftShaper());
		ServerTickEvents.END_SERVER_TICK.register(RiftService::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(RiftService::died);
	}

	private static double chance(final MinecraftServer server) {
		return switch (Hardship.of(server)) {
			case EASY -> 0.20;
			case NORMAL -> 0.30;
			case RUST_ANCHOR -> 0.45;
		};
	}

	// ── Over the days ─────────────────────────────────────────────

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		long clock = level.getOverworldClockTime();
		long day = Math.floorDiv(clock, 24000L);
		long hour = Math.floorMod(clock, 24000L);
		Weighing weighing = level.getAttachedOrElse(WEIGHING, Weighing.NONE);
		if (weighing.day() != day) {
			// a new day, and a moment in it when the sky is weighed
			weighing = new Weighing(day, level.getRandom().nextInt(24000), weighing.widened());
			level.setAttached(WEIGHING, weighing);
		}
		if (weighing.widened() != day && !level.isDarkOutside()) {
			// each morning the rifts left open widen
			boolean first = weighing.widened() < 0;
			weighing = new Weighing(weighing.day(), weighing.at(), day);
			level.setAttached(WEIGHING, weighing);
			if (!first) {
				widen(server);
			}
		}
		if (certain || weighing.at() >= 0 && hour >= weighing.at()) {
			level.setAttached(WEIGHING, new Weighing(weighing.day(), -1L, weighing.widened()));
			if (certain || allowed(level, day) && level.getRandom().nextDouble() < chance(server)) {
				certain = false;
				tear(level);
			}
		}
		for (Domain domain : DomainService.of(server, Domain.Kind.RIFT)) {
			if (domain.open()) {
				watch(level, domain);
			}
		}
	}

	private static boolean allowed(final ServerLevel level, final long day) {
		return !AUTOMATED && day >= FIRST_DAY && level.getDifficulty() != Difficulty.PEACEFUL && WorldMode.of(level.getServer()) != WorldMode.TRAINING
			&& DomainService.of(level.getServer(), Domain.Kind.RIFT).stream().noneMatch(Domain::open);
	}

	/** Every rift left open with nobody fighting under it widens. */
	private static void widen(final MinecraftServer server) {
		for (Domain domain : DomainService.of(server, Domain.Kind.RIFT)) {
			if (domain.phase() == Domain.Phase.OPEN && domain.radius() < WIDEST) {
				DomainService.update(server, domain.radius(Math.min(WIDEST, domain.radius() + WIDEN)));
			}
		}
	}

	// ── Tearing open ──────────────────────────────────────────────

	/** A rift tears open somewhere a long walk from the hunters: a new one, or an old scar opened again once the world holds all it can. */
	public static @Nullable Domain tear(final ServerLevel level) {
		MinecraftServer server = level.getServer();
		List<Domain> scars = new ArrayList<>(DomainService.of(server, Domain.Kind.RIFT).stream().filter(d -> d.phase() == Domain.Phase.SCAR).toList());
		Domain domain = null;
		if (DomainService.of(server, Domain.Kind.RIFT).size() >= SCARS) {
			scars.removeIf(scar -> nearest(level, scar.centre()) < NEAR);
			if (!scars.isEmpty()) {
				domain = DomainService.enter(server, scars.get(level.getRandom().nextInt(scars.size())), Domain.Phase.OPEN);
			}
		} else {
			BlockPos centre = site(level);
			if (centre != null) {
				domain = DomainService.add(server, Domain.Kind.RIFT, centre, RADIUS, Domain.Phase.OPEN);
			}
		}
		if (domain != null) {
			announce(level, domain);
		}
		return domain;
	}

	/** Tears a rift open just here (for tests and commands). */
	public static Domain tearAt(final ServerLevel level, final BlockPos centre) {
		Domain domain = DomainService.add(level.getServer(), Domain.Kind.RIFT, ground(level, centre), RADIUS, Domain.Phase.OPEN);
		announce(level, domain);
		return domain;
	}

	private static double nearest(final ServerLevel level, final BlockPos at) {
		double nearest = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator()) {
				nearest = Math.min(nearest, Math.hypot(player.getX() - at.getX(), player.getZ() - at.getZ()));
			}
		}
		return nearest;
	}

	/** Somewhere on land, at least {@link #NEAR} from every hunter and no more than {@link #FAR} from one, clear of spawn and other domains. */
	private static @Nullable BlockPos site(final ServerLevel level) {
		List<ServerPlayer> hunters = level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
		if (hunters.isEmpty()) {
			return null;
		}
		RandomSource random = level.getRandom();
		BlockPos spawn = level.getRespawnData().pos();
		for (int i = 0; i < 24; i++) {
			ServerPlayer from = hunters.get(random.nextInt(hunters.size()));
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = NEAR + 8 + random.nextDouble() * (FAR - NEAR - 8);
			BlockPos column = BlockPos.containing(from.getX() + Math.cos(angle) * distance, 0.0, from.getZ() + Math.sin(angle) * distance);
			if (nearest(level, column) < NEAR || Math.hypot(column.getX() - spawn.getX(), column.getZ() - spawn.getZ()) < 200.0
				|| DomainService.crowded(level.getServer(), column, RADIUS, 48)) {
				continue;
			}
			BlockPos centre = ground(level, column);
			var biome = level.getBiome(centre);
			if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
				continue;
			}
			return centre;
		}
		return null;
	}

	private static BlockPos ground(final ServerLevel level, final BlockPos column) {
		// the middle's chunk is loaded (or made) to find the ground
		level.getChunk(column.getX() >> 4, column.getZ() >> 4);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
	}

	private static void announce(final ServerLevel level, final Domain domain) {
		for (ServerPlayer player : level.players()) {
			int distance = (int) Math.round(domain.distance(player.getX(), player.getZ()));
			player.sendSystemMessage(Component.translatable("message.mistinn.rift.torn", BountyService.direction(player, domain.centre()), distance)
				.withStyle(ChatFormatting.RED));
			// far thunder, for this hunter alone
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.LIGHTNING_BOLT_THUNDER), SoundSource.WEATHER,
				player.getX(), player.getY() + 20.0, player.getZ(), 0.8F, 0.5F, level.getRandom().nextLong()));
		}
	}

	// ── Under an open rift ────────────────────────────────────────

	private static void watch(final ServerLevel level, final Domain domain) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		Battle battle = level.getAttached(BATTLE);
		if (battle != null && battle.domain() != domain.id()) {
			battle = null;
		}
		double nearest = nearest(level, domain.centre());
		if (domain.phase() == Domain.Phase.OPEN) {
			if (nearest <= FIGHT && level.isLoaded(domain.centre())) {
				DomainService.enter(server, domain, Domain.Phase.BATTLE);
				level.setAttached(BATTLE, new Battle(domain.id(), 0, List.of(), now + 60, now));
				for (ServerPlayer player : level.players()) {
					if (domain.distance(player.getX(), player.getZ()) <= LEAVE) {
						player.sendSystemMessage(Component.translatable("message.mistinn.rift.falling").withStyle(ChatFormatting.RED));
					}
				}
			} else if ((now - domain.since()) % RELEASE < 10 && now - domain.since() >= RELEASE && level.isLoaded(domain.centre())) {
				release(level, domain);
			}
			return;
		}
		if (battle == null) {
			battle = new Battle(domain.id(), 0, List.of(), now + 60, now);
		}
		List<UUID> standing = new ArrayList<>();
		for (UUID id : battle.guardians()) {
			Entity guardian = level.getEntity(id);
			if (guardian instanceof LivingEntity living && living.isAlive()) {
				standing.add(id);
			}
		}
		long lonely = nearest <= LEAVE ? now : battle.lonely();
		if (now - lonely > PATIENCE) {
			// nobody came back: the guardians go up again, and the rift waits
			for (UUID id : standing) {
				Entity guardian = level.getEntity(id);
				if (guardian != null) {
					level.sendParticles(ParticleTypes.POOF, guardian.getX(), guardian.getY() + 1.0, guardian.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
					guardian.discard();
				}
			}
			level.removeAttached(BATTLE);
			DomainService.enter(server, domain, Domain.Phase.OPEN);
			return;
		}
		if (standing.isEmpty() && now >= battle.next()) {
			if (battle.wave() >= WAVES) {
				level.removeAttached(BATTLE);
				seal(level, domain);
				return;
			}
			int wave = battle.wave() + 1;
			standing.addAll(wave(level, domain, wave));
			battle = new Battle(domain.id(), wave, standing, now + 100, lonely);
		} else {
			battle = new Battle(domain.id(), battle.wave(), standing, standing.isEmpty() && battle.guardians().size() > standing.size() ? now + 80 : battle.next(), lonely);
		}
		level.setAttached(BATTLE, battle);
	}

	/** One wave down out of the rift: walkers, more for every hunter near; the last led by the herald. */
	private static List<UUID> wave(final ServerLevel level, final Domain domain, final int wave) {
		int hunters = (int) level.players().stream().filter(p -> !p.isSpectator() && domain.distance(p.getX(), p.getZ()) <= LEAVE).count();
		float scale = switch (Hardship.of(level.getServer())) {
			case EASY -> 0.75F;
			case NORMAL -> 1.0F;
			case RUST_ANCHOR -> 1.5F;
		};
		int walkers = Math.max(2, Math.round((2 + wave + Math.max(0, hunters - 1) * 2) * scale));
		List<UUID> down = new ArrayList<>();
		for (int i = 0; i < walkers; i++) {
			Mob walker = drop(level, domain, MistinnEntities.RIFT_WALKER);
			if (walker != null) {
				down.add(walker.getUUID());
			}
		}
		if (wave == WAVES) {
			Mob herald = drop(level, domain, MistinnEntities.RIFT_HERALD);
			if (herald != null) {
				down.add(herald.getUUID());
				for (ServerPlayer player : level.players()) {
					if (domain.distance(player.getX(), player.getZ()) <= LEAVE) {
						player.sendSystemMessage(Component.translatable("message.mistinn.rift.herald").withStyle(ChatFormatting.DARK_RED));
					}
				}
			}
		}
		level.playSound(null, domain.centre().getX(), domain.centre().getY() + 20, domain.centre().getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 4.0F, 0.6F);
		return down;
	}

	/** One creature comes down a beam of light from the rift onto the scarred ground near the middle. */
	private static <T extends Mob> @Nullable T drop(final ServerLevel level, final Domain domain, final EntityType<T> type) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < 12; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double reach = 4.0 + random.nextDouble() * 12.0;
			BlockPos column = BlockPos.containing(domain.centre().getX() + Math.cos(angle) * reach, 0, domain.centre().getZ() + Math.sin(angle) * reach);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()) {
				continue;
			}
			T mob = type.spawn(level, at, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			mob.setPersistenceRequired();
			mob.setAttached(GUARDS, domain.id());
			beam(level, at, domain.centre().getY() + HEIGHT);
			return mob;
		}
		return null;
	}

	/** A streak of the rift's light from on high down to where something lands. */
	private static void beam(final ServerLevel level, final BlockPos at, final int top) {
		for (int y = at.getY(); y < top; y += 2) {
			level.sendParticles(BEAM, true, true, at.getX() + 0.5, y, at.getZ() + 0.5, 1, 0.08, 0.6, 0.08, 0.0);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, true, true, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
	}

	/** Left alone, an open rift lets a walker out now and then, onto its own ground. */
	private static void release(final ServerLevel level, final Domain domain) {
		int out = level.getEntitiesOfClass(RiftWalker.class, new net.minecraft.world.phys.AABB(domain.centre()).inflate(domain.radius(), 64.0, domain.radius())).size();
		if (out < RELEASED) {
			drop(level, domain, MistinnEntities.RIFT_WALKER);
		}
	}

	// ── Sealing ───────────────────────────────────────────────────

	/** The last of them is down: the rift seals, and what it scarred stays. */
	public static void seal(final ServerLevel level, final Domain domain) {
		DomainService.enter(level.getServer(), domain, Domain.Phase.SCAR);
		LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(domain.centre().getX() + 0.5, domain.centre().getY(), domain.centre().getZ() + 0.5);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, domain.centre().getX(), domain.centre().getY() + 30, domain.centre().getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 6.0F, 0.5F);
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && domain.distance(player.getX(), player.getZ()) <= LEAVE) {
				SaltService.add(player, REWARD);
				AchievementService.grant(player, Achievement.RIFT_SEALED);
				player.sendSystemMessage(Component.translatable("message.mistinn.rift.sealed", REWARD).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static void died(final LivingEntity entity, final DamageSource source) {
		if (!(entity instanceof RiftHerald) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer player ? player
			: entity.getLastHurtByPlayer() instanceof ServerPlayer striker ? striker : null;
		if (killer == null) {
			return;
		}
		SaltService.add(killer, HERALD_BONUS);
		List<net.minecraft.world.item.Item> pieces = MistinnItems.gearOfTier(3 + level.getRandom().nextInt(3));
		entity.spawnAtLocation(level, new ItemStack(pieces.get(level.getRandom().nextInt(pieces.size()))));
	}

	/** The fight under a rift, if one is on. */
	public static Optional<Battle> battle(final MinecraftServer server) {
		return Optional.ofNullable(server.overworld().getAttached(BATTLE));
	}
}
