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
