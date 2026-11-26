package life.shizune.mistinn.client.terra;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.terra.CrystalElder;
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
 * 晶龍的模型: a wyvern of faceted crystal: a long neck and a horned, crested head with a jaw that
 * opens, two wings of arm, forearm and three finger spars with flat crystal panes between them,
 * two hind legs of thigh, shin and foot, and a tail of four segments ending in a blade. The boxes
 * between the GENERATED markers come from the crystal drake's model spec together with its
 * 256x256 texture layout; do not edit them by hand.
 * <p>
 * Aloft it beats its wings (the forearms following a beat behind), sweeps them back as it dives,
 * tucks its legs and lets its tail stream; on the ground it folds its wings along its back and
 * walks on its hind legs. The crown elder's moves on the ground are posed on top: coiling and
 * turning a full circle for its sweep, head lowered and pawing before a charge and streamlined
 * in it, and slumped with its head to the ground when a charge has stunned it.
 */
public class CrystalDrakeModel extends EntityModel<CrystalDrakeRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("crystal_drake"), "main");

	private final ModelPart body;
	private final ModelPart neckLower;
	private final ModelPart neckUpper;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart[] tail = new ModelPart[4];
	private final ModelPart legUpperRight;
	private final ModelPart legUpperLeft;
	private final ModelPart legLowerRight;
	private final ModelPart legLowerLeft;
	private final ModelPart wingUpperRight;
	private final ModelPart wingUpperLeft;
	private final ModelPart wingLowerRight;
	private final ModelPart wingLowerLeft;
	private final ModelPart[] fingersRight = new ModelPart[3];
	private final ModelPart[] fingersLeft = new ModelPart[3];

	public CrystalDrakeModel(final ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.neckLower = this.body.getChild("neck_lower");
		this.neckUpper = this.neckLower.getChild("neck_upper");
		this.head = this.neckUpper.getChild("head");
		this.jaw = this.head.getChild("jaw");
		ModelPart segment = this.body;
		for (int i = 0; i < this.tail.length; i++) {
			segment = segment.getChild("tail" + (i + 1));
			this.tail[i] = segment;
		}
		this.legUpperRight = this.body.getChild("leg_upper_right");
		this.legUpperLeft = this.body.getChild("leg_upper_left");
		this.legLowerRight = this.legUpperRight.getChild("leg_lower_right");
		this.legLowerLeft = this.legUpperLeft.getChild("leg_lower_left");
		this.wingUpperRight = this.body.getChild("wing_upper_right");
		this.wingUpperLeft = this.body.getChild("wing_upper_left");
		this.wingLowerRight = this.wingUpperRight.getChild("wing_lower_right");
		this.wingLowerLeft = this.wingUpperLeft.getChild("wing_lower_left");
		for (int i = 0; i < 3; i++) {
			this.fingersRight[i] = this.wingLowerRight.getChild("finger" + (i + 1) + "_right");
			this.fingersLeft[i] = this.wingLowerLeft.getChild("finger" + (i + 1) + "_left");
		}
	}

	public static LayerDefinition createBodyLayer() {
		// BEGIN GENERATED LAYER
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			// chest
			.texOffs(0, 0).addBox(-7.0F, -5.5F, -10.0F, 14.0F, 11.0F, 16.0F)
			// keel
			.texOffs(145, 66).addBox(-4.0F, 5.4F, -8.0F, 8.0F, 2.0F, 11.0F)
			// abdomen
			.texOffs(116, 0).addBox(-5.5F, -4.25F, 5.25F, 11.0F, 9.0F, 11.0F)
			// back_ridge
			.texOffs(61, 0).addBox(-2.0F, -6.45F, -9.0F, 4.0F, 1.0F, 23.0F)
			// shoulder_plate_right
			.texOffs(0, 66).addBox(-8.5F, -6.8F, -9.75F, 2.0F, 6.0F, 9.0F)
			// shoulder_plate_left
			.texOffs(23, 66).addBox(6.5F, -6.8F, -9.75F, 2.0F, 6.0F, 9.0F)
			// hip_plate_right
			.texOffs(77, 66).addBox(-7.0F, -2.75F, 6.0F, 2.0F, 7.0F, 7.0F)
			// hip_plate_left
			.texOffs(96, 66).addBox(5.0F, -2.75F, 6.0F, 2.0F, 7.0F, 7.0F)
			// spine1
			.texOffs(205, 106).addBox(-1.0F, -11.5F, -7.0F, 2.0F, 5.0F, 3.0F)
			// spine2
			.texOffs(89, 106).addBox(-1.0F, -12.5F, -2.0F, 2.0F, 6.0F, 3.0F)
			// spine3
			.texOffs(216, 106).addBox(-1.0F, -11.5F, 3.0F, 2.0F, 5.0F, 3.0F)
			// spine4
			.texOffs(24, 116).addBox(-1.0F, -10.25F, 8.0F, 2.0F, 4.0F, 3.0F)
			// spine5
			.texOffs(27, 124).addBox(-1.0F, -9.25F, 12.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.05F))
			// rib_right_1
			.texOffs(227, 106).addBox(-7.95F, -2.0F, -7.0F, 1.0F, 6.0F, 2.0F)
			// rib_right_2
			.texOffs(234, 106).addBox(-7.95F, -2.25F, -3.0F, 1.0F, 6.0F, 2.0F)
			// rib_right_3
			.texOffs(35, 116).addBox(-7.95F, -2.0F, 1.0F, 1.0F, 5.0F, 2.0F)
			// rib_left_1
			.texOffs(241, 106).addBox(6.95F, -2.0F, -7.0F, 1.0F, 6.0F, 2.0F)
			// rib_left_2
			.texOffs(248, 106).addBox(6.95F, -2.25F, -3.0F, 1.0F, 6.0F, 2.0F)
			// rib_left_3
			.texOffs(42, 116).addBox(6.95F, -2.0F, 1.0F, 1.0F, 5.0F, 2.0F)
			// core_gem
			.texOffs(127, 116).addBox(-2.5F, -2.0F, -10.6F, 5.0F, 5.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition neckLower = body.addOrReplaceChild("neck_lower", CubeListBuilder.create()
			// neck_lower_core
			.texOffs(176, 48).addBox(-3.5F, -3.5F, -8.25F, 7.0F, 7.0F, 9.0F)
			// neck_lower_ridge
			.texOffs(100, 106).addBox(-1.0F, -5.45F, -7.0F, 2.0F, 2.0F, 6.0F),
			PartPose.offsetAndRotation(0.0F, -3.0F, -9.0F, -0.5585F, 0.0F, 0.0F));
		PartDefinition neckUpper = neckLower.addOrReplaceChild("neck_upper", CubeListBuilder.create()
			// neck_upper_core
			.texOffs(209, 48).addBox(-3.0F, -3.0F, -8.25F, 6.0F, 6.0F, 9.0F)
			// neck_upper_ridge
			.texOffs(117, 106).addBox(-1.0F, -4.75F, -7.0F, 2.0F, 2.0F, 6.0F),
			PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, 0.2443F, 0.0F, 0.0F));
		PartDefinition head = neckUpper.addOrReplaceChild("head", CubeListBuilder.create()
			// skull
			.texOffs(161, 0).addBox(-4.5F, -4.0F, -10.25F, 9.0F, 8.0F, 11.0F)
			// snout
			.texOffs(184, 66).addBox(-3.0F, -1.25F, -17.8F, 6.0F, 5.0F, 8.0F)
			// nose_ridge
			.texOffs(121, 82).addBox(-1.0F, -2.05F, -17.0F, 2.0F, 1.0F, 9.0F)
			// brow_right
			.texOffs(47, 106).addBox(-5.2F, -4.7F, -9.75F, 3.0F, 2.0F, 7.0F)
			// brow_left
			.texOffs(68, 106).addBox(2.2F, -4.7F, -9.75F, 3.0F, 2.0F, 7.0F)
			// eye_right
			.texOffs(213, 116).addBox(-5.15F, -1.8F, -8.0F, 1.0F, 2.0F, 3.0F)
			// eye_left
			.texOffs(222, 116).addBox(4.15F, -1.8F, -8.0F, 1.0F, 2.0F, 3.0F)
			// crest
			.texOffs(50, 82).addBox(-1.0F, -7.05F, -8.25F, 2.0F, 3.0F, 8.0F)
			// cheek_right
			.texOffs(168, 106).addBox(-5.45F, 0.5F, -5.0F, 1.0F, 3.0F, 5.0F)
			// cheek_left
			.texOffs(181, 106).addBox(4.45F, 0.5F, -5.0F, 1.0F, 3.0F, 5.0F)
			// fang_right
			.texOffs(152, 124).addBox(-2.6F, 3.3F, -17.75F, 1.0F, 2.0F, 2.0F)
			// fang_left
			.texOffs(159, 124).addBox(1.6F, 3.3F, -17.75F, 1.0F, 2.0F, 2.0F)
			// tooth_right_1
			.texOffs(193, 130).addBox(-3.05F, 3.5F, -14.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.1F))
			// tooth_right_2
			.texOffs(198, 130).addBox(-3.05F, 3.5F, -11.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.1F))
			// tooth_left_1
			.texOffs(203, 130).addBox(2.05F, 3.5F, -14.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.1F))
			// tooth_left_2
			.texOffs(208, 130).addBox(2.05F, 3.5F, -11.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.1F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, 0.384F, 0.0F, 0.0F));
		head.addOrReplaceChild("jaw", CubeListBuilder.create()
			// jaw_core
			.texOffs(175, 28).addBox(-3.0F, 0.5F, -16.0F, 6.0F, 2.0F, 16.0F, new CubeDeformation(0.05F))
			// chin_spike
			.texOffs(231, 116).addBox(-0.5F, 2.45F, -14.0F, 1.0F, 2.0F, 3.0F),
			PartPose.offsetAndRotation(0.0F, 3.0F, -1.0F, 0.0F, 0.0F, 0.0F));
		head.addOrReplaceChild("horn_right", CubeListBuilder.create()
			// horn_right_base
			.texOffs(213, 66).addBox(-1.1F, -1.1F, -0.75F, 2.0F, 2.0F, 11.0F)
			// horn_right_tip
			.texOffs(134, 106).addBox(-0.6F, -0.6F, 9.9F, 1.0F, 1.0F, 7.0F),
			PartPose.offsetAndRotation(-3.0F, -3.5F, -1.0F, -0.4887F, -0.2094F, 0.0F));
		head.addOrReplaceChild("horn_left", CubeListBuilder.create()
			// horn_left_base
			.texOffs(0, 82).addBox(-0.9F, -1.1F, -0.75F, 2.0F, 2.0F, 11.0F)
			// horn_left_tip
			.texOffs(151, 106).addBox(-0.4F, -0.6F, 9.9F, 1.0F, 1.0F, 7.0F),
			PartPose.offsetAndRotation(3.0F, -3.5F, -1.0F, -0.4887F, 0.2094F, 0.0F));
		PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create()
			// tail1_core
			.texOffs(202, 0).addBox(-4.0F, -3.5F, -0.25F, 8.0F, 7.0F, 12.0F)
			// tail1_spike
			.texOffs(0, 116).addBox(-1.0F, -6.4F, 2.5F, 2.0F, 3.0F, 4.0F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 15.5F, -0.1047F, 0.0F, 0.0F));
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
			// tail2_core
			.texOffs(220, 28).addBox(-3.0F, -2.5F, -0.25F, 6.0F, 5.0F, 12.0F)
			// tail2_spike
			.texOffs(13, 116).addBox(-0.5F, -5.2F, 2.5F, 1.0F, 3.0F, 4.0F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, -0.0698F, 0.0F, 0.0F));
		PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
			// tail3_core
			.texOffs(46, 66).addBox(-2.0F, -1.5F, -0.25F, 4.0F, 3.0F, 11.0F)
			// tail3_spike
			.texOffs(140, 116).addBox(-0.5F, -3.5F, 2.5F, 1.0F, 2.0F, 4.0F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 11.0F, 0.0F, 0.0F, 0.0F));
		tail3.addOrReplaceChild("tail4", CubeListBuilder.create()
			// tail4_core
			.texOffs(27, 82).addBox(-1.0F, -1.0F, -0.25F, 2.0F, 2.0F, 9.0F)
			// tail_blade
			.texOffs(0, 28).addBox(-0.5F, -5.25F, 3.5F, 1.0F, 10.0F, 9.0F)
			// tail_blade_tip
			.texOffs(194, 106).addBox(-0.5F, -2.0F, 12.4F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0.05F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 10.0F, 0.0873F, 0.0F, 0.0F));
		PartDefinition legUpperRight = body.addOrReplaceChild("leg_upper_right", CubeListBuilder.create()
			// thigh_right
			.texOffs(0, 48).addBox(-2.8F, -2.0F, -3.5F, 5.0F, 10.0F, 7.0F)
			// knee_spike_right
			.texOffs(173, 116).addBox(-1.3F, 5.0F, 2.8F, 2.0F, 4.0F, 2.0F),
			PartPose.offsetAndRotation(-6.0F, 3.0F, 8.0F, 0.3491F, 0.0F, 0.0F));
		PartDefinition legLowerRight = legUpperRight.addOrReplaceChild("leg_lower_right", CubeListBuilder.create()
			// shin_right
			.texOffs(115, 66).addBox(-1.6F, 0.0F, -1.75F, 3.0F, 10.0F, 4.0F),
			PartPose.offsetAndRotation(0.0F, 7.0F, 1.0F, -0.6632F, 0.0F, 0.0F));
		legLowerRight.addOrReplaceChild("foot_right", CubeListBuilder.create()
			// foot_right_core
			.texOffs(71, 82).addBox(-2.6F, 1.2F, -5.0F, 5.0F, 3.0F, 7.0F)
			// toe1_right
			.texOffs(240, 116).addBox(1.2F, 2.2F, -7.95F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.05F))
			// toe2_right
			.texOffs(151, 116).addBox(-0.6F, 2.1F, -8.7F, 1.0F, 2.0F, 4.0F)
			// toe3_right
			.texOffs(0, 124).addBox(-2.4F, 2.2F, -7.95F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.05F))
			// heel_spur_right
			.texOffs(166, 124).addBox(-0.6F, 2.6F, 1.95F, 1.0F, 1.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.3142F, 0.0F, 0.0F));
		PartDefinition legUpperLeft = body.addOrReplaceChild("leg_upper_left", CubeListBuilder.create()
			// thigh_left
			.texOffs(25, 48).addBox(-2.2F, -2.0F, -3.5F, 5.0F, 10.0F, 7.0F)
			// knee_spike_left
			.texOffs(182, 116).addBox(-0.7F, 5.0F, 2.8F, 2.0F, 4.0F, 2.0F),
			PartPose.offsetAndRotation(6.0F, 3.0F, 8.0F, 0.3491F, 0.0F, 0.0F));
		PartDefinition legLowerLeft = legUpperLeft.addOrReplaceChild("leg_lower_left", CubeListBuilder.create()
			// shin_left
			.texOffs(130, 66).addBox(-1.4F, 0.0F, -1.75F, 3.0F, 10.0F, 4.0F),
			PartPose.offsetAndRotation(0.0F, 7.0F, 1.0F, -0.6632F, 0.0F, 0.0F));
		legLowerLeft.addOrReplaceChild("foot_left", CubeListBuilder.create()
			// foot_left_core
			.texOffs(96, 82).addBox(-2.4F, 1.2F, -5.0F, 5.0F, 3.0F, 7.0F)
			// toe1_left
			.texOffs(9, 124).addBox(-2.2F, 2.2F, -7.95F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.05F))
			// toe2_left
			.texOffs(162, 116).addBox(-0.4F, 2.1F, -8.7F, 1.0F, 2.0F, 4.0F)
			// toe3_left
			.texOffs(18, 124).addBox(1.4F, 2.2F, -7.95F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.05F))
			// heel_spur_left
			.texOffs(173, 124).addBox(-0.4F, 2.6F, 1.95F, 1.0F, 1.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.3142F, 0.0F, 0.0F));
		PartDefinition wingUpperRight = body.addOrReplaceChild("wing_upper_right", CubeListBuilder.create()
			// wing_arm_right
			.texOffs(49, 116).addBox(-16.1F, -1.4F, -1.5F, 16.0F, 3.0F, 3.0F)
			// wing_shard_right
			.texOffs(126, 124).addBox(-9.0F, -3.0F, -1.0F, 4.0F, 2.0F, 2.0F)
			// wing_membrane_upper_right
			.texOffs(50, 48).addBox(-16.0F, 0.3F, 1.2F, 15.0F, 0.0F, 16.0F),
			PartPose.offsetAndRotation(-7.0F, -4.0F, -5.0F, 0.0F, 0.0F, -0.2094F));
		PartDefinition wingLowerRight = wingUpperRight.addOrReplaceChild("wing_lower_right", CubeListBuilder.create()
			// wing_forearm_right
			.texOffs(36, 124).addBox(-20.1F, -0.9F, -1.0F, 20.0F, 2.0F, 2.0F)
			// wing_claw_right
			.texOffs(191, 116).addBox(-21.0F, -1.0F, -3.9F, 2.0F, 2.0F, 3.0F)
			// wing_membrane_lower_right
			.texOffs(21, 28).addBox(-20.0F, 0.2F, 0.9F, 20.0F, 0.0F, 18.0F, new CubeDeformation(0.05F)),
			PartPose.offsetAndRotation(-16.0F, 0.0F, 0.0F, 0.0F, 0.1396F, 0.1396F));
		wingLowerRight.addOrReplaceChild("finger1_right", CubeListBuilder.create()
			// finger1_spar_right
			.texOffs(180, 124).addBox(-21.0F, -0.3F, -0.5F, 21.0F, 1.0F, 1.0F)
			// finger1_membrane_right
			.texOffs(144, 82).addBox(-20.25F, 0.4F, 0.2F, 20.0F, 0.0F, 9.0F),
			PartPose.offsetAndRotation(-20.0F, 0.0F, 0.0F, 0.0F, -0.1396F, 0.0F));
		wingLowerRight.addOrReplaceChild("finger2_right", CubeListBuilder.create()
			// finger2_spar_right
			.texOffs(45, 130).addBox(-19.0F, -0.3F, -0.5F, 19.0F, 1.0F, 1.0F, new CubeDeformation(0.05F))
			// finger2_membrane_right
			.texOffs(59, 96).addBox(-18.25F, 0.3F, 0.2F, 18.0F, 0.0F, 9.0F),
			PartPose.offsetAndRotation(-20.0F, 0.0F, 0.0F, 0.0F, 0.4887F, 0.0F));
		wingLowerRight.addOrReplaceChild("finger3_right", CubeListBuilder.create()
			// finger3_spar_right
			.texOffs(127, 130).addBox(-15.0F, -0.3F, -0.5F, 15.0F, 1.0F, 1.0F, new CubeDeformation(0.1F))
			// finger3_membrane_right
			.texOffs(169, 96).addBox(-14.25F, 0.2F, 0.2F, 14.0F, 0.0F, 9.0F),
			PartPose.offsetAndRotation(-20.0F, 0.0F, 0.0F, 0.0F, 1.0821F, 0.0F));
		PartDefinition wingUpperLeft = body.addOrReplaceChild("wing_upper_left", CubeListBuilder.create()
			// wing_arm_left
			.texOffs(88, 116).addBox(0.1F, -1.4F, -1.5F, 16.0F, 3.0F, 3.0F)
			// wing_shard_left
			.texOffs(139, 124).addBox(5.0F, -3.0F, -1.0F, 4.0F, 2.0F, 2.0F)
			// wing_membrane_upper_left
			.texOffs(113, 48).addBox(1.0F, 0.3F, 1.2F, 15.0F, 0.0F, 16.0F),
			PartPose.offsetAndRotation(7.0F, -4.0F, -5.0F, 0.0F, 0.0F, 0.2094F));
		PartDefinition wingLowerLeft = wingUpperLeft.addOrReplaceChild("wing_lower_left", CubeListBuilder.create()
			// wing_forearm_left
			.texOffs(81, 124).addBox(0.1F, -0.9F, -1.0F, 20.0F, 2.0F, 2.0F)
			// wing_claw_left
			.texOffs(202, 116).addBox(19.0F, -1.0F, -3.9F, 2.0F, 2.0F, 3.0F)
			// wing_membrane_lower_left
			.texOffs(98, 28).addBox(0.0F, 0.2F, 0.9F, 20.0F, 0.0F, 18.0F, new CubeDeformation(0.05F)),
			PartPose.offsetAndRotation(16.0F, 0.0F, 0.0F, 0.0F, -0.1396F, -0.1396F));
		wingLowerLeft.addOrReplaceChild("finger1_left", CubeListBuilder.create()
			// finger1_spar_left
			.texOffs(0, 130).addBox(0.0F, -0.3F, -0.5F, 21.0F, 1.0F, 1.0F)
			// finger1_membrane_left
			.texOffs(0, 96).addBox(0.25F, 0.4F, 0.2F, 20.0F, 0.0F, 9.0F),
			PartPose.offsetAndRotation(20.0F, 0.0F, 0.0F, 0.0F, 0.1396F, 0.0F));
		wingLowerLeft.addOrReplaceChild("finger2_left", CubeListBuilder.create()
			// finger2_spar_left
			.texOffs(86, 130).addBox(0.0F, -0.3F, -0.5F, 19.0F, 1.0F, 1.0F, new CubeDeformation(0.05F))
			// finger2_membrane_left
			.texOffs(114, 96).addBox(0.25F, 0.3F, 0.2F, 18.0F, 0.0F, 9.0F),
			PartPose.offsetAndRotation(20.0F, 0.0F, 0.0F, 0.0F, -0.4887F, 0.0F));
		wingLowerLeft.addOrReplaceChild("finger3_left", CubeListBuilder.create()
			// finger3_spar_left
			.texOffs(160, 130).addBox(0.0F, -0.3F, -0.5F, 15.0F, 1.0F, 1.0F, new CubeDeformation(0.1F))
			// finger3_membrane_left
			.texOffs(0, 106).addBox(0.25F, 0.2F, 0.2F, 14.0F, 0.0F, 9.0F),
			PartPose.offsetAndRotation(20.0F, 0.0F, 0.0F, 0.0F, -1.0821F, 0.0F));
		return LayerDefinition.create(mesh, 256, 256);
		// END GENERATED LAYER
	}

	@Override
	public void setupAnim(final CrystalDrakeRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		float aloft = state.aloft;
		float swoop = state.swoop;
		float ground = 1.0F - aloft;

		// the wings: beating aloft (slower and shallower as it glides into a dive), folded along the back on the ground
		float beat = Mth.sin(age * 0.33F) * (0.62F - 0.4F * swoop) * aloft;
		float follow = Mth.sin(age * 0.33F - 0.9F) * (0.4F - 0.25F * swoop) * aloft;
		float sweep = swoop * 0.95F;
		this.wingUpperRight.zRot += beat + ground * 0.38F;
		this.wingUpperLeft.zRot -= beat + ground * 0.38F;
		this.wingUpperRight.yRot += sweep + ground * 1.35F;
		this.wingUpperLeft.yRot -= sweep + ground * 1.35F;
		this.wingLowerRight.zRot += follow;
		this.wingLowerLeft.zRot -= follow;
		this.wingLowerRight.yRot -= ground * 2.55F - swoop * 0.3F;
		this.wingLowerLeft.yRot += ground * 2.55F - swoop * 0.3F;
		for (int i = 0; i < 3; i++) {
			float spread = (i - 1) * 0.12F * aloft * (1.0F - swoop);
			this.fingersRight[i].yRot += ground * 2.1F - spread + swoop * 0.25F;
			this.fingersLeft[i].yRot -= ground * 2.1F - spread + swoop * 0.25F;
		}

		// the body rides its wingbeats aloft and pitches into a dive
		this.body.y += Mth.sin(age * 0.33F + 1.2F) * 0.9F * aloft;
		this.body.xRot += state.dive;

		// legs: tucked back aloft, walking on the ground
		float walk = Mth.cos(state.walkAnimationPos * 0.55F) * 0.7F * state.walkAnimationSpeed * ground;
		this.legUpperRight.xRot += 0.95F * aloft + walk;
		this.legUpperLeft.xRot += 0.95F * aloft - walk;
		this.legLowerRight.xRot += 0.5F * aloft - Math.max(0.0F, walk) * 0.6F;
		this.legLowerLeft.xRot += 0.5F * aloft - Math.max(0.0F, -walk) * 0.6F;

		// the neck and head follow its gaze; the jaw opens to bite
		float yaw = state.yRot * Mth.DEG_TO_RAD;
		float pitch = state.xRot * Mth.DEG_TO_RAD;
		this.neckLower.yRot += yaw * 0.3F;
		this.neckUpper.yRot += yaw * 0.3F;
		this.head.yRot += yaw * 0.4F;
		this.neckUpper.xRot += pitch * 0.4F;
		this.head.xRot += pitch * 0.6F;
		this.jaw.xRot += state.bite * 0.65F + Mth.sin(age * 0.07F) * 0.04F;

		// the tail streams and sways, more towards its tip
		for (int i = 0; i < this.tail.length; i++) {
			this.tail[i].yRot += Mth.sin(age * 0.11F - i * 0.55F) * (0.08F + 0.04F * i) * (1.0F - 0.5F * swoop);
			this.tail[i].xRot += (Mth.sin(age * 0.33F - 1.6F - i * 0.5F) * 0.06F - 0.05F) * aloft - state.dive * 0.15F;
		}
		this.move(state);
	}

	/** The crown elder's moves on the ground, over its standing pose. */
	private void move(final CrystalDrakeRenderState state) {
		float t = state.moveAge;
		switch (state.move) {
			case SWEEP_WINDUP -> {
				float p = ease(t / CrystalElder.SWEEP_WINDUP);
				this.body.yRot -= 0.7F * p;
				this.neckLower.xRot += 0.25F * p;
				this.neckLower.yRot += 0.35F * p;
				for (ModelPart segment : this.tail) {
					segment.yRot += 0.28F * p;
				}
				this.wingUpperRight.zRot -= 0.3F * p;
				this.wingUpperLeft.zRot += 0.3F * p;
				this.jaw.xRot += 0.35F * p;
			}
			case SWEEP -> {
				float e = ease(t / CrystalElder.SWEEP_TIME);
				this.body.yRot += -0.7F + e * (Mth.TWO_PI + 0.7F);
				for (ModelPart segment : this.tail) {
					segment.yRot -= 0.12F * (1.0F - e);
				}
				this.wingUpperRight.zRot -= 0.3F * (1.0F - e);
				this.wingUpperLeft.zRot += 0.3F * (1.0F - e);
			}
			case CHARGE_WINDUP -> {
				float p = ease(t / CrystalElder.CHARGE_WINDUP);
				this.body.xRot += 0.14F * p;
				this.body.y += 2.0F * p;
				this.neckLower.xRot += 0.5F * p;
				this.head.xRot += 0.15F * p;
				this.legUpperRight.xRot += Mth.sin(t * 0.9F) * 0.45F * p;
				this.wingUpperRight.zRot += 0.35F * p;
				this.wingUpperLeft.zRot -= 0.35F * p;
				this.jaw.xRot += 0.2F * p;
			}
			case CHARGE -> {
				float gallop = Mth.sin(t * 1.4F) * 0.9F;
				this.body.xRot += 0.16F;
				this.body.y += Math.abs(Mth.cos(t * 1.4F)) * -1.2F;
				this.neckLower.xRot += 0.55F;
				this.head.xRot -= 0.2F;
				this.legUpperRight.xRot += gallop;
				this.legUpperLeft.xRot -= gallop;
				this.wingUpperRight.yRot += 0.9F;
				this.wingUpperLeft.yRot -= 0.9F;
				this.jaw.xRot += 0.5F;
			}
			case STUNNED -> {
				float p = ease(t / 6.0F);
				float sway = Mth.sin(t * 0.3F) * 0.05F;
				this.body.zRot += (0.22F + sway) * p;
				this.body.y += 1.5F * p;
				this.neckLower.xRot += 0.8F * p;
				this.neckUpper.xRot += 0.3F * p;
				this.neckLower.yRot += sway * 2.0F;
				// wings slack, tips down on the ground
				this.wingUpperRight.zRot -= 0.75F * p;
				this.wingUpperLeft.zRot += 0.75F * p;
				this.wingUpperRight.yRot -= 0.5F * p;
				this.wingUpperLeft.yRot += 0.5F * p;
				this.jaw.xRot += 0.3F * p;
			}
			default -> {
			}
		}
	}

	private static float ease(final float x) {
		float c = Mth.clamp(x, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}
}
