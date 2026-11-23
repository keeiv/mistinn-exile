package life.shizune.mistinn.client.keeper;

import life.shizune.mistinn.Mistinn;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 鏽輪的模型: an iron-shod brine wheel some three blocks high, two rims with plates across the face and wooden buckets
 * between, spokes to a heavy hub; a yoke from the axle ends back to a crossbar; and bent over the bar, wrists chained to
 * it, a man whose legs drag behind. The wheel turns as far as it has rolled; the man jerks as it goes, and once he has
 * torn his left hand free it swings loose. The boxes are generated together with the 128x64 texture layout; boxes that
 * look alike share one area of it.
 */
public class RustWheelModel extends EntityModel<RustWheelRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("rust_wheel"), "main");
	/** The rim segments that lose their plates when the wheel breaks. */
	private static final int[] SHED = {0, 4, 10};

	private final ModelPart wheel;
	private final ModelPart body;
	private final ModelPart leftArm;
	private final ModelPart rightArm;
	private final ModelPart leftChain;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;
	private final ModelPart[] shed = new ModelPart[SHED.length];

	public RustWheelModel(final ModelPart root) {
		super(root);
		this.wheel = root.getChild("wheel");
		ModelPart man = root.getChild("man");
		this.body = man.getChild("body");
		this.leftArm = this.body.getChild("left_arm");
		this.rightArm = this.body.getChild("right_arm");
		this.leftChain = this.leftArm.getChild("left_chain");
		this.leftLeg = man.getChild("left_leg");
		this.rightLeg = man.getChild("right_leg");
		for (int i = 0; i < SHED.length; i++) {
			this.shed[i] = this.wheel.getChild("segment_" + SHED[i]).getChild("plate");
		}
	}

	public static LayerDefinition definition() {
		// BODY: generated
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// the wheel: it turns about the axle, side to side through the hub
		PartDefinition wheel = root.addOrReplaceChild("wheel", CubeListBuilder.create()
			.texOffs(40, 20).addBox(-7.0F, -3.0F, -3.0F, 14.0F, 6.0F, 6.0F)
			.texOffs(0, 0).addBox(-6.0F, -5.0F, -5.0F, 2.0F, 10.0F, 10.0F)
			.texOffs(0, 0).addBox(4.0F, -5.0F, -5.0F, 2.0F, 10.0F, 10.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
		// rim segment 0: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment0 = wheel.addOrReplaceChild("segment_0", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(0.0000F, 0.0F, 0.0F));
		segment0.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(32, 34).addBox(-6.0F, -24.0F, -4.0F, 12.0F, 1.0F, 8.0F), PartPose.ZERO);
		// rim segment 1: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment1 = wheel.addOrReplaceChild("segment_1", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(0.3927F, 0.0F, 0.0F));
		segment1.addOrReplaceChild("bucket", CubeListBuilder.create().texOffs(0, 34).addBox(-5.0F, -20.0F, -3.0F, 10.0F, 4.0F, 6.0F), PartPose.ZERO);
		// rim segment 2: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment2 = wheel.addOrReplaceChild("segment_2", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(0.7854F, 0.0F, 0.0F));
		segment2.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(32, 34).addBox(-6.0F, -24.0F, -4.0F, 12.0F, 1.0F, 8.0F), PartPose.ZERO);
		// rim segment 3: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment3 = wheel.addOrReplaceChild("segment_3", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(1.1781F, 0.0F, 0.0F));
		segment3.addOrReplaceChild("bucket", CubeListBuilder.create().texOffs(0, 34).addBox(-5.0F, -20.0F, -3.0F, 10.0F, 4.0F, 6.0F), PartPose.ZERO);
		// rim segment 4: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment4 = wheel.addOrReplaceChild("segment_4", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(1.5708F, 0.0F, 0.0F));
		segment4.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(32, 34).addBox(-6.0F, -24.0F, -4.0F, 12.0F, 1.0F, 8.0F), PartPose.ZERO);
		// rim segment 5: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment5 = wheel.addOrReplaceChild("segment_5", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(1.9635F, 0.0F, 0.0F));
		segment5.addOrReplaceChild("bucket", CubeListBuilder.create().texOffs(0, 34).addBox(-5.0F, -20.0F, -3.0F, 10.0F, 4.0F, 6.0F), PartPose.ZERO);
		// rim segment 6: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment6 = wheel.addOrReplaceChild("segment_6", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(2.3562F, 0.0F, 0.0F));
		segment6.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(32, 34).addBox(-6.0F, -24.0F, -4.0F, 12.0F, 1.0F, 8.0F), PartPose.ZERO);
		// rim segment 7: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment7 = wheel.addOrReplaceChild("segment_7", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(2.7489F, 0.0F, 0.0F));
		segment7.addOrReplaceChild("bucket", CubeListBuilder.create().texOffs(0, 34).addBox(-5.0F, -20.0F, -3.0F, 10.0F, 4.0F, 6.0F), PartPose.ZERO);
		// rim segment 8: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment8 = wheel.addOrReplaceChild("segment_8", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(3.1416F, 0.0F, 0.0F));
		segment8.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(32, 34).addBox(-6.0F, -24.0F, -4.0F, 12.0F, 1.0F, 8.0F), PartPose.ZERO);
		// rim segment 9: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment9 = wheel.addOrReplaceChild("segment_9", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(3.5343F, 0.0F, 0.0F));
		segment9.addOrReplaceChild("bucket", CubeListBuilder.create().texOffs(0, 34).addBox(-5.0F, -20.0F, -3.0F, 10.0F, 4.0F, 6.0F), PartPose.ZERO);
		// rim segment 10: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment10 = wheel.addOrReplaceChild("segment_10", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(3.9270F, 0.0F, 0.0F));
		segment10.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(32, 34).addBox(-6.0F, -24.0F, -4.0F, 12.0F, 1.0F, 8.0F), PartPose.ZERO);
		// rim segment 11: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment11 = wheel.addOrReplaceChild("segment_11", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(4.3197F, 0.0F, 0.0F));
		segment11.addOrReplaceChild("bucket", CubeListBuilder.create().texOffs(0, 34).addBox(-5.0F, -20.0F, -3.0F, 10.0F, 4.0F, 6.0F), PartPose.ZERO);
		// rim segment 12: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment12 = wheel.addOrReplaceChild("segment_12", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(4.7124F, 0.0F, 0.0F));
		segment12.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(32, 34).addBox(-6.0F, -24.0F, -4.0F, 12.0F, 1.0F, 8.0F), PartPose.ZERO);
		// rim segment 13: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment13 = wheel.addOrReplaceChild("segment_13", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(5.1051F, 0.0F, 0.0F));
		segment13.addOrReplaceChild("bucket", CubeListBuilder.create().texOffs(0, 34).addBox(-5.0F, -20.0F, -3.0F, 10.0F, 4.0F, 6.0F), PartPose.ZERO);
		// rim segment 14: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment14 = wheel.addOrReplaceChild("segment_14", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(5.4978F, 0.0F, 0.0F));
		segment14.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(32, 34).addBox(-6.0F, -24.0F, -4.0F, 12.0F, 1.0F, 8.0F), PartPose.ZERO);
		// rim segment 15: two iron rims, and on every other one a plate across the face, on the rest a bucket between them
		PartDefinition segment15 = wheel.addOrReplaceChild("segment_15", CubeListBuilder.create()
			.texOffs(80, 20).addBox(-7.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F)
			.texOffs(80, 20).addBox(5.0F, -23.0F, -4.5F, 2.0F, 3.0F, 9.0F), PartPose.rotation(5.8905F, 0.0F, 0.0F));
		segment15.addOrReplaceChild("bucket", CubeListBuilder.create().texOffs(0, 34).addBox(-5.0F, -20.0F, -3.0F, 10.0F, 4.0F, 6.0F), PartPose.ZERO);
		// spoke 0, a pair, hub to rim
		wheel.addOrReplaceChild("spoke_0", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-6.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F)
			.texOffs(24, 0).addBox(5.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F), PartPose.rotation(0.3927F, 0.0F, 0.0F));
		// spoke 1, a pair, hub to rim
		wheel.addOrReplaceChild("spoke_1", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-6.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F)
			.texOffs(24, 0).addBox(5.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F), PartPose.rotation(1.1781F, 0.0F, 0.0F));
		// spoke 2, a pair, hub to rim
		wheel.addOrReplaceChild("spoke_2", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-6.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F)
			.texOffs(24, 0).addBox(5.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F), PartPose.rotation(1.9635F, 0.0F, 0.0F));
		// spoke 3, a pair, hub to rim
		wheel.addOrReplaceChild("spoke_3", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-6.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F)
			.texOffs(24, 0).addBox(5.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F), PartPose.rotation(2.7489F, 0.0F, 0.0F));
		// spoke 4, a pair, hub to rim
		wheel.addOrReplaceChild("spoke_4", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-6.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F)
			.texOffs(24, 0).addBox(5.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F), PartPose.rotation(3.5343F, 0.0F, 0.0F));
		// spoke 5, a pair, hub to rim
		wheel.addOrReplaceChild("spoke_5", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-6.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F)
			.texOffs(24, 0).addBox(5.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F), PartPose.rotation(4.3197F, 0.0F, 0.0F));
		// spoke 6, a pair, hub to rim
		wheel.addOrReplaceChild("spoke_6", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-6.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F)
			.texOffs(24, 0).addBox(5.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F), PartPose.rotation(5.1051F, 0.0F, 0.0F));
		// spoke 7, a pair, hub to rim
		wheel.addOrReplaceChild("spoke_7", CubeListBuilder.create()
			.texOffs(24, 0).addBox(-6.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F)
			.texOffs(24, 0).addBox(5.5F, -20.0F, -1.0F, 1.0F, 17.0F, 2.0F), PartPose.rotation(5.8905F, 0.0F, 0.0F));
		// the yoke: two arms back from the axle ends, past the rim, to a crossbar; it does not turn
		root.addOrReplaceChild("yoke", CubeListBuilder.create()
			.texOffs(102, 20).addBox(-9.0F, -3.0F, -3.0F, 2.0F, 6.0F, 6.0F)
			.texOffs(102, 20).addBox(7.0F, -3.0F, -3.0F, 2.0F, 6.0F, 6.0F)
			.texOffs(30, 0).addBox(-9.0F, -1.5F, 0.0F, 2.0F, 3.0F, 16.0F)
			.texOffs(30, 0).addBox(-9.0F, -1.5F, 14.0F, 2.0F, 3.0F, 16.0F)
			.texOffs(30, 0).addBox(7.0F, -1.5F, 0.0F, 2.0F, 3.0F, 16.0F)
			.texOffs(30, 0).addBox(7.0F, -1.5F, 14.0F, 2.0F, 3.0F, 16.0F)
			.texOffs(0, 44).addBox(-9.0F, -1.5F, 28.0F, 18.0F, 3.0F, 3.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
		// the man chained to it: hips behind the bar, bent over it, wrists chained to it, legs dragging
		PartDefinition man = root.addOrReplaceChild("man", CubeListBuilder.create(), PartPose.offset(0.0F, 10.0F, 35.0F));
		PartDefinition body = man.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(78, 0).addBox(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F)
			.texOffs(72, 34).addBox(-4.0F, -3.0F, -2.6F, 8.0F, 6.0F, 1.0F), PartPose.rotation(0.6F, 0.0F, 0.0F));
		body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 20).addBox(-3.5F, -5.0F, -6.0F, 7.0F, 7.0F, 7.0F), PartPose.offsetAndRotation(0.0F, -12.0F, 0.0F, 0.5F, 0.0F, 0.0F));
		PartDefinition leftArm = body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(28, 20).addBox(-1.0F, -1.0F, -1.5F, 3.0F, 11.0F, 3.0F),
			PartPose.offsetAndRotation(5.0F, -11.0F, 0.0F, -1.9F, 0.0F, -0.15F));
		leftArm.addOrReplaceChild("left_chain", CubeListBuilder.create().texOffs(42, 44).addBox(-0.5F, 9.5F, -2.5F, 1.0F, 1.0F, 5.0F), PartPose.ZERO);
		PartDefinition rightArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(28, 20).addBox(-2.0F, -1.0F, -1.5F, 3.0F, 11.0F, 3.0F),
			PartPose.offsetAndRotation(-5.0F, -11.0F, 0.0F, -1.9F, 0.0F, 0.15F));
		rightArm.addOrReplaceChild("right_chain", CubeListBuilder.create().texOffs(42, 44).addBox(-0.5F, 9.5F, -2.5F, 1.0F, 1.0F, 5.0F), PartPose.ZERO);
		man.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(66, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 14.0F, 3.0F), PartPose.offsetAndRotation(2.0F, 0.0F, 0.0F, 0.35F, 0.0F, 0.0F));
		man.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(66, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 14.0F, 3.0F), PartPose.offsetAndRotation(-2.0F, 0.0F, 0.0F, 0.5F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 128, 64);
		// BODY: end
	}

	@Override
	public void setupAnim(final RustWheelRenderState state) {
		super.setupAnim(state);
		// forward along its facing turns the top of the wheel forward
		this.wheel.xRot = state.turned;
		float jerk = Mth.sin(state.turned * 2.0F);
		this.body.xRot += 0.08F * jerk + (state.stuck ? 0.12F * Mth.sin(state.ageInTicks * 1.7F) : 0.0F);
		this.rightArm.xRot += 0.1F * jerk;
		this.leftLeg.xRot += 0.15F * Mth.sin(state.turned);
		this.rightLeg.xRot -= 0.15F * Mth.sin(state.turned);
		for (ModelPart plate : this.shed) {
			plate.visible = !state.broken;
		}
		this.leftChain.visible = !state.broken;
		if (state.broken) {
			// the torn-free arm hangs and swings
			this.leftArm.xRot = -0.4F + 0.5F * Mth.sin(state.ageInTicks * 0.15F);
			this.leftArm.zRot = -0.3F;
		} else {
			this.leftArm.xRot += 0.1F * jerk;
		}
	}
}
