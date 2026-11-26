package life.shizune.mistinn.client.rift;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** The rift's light leaking out of a creature's cracks: the glowing parts of its skin, drawn again at full brightness. */
public final class RiftGlowLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final Function<AvatarRenderState, Identifier> glow;

	public RiftGlowLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> parent, final Function<AvatarRenderState, Identifier> glow) {
		super(parent);
		this.glow = glow;
	}

	@Override
	public void submit(final PoseStack poseStack, final SubmitNodeCollector collector, final int light, final AvatarRenderState state, final float yRot,
		final float xRot) {
		collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(this.glow.apply(state)), light,
			OverlayTexture.NO_OVERLAY, state.outlineColor);
	}
}
