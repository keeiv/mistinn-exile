package life.shizune.mistinn.registry;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.block.GravestoneBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class MistinnBlockEntities {
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
