package life.shizune.mistinn.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.registry.MistinnComponents;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What a hunter carries outside the hotbar: pockets, the safe box, and the equipped backpack
 * (whose contents are stored inside the backpack item, so they travel with it). Immutable: every
 * change makes a new value, which is what the attachment sync expects. The safe box is square:
 * {@code safeSize} cells a side, 2 normally and 3 in a Rust Anchor world.
 */
public record GridInventory(List<Placed> pockets, List<Placed> safe, ItemStack backpack, ItemStack rig, ItemStack mask, int safeSize) {
	public static final GridShape POCKET_SHAPE = GridShape.repeat(4, 1, 1);
	/** Cells a side of the safe box in a normal world. */
	public static final int SAFE_SIZE = 2;
	/** Cells a side of the safe box in a Rust Anchor world. */
	public static final int RUST_ANCHOR_SAFE_SIZE = 3;
	public static final GridInventory EMPTY = new GridInventory(List.of(), List.of(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, SAFE_SIZE);

	/** Every field is optional, so saves from before the rig and mask existed still load. */
	public static final Codec<GridInventory> CODEC = RecordCodecBuilder.create(
		i -> i.group(
				Placed.LIST_CODEC.optionalFieldOf("pockets", List.of()).forGetter(GridInventory::pockets),
				Placed.LIST_CODEC.optionalFieldOf("safe", List.of()).forGetter(GridInventory::safe),
				ItemStack.OPTIONAL_CODEC.optionalFieldOf("backpack", ItemStack.EMPTY).forGetter(GridInventory::backpack),
				ItemStack.OPTIONAL_CODEC.optionalFieldOf("rig", ItemStack.EMPTY).forGetter(GridInventory::rig),
				ItemStack.OPTIONAL_CODEC.optionalFieldOf("mask", ItemStack.EMPTY).forGetter(GridInventory::mask),
				Codec.INT.optionalFieldOf("safe_size", SAFE_SIZE).forGetter(GridInventory::safeSize)
			)
			.apply(i, GridInventory::new)
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, GridInventory> STREAM_CODEC = StreamCodec.composite(
		Placed.LIST_STREAM_CODEC, GridInventory::pockets,
		Placed.LIST_STREAM_CODEC, GridInventory::safe,
		ItemStack.OPTIONAL_STREAM_CODEC, GridInventory::backpack,
		ItemStack.OPTIONAL_STREAM_CODEC, GridInventory::rig,
		ItemStack.OPTIONAL_STREAM_CODEC, GridInventory::mask,
		ByteBufCodecs.VAR_INT, GridInventory::safeSize,
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
			case SAFE -> GridShape.of(this.safeSize, this.safeSize);
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
			case POCKETS -> new GridInventory(copy, this.safe, this.backpack, this.rig, this.mask, this.safeSize);
			case SAFE -> new GridInventory(this.pockets, copy, this.backpack, this.rig, this.mask, this.safeSize);
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
		return new GridInventory(this.pockets, this.safe, pack, this.rig, this.mask, this.safeSize);
	}

	public GridInventory withRig(final ItemStack rig) {
		return new GridInventory(this.pockets, this.safe, this.backpack, rig, this.mask, this.safeSize);
	}

	/** Everything carried gone but the safe box, as death leaves it. */
	public GridInventory onlySafe() {
		return new GridInventory(List.of(), this.safe, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, this.safeSize);
	}

	/** A safe box with {@code size} cells a side; what it holds stays where it is. */
	public GridInventory withSafeSize(final int size) {
		return new GridInventory(this.pockets, this.safe, this.backpack, this.rig, this.mask, size);
	}

	public GridInventory withMask(final ItemStack mask) {
		return new GridInventory(this.pockets, this.safe, this.backpack, this.rig, mask, this.safeSize);
	}
}
