package life.shizune.mistinn.client.shore;

import life.shizune.mistinn.Mistinn;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * 鹽屍的模型: a drowned man the salt has dried out. A shrunken skull with dark sockets, a hole for a nose and
 * a slack jaw hanging open, a few strands of hair left at the back; a narrow chest with the ribs and the
 * breastbone standing out, a sunken belly and the spine a ridge down the back; what is left of a shirt on
 * one shoulder and down the back, trousers torn off at the knee and held up with rope, bare bony shins and
 * long toes. White salt has grown into crystals on its crown, its right shoulder, its right forearm, its hip
 * and its knee. It keeps the humanoid bone names, so the husk's walk, the enemy motion and the fall still pose it.
 * The boxes between the BODY markers are generated together with the 128x128 texture layout.
 */
public class SaltCorpseModel extends ZombieModel<ZombieRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("salt_corpse"), "main");

	/** How far, in radians, the slack jaw stirs on its own, and how much wider it gapes at a hunter. */
	private static final float JAW_STIR = 0.09F;
	private static final float JAW_GAPE = 0.35F;

	/** The slack jaw; null on the armour layers, which have none. */
	private final @Nullable ModelPart jaw;

	public SaltCorpseModel(final ModelPart root) {
		super(root);
		this.jaw = this.head.hasChild("jaw") ? this.head.getChild("jaw") : null;
	}

	/** Its slack jaw hangs and stirs on its own, and gapes when it turns on a hunter. */
	@Override
	public void setupAnim(final ZombieRenderState state) {
		super.setupAnim(state);
		if (this.jaw == null) {
			return;
		}
		this.jaw.xRot += JAW_STIR * (0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.17F)) + (state.isAggressive ? JAW_GAPE : 0.0F);
	}

	public static LayerDefinition definition() {
		// BODY: generated
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-3.5F, -7.6F, -3.2F, 7.0F, 6.2F, 6.6F)
			.texOffs(5, 43).addBox(-3.6F, -6.0F, -3.6F, 7.2F, 1.0F, 0.6F)
			.texOffs(67, 37).addBox(-3.2F, -4.0F, -3.5F, 1.6F, 1.4F, 0.5F)
			.texOffs(74, 37).addBox(1.6F, -4.0F, -3.5F, 1.6F, 1.4F, 0.5F)
			.texOffs(24, 43).addBox(-2.4F, -5.1F, -3.4F, 1.8F, 0.9F, 0.3F)
			.texOffs(31, 43).addBox(0.6F, -5.1F, -3.4F, 1.8F, 0.9F, 0.3F)
			.texOffs(81, 37).addBox(-0.5F, -4.4F, -3.45F, 1.0F, 1.2F, 0.35F)
			.texOffs(38, 43).addBox(-2.0F, -2.2F, -3.5F, 4.0F, 0.95F, 0.45F), PartPose.ZERO);
		PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create()
			.texOffs(0, 24).addBox(-1.0F, -7.9F, -1.0F, 4.0F, 0.5F, 4.2F), PartPose.ZERO);
		hat.addOrReplaceChild("salt_crown_2", CubeListBuilder.create()
			.texOffs(86, 37).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 1.1F, 1.0F), PartPose.offsetAndRotation(2.1F, -6.8F, 1.1F, -0.2618F, 0.0F, -0.2618F));
		hat.addOrReplaceChild("salt_crown_1", CubeListBuilder.create()
			.texOffs(15, 37).addBox(-0.6F, -1.4F, -0.6F, 1.2F, 1.5F, 1.2F), PartPose.offsetAndRotation(-2.0F, -6.8F, -0.8F, 0.0F, 0.0F, 0.3491F));
		hat.addOrReplaceChild("hair_strand_3", CubeListBuilder.create()
			.texOffs(121, 15).addBox(-0.4F, 0.0F, -0.25F, 0.8F, 4.2F, 0.5F), PartPose.offsetAndRotation(-2.8F, -7.2F, 3.5F, -0.1745F, 0.0F, 0.0F));
		hat.addOrReplaceChild("hair_strand_2", CubeListBuilder.create()
			.texOffs(108, 0).addBox(-0.4F, 0.0F, -0.2F, 0.8F, 6.3F, 0.5F), PartPose.offsetAndRotation(-1.0F, -7.3F, 3.5F, -0.1047F, 0.0F, 0.0F));
		hat.addOrReplaceChild("hair_strand_1", CubeListBuilder.create()
			.texOffs(42, 15).addBox(-0.4F, 0.0F, -0.25F, 0.8F, 5.2F, 0.5F), PartPose.offsetAndRotation(2.8F, -7.2F, 3.5F, -0.1396F, 0.0F, 0.0F));
		head.addOrReplaceChild("jaw", CubeListBuilder.create()
			.texOffs(19, 15).addBox(-2.8F, 0.0F, -2.7F, 5.6F, 1.6F, 4.7F)
			.texOffs(49, 43).addBox(-1.8F, -0.5F, -2.75F, 3.6F, 0.6F, 0.4F), PartPose.offsetAndRotation(0.0F, -1.6F, -0.8F, 0.2443F, 0.0F, 0.0F));
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(109, 24).addBox(-1.2F, -1.6F, -1.0F, 2.4F, 2.0F, 2.2F)
			.texOffs(36, 0).addBox(-3.6F, 0.0F, -1.8F, 7.2F, 7.0F, 3.6F)
			.texOffs(60, 43).addBox(-3.4F, 5.4F, -2.15F, 6.8F, 0.6F, 0.45F)
			.texOffs(77, 43).addBox(-3.4F, 4.0F, -2.15F, 6.8F, 0.6F, 0.45F)
			.texOffs(94, 43).addBox(-3.4F, 2.6F, -2.15F, 6.8F, 0.6F, 0.45F)
			.texOffs(111, 43).addBox(-3.4F, 1.2F, -2.15F, 6.8F, 0.6F, 0.45F)
			.texOffs(47, 15).addBox(-0.5F, 0.4F, -2.3F, 1.0F, 6.0F, 0.55F)
			.texOffs(0, 15).addBox(-2.8F, 6.8F, -1.2F, 5.6F, 4.6F, 2.6F)
			.texOffs(29, 0).addBox(-0.6F, 0.4F, 1.3F, 1.2F, 10.8F, 0.8F)
			.texOffs(19, 24).addBox(-3.8F, 10.4F, -1.6F, 7.6F, 2.0F, 3.2F)
			.texOffs(0, 31).addBox(-3.9F, 10.6F, -1.75F, 7.8F, 0.7F, 3.5F, new CubeDeformation(0.05F))
			.texOffs(95, 0).addBox(-1.0F, 0.2F, 1.85F, 4.8F, 7.8F, 0.5F)
			.texOffs(52, 15).addBox(-4.2F, -0.3F, -2.0F, 2.2F, 2.8F, 4.0F, new CubeDeformation(0.1F)), PartPose.ZERO);
		body.addOrReplaceChild("salt_hip", CubeListBuilder.create()
			.texOffs(33, 37).addBox(-0.5F, -1.8F, -0.7F, 1.0F, 1.8F, 1.4F), PartPose.offsetAndRotation(3.7F, 11.2F, -0.3F, 0.0F, 0.0F, -0.3142F));
		body.addOrReplaceChild("salt_shoulder_2", CubeListBuilder.create()
			.texOffs(91, 37).addBox(-0.4F, -1.6F, -0.4F, 0.8F, 1.6F, 0.8F), PartPose.offsetAndRotation(-3.6F, -0.6F, 0.4F, -0.1745F, 0.0F, 0.4363F));
		body.addOrReplaceChild("salt_shoulder_1", CubeListBuilder.create()
			.texOffs(24, 37).addBox(-0.9F, -0.7F, -0.9F, 1.8F, 1.4F, 1.8F), PartPose.offsetAndRotation(-3.5F, -0.3F, -0.3F, 0.0F, -0.3491F, 0.2094F));
		body.addOrReplaceChild("shirt_rag_front", CubeListBuilder.create()
			.texOffs(44, 24).addBox(-1.1F, 0.0F, -0.2F, 2.2F, 5.0F, 0.35F), PartPose.offsetAndRotation(2.7F, 3.5F, -2.1F, 0.0F, 0.0F, -0.1047F));
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(53, 24).addBox(-2.0F, -2.2F, -1.4F, 3.6F, 2.6F, 2.8F)
			.texOffs(67, 15).addBox(-1.6F, 0.0F, -1.0F, 1.8F, 5.0F, 2.0F)
			.texOffs(83, 24).addBox(-2.1F, -0.4F, -1.5F, 2.8F, 2.9F, 3.0F)
			.texOffs(25, 31).addBox(-1.8F, 4.6F, -1.2F, 2.2F, 1.4F, 2.4F)
			.texOffs(85, 15).addBox(-1.5F, 5.8F, -0.9F, 1.6F, 4.2F, 1.8F)
			.texOffs(51, 31).addBox(-1.8F, 9.8F, -1.1F, 2.2F, 1.8F, 2.2F)
			.texOffs(40, 37).addBox(-0.2F, 11.4F, -1.0F, 0.5F, 2.8F, 0.5F)
			.texOffs(77, 31).addBox(-0.95F, 11.4F, -1.0F, 0.5F, 3.3F, 0.5F)
			.texOffs(50, 37).addBox(-1.7F, 11.4F, -1.0F, 0.5F, 2.8F, 0.5F), PartPose.offset(-5.0F, 2.0F, 0.0F));
		rightArm.addOrReplaceChild("salt_forearm", CubeListBuilder.create()
			.texOffs(60, 37).addBox(-0.5F, -0.8F, -0.6F, 1.0F, 1.6F, 1.2F), PartPose.offsetAndRotation(-1.7F, 8.2F, 0.0F, 0.0F, 0.0F, 0.2618F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create()
			.texOffs(68, 24).addBox(-1.6F, -2.2F, -1.4F, 3.6F, 2.6F, 2.8F)
			.texOffs(76, 15).addBox(-0.2F, 0.0F, -1.0F, 1.8F, 5.0F, 2.0F)
			.texOffs(96, 24).addBox(-0.7F, -0.4F, -1.5F, 2.8F, 2.9F, 3.0F)
			.texOffs(38, 31).addBox(-0.4F, 4.6F, -1.2F, 2.2F, 1.4F, 2.4F)
			.texOffs(94, 15).addBox(-0.1F, 5.8F, -0.9F, 1.6F, 4.2F, 1.8F)
			.texOffs(64, 31).addBox(-0.4F, 9.8F, -1.1F, 2.2F, 1.8F, 2.2F)
			.texOffs(45, 37).addBox(-0.3F, 11.4F, -1.0F, 0.5F, 2.8F, 0.5F)
			.texOffs(82, 31).addBox(0.45F, 11.4F, -1.0F, 0.5F, 3.3F, 0.5F)
			.texOffs(55, 37).addBox(1.2F, 11.4F, -1.0F, 0.5F, 2.8F, 0.5F), PartPose.offset(5.0F, 2.0F, 0.0F));
		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
			.texOffs(61, 0).addBox(-1.7F, -0.2F, -1.65F, 3.4F, 5.8F, 3.3F)
			.texOffs(96, 37).addBox(-0.1F, 5.4F, -1.75F, 1.6F, 1.4F, 0.4F)
			.texOffs(0, 47).addBox(-1.5F, 5.4F, 1.35F, 1.4F, 1.0F, 0.4F)
			.texOffs(87, 31).addBox(-1.1F, 5.2F, -1.3F, 2.2F, 1.4F, 2.4F)
			.texOffs(103, 15).addBox(-0.8F, 6.4F, -0.9F, 1.6F, 4.4F, 1.8F)
			.texOffs(110, 37).addBox(-1.0F, 10.2F, -1.0F, 2.0F, 1.0F, 2.0F)
			.texOffs(113, 31).addBox(-1.1F, 11.0F, -3.0F, 2.2F, 1.0F, 3.8F)
			.texOffs(14, 47).addBox(0.4F, 11.4F, -3.6F, 0.6F, 0.55F, 0.7F)
			.texOffs(24, 47).addBox(-0.3F, 11.4F, -3.7F, 0.6F, 0.55F, 0.8F)
			.texOffs(34, 47).addBox(-1.0F, 11.4F, -3.5F, 0.6F, 0.55F, 0.6F), PartPose.offset(-1.9F, 12.0F, 0.0F));
		rightLeg.addOrReplaceChild("salt_knee", CubeListBuilder.create()
			.texOffs(0, 43).addBox(-0.5F, -0.6F, -0.5F, 1.0F, 1.2F, 1.0F), PartPose.offsetAndRotation(-1.0F, 5.4F, -1.2F, 0.0F, 0.0F, 0.2094F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create()
			.texOffs(78, 0).addBox(-1.7F, -0.2F, -1.65F, 3.4F, 5.8F, 3.3F)
			.texOffs(103, 37).addBox(-1.5F, 5.4F, -1.75F, 1.6F, 1.4F, 0.4F)
			.texOffs(7, 47).addBox(0.1F, 5.4F, 1.35F, 1.4F, 1.0F, 0.4F)
			.texOffs(100, 31).addBox(-1.1F, 5.2F, -1.3F, 2.2F, 1.4F, 2.4F)
			.texOffs(112, 15).addBox(-0.8F, 6.4F, -0.9F, 1.6F, 4.4F, 1.8F)
			.texOffs(119, 37).addBox(-1.0F, 10.2F, -1.0F, 2.0F, 1.0F, 2.0F)
			.texOffs(0, 37).addBox(-1.1F, 11.0F, -3.0F, 2.2F, 1.0F, 3.8F)
			.texOffs(19, 47).addBox(-1.0F, 11.4F, -3.6F, 0.6F, 0.55F, 0.7F)
			.texOffs(29, 47).addBox(-0.3F, 11.4F, -3.7F, 0.6F, 0.55F, 0.8F)
			.texOffs(39, 47).addBox(0.4F, 11.4F, -3.5F, 0.6F, 0.55F, 0.6F), PartPose.offset(1.9F, 12.0F, 0.0F));
		return LayerDefinition.create(mesh, 128, 128);
		// BODY END
	}
}
