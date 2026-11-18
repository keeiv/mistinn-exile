package life.shizune.mistinn.registry;

import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.Placed;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MistinnComponents {
	/** What a backpack holds, laid out in its grid. Travels with the backpack item. */
	public static final DataComponentType<List<Placed>> BACKPACK_CONTENTS = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Mistinn.id("backpack_contents"),
		DataComponentType.<List<Placed>>builder().persistent(Placed.LIST_CODEC).networkSynchronized(Placed.LIST_STREAM_CODEC).build()
	);

	private MistinnComponents() {
	}

	public static void init() {
		// Loading the class registers the field above.
	}
}
