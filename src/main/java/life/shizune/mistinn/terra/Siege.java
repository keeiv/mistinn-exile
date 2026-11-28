package life.shizune.mistinn.terra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * 晶潮: some nights the lattice under a homestead stirs, and the lifeless come up out of the
 * highland for its crystal fields, in three waves: crystal lizards at first, drakes with them
 * after. The hunters near the hub when it begins have a little warning; each wave comes when the
 * last is down (or a minute on regardless). Hold all three and every hunter near the hub has salt
 * and a crystal core of the tillers' thanks; at dawn whatever is left crumbles back into the ground.
 */
public final class Siege {
	/** The chance, on a normal world, that a night falls on a homestead with a hunter near its hub. */
	public static final float CHANCE = 0.25F;
	/** How near the hub a hunter must be as night falls, how near to share the thanks, and how far off before it gives up. */
	public static final double NEAR = 48.0;
	public static final double THANKS = 64.0;
	public static final double GONE = 96.0;
	/** How long the lattice stirs before the first wave, and how long a wave is given before the next comes anyway. */
	public static final int WARNING = 300;
	public static final int WAVE_LIMIT = 1200;
	/** How long nobody near before it gives up. */
	public static final int ABANDONED = 600;
	public static final int WAVES = 3;
	/** How far from the hub they come up. */
	public static final int RING_NEAR = 24;
	public static final int RING_FAR = 36;
	/** Each hunter's thanks. */
	public static final int SALT = 300;
	/** A homestead is left alone its first half day. */
	public static final long SETTLING = 12000L;
	/** Under the automated tests no siege comes of itself. */
	private static final boolean AUTOMATED = System.getProperty("fabric.client.gametest") != null;
	/** The mark every creature of a siege carries. */
	public static final String MARK = "mistinn.siege";

	/**
	 * One homestead's watch: the last night weighed, the wave now (0: none yet), when the first
	 * wave comes or the present one came (0: no siege), the creatures of the siege, and when a
	 * hunter was last near.
	 */
	public record Watch(int domain, long night, int wave, long at, List<UUID> foes, long seen) {
		public static final Codec<Watch> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Watch::domain),
			Codec.LONG.fieldOf("night").forGetter(Watch::night),
			Codec.INT.fieldOf("wave").forGetter(Watch::wave),
			Codec.LONG.fieldOf("at").forGetter(Watch::at),
			UUIDUtil.CODEC.listOf().optionalFieldOf("foes", List.of()).forGetter(Watch::foes),
			Codec.LONG.optionalFieldOf("seen", 0L).forGetter(Watch::seen)
		).apply(i, Watch::new));

		public boolean on() {
			return this.at != 0L;
		}

		Watch idle() {
			return new Watch(this.domain, this.night, 0, 0L, List.of(), 0L);
		}
	}

	public static final AttachmentType<List<Watch>> WATCHES = AttachmentRegistry.<List<Watch>>builder()
		.persistent(Watch.CODEC.listOf())
		.buildAndRegister(Mistinn.id("homestead_sieges"));

	private Siege() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				tick(server);
			}
		});
		// one of a siege that is over (left unloaded when it ended) crumbles as it comes back into the world
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(MARK) && !fighting(level.getServer(), entity.getUUID())) {
				entity.discard();
			}
		});
	}

	/** Whether this creature is one of a siege still going on. */
	public static boolean fighting(final MinecraftServer server, final UUID id) {
		for (Watch watch : server.overworld().getAttachedOrElse(WATCHES, List.of())) {
			if (watch.on() && watch.foes().contains(id)) {
				return true;
			}
		}
		return false;
	}

	/** Night by the clock (a daytime storm is not night). */
	public static boolean night(final ServerLevel level) {
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return time >= 13000L && time < 23000L;
	}

	/** Whether all the ground a siege's creatures come up on is in the world. */
	private static boolean loaded(final ServerLevel level, final BlockPos hub) {
		int reach = RING_FAR + 16;
		for (int dx = -reach; dx <= reach; dx += 16) {
			for (int dz = -reach; dz <= reach; dz += 16) {
				if (!level.isLoaded(hub.offset(dx, 0, dz))) {
					return false;
				}
			}
		}
		return true;
	}

	public static Watch watch(final MinecraftServer server, final int domain) {
		for (Watch watch : server.overworld().getAttachedOrElse(WATCHES, List.of())) {
			if (watch.domain() == domain) {
				return watch;
			}
		}
		return new Watch(domain, -1L, 0, 0L, List.of(), 0L);
	}

	private static void set(final MinecraftServer server, final Watch changed) {
		List<Watch> all = new ArrayList<>(server.overworld().getAttachedOrElse(WATCHES, List.of()));
		all.removeIf(watch -> watch.domain() == changed.domain());
		all.add(changed);
		server.overworld().setAttached(WATCHES, List.copyOf(all));
	}

	/** The chance on this world that a night is a siege night for a homestead. */
	public static float chance(final MinecraftServer server) {
		return switch (Hardship.of(server)) {
			case EASY -> CHANCE * 0.6F;
			case NORMAL -> CHANCE;
			case RUST_ANCHOR -> CHANCE * 1.6F;
			case HUNTER -> CHANCE;
		};
	}

	private static void tick(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		long night = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		for (Settlements.Settlement home : Settlements.homesteads(server)) {
			Watch watch = watch(server, home.domain());
			if (watch.on()) {
				watch = go(level, home, watch, now);
				set(server, watch);
				continue;
			}
			if (night(level) && watch.night() != night) {
				// each night is weighed once, as it falls
				watch = new Watch(home.domain(), night, 0, 0L, List.of(), 0L);
				set(server, watch);
				List<ServerPlayer> there = near(level, home.hub(), NEAR);
				// a hunter who took its guard brings the tide that night; otherwise it may come, as it will
				boolean called = Commissions.guarding(there, home.domain());
				if (allowed(level, home) && !there.isEmpty() && (called || !AUTOMATED && level.getRandom().nextFloat() < chance(server))) {
					begin(level, home, WARNING);
				}
			}
		}
	}

	private static boolean allowed(final ServerLevel level, final Settlements.Settlement home) {
		return level.getDifficulty() != Difficulty.PEACEFUL && WorldMode.of(level.getServer()) != WorldMode.TRAINING
			&& level.getGameTime() - home.litAt() >= SETTLING;
	}

	private static List<ServerPlayer> near(final ServerLevel level, final BlockPos hub, final double reach) {
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(hub.getX() + 0.5, player.getY(), hub.getZ() + 0.5) <= reach * reach) {
				near.add(player);
			}
		}
		return near;
	}

	/** The lattice under a homestead stirs: the first wave comes in so many ticks. */
	public static void begin(final ServerLevel level, final Settlements.Settlement home, final int warning) {
		long now = level.getGameTime();
		Watch watch = watch(level.getServer(), home.domain());
		long night = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		set(level.getServer(), new Watch(home.domain(), Math.max(watch.night(), night), 0, now + Math.max(1, warning), List.of(), now));
		BlockPos hub = home.hub();
		level.playSound(null, hub, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3.0F, 0.45F);
		for (ServerPlayer player : near(level, hub, THANKS * 1.5)) {
			player.sendSystemMessage(Component.translatable("message.mistinn.siege.stirs").withStyle(ChatFormatting.AQUA));
		}
	}

	/** A moment of a siege: on to the next wave, the thanks, or its end. */
	private static Watch go(final ServerLevel level, final Settlements.Settlement home, final Watch watch, final long now) {
		boolean watched = !near(level, home.hub(), GONE).isEmpty();
		long seen = watched ? now : watch.seen();
		if (!night(level)) {
			crumble(level, home, watch, true);
			return watch.idle();
		}
		if (now - seen > ABANDONED) {
			crumble(level, home, watch, false);
			return watch.idle();
		}
		if (!watched || !loaded(level, home.hub())) {
			// nobody to hold it, or the ground round the hub not all there: it waits; what is not in the world is not counted gone
			return new Watch(watch.domain(), watch.night(), watch.wave(), watch.at(), watch.foes(), seen);
		}
		List<UUID> standing = watch.foes().stream().filter(id -> level.getEntity(id) instanceof Mob mob && mob.isAlive()).toList();
		// whoever of it has lost its quarry goes for the nearest hunter again, and does not lie down
		List<ServerPlayer> hunters = near(level, home.hub(), GONE);
		for (UUID id : standing) {
			if (level.getEntity(id) instanceof Mob mob && mob.getTarget() == null) {
				hunters.stream().filter(h -> !h.isCreative()).min(java.util.Comparator.comparingDouble(h -> h.distanceToSqr(mob))).ifPresent(mob::setTarget);
			}
		}
		if (watch.wave() == 0) {
			return now >= watch.at() ? wave(level, home, watch, 1, standing, now, seen) : new Watch(watch.domain(), watch.night(), 0, watch.at(), standing, seen);
		}
		if (standing.isEmpty()) {
			if (watch.wave() >= WAVES) {
				thank(level, home);
				return watch.idle();
			}
			return wave(level, home, watch, watch.wave() + 1, standing, now, seen);
		}
		if (watch.wave() < WAVES && now - watch.at() > WAVE_LIMIT) {
			return wave(level, home, watch, watch.wave() + 1, standing, now, seen);
		}
		return new Watch(watch.domain(), watch.night(), watch.wave(), watch.at(), standing, seen);
	}

	/** How many lizards, and drakes, in a wave. */
	public static int lizards(final int wave, final int hunters) {
		return 3 + wave + Math.max(0, hunters - 1);
	}

	public static int drakes(final int wave) {
		return wave - 1;
	}

	/** A wave comes up out of the ground round the hub. */
	private static Watch wave(final ServerLevel level, final Settlements.Settlement home, final Watch watch, final int wave, final List<UUID> standing,
		final long now, final long seen) {
		BlockPos hub = home.hub();
		List<ServerPlayer> hunters = near(level, hub, GONE);
		List<UUID> foes = new ArrayList<>(standing);
		RandomSource random = level.getRandom();
		for (int i = 0; i < lizards(wave, hunters.size()); i++) {
			BlockPos at = ground(level, hub, random);
			if (at == null) {
				continue;
			}
			CrystalLizard lizard = MistinnEntities.CRYSTAL_LIZARD.create(level, EntitySpawnReason.EVENT);
			if (lizard != null) {
				lizard.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
				rise(level, lizard, hunters, foes);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.VEIN_STONE.defaultBlockState()),
					at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 24, 0.4, 0.3, 0.4, 0.15);
				level.playSound(null, at, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.2F, 0.7F);
			}
		}
		for (int i = 0; i < drakes(wave); i++) {
			BlockPos at = ground(level, hub, random);
			CrystalDrake drake = MistinnEntities.CRYSTAL_DRAKE.create(level, EntitySpawnReason.EVENT);
			if (at != null && drake != null) {
				drake.snapTo(at.getX() + 0.5, at.getY() + 18.0, at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
				drake.home(hub);
				rise(level, drake, hunters, foes);
			}
		}
		level.playSound(null, hub, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3.0F, 0.6F + 0.15F * wave);
		for (ServerPlayer player : near(level, hub, THANKS * 1.5)) {
			player.sendSystemMessage(Component.translatable("message.mistinn.siege.wave", wave, WAVES).withStyle(ChatFormatting.AQUA));
		}
		return new Watch(watch.domain(), watch.night(), wave, now, List.copyOf(foes), seen);
	}

	/** One of the siege takes the world, kept, and goes for the nearest hunter. */
	private static void rise(final ServerLevel level, final Mob mob, final List<ServerPlayer> hunters, final List<UUID> foes) {
		mob.setPersistenceRequired();
		ServerPlayer nearest = null;
		for (ServerPlayer hunter : hunters) {
			if (!hunter.isCreative() && (nearest == null || hunter.distanceToSqr(mob) < nearest.distanceToSqr(mob))) {
				nearest = hunter;
			}
		}
		if (nearest != null) {
			mob.setTarget(nearest);
		}
		level.addFreshEntity(mob);
		// marked once in the world (the mark is for when it comes back into it later)
		mob.addTag(MARK);
		foes.add(mob.getUUID());
	}

	/** Somewhere on open ground a way off the hub, or nowhere. */
	private static @Nullable BlockPos ground(final ServerLevel level, final BlockPos hub, final RandomSource random) {
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int reach = RING_NEAR + random.nextInt(RING_FAR - RING_NEAR + 1);
			int x = Mth.floor(hub.getX() + Math.cos(angle) * reach);
			int z = Mth.floor(hub.getZ() + Math.sin(angle) * reach);
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (Math.abs(at.getY() - hub.getY()) > 16 || !level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()) {
				continue;
			}
			return at;
		}
		return null;
	}

	/** All three held: every hunter near the hub has salt and a core, and the tillers are glad. */
	private static void thank(final ServerLevel level, final Settlements.Settlement home) {
		BlockPos hub = home.hub();
		level.sendParticles(ParticleTypes.END_ROD, hub.getX() + 0.5, hub.getY() + 1.5, hub.getZ() + 0.5, 80, 1.5, 2.0, 1.5, 0.08);
		level.playSound(null, hub, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2.0F, 1.2F);
		for (Tiller tiller : level.getEntitiesOfClass(Tiller.class, new net.minecraft.world.phys.AABB(hub).inflate(Settlements.HOME + 8))) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, tiller.getX(), tiller.getY() + 2.0, tiller.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
		}
		int times = HomesteadUpgrades.thanks(level.getServer(), home.domain());
		for (ServerPlayer player : near(level, hub, THANKS)) {
			SaltService.add(player, SALT * times);
			life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.AchievementService.SIEGE_HELD);
			Commissions.held(player, home.domain());
			player.spawnAtLocation(level, new ItemStack(MistinnItems.CRYSTAL_CORE, times));
			player.sendSystemMessage(Component.translatable("message.mistinn.siege.held", SALT * times).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Dawn (or nobody left to hold it): what is left crumbles back into the ground. */
	private static void crumble(final ServerLevel level, final Settlements.Settlement home, final Watch watch, final boolean dawn) {
		for (UUID id : watch.foes()) {
			Entity foe = level.getEntity(id);
			if (foe != null && foe.isAlive()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState()),
					foe.getX(), foe.getY() + 0.5, foe.getZ(), 30, 0.4, 0.4, 0.4, 0.15);
				foe.discard();
			}
		}
		if (dawn) {
			for (ServerPlayer player : near(level, home.hub(), THANKS * 1.5)) {
				player.sendSystemMessage(Component.translatable("message.mistinn.siege.ebbs").withStyle(ChatFormatting.GRAY));
			}
		}
	}
}
