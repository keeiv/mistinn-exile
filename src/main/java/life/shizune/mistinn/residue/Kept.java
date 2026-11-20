package life.shizune.mistinn.residue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * One thing a fallen hunter carried, kept in their gravestone, and where it was: an inventory
 * slot ({@code "slot"} with its index: hotbar, armour, off hand), a pocket, or the worn mask,
 * chest rig or backpack (whose contents travel inside it).
 */
public record Kept(String where, int index, ItemStack stack) {
	public static final String SLOT = "slot";
	public static final String POCKET = "pocket";
	public static final String MASK = "mask";
	public static final String RIG = "rig";
	public static final String BACKPACK = "backpack";

	public static final Codec<Kept> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("where").forGetter(Kept::where),
		Codec.INT.optionalFieldOf("index", 0).forGetter(Kept::index),
		ItemStack.CODEC.fieldOf("stack").forGetter(Kept::stack)
	).apply(i, Kept::new));
}
