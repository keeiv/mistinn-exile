package life.shizune.mistinn.registry;

import com.mojang.serialization.Codec;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.Placed;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** The mod's data components: what its items carry with them. */
public final class MistinnComponents {
	/** What a backpack holds, laid out in its grid. Travels with the backpack item. */
	public static final DataComponentType<List<Placed>> BACKPACK_CONTENTS = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Mistinn.id("backpack_contents"),
		DataComponentType.<List<Placed>>builder().persistent(Placed.LIST_CODEC).networkSynchronized(Placed.LIST_STREAM_CODEC).build()
	);

	/** 維修磨損: points of maximum durability repairs have worn away for good. Shown in the tooltip. */
	public static final DataComponentType<Integer> REPAIR_WEAR = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Mistinn.id("repair_wear"),
		DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build()
	);

	private MistinnComponents() {
	}

	public static void init() {
		// Loading the class registers the fields above.
	}
}
