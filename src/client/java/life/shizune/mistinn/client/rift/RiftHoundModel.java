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
 * 裂隙獵犬的模型: a gaunt hound: a deep chest, a narrow waist and haunch, a long head with a jaw
 * that opens wide and two swept-back horns, long legs, a tail ending in a shard, and shards of rift
 * crystal along its spine and out of its shoulders. The boxes between the GENERATED markers come
 * from the rift hound's model spec together with its 128x128 texture layout; do not edit them by hand.
 * <p>
 * It trots with its legs in diagonal pairs and breaks into a bounding gallop at speed; before a
 * lunge it crouches low on its forelegs, and in the lunge it stretches out, jaws wide.
 */
public class RiftHoundModel extends EntityModel<RiftHoundRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("rift_hound"), "main");

	private final ModelPart body;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart frontRight;
	private final ModelPart frontLeft;
	private final ModelPart hindRight;
	private final ModelPart hindLeft;
	private final ModelPart frontRightLower;
	private final ModelPart frontLeftLower;
	private final ModelPart hindRightLower;
	private final ModelPart hindLeftLower;
	private final ModelPart tail1;
	private final ModelPart tail2;

	public RiftHoundModel(final ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.neck = this.body.getChild("neck");
		this.head = this.neck.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.frontRight = this.body.getChild("leg_front_right");
		this.frontLeft = this.body.getChild("leg_front_left");
		this.hindRight = this.body.getChild("leg_hind_right");
		this.hindLeft = this.body.getChild("leg_hind_left");
		this.frontRightLower = this.frontRight.getChild("leg_front_right_lower");
		this.frontLeftLower = this.frontLeft.getChild("leg_front_left_lower");
		this.hindRightLower = this.hindRight.getChild("leg_hind_right_lower");
		this.hindLeftLower = this.hindLeft.getChild("leg_hind_left_lower");
		this.tail1 = this.body.getChild("tail1");
		this.tail2 = this.tail1.getChild("tail2");
	}

	public static LayerDefinition createBodyLayer() {
		// BEGIN GENERATED LAYER
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			// chest
			.texOffs(0, 0).addBox(-4.0F, -4.0F, -9.0F, 8.0F, 8.0F, 8.0F)
			// waist
			.texOffs(33, 0).addBox(-3.0F, -2.5F, -1.0F, 6.0F, 5.0F, 9.0F)
			// haunch
			.texOffs(102, 17).addBox(-3.5F, -2.0F, 7.0F, 7.0F, 5.0F, 4.0F, new CubeDeformation(0.02F))
			// ribs_right
			.texOffs(76, 17).addBox(-4.6F, -2.0F, -7.0F, 1.0F, 5.0F, 5.0F, new CubeDeformation(0.02F))
			// ribs_left
			.texOffs(89, 17).addBox(3.6F, -2.0F, -7.0F, 1.0F, 5.0F, 5.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
			// neck_core
			.texOffs(55, 17).addBox(-2.5F, -3.0F, -4.0F, 5.0F, 5.0F, 5.0F),
			PartPose.offsetAndRotation(0.0F, -1.0F, -8.0F, -0.4887F, 0.0F, 0.0F));
		PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
			// skull
			.texOffs(102, 0).addBox(-3.0F, -2.0F, -6.0F, 6.0F, 5.0F, 6.0F)
			// snout
			.texOffs(48, 29).addBox(-2.0F, -0.5F, -11.0F, 4.0F, 3.0F, 5.0F)
			// brow_right
			.texOffs(117, 39).addBox(-3.5F, -2.5F, -6.0F, 2.0F, 1.0F, 3.0F)
			// brow_left
			.texOffs(0, 47).addBox(1.5F, -2.5F, -6.0F, 2.0F, 1.0F, 3.0F)
			// eye_right
			.texOffs(11, 47).addBox(-3.35F, 0.0F, -5.5F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.02F))
			// eye_left
			.texOffs(18, 47).addBox(2.35F, 0.0F, -5.5F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.02F))
			// fang_right
			.texOffs(25, 47).addBox(-2.2F, 2.25F, -10.5F, 1.0F, 2.0F, 1.0F)
			// fang_left
			.texOffs(30, 47).addBox(1.2F, 2.25F, -10.5F, 1.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, -2.0F, -3.0F, 0.4189F, 0.0F, 0.0F));
		head.addOrReplaceChild("jaw", CubeListBuilder.create()
			// jaw_core
			.texOffs(0, 17).addBox(-1.5F, 0.0F, -8.75F, 3.0F, 2.0F, 9.0F),
			PartPose.offsetAndRotation(0.0F, 2.5F, -2.0F, 0.0F, 0.0F, 0.0F));
		head.addOrReplaceChild("horn_right", CubeListBuilder.create()
			// horn_right_core
			.texOffs(95, 29).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F),
			PartPose.offsetAndRotation(-2.0F, -2.0F, -2.0F, 0.3142F, -0.2094F, 0.0F));
		head.addOrReplaceChild("horn_left", CubeListBuilder.create()
			// horn_left_core
			.texOffs(110, 29).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F),
			PartPose.offsetAndRotation(2.0F, -2.0F, -2.0F, 0.3142F, 0.2094F, 0.0F));
		body.addOrReplaceChild("shard1", CubeListBuilder.create()
			// shard1_core
			.texOffs(0, 39).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -4.0F, -6.0F, -0.384F, 0.0F, 0.0F));
		body.addOrReplaceChild("shard2", CubeListBuilder.create()
			// shard2_core
			.texOffs(19, 29).addBox(-1.5F, -7.0F, -1.0F, 3.0F, 7.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -4.0F, -2.0F, -0.5236F, 0.0F, 0.0F));
		body.addOrReplaceChild("shard3", CubeListBuilder.create()
			// shard3_core
			.texOffs(9, 39).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -2.0F, 3.0F, -0.6283F, 0.0F, 0.0F));
		body.addOrReplaceChild("shard4", CubeListBuilder.create()
			// shard4_core
			.texOffs(110, 39).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -2.0F, 7.0F, -0.7679F, 0.0F, 0.0F));
		body.addOrReplaceChild("shard_right", CubeListBuilder.create()
			// shard_right_core
			.texOffs(96, 39).addBox(-0.5F, -4.0F, -1.0F, 1.0F, 4.0F, 2.0F),
			PartPose.offsetAndRotation(-3.5F, -3.0F, -5.0F, -0.3142F, 0.0F, -0.6632F));
		body.addOrReplaceChild("shard_left", CubeListBuilder.create()
			// shard_left_core
			.texOffs(103, 39).addBox(-0.5F, -4.0F, -1.0F, 1.0F, 4.0F, 2.0F),
			PartPose.offsetAndRotation(3.5F, -3.0F, -5.0F, -0.3142F, 0.0F, 0.6632F));
		PartDefinition legFrontRight = body.addOrReplaceChild("leg_front_right", CubeListBuilder.create()
			// upper_front_right
			.texOffs(25, 17).addBox(-2.0F, -0.5F, -2.0F, 3.0F, 7.0F, 4.0F),
			PartPose.offsetAndRotation(-2.8F, 1.0F, -5.0F, 0.0F, 0.0F, 0.0F));
		legFrontRight.addOrReplaceChild("leg_front_right_lower", CubeListBuilder.create()
			// lower_front_right
			.texOffs(18, 39).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F)
			// paw_front_right
			.texOffs(36, 39).addBox(-1.5F, 4.0F, -2.5F, 3.0F, 2.0F, 4.0F),
			PartPose.offsetAndRotation(-0.5F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition legFrontLeft = body.addOrReplaceChild("leg_front_left", CubeListBuilder.create()
			// upper_front_left
			.texOffs(40, 17).addBox(-1.0F, -0.5F, -2.0F, 3.0F, 7.0F, 4.0F),
			PartPose.offsetAndRotation(2.8F, 1.0F, -5.0F, 0.0F, 0.0F, 0.0F));
		legFrontLeft.addOrReplaceChild("leg_front_left_lower", CubeListBuilder.create()
			// lower_front_left
			.texOffs(27, 39).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F)
			// paw_front_left
			.texOffs(51, 39).addBox(-1.5F, 4.0F, -2.5F, 3.0F, 2.0F, 4.0F),
			PartPose.offsetAndRotation(0.5F, 6.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition legHindRight = body.addOrReplaceChild("leg_hind_right", CubeListBuilder.create()
			// thigh_right
			.texOffs(64, 0).addBox(-1.5F, -1.0F, -2.5F, 3.0F, 7.0F, 6.0F),
			PartPose.offsetAndRotation(-2.8F, 0.0F, 8.5F, 0.0F, 0.0F, 0.0F));
		legHindRight.addOrReplaceChild("leg_hind_right_lower", CubeListBuilder.create()
			// lower_hind_right
			.texOffs(30, 29).addBox(-1.0F, -0.75F, -1.0F, 2.0F, 7.0F, 2.0F)
			// paw_hind_right
			.texOffs(66, 39).addBox(-1.5F, 5.0F, -2.5F, 3.0F, 2.0F, 4.0F),
			PartPose.offsetAndRotation(-0.5F, 6.0F, 1.5F, 0.0F, 0.0F, 0.0F));
		PartDefinition legHindLeft = body.addOrReplaceChild("leg_hind_left", CubeListBuilder.create()
			// thigh_left
			.texOffs(83, 0).addBox(-1.5F, -1.0F, -2.5F, 3.0F, 7.0F, 6.0F),
			PartPose.offsetAndRotation(2.8F, 0.0F, 8.5F, 0.0F, 0.0F, 0.0F));
		legHindLeft.addOrReplaceChild("leg_hind_left_lower", CubeListBuilder.create()
			// lower_hind_left
			.texOffs(39, 29).addBox(-1.0F, -0.75F, -1.0F, 2.0F, 7.0F, 2.0F)
			// paw_hind_left
			.texOffs(81, 39).addBox(-1.5F, 5.0F, -2.5F, 3.0F, 2.0F, 4.0F),
			PartPose.offsetAndRotation(0.5F, 6.0F, 1.5F, 0.0F, 0.0F, 0.0F));
		PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create()
			// tail1_core
			.texOffs(0, 29).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 6.0F),
			PartPose.offsetAndRotation(0.0F, -1.0F, 11.0F, -0.2793F, 0.0F, 0.0F));
		tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
			// tail2_core
			.texOffs(67, 29).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F)
			// tail_shard
			.texOffs(84, 29).addBox(-0.5F, -2.5F, 5.5F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, 0.2443F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 128, 128);
		// END GENERATED LAYER
	}

	@Override
	public void setupAnim(final RiftHoundRenderState state) {
		super.setupAnim(state);
		float crouch = state.crouch;
		float lunge = state.lunge;
		float free = (1.0F - crouch) * (1.0F - lunge);
		float pos = state.walkAnimationPos * 0.66F;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float gallop = Mth.clamp((state.walkAnimationSpeed - 0.55F) * 2.5F, 0.0F, 1.0F);

		// a trot in diagonal pairs, blending into a bound with the forelegs together and the hind legs together
		float trot = Mth.cos(pos) * 1.1F * speed;
		float bound = Mth.cos(pos * 0.8F) * 1.3F * speed;
		float boundHind = Mth.cos(pos * 0.8F + Mth.PI) * 1.3F * speed;
		float fr = Mth.lerp(gallop, trot, bound);
		float fl = Mth.lerp(gallop, -trot, bound);
		float hr = Mth.lerp(gallop, -trot, boundHind);
		float hl = Mth.lerp(gallop, trot, boundHind);
		this.frontRight.xRot += fr * free;
		this.frontLeft.xRot += fl * free;
		this.hindRight.xRot += hr * free;
		this.hindLeft.xRot += hl * free;
		// the lower legs fold as each leg comes forward
		this.frontRightLower.xRot += Math.max(0.0F, -fr) * 0.6F * free;
		this.frontLeftLower.xRot += Math.max(0.0F, -fl) * 0.6F * free;
		this.hindRightLower.xRot -= Math.max(0.0F, hr) * 0.5F * free;
		this.hindLeftLower.xRot -= Math.max(0.0F, hl) * 0.5F * free;
		this.body.xRot += Mth.sin(pos * 0.8F) * 0.08F * gallop * free;
		this.body.y += Math.abs(Mth.sin(pos)) * -0.8F * speed * free;

		// crouched to lunge: low on its forelegs, hind legs gathered
		this.body.y += crouch * 2.5F;
		this.body.xRot += crouch * 0.22F;
		this.frontRight.xRot -= crouch * 0.55F;
		this.frontLeft.xRot -= crouch * 0.55F;
		this.frontRightLower.xRot += crouch * 0.6F;
		this.frontLeftLower.xRot += crouch * 0.6F;
		this.hindRight.xRot += crouch * 0.4F;
		this.hindLeft.xRot += crouch * 0.4F;
		this.hindRightLower.xRot -= crouch * 0.5F;
		this.hindLeftLower.xRot -= crouch * 0.5F;

		// in the lunge: stretched out, forelegs reaching, hind legs trailing, jaws wide
		this.frontRight.xRot -= lunge * 1.1F;
		this.frontLeft.xRot -= lunge * 1.0F;
		this.hindRight.xRot += lunge * 1.0F;
		this.hindLeft.xRot += lunge * 1.1F;
		this.body.xRot -= lunge * 0.12F;

		// the head follows its gaze, held low as it stalks
		float yaw = state.yRot * Mth.DEG_TO_RAD;
		float pitch = state.xRot * Mth.DEG_TO_RAD;
		this.neck.yRot += yaw * 0.35F;
		this.head.yRot += yaw * 0.45F;
		this.head.xRot += pitch * 0.6F + crouch * 0.2F - lunge * 0.25F;
		float pant = Mth.sin(state.ageInTicks * 0.3F) * 0.05F * free;
		this.jaw.xRot += Math.max(state.bite, lunge) * 0.8F + crouch * 0.25F + Math.max(0.0F, pant);

		// the tail streams out behind at speed and in the lunge, and sways as it goes
		this.tail1.xRot -= (gallop * 0.35F + lunge * 0.5F) * 1.0F - crouch * 0.2F;
		this.tail1.yRot += Mth.sin(state.ageInTicks * 0.12F) * 0.12F * free + Mth.cos(pos) * 0.15F * speed * free;
		this.tail2.yRot += Mth.sin(state.ageInTicks * 0.12F - 0.6F) * 0.14F * free;
	}
}
