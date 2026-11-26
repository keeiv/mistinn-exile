package life.shizune.mistinn.client.rift;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The rift's light leaking out of a creature's cracks: the glowing parts of its skin, drawn again at
 * full brightness. Which glow is drawn can differ from one creature to the next (a walker's look).
 */
public final class RiftGlowLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	private final Function<S, Identifier> glow;

	public RiftGlowLayer(final RenderLayerParent<S, M> parent, final Function<S, Identifier> glow) {
		super(parent);
		this.glow = glow;
	}

	@Override
	public void submit(final PoseStack poseStack, final SubmitNodeCollector collector, final int light, final S state, final float yRot, final float xRot) {
		collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(this.glow.apply(state)), light,
			OverlayTexture.NO_OVERLAY, state.outlineColor);
	}
}
