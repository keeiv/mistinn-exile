package life.shizune.mistinn.udasru;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.world.Builder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Everything built in Udasru, each where it was drawn: the bell towers, the capital piece by piece, the towns house by
 * house, the beasts' grounds with their great bells, and the anchor's crown. A piece is built once its ground and the
 * ground round it are loaded (and any piece it stands on is built), so nothing is built into ground not yet made; what
 * was built is written down with the world.
 */
public final class UdasruSites {
	/** The pieces built so far, by id. */
	public static final AttachmentType<List<String>> BUILT = AttachmentRegistry.<List<String>>builder()
		.persistent(Codec.STRING.listOf())
		.buildAndRegister(Mistinn.id("udasru_built"));

	/** The ground level the capital was drawn at. */
	public static final int CAPITAL_Y = UdasruPlaces.CAPITAL.getY();
	/** The capital's wall stands this far from its middle. */
	public static final int WALL = 108;

	/**
	 * One thing to build: its id, the box of ground it takes (x and z, inclusive), its origin column, the height of its
	 * origin (or {@link #GROUND} for the ground there), what it builds, what must stand first, and when it may be built.
	 */
	public record Site(String id, int minX, int minZ, int maxX, int maxZ, int x, int z, int y, Consumer<Builder> build, List<String> after,
		@Nullable Predicate<MinecraftServer> when) {
	}

	/** Build on the ground found at the origin column. */
	public static final int GROUND = Integer.MIN_VALUE;

	private static final Map<String, Site> SITES = new LinkedHashMap<>();
	/** Where each tower's bell and each shrine's small bell hang, once built (this server's run). */
	private static final Map<String, BlockPos> BELLS = new LinkedHashMap<>();

	private UdasruSites() {
	}

	private static Site site(final String id, final int x, final int z, final int half, final int y, final Consumer<Builder> build, final String... after) {
		Site site = new Site(id, x - half, z - half, x + half, z + half, x, z, y, build, List.of(after), null);
		SITES.put(id, site);
		return site;
	}

	public static Map<String, Site> sites() {
		if (SITES.isEmpty()) {
			plan();
		}
		return SITES;
	}

	/** Lays out every piece of Udasru. */
	private static void plan() {
		for (UdasruPlaces.Tower tower : UdasruPlaces.Tower.values()) {
			site("tower_" + tower.id, tower.x, tower.z, 9, GROUND, b -> {
				BELLS.put("tower_" + tower.id, UdasruBuild.tower(b));
				UdasruBuild.person(b, StaffRole.TOWER_KEEPER, 2, 1, 7, 180.0F, 4);
			});
		}
		// the beasts' grounds: each place the rust took a great beast in
		site("arena_salt", UdasruPlaces.GreatBell.SALT.x, UdasruPlaces.GreatBell.SALT.z, 12, 63, UdasruArenas::saltTemple);
		site("arena_woods", UdasruPlaces.GreatBell.WOODS.x, UdasruPlaces.GreatBell.WOODS.z, 10, GROUND, UdasruArenas::woodsLighthouse);
		site("arena_rust", UdasruPlaces.GreatBell.RUST.x, UdasruPlaces.GreatBell.RUST.z, 17, GROUND, UdasruArenas::forgeYard);
		site("arena_crystal", UdasruPlaces.GreatBell.CRYSTAL.x, UdasruPlaces.GreatBell.CRYSTAL.z, 9, GROUND, UdasruArenas::crownAltar);
		// and their great bells, a little way off
		for (UdasruPlaces.GreatBell bell : UdasruPlaces.GreatBell.values()) {
			int[] at = shrinePlace(bell);
			site("shrine_" + bell.id, at[0], at[1], 8, GROUND, b -> BELLS.put("shrine_" + bell.id, UdasruBuild.shrine(b)));
		}
		capital();
		towns();
		site("crown", UdasruPlaces.ANCHOR_CROWN.getX(), UdasruPlaces.ANCHOR_CROWN.getZ(), 12, GROUND, UdasruCrown::build);
		Site chain = new Site("chain", UdasruPlaces.CHAIN_SHORE.getX() - 4, UdasruPlaces.CHAIN_SHORE.getZ(), UdasruPlaces.CHAIN_SHORE.getX() + 4,
			UdasruPlaces.CHAIN_SHORE.getZ() + UdasruCrown.CHAIN, UdasruPlaces.CHAIN_SHORE.getX(), UdasruPlaces.CHAIN_SHORE.getZ(), 63, UdasruCrown::chain,
			List.of("crown"), life.shizune.mistinn.epic.EpicService::chainBare);
		SITES.put(chain.id(), chain);
	}

	/** Where a great bell's shrine stands: by its beast's ground, on dry land. */
	public static int[] shrinePlace(final UdasruPlaces.GreatBell bell) {
		return switch (bell) {
			case SALT -> new int[] {bell.x, bell.z + 250};
			case WOODS -> new int[] {bell.x + 30, bell.z + 6};
			case RUST -> new int[] {bell.x, bell.z + 46};
			case CRYSTAL -> new int[] {bell.x - 40, bell.z + 30};
		};
	}

	// ── The capital ───────────────────────────────────────────────

	/** Boxes in the capital its great buildings keep for themselves: x0, z0, x1, z1. */
	private static final int[][] RESERVED = {{-22, -22, 22, 22}, {-18, -76, 18, -48}, {46, -14, 78, 14}, {-80, -16, -42, 16}, {32, 44, 48, 60},
		{-50, 42, -30, 62}};

	private static void capital() {
		int cell = 32;
		List<String> cells = new ArrayList<>();
		for (int cx = -4; cx < 4; cx++) {
			for (int cz = -4; cz < 4; cz++) {
				int x0 = cx * cell;
				int z0 = cz * cell;
				String id = "capital_cell_" + cx + "_" + cz;
				Site site = new Site(id, x0, z0, x0 + cell - 1, z0 + cell - 1, x0, z0, CAPITAL_Y, b -> pave(b, cell), List.of(), null);
				SITES.put(id, site);
				cells.add(id);
			}
		}
		// the wall, in sixteen lengths, and its four gates
		for (int k = 0; k < 16; k++) {
			double a0 = k * Math.PI / 8.0;
			double a1 = (k + 1) * Math.PI / 8.0;
			int mx = (int) Math.round(Math.cos((a0 + a1) / 2) * WALL);
			int mz = (int) Math.round(Math.sin((a0 + a1) / 2) * WALL);
			site("capital_wall_" + k, mx, mz, 26, CAPITAL_Y, b -> UdasruCapital.wall(b, a0, a1), near(mx, mz, 26, cell));
		}
		for (Direction4 gate : Direction4.values()) {
			int gx = gate.dx * WALL;
			int gz = gate.dz * WALL;
			site("capital_gate_" + gate.name().toLowerCase(java.util.Locale.ROOT), gx, gz, 10, CAPITAL_Y, b -> UdasruCapital.gate(b, gate),
				near(gx, gz, 10, cell));
		}
		site("capital_tower", 0, 0, 10, CAPITAL_Y, b -> {
			Builder at = b;
			BELLS.put("capital_tower", UdasruBuild.greatTower(at));
		}, near(0, 0, 10, cell));
		site("capital_palace", 0, -62, 18, CAPITAL_Y, UdasruBuild::palace, near(0, -62, 18, cell));
		site("capital_library", 62, 0, 16, CAPITAL_Y, UdasruBuild::library, near(62, 0, 16, cell));
		site("capital_market", -62, 0, 18, CAPITAL_Y, UdasruCapital::market, near(-62, 0, 18, cell));
		site("capital_tea", 40, 52, 8, CAPITAL_Y, UdasruBuild::teaHouse, near(40, 52, 8, cell));
		site("capital_stelae", -40, 52, 10, CAPITAL_Y, UdasruBuild::stelae, near(-40, 52, 10, cell));
		// houses on the lawns between, on plots sixteen blocks apart
		RandomSource random = RandomSource.create(0x55444153L);
		int n = 0;
		for (int x = -88; x <= 88; x += 16) {
			for (int z = -88; z <= 88; z += 16) {
				int hx = x + random.nextInt(5) - 2;
				int hz = z + random.nextInt(5) - 2;
				double r = Math.hypot(hx, hz);
				if (r < 28 || r > WALL - 14 || Math.abs(hx) < 11 || Math.abs(hz) < 11 || Math.abs(r - 60) < 6 || reserved(hx, hz)) {
					continue;
				}
				int sx = 3 + random.nextInt(2);
				int sz = 3 + random.nextInt(2);
				int floors = 1 + random.nextInt(2);
				int turn = random.nextInt(4);
				boolean person = random.nextInt(3) == 0;
				StaffRole role = random.nextBoolean() ? StaffRole.TOWNSMAN : StaffRole.TOWNSWOMAN;
				site("capital_house_" + n++, hx, hz, 8, CAPITAL_Y, b -> {
					Builder turned = b.rotated(net.minecraft.world.level.block.Rotation.values()[turn]);
					UdasruBuild.house(turned, UdasruBuild.Style.CAPITAL, sx, sz, floors);
					if (person) {
						UdasruBuild.person(turned, role, 0, 1, sz + 3, 0.0F, 6);
					}
				}, near(hx, hz, 8, cell));
			}
		}
	}

	private static boolean reserved(final int x, final int z) {
		for (int[] box : RESERVED) {
			if (x >= box[0] - 8 && x <= box[2] + 8 && z >= box[1] - 8 && z <= box[3] + 8) {
				return true;
			}
		}
		return false;
	}

	/** The capital's cells a box of half-width {@code half} round (x, z) touches: they are paved first. */
	private static String[] near(final int x, final int z, final int half, final int cell) {
		Set<String> ids = new HashSet<>();
		for (int cx = Math.floorDiv(x - half, cell); cx <= Math.floorDiv(x + half, cell); cx++) {
			for (int cz = Math.floorDiv(z - half, cell); cz <= Math.floorDiv(z + half, cell); cz++) {
				if (cx >= -4 && cx < 4 && cz >= -4 && cz < 4) {
					ids.add("capital_cell_" + cx + "_" + cz);
				}
			}
		}
		return ids.toArray(new String[0]);
	}

	/** The four ways out of the capital. */
	public enum Direction4 {
		NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

		public final int dx;
		public final int dz;

		Direction4(final int dx, final int dz) {
			this.dx = dx;
			this.dz = dz;
		}
	}

	/** A cell of the capital: level ground paved where the roads run, lawn elsewhere, nothing standing above. */
	private static void pave(final Builder b, final int cell) {
		BlockPos origin = b.at(0, 0, 0);
		for (int dx = 0; dx < cell; dx++) {
			for (int dz = 0; dz < cell; dz++) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				double r = Math.hypot(x, z);
				if (r > WALL + 8) {
					continue;
				}
				b.fill(dx, 1, dz, dx, 16, dz, Blocks.AIR);
				b.footing(dx, dz, Blocks.DIRT, 16);
				boolean road = Math.abs(x) <= 3 || Math.abs(z) <= 3 || r <= 20 || Math.abs(r - 60) <= 2 || r > WALL - 6;
				b.set(dx, 0, dz, road ? ((x * 3 + z * 5 & 7) == 0 ? Blocks.POLISHED_ANDESITE : life.shizune.mistinn.registry.MistinnBlocks.UDASRU_STONE_BRICKS)
					: Blocks.GRASS_BLOCK);
				b.set(dx, -1, dz, Blocks.DIRT);
				// lamp posts along the avenues
				if ((Math.abs(x) == 5 && z % 12 == 0 || Math.abs(z) == 5 && x % 12 == 0) && r > 22 && r < WALL - 8) {
					b.set(dx, 1, dz, Blocks.SPRUCE_FENCE);
					b.set(dx, 2, dz, Blocks.SPRUCE_FENCE);
					b.set(dx, 3, dz, Blocks.LANTERN);
				}
			}
		}
	}

	// ── The towns ─────────────────────────────────────────────────

	private static void towns() {
		town("salt_town", UdasruPlaces.SALT_TOWN, UdasruBuild.Style.SALT, StaffRole.SALT_WORKER);
		town("bell_port", UdasruPlaces.BELL_PORT, UdasruBuild.Style.PORT, StaffRole.TOWNSMAN);
		town("woods_village", UdasruPlaces.WOODS_VILLAGE, UdasruBuild.Style.WOODS, StaffRole.WOODSMAN);
		town("frost_gate", UdasruPlaces.FROST_GATE, UdasruBuild.Style.FROST, StaffRole.FROST_GUARD);
		town("forge_town", UdasruPlaces.FORGE_TOWN, UdasruBuild.Style.FORGE, StaffRole.FORGE_SMITH);
	}

	private static void town(final String id, final BlockPos at, final UdasruBuild.Style style, final StaffRole folk) {
		site(id + "_square", at.getX(), at.getZ(), 9, GROUND, b -> {
			UdasruBuild.square(b, style, 8);
			UdasruBuild.person(b, folk, 3, 1, 4, 0.0F, 8);
			UdasruBuild.person(b, StaffRole.TOWNSWOMAN, -4, 1, -3, 90.0F, 8);
		});
		for (int i = 0; i < 6; i++) {
			double angle = i * Math.PI / 3.0 + 0.3;
			int hx = at.getX() + (int) Math.round(Math.cos(angle) * 22);
			int hz = at.getZ() + (int) Math.round(Math.sin(angle) * 22);
			int house = i;
			site(id + "_house_" + i, hx, hz, 8, GROUND, b -> {
				UdasruBuild.house(b, style, house % 2 == 0 ? 4 : 3, 3, house % 3 == 0 ? 2 : 1);
				if (house % 2 == 1) {
					UdasruBuild.person(b, folk, 0, 1, 6, 0.0F, 6);
				}
			});
		}
	}

	// ── Building ──────────────────────────────────────────────────

	public static Set<String> built(final MinecraftServer server) {
		return new HashSet<>(server.overworld().getAttachedOrElse(BUILT, List.of()));
	}

	/** Where a built bell hangs, by its site's id (towers, shrines and the capital's tower), if built in this run. */
	public static @Nullable BlockPos bell(final String site) {
		return BELLS.get(site);
	}

	/** Builds every piece whose ground is ready and whose time has come. */
	public static void tick(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		Set<String> built = built(server);
		boolean changed = false;
		for (Site site : sites().values()) {
			if (built.contains(site.id()) || !built.containsAll(site.after()) || site.when() != null && !site.when().test(server) || !ready(level, site)) {
				continue;
			}
			RandomSource random = RandomSource.create(level.getSeed() ^ site.id().hashCode());
			int y = site.y() == GROUND ? level.getHeight(Heightmap.Types.OCEAN_FLOOR, site.x(), site.z()) - 1 : site.y();
			site.build().accept(new Builder(level, new BlockPos(site.x(), y, site.z()), random));
			built.add(site.id());
			changed = true;
			Mistinn.LOGGER.info("Udasru: built {} at {}, {}, {}", site.id(), site.x(), y, site.z());
		}
		if (changed) {
			level.setAttached(BUILT, List.copyOf(built));
		}
	}

	/** Whether every chunk under a site, and one chunk round it, is loaded and finished. */
	private static boolean ready(final ServerLevel level, final Site site) {
		for (int cx = (site.minX() >> 4) - 1; cx <= (site.maxX() >> 4) + 1; cx++) {
			for (int cz = (site.minZ() >> 4) - 1; cz <= (site.maxZ() >> 4) + 1; cz++) {
				if (level.getChunkSource().getChunkNow(cx, cz) == null) {
					return false;
				}
			}
		}
		return true;
	}
}
