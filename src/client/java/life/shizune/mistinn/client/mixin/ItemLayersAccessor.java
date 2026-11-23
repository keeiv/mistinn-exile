package life.shizune.mistinn.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The layers an item is drawn from, for finding where on it a hand closes. */
@Mixin(ItemStackRenderState.class)
public interface ItemLayersAccessor {
	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] mistinn$layers();

	@Accessor("activeLayerCount")
	int mistinn$layerCount();
}
