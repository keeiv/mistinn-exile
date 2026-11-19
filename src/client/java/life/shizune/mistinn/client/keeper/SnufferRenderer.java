package life.shizune.mistinn.client.keeper;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.keeper.Snuffer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * 熄燈人 on the vanilla humanoid frame, drawn tall and thin. Its head is the lantern it wears in
 * the head slot (lit, then a soul lantern once the room goes dark); the hook is its held item.
 */
public class SnufferRenderer extends HumanoidMobRenderer<Snuffer, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/snuffer.png");

	public SnufferRenderer(final EntityRendererProvider.Context context) {
		// the lantern in the head slot, drawn large and low enough to be the head itself
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.7F, new CustomHeadLayer.Transforms(0.32F, 0.0F, 2.0F, 2.0F));
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(final HumanoidRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(final HumanoidRenderState state, final PoseStack poseStack) {
		// 2.7 blocks tall from a 1.95 frame, and narrower than a man
		poseStack.scale(1.2F, 1.38F, 1.2F);
	}
}
