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
	/** Where a hunter wakes: the barracks, between the bunks. */
	public static final BlockPos ENTRY = new BlockPos(-24, 65, 17);

	/** Saves from before the station: the old chain of quays. */
	private static final AttachmentType<Boolean> BUILT = AttachmentRegistry.createPersistent(Mistinn.id("training_ground_built"), Codec.BOOL);
	private static final AttachmentType<Integer> BUILT_VERSION = AttachmentRegistry.createPersistent(Mistinn.id("training_ground_version"), Codec.INT);

	/** Where each member of the staff keeps their post, and which way they face. */
	public static final List<Post> POSTS = List.of(
		new Post(StaffRole.QUARTERMASTER, new BlockPos(5, 65, 4), 0.0F));

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
