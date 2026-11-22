package life.shizune.mistinn.registry;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.bell.BellBlockEntity;
import life.shizune.mistinn.block.GravestoneBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** The mod's block entity types. */
public final class MistinnBlockEntities {
	public static final BlockEntityType<BellBlockEntity> BELL = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		Mistinn.id("bell"),
		FabricBlockEntityTypeBuilder.create(BellBlockEntity::new, MistinnBlocks.GOLDEN_BELL, MistinnBlocks.ORE_BELL).build()
	);

	public static final BlockEntityType<GravestoneBlockEntity> GRAVESTONE = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		Mistinn.id("gravestone"),
		FabricBlockEntityTypeBuilder.create(GravestoneBlockEntity::new, MistinnBlocks.GRAVESTONE).build()
	);

	private MistinnBlockEntities() {
	}

	public static void init() {
		// Loading the class registers the field above.
	}
}
