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
	public static final int VERSION = 2;
	/** Where a hunter heard in the listening pen is sent back to: its west door. */
	public static final BlockPos SNEAK_START = new BlockPos(-3, 77, -60);
	/** Where a hunter wakes: the barracks, between the bunks. */
	public static final BlockPos ENTRY = new BlockPos(-24, 65, 17);

	/** Saves from before the station: the old chain of quays. */
	private static final AttachmentType<Boolean> BUILT = AttachmentRegistry.createPersistent(Mistinn.id("training_ground_built"), Codec.BOOL);
	private static final AttachmentType<Integer> BUILT_VERSION = AttachmentRegistry.createPersistent(Mistinn.id("training_ground_version"), Codec.INT);

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

	public record Post(StaffRole role, BlockPos pos, float yaw) {
	}

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
		if (level.getAttachedOrElse(BUILT, false)) {
			clearQuays(b);
			level.removeAttached(BUILT);
		}
		StationMap.build(b);
		level.setAttached(BUILT_VERSION, VERSION);
		Mistinn.LOGGER.info("Built Rust Anchor Station in {} ms", (System.nanoTime() - started) / 1_000_000L);
	}

	/** Takes the old quays, towers and rocks back down to open water. */
	private static void clearQuays(final Builder b) {
		b.fill(-36, -5, -166, 36, StationParts.WATERLINE, 20, Blocks.WATER);
		b.clear(-36, StationParts.WATERLINE + 1, -166, 36, 30, 20);
	}
}
