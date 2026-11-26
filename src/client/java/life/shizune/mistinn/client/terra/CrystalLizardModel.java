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
import net.minecraft.util.Mth;

/**
 * 晶蜥的模型: a low lizard of vein stone and crystal: a flat body with crystal shards jutting from
 * its back, a wedge head with a jaw, four legs reaching out sideways and down, and a tail of three
 * tapering segments ending in a shard. The boxes between the GENERATED markers come from the
 * crystal lizard's model spec together with its 64x64 texture layout; do not edit them by hand.
 * <p>
 * It walks with a sprawling lizard's gait, its legs swinging in diagonal pairs and its body and tail
 * swaying against them; lying still it sinks flat on the stone with its legs splayed.
 */
public class CrystalLizardModel extends EntityModel<CrystalLizardRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("crystal_lizard"), "main");

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart frontRight;
	private final ModelPart frontLeft;
	private final ModelPart hindRight;
	private final ModelPart hindLeft;
	private final ModelPart[] tail = new ModelPart[3];

	public CrystalLizardModel(final ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.frontRight = this.body.getChild("leg_front_right");
		this.frontLeft = this.body.getChild("leg_front_left");
		this.hindRight = this.body.getChild("leg_hind_right");
		this.hindLeft = this.body.getChild("leg_hind_left");
		ModelPart segment = this.body;
		for (int i = 0; i < this.tail.length; i++) {
			segment = segment.getChild("tail" + (i + 1));
			this.tail[i] = segment;
		}
	}

	public static LayerDefinition createBodyLayer() {
		// BEGIN GENERATED LAYER
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			// torso
			.texOffs(0, 0).addBox(-3.5F, -2.0F, -6.0F, 7.0F, 4.0F, 12.0F),
			PartPose.offsetAndRotation(0.0F, 19.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
			// skull
			.texOffs(0, 17).addBox(-2.5F, -2.0F, -5.0F, 5.0F, 4.0F, 5.0F)
			// snout
			.texOffs(15, 42).addBox(-1.5F, 0.0F, -8.0F, 3.0F, 2.0F, 3.0F)
			// brow_right
			.texOffs(0, 54).addBox(-2.5F, -2.5F, -4.0F, 1.0F, 1.0F, 3.0F)
			// brow_left
			.texOffs(9, 54).addBox(1.5F, -2.5F, -4.0F, 1.0F, 1.0F, 3.0F)
			// eye_right
			.texOffs(32, 54).addBox(-3.1F, -1.0F, -4.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.02F))
			// eye_left
			.texOffs(39, 54).addBox(2.1F, -1.0F, -4.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, -0.5F, -6.0F, 0.0F, 0.0F, 0.0F));
		head.addOrReplaceChild("jaw", CubeListBuilder.create()
			// jaw_core
			.texOffs(21, 17).addBox(-2.0F, 0.5F, -7.0F, 4.0F, 1.0F, 7.0F),
			PartPose.offsetAndRotation(0.0F, 1.5F, -1.0F, 0.0F, 0.0F, 0.0F));
		head.addOrReplaceChild("crest", CubeListBuilder.create()
			// crest_shard
			.texOffs(55, 42).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -2.0F, -2.0F, -0.4363F, 0.0F, 0.0F));
		body.addOrReplaceChild("ridge1", CubeListBuilder.create()
			// ridge1_shard
			.texOffs(28, 42).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -2.0F, -3.0F, -0.3491F, 0.0F, 0.0F));
		body.addOrReplaceChild("ridge2", CubeListBuilder.create()
			// ridge2_shard
			.texOffs(28, 27).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -2.0F, 1.0F, -0.2618F, 0.0F, 0.0F));
		body.addOrReplaceChild("ridge3", CubeListBuilder.create()
			// ridge3_shard
			.texOffs(37, 42).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -2.0F, 4.5F, -0.4887F, 0.0F, 0.0F));
		body.addOrReplaceChild("shard_right", CubeListBuilder.create()
			// shard_right_core
			.texOffs(0, 48).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F),
			PartPose.offsetAndRotation(-2.5F, -2.0F, -1.0F, -0.1745F, 0.0F, -0.5585F));
		body.addOrReplaceChild("shard_left", CubeListBuilder.create()
			// shard_left_core
			.texOffs(7, 48).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F),
			PartPose.offsetAndRotation(2.5F, -2.0F, -1.0F, -0.1745F, 0.0F, 0.5585F));
		body.addOrReplaceChild("shard_right2", CubeListBuilder.create()
			// shard_right2_core
			.texOffs(18, 54).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F),
			PartPose.offsetAndRotation(-2.5F, -2.0F, 3.0F, -0.2618F, 0.0F, -0.6981F));
		body.addOrReplaceChild("shard_left2", CubeListBuilder.create()
			// shard_left2_core
			.texOffs(25, 54).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F),
			PartPose.offsetAndRotation(2.5F, -2.0F, 3.0F, -0.2618F, 0.0F, 0.6981F));
		PartDefinition legFrontRight = body.addOrReplaceChild("leg_front_right", CubeListBuilder.create()
			// arm_front_right
			.texOffs(14, 48).addBox(-3.0F, -0.5F, -1.0F, 3.0F, 2.0F, 2.0F),
			PartPose.offsetAndRotation(-3.5F, 0.0F, -4.0F, 0.0F, 0.0F, 0.0F));
		legFrontRight.addOrReplaceChild("leg_front_right_lower", CubeListBuilder.create()
			// shin_front_right
			.texOffs(37, 27).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F)
			// foot_front_right
			.texOffs(9, 35).addBox(-1.5F, 3.5F, -3.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(-2.5F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition legFrontLeft = body.addOrReplaceChild("leg_front_left", CubeListBuilder.create()
			// arm_front_left
			.texOffs(25, 48).addBox(0.0F, -0.5F, -1.0F, 3.0F, 2.0F, 2.0F),
			PartPose.offsetAndRotation(3.5F, 0.0F, -4.0F, 0.0F, 0.0F, 0.0F));
		legFrontLeft.addOrReplaceChild("leg_front_left_lower", CubeListBuilder.create()
			// shin_front_left
			.texOffs(46, 27).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F)
			// foot_front_left
			.texOffs(24, 35).addBox(-1.5F, 3.5F, -3.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(2.5F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition legHindRight = body.addOrReplaceChild("leg_hind_right", CubeListBuilder.create()
			// arm_hind_right
			.texOffs(36, 48).addBox(-3.0F, -0.5F, -1.0F, 3.0F, 2.0F, 2.0F),
			PartPose.offsetAndRotation(-3.5F, 0.0F, 4.0F, 0.0F, 0.0F, 0.0F));
		legHindRight.addOrReplaceChild("leg_hind_right_lower", CubeListBuilder.create()
			// shin_hind_right
			.texOffs(55, 27).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F)
			// foot_hind_right
			.texOffs(39, 35).addBox(-1.5F, 3.5F, -3.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(-2.5F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition legHindLeft = body.addOrReplaceChild("leg_hind_left", CubeListBuilder.create()
			// arm_hind_left
			.texOffs(47, 48).addBox(0.0F, -0.5F, -1.0F, 3.0F, 2.0F, 2.0F),
			PartPose.offsetAndRotation(3.5F, 0.0F, 4.0F, 0.0F, 0.0F, 0.0F));
		legHindLeft.addOrReplaceChild("leg_hind_left_lower", CubeListBuilder.create()
			// shin_hind_left
			.texOffs(0, 35).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F)
			// foot_hind_left
			.texOffs(0, 42).addBox(-1.5F, 3.5F, -3.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(2.5F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create()
			// tail1_core
			.texOffs(39, 0).addBox(-2.5F, -1.5F, 0.0F, 5.0F, 3.0F, 6.0F)
			// tail1_spike
			.texOffs(46, 42).addBox(-0.5F, -3.5F, 1.0F, 1.0F, 2.0F, 3.0F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
			// tail2_core
			.texOffs(44, 17).addBox(-1.5F, -1.0F, 0.0F, 3.0F, 2.0F, 6.0F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, 0.0F, 0.0F, 0.0F));
		tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
			// tail3_core
			.texOffs(0, 27).addBox(-1.0F, -0.5F, 0.0F, 2.0F, 1.0F, 6.0F)
			// tail_shard
			.texOffs(17, 27).addBox(-0.5F, -2.0F, 5.5F, 1.0F, 3.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, 0.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
		// END GENERATED LAYER
	}

	@Override
	public void setupAnim(final CrystalLizardRenderState state) {
		super.setupAnim(state);
		float still = state.still;
		float awake = 1.0F - still;
		float pos = state.walkAnimationPos * 0.9F;
		float speed = Math.min(1.0F, state.walkAnimationSpeed * 1.4F) * awake;

		// diagonal pairs: the right foreleg with the left hind leg, then the other two; each lifts as it swings forward
		float swing = Mth.cos(pos) * 0.65F * speed;
		float lift = Math.max(0.0F, -Mth.sin(pos)) * 0.4F * speed;
		float liftOther = Math.max(0.0F, Mth.sin(pos)) * 0.4F * speed;
		this.frontRight.yRot -= swing;
		this.hindLeft.yRot += swing;
		this.frontLeft.yRot -= swing;
		this.hindRight.yRot += swing;
		this.frontRight.zRot += lift + still * 0.35F;
		this.hindLeft.zRot -= lift + still * 0.35F;
		this.frontLeft.zRot -= liftOther + still * 0.35F;
		this.hindRight.zRot += liftOther + still * 0.35F;

		// the body sways against its stride and sinks flat when it lies still
		this.body.yRot += Mth.cos(pos) * 0.14F * speed;
		this.body.y += still * 1.6F;

		// the tail follows the sway, more towards its tip; idly, a slow sweep
		for (int i = 0; i < this.tail.length; i++) {
			this.tail[i].yRot += -Mth.cos(pos - 0.6F * (i + 1)) * (0.18F + 0.06F * i) * speed
				+ Mth.sin(state.ageInTicks * 0.05F - i * 0.6F) * 0.05F * awake;
			this.tail[i].xRot += still * (i == 0 ? -0.08F : 0.04F);
		}

		// the head follows its gaze; now and then the jaw flicks, tasting the air, and opens wide to bite
		float yaw = state.yRot * Mth.DEG_TO_RAD;
		float pitch = state.xRot * Mth.DEG_TO_RAD;
		this.head.yRot += yaw * 0.7F * awake;
		this.head.xRot += pitch * 0.6F * awake + still * 0.12F;
		float taste = Math.max(0.0F, Mth.sin(state.ageInTicks * 0.09F) - 0.9F) * 3.0F * awake;
		this.jaw.xRot += state.bite * 0.75F + taste * 0.3F;
	}
}
