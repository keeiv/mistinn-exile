package life.shizune.mistinn.expedition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import life.shizune.mistinn.elite.EliteService;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.terra.CrystalDrake;
import life.shizune.mistinn.terra.CrystalLizard;
import life.shizune.mistinn.world.Builder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * 泰拉水壩: the dam the forebears raised across a highland canyon to hold its water back and water the valley below.
 *
 * <p>The canyon runs north to south (x east, z south); the dam stands across it. North of it the reservoir, its floor
 * high and its water still; south of it the riverbed, dry but for a trickle, where the party comes in beside the
 * forebears' observation hut. The way goes up the riverbed to the spillway at the dam's foot (one way out, near but back
 * where they came from), in through its side and up the maintenance stairs to the three corridors inside, along them to
 * the sluice control room in the third, up to the crest, across it and over the bridge to the intake tower standing in
 * the reservoir (the other way out, on its roof). Everything is measured from one riverbed: the canyon widens the same
 * a quarter block for every block up, north of the dam or south, so the crest meets the walls on both sides.
 *
 * <p>No route has a door: the creatures and the hunters go the same ways.
 */
public final class DamGround implements Layout {
	/** Top of the riverbed. */
	public static final int BED = 40;
	/** Top of the reservoir's floor. */
	public static final int SHORE = 96;
	/** The reservoir's surface. */
	public static final int WATER = 100;
	/** The dam's top. */
	public static final int CREST = 108;
	/** Top of the canyon walls. */
	public static final int RIM = 150;
	/** The dam's upstream face. */
	public static final int FACE = -12;
	/** The foot of its downstream face. */
	public static final int TOE = 12;
	/** The reservoir's far end. */
	public static final int NORTH = -120;
	/** The riverbed's far end. */
	public static final int SOUTH = 149;
	/** How thick the canyon's walls are. */
	public static final int WALL = 6;

	/** The control room, on the third corridor. */
	public static final BlockPos CONTROL = new BlockPos(0, 88, -6);
	/** The safe in it. */
	public static final BlockPos SAFE = new BlockPos(0, 88, -9);
	/** The way out at the spillway: near the way in. */
	public static final Exit SPILLWAY = new Exit(new BlockPos(0, 41, 8), 3.0);
	/** The way out on the intake tower's roof: far, but where the way goes. */
	public static final Exit TOWER = new Exit(new BlockPos(0, 119, -44), 3.5);
	/** Where a party comes in, beside the observation hut. */
	public static final List<BlockPos> STARTS = List.of(new BlockPos(12, 41, 124), new BlockPos(12, 41, 127), new BlockPos(14, 41, 124),
		new BlockPos(14, 41, 127));
	/** The crates lying overturned on the riverbed. */
	public static final List<BlockPos> CRATES = List.of(new BlockPos(-14, 41, 118), new BlockPos(22, 41, 104), new BlockPos(-20, 41, 92),
		new BlockPos(6, 41, 80), new BlockPos(-8, 41, 66), new BlockPos(18, 41, 58), new BlockPos(-24, 41, 46), new BlockPos(10, 41, 34),
		new BlockPos(-4, 41, 24), new BlockPos(26, 41, 28));
	/** The tool lockers along the corridors. */
	public static final List<BlockPos> LOCKERS = List.of(new BlockPos(-24, 56, -4), new BlockPos(0, 56, -4), new BlockPos(22, 56, -4),
		new BlockPos(-20, 72, -8), new BlockPos(6, 72, -8), new BlockPos(26, 72, -8), new BlockPos(18, 88, -4));

	private static final int FLAGS = Block.UPDATE_CLIENTS;
	/** The forebears' copper, waxed: it has gone green with the years, and goes no further. */
	private static final Block OX_CUT = Blocks.CUT_COPPER.waxed().oxidized();
	private static final Block OX_BLOCK = Blocks.COPPER_BLOCK.waxed().oxidized();
	private static final Block OX_GRATE = Blocks.COPPER_GRATE.waxed().oxidized();
	private static final Block WEATHERED = Blocks.COPPER_BLOCK.waxed().weathered();
	private static final Block ROD = Blocks.LIGHTNING_ROD.waxed().oxidized();
	private static final double FLARE = 0.25;

	@Override
	public BoundingBox bounds() {
		return new BoundingBox(-(open(0, RIM) + WALL + 12), BED - 8, NORTH - 8, open(0, RIM) + WALL + 12, RIM + 4, SOUTH + 8);
	}

	@Override
	public List<BlockPos> starts() {
		return STARTS;
	}

	@Override
	public List<Exit> exits() {
		return List.of(SPILLWAY, TOWER);
	}

	/** What the riverbed's crates hold. */
	public static final ResourceKey<LootTable> CRATE = loot("dam_crate");
	/** What the corridors' lockers hold. */
	public static final ResourceKey<LootTable> LOCKER = loot("dam_locker");
	/** What the control room's safe holds. */
	public static final ResourceKey<LootTable> STRONGBOX = loot("dam_safe");
	/** The hut's barrel. */
	public static final BlockPos HUT_BARREL = new BlockPos(25, BED + 1, 129);
	/** Where the lizards lie in wait, in threes on the riverbed and alone or in twos inside. */
	public static final List<BlockPos> LIZARDS = List.of(
		new BlockPos(-10, 41, 100), new BlockPos(-8, 41, 103), new BlockPos(-12, 41, 98),
		new BlockPos(16, 41, 70), new BlockPos(19, 41, 72), new BlockPos(14, 41, 67),
		new BlockPos(-14, 41, 40), new BlockPos(-11, 41, 37), new BlockPos(-17, 41, 42),
		new BlockPos(-3, 41, 6), new BlockPos(3, 41, 7),
		new BlockPos(-20, 56, -3), new BlockPos(10, 56, -3), new BlockPos(26, 56, 1), new BlockPos(-40, 64, -5),
		new BlockPos(-15, 72, -7), new BlockPos(5, 72, -7), new BlockPos(22, 72, -7), new BlockPos(40, 80, -5),
		new BlockPos(18, 88, -3), new BlockPos(26, 88, -3), new BlockPos(0, 88, -4),
		new BlockPos(-30, 109, -8), new BlockPos(30, 109, -8), new BlockPos(0, 109, -42), new BlockPos(-2, 119, -47));
	/** The control room's guards: elites. */
	public static final List<BlockPos> GUARDS = List.of(new BlockPos(-4, 88, -6), new BlockPos(4, 88, -6));
	/** The drakes keeping the crest, and where each comes back to. */
	public static final List<BlockPos> DRAKES = List.of(new BlockPos(-20, 114, -8), new BlockPos(20, 114, -8));

	private static ResourceKey<LootTable> loot(final String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, life.shizune.mistinn.Mistinn.id("expedition/" + name));
	}

	@Override
	public void stock(final ServerLevel level, final RandomSource random) {
		for (BlockPos crate : CRATES) {
			fill(level, crate, CRATE, random);
		}
		fill(level, HUT_BARREL, CRATE, random);
		for (BlockPos locker : LOCKERS) {
			fill(level, locker, LOCKER, random);
		}
		fill(level, SAFE, STRONGBOX, random);
		for (BlockPos at : LIZARDS) {
			lizard(level, at, random);
		}
		for (BlockPos at : GUARDS) {
			Mob guard = lizard(level, at, random);
			if (guard != null) {
				EliteService.make(guard, EliteService.pick(guard, random, 2));
				guard.setHealth(guard.getMaxHealth());
			}
		}
		for (BlockPos at : DRAKES) {
			CrystalDrake drake = MistinnEntities.CRYSTAL_DRAKE.create(level, EntitySpawnReason.STRUCTURE);
			if (drake != null) {
				drake.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
				drake.home(at);
				Expedition.harden(drake);
				level.addFreshEntity(drake);
			}
		}
	}

	private static void fill(final ServerLevel level, final BlockPos at, final ResourceKey<LootTable> table, final RandomSource random) {
		if (level.getBlockEntity(at) instanceof RandomizableContainerBlockEntity container) {
			container.clearContent();
			container.setLootTable(table, random.nextLong());
		}
	}

	private static @Nullable Mob lizard(final ServerLevel level, final BlockPos at, final RandomSource random) {
		CrystalLizard lizard = MistinnEntities.CRYSTAL_LIZARD.create(level, EntitySpawnReason.STRUCTURE);
		if (lizard == null) {
			return null;
		}
		lizard.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		Expedition.harden(lizard);
		level.addFreshEntity(lizard);
		return lizard;
	}

	/**
	 * A wave at a way out: two lizards and one more for every wave before (six at most) come up out of the ground a little
	 * way off, at whoever holds it; from the third wave at the tower, a drake with them.
	 */
	@Override
	public void wave(final ServerLevel level, final Exit exit, final int wave, final RandomSource random) {
		List<ServerPlayer> holding = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (exit.holds(player) && !player.isCreative() && !player.isSpectator()) {
				holding.add(player);
			}
		}
		int lizards = Math.min(6, 2 + wave);
		for (int i = 0; i < lizards; i++) {
			BlockPos at = ground(level, exit, random);
			if (at == null) {
				continue;
			}
			CrystalLizard lizard = MistinnEntities.CRYSTAL_LIZARD.create(level, EntitySpawnReason.EVENT);
			if (lizard != null) {
				lizard.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
				rise(level, lizard, holding);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SMOOTH_STONE.defaultBlockState()),
					at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 18, 0.4, 0.3, 0.4, 0.15);
			}
		}
		if (exit.equals(TOWER) && wave >= 2) {
			CrystalDrake drake = MistinnEntities.CRYSTAL_DRAKE.create(level, EntitySpawnReason.EVENT);
			if (drake != null) {
				BlockPos c = exit.centre();
				drake.snapTo(c.getX() + 12.5, c.getY() + 8.0, c.getZ() + 0.5, 0.0F, 0.0F);
				drake.home(c);
				rise(level, drake, holding);
			}
		}
		level.playSound(null, exit.centre(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.5F, 0.6F + 0.1F * Math.min(wave, 5));
	}

	private static void rise(final ServerLevel level, final Mob mob, final List<ServerPlayer> holding) {
		Expedition.harden(mob);
		ServerPlayer nearest = null;
		for (ServerPlayer player : holding) {
			if (nearest == null || player.distanceToSqr(mob) < nearest.distanceToSqr(mob)) {
				nearest = player;
			}
		}
		if (nearest != null) {
			mob.setTarget(nearest);
		}
		level.addFreshEntity(mob);
	}

	/** Somewhere to stand, seven to thirteen blocks from a way out and not far above or below it. */
	private static @Nullable BlockPos ground(final ServerLevel level, final Exit exit, final RandomSource random) {
		BlockPos c = exit.centre();
		for (int tries = 0; tries < 16; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double reach = 7.0 + random.nextDouble() * 6.0;
			int x = c.getX() + (int) Math.round(Math.cos(angle) * reach);
			int z = c.getZ() + (int) Math.round(Math.sin(angle) * reach);
			for (int y = c.getY() + 5; y >= c.getY() - 12; y--) {
				BlockPos at = new BlockPos(x, y, z);
				if (level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
					&& level.getFluidState(at).isEmpty() && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) {
					return at;
				}
			}
		}
		return null;
	}

	@Override
	public List<Consumer<ServerLevel>> plan() {
		List<Consumer<ServerLevel>> plan = new ArrayList<>();
		for (int z = NORTH - 5; z <= SOUTH + 5; z += 3) {
			int from = z;
			plan.add(level -> canyon(level, from, Math.min(from + 2, SOUTH + 5)));
		}
		int reach = open(0, CREST) + 10;
		for (int x = -reach; x <= reach; x += 8) {
			int from = x;
			plan.add(level -> dam(level, from, Math.min(from + 7, reach)));
		}
		plan.add(DamGround::buttresses);
		plan.add(DamGround::spillway);
		plan.add(DamGround::corridors);
		plan.add(DamGround::control);
		plan.add(DamGround::crest);
		plan.add(DamGround::bridge);
		plan.add(DamGround::tower);
		plan.add(DamGround::hut);
		plan.add(DamGround::riverbed);
		plan.add(DamGround::lights);
		for (int z = NORTH; z < FACE; z += 10) {
			int from = z;
			plan.add(level -> reservoir(level, from, Math.min(from + 9, FACE - 1)));
		}
		plan.add(DamGround::trickle);
		return plan;
	}

	// ── the measures ─────────────────────────────────────────────

	/** How far either way from the middle the canyon is open, at {@code z} and height {@code y}. */
	public static double half(final int z, final int y) {
		return 34.0 + 4.0 * Math.sin(z / 17.0) + (y - BED) * FLARE;
	}

	/** The last open column either way from the middle. */
	public static int open(final int z, final int y) {
		return (int) Math.floor(half(z, y));
	}

	/**
	 * The last open column on one side ({@code side} 1 east, -1 west), with the wall's own bulges and hollows: none near
	 * the dam, which meets walls as regular as itself.
	 */
	public static int edge(final int z, final int y, final int side) {
		double calm = Math.max(0.0, Math.min(1.0, (Math.abs(z) - 16) / 12.0));
		double bulge = (smooth(z / 9.0, y / 6.0, side > 0 ? 11 : 29) - 0.5) * 5.0 * calm;
		return (int) Math.floor(half(z, y) + bulge);
	}

	/** Noise that changes smoothly, between 0 and 1. */
	private static double smooth(final double a, final double b, final int salt) {
		int a0 = (int) Math.floor(a);
		int b0 = (int) Math.floor(b);
		double fa = a - a0;
		double fb = b - b0;
		fa = fa * fa * (3.0 - 2.0 * fa);
		fb = fb * fb * (3.0 - 2.0 * fb);
		double near = noise(a0, salt, b0) + (noise(a0 + 1, salt, b0) - noise(a0, salt, b0)) * fa;
		double far = noise(a0, salt, b0 + 1) + (noise(a0 + 1, salt, b0 + 1) - noise(a0, salt, b0 + 1)) * fa;
		return near + (far - near) * fb;
	}

	/** The last column of the dam's downstream face at height {@code y}. */
	public static int downstream(final int y) {
		return y <= BED ? TOE : (int) Math.floor(TOE - (y - BED) * 16.0 / 68.0);
	}

	/** Where the trickle runs, at {@code z}. */
	public static int trickle(final int z) {
		return (int) Math.round(5.0 * Math.sin(z / 23.0));
	}

	private static double noise(final int x, final int y, final int z) {
		long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
		h ^= h >>> 13;
		h *= 0x5bd1e995L;
		h ^= h >>> 15;
		return (h & 0xFFFFL) / 65536.0;
	}

	private static void put(final ServerLevel level, final int x, final int y, final int z, final BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, FLAGS);
	}

	private static void put(final ServerLevel level, final int x, final int y, final int z, final Block block) {
		put(level, x, y, z, block.defaultBlockState());
	}

	private static void air(final ServerLevel level, final int x0, final int y0, final int z0, final int x1, final int y1, final int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					put(level, x, y, z, Blocks.AIR);
				}
			}
		}
	}

	private static void fill(final ServerLevel level, final int x0, final int y0, final int z0, final int x1, final int y1, final int z1,
		final Block block) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					put(level, x, y, z, block);
				}
			}
		}
	}

	// ── the canyon ───────────────────────────────────────────────

	/** The canyon's rock: dark at the foot, then layer on layer, gently tilted. */
	private static Block rock(final int x, final int y, final int z) {
		double n = noise(x, y, z);
		double tilt = 2.5 * Math.sin(x / 11.0 + z / 17.0) + 1.5 * Math.sin(z / 7.0);
		if (y < BED + 14 + tilt) {
			return n < 0.25 ? Blocks.COBBLED_DEEPSLATE : Blocks.DEEPSLATE;
		}
		if (n < 0.05) {
			return Blocks.COBBLESTONE;
		}
		return switch (Math.floorMod((int) Math.floor((y + tilt) / 3.0), 9)) {
			case 2, 7 -> Blocks.ANDESITE;
			case 4, 8 -> Blocks.TUFF;
			case 6 -> n < 0.6 ? Blocks.CALCITE : Blocks.STONE;
			default -> n > 0.985 ? Blocks.DYED_TERRACOTTA.cyan() : Blocks.STONE;
		};
	}

	/** The riverbed: stones fallen at the walls' feet, mud by the trickle, and between, patches of earth, gravel and green. */
	private static Block bed(final int x, final int z, final int east, final int west) {
		double n = noise(x, BED, z);
		int talus = 2 + (int) (smooth(z / 5.0, x > 0 ? 1.0 : 7.0, 5) * 4.0);
		if (x > east - talus || x < -west + talus) {
			return n < 0.55 ? Blocks.GRAVEL : n < 0.85 ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE;
		}
		if (Math.abs(x - trickle(z) - 0.5) <= 2.5) {
			return n < 0.7 ? Blocks.MUD : Blocks.PACKED_MUD;
		}
		double patch = smooth(x / 7.0, z / 7.0, 3);
		if (patch < 0.28) {
			return Blocks.MUD;
		}
		if (patch < 0.45) {
			return n < 0.1 ? Blocks.GRAVEL : Blocks.COARSE_DIRT;
		}
		if (patch < 0.58) {
			return Blocks.PACKED_MUD;
		}
		if (patch < 0.68) {
			return Blocks.GRAVEL;
		}
		return n < 0.08 ? Blocks.COARSE_DIRT : life.shizune.mistinn.registry.MistinnBlocks.JADE_TURF;
	}

	private static Block lakebed(final int x, final int z) {
		double n = noise(x, SHORE, z);
		return n < 0.4 ? Blocks.CLAY : n < 0.65 ? Blocks.GRAVEL : n < 0.85 ? Blocks.MUD : Blocks.SAND;
	}

	/**
	 * The canyon from {@code z0} to {@code z1}: floor, walls, and beyond either end a wall right across. Where a wall
	 * steps back it leaves a ledge, green with the highland's turf; now and then a crystal grows out of its face.
	 */
	private static void canyon(final ServerLevel level, final int z0, final int z1) {
		for (int z = z0; z <= z1; z++) {
			boolean end = z < NORTH || z > SOUTH;
			int bottom = z < FACE ? SHORE : BED;
			for (int y = bottom - 4; y <= RIM; y++) {
				int east = edge(z, y, 1);
				int west = edge(z, y, -1);
				if (end) {
					for (int x = -(west + WALL); x <= east + WALL; x++) {
						put(level, x, y, z, rock(x, y, z));
					}
					continue;
				}
				if (y <= bottom) {
					for (int x = -west; x <= east; x++) {
						put(level, x, y, z, y < bottom ? rock(x, y, z) : z < FACE ? lakebed(x, z) : bed(x, z, east, west));
					}
				}
				wall(level, z, y, bottom, 1, east);
				wall(level, z, y, bottom, -1, west);
			}
		}
	}

	/** One side's wall at one height: its ledge (if it steps back above), and a crystal now and then on its face. */
	private static void wall(final ServerLevel level, final int z, final int y, final int bottom, final int side, final int edge) {
		boolean wet = z < FACE && y <= WATER + 1;
		int above = edge(z, y + 1, side);
		for (int d = edge + 1; d <= edge + WALL; d++) {
			int x = d * side;
			double n = noise(x, y, z);
			Block block = rock(x, y, z);
			boolean ledge = d <= above && y >= bottom && y < RIM;
			if (ledge) {
				block = n < 0.55 ? life.shizune.mistinn.registry.MistinnBlocks.JADE_TURF : n < 0.7 ? Blocks.MOSS_BLOCK : block;
				if (block == life.shizune.mistinn.registry.MistinnBlocks.JADE_TURF && !wet && noise(x, y + 1, z) < 0.35) {
					put(level, x, y + 1, z, noise(x, y + 2, z) < 0.8 ? life.shizune.mistinn.registry.MistinnBlocks.JADE_GRASS : life.shizune.mistinn.registry.MistinnBlocks.JADE_FERN);
				}
			}
			put(level, x, y, z, block);
			if (d == edge + 1 && !ledge && !wet && y > bottom + 2 && n > 0.992) {
				put(level, x - side, y, z, life.shizune.mistinn.registry.MistinnBlocks.TERRA_CRYSTAL_CLUSTER.defaultBlockState()
					.setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, side > 0 ? Direction.WEST : Direction.EAST));
			}
		}
	}

	// ── the dam ──────────────────────────────────────────────────

	private static Block concrete(final int x, final int y, final int z, final int south) {
		double n = noise(x, y, z);
		if (y == CREST) {
			return Math.floorMod(x, 6) == 0 ? Blocks.STONE_BRICKS : Blocks.POLISHED_ANDESITE;
		}
		if (z == FACE) {
			return n < 0.15 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS;
		}
		if (z >= south - 1) {
			if (Math.floorMod(x, 12) == 0) {
				return Blocks.POLISHED_ANDESITE;
			}
			if (y < BED + 10 && n < 0.4 - (y - BED) * 0.035) {
				return Blocks.MOSSY_STONE_BRICKS;
			}
			if (Math.floorMod(y, 8) == 0) {
				return Blocks.STONE_BRICKS;
			}
		}
		return Blocks.SMOOTH_STONE;
	}

	/** The dam's mass, from column {@code x0} to {@code x1}: right across the canyon, into its walls a block. */
	private static void dam(final ServerLevel level, final int x0, final int x1) {
		for (int x = x0; x <= x1; x++) {
			for (int y = BED - 4; y <= CREST; y++) {
				int south = downstream(y);
				for (int z = FACE; z <= south; z++) {
					if (x <= edge(z, y, 1) + 1 && x >= -edge(z, y, -1) - 1) {
						put(level, x, y, z, concrete(x, y, z, south));
					}
				}
			}
		}
	}

	/** A run of stairs: {@code steps} steps from ({@code x}, {@code y}) going {@code way} along x, three wide, four clear above. */
	private static void stairs(final Builder b, final int x, final int y, final int z0, final int steps, final Direction way) {
		int dx = way == Direction.EAST ? 1 : -1;
		for (int i = 0; i < steps; i++) {
			for (int z = z0; z <= z0 + 2; z++) {
				b.stairs(x + i * dx, y + i, z, Blocks.POLISHED_ANDESITE_STAIRS, way, false);
				for (int up = 1; up <= 4; up++) {
					b.set(x + i * dx, y + i + up, z, Blocks.AIR);
				}
			}
		}
	}

	/**
	 * A corridor three wide and four high on a floor at {@code floor}, its floor laid and striped with grates, its walls
	 * skirted dark at the foot and ribbed every six columns (only where they are solid: a way already cut into them stays).
	 */
	private static void corridor(final ServerLevel level, final int x0, final int x1, final int z0, final int z1, final int floor) {
		air(level, x0, floor + 1, z0, x1, floor + 4, z1);
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				put(level, x, floor, z, Math.floorMod(x, 8) == 0 ? OX_GRATE : Blocks.POLISHED_ANDESITE);
			}
			for (int z : new int[] {z0 - 1, z1 + 1}) {
				for (int y = floor + 1; y <= floor + 4; y++) {
					boolean rib = Math.floorMod(x, 6) == 3;
					if ((y == floor + 1 || rib) && !level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						put(level, x, y, z, y == floor + 1 ? Blocks.POLISHED_DEEPSLATE : Blocks.POLISHED_ANDESITE);
					}
				}
			}
		}
	}

	/**
	 * The buttresses standing out of the downstream face every twelve columns, two wide, a line of the forebears' crystal
	 * light up each; and a copper trim round the spillway's arch.
	 */
	private static void buttresses(final ServerLevel level) {
		int reach = open(0, CREST) + 2;
		for (int x = -reach; x <= reach; x++) {
			int column = Math.floorMod(x, 12);
			if (column > 1) {
				continue;
			}
			for (int y = BED + 1; y <= CREST - 3; y++) {
				int z = downstream(y) + 1;
				if (y <= 54 && Math.abs(x) <= 9 || x > edge(z, y, 1) || x < -edge(z, y, -1)) {
					continue;
				}
				boolean light = column == 0 && Math.floorMod(y, 3) != 0 && y > BED + 4;
				put(level, x, y, z, light ? life.shizune.mistinn.registry.MistinnBlocks.LATTICE_CRYSTAL : y < BED + 6 ? Blocks.MOSSY_STONE_BRICKS : Blocks.POLISHED_ANDESITE);
			}
		}
		for (int x = -8; x <= 8; x++) {
			int top = 53 - x * x / 12;
			for (int y = BED + 1; y <= top; y++) {
				boolean rim = Math.abs(x) == 8 || y == top;
				if (rim) {
					put(level, x, y, downstream(y), OX_CUT);
				}
			}
		}
	}

	/** The spillway at the dam's foot, its gate rusted shut, and the side way in to the stairs. */
	private static void spillway(final ServerLevel level) {
		for (int x = -7; x <= 7; x++) {
			int top = 52 - x * x / 12;
			for (int y = BED + 1; y <= top; y++) {
				for (int z = 2; z <= downstream(y); z++) {
					put(level, x, y, z, Blocks.AIR);
				}
			}
			for (int y = BED + 1; y <= top; y++) {
				put(level, x, y, 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
			}
			for (int z = 2; z <= TOE; z++) {
				put(level, x, BED, z, Math.abs(x) == 7 ? Blocks.STONE_BRICKS : Blocks.POLISHED_ANDESITE);
			}
		}
		for (int x = -5; x <= 5; x++) {
			for (int y = BED + 1; y <= BED + 8; y++) {
				put(level, x, y, 2, x % 2 == 0 || y == BED + 8 ? OX_CUT : Blocks.IRON_BARS);
			}
		}
		put(level, -3, BED, 6, Blocks.WATER);
		put(level, 4, BED, 9, Blocks.WATER);
		put(level, 0, BED, 4, Blocks.WATER);
		Builder b = new Builder(level, BlockPos.ZERO, level.getRandom());
		// the side way, east, and the stairs up to the first corridor
		air(level, 8, BED + 1, 4, 9, BED + 4, 6);
		stairs(b, 10, BED + 1, 4, 15, Direction.EAST);
		air(level, 25, 56, 4, 27, 59, 6);
		air(level, 25, 56, -1, 27, 59, 3);
		b.settle();
	}

	/** The three corridors inside the dam and the stairs between them. */
	private static void corridors(final ServerLevel level) {
		Builder b = new Builder(level, BlockPos.ZERO, level.getRandom());
		corridor(level, -30, 30, -4, -2, 55);
		// west: the first corridor up to the second
		stairs(b, -31, 56, -4, 8, Direction.WEST);
		corridor(level, -42, -39, -8, -2, 63);
		stairs(b, -38, 64, -8, 8, Direction.EAST);
		corridor(level, -30, 30, -8, -6, 71);
		// east: the second up to the third
		stairs(b, 31, 72, -8, 8, Direction.EAST);
		corridor(level, 39, 42, -8, -2, 79);
		stairs(b, 38, 80, -4, 8, Direction.WEST);
		corridor(level, 10, 30, -4, -2, 87);
		b.settle();
	}

	/** The sluice control room on the third corridor, and the stairs from it up to the crest. */
	private static void control(final ServerLevel level) {
		air(level, -9, 88, -10, 9, 95, -2);
		fill(level, -9, 87, -10, 9, 87, -2, Blocks.POLISHED_DEEPSLATE);
		// the window looks down the valley
		for (int x = -6; x <= 6; x++) {
			for (int y = 89; y <= 93; y++) {
				for (int z = -1; z <= 0; z++) {
					if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
						put(level, x, y, z, Blocks.GLASS);
					}
				}
			}
		}
		// the consoles along the north wall, the pipes in the corners, the gate's wheel on the east wall
		for (int x = -7; x <= 7; x++) {
			put(level, x, 88, -10, Blocks.POLISHED_BLACKSTONE);
			put(level, x, 89, -10, x % 3 == 0 ? Blocks.REDSTONE_LAMP : OX_GRATE);
		}
		for (int[] corner : new int[][] {{-9, -10}, {9, -10}, {-9, -2}, {9, -2}}) {
			fill(level, corner[0], 88, corner[1], corner[0], 95, corner[1], OX_CUT);
		}
		for (int z = -9; z <= -5; z++) {
			for (int y = 89; y <= 93; y++) {
				boolean ring = z == -9 || z == -5 || y == 89 || y == 93;
				boolean spoke = z == -7 || y == 91;
				if (ring || spoke) {
					put(level, 10, y, z, ring ? OX_BLOCK : OX_CUT);
				}
			}
		}
		Builder b = new Builder(level, BlockPos.ZERO, level.getRandom());
		b.facing(SAFE.getX(), SAFE.getY(), SAFE.getZ(), Blocks.CHEST, Direction.SOUTH);
		// west, up to the crest
		air(level, -10, 88, -6, -10, 90, -4);
		stairs(b, -11, 88, -6, 10, Direction.WEST);
		corridor(level, -23, -21, -10, -4, 97);
		stairs(b, -20, 98, -10, 11, Direction.EAST);
		b.settle();
	}

	/** On the crest: its railings and lamps, the gatehouse over the stairs, the sluice gates' housings and frames. */
	private static void crest(final ServerLevel level) {
		Builder b = new Builder(level, BlockPos.ZERO, level.getRandom());
		for (int x = -open(FACE, CREST + 1); x <= open(FACE, CREST + 1); x++) {
			if (Math.abs(x) > 2) {
				b.set(x, CREST + 1, FACE, Blocks.ANDESITE_WALL);
			}
			b.set(x, CREST + 1, -4, Blocks.ANDESITE_WALL);
			if (Math.floorMod(x, 12) == 6) {
				b.set(x, CREST + 2, -4, Blocks.LANTERN);
			}
		}
		// the gatehouse, open to the east
		for (int x = -16; x <= -8; x++) {
			for (int z = -11; z <= -7; z++) {
				for (int y = CREST + 1; y <= CREST + 4; y++) {
					boolean wall = x == -16 || x == -8 || z == -11 || z == -7;
					boolean door = x == -8 && z >= -10 && z <= -8 && y <= CREST + 3;
					boolean window = wall && y == CREST + 2 && !door && (x == -12 || z == -9);
					b.set(x, y, z, !wall || door ? Blocks.AIR : window ? Blocks.GLASS : Blocks.STONE_BRICKS);
				}
				b.set(x, CREST + 5, z, x == -16 || x == -8 || z == -11 || z == -7 ? OX_CUT : OX_BLOCK);
			}
		}
		b.set(-12, CREST + 6, -9, life.shizune.mistinn.registry.MistinnBlocks.LATTICE_CRYSTAL);
		b.hanging(-12, CREST + 4, -9, Blocks.LANTERN);
		// the sluice gates: their housings on the crest, their frames on the face above the water
		for (int c : new int[] {-24, 24}) {
			for (int x = c - 1; x <= c + 1; x++) {
				for (int z = -9; z <= -7; z++) {
					b.set(x, CREST + 1, z, OX_CUT);
					b.set(x, CREST + 2, z, x == c && z == -8 ? OX_GRATE : OX_CUT);
				}
			}
		}
		for (int c : new int[] {-24, 0, 24}) {
			for (int x = c - 3; x <= c + 3; x++) {
				for (int y = WATER + 1; y <= CREST - 1; y++) {
					boolean frame = Math.abs(x - c) == 3 || y == WATER + 1 || y == CREST - 1;
					b.set(x, y, FACE, frame ? Blocks.POLISHED_BLACKSTONE_BRICKS : x == c ? OX_GRATE : OX_CUT);
				}
			}
		}
		b.settle();
	}

	/** The bridge from the crest out over the reservoir to the intake tower. */
	private static void bridge(final ServerLevel level) {
		Builder b = new Builder(level, BlockPos.ZERO, level.getRandom());
		for (int z = -37; z <= FACE - 1; z++) {
			for (int x = -2; x <= 2; x++) {
				b.set(x, CREST, z, Math.abs(x) == 2 ? Blocks.STONE_BRICKS : Blocks.POLISHED_ANDESITE);
			}
			b.set(-2, CREST + 1, z, Blocks.ANDESITE_WALL);
			b.set(2, CREST + 1, z, Blocks.ANDESITE_WALL);
			if (Math.floorMod(z, 8) == 0) {
				b.set(2, CREST + 2, z, Blocks.LANTERN);
			}
		}
		for (int pier : new int[] {-20, -29}) {
			for (int x = -1; x <= 1; x++) {
				for (int z = pier - 1; z <= pier + 1; z++) {
					for (int y = SHORE - 4; y < CREST; y++) {
						b.set(x, y, z, Blocks.STONE_BRICKS);
					}
				}
			}
		}
		b.settle();
	}

	/** Whether a column of the intake tower (its centre {@code (0, -44)}) is inside it, on its wall, or neither. */
	private static int towerPart(final int x, final int z) {
		int dx = Math.abs(x);
		int dz = Math.abs(z + 44);
		if (dx <= 5 && dz <= 5 && dx + dz <= 9) {
			return 1;
		}
		return dx <= 6 && dz <= 6 && dx + dz <= 11 ? 2 : 0;
	}

	/**
	 * The intake tower, standing in the reservoir: eight-sided, a room at the bridge's height with stairs up its east
	 * side and out through the roof, a crown about the roof, and a mast with the forebears' light on it.
	 */
	private static void tower(final ServerLevel level) {
		Builder b = new Builder(level, BlockPos.ZERO, level.getRandom());
		for (int x = -6; x <= 6; x++) {
			for (int z = -50; z <= -38; z++) {
				int part = towerPart(x, z);
				if (part == 0) {
					continue;
				}
				boolean ring = part == 2;
				boolean seam = Math.abs(x) + Math.abs(z + 44) == 11 || Math.abs(x) == 6 && Math.abs(z + 44) == 5 || Math.abs(z + 44) == 6 && Math.abs(x) == 5;
				for (int y = SHORE - 4; y <= CREST; y++) {
					b.set(x, y, z, ring ? y % 4 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE);
				}
				for (int y = CREST + 1; y <= CREST + 9; y++) {
					boolean window = ring && !seam && y >= CREST + 3 && y <= CREST + 5 && (x == 0 || z == -44);
					Block wall = y == CREST + 7 ? OX_CUT : seam ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS;
					b.set(x, y, z, !ring ? Blocks.AIR : window ? Blocks.GLASS : wall);
				}
				b.set(x, CREST + 10, z, ring ? OX_CUT : Blocks.POLISHED_ANDESITE);
				if (ring) {
					b.set(x, CREST + 11, z, seam ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
					b.set(x, CREST + 12, z, seam ? Blocks.LANTERN : Blocks.ANDESITE_WALL);
				}
			}
		}
		air(level, -1, CREST + 1, -38, 1, CREST + 3, -38);
		// the stairs up the east side, and out through the roof
		for (int k = 0; k <= 8; k++) {
			for (int x = 3; x <= 4; x++) {
				b.stairs(x, CREST + 1 + k, -40 - k, Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false);
			}
		}
		air(level, 3, CREST + 10, -48, 4, CREST + 10, -45);
		b.stairs(3, CREST + 10, -49, Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false);
		b.stairs(4, CREST + 10, -49, Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false);
		b.hanging(0, CREST + 9, -44, Blocks.LANTERN);
		b.hanging(-3, CREST + 9, -41, Blocks.LANTERN);
		// the old intake, and the mast
		b.set(-3, CREST + 11, -47, OX_CUT);
		b.set(-3, CREST + 12, -47, OX_GRATE);
		for (int y = CREST + 11; y <= CREST + 16; y++) {
			b.set(-4, y, -41, y == CREST + 11 ? OX_CUT : Blocks.IRON_BARS);
		}
		b.set(-4, CREST + 17, -41, life.shizune.mistinn.registry.MistinnBlocks.LATTICE_CRYSTAL);
		b.set(-4, CREST + 18, -41, ROD);
		b.settle();
	}

	/** The forebears' observation hut on the riverbed, where a party comes in. */
	private static void hut(final ServerLevel level) {
		Builder b = new Builder(level, BlockPos.ZERO, level.getRandom());
		for (int x = 18; x <= 26; x++) {
			for (int z = 122; z <= 130; z++) {
				boolean wall = x == 18 || x == 26 || z == 122 || z == 130;
				boolean corner = (x == 18 || x == 26) && (z == 122 || z == 130);
				b.set(x, BED, z, Blocks.POLISHED_ANDESITE);
				for (int y = BED + 1; y <= BED + 4; y++) {
					boolean door = x == 18 && (z == 125 || z == 126) && y <= BED + 2;
					boolean window = wall && !corner && y >= BED + 2 && y <= BED + 3 && (z == 128 || x == 22);
					b.set(x, y, z, !wall || door ? Blocks.AIR : window ? Blocks.GLASS : corner ? Blocks.STONE_BRICKS : Blocks.CONCRETE.lightGray());
				}
				b.set(x, BED + 5, z, Blocks.SMOOTH_STONE);
			}
		}
		b.hanging(22, BED + 4, 126, Blocks.LANTERN);
		b.set(25, BED + 1, 123, Blocks.CARTOGRAPHY_TABLE);
		b.set(25, BED + 1, 124, Blocks.LECTERN);
		b.facing(25, BED + 1, 129, Blocks.BARREL, Direction.WEST);
		for (int y = BED + 6; y <= BED + 9; y++) {
			b.set(24, y, 128, Blocks.IRON_BARS);
		}
		b.set(24, BED + 10, 128, ROD);
		b.settle();
	}

	/** The riverbed's lie: stones fallen from the walls, pieces of the dam, rusted pipes, and the crates. */
	private static void riverbed(final ServerLevel level) {
		RandomSource random = RandomSource.create(1937L);
		Builder b = new Builder(level, BlockPos.ZERO, random);
		for (int i = 0; i < 18; i++) {
			int z = 20 + random.nextInt(120);
			int reach = open(z, BED + 1) - 6;
			int x = -reach + random.nextInt(reach * 2 + 1);
			if (nearHut(x, z) || Math.abs(x - trickle(z)) < 3 || z < 22 && Math.abs(x) < 10) {
				continue;
			}
			Block stone = random.nextFloat() < 0.4 ? Blocks.MOSSY_COBBLESTONE : random.nextBoolean() ? Blocks.COBBLESTONE : Blocks.STONE;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					int height = (dx == 0 && dz == 0 ? 2 : 1) + (random.nextFloat() < 0.3 ? 1 : 0);
					for (int y = BED + 1; y <= BED + height; y++) {
						b.set(x + dx, y, z + dz, stone);
					}
				}
			}
		}
		// pieces of the dam, near its foot
		for (int[] piece : new int[][] {{-18, 20}, {16, 24}, {-6, 32}, {24, 38}, {-26, 30}}) {
			for (int dx = 0; dx <= 2; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					b.set(piece[0] + dx, BED + 1, piece[1] + dz, dx == 1 ? Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE);
				}
			}
			b.set(piece[0] + 1, BED + 2, piece[1], Blocks.CRACKED_STONE_BRICKS);
		}
		// rusted pipes, lying across the bed: walk through, or round
		for (int[] pipe : new int[][] {{-16, 76}, {12, 96}, {-14, 50}}) {
			for (int x = pipe[0]; x <= pipe[0] + 8; x++) {
				for (int dy = -2; dy <= 2; dy++) {
					for (int dz = -2; dz <= 2; dz++) {
						double r = Math.sqrt(dy * dy + dz * dz);
						int y = BED + 2 + dy;
						if (r > 1.5 && r <= 2.5 && y >= BED) {
							b.set(x, y, pipe[1] + dz, (x + dy) % 3 == 0 ? OX_BLOCK : WEATHERED);
						} else if (r <= 1.5 && y > BED) {
							b.set(x, y, pipe[1] + dz, Blocks.AIR);
						}
					}
				}
			}
		}
		Direction[] sides = {Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH};
		for (int i = 0; i < CRATES.size(); i++) {
			BlockPos crate = CRATES.get(i);
			b.facing(crate.getX(), crate.getY(), crate.getZ(), Blocks.BARREL, sides[i % sides.length]);
			b.set(crate.getX() + 1, crate.getY(), crate.getZ(), i % 2 == 0 ? Blocks.SPRUCE_PLANKS : Blocks.AIR);
		}
		for (int z = TOE + 2; z < SOUTH; z++) {
			for (int x = -edge(z, BED, -1); x <= edge(z, BED, 1); x++) {
				if (nearHut(x, z) || Math.abs(x - trickle(z) - 0.5) <= 1.5 || !level.getBlockState(new BlockPos(x, BED + 1, z)).isAir()) {
					continue;
				}
				BlockState ground = level.getBlockState(new BlockPos(x, BED, z));
				double n = noise(x, BED + 1, z);
				if (ground.is(life.shizune.mistinn.registry.MistinnBlocks.JADE_TURF) && n < 0.45) {
					b.set(x, BED + 1, z, n < 0.37 ? life.shizune.mistinn.registry.MistinnBlocks.JADE_GRASS : life.shizune.mistinn.registry.MistinnBlocks.JADE_FERN);
				} else if ((ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.PACKED_MUD)) && n < 0.05) {
					b.set(x, BED + 1, z, n < 0.02 ? Blocks.DEAD_BUSH : Blocks.SHORT_DRY_GRASS);
				}
			}
		}
		b.settle();
	}

	/** Whether a column is by the hut, where the party comes in: nothing lies there. */
	private static boolean nearHut(final int x, final int z) {
		return x > 6 && x < 30 && z > 116 && z < 136;
	}

	/** Lamps along the corridors and stairs, and the lockers. */
	private static void lights(final ServerLevel level) {
		Builder b = new Builder(level, BlockPos.ZERO, level.getRandom());
		for (int[] run : new int[][] {{-30, 30, -3, 55}, {-30, 30, -7, 71}, {10, 30, -3, 87}}) {
			for (int x = run[0]; x <= run[1]; x += 6) {
				b.hanging(x, run[3] + 4, run[2], Blocks.LANTERN);
				if (Math.floorMod(x, 12) == 0) {
					b.set(x, run[3] + 5, run[2] - 1, Blocks.SEA_LANTERN);
				}
			}
		}
		for (int[] lamp : new int[][] {{-40, 67, -5}, {40, 83, -5}, {-22, 101, -7}, {26, 59, 5}, {17, 52, 5}, {-3, 95, -6}, {3, 95, -6},
			{-6, 95, -3}, {6, 95, -3}, {-6, 95, -9}, {6, 95, -9}}) {
			b.hanging(lamp[0], lamp[1], lamp[2], Blocks.LANTERN);
		}
		for (BlockPos locker : LOCKERS) {
			b.facing(locker.getX(), locker.getY(), locker.getZ(), Blocks.BARREL, Direction.SOUTH);
		}
		b.settle();
	}

	// ── the water, last: everything that holds it stands already ──

	private static void reservoir(final ServerLevel level, final int z0, final int z1) {
		for (int z = z0; z <= z1; z++) {
			for (int y = SHORE + 1; y <= WATER; y++) {
				for (int x = -edge(z, y, -1); x <= edge(z, y, 1); x++) {
					BlockPos at = new BlockPos(x, y, z);
					if (level.getBlockState(at).isAir()) {
						level.setBlock(at, Blocks.WATER.defaultBlockState(), FLAGS);
					}
				}
			}
		}
	}

	/** The trickle down the riverbed, two wide, sunk into it; and a few still pools. */
	private static void trickle(final ServerLevel level) {
		for (int z = TOE + 1; z <= SOUTH; z++) {
			int x = trickle(z);
			put(level, x, BED, z, Blocks.WATER);
			put(level, x + 1, BED, z, Blocks.WATER);
			if (level.getBlockState(new BlockPos(x, BED + 1, z)).is(Blocks.DEAD_BUSH)) {
				put(level, x, BED + 1, z, Blocks.AIR);
			}
		}
		for (int[] pool : new int[][] {{-12, 40}, {20, 70}, {-22, 110}, {8, 136}, {-6, 88}}) {
			for (int dx = 0; dx <= 1; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					put(level, pool[0] + dx, BED, pool[1] + dz, Blocks.WATER);
				}
			}
		}
	}
}
