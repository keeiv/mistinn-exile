package life.shizune.mistinn.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.tier.Tier;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/**
 * The worn mask, drawn over the face of any player wearing one. It is a head a little larger
 * than the helmet's, so the mask sits in front of the helmet's face opening and turns with the
 * head; its texture paints only the face and the straps.
 */
public class MaskLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("mask"), "main");
	/** Which mask a player wears, by tier index; absent when none. */
	public static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "mistinn:mask");

	private final HumanoidModel<AvatarRenderState> model;

	public MaskLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> parent, final EntityRendererProvider.Context context) {
		super(parent);
		this.model = new HumanoidModel<>(context.bakeLayer(LAYER));
	}

	/** Just outside the helmet (1.0) so the plate covers its face. */
	public static LayerDefinition definition() {
		return LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1.18F), 0.0F), 64, 32);
	}

	@Override
	public void submit(final PoseStack poseStack, final SubmitNodeCollector collector, final int light, final AvatarRenderState state, final float yRot, final float xRot) {
		Integer tier = state.getData(KEY);
		if (tier == null || tier < 0 || tier >= Tier.values().length) {
			return;
		}
		// only the head carries the mask
		this.model.head.visible = true;
		this.model.hat.visible = false;
		this.model.body.visible = false;
		this.model.rightArm.visible = false;
		this.model.leftArm.visible = false;
		this.model.rightLeg.visible = false;
		this.model.leftLeg.visible = false;
		Identifier texture = Mistinn.id("textures/entity/mask/mask_" + Tier.values()[tier].id() + ".png");
		coloredCutoutModelCopyLayerRender(this.model, texture, poseStack, collector, light, state, -1, 2);
	}
}
