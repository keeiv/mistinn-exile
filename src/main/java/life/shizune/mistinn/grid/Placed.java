package life.shizune.mistinn.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * An item lying in a grid: which section, its top-left cell, whether it is turned on its side,
 * and the stack itself.
 */
public record Placed(int section, int x, int y, boolean rotated, ItemStack stack) {
	public static final Codec<Placed> CODEC = RecordCodecBuilder.create(
		i -> i.group(
				Codec.INT.fieldOf("section").forGetter(Placed::section),
				Codec.INT.fieldOf("x").forGetter(Placed::x),
				Codec.INT.fieldOf("y").forGetter(Placed::y),
				Codec.BOOL.optionalFieldOf("rotated", false).forGetter(Placed::rotated),
				ItemStack.CODEC.fieldOf("stack").forGetter(Placed::stack)
			)
			.apply(i, Placed::new)
	);
	public static final Codec<List<Placed>> LIST_CODEC = CODEC.listOf();
	public static final StreamCodec<RegistryFriendlyByteBuf, Placed> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, Placed::section,
		ByteBufCodecs.VAR_INT, Placed::x,
		ByteBufCodecs.VAR_INT, Placed::y,
		ByteBufCodecs.BOOL, Placed::rotated,
		ItemStack.STREAM_CODEC, Placed::stack,
		Placed::new
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, List<Placed>> LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list());

	/** Width in cells as it lies (swapped when rotated). */
	public int width() {
		int[] size = ItemSizes.of(this.stack);
		return this.rotated ? size[1] : size[0];
	}

	public int height() {
		int[] size = ItemSizes.of(this.stack);
		return this.rotated ? size[0] : size[1];
	}

	public boolean covers(final int section, final int cx, final int cy) {
		return this.section == section && cx >= this.x && cx < this.x + this.width() && cy >= this.y && cy < this.y + this.height();
	}

	public Placed withStack(final ItemStack newStack) {
		return new Placed(this.section, this.x, this.y, this.rotated, newStack);
	}
}
