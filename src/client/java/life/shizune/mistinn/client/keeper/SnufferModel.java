package life.shizune.mistinn.client.keeper;

import life.shizune.mistinn.Mistinn;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 熄燈人的模型: a tall, thin keeper whose head is an iron lantern. The lantern (base ring, open
 * cage, stepped cap and hanging ring) sits where a head would, with the flame inside its cage;
 * a sailcloth mantle covers the shoulders, a long sea-coat hangs past the knees, and the arms are
 * longer than a man's. The flame itself is lit by {@link SnufferRenderer}'s glow layer.
 * Texture layout is the vanilla box layout on a 64x64 sheet.
 */
public class SnufferModel extends HumanoidModel<SnufferRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("snuffer"), "main");

	public SnufferModel(final ModelPart root) {
		super(root);
	}

	public static LayerDefinition definition() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// 提燈頭: the lantern stands on the neck, its ring 13 pixels up
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-3.5F, -2.0F, -3.5F, 7, 2, 7)
			.texOffs(0, 0).addBox(-3.0F, -8.0F, -3.0F, 6, 6, 6)
			.texOffs(0, 12).addBox(-2.5F, -9.0F, -2.5F, 5, 1, 5)
			.texOffs(52, 0).addBox(-1.5F, -10.0F, -1.5F, 3, 1, 3)
			.texOffs(52, 4).addBox(-2.0F, -13.0F, -0.5F, 4, 3, 1)
			.texOffs(20, 12).addBox(-1.0F, -6.0F, -1.0F, 2, 4, 2), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		// 外套: torso, the shoulder mantle, the coat skirt, and the candle snuffer at the belt
		root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(0, 18).addBox(-3.5F, 0.0F, -2.0F, 7, 12, 4)
			.texOffs(0, 49).addBox(-6.5F, -1.0F, -2.5F, 13, 6, 5)
			.texOffs(22, 20).addBox(-4.0F, 10.0F, -2.5F, 8, 9, 5)
			.texOffs(28, 12).addBox(1.5F, 10.5F, -3.5F, 2, 3, 2), PartPose.ZERO);
		// 長臂: thin and two pixels longer than a man's
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(48, 20).addBox(-2.0F, -2.0F, -1.5F, 3, 14, 3), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(48, 20).mirror().addBox(-1.0F, -2.0F, -1.5F, 3, 14, 3), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 34).addBox(-1.5F, 0.0F, -1.5F, 3, 12, 3), PartPose.offset(-2.0F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 34).mirror().addBox(-1.5F, 0.0F, -1.5F, 3, 12, 3), PartPose.offset(2.0F, 12.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(final SnufferRenderState state) {
		super.setupAnim(state);
		// the lantern hangs a little forward and sways, like a lamp on a hook
		this.head.xRot += 0.08F;
		this.head.zRot += Mth.sin(state.ageInTicks * 0.07F) * 0.05F;
	}
}
