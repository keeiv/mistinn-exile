package life.shizune.mistinn.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.enemy.ExecutionMove;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.projectile.ArrowModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** The arrow of a headshot, left in the fallen hunter's forehead (vanilla's stuck arrows land anywhere on the body). */
public final class HeadshotArrowLayer<M extends PlayerModel> extends RenderLayer<AvatarRenderState, M> {
	private final ArrowModel model;
	private final ArrowRenderState arrow = new ArrowRenderState();

	public HeadshotArrowLayer(final LivingEntityRenderer<?, AvatarRenderState, M> renderer, final EntityRendererProvider.Context context) {
		super(renderer);
		this.model = new ArrowModel(context.bakeLayer(ModelLayers.ARROW));
	}

	@Override
	public void submit(final PoseStack pose, final SubmitNodeCollector collector, final int light, final AvatarRenderState state,
		final float yRot, final float xRot) {
		ExecutionView.Pose execution = state.getData(ExecutionView.KEY);
		if (execution == null || execution.move() != ExecutionMove.HEADSHOT || !execution.done()) {
			return;
		}
		pose.pushPose();
		this.getParentModel().head.translateAndRotate(pose);
		// the forehead, with the shaft standing out toward where the shot came from
		pose.translate(0.0F, -0.34F, -0.2F);
		pose.rotateDegrees(Axis.YP, 90.0F);
		pose.rotateDegrees(Axis.ZP, -12.0F);
		collector.submitModel(this.model, this.arrow, pose, TippableArrowRenderer.NORMAL_ARROW_LOCATION, light,
			OverlayTexture.NO_OVERLAY, state.outlineColor);
		pose.popPose();
	}
}
