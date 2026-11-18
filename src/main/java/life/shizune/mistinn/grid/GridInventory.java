package life.shizune.mistinn.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.registry.MistinnComponents;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What a hunter carries outside the hotbar: pockets, the safe box, and the equipped backpack
 * (whose contents are stored inside the backpack item, so they travel with it). Immutable: every
 * change makes a new value, which is what the attachment sync expects.
 */
public record GridInventory(List<Placed> pockets, List<Placed> safe, ItemStack backpack) {
	public static final GridShape POCKET_SHAPE = GridShape.repeat(4, 1, 1);
	public static final GridShape SAFE_SHAPE = GridShape.of(2, 2);
	public static final GridInventory EMPTY = new GridInventory(List.of(), List.of(), ItemStack.EMPTY);

	public static final Codec<GridInventory> CODEC = RecordCodecBuilder.create(
		i -> i.group(
				Placed.LIST_CODEC.optionalFieldOf("pockets", List.of()).forGetter(GridInventory::pockets),
				Placed.LIST_CODEC.optionalFieldOf("safe", List.of()).forGetter(GridInventory::safe),
				ItemStack.OPTIONAL_CODEC.optionalFieldOf("backpack", ItemStack.EMPTY).forGetter(GridInventory::backpack)
			)
			.apply(i, GridInventory::new)
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, GridInventory> STREAM_CODEC = StreamCodec.composite(
		Placed.LIST_STREAM_CODEC, GridInventory::pockets,
		Placed.LIST_STREAM_CODEC, GridInventory::safe,
		ItemStack.OPTIONAL_STREAM_CODEC, GridInventory::backpack,
		GridInventory::new
	);

	public static Gear.@Nullable Backpack backpackOf(final ItemStack stack) {
		int index = MistinnItems.BACKPACKS.indexOf(stack.getItem());
		return index < 0 ? null : Gear.BACKPACKS.get(index);
	}

	public GridShape shape(final Area area) {
		return switch (area) {
			case POCKETS -> POCKET_SHAPE;
			case SAFE -> SAFE_SHAPE;
			case BACKPACK -> {
				Gear.Backpack pack = backpackOf(this.backpack);
				yield pack == null ? GridShape.EMPTY : pack.shape();
			}
		};
	}

	public List<Placed> items(final Area area) {
		return switch (area) {
			case POCKETS -> this.pockets;
			case SAFE -> this.safe;
			case BACKPACK -> this.backpack.getOrDefault(MistinnComponents.BACKPACK_CONTENTS, List.of());
		};
	}

	public GridInventory with(final Area area, final List<Placed> items) {
		List<Placed> copy = List.copyOf(items);
		return switch (area) {
			case POCKETS -> new GridInventory(copy, this.safe, this.backpack);
			case SAFE -> new GridInventory(this.pockets, copy, this.backpack);
			case BACKPACK -> {
				if (this.backpack.isEmpty()) {
					yield this;
				}
				ItemStack pack = this.backpack.copy();
				pack.set(MistinnComponents.BACKPACK_CONTENTS, copy);
				yield new GridInventory(this.pockets, this.safe, pack);
			}
		};
	}

	public GridInventory withBackpack(final ItemStack pack) {
		return new GridInventory(this.pockets, this.safe, pack);
	}
}
