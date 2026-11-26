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

	/** 紅石息壤: earth that breeds redstone, ripening by itself. */
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
