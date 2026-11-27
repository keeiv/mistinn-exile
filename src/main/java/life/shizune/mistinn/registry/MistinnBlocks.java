package life.shizune.mistinn.registry;

import java.util.function.Function;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.block.GoldenBellBlock;
import life.shizune.mistinn.block.GravestoneBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The mod's blocks. */
public final class MistinnBlocks {
	/** 金鐘: the save point. Made of a matter the border world does not have; it cannot be crafted. */
	public static final Block GOLDEN_BELL = register(
		"golden_bell",
		p -> new GoldenBellBlock(p, false),
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.GOLD)
			.strength(-1.0F, 3600000.0F)
			.sound(SoundType.ANVIL)
			.lightLevel(state -> state.getValue(GoldenBellBlock.OILED) ? 7 : 0)
			.noOcclusion()
			.pushReaction(PushReaction.IMMOVEABLE)
	);
	/** 一次性金鐘: cast from ordinary gold ore; brings you back once, then cracks. */
	public static final Block ORE_BELL = register(
		"ore_bell",
		p -> new GoldenBellBlock(p, true),
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.GOLD)
			.strength(0.6F, 1.0F)
			.sound(SoundType.METAL)
			.lightLevel(state -> 1)
			.noOcclusion()
			.pushReaction(PushReaction.POPPED)
	);
	/** The gravestone left where a hunter fell; it holds the residue of their salt. */
	public static final Block GRAVESTONE = register(
		"gravestone",
		GravestoneBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.QUARTZ)
			.strength(-1.0F, 3600000.0F)
			.sound(SoundType.STONE)
			.lightLevel(state -> 4)
			.noOcclusion()
			.noLootTable()
			.pushReaction(PushReaction.IMMOVEABLE)
	);

	/** 燈台: lamp stands around a lamp room; lit by hand, snuffed by the keeper. */
	public static final Block LAMP_STAND = register(
		"lamp_stand",
		life.shizune.mistinn.keeper.LampStandBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL)
			.strength(-1.0F, 3600000.0F)
			.sound(SoundType.LANTERN)
			.lightLevel(state -> state.getValue(life.shizune.mistinn.keeper.LampStandBlock.LIT) ? 15 : 0)
			.noOcclusion()
			.noLootTable()
			.pushReaction(PushReaction.IMMOVEABLE)
	);

	/** 投放站無線電: every intact drop station's radio; mended with redstone, it carries the story's calls. */
	public static final Block RADIO = register(
		"radio",
		life.shizune.mistinn.story.RadioBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL)
			.strength(-1.0F, 3600000.0F)
			.sound(SoundType.METAL)
			.lightLevel(state -> state.getValue(life.shizune.mistinn.story.RadioBlock.LIT) ? 6 : 0)
			.noLootTable()
			.pushReaction(PushReaction.IMMOVEABLE)
	);

	/** 標記信標: a lamp on a stake, planted to mark a place for the team. */
	public static final Block MARKER_BEACON = register(
		"marker_beacon",
		life.shizune.mistinn.team.MarkerBeaconBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL)
			.strength(0.5F, 3.0F)
			.sound(SoundType.LANTERN)
			.lightLevel(state -> 10)
			.noOcclusion()
			.pushReaction(PushReaction.POPPED)
	);

	/** 草紅石息壤: redstone soil under grass, found on the surface. */
	public static final Block GRASS_REDSTONE_SOIL = register(
		"grass_redstone_soil",
		life.shizune.mistinn.block.GrassRedstoneSoilBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.GRASS)
			.strength(0.6F)
			.sound(SoundType.GRASS)
			.randomTicks()
			.lightLevel(state -> state.getValue(life.shizune.mistinn.block.RedstoneSoilBlock.AGE) == life.shizune.mistinn.block.RedstoneSoilBlock.RIPE ? 7 : 0)
	);

	/** 泥紅石息壤: earth that breeds redstone, ripening by itself; found in the dirt layers, and crafted. */
	public static final Block REDSTONE_SOIL = register(
		"redstone_soil",
		life.shizune.mistinn.block.RedstoneSoilBlock::new,
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_RED)
			.strength(0.6F)
			.sound(SoundType.ROOTED_DIRT)
			.randomTicks()
			.lightLevel(state -> state.getValue(life.shizune.mistinn.block.RedstoneSoilBlock.AGE) == life.shizune.mistinn.block.RedstoneSoilBlock.RIPE ? 7 : 0)
	);

	// ── The sky rift's ground ─────────────────────────────────────

	/** 赤壤: the scabbed surface of ground a sky rift has seared. */
	public static final Block RIFT_TURF = register("rift_turf", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.CRIMSON_NYLIUM).strength(0.6F).sound(SoundType.ROOTED_DIRT));
	/** 赤土: the earth under it. */
	public static final Block RIFT_SOIL = register("rift_soil", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.TERRACOTTA_RED).strength(0.5F).sound(SoundType.ROOTED_DIRT));
	/** 焦岩: stone the rift's heat has blackened. */
	public static final Block SCORCHED_STONE = register("scorched_stone", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(1.5F, 6.0F).sound(SoundType.BASALT));
	/** 赤苔: crimson moss over everything, as a block and as a carpet. */
	public static final Block CRIMSON_MOSS = register("crimson_moss", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.CRIMSON_NYLIUM).strength(0.1F).sound(SoundType.MOSS));
	public static final Block CRIMSON_MOSS_CARPET = register("crimson_moss_carpet", net.minecraft.world.level.block.CarpetBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.CRIMSON_NYLIUM).strength(0.1F).sound(SoundType.MOSS_CARPET).pushReaction(PushReaction.POPPED));
	/** 血棘: thorns that tear at whatever walks through them. */
	public static final Block BLOOD_THORN = register("blood_thorn", life.shizune.mistinn.block.BloodThornBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.CRIMSON_STEM).noCollision().instabreak().sound(SoundType.SWEET_BERRY_BUSH)
		.offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED));
	/** 裂晶: the rift's crystal, a bud, a full cluster and a block of it; all of it glows. */
	public static final Block RIFT_CRYSTAL_BUD = register("rift_crystal_bud", p -> new net.minecraft.world.level.block.AmethystClusterBlock(3.0F, 4.0F, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).forceSolidOn().noOcclusion().strength(1.5F)
			.sound(SoundType.SMALL_AMETHYST_BUD).lightLevel(state -> 3).pushReaction(PushReaction.POPPED));
	public static final Block RIFT_CRYSTAL_CLUSTER = register("rift_crystal_cluster", p -> new net.minecraft.world.level.block.AmethystClusterBlock(7.0F, 3.0F, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).forceSolidOn().noOcclusion().strength(1.5F)
			.sound(SoundType.AMETHYST_CLUSTER).lightLevel(state -> 7).pushReaction(PushReaction.POPPED));
	public static final Block RIFT_CRYSTAL_BLOCK = register("rift_crystal_block", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_RED).requiresCorrectToolForDrops().strength(1.5F).sound(SoundType.AMETHYST).lightLevel(state -> 5));
	/** 焦木: wood the rift burned black; its herald comes down on an altar of it. */
	public static final Block CHARRED_LOG = register("charred_log", net.minecraft.world.level.block.RotatedPillarBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BLACK).strength(2.0F).sound(SoundType.WOOD));

	// ── Terra ─────────────────────────────────────────────────────

	/** 玉草地: the jade turf of the Terra highlands. */
	public static final Block JADE_TURF = register("jade_turf", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.EMERALD).strength(0.6F).sound(SoundType.GRASS));
	/** 晶脈土: grey-violet earth flecked with crystal. */
	public static final Block VEIN_SOIL = register("vein_soil", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.TERRACOTTA_PURPLE).strength(0.5F).sound(SoundType.ROOTED_DIRT));
	/** 晶脈岩: pale stone veined with crystal, as it lies, polished, and cut into bricks. */
	public static final Block VEIN_STONE = register("vein_stone", Block::new, veinStone());
	public static final Block VEIN_STONE_POLISHED = register("vein_stone_polished", Block::new, veinStone());
	public static final Block VEIN_STONE_BRICKS = register("vein_stone_bricks", Block::new, veinStone());
	/** 晶脈露頭: where the crystal breaks through the stone. */
	public static final Block VEIN_CRYSTAL_ORE = register("vein_crystal_ore", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).requiresCorrectToolForDrops().strength(3.0F, 3.0F).lightLevel(state -> 2));
	/** 霧松: the highlands' one tree, silver-barked. */
	public static final Block MIST_PINE_LOG = register("mist_pine_log", net.minecraft.world.level.block.RotatedPillarBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.QUARTZ).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
	public static final Block MIST_PINE_PLANKS = register("mist_pine_planks", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.QUARTZ).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
	public static final Block MIST_PINE_LEAVES = register("mist_pine_leaves", p -> new net.minecraft.world.level.block.UntintedParticleLeavesBlock(0.02F,
		net.minecraft.core.particles.ParticleTypes.WHITE_ASH, net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
		BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_CYAN).strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()
		.isValidSpawn((state, level, pos, type) -> false).isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos, box) -> false)
		.ignitedByLava().pushReaction(PushReaction.POPPED).isRedstoneConductor((state, level, pos) -> false));
	/** 玉草, 玉蕨: the highlands' grass and fern; 霧蘆, a tall reed by the water. */
	public static final Block JADE_GRASS = register("jade_grass", life.shizune.mistinn.block.TerraPlantBlock::new, plant()
		.offsetType(BlockBehaviour.OffsetType.XYZ).pushReaction(PushReaction.POPPED));
	public static final Block JADE_FERN = register("jade_fern", life.shizune.mistinn.block.TerraPlantBlock::new, plant()
		.offsetType(BlockBehaviour.OffsetType.XYZ).pushReaction(PushReaction.POPPED));
	public static final Block MIST_REED = register("mist_reed", net.minecraft.world.level.block.DoublePlantBlock::new, plant()
		.offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED));
	/** 晶苗: the valley people's crystal crop. */
	public static final Block CRYSTAL_SPROUT = register("crystal_sprout", life.shizune.mistinn.block.CrystalSproutBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));
	/** 晶格: the living crystal of Terra, in four growths and as blocks. */
	public static final Block TERRA_CRYSTAL_SMALL_BUD = register("terra_crystal_small_bud", p -> new net.minecraft.world.level.block.AmethystClusterBlock(3F, 4F, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).forceSolidOn().noOcclusion().strength(1.5F)
			.sound(SoundType.SMALL_AMETHYST_BUD).lightLevel(state -> 1).pushReaction(PushReaction.POPPED));
	/** A medium growth. */
	public static final Block TERRA_CRYSTAL_MEDIUM_BUD = register("terra_crystal_medium_bud", p -> new net.minecraft.world.level.block.AmethystClusterBlock(4F, 3F, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).forceSolidOn().noOcclusion().strength(1.5F)
			.sound(SoundType.MEDIUM_AMETHYST_BUD).lightLevel(state -> 2).pushReaction(PushReaction.POPPED));
	/** A large growth. */
	public static final Block TERRA_CRYSTAL_LARGE_BUD = register("terra_crystal_large_bud", p -> new net.minecraft.world.level.block.AmethystClusterBlock(5F, 3F, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).forceSolidOn().noOcclusion().strength(1.5F)
			.sound(SoundType.LARGE_AMETHYST_BUD).lightLevel(state -> 4).pushReaction(PushReaction.POPPED));
	/** A full cluster. */
	public static final Block TERRA_CRYSTAL_CLUSTER = register("terra_crystal_cluster", p -> new net.minecraft.world.level.block.AmethystClusterBlock(7F, 3F, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).forceSolidOn().noOcclusion().strength(1.5F)
			.sound(SoundType.AMETHYST_CLUSTER).lightLevel(state -> 5).pushReaction(PushReaction.POPPED));
	public static final Block TERRA_CRYSTAL_BLOCK = register("terra_crystal_block", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.DIAMOND).requiresCorrectToolForDrops().strength(1.5F).sound(SoundType.AMETHYST).lightLevel(state -> 3));
	/** 晶格: crystal grown in a lattice, the great formations; dim where the crystal has died. */
	public static final Block LATTICE_CRYSTAL = register("lattice_crystal", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.DIAMOND).requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.AMETHYST).lightLevel(state -> 6));
	public static final Block LATTICE_CRYSTAL_DIM = register("lattice_crystal_dim", Block::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_LIGHT_GRAY).requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.AMETHYST));

	/** What the crystal tillers build with: like bedrock it cannot be broken, blown up, pushed or taken. */
	private static BlockBehaviour.Properties facility() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL)
			.pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((state, level, pos, type) -> false);
	}

	private static BlockBehaviour.Properties veinStone() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).requiresCorrectToolForDrops().strength(1.5F, 6.0F);
	}

	private static BlockBehaviour.Properties plant() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).replaceable().noCollision().instabreak().sound(SoundType.GRASS);
	}

	// ── The crystal tillers' facilities: they cannot be broken, moved or made ──

	public static final Block FACILITY_DECK = register("facility_deck", Block::new, facility());
	public static final Block FACILITY_DECK_EDGE = register("facility_deck_edge", Block::new, facility());
	public static final Block FACILITY_DECK_SLAB = register("facility_deck_slab", net.minecraft.world.level.block.SlabBlock::new, facility());
	public static final Block FACILITY_PANEL = register("facility_panel", Block::new, facility());
	public static final Block FACILITY_FRAME = register("facility_frame", Block::new, facility().noOcclusion());
	public static final Block FACILITY_CONDUIT = register("facility_conduit", life.shizune.mistinn.block.ConduitBlock::new, facility().noOcclusion()
		.lightLevel(state -> state.getValue(life.shizune.mistinn.block.ConduitBlock.LIT) ? 4 : 0));
	public static final Block FACILITY_LAMP = register("facility_lamp", Block::new, facility().lightLevel(state -> 13));
	public static final Block FACILITY_CRATE = register("facility_crate", Block::new, facility());
	public static final Block FACILITY_GLASS = register("facility_glass", life.shizune.mistinn.block.FacilityGlassBlock::new, facility()
		.sound(SoundType.GLASS).noOcclusion().isViewBlocking((state, level, pos, box) -> false));
	public static final Block HAZARD_STRIPE = register("hazard_stripe", Block::new, facility());
	/** 樞紐核心: the heart of a settlement; woken with crystal cores, it makes the settlement a homestead. */
	public static final Block HUB_CORE = register("hub_core", life.shizune.mistinn.block.HubCoreBlock::new, facility().noOcclusion()
		.lightLevel(state -> state.getValue(life.shizune.mistinn.block.HubCoreBlock.LIT) ? 14 : 3));
	/** 樞紐尖塔: the finned spire over a hub core, crowned with a crystal that lights with it. */
	public static final Block HUB_SPIRE = register("hub_spire", life.shizune.mistinn.block.HubSpireBlock::new, facility().noOcclusion()
		.lightLevel(state -> state.getValue(life.shizune.mistinn.block.HubSpireBlock.LIT) ? 12 : 2));
	/** 晶煉爐: the settlement's crystal refinery; its crystal band lights with the hub. */
	public static final Block REFINERY = register("refinery", life.shizune.mistinn.block.RefineryBlock::new, facility().noOcclusion()
		.lightLevel(state -> state.getValue(life.shizune.mistinn.block.RefineryBlock.LIT) ? 8 : 0));
	/** 屋頂風扇: the fan unit on a workshop's roof; its crystal strip lights with the hub. */
	public static final Block ROOF_VENT = register("roof_vent", life.shizune.mistinn.block.RoofVentBlock::new, facility().noOcclusion()
		.lightLevel(state -> state.getValue(life.shizune.mistinn.block.RoofVentBlock.LIT) ? 6 : 0));
	/** 中繼塔: a relay pylon, stood in a base, a lattice mast and a head; its crystal light wakes with the hub. */
	public static final Block RELAY_PYLON = register("relay_pylon", life.shizune.mistinn.block.RelayPylonBlock::new, facility().noOcclusion()
		.lightLevel(state -> state.getValue(life.shizune.mistinn.block.RelayPylonBlock.LIT) ? 9 : 1));

	private MistinnBlocks() {
	}

	private static Block register(final String name, final Function<BlockBehaviour.Properties, Block> factory, final BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Mistinn.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
		// Loading the class registers the fields above.
	}
}
