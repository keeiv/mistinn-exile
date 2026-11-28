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
 * 陸鯊的模型: a thick body tapering to a two-part tail with a tall fin, a broad head with a heavy jaw, pectoral fins, and
 * a dorsal fin of grown crystal. The boxes between the GENERATED markers come from the land shark's model spec together
 * with its 64x64 texture layout; do not edit them by hand. It swims with its tail sweeping side to side; out of the
 * ground in a leap, its jaw gapes and its body arches.
 */
public class LandSharkModel extends EntityModel<LandSharkRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("land_shark"), "main");

	private final ModelPart body;
	private final ModelPart jaw;
	private final ModelPart tail1;
	private final ModelPart tail2;

	public LandSharkModel(final ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.jaw = this.body.getChild("head").getChild("jaw");
		this.tail1 = this.body.getChild("tail1");
		this.tail2 = this.tail1.getChild("tail2");
	}

	public static LayerDefinition createBodyLayer() {
		// BEGIN GENERATED LAYER
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			// torso
			.texOffs(0, 0).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 14.0F),
			PartPose.offsetAndRotation(0.0F, 18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
			// skull
			.texOffs(0, 23).addBox(-3.5F, -3.0F, -7.0F, 7.0F, 6.0F, 7.0F)
			// snout
			.texOffs(0, 50).addBox(-2.5F, -2.0F, -10.0F, 5.0F, 4.0F, 3.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, -8.0F, 0.0F, 0.0F, 0.0F));
		head.addOrReplaceChild("jaw", CubeListBuilder.create()
			// jaw_core
			.texOffs(15, 37).addBox(-3.0F, 1.0F, -8.0F, 6.0F, 2.0F, 8.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.0F, 2.0F, -1.0F, 0.0F, 0.0F, 0.0F));
		body.addOrReplaceChild("dorsal", CubeListBuilder.create()
			// dorsal_core
			.texOffs(0, 37).addBox(-1.0F, -7.0F, -2.0F, 2.0F, 7.0F, 5.0F),
			PartPose.offsetAndRotation(0.0F, -4.0F, -2.0F, -0.384F, 0.0F, 0.0F));
		body.addOrReplaceChild("fin_right", CubeListBuilder.create()
			// fin_right_core
			.texOffs(17, 50).addBox(-5.0F, 0.0F, -2.0F, 5.0F, 1.0F, 4.0F),
			PartPose.offsetAndRotation(-4.0F, 2.0F, -4.0F, 0.0F, 0.0F, -0.4363F));
		body.addOrReplaceChild("fin_left", CubeListBuilder.create()
			// fin_left_core
			.texOffs(36, 50).addBox(0.0F, 0.0F, -2.0F, 5.0F, 1.0F, 4.0F),
			PartPose.offsetAndRotation(4.0F, 2.0F, -4.0F, 0.0F, 0.0F, 0.4363F));
		PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create()
			// tail1_core
			.texOffs(29, 23).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 7.0F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 6.0F, 0.0F, 0.0F, 0.0F));
		tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
			// tail2_core
			.texOffs(44, 37).addBox(-1.5F, -2.0F, 0.0F, 3.0F, 4.0F, 6.0F)
			// tail_fin
			.texOffs(45, 0).addBox(-0.5F, -6.0F, 5.0F, 1.0F, 11.0F, 4.0F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
		// END GENERATED LAYER
	}

	@Override
	public void setupAnim(final LandSharkRenderState state) {
		super.setupAnim(state);
		float swim = state.ageInTicks * (0.25F + Math.min(1.0F, state.walkAnimationSpeed) * 0.35F);
		this.tail1.yRot += Mth.sin(swim) * 0.25F;
		this.tail2.yRot += Mth.sin(swim - 0.9F) * 0.35F;
		this.body.yRot += Mth.sin(swim + 1.4F) * 0.05F;
		// the leap: the jaw gapes, the body arches
		this.jaw.xRot += state.out * 0.7F;
		this.body.xRot += -state.out * 0.25F;
		this.tail1.xRot += state.out * 0.2F;
	}
}
