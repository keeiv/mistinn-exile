package life.shizune.mistinn.client.terra;

import life.shizune.mistinn.Mistinn;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * 玉角羚的模型: a slim body on four long legs, a neck carried up and forward, a narrow head with two ears and two horns of
 * jade crystal sweeping back, a short tail. The boxes between the GENERATED markers come from the jade-horn antelope's
 * model spec together with its 64x64 texture layout; do not edit them by hand.
 * <p>
 * It walks with its legs in diagonal pairs, its head bobbing a little; standing, its tail flicks now and then.
 */
public class JadeAntelopeModel extends EntityModel<LivingEntityRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("jade_antelope"), "main");

	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart tail;
	private final ModelPart frontRight;
	private final ModelPart frontLeft;
	private final ModelPart hindRight;
	private final ModelPart hindLeft;

	public JadeAntelopeModel(final ModelPart root) {
		super(root);
		ModelPart body = root.getChild("body");
		this.neck = body.getChild("neck");
		this.head = this.neck.getChild("head");
		this.tail = body.getChild("tail");
		this.frontRight = root.getChild("leg_front_right");
		this.frontLeft = root.getChild("leg_front_left");
		this.hindRight = root.getChild("leg_hind_right");
		this.hindLeft = root.getChild("leg_hind_left");
	}

	public static LayerDefinition createBodyLayer() {
		// BEGIN GENERATED LAYER
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			// torso
			.texOffs(0, 0).addBox(-3.0F, -5.0F, -7.0F, 6.0F, 7.0F, 14.0F),
			PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
			// neck_core
			.texOffs(18, 22).addBox(-1.5F, -7.0F, -2.0F, 3.0F, 8.0F, 3.0F),
			PartPose.offsetAndRotation(0.0F, -4.0F, -6.0F, 0.4887F, 0.0F, 0.0F));
		PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
			// skull
			.texOffs(31, 22).addBox(-2.0F, -3.0F, -4.0F, 4.0F, 4.0F, 5.0F)
			// muzzle
			.texOffs(0, 36).addBox(-1.5F, -2.0F, -7.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.02F))
			// ear_right
			.texOffs(20, 36).addBox(-4.0F, -3.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.02F))
			// ear_left
			.texOffs(27, 36).addBox(2.0F, -3.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, -7.0F, -1.0F, -0.4887F, 0.0F, 0.0F));
		head.addOrReplaceChild("horn_right", CubeListBuilder.create()
			// horn_right_core
			.texOffs(50, 22).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F),
			PartPose.offsetAndRotation(-1.5F, -2.0F, -1.0F, -0.6981F, 0.0F, 0.2094F));
		head.addOrReplaceChild("horn_left", CubeListBuilder.create()
			// horn_left_core
			.texOffs(55, 22).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F),
			PartPose.offsetAndRotation(1.5F, -2.0F, -1.0F, -0.6981F, 0.0F, -0.2094F));
		body.addOrReplaceChild("tail", CubeListBuilder.create()
			// tail_core
			.texOffs(13, 36).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 4.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, -4.0F, 7.0F, -0.5236F, 0.0F, 0.0F));
		root.addOrReplaceChild("leg_front_right", CubeListBuilder.create()
			// leg_front_right_core
			.texOffs(41, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
			PartPose.offsetAndRotation(-2.0F, 13.0F, -5.0F, 0.0F, 0.0F, 0.0F));
		root.addOrReplaceChild("leg_front_left", CubeListBuilder.create()
			// leg_front_left_core
			.texOffs(50, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
			PartPose.offsetAndRotation(2.0F, 13.0F, -5.0F, 0.0F, 0.0F, 0.0F));
		root.addOrReplaceChild("leg_hind_right", CubeListBuilder.create()
			// leg_hind_right_core
			.texOffs(0, 22).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
			PartPose.offsetAndRotation(-2.0F, 13.0F, 5.0F, 0.0F, 0.0F, 0.0F));
		root.addOrReplaceChild("leg_hind_left", CubeListBuilder.create()
			// leg_hind_left_core
			.texOffs(9, 22).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
			PartPose.offsetAndRotation(2.0F, 13.0F, 5.0F, 0.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
		// END GENERATED LAYER
	}

	@Override
	public void setupAnim(final LivingEntityRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos * 0.9F;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float step = Mth.cos(pos) * 0.9F * speed;
		this.frontRight.xRot += step;
		this.hindLeft.xRot += step;
		this.frontLeft.xRot -= step;
		this.hindRight.xRot -= step;
		this.neck.xRot += Mth.sin(pos * 2.0F) * 0.05F * speed;
		this.head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.7F;
		this.head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.6F;
		// a flick of the tail now and then
		float flick = Mth.sin(state.ageInTicks * 0.9F) * Math.max(0.0F, Mth.sin(state.ageInTicks * 0.07F) - 0.8F) * 4.0F;
		this.tail.yRot += flick * 0.5F;
	}
}
