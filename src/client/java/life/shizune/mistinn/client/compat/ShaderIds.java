package life.shizune.mistinn.client.compat;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import life.shizune.mistinn.registry.MistinnBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

/**
 * Shader packs (run by Iris) give their effects block by block: which blocks sway in the wind, which glow, which shine as
 * metal or stone. A pack names vanilla's blocks, not the mod's, so the mod's would get none of it. When Iris reads a pack,
 * each of the mod's blocks the pack has not named is told to it as the vanilla block it is most like, state by state
 * (property by property): the mist pines' leaves sway as leaves, the jade grass, ferns and reeds wave, the crystal glints,
 * the logs and planks are wood, the facilities metal. The Complementary packs (the launcher's own shaders) keep slots for
 * a modded block's glow and a modded ore: there the lit facilities and the crystal take the glow (the pack lights up their
 * bright pixels), and the crystal vein ore the ore. The pack's own files are never touched; nothing it names is changed.
 */
public final class ShaderIds {
	/** Complementary's slots for a modded block that gives light, and for a modded ore. */
	public static final int GLOW = 10028;
	public static final int ORE = 10024;
	/** Where Complementary puts two vanilla blocks, by which its packs are known. */
	private static final int AMETHYST = 10332;
	private static final int SEA_LANTERN = 10448;

	/**
	 * What one of the mod's blocks is told as: a vanilla block it is like, and, for Complementary, a slot from a given
	 * light; with {@code litOnly}, only its states giving light are told at all (a lamp unlit is not a lantern).
	 */
	private record Like(@Nullable Block block, boolean glows, boolean ore, int glowFrom, boolean litOnly) {
		Like(final @Nullable Block block, final boolean glows, final boolean ore, final int glowFrom) {
			this(block, glows, ore, glowFrom, false);
		}
	}

	private static @Nullable Map<Block, Like> likes;

	private ShaderIds() {
	}

	private static Map<Block, Like> likes() {
		if (likes != null) {
			return likes;
		}
		Map<Block, Like> map = new LinkedHashMap<>();
		// Terra: the ground, the stone, the wood, the growth, the crystal
		map.put(MistinnBlocks.JADE_TURF, like(Blocks.GRASS_BLOCK));
		map.put(MistinnBlocks.VEIN_SOIL, like(Blocks.DIRT));
		map.put(MistinnBlocks.VEIN_STONE, like(Blocks.STONE));
		map.put(MistinnBlocks.VEIN_STONE_POLISHED, like(Blocks.POLISHED_ANDESITE));
		map.put(MistinnBlocks.VEIN_STONE_BRICKS, like(Blocks.STONE_BRICKS));
		map.put(MistinnBlocks.VEIN_CRYSTAL_ORE, new Like(Blocks.EMERALD_ORE, false, true, 0));
		map.put(MistinnBlocks.MIST_PINE_LOG, like(Blocks.SPRUCE_LOG));
		map.put(MistinnBlocks.MIST_PINE_PLANKS, like(Blocks.SPRUCE_PLANKS));
		map.put(MistinnBlocks.STRIPPED_MIST_PINE_LOG, like(Blocks.STRIPPED_SPRUCE_LOG));
		map.put(MistinnBlocks.MIST_PINE_SIGN, like(Blocks.SPRUCE_SIGN));
		map.put(MistinnBlocks.MIST_PINE_WALL_SIGN, like(Blocks.SPRUCE_WALL_SIGN));
		map.put(MistinnBlocks.MIST_PINE_HANGING_SIGN, like(Blocks.SPRUCE_HANGING_SIGN));
		map.put(MistinnBlocks.MIST_PINE_WALL_HANGING_SIGN, like(Blocks.SPRUCE_WALL_HANGING_SIGN));
		map.put(MistinnBlocks.MIST_PINE_STAIRS, like(Blocks.SPRUCE_STAIRS));
		map.put(MistinnBlocks.MIST_PINE_SLAB, like(Blocks.SPRUCE_SLAB));
		map.put(MistinnBlocks.MIST_PINE_FENCE, like(Blocks.SPRUCE_FENCE));
		map.put(MistinnBlocks.MIST_PINE_FENCE_GATE, like(Blocks.SPRUCE_FENCE_GATE));
		map.put(MistinnBlocks.MIST_PINE_DOOR, like(Blocks.SPRUCE_DOOR));
		map.put(MistinnBlocks.MIST_PINE_TRAPDOOR, like(Blocks.SPRUCE_TRAPDOOR));
		map.put(MistinnBlocks.MIST_PINE_BUTTON, like(Blocks.SPRUCE_BUTTON));
		map.put(MistinnBlocks.MIST_PINE_PRESSURE_PLATE, like(Blocks.SPRUCE_PRESSURE_PLATE));
		map.put(MistinnBlocks.MIST_PINE_LEAVES, like(Blocks.SPRUCE_LEAVES));
		map.put(MistinnBlocks.JADE_GRASS, like(Blocks.SHORT_GRASS));
		map.put(MistinnBlocks.JADE_FERN, like(Blocks.FERN));
		map.put(MistinnBlocks.MIST_REED, like(Blocks.TALL_GRASS));
		map.put(MistinnBlocks.CRYSTAL_SPROUT, like(Blocks.WHEAT));
		for (Block crystal : new Block[] {MistinnBlocks.TERRA_CRYSTAL_SMALL_BUD, MistinnBlocks.TERRA_CRYSTAL_MEDIUM_BUD, MistinnBlocks.TERRA_CRYSTAL_LARGE_BUD,
			MistinnBlocks.TERRA_CRYSTAL_CLUSTER, MistinnBlocks.RIFT_CRYSTAL_BUD, MistinnBlocks.RIFT_CRYSTAL_CLUSTER}) {
			map.put(crystal, glow(Blocks.AMETHYST_CLUSTER, 1));
		}
		// the crown's spires glow softly through: as amethyst they would stand dull grey at dusk
		map.put(MistinnBlocks.TERRA_CRYSTAL_BLOCK, glow(Blocks.AMETHYST_BLOCK, 1));
		map.put(MistinnBlocks.RIFT_CRYSTAL_BLOCK, glow(Blocks.AMETHYST_BLOCK, 1));
		map.put(MistinnBlocks.LATTICE_CRYSTAL, glow(Blocks.AMETHYST_BLOCK, 1));
		map.put(MistinnBlocks.LATTICE_CRYSTAL_DIM, like(Blocks.CALCITE));
		// the rifts
		map.put(MistinnBlocks.RIFT_TURF, like(Blocks.CRIMSON_NYLIUM));
		map.put(MistinnBlocks.RIFT_SOIL, like(Blocks.DIRT));
		map.put(MistinnBlocks.SCORCHED_STONE, like(Blocks.BLACKSTONE));
		map.put(MistinnBlocks.CRIMSON_MOSS, like(Blocks.MOSS_BLOCK));
		map.put(MistinnBlocks.CRIMSON_MOSS_CARPET, like(Blocks.MOSS_CARPET));
		map.put(MistinnBlocks.BLOOD_THORN, like(Blocks.CRIMSON_ROOTS));
		map.put(MistinnBlocks.CHARRED_LOG, like(Blocks.DARK_OAK_LOG));
		// the tillers' facilities: metal, and their lights when lit
		map.put(MistinnBlocks.FACILITY_DECK, like(Blocks.POLISHED_ANDESITE));
		map.put(MistinnBlocks.FACILITY_DECK_EDGE, like(Blocks.POLISHED_ANDESITE));
		map.put(MistinnBlocks.FACILITY_DECK_SLAB, like(Blocks.POLISHED_ANDESITE_SLAB));
		map.put(MistinnBlocks.FACILITY_PANEL, like(Blocks.IRON_BLOCK));
		map.put(MistinnBlocks.FACILITY_FRAME, like(Blocks.IRON_BLOCK));
		map.put(MistinnBlocks.FACILITY_CRATE, like(Blocks.SPRUCE_PLANKS));
		map.put(MistinnBlocks.HAZARD_STRIPE, like(Blocks.SMOOTH_STONE));
		map.put(MistinnBlocks.FACILITY_LAMP, glow(Blocks.SEA_LANTERN, 1));
		for (Block facility : new Block[] {MistinnBlocks.FACILITY_CONDUIT, MistinnBlocks.HUB_CORE, MistinnBlocks.HUB_SPIRE, MistinnBlocks.REFINERY,
			MistinnBlocks.ROOF_VENT, MistinnBlocks.WORKSHOP_WALL, MistinnBlocks.WORKBENCH, MistinnBlocks.TURRET, MistinnBlocks.RELAY_PYLON}) {
			map.put(facility, glow(Blocks.IRON_BLOCK, 4));
		}
		// the shore's and the inn's
		map.put(MistinnBlocks.LAMP_STAND, new Like(Blocks.LANTERN, false, false, 1, true));
		map.put(MistinnBlocks.RADIO, glow(null, 1));
		map.put(MistinnBlocks.MARKER_BEACON, glow(null, 1));
		map.put(MistinnBlocks.GRASS_REDSTONE_SOIL, like(Blocks.GRASS_BLOCK));
		map.put(MistinnBlocks.REDSTONE_SOIL, like(Blocks.DIRT));
		// Udasru's
		map.put(MistinnBlocks.UDASRU_STONE_BRICKS, like(Blocks.STONE_BRICKS));
		map.put(MistinnBlocks.UDASRU_STONE_BRICK_STAIRS, like(Blocks.STONE_BRICK_STAIRS));
		map.put(MistinnBlocks.UDASRU_STONE_BRICK_SLAB, like(Blocks.STONE_BRICK_SLAB));
		map.put(MistinnBlocks.UDASRU_STONE_BRICK_WALL, like(Blocks.STONE_BRICK_WALL));
		map.put(MistinnBlocks.UDASRU_ROOF_TILES, like(Blocks.DEEPSLATE_TILES));
		map.put(MistinnBlocks.UDASRU_ROOF_TILE_STAIRS, like(Blocks.DEEPSLATE_TILE_STAIRS));
		map.put(MistinnBlocks.UDASRU_ROOF_TILE_SLAB, like(Blocks.DEEPSLATE_TILE_SLAB));
		map.put(MistinnBlocks.UDASRU_PLASTER, like(Blocks.CALCITE));
		map.put(MistinnBlocks.UDASRU_TIMBER, like(Blocks.DARK_OAK_LOG));
		map.put(MistinnBlocks.SALT_CRUST, like(Blocks.CALCITE));
		map.put(MistinnBlocks.RUST_SOIL, like(Blocks.COARSE_DIRT));
		map.put(MistinnBlocks.BELL_BRONZE, like(Blocks.COPPER_BLOCK.waxed().unaffected()));
		likes = map;
		return map;
	}

	private static Like like(final Block block) {
		return new Like(block, false, false, 0);
	}

	private static Like glow(final @Nullable Block block, final int from) {
		return new Like(block, true, false, from);
	}

	/** Whether a pack's block ids are Complementary's (Reimagined or Unbound, with or without patches). */
	public static boolean complementary(final Object2IntMap<BlockState> ids) {
		return ids.getInt(Blocks.AMETHYST_CLUSTER.defaultBlockState()) == AMETHYST && ids.getInt(Blocks.SEA_LANTERN.defaultBlockState()) == SEA_LANTERN;
	}

	/** Tells a pack's block ids each of the mod's block states it does not name. Returns how many states were told. */
	public static int alias(final Object2IntMap<BlockState> ids) {
		boolean complementary = complementary(ids);
		int told = 0;
		for (Map.Entry<Block, Like> entry : likes().entrySet()) {
			Like like = entry.getValue();
			for (BlockState state : entry.getKey().getStateDefinition().getPossibleStates()) {
				if (ids.containsKey(state) || like.litOnly() && state.getLightEmission() < like.glowFrom()) {
					continue;
				}
				int id = ids.defaultReturnValue();
				if (complementary && like.ore()) {
					id = ORE;
				} else if (complementary && like.glows() && state.getLightEmission() >= like.glowFrom()) {
					id = GLOW;
				} else if (like.block() != null) {
					id = ids.getInt(match(state, like.block()));
				}
				if (id != ids.defaultReturnValue()) {
					ids.put(state, id);
					told++;
				}
			}
		}
		return told;
	}

	/** The mod's entities a pack should take as vanilla's: its boats as boats (so no water shows inside them). */
	private static final String[][] ENTITIES = {{"mist_pine_boat", "spruce_boat"}, {"mist_pine_chest_boat", "spruce_chest_boat"}};
	/** The mod's lights, held in the hand, in the colour of vanilla's nearest: jade crystal and lamps cool, rift crystal red. */
	private static final String[][] ITEMS = {{"facility_lamp", "sea_lantern"}, {"lattice_crystal", "sea_lantern"}, {"terra_crystal_block", "sea_lantern"},
		{"terra_crystal_cluster", "sea_lantern"}, {"terra_crystal_large_bud", "sea_lantern"}, {"terra_crystal_medium_bud", "sea_lantern"},
		{"terra_crystal_small_bud", "sea_lantern"}, {"rift_crystal_cluster", "redstone_torch"}, {"rift_crystal_bud", "redstone_torch"},
		{"rift_crystal_block", "redstone_torch"}, {"lamp_stand", "lantern"}, {"mist_pine_boat", "spruce_boat"}, {"mist_pine_chest_boat", "spruce_chest_boat"}};

	/** For the boats as entities. */
	public static <K> int aliasEntities(final Object2IntMap<K> ids, final BiFunction<String, String, K> key) {
		return aliasNamed(ENTITIES, ids, key);
	}

	/** For the lights as held items. */
	public static <K> int aliasItems(final Object2IntMap<K> ids, final BiFunction<String, String, K> key) {
		return aliasNamed(ITEMS, ids, key);
	}

	/**
	 * Tells a pack's ids (of entities, or of items) the mod's it does not name, keyed as the pack keys them ({@code key}
	 * makes a key of a namespace and a name). Returns how many were told.
	 */
	private static <K> int aliasNamed(final String[][] pairs, final Object2IntMap<K> ids, final BiFunction<String, String, K> key) {
		int told = 0;
		for (String[] pair : pairs) {
			K ours = key.apply(life.shizune.mistinn.Mistinn.MOD_ID, pair[0]);
			int id = ids.getInt(key.apply("minecraft", pair[1]));
			if (!ids.containsKey(ours) && id != ids.defaultReturnValue()) {
				ids.put(ours, id);
				told++;
			}
		}
		return told;
	}

	/** The vanilla block's state most like one of the mod's: each property they share set the same. */
	static BlockState match(final BlockState from, final Block to) {
		BlockState into = to.defaultBlockState();
		for (Property<?> theirs : into.getProperties()) {
			for (Property<?> ours : from.getProperties()) {
				if (ours.getName().equals(theirs.getName())) {
					into = set(into, theirs, valueName(from, ours));
				}
			}
		}
		return into;
	}

	private static <T extends Comparable<T>> String valueName(final BlockState state, final Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static <T extends Comparable<T>> BlockState set(final BlockState state, final Property<T> property, final String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}
}
