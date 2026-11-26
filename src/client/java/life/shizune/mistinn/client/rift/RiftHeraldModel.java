package life.shizune.mistinn.client.rift;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.rift.RiftHerald;
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
 * 裂隙使者的模型: a tall, gaunt figure robed in tatters: long legs under a robe of four hanging
 * panels, arms that reach past its knees and end in claws, a long masked head with two swept horns,
 * shards of rift crystal on its shoulders and back, and over its head a ring of six shards. The
 * boxes between the GENERATED markers come from the rift herald's model spec together with its
 * 128x128 texture layout; do not edit them by hand.
 * <p>
 * It walks with a slow stride, its long arms swinging a beat behind and the robe's panels flaring
 * and fluttering; the ring turns over its head. Dragging a hunter in, it reaches out with its right
 * hand; calling the rift down, it raises both and the ring lifts; it strikes with its right claw.
 */
public class RiftHeraldModel extends EntityModel<RiftHeraldRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("rift_herald"), "main");

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart halo;
	private final ModelPart[] shards = new ModelPart[6];
	private final ModelPart armRight;
	private final ModelPart armLeft;
	private final ModelPart forearmRight;
	private final ModelPart forearmLeft;
	private final ModelPart legRight;
	private final ModelPart legLeft;
	private final ModelPart shinRight;
	private final ModelPart shinLeft;
	private final ModelPart robeFront;
	private final ModelPart robeBack;
	private final ModelPart robeRight;
	private final ModelPart robeLeft;

	public RiftHeraldModel(final ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.halo = this.head.getChild("halo");
		for (int i = 0; i < this.shards.length; i++) {
			this.shards[i] = this.halo.getChild("halo_" + (i + 1));
		}
		this.armRight = this.body.getChild("arm_right");
		this.armLeft = this.body.getChild("arm_left");
		this.forearmRight = this.armRight.getChild("forearm_right");
		this.forearmLeft = this.armLeft.getChild("forearm_left");
		this.legRight = root.getChild("leg_right");
		this.legLeft = root.getChild("leg_left");
		this.shinRight = this.legRight.getChild("shin_right");
		this.shinLeft = this.legLeft.getChild("shin_left");
		this.robeFront = this.body.getChild("robe_front");
		this.robeBack = this.body.getChild("robe_back");
		this.robeRight = this.body.getChild("robe_right");
		this.robeLeft = this.body.getChild("robe_left");
	}

	public static LayerDefinition createBodyLayer() {
		// BEGIN GENERATED LAYER
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			// chest
			.texOffs(26, 23).addBox(-4.5F, -12.0F, -2.5F, 9.0F, 8.0F, 5.0F)
			// waist
			.texOffs(99, 23).addBox(-3.5F, -4.0F, -2.0F, 7.0F, 5.0F, 4.0F)
			// collar
			.texOffs(103, 38).addBox(-3.0F, -13.0F, -2.0F, 6.0F, 2.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
			// neck
			.texOffs(56, 48).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 2.0F, 3.0F)
			// skull
			.texOffs(92, 0).addBox(-3.0F, -10.0F, -3.0F, 6.0F, 8.0F, 6.0F)
			// mask
			.texOffs(42, 38).addBox(-2.5F, -9.5F, -4.0F, 5.0F, 7.0F, 1.0F, new CubeDeformation(0.02F))
			// jaw
			.texOffs(104, 48).addBox(-2.0F, -3.0F, -3.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.02F))
			// brow
			.texOffs(42, 55).addBox(-3.0F, -8.0F, -4.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.04F)),
			PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		head.addOrReplaceChild("horn_right", CubeListBuilder.create()
			// horn_right_core
			.texOffs(64, 38).addBox(-0.5F, -7.0F, -0.5F, 1.0F, 7.0F, 1.0F),
			PartPose.offsetAndRotation(-2.5F, -8.5F, 0.0F, -0.4887F, 0.0F, -0.3142F));
		head.addOrReplaceChild("horn_left", CubeListBuilder.create()
			// horn_left_core
			.texOffs(69, 38).addBox(-0.5F, -7.0F, -0.5F, 1.0F, 7.0F, 1.0F),
			PartPose.offsetAndRotation(2.5F, -8.5F, 0.0F, -0.4887F, 0.0F, 0.3142F));
		PartDefinition halo = head.addOrReplaceChild("halo", CubeListBuilder.create(),
			PartPose.offsetAndRotation(0.0F, -14.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		halo.addOrReplaceChild("halo_1", CubeListBuilder.create()
			// halo_shard_1
			.texOffs(88, 38).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 6.0F, 1.0F)
			// halo_chip_1
			.texOffs(0, 55).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(-6.5F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2443F));
		halo.addOrReplaceChild("halo_2", CubeListBuilder.create()
			// halo_shard_2
			.texOffs(89, 48).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F)
			// halo_chip_2
			.texOffs(7, 55).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(-3.25F, 0.0F, 5.629F, 0.0F, 1.0472F, 0.3142F));
		halo.addOrReplaceChild("halo_3", CubeListBuilder.create()
			// halo_shard_3
			.texOffs(93, 38).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 6.0F, 1.0F)
			// halo_chip_3
			.texOffs(14, 55).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(3.25F, 0.0F, 5.629F, 0.0F, 2.0944F, -0.2443F));
		halo.addOrReplaceChild("halo_4", CubeListBuilder.create()
			// halo_shard_4
			.texOffs(94, 48).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F)
			// halo_chip_4
			.texOffs(21, 55).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(6.5F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.3142F));
		halo.addOrReplaceChild("halo_5", CubeListBuilder.create()
			// halo_shard_5
			.texOffs(98, 38).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 6.0F, 1.0F)
			// halo_chip_5
			.texOffs(28, 55).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(3.25F, 0.0F, -5.629F, 0.0F, 4.1888F, -0.2443F));
		halo.addOrReplaceChild("halo_6", CubeListBuilder.create()
			// halo_shard_6
			.texOffs(99, 48).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 4.0F, 1.0F)
			// halo_chip_6
			.texOffs(35, 55).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(-3.25F, 0.0F, -5.629F, 0.0F, 5.236F, 0.3142F));
		PartDefinition shoulderRight = body.addOrReplaceChild("shoulder_right", CubeListBuilder.create()
			// pauldron_right
			.texOffs(0, 38).addBox(-2.5F, -1.5F, -3.0F, 4.0F, 3.0F, 6.0F),
			PartPose.offsetAndRotation(-5.5F, -11.0F, 0.0F, 0.0F, 0.0F, -0.1396F));
		PartDefinition shoulderLeft = body.addOrReplaceChild("shoulder_left", CubeListBuilder.create()
			// pauldron_left
			.texOffs(21, 38).addBox(-1.5F, -1.5F, -3.0F, 4.0F, 3.0F, 6.0F),
			PartPose.offsetAndRotation(5.5F, -11.0F, 0.0F, 0.0F, 0.0F, 0.1396F));
		shoulderRight.addOrReplaceChild("spike_right", CubeListBuilder.create()
			// spike_right_core
			.texOffs(74, 38).addBox(-0.5F, -5.0F, -1.0F, 1.0F, 5.0F, 2.0F),
			PartPose.offsetAndRotation(-0.5F, -1.5F, 0.0F, 0.0F, 0.0F, -0.4189F));
		shoulderLeft.addOrReplaceChild("spike_left", CubeListBuilder.create()
			// spike_left_core
			.texOffs(81, 38).addBox(-0.5F, -5.0F, -1.0F, 1.0F, 5.0F, 2.0F),
			PartPose.offsetAndRotation(0.5F, -1.5F, 0.0F, 0.0F, 0.0F, 0.4189F));
		body.addOrReplaceChild("back_shard", CubeListBuilder.create()
			// back_shard_core
			.texOffs(55, 38).addBox(-1.0F, -6.0F, 0.0F, 2.0F, 6.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -9.0F, 2.5F, -0.5236F, 0.0F, 0.0F));
		PartDefinition armRight = body.addOrReplaceChild("arm_right", CubeListBuilder.create()
			// upper_arm_right
			.texOffs(66, 0).addBox(-1.5F, -0.25F, -1.5F, 3.0F, 12.0F, 3.0F),
			PartPose.offsetAndRotation(-6.5F, -10.5F, 0.0F, 0.0F, 0.0F, 0.0698F));
		armRight.addOrReplaceChild("forearm_right", CubeListBuilder.create()
			// lower_arm_right
			.texOffs(0, 23).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F)
			// hand_right
			.texOffs(30, 48).addBox(-1.5F, 11.0F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.02F))
			// claw_right_1
			.texOffs(69, 48).addBox(0.5F, 14.0F, -1.5F, 1.0F, 4.0F, 1.0F)
			// claw_right_2
			.texOffs(74, 48).addBox(-1.5F, 14.0F, -1.5F, 1.0F, 4.0F, 1.0F)
			// claw_right_3
			.texOffs(117, 48).addBox(-0.5F, 14.0F, 0.5F, 1.0F, 3.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 11.5F, 0.0F, 0.1047F, 0.0F, 0.0F));
		PartDefinition armLeft = body.addOrReplaceChild("arm_left", CubeListBuilder.create()
			// upper_arm_left
			.texOffs(79, 0).addBox(-1.5F, -0.25F, -1.5F, 3.0F, 12.0F, 3.0F),
			PartPose.offsetAndRotation(6.5F, -10.5F, 0.0F, 0.0F, 0.0F, -0.0698F));
		armLeft.addOrReplaceChild("forearm_left", CubeListBuilder.create()
			// lower_arm_left
			.texOffs(13, 23).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F)
			// hand_left
			.texOffs(43, 48).addBox(-1.5F, 11.0F, -1.5F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.02F))
			// claw_left_1
			.texOffs(79, 48).addBox(-1.5F, 14.0F, -1.5F, 1.0F, 4.0F, 1.0F)
			// claw_left_2
			.texOffs(84, 48).addBox(0.5F, 14.0F, -1.5F, 1.0F, 4.0F, 1.0F)
			// claw_left_3
			.texOffs(122, 48).addBox(-0.5F, 14.0F, 0.5F, 1.0F, 3.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 11.5F, 0.0F, 0.1047F, 0.0F, 0.0F));
		PartDefinition legRight = root.addOrReplaceChild("leg_right", CubeListBuilder.create()
			// thigh_right
			.texOffs(55, 23).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F),
			PartPose.offsetAndRotation(-2.0F, 3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		legRight.addOrReplaceChild("shin_right", CubeListBuilder.create()
			// shin_right_core
			.texOffs(81, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F)
			// foot_right
			.texOffs(0, 48).addBox(-1.5F, 9.25F, -3.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(-0.5F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition legLeft = root.addOrReplaceChild("leg_left", CubeListBuilder.create()
			// thigh_left
			.texOffs(68, 23).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F),
			PartPose.offsetAndRotation(2.0F, 3.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		legLeft.addOrReplaceChild("shin_left", CubeListBuilder.create()
			// shin_left_core
			.texOffs(90, 23).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 10.0F, 2.0F)
			// foot_left
			.texOffs(15, 48).addBox(-1.5F, 9.25F, -3.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.5F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		body.addOrReplaceChild("robe_front", CubeListBuilder.create()
			// robe_front_panel
			.texOffs(47, 0).addBox(-4.0F, 0.0F, -0.5F, 8.0F, 18.0F, 1.0F, new CubeDeformation(0.03F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, -2.5F, 0.0698F, 0.0F, 0.0F));
		body.addOrReplaceChild("robe_back", CubeListBuilder.create()
			// robe_back_panel
			.texOffs(26, 0).addBox(-4.5F, 0.0F, -0.5F, 9.0F, 20.0F, 1.0F, new CubeDeformation(0.03F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 2.5F, -0.0873F, 0.0F, 0.0F));
		body.addOrReplaceChild("robe_right", CubeListBuilder.create()
			// robe_right_panel
			.texOffs(0, 0).addBox(-1.0F, 0.0F, -2.5F, 1.0F, 17.0F, 5.0F, new CubeDeformation(0.03F)),
			PartPose.offsetAndRotation(-4.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.0873F));
		body.addOrReplaceChild("robe_left", CubeListBuilder.create()
			// robe_left_panel
			.texOffs(13, 0).addBox(0.0F, 0.0F, -2.5F, 1.0F, 17.0F, 5.0F, new CubeDeformation(0.03F)),
			PartPose.offsetAndRotation(4.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0873F));
		return LayerDefinition.create(mesh, 128, 128);
		// END GENERATED LAYER
	}

	@Override
	public void setupAnim(final RiftHeraldRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float pos = state.walkAnimationPos * 0.6F;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float drag = state.castKind == RiftHerald.Cast.DRAG ? state.cast : 0.0F;
		float mark = state.castKind == RiftHerald.Cast.MARK ? state.cast : 0.0F;

		// a slow stride: the knees bend as each leg comes through
		float stride = Mth.cos(pos) * 0.85F * speed;
		this.legRight.xRot += stride;
		this.legLeft.xRot -= stride;
		this.shinRight.xRot += Math.max(0.0F, -stride) * 0.9F;
		this.shinLeft.xRot += Math.max(0.0F, stride) * 0.9F;
		this.body.zRot += Mth.cos(pos) * 0.035F * speed;
		this.body.y += Math.abs(Mth.sin(pos)) * -0.6F * speed + Mth.sin(age * 0.06F) * 0.25F;

		// the long arms swing a beat behind the legs and hang loose, swaying a little when it stands
		float sway = Mth.sin(age * 0.05F) * 0.05F;
		this.armRight.xRot += -Mth.cos(pos - 0.6F) * 0.4F * speed + sway;
		this.armLeft.xRot += Mth.cos(pos - 0.6F) * 0.4F * speed - sway;
		this.forearmRight.xRot -= Math.max(0.0F, Mth.sin(pos - 1.2F)) * 0.3F * speed;
		this.forearmLeft.xRot -= Math.max(0.0F, -Mth.sin(pos - 1.2F)) * 0.3F * speed;

		// dragging a hunter in: the right hand reaches out at them
		this.armRight.xRot = Mth.lerp(drag, this.armRight.xRot, -1.45F);
		this.armRight.zRot = Mth.lerp(drag, this.armRight.zRot, 0.08F);
		this.forearmRight.xRot = Mth.lerp(drag, this.forearmRight.xRot, -0.1F);
		this.armLeft.xRot = Mth.lerp(drag, this.armLeft.xRot, 0.3F);

		// calling the rift down: both hands raised, the face turned up, the ring lifted and spread
		this.armRight.xRot = Mth.lerp(mark, this.armRight.xRot, -2.75F);
		this.armLeft.xRot = Mth.lerp(mark, this.armLeft.xRot, -2.75F);
		this.armRight.zRot = Mth.lerp(mark, this.armRight.zRot, 0.4F);
		this.armLeft.zRot = Mth.lerp(mark, this.armLeft.zRot, -0.4F);
		this.forearmRight.xRot = Mth.lerp(mark, this.forearmRight.xRot, -0.25F);
		this.forearmLeft.xRot = Mth.lerp(mark, this.forearmLeft.xRot, -0.25F);

		// a blow: the right claw comes down
		float blow = Mth.sin(state.attack * Mth.PI);
		this.armRight.xRot -= blow * 1.7F;
		this.forearmRight.xRot -= blow * 0.4F;
		this.body.yRot -= blow * 0.25F;

		// the head follows its gaze
		this.head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.8F;
		this.head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.7F - mark * 0.4F;

		// the ring turns, and rises and spreads when it calls the rift
		this.halo.yRot += age * 0.045F;
		this.halo.y -= mark * 2.5F + Mth.sin(age * 0.08F) * 0.4F;
		for (int i = 0; i < this.shards.length; i++) {
			this.shards[i].zRot += Mth.sin(age * 0.11F + i) * 0.08F + mark * (i % 2 == 0 ? 0.35F : -0.35F);
			this.shards[i].y += Mth.sin(age * 0.09F + i * 1.7F) * 0.6F;
		}

		// the robe: its panels flare with the stride and flutter as it stands
		float flare = Math.abs(stride) * 0.35F;
		this.robeFront.xRot -= flare + Mth.sin(age * 0.09F) * 0.03F;
		this.robeBack.xRot += flare * 0.8F + Mth.sin(age * 0.08F + 1.1F) * 0.04F;
		this.robeRight.zRot += flare * 0.5F + Mth.sin(age * 0.1F + 2.0F) * 0.03F;
		this.robeLeft.zRot -= flare * 0.5F + Mth.sin(age * 0.1F + 0.5F) * 0.03F;
	}
}
