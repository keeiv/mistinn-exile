package life.shizune.mistinn.training;

import com.mojang.serialization.Codec;
import java.util.Comparator;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.world.Builder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Rust Anchor Station, where hunters train before the drop: see {@link StationMap}. */
public final class TrainingGround {
	public static final String PACK_ID = "mistinn:training";
	public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, Mistinn.id("training_ground"));
	/** The station as it is built now; a save built by an older version is cleared and built again. */
	public static final int VERSION = 3;
	/** Where a hunter heard in the listening pen is sent back to: its west door. */
	public static final BlockPos SNEAK_START = new BlockPos(-3, 77, -60);
	/** Where a hunter wakes: the barracks, between the bunks. */
	public static final BlockPos ENTRY = new BlockPos(-24, 65, 17);

	/** Saves from before the station: the old chain of quays. */
	private static final AttachmentType<Boolean> BUILT = AttachmentRegistry.createPersistent(Mistinn.id("training_ground_built"), Codec.BOOL);
	private static final AttachmentType<Integer> BUILT_VERSION = AttachmentRegistry.createPersistent(Mistinn.id("training_ground_version"), Codec.INT);
	/** Set when an old save's quays were cleared for the station: the owner's course starts over. */
	private static final AttachmentType<Boolean> UPGRADED = AttachmentRegistry.createPersistent(Mistinn.id("training_ground_upgraded"), Codec.BOOL);

	/** Where each member of the staff keeps their post, and which way they face. */
	public static final List<Post> POSTS = List.of(
		new Post(StaffRole.ROOMMATE, new BlockPos(-21, 65, 18), 90.0F),
		new Post(StaffRole.QUARTERMASTER, new BlockPos(5, 65, 4), 0.0F),
		new Post(StaffRole.COOK, new BlockPos(-42, 77, -21), -90.0F),
		new Post(StaffRole.SMITH, new BlockPos(-54, 77, -50), -90.0F),
		new Post(StaffRole.INSTRUCTOR, new BlockPos(-22, 77, -44), 0.0F),
		new Post(StaffRole.SCOUT, new BlockPos(0, 77, -60), 90.0F),
		new Post(StaffRole.DOCTOR, new BlockPos(40, 77, -44), 90.0F),
		new Post(StaffRole.ARCHIVIST, new BlockPos(43, 77, -75), 0.0F),
		new Post(StaffRole.BELL_KEEPER, new BlockPos(5, 77, -92), -90.0F),
		new Post(StaffRole.RADIO_OPERATOR, new BlockPos(-14, 77, -88), -90.0F),
		new Post(StaffRole.CRANE_OPERATOR, new BlockPos(-24, 91, -116), 90.0F),
		new Post(StaffRole.DOCK_WORKER_1, new BlockPos(8, 65, 15), -90.0F),
		new Post(StaffRole.DOCK_WORKER_2, new BlockPos(-4, 65, 20), 0.0F),
		new Post(StaffRole.DOCK_WORKER_3, new BlockPos(-20, 65, 2), -90.0F),
		new Post(StaffRole.DOCK_WORKER_4, new BlockPos(15, 65, -11), 180.0F));

	/** The dock hands' rounds, starting from their posts. */
	private static final java.util.Map<StaffRole, List<BlockPos>> ROUTES = java.util.Map.of(
		StaffRole.DOCK_WORKER_1, List.of(new BlockPos(8, 65, 15), new BlockPos(18, 65, 13)),
		StaffRole.DOCK_WORKER_2, List.of(new BlockPos(-4, 65, 20), new BlockPos(2, 65, 9)),
		StaffRole.DOCK_WORKER_3, List.of(new BlockPos(-20, 65, 2), new BlockPos(-6, 65, 14), new BlockPos(15, 65, -6)));

	/** The stops on this member's round; empty for those who keep to one post. */
	public static List<BlockPos> route(final StaffRole role) {
		return ROUTES.getOrDefault(role, List.of());
	}

	/** Safe places on each deck, for a hunter fished out of the water. */
	public static final List<BlockPos> LANDINGS = List.of(
		new BlockPos(-6, 65, 16), new BlockPos(15, 65, -4), new BlockPos(-33, 77, -8), new BlockPos(-40, 77, -34),
		new BlockPos(-21, 77, -45), new BlockPos(10, 77, -52), new BlockPos(30, 77, -54), new BlockPos(45, 77, -26),
		new BlockPos(42, 77, -57), new BlockPos(12, 77, -84), new BlockPos(-6, 77, -82), new BlockPos(-31, 77, -82),
		new BlockPos(-46, 77, -95), new BlockPos(-31, 91, -112));

	/** Where one of the station's staff stands, and which way they face. */
	public record Post(StaffRole role, BlockPos pos, float yaw) {
	}

	/** One stop on the course's route: where a hunter's feet are, and what is there. */
	public record Waypoint(String name, BlockPos pos) {
	}

	/**
	 * The course, walked from the bunk to the pod: every lesson's spot, and the door, bridge end or
	 * stair between them, in order. Each leg is meant to be walked straight; the route test walks
	 * every one of them, and the guide arrow leads along them.
	 */
	public static final List<Waypoint> ROUTE = List.of(
		new Waypoint("barracks bunks", ENTRY),
		new Waypoint("barracks door", new BlockPos(-11, 65, 16)),
		new Waypoint("arrival", new BlockPos(-6, 65, 16)),
		new Waypoint("stores counter", new BlockPos(5, 65, 8)),
		new Waypoint("stair landing", new BlockPos(-5, 65, -5)),
		new Waypoint("stair head", new BlockPos(-21, 77, -5)),
		new Waypoint("market, east of the mess", new BlockPos(-28, 77, -10)),
		new Waypoint("north of the mess", new BlockPos(-28, 77, -30)),
		new Waypoint("forge bridge, market end", new BlockPos(-42, 77, -31)),
		new Waypoint("forge bridge, forge end", new BlockPos(-42, 77, -38)),
		new Waypoint("heavy blow", new BlockPos(-42, 77, -45)),
		new Waypoint("forge to drill yard", new BlockPos(-36, 77, -49)),
		new Waypoint("sprint", new BlockPos(-22, 77, -42)),
		new Waypoint("slide", new BlockPos(-32, 77, -50)),
		new Waypoint("strike", new BlockPos(-21, 77, -53)),
		new Waypoint("pen bridge, drill end", new BlockPos(-11, 77, -60)),
		new Waypoint("pen bridge, pen end", new BlockPos(-3, 77, -60)),
		new Waypoint("scout's lamp", new BlockPos(10, 77, -65)),
		new Waypoint("pen to infirmary", new BlockPos(23, 77, -52)),
		new Waypoint("infirmary deck", new BlockPos(27, 77, -52)),
		new Waypoint("infirmary west door", new BlockPos(27, 77, -41)),
		new Waypoint("infirmary", new BlockPos(38, 77, -39)),
		new Waypoint("infirmary south door", new BlockPos(45, 77, -32)),
		new Waypoint("workshop bridge", new BlockPos(45, 77, -27)),
		new Waypoint("workshop north door", new BlockPos(45, 77, -21)),
		new Waypoint("workshop table", new BlockPos(45, 77, -13)),
		new Waypoint("back to the workshop door", new BlockPos(45, 77, -21)),
		new Waypoint("back over the workshop bridge", new BlockPos(45, 77, -27)),
		new Waypoint("back into the infirmary", new BlockPos(45, 77, -32)),
		new Waypoint("infirmary north door", new BlockPos(42, 77, -50)),
		new Waypoint("archive bridge", new BlockPos(42, 77, -57)),
		new Waypoint("archive south door", new BlockPos(42, 77, -63)),
		new Waypoint("archive", new BlockPos(43, 77, -71)),
		new Waypoint("archive west door", new BlockPos(33, 77, -71)),
		new Waypoint("archive deck, north-west", new BlockPos(33, 77, -84)),
		new Waypoint("bell court bridge", new BlockPos(28, 77, -84)),
		new Waypoint("bell court, east", new BlockPos(21, 77, -84)),
		new Waypoint("golden bell", new BlockPos(12, 77, -86)),
		new Waypoint("fall", new BlockPos(12, 77, -98)),
		new Waypoint("back at the bell", new BlockPos(12, 77, -86)),
		new Waypoint("radio bridge, bell end", new BlockPos(2, 77, -88)),
		new Waypoint("radio bridge, radio end", new BlockPos(-6, 77, -88)),
		new Waypoint("radio room east door", new BlockPos(-9, 77, -86)),
		new Waypoint("radio", new BlockPos(-17, 77, -84)),
		new Waypoint("radio room west door", new BlockPos(-27, 77, -82)),
		new Waypoint("lamp hall bridge", new BlockPos(-35, 77, -82)),
		new Waypoint("lamp hall east door", new BlockPos(-38, 77, -82)),
		new Waypoint("lamp hall", new BlockPos(-48, 77, -79)),
		new Waypoint("lamp hall north gate", new BlockPos(-46, 77, -95)),
		new Waypoint("cold stair top", new BlockPos(-46, 91, -112)),
		new Waypoint("drop platform", new BlockPos(-40, 91, -112)),
		new Waypoint("report console", new BlockPos(-33, 91, -108)),
		new Waypoint("beside the console", new BlockPos(-36, 91, -108)),
		new Waypoint("past the console", new BlockPos(-36, 91, -112)),
		new Waypoint("pod", new BlockPos(-27, 91, -127)));

	private TrainingGround() {
	}

	public static void init() {
		boolean registered = ResourceLoader.registerBuiltinPack(Mistinn.id("training"),
			FabricLoader.getInstance().getModContainer(Mistinn.MOD_ID).orElseThrow(),
			Component.translatable("mode.mistinn.training"), PackActivationType.NORMAL);
		if (!registered) {
			Mistinn.LOGGER.error("Could not register the hunter training data pack");
		}
	}

	/** The nearest safe place on a deck to where the hunter is. */
	public static BlockPos landing(final Vec3 near) {
		return LANDINGS.stream().min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(near.x, pos.getY(), near.z))).orElse(ENTRY);
	}

	public static void ensureBuilt(final ServerLevel level) {
		if (!level.dimension().equals(KEY) || level.getAttachedOrElse(BUILT_VERSION, 0) >= VERSION) {
			return;
		}
		long started = System.nanoTime();
		Builder b = new Builder(level, new BlockPos(0, 64, 0), RandomSource.create(0x52555354414E4348L));
		if (level.getAttachedOrElse(BUILT_VERSION, 0) == 2) {
			StationMap.repairBridges(b);
		}
		if (level.getAttachedOrElse(BUILT, false)) {
			clearQuays(b);
			level.removeAttached(BUILT);
			markUpgraded(level);
		}
		StationMap.build(b);
		level.setAttached(BUILT_VERSION, VERSION);
		Mistinn.LOGGER.info("Built Rust Anchor Station in {} ms", (System.nanoTime() - started) / 1_000_000L);
	}

	/** Within this far of a lesson's spot, the guide points at the spot itself, not along the route. */
	public static final double NEAR_SPOT = 8.0;

	/** Where on the route each lesson is, in course order (the route visits some places twice). */
	private static java.util.Map<TrainingLesson, Integer> stops;

	/** Where on the route this lesson's spot is. */
	public static int stop(final TrainingLesson lesson) {
		if (stops == null) {
			java.util.Map<TrainingLesson, Integer> found = new java.util.EnumMap<>(TrainingLesson.class);
			int cursor = 0;
			for (TrainingLesson each : TrainingLesson.values()) {
				int best = cursor;
				for (int i = cursor; i < ROUTE.size(); i++) {
					if (ROUTE.get(i).pos().distSqr(each.target) < ROUTE.get(best).pos().distSqr(each.target)) {
						best = i;
					}
				}
				found.put(each, best);
				cursor = best;
			}
			stops = found;
		}
		return stops.get(lesson);
	}

	/**
	 * Where the guide should lead a hunter at {@code at} who is making for this lesson: the end of
	 * the leg of the route they are nearest to, so the way goes over the bridges and up the stairs
	 * rather than straight at the spot across the water; near the spot, the spot itself.
	 */
	public static BlockPos nextStop(final Vec3 at, final TrainingLesson lesson, final BlockPos spot) {
		int goal = stop(lesson);
		if (goal == 0 || at.distanceTo(Vec3.atBottomCenterOf(spot)) <= NEAR_SPOT) {
			return spot;
		}
		int leg = 0;
		double nearest = Double.MAX_VALUE;
		for (int i = 0; i < goal; i++) {
			double d = toSegment(at, Vec3.atBottomCenterOf(ROUTE.get(i).pos()), Vec3.atBottomCenterOf(ROUTE.get(i + 1).pos()));
			// where the route goes back the way it came, the later leg is the one being walked
			if (d <= nearest + 1.0E-6) {
				nearest = d;
				leg = i;
			}
		}
		return leg + 1 >= goal ? spot : ROUTE.get(leg + 1).pos();
	}

	private static double toSegment(final Vec3 p, final Vec3 a, final Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len = ab.lengthSqr();
		double t = len == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	/** Test hook: builds the station again as if this level had been built by {@code version}. */
	public static void rebuildFrom(final ServerLevel level, final int version) {
		level.setAttached(BUILT_VERSION, version);
		ensureBuilt(level);
	}

	public static void markUpgraded(final ServerLevel level) {
		level.setAttached(UPGRADED, true);
	}

	public static boolean upgraded(final ServerLevel level) {
		return level.getAttachedOrElse(UPGRADED, false);
	}

	static void clearUpgraded(final ServerLevel level) {
		level.removeAttached(UPGRADED);
	}

	/** Takes the old quays, towers and rocks back down to open water. */
	private static void clearQuays(final Builder b) {
		b.fill(-36, -5, -166, 36, StationParts.WATERLINE, 20, Blocks.WATER);
		b.clear(-36, StationParts.WATERLINE + 1, -166, 36, 30, 20);
	}
}
