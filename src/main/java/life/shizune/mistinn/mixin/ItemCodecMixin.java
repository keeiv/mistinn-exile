package life.shizune.mistinn.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import life.shizune.mistinn.Mistinn;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Migrate old salve stacks wherever they were saved, including inside grids and gravestones. */
@Mixin(Item.class)
public abstract class ItemCodecMixin {
	@ModifyExpressionValue(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/DefaultedRegistry;holderByNameCodec()Lcom/mojang/serialization/Codec;"))
	private static Codec<Holder<Item>> mistinn$readRetiredItem(final Codec<Holder<Item>> original) {
		Codec<Identifier> salve = Identifier.CODEC.validate(id -> id.equals(Mistinn.id("bell_salve"))
			? DataResult.success(id) : DataResult.error(() -> "Unknown item " + id));
		// Raw gold was an ingredient and fits the same cells and stack counts as the old item.
		return original.withAlternative(salve, id -> BuiltInRegistries.ITEM.wrapAsHolder(Items.RAW_GOLD));
	}
}
