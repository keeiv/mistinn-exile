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
public record GridInventory(List<Placed> pockets, List<Placed> safe, ItemStack backpack, ItemStack rig, ItemStack mask) {
	public static final GridShape POCKET_SHAPE = GridShape.repeat(4, 1, 1);
	public static final GridShape SAFE_SHAPE = GridShape.of(2, 2);
	public static final GridInventory EMPTY = new GridInventory(List.of(), List.of(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

	/** Every field is optional, so saves from before the rig and mask existed still load. */
	public static final Codec<GridInventory> CODEC = RecordCodecBuilder.create(
		i -> i.group(
				Placed.LIST_CODEC.optionalFieldOf("pockets", List.of()).forGetter(GridInventory::pockets),
				Placed.LIST_CODEC.optionalFieldOf("safe", List.of()).forGetter(GridInventory::safe),
				ItemStack.OPTIONAL_CODEC.optionalFieldOf("backpack", ItemStack.EMPTY).forGetter(GridInventory::backpack),
				ItemStack.OPTIONAL_CODEC.optionalFieldOf("rig", ItemStack.EMPTY).forGetter(GridInventory::rig),
				ItemStack.OPTIONAL_CODEC.optionalFieldOf("mask", ItemStack.EMPTY).forGetter(GridInventory::mask)
			)
			.apply(i, GridInventory::new)
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, GridInventory> STREAM_CODEC = StreamCodec.composite(
		Placed.LIST_STREAM_CODEC, GridInventory::pockets,
		Placed.LIST_STREAM_CODEC, GridInventory::safe,
		ItemStack.OPTIONAL_STREAM_CODEC, GridInventory::backpack,
		ItemStack.OPTIONAL_STREAM_CODEC, GridInventory::rig,
		ItemStack.OPTIONAL_STREAM_CODEC, GridInventory::mask,
		GridInventory::new
	);

	public static Gear.@Nullable Backpack backpackOf(final ItemStack stack) {
		int index = MistinnItems.BACKPACKS.indexOf(stack.getItem());
		return index < 0 ? null : Gear.BACKPACKS.get(index);
	}

	public static Gear.@Nullable ChestRig rigOf(final ItemStack stack) {
		int index = MistinnItems.RIGS.indexOf(stack.getItem());
		return index < 0 ? null : Gear.RIGS.get(index);
	}

	public static Gear.@Nullable Mask maskOf(final ItemStack stack) {
		int index = MistinnItems.MASKS.indexOf(stack.getItem());
		return index < 0 ? null : Gear.MASKS.get(index);
	}

	public GridShape shape(final Area area) {
		return switch (area) {
			case POCKETS -> POCKET_SHAPE;
			case SAFE -> SAFE_SHAPE;
			case BACKPACK -> {
				Gear.Backpack pack = backpackOf(this.backpack);
				yield pack == null ? GridShape.EMPTY : pack.shape();
			}
			case RIG -> {
				Gear.ChestRig rig = rigOf(this.rig);
				yield rig == null ? GridShape.EMPTY : rig.shape();
			}
		};
	}

	public List<Placed> items(final Area area) {
		return switch (area) {
			case POCKETS -> this.pockets;
			case SAFE -> this.safe;
			case BACKPACK -> this.backpack.getOrDefault(MistinnComponents.BACKPACK_CONTENTS, List.of());
			case RIG -> this.rig.getOrDefault(MistinnComponents.BACKPACK_CONTENTS, List.of());
		};
	}

	public GridInventory with(final Area area, final List<Placed> items) {
		List<Placed> copy = List.copyOf(items);
		return switch (area) {
			case POCKETS -> new GridInventory(copy, this.safe, this.backpack, this.rig, this.mask);
			case SAFE -> new GridInventory(this.pockets, copy, this.backpack, this.rig, this.mask);
			case BACKPACK -> this.backpack.isEmpty() ? this : this.withBackpack(holding(this.backpack, copy));
			case RIG -> this.rig.isEmpty() ? this : this.withRig(holding(this.rig, copy));
		};
	}

	private static ItemStack holding(final ItemStack container, final List<Placed> items) {
		ItemStack copy = container.copy();
		copy.set(MistinnComponents.BACKPACK_CONTENTS, items);
		return copy;
	}

	public GridInventory withBackpack(final ItemStack pack) {
		return new GridInventory(this.pockets, this.safe, pack, this.rig, this.mask);
	}

	public GridInventory withRig(final ItemStack rig) {
		return new GridInventory(this.pockets, this.safe, this.backpack, rig, this.mask);
	}

	public GridInventory withMask(final ItemStack mask) {
		return new GridInventory(this.pockets, this.safe, this.backpack, this.rig, mask);
	}
}
