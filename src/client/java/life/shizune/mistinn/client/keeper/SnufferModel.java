package life.shizune.mistinn.client.keeper;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * 熄燈人的模型: a tall, thin keeper whose head is an iron lantern. The lantern (base ring, open
 * cage, stepped cap and hanging ring) sits where a head would, with the flame inside its cage;
 * a sailcloth mantle covers the shoulders, a long sea-coat hangs past the knees, and the arms are
 * longer than a man's. The flame itself is lit by {@link SnufferRenderer}'s glow layer.
 * It stalks rather than walks, and each move plays its clip ({@code anim/keeper/*}) from the
 * tell through the blow to the recovery, so the tell can be read in the body.
 * Texture layout is the vanilla box layout on a 64x64 sheet.
 */
public class SnufferModel extends HumanoidModel<SnufferRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("snuffer"), "main");

	private final ModelPart skirt;

	public SnufferModel(final ModelPart root) {
		super(root);
		this.skirt = this.body.getChild("skirt");
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
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(0, 18).addBox(-3.5F, 0.0F, -2.0F, 7, 12, 4)
			.texOffs(0, 49).addBox(-6.5F, -1.0F, -2.5F, 13, 6, 5), PartPose.ZERO);
		// 衣襬: the skirt hangs from the waist on its own, so it can swing clear of the legs
		body.addOrReplaceChild("skirt", CubeListBuilder.create()
			.texOffs(22, 20).addBox(-4.0F, 0.0F, -2.5F, 8, 9, 5)
			.texOffs(28, 12).addBox(1.5F, 0.5F, -3.5F, 2, 3, 2), PartPose.offset(0.0F, 10.0F, 0.0F));
		// 長臂: thin and two pixels longer than a man's
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(48, 20).addBox(-2.0F, -2.0F, -1.5F, 3, 14, 3), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(48, 20).mirror().addBox(-1.0F, -2.0F, -1.5F, 3, 14, 3), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 34).addBox(-1.5F, 0.0F, -1.5F, 3, 12, 3), PartPose.offset(-2.0F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 34).mirror().addBox(-1.5F, 0.0F, -1.5F, 3, 12, 3), PartPose.offset(2.0F, 12.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** The clip of the move the keeper is in, or null when it is only walking or standing. */
	public static @Nullable Clip clip(final SnufferRenderState state) {
		return state.move == null ? null : Clip.find("keeper/" + state.move);
	}

	/** Progress through the move's clip, which spans the whole move. */
	public static float progress(final SnufferRenderState state) {
		return state.moveLength <= 0 ? 0.0F : Mth.clamp(state.moveTicks / state.moveLength, 0.0F, 1.0F);
	}

	@Override
	public void setupAnim(final SnufferRenderState state) {
		super.setupAnim(state);
		this.stalk(state);
		Clip clip = clip(state);
		if (clip != null) {
			float p = progress(state);
			Poser.limbs(clip, p, this, 1, clip.weight(p));
		}
		this.lantern(state);
		// the skirt follows the leading leg out and drags behind the trailing one
		float forward = Math.min(this.rightLeg.xRot, this.leftLeg.xRot);
		float back = Math.max(this.rightLeg.xRot, this.leftLeg.xRot);
		this.skirt.xRot = 0.55F * forward + 0.25F * back - this.body.xRot * 0.6F;
	}

	/**
	 * 步態: a long, slow stalk instead of a man's walk. The stride is longer and slower, the
	 * arms hang and trail the legs instead of pumping, and the torso stoops and rolls with each
	 * step. A swing in progress keeps its arms.
	 */
	private void stalk(final SnufferRenderState state) {
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		float phase = state.walkAnimationPos * 0.6662F * 0.7F;
		this.rightLeg.xRot = Mth.cos(phase) * 0.95F * speed;
		this.leftLeg.xRot = Mth.cos(phase + Mth.PI) * 0.95F * speed;
		if (state.swingAnimation <= 0.0F) {
			this.rightArm.xRot = Mth.cos(phase + Mth.PI - 0.5F) * 0.35F * speed + 0.1F;
			this.leftArm.xRot = Mth.cos(phase - 0.5F) * 0.35F * speed + 0.1F;
			this.rightArm.zRot = 0.08F;
			this.leftArm.zRot = -0.08F;
		}
		this.body.xRot += 0.1F + 0.12F * speed;
		this.body.zRot += Mth.sin(phase) * 0.06F * speed;
	}

	/**
	 * 燈搖: the lantern head hangs forward like a lamp on a hook. It bobs twice a stride, lags
	 * behind a turn and swings out against it, and in still air drifts slowly.
	 */
	private void lantern(final SnufferRenderState state) {
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		float phase = state.walkAnimationPos * 0.6662F * 0.7F;
		this.head.xRot += 0.08F + this.body.xRot * 0.5F + Mth.cos(phase * 2.0F - 0.6F) * 0.07F * speed;
		this.head.zRot += Mth.sin(state.ageInTicks * 0.07F) * 0.05F * (1.0F - speed) + Mth.sin(phase - 0.6F) * 0.08F * speed;
		this.head.zRot += Mth.clamp(-state.turnSpeed * 0.02F, -0.3F, 0.3F);
	}
}
