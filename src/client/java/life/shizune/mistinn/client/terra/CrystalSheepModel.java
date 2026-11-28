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
 * 晶羊的模型: a stocky sheep, a bed of fleece on its back with crystal clusters standing out of it (gone when it is shorn),
 * a blunt head with two curled horns. The boxes between the GENERATED markers come from the crystal sheep's model spec
 * together with its 128x128 texture layout; do not edit them by hand. It walks with its legs in diagonal pairs; angry,
 * it carries its head low to ram.
 */
public class CrystalSheepModel extends EntityModel<CrystalSheepRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("crystal_sheep"), "main");

	private final ModelPart head;
	private final ModelPart crystals;
	private final ModelPart frontRight;
	private final ModelPart frontLeft;
	private final ModelPart hindRight;
	private final ModelPart hindLeft;

	public CrystalSheepModel(final ModelPart root) {
		super(root);
		ModelPart body = root.getChild("body");
		this.head = body.getChild("head");
		this.crystals = body.getChild("crystals");
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
			.texOffs(0, 0).addBox(-4.0F, -3.0F, -7.0F, 8.0F, 7.0F, 15.0F)
			// fleece
			.texOffs(47, 0).addBox(-4.5F, -4.0F, -6.0F, 9.0F, 3.0F, 13.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		body.addOrReplaceChild("head", CubeListBuilder.create()
			// skull
			.texOffs(92, 0).addBox(-3.0F, -3.0F, -6.0F, 6.0F, 6.0F, 7.0F)
			// horn_right
			.texOffs(70, 23).addBox(-5.0F, -3.0F, -2.0F, 2.0F, 3.0F, 3.0F)
			// horn_left
			.texOffs(81, 23).addBox(3.0F, -3.0F, -2.0F, 2.0F, 3.0F, 3.0F),
			PartPose.offsetAndRotation(0.0F, -3.0F, -7.0F, 0.0F, 0.0F, 0.0F));
		body.addOrReplaceChild("crystals", CubeListBuilder.create()
			// cluster_a
			.texOffs(92, 23).addBox(1.0F, -4.0F, -4.0F, 2.0F, 4.0F, 2.0F)
			// cluster_b
			.texOffs(61, 23).addBox(-3.0F, -5.0F, -3.0F, 2.0F, 5.0F, 2.0F)
			// cluster_c
			.texOffs(52, 23).addBox(-1.0F, -6.0F, 1.0F, 2.0F, 6.0F, 2.0F)
			// cluster_d
			.texOffs(110, 23).addBox(1.0F, -3.0F, 4.0F, 2.0F, 3.0F, 2.0F)
			// cluster_e
			.texOffs(101, 23).addBox(-3.0F, -4.0F, 4.0F, 2.0F, 4.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		root.addOrReplaceChild("leg_front_right", CubeListBuilder.create()
			// leg_front_right_core
			.texOffs(0, 23).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F),
			PartPose.offsetAndRotation(-2.5F, 16.0F, -4.5F, 0.0F, 0.0F, 0.0F));
		root.addOrReplaceChild("leg_front_left", CubeListBuilder.create()
			// leg_front_left_core
			.texOffs(13, 23).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F),
			PartPose.offsetAndRotation(2.5F, 16.0F, -4.5F, 0.0F, 0.0F, 0.0F));
		root.addOrReplaceChild("leg_hind_right", CubeListBuilder.create()
			// leg_hind_right_core
			.texOffs(26, 23).addBox(-1.5F, -1.0F, -1.0F, 3.0F, 9.0F, 3.0F),
			PartPose.offsetAndRotation(-2.5F, 16.0F, 5.0F, 0.0F, 0.0F, 0.0F));
		root.addOrReplaceChild("leg_hind_left", CubeListBuilder.create()
			// leg_hind_left_core
			.texOffs(39, 23).addBox(-1.5F, -1.0F, -1.0F, 3.0F, 9.0F, 3.0F),
			PartPose.offsetAndRotation(2.5F, 16.0F, 5.0F, 0.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 128, 128);
		// END GENERATED LAYER
	}

	@Override
	public void setupAnim(final CrystalSheepRenderState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos * 0.9F;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float step = Mth.cos(pos) * 0.8F * speed;
		this.frontRight.xRot += step;
		this.hindLeft.xRot += step;
		this.frontLeft.xRot -= step;
		this.hindRight.xRot -= step;
		this.crystals.visible = !state.shorn;
		this.head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.6F;
		this.head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F + (state.angry ? 0.45F : 0.0F);
	}
}
