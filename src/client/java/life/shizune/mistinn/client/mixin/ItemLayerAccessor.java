package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Supplier;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** One layer of an item as it is drawn in the hand, for finding where on it a hand closes. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemLayerAccessor {
	/** Applies the display transform the layer's model was given, as the game does before drawing it. */
	@Invoker("applyTransform")
	void mistinn$applyTransform(PoseStack.Pose pose);

	/** What draws the layer if it is not a model of flat faces (a shield, a trident, a head); null for those that are. */
	@Accessor("specialRenderer")
	@Nullable SpecialModelRenderer<Object> mistinn$special();

	/** The corners of the layer's model, in the model's own space. */
	@Accessor("extents")
	Supplier<Vector3fc[]> mistinn$corners();
}
