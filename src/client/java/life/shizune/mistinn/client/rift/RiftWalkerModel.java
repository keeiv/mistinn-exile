package life.shizune.mistinn.client.rift;

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
import net.minecraft.util.Mth;

/**
 * 裂隙行者的模型: a too-tall husk, hunched: thin legs, a narrow waist under a hunched chest with
 * its spine showing, arms that reach to its knees and end in two claws, a long head with its jaw
 * hanging open, and shards of rift crystal grown out through its back. The boxes between the
 * GENERATED markers come from the rift walker's model spec together with its 64x64 texture layout;
 * do not edit them by hand.
 * <p>
 * It lurches as it walks, swaying from side to side with its arms swinging loose; its head jerks
 * now and then; leaping, it throws its claws out ahead; it rakes with both claws.
 */
public class RiftWalkerModel extends EntityModel<RiftWalkerRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("rift_walker"), "main");

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart armRight;
	private final ModelPart armLeft;
	private final ModelPart forearmRight;
	private final ModelPart forearmLeft;
	private final ModelPart legRight;
	private final ModelPart legLeft;
	private final ModelPart shinRight;
	private final ModelPart shinLeft;

	public RiftWalkerModel(final ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.armRight = this.body.getChild("arm_right");
		this.armLeft = this.body.getChild("arm_left");
		this.forearmRight = this.armRight.getChild("forearm_right");
		this.forearmLeft = this.armLeft.getChild("forearm_left");
		this.legRight = root.getChild("leg_right");
		this.legLeft = root.getChild("leg_left");
		this.shinRight = this.legRight.getChild("shin_right");
		this.shinLeft = this.legLeft.getChild("shin_left");
	}

	public static LayerDefinition createBodyLayer() {
		// BEGIN GENERATED LAYER
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			// chest
			.texOffs(36, 0).addBox(-3.5F, -12.0F, -2.0F, 7.0F, 7.0F, 4.0F)
			// belly
			.texOffs(0, 26).addBox(-2.5F, -5.0F, -1.5F, 5.0F, 5.0F, 3.0F)
			// spine_1
			.texOffs(45, 35).addBox(-0.5F, -4.0F, 1.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F))
			// spine_2
			.texOffs(50, 35).addBox(-0.5F, -7.0F, 2.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F))
			// spine_3
			.texOffs(55, 35).addBox(-0.5F, -11.0F, 2.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.2094F, 0.0F, 0.0F));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
			// neck
			.texOffs(26, 35).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(-0.02F))
			// skull
			.texOffs(0, 14).addBox(-2.5F, -8.0F, -3.0F, 5.0F, 6.0F, 5.0F)
			// crest
			.texOffs(0, 35).addBox(-0.5F, -9.0F, -2.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, -12.0F, -0.5F, -0.1745F, 0.0F, 0.0F));
		head.addOrReplaceChild("jaw", CubeListBuilder.create()
			// jaw_core
			.texOffs(22, 26).addBox(-2.0F, 0.0F, -3.0F, 4.0F, 2.0F, 3.0F),
			PartPose.offsetAndRotation(0.0F, -3.0F, -0.5F, -0.3142F, 0.0F, 0.0F));
		body.addOrReplaceChild("shard_right", CubeListBuilder.create()
			// shard_right_core
			.texOffs(17, 26).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F),
			PartPose.offsetAndRotation(-1.5F, -9.0F, 2.0F, -0.6109F, 0.0F, -0.384F));
		body.addOrReplaceChild("shard_left", CubeListBuilder.create()
			// shard_left_core
			.texOffs(11, 35).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
			PartPose.offsetAndRotation(1.5F, -7.0F, 2.0F, -0.6981F, 0.0F, 0.4538F));
		PartDefinition armRight = body.addOrReplaceChild("arm_right", CubeListBuilder.create()
			// upper_arm_right
			.texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
			PartPose.offsetAndRotation(-4.5F, -11.0F, 0.0F, -0.2443F, 0.0F, 0.1047F));
		armRight.addOrReplaceChild("forearm_right", CubeListBuilder.create()
			// lower_arm_right
			.texOffs(9, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F)
			// claw_right_1
			.texOffs(35, 35).addBox(0.0F, 11.0F, -1.0F, 1.0F, 3.0F, 1.0F)
			// claw_right_2
			.texOffs(16, 35).addBox(-1.0F, 11.0F, 0.0F, 1.0F, 4.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, -0.2094F, 0.0F, 0.0F));
		PartDefinition armLeft = body.addOrReplaceChild("arm_left", CubeListBuilder.create()
			// upper_arm_left
			.texOffs(18, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
			PartPose.offsetAndRotation(4.5F, -11.0F, 0.0F, -0.2443F, 0.0F, -0.1047F));
		armLeft.addOrReplaceChild("forearm_left", CubeListBuilder.create()
			// lower_arm_left
			.texOffs(27, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F)
			// claw_left_1
			.texOffs(40, 35).addBox(-1.0F, 11.0F, -1.0F, 1.0F, 3.0F, 1.0F)
			// claw_left_2
			.texOffs(21, 35).addBox(0.0F, 11.0F, 0.0F, 1.0F, 4.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, -0.2094F, 0.0F, 0.0F));
		PartDefinition legRight = root.addOrReplaceChild("leg_right", CubeListBuilder.create()
			// thigh_right
			.texOffs(21, 14).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F),
			PartPose.offsetAndRotation(-1.5F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		legRight.addOrReplaceChild("shin_right", CubeListBuilder.create()
			// shin_right_core
			.texOffs(39, 14).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F)
			// foot_right
			.texOffs(37, 26).addBox(-1.0F, 8.0F, -2.75F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition legLeft = root.addOrReplaceChild("leg_left", CubeListBuilder.create()
			// thigh_left
			.texOffs(30, 14).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F),
			PartPose.offsetAndRotation(1.5F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		legLeft.addOrReplaceChild("shin_left", CubeListBuilder.create()
			// shin_left_core
			.texOffs(48, 14).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F)
			// foot_left
			.texOffs(50, 26).addBox(-1.0F, 8.0F, -2.75F, 2.0F, 1.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
		// END GENERATED LAYER
	}

	@Override
	public void setupAnim(final RiftWalkerRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float pos = state.walkAnimationPos * 0.62F;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float leap = state.aloft ? 1.0F : 0.0F;

		// a lurching stride: stiff legs, the body rolling over each step
		float stride = Mth.cos(pos) * 0.9F * speed * (1.0F - leap);
		this.legRight.xRot += stride;
		this.legLeft.xRot -= stride;
		this.shinRight.xRot += Math.max(0.0F, -stride) * 0.6F;
		this.shinLeft.xRot += Math.max(0.0F, stride) * 0.6F;
		this.body.zRot += Mth.cos(pos) * 0.12F * speed;
		this.body.y += Math.abs(Mth.sin(pos)) * -0.8F * speed;

		// the arms swing loose behind the stride and sway when it stands
		this.armRight.xRot += -Mth.cos(pos - 0.8F) * 0.55F * speed + Mth.sin(age * 0.07F) * 0.05F;
		this.armLeft.xRot += Mth.cos(pos - 0.8F) * 0.55F * speed - Mth.sin(age * 0.07F + 0.5F) * 0.05F;
		this.armRight.zRot += Mth.cos(pos) * 0.08F * speed;
		this.armLeft.zRot += Mth.cos(pos) * 0.08F * speed;
		this.forearmRight.xRot -= Math.max(0.0F, Mth.sin(pos - 1.4F)) * 0.35F * speed;
		this.forearmLeft.xRot -= Math.max(0.0F, -Mth.sin(pos - 1.4F)) * 0.35F * speed;

		// leaping: claws thrown out ahead, legs trailing
		this.armRight.xRot -= leap * 1.5F;
		this.armLeft.xRot -= leap * 1.5F;
		this.forearmRight.xRot -= leap * 0.3F;
		this.forearmLeft.xRot -= leap * 0.3F;
		this.legRight.xRot += leap * 0.6F;
		this.legLeft.xRot += leap * 0.35F;
		this.body.xRot -= leap * 0.25F;

		// a rake with both claws, the right a moment ahead
		float rakeRight = Mth.sin(state.attack * Mth.PI);
		float rakeLeft = Mth.sin(Math.max(0.0F, state.attack - 0.15F) / 0.85F * Mth.PI);
		this.armRight.xRot -= rakeRight * 1.5F;
		this.armLeft.xRot -= rakeLeft * 1.4F;
		this.armRight.zRot -= rakeRight * 0.3F;
		this.armLeft.zRot += rakeLeft * 0.3F;

		// the head follows its gaze and now and then jerks aside; the jaw hangs and works
		float jerk = (float) Math.pow(Math.max(0.0F, Mth.sin(age * 0.19F + state.twitch)), 14) * 0.55F;
		float side = Mth.sin(age * 0.031F + state.twitch * 0.5F) > 0.0F ? 1.0F : -1.0F;
		this.head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.8F + jerk * side * 0.6F;
		this.head.zRot += jerk * side;
		this.head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.6F;
		this.jaw.xRot += Mth.sin(age * 0.13F) * 0.08F + rakeRight * 0.3F + leap * 0.3F;
	}
}
