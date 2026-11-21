package life.shizune.mistinn.training;

import com.mojang.serialization.Codec;
import life.shizune.mistinn.Mistinn;
import java.util.List;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.story.RadioBlock;
import life.shizune.mistinn.world.Builder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Rust Anchor's training yard, built on a chain of quays along the coast. */
public final class TrainingGround {
	public static final String PACK_ID = "mistinn:training";
	public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, Mistinn.id("training_ground"));

	private static final AttachmentType<Boolean> BUILT = AttachmentRegistry.createPersistent(Mistinn.id("training_ground_built"), Codec.BOOL);
	private static final Block HULL = Blocks.COPPER_BLOCK.waxed().oxidized();
	private static final Block FRAME = Blocks.CUT_COPPER.waxed().weathered();
	private static final Block RUST = Blocks.CUT_COPPER.waxed().exposed();
	private static final Block GRATE = Blocks.COPPER_GRATE.waxed().oxidized();
	private static final Block RAIL = Blocks.COPPER_BARS.waxed().oxidized();
	private static final Block BULB = Blocks.COPPER_BULB.waxed().exposed();
	private static final Block STONE = Blocks.DEEPSLATE_BRICKS;
	private static final Block BEAM = Blocks.POLISHED_DEEPSLATE;
	private static final int[] SIDES = {-1, 1};
	/** Where each member of the staff keeps their post, and which way they face. */
	public static final List<Post> POSTS = List.of(
		new Post(StaffRole.QUARTERMASTER, new BlockPos(-5, 65, -5), -90.0F));

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

	public static void ensureBuilt(final ServerLevel level) {
		if (!level.dimension().equals(KEY) || level.getAttachedOrElse(BUILT, false)) {
			return;
		}
		Builder b = new Builder(level, new BlockPos(0, 64, 0), RandomSource.create(0x52555354414E4348L));
		quay(b, 9, 9, 5, Blocks.POLISHED_ANDESITE);
		quay(b, -4, 8, 6, Blocks.SPRUCE_PLANKS);
		quay(b, -18, 8, 6, Blocks.SMOOTH_STONE);
		quay(b, -32, 10, 7, Blocks.TUFF_BRICKS);
		quay(b, -48, 9, 6, Blocks.POLISHED_ANDESITE);
		quay(b, -64, 12, 7, Blocks.TUFF_BRICKS);
		quay(b, -77, 8, 5, Blocks.DEEPSLATE_TILES);
		quay(b, -92, 10, 6, Blocks.POLISHED_ANDESITE);
		quay(b, -108, 11, 7, Blocks.DEEPSLATE_TILES);
		quay(b, -128, 10, 9, Blocks.POLISHED_DEEPSLATE);

		gangway(b, 2, 4);
		gangway(b, -12, -10);
		gangway(b, -25, -24);
		gangway(b, -42, -39);
		gangway(b, -57, -54);
		gangway(b, -72, -71);
		gangway(b, -86, -82);
		gangway(b, -101, -98);
		gangway(b, -119, -115);

		arrival(b);
		armory(b);
		rollLane(b);
		sparringRing(b);
		infirmary(b);
		bellTower(b);
		memorial(b);
		radioRoom(b);
		lampArena(b);
		deploymentPod(b);
		breakwater(b);
		b.settle();
		level.setAttached(BUILT, true);
		Mistinn.LOGGER.info("Built the hunter training ground");
	}

	private static boolean onQuay(final int x, final int z, final int width, final int depth) {
		return Math.abs(x) <= width && Math.abs(z) <= depth && Math.abs(x) + Math.abs(z) <= width + depth - 3;
	}

	private static void quay(final Builder b, final int centerZ, final int width, final int depth, final Block floor) {
		for (int dz = -depth; dz <= depth; dz++) {
			int z = centerZ + dz;
			for (int x = -width; x <= width; x++) {
				if (!onQuay(x, dz, width, depth)) {
					continue;
				}
				boolean edge = !onQuay(x + 1, dz, width, depth) || !onQuay(x - 1, dz, width, depth)
					|| !onQuay(x, dz + 1, width, depth) || !onQuay(x, dz - 1, width, depth);
				b.set(x, -2, z, edge ? Blocks.TUFF : Blocks.POLISHED_TUFF);
				b.set(x, -1, z, edge ? STONE : BEAM);
				b.set(x, 0, z, edge ? FRAME : floor);
				if (edge && Math.abs(x) > 2) {
					b.set(x, 1, z, (x + z) % 5 == 0 ? FRAME : RAIL);
				}
			}
		}
		// The outside ladder gives swimmers a way back onto each quay.
		b.set(width, 1, centerZ, Blocks.AIR);
		for (int y = -3; y <= 0; y++) {
			b.set(width, y, centerZ, BEAM);
			b.set(width + 1, y, centerZ, Blocks.LADDER.defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
				.setValue(BlockStateProperties.WATERLOGGED, y <= -2));
		}
	}

	private static void gangway(final Builder b, final int north, final int south) {
		b.fill(-3, -1, north, 3, -1, south, BEAM);
		b.fill(-3, 0, north, 3, 0, south, Blocks.DARK_OAK_PLANKS);
		for (int side : SIDES) {
			b.fill(side * 3, 0, north, side * 3, 0, south, FRAME);
			b.fill(side * 3, 1, north, side * 3, 1, south, RAIL);
			b.fill(side * 2, 0, north, side * 2, 0, south, GRATE);
			b.fill(side * 3, -4, north, side * 3, 0, north, BEAM);
			b.set(side * 3, 1, north, FRAME);
			b.set(side * 3, 2, north, Blocks.SOUL_LANTERN);
		}
		b.fill(0, 0, north, 0, 0, south, RUST);
	}

	private static void litBulb(final Builder b, final int x, final int y, final int z) {
		b.set(x, y, z, BULB.defaultBlockState().setValue(BlockStateProperties.LIT, true));
	}

	private static void column(final Builder b, final int x, final int z, final int height) {
		b.fill(x, 1, z, x, height, z, BEAM);
		b.set(x, 1, z, STONE);
		b.set(x, height, z, FRAME);
	}

	private static void arrival(final Builder b) {
		for (int side : SIDES) {
			b.fill(side * 3, 0, 5, side * 3, 0, 12, RUST);
			b.fill(side * 5, 1, 6, side * 5, 4, 6, STONE);
			b.fill(side * 4, 1, 6, side * 4, 8, 7, BEAM);
			b.fill(side * 6, 1, 8, side * 6, 3, 10, HULL);
			b.fill(side * 7, 0, 7, side * 7, 1, 11, FRAME);
			b.fill(side * 2, 6, 6, side * 3, 7, 7, FRAME);
			b.hanging(side * 2, 5, 6, Blocks.LANTERN);
			b.set(side * 4, 8, 8, GRATE);
			litBulb(b, side * 4, 6, 8);
		}
		b.fill(-4, 8, 6, 4, 8, 7, HULL);
		b.fill(-2, 8, 8, 2, 8, 8, RUST);
		b.fill(-2, 9, 6, 2, 9, 7, FRAME);
		b.set(0, 10, 6, Blocks.CHISELED_TUFF_BRICKS);
		for (int x = -2; x <= 2; x++) {
			b.set(x, 0, 10 - Math.abs(x), RUST);
		}
	}

	private static void armory(final Builder b) {
		b.fill(-7, 5, -9, 7, 5, 1, GRATE);
		b.fill(-5, 6, -9, 5, 6, 1, FRAME);
		b.fill(-3, 7, -9, 3, 7, 1, HULL);
		b.fill(-1, 7, -8, 1, 7, 0, Blocks.STAINED_GLASS.cyan());
		for (int side : SIDES) {
			b.fill(side * 6, 1, -8, side * 6, 3, 0, Blocks.STRIPPED_SPRUCE_WOOD);
			b.fill(side * 6, 3, -7, side * 6, 3, -1, Blocks.STAINED_GLASS.brown());
			b.fill(side * 4, 0, -9, side * 4, 0, 1, FRAME);
			b.fill(side * 5, 1, -8, side * 4, 1, -7, HULL);
			b.fill(side * 5, 1, -2, side * 4, 1, -1, BEAM);
			column(b, side * 6, -9, 5);
			column(b, side * 6, -4, 5);
			column(b, side * 6, 1, 5);
			b.hanging(side * 4, 5, -4, Blocks.LANTERN);
		}
		b.facing(-3, 1, -4, Blocks.CHEST, Direction.EAST);
		b.set(-4, 1, -4, RUST);
		b.set(-5, 1, -4, Blocks.SMITHING_TABLE);
		b.set(4, 1, -5, Blocks.CRAFTING_TABLE);
	}

	private static void rollLane(final Builder b) {
		b.fill(-4, 0, -23, 4, 0, -13, Blocks.SMOOTH_STONE);
		for (int side : SIDES) {
			b.fill(side * 5, 0, -23, side * 5, 0, -13, GRATE);
			b.fill(side * 6, 1, -22, side * 6, 1, -14, STONE);
			for (int z = -22; z <= -14; z += 4) {
				column(b, side * 7, z, 3);
				b.set(side * 7, 4, z, Blocks.LANTERN);
				b.set(side * 4, 0, z, RUST);
			}
		}
		for (int z : new int[] {-21, -17, -13}) {
			for (int x = -2; x <= 2; x++) {
				b.set(x, 0, z + Math.abs(x), RUST);
			}
		}
	}

	private static void ring(final Builder b, final int centerZ, final int radius, final Block band, final Block inner) {
		for (int dz = -radius; dz <= radius; dz++) {
			for (int x = -radius; x <= radius; x++) {
				int distance = x * x + dz * dz;
				if (distance <= radius * radius) {
					b.set(x, 0, centerZ + dz, distance > (radius - 1) * (radius - 1) ? band : inner);
				}
			}
		}
	}

	private static void sparringRing(final Builder b) {
		ring(b, -32, 6, RUST, Blocks.POLISHED_TUFF);
		ring(b, -32, 2, Blocks.CHISELED_TUFF, Blocks.SMOOTH_STONE);
		for (int side : SIDES) {
			b.fill(side * 8, 2, -36, side * 8, 2, -28, STONE);
			b.fill(side * 9, 3, -35, side * 9, 3, -29, BEAM);
			b.fill(side * 9, 5, -36, side * 9, 5, -28, FRAME);
			b.fill(side * 8, 5, -36, side * 7, 5, -28, HULL);
			for (int z : new int[] {-36, -28}) {
				column(b, side * 8, z, 5);
				b.hanging(side * 7, 4, z, Blocks.LANTERN);
				b.stairs(side * 7, 1, z, Blocks.POLISHED_DEEPSLATE_STAIRS, side > 0 ? Direction.EAST : Direction.WEST, false);
			}
		}
	}

	private static void infirmary(final Builder b) {
		b.fill(-7, 5, -53, 7, 5, -43, Blocks.SMOOTH_QUARTZ);
		b.fill(-5, 6, -53, 5, 6, -43, HULL);
		b.fill(-1, 6, -52, 1, 6, -44, Blocks.STAINED_GLASS.lightBlue());
		b.fill(-1, 5, -52, 1, 5, -44, GRATE);
		b.fill(-2, 0, -49, 2, 0, -47, Blocks.DYED_TERRACOTTA.cyan());
		b.fill(0, 0, -51, 0, 0, -45, Blocks.DYED_TERRACOTTA.cyan());
		b.set(4, 1, -50, Blocks.CRAFTING_TABLE);
		for (int side : SIDES) {
			b.fill(side * 7, 1, -52, side * 7, 4, -44, Blocks.CALCITE);
			b.fill(side * 7, 2, -51, side * 7, 3, -45, Blocks.STAINED_GLASS.lightBlue());
			b.fill(side * 6, 1, -51, side * 5, 1, -50, Blocks.SMOOTH_QUARTZ);
			b.bed(side * 6, 1, -48, Blocks.BED.white(), side > 0 ? Direction.EAST : Direction.WEST);
			b.fill(side * 6, 1, -46, side * 5, 1, -45, Blocks.SMOOTH_QUARTZ);
			for (int z : new int[] {-52, -44}) {
				column(b, side * 6, z, 5);
				b.hanging(side * 3, 4, z, Blocks.LANTERN);
			}
		}
	}

	private static void bellTower(final Builder b) {
		ring(b, -64, 6, Blocks.GOLD_BLOCK, Blocks.POLISHED_DEEPSLATE);
		ring(b, -64, 3, RUST, Blocks.CHISELED_TUFF_BRICKS);
		b.set(0, 1, -64, MistinnBlocks.GOLDEN_BELL);
		for (int side : SIDES) {
			b.fill(side * 5, 7, -68, side * 5, 7, -60, FRAME);
			b.fill(side * 4, 8, -67, side * 4, 18, -61, HULL);
			b.fill(side * 4, 9, -65, side * 4, 17, -63, Blocks.STAINED_GLASS.cyan());
			b.fill(-4, 8, -64 + side * 3, 4, 18, -64 + side * 3, HULL);
			b.fill(-2, 10, -64 + side * 3, 2, 16, -64 + side * 3, Blocks.STAINED_GLASS.cyan());
			for (int z : new int[] {-68, -60}) {
				column(b, side * 5, z, 8);
				b.fill(side * 4, 10, z, side * 4, 15, z, GRATE);
				b.fill(side * 6, 1, z, side * 6, 4, z, STONE);
				b.set(side * 6, 5, z, Blocks.SOUL_LANTERN);
				b.hanging(side * 5, 6, z, Blocks.LANTERN);
			}
		}
		b.fill(-5, 8, -68, 5, 8, -60, FRAME);
		b.fill(-3, 8, -66, 3, 8, -62, GRATE);
		b.fill(-5, 19, -68, 5, 19, -60, RUST);
		b.fill(-4, 20, -67, 4, 23, -61, Blocks.STAINED_GLASS.yellow());
		b.fill(-3, 20, -66, 3, 23, -62, Blocks.AIR);
		b.fill(-1, 20, -65, 1, 23, -63, Blocks.SHROOMLIGHT);
		b.fill(-5, 24, -68, 5, 24, -60, FRAME);
		b.fill(-4, 25, -67, 4, 25, -61, HULL);
		b.fill(-3, 26, -66, 3, 26, -62, HULL);
		b.fill(-2, 27, -65, 2, 27, -63, GRATE);
		b.set(0, 28, -64, Blocks.GOLD_BLOCK);
		b.facing(0, 29, -64, Blocks.LIGHTNING_ROD.waxed().oxidized(), Direction.UP);
		for (int side : SIDES) {
			for (int dz : SIDES) {
				b.fill(side * 4, 20, -64 + dz * 3, side * 4, 23, -64 + dz * 3, FRAME);
			}
		}
	}

	private static void memorial(final Builder b) {
		ring(b, -77, 4, Blocks.CUT_SANDSTONE, Blocks.POLISHED_TUFF);
		b.set(0, 0, -77, Blocks.GOLD_BLOCK);
		for (int side : SIDES) {
			b.fill(side * 6, 1, -80, side * 6, 1, -74, STONE);
			b.fill(side * 6, 2, -79, side * 6, 2, -75, Blocks.POLISHED_BLACKSTONE);
			b.fill(side * 6, 3, -78, side * 6, 3, -76, Blocks.CHISELED_POLISHED_BLACKSTONE);
			b.set(side * 6, 4, -77, Blocks.SOUL_LANTERN);
			b.set(side * 4, 0, -77, Blocks.SEA_LANTERN);
		}
	}

	private static void radioRoom(final Builder b) {
		b.fill(-8, 6, -97, 8, 6, -87, HULL);
		b.fill(-6, 7, -97, 6, 7, -87, FRAME);
		b.fill(-2, 7, -97, 2, 7, -87, GRATE);
		for (int side : SIDES) {
			b.fill(side * 7, 1, -96, side * 7, 5, -88, STONE);
			b.fill(side * 7, 2, -95, side * 7, 4, -89, Blocks.STAINED_GLASS.cyan());
			b.fill(side * 4, 1, -94, side * 3, 1, -90, HULL);
			b.fill(side * 3, 2, -93, side * 3, 2, -91, GRATE);
			column(b, side * 7, -96, 6);
			column(b, side * 7, -88, 6);
			b.hanging(side * 2, 5, -90, Blocks.LANTERN);
			b.hanging(side * 2, 5, -94, Blocks.LANTERN);
			b.fill(side * 3, 8, -92, side * 3, 12, -92, FRAME);
			b.fill(side * 2, 12, -92, side * 2, 15, -92, HULL);
			b.fill(side, 9, -92, side, 15, -92, GRATE);
			b.fill(side * 7, 12, -92, side * 2, 12, -92, GRATE);
			litBulb(b, side * 7, 12, -92);
		}
		b.fill(-2, 0, -93, 2, 0, -91, RUST);
		b.set(0, 1, -92, MistinnBlocks.RADIO.defaultBlockState().setValue(RadioBlock.FACING, Direction.SOUTH));
		b.fill(0, 15, -92, 0, 18, -92, FRAME);
		b.facing(0, 19, -92, Blocks.LIGHTNING_ROD.waxed().oxidized(), Direction.UP);
	}

	private static void lampArena(final Builder b) {
		ring(b, -108, 6, FRAME, Blocks.POLISHED_BLACKSTONE_BRICKS);
		for (int side : SIDES) {
			b.fill(side * 9, 6, -112, side * 9, 6, -104, HULL);
			b.fill(side * 8, 7, -112, side * 8, 7, -104, GRATE);
			b.fill(side * 10, 2, -111, side * 10, 4, -105, RAIL);
			litBulb(b, side * 10, 1, -108);
			for (int z : new int[] {-113, -103}) {
				b.set(side * 4, 0, z, Blocks.CHISELED_TUFF);
				b.set(side * 4, 1, z, MistinnBlocks.LAMP_STAND);
				b.set(side * 5, 0, z, GRATE);
			}
			for (int z : new int[] {-112, -104}) {
				column(b, side * 9, z, 6);
				b.fill(side * 8, 6, z, side * 6, 6, z, FRAME);
				b.hanging(side * 6, 5, z, Blocks.SOUL_LANTERN);
			}
		}
		b.set(0, 0, -108, Blocks.CHISELED_POLISHED_BLACKSTONE);
	}

	private static void deploymentPod(final Builder b) {
		b.fill(-3, 0, -133, 3, 0, -125, HULL);
		b.fill(-1, 0, -131, 1, 0, -125, GRATE);
		b.fill(-2, 1, -132, 2, 3, -132, BEAM);
		b.fill(-1, 4, -132, 1, 4, -126, HULL);
		b.fill(-1, 5, -131, 1, 5, -127, FRAME);
		b.fill(-1, 3, -126, 1, 3, -126, RUST);
		b.set(0, 2, -132, Blocks.GOLD_BLOCK);
		b.facing(0, 2, -131, Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.SOUTH);
		b.set(0, 4, -131, Blocks.SEA_LANTERN);
		b.set(0, 0, -129, RUST);
		b.fill(-2, 0, -121, 2, 0, -121, RUST);
		b.set(0, 1, -121, MistinnBlocks.RADIO.defaultBlockState()
			.setValue(RadioBlock.FACING, Direction.SOUTH).setValue(RadioBlock.LIT, true));
		for (int side : SIDES) {
			b.fill(side * 2, 1, -132, side * 2, 3, -126, HULL);
			b.fill(side * 2, 2, -131, side * 2, 2, -127, Blocks.STAINED_GLASS.cyan());
			b.fill(side * 2, 4, -131, side * 2, 4, -127, FRAME);
			b.fill(side * 3, 1, -131, side * 3, 1, -127, RUST);
			b.fill(side * 3, 2, -130, side * 3, 2, -128, GRATE);
			b.fill(side * 5, -3, -134, side * 5, 8, -126, BEAM);
			b.fill(side * 5, 1, -132, side * 5, 5, -128, Blocks.AIR);
			b.fill(side * 5, 7, -133, side * 3, 7, -127, FRAME);
			b.fill(side * 6, 9, -133, side * 6, 9, -127, RUST);
			b.fill(side * 3, 6, -132, side * 3, 6, -128, Blocks.IRON_BLOCK);
			b.fill(side * 3, 1, -123, side * 3, 1, -120, HULL);
			b.fill(side * 4, 1, -122, side * 4, 3, -122, STONE);
			b.set(side * 4, 4, -122, Blocks.LANTERN);
			litBulb(b, side * 2, 1, -126);
			litBulb(b, side * 5, 6, -126);
		}
		b.fill(-5, 9, -130, 5, 9, -129, BEAM);
		b.fill(-3, 8, -130, 3, 8, -129, FRAME);
		b.axis(0, 7, -129, Blocks.IRON_CHAIN, Direction.Axis.Y);
		b.axis(0, 6, -129, Blocks.IRON_CHAIN, Direction.Axis.Y);
	}

	private static void breakwater(final Builder b) {
		reef(b, -15, 6, 5, 14, 4);
		reef(b, 16, -14, 4, 11, 2);
		reef(b, -18, -45, 6, 17, 5);
		reef(b, 18, -69, 4, 15, 3);
		reef(b, -16, -96, 5, 14, 4);
		reef(b, 17, -119, 5, 13, 4);
		reef(b, -9, -142, 4, 10, 2);
		reef(b, 10, -147, 5, 10, 4);
		reef(b, -25, -73, 9, 19, 21);
		reef(b, 27, -105, 8, 16, 17);
		reef(b, -22, -145, 10, 20, 26);
	}

	private static void reef(final Builder b, final int x, final int z, final int width, final int length, final int height) {
		for (int dx = -width; dx <= width; dx++) {
			for (int dz = -length; dz <= length; dz++) {
				double distance = Math.pow(dx / (double)width, 2) + Math.pow(dz / (double)length, 2);
				if (distance >= 1) {
					continue;
				}
				int top = (int)Math.round(height * (1 - distance)) - b.random().nextInt(3);
				b.fill(x + dx, -5, z + dz, x + dx, top - 1, z + dz, b.pick(Blocks.TUFF, Blocks.BASALT, Blocks.DEEPSLATE));
				b.set(x + dx, top, z + dz, b.pick(Blocks.TUFF, Blocks.POLISHED_TUFF, Blocks.COBBLED_DEEPSLATE));
			}
		}
	}
}
