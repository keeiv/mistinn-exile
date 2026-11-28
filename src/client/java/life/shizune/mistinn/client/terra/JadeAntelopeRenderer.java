package life.shizune.mistinn.client.terra;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.terra.JadeAntelope;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Draws a jade-horn antelope; a fawn is a little over half its size. */
public class JadeAntelopeRenderer extends MobRenderer<JadeAntelope, LivingEntityRenderState, JadeAntelopeModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/terra/jade_antelope.png");

	public JadeAntelopeRenderer(final EntityRendererProvider.Context context) {
		super(context, new JadeAntelopeModel(context.bakeLayer(JadeAntelopeModel.LAYER)), 0.5F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	protected void scale(final LivingEntityRenderState state, final PoseStack poseStack) {
		if (state.isBaby) {
			poseStack.scale(0.55F, 0.55F, 0.55F);
		}
	}

	@Override
	public Identifier getTextureLocation(final LivingEntityRenderState state) {
		return TEXTURE;
	}
}
