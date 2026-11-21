package life.shizune.mistinn.client.keeper;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.Nullable;

/**
 * 熄燈人的模型: taller than a man and hunched forward under it. Its head is an iron lantern hung forward on a
 * bent neck, cracked open on its right with one burning eye inside, iron spikes standing off its cap; a ragged
 * mantle bristles with more of them, a chain runs from the left shoulder across the chest to a hook at the hip,
 * and the robe hangs in tatters past the knees. The arms are longer than a man's and end in three hooked claws.
 * The eye is lit by {@link SnufferRenderer}'s glow layer. It stalks rather than walks, each move plays its clip
 * ({@code anim/keeper/<move>}) from the tell through the blow to the recovery, and standing idle it now and then
 * tilts its lantern, shifts its weight or heaves a breath.
 * The boxes are written by {@code MistinnArt/mod/snuffer_body.py}, which also lays out the 128x128 texture.
 */
public class SnufferModel extends HumanoidModel<SnufferRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("snuffer"), "main");
	/** The idles it may play while standing, and how often it plays one (ticks per chance). */
	public static final String[] IDLES = {"idle_tilt", "idle_pace", "idle_breathe"};
	public static final int IDLE_PERIOD = 90;
	public static final int IDLE_LENGTH = 60;
	/** How far the held spear sits below the shoulder, past where a man's hand would be. */
	private static final float HAND_DROP = 3.0F / 16.0F;
	/** For the pose sheet: every keeper holds this clip at this progress (null: off). */
	public static @Nullable String previewClip;
	public static float previewProgress;

	private final ModelPart skirt;
	private final ModelPart chain;
	private final ModelPart rightClaw;
	private final ModelPart leftClaw;

	public SnufferModel(final ModelPart root) {
		super(root);
		this.skirt = this.body.getChild("skirt");
		this.chain = this.body.getChild("chain");
		this.rightClaw = this.rightArm.getChild("right_claw");
		this.leftClaw = this.leftArm.getChild("left_claw");
	}

	public static LayerDefinition definition() {
		// BODY: written by MistinnArt/mod/snuffer_body.py
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(68, 31).addBox(-1.5F, -2.0F, -1.5F, 3, 3, 3)
			.texOffs(52, 18).addBox(-4.0F, -4.0F, -4.0F, 8, 2, 8)
			.texOffs(0, 18).addBox(-3.5F, -10.0F, -3.5F, 7, 6, 7)
			.texOffs(80, 31).addBox(-1.5F, -8.5F, -1.5F, 3, 3, 3)
			.texOffs(8, 38).addBox(2.5F, -13.0F, -3.5F, 1, 3, 2)
			.texOffs(0, 31).addBox(-3.0F, -11.0F, -3.0F, 6, 1, 6)
			.texOffs(112, 31).addBox(-2.0F, -12.0F, -2.0F, 4, 1, 4)
			.texOffs(44, 38).addBox(-2.0F, -15.0F, -0.5F, 4, 3, 1), PartPose.offset(0.0F, 1.0F, -3.0F));
		PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition hornLeft = head.addOrReplaceChild("horn_left", CubeListBuilder.create()
			.texOffs(92, 31).addBox(-0.5F, -5.0F, -0.5F, 1, 5, 1), PartPose.offsetAndRotation(2.5F, -11.0F, 0.0F, 0.0F, 0.0F, -0.6F));
		PartDefinition hornRight = head.addOrReplaceChild("horn_right", CubeListBuilder.create()
			.texOffs(20, 38).addBox(-0.5F, -4.0F, -0.5F, 1, 4, 1), PartPose.offsetAndRotation(-2.5F, -11.0F, 0.0F, 0.0F, 0.0F, 0.6F));
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-4.0F, 0.0F, -2.5F, 8, 13, 5)
			.texOffs(84, 0).addBox(-7.0F, -1.0F, -3.5F, 14, 6, 7)
			.texOffs(84, 18).addBox(-4.5F, 9.0F, -3.0F, 9, 2, 6)
			.texOffs(86, 38).addBox(1.5F, 9.5F, -3.5F, 2, 1, 1), PartPose.ZERO);
		PartDefinition spikeLeft = body.addOrReplaceChild("spike_left", CubeListBuilder.create()
			.texOffs(96, 31).addBox(-0.5F, -5.0F, -0.5F, 1, 5, 1), PartPose.offsetAndRotation(5.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.45F));
		PartDefinition spikeMid = body.addOrReplaceChild("spike_mid", CubeListBuilder.create()
			.texOffs(24, 38).addBox(-0.5F, -4.0F, -0.5F, 1, 4, 1), PartPose.offsetAndRotation(3.0F, -1.0F, 1.5F, 0.35F, 0.0F, -0.2F));
		PartDefinition spikeRight = body.addOrReplaceChild("spike_right", CubeListBuilder.create()
			.texOffs(100, 31).addBox(-0.5F, -5.0F, -0.5F, 1, 5, 1), PartPose.offsetAndRotation(-5.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.45F));
		PartDefinition chain = body.addOrReplaceChild("chain", CubeListBuilder.create()
			.texOffs(56, 0).addBox(-0.5F, 0.0F, -0.5F, 1, 15, 1), PartPose.offsetAndRotation(4.5F, 0.0F, -3.0F, 0.0F, 0.0F, 0.62F));
		PartDefinition chainHook = chain.addOrReplaceChild("chain_hook", CubeListBuilder.create()
			.texOffs(60, 38).addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1)
			.texOffs(80, 38).addBox(-0.5F, 2.0F, -1.5F, 1, 1, 2), PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F, 0.0F, 0.0F, -0.62F));
		PartDefinition skirt = body.addOrReplaceChild("skirt", CubeListBuilder.create()
			.texOffs(26, 0).addBox(-4.5F, 0.0F, -3.0F, 9, 10, 6)
			.texOffs(54, 38).addBox(-4.5F, 10.0F, -3.0F, 3, 4, 0)
			.texOffs(92, 38).addBox(-1.0F, 10.0F, -3.0F, 2, 2, 0)
			.texOffs(14, 38).addBox(1.5F, 10.0F, -3.0F, 3, 5, 0)
			.texOffs(0, 38).addBox(-4.5F, 10.0F, 3.0F, 4, 5, 0)
			.texOffs(72, 38).addBox(0.5F, 10.0F, 3.0F, 4, 3, 0), PartPose.offset(0.0F, 11.0F, 0.0F));
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(60, 0).addBox(-2.5F, -2.0F, -1.5F, 3, 11, 3)
			.texOffs(24, 31).addBox(-2.0F, 9.0F, -1.0F, 2, 5, 2), PartPose.offset(-5.5F, 2.5F, -1.0F));
		PartDefinition rightClaw = rightArm.addOrReplaceChild("right_claw", CubeListBuilder.create()
			.texOffs(28, 38).addBox(-1.5F, 0.0F, -1.5F, 1, 4, 1)
			.texOffs(104, 31).addBox(-0.5F, 0.0F, -1.5F, 1, 5, 1)
			.texOffs(32, 38).addBox(0.5F, 0.0F, -1.5F, 1, 4, 1)
			.texOffs(64, 38).addBox(-0.5F, 0.0F, 0.5F, 1, 3, 1), PartPose.offsetAndRotation(-1.0F, 14.0F, 0.0F, -0.35F, 0.0F, 0.0F));
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
			.texOffs(72, 0).addBox(-0.5F, -2.0F, -1.5F, 3, 11, 3)
			.texOffs(32, 31).addBox(0.0F, 9.0F, -1.0F, 2, 5, 2), PartPose.offset(5.5F, 2.5F, -1.0F));
		PartDefinition leftClaw = leftArm.addOrReplaceChild("left_claw", CubeListBuilder.create().mirror()
			.texOffs(36, 38).addBox(-1.5F, 0.0F, -1.5F, 1, 4, 1)
			.texOffs(108, 31).addBox(-0.5F, 0.0F, -1.5F, 1, 5, 1)
			.texOffs(40, 38).addBox(0.5F, 0.0F, -1.5F, 1, 4, 1)
			.texOffs(68, 38).addBox(-0.5F, 0.0F, 0.5F, 1, 3, 1), PartPose.offsetAndRotation(1.0F, 14.0F, 0.0F, -0.35F, 0.0F, 0.0F));
		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
			.texOffs(28, 18).addBox(-1.5F, 0.0F, -1.5F, 3, 10, 3)
			.texOffs(40, 31).addBox(-1.5F, 10.0F, -3.0F, 3, 2, 4), PartPose.offset(-2.0F, 12.0F, 0.0F));
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().mirror()
			.texOffs(40, 18).addBox(-1.5F, 0.0F, -1.5F, 3, 10, 3)
			.texOffs(54, 31).addBox(-1.5F, 10.0F, -3.0F, 3, 2, 4), PartPose.offset(2.0F, 12.0F, 0.0F));
		return LayerDefinition.create(mesh, 128, 128);
		// BODY END
	}

	/** The clip of the move the keeper is in, or of an idle while it stands; null when it only walks. */
	public static @Nullable Clip clip(final SnufferRenderState state) {
		if (previewClip != null) {
			return Clip.find("keeper/" + previewClip);
		}
		if (state.move != null) {
			return Clip.find("keeper/" + state.move);
		}
		return state.idle == null ? null : Clip.find("keeper/" + state.idle);
	}

	/** Progress through the clip: a move's spans the whole move, an idle's its own length. */
	public static float progress(final SnufferRenderState state) {
		if (previewClip != null) {
			return previewProgress;
		}
		if (state.move != null) {
			return state.moveLength <= 0 ? 0.0F : Mth.clamp(state.moveTicks / state.moveLength, 0.0F, 1.0F);
		}
		return Mth.clamp(state.idleTicks / IDLE_LENGTH, 0.0F, 1.0F);
	}

	/**
	 * The idle a keeper standing still plays at {@code age} (its ticks alive), or null: one chance every
	 * {@link #IDLE_PERIOD} ticks, picked by its id so two keepers do not move together, and one chance in four
	 * of nothing at all.
	 */
	public static @Nullable String idle(final int id, final float age) {
		int cycle = (int) (age / IDLE_PERIOD);
		if (age - cycle * IDLE_PERIOD >= IDLE_LENGTH) {
			return null;
		}
		int pick = Math.floorMod(mix(id * 31 + cycle), IDLES.length + 1);
		return pick < IDLES.length ? IDLES[pick] : null;
	}

	/** Ticks into the idle playing at {@code age}. */
	public static float idleTicks(final float age) {
		return age % IDLE_PERIOD;
	}

	private static int mix(final int seed) {
		int h = seed * 0x9E3779B9;
		h ^= h >>> 16;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		return h;
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
		// the chain swings against the stride, the claws flex slowly, and clench as an arm swings forward
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		float phase = state.walkAnimationPos * 0.6662F * 0.7F;
		this.chain.zRot = 0.62F + Mth.sin(phase) * 0.12F * speed + Mth.sin(state.ageInTicks * 0.05F) * 0.03F;
		this.chain.xRot = -this.body.xRot * 0.5F;
		float flex = Mth.sin(state.ageInTicks * 0.09F) * 0.12F;
		this.rightClaw.xRot = -0.35F + flex - Math.max(0.0F, -this.rightArm.xRot) * 0.35F;
		this.leftClaw.xRot = -0.35F - flex - Math.max(0.0F, -this.leftArm.xRot) * 0.35F;
	}

	/**
	 * 步態: a long, slow stalk instead of a man's walk, bent under its own height. The stride is longer and
	 * slower, the arms hang and trail the legs instead of pumping, and the torso stoops and rolls with each step.
	 * A swing in progress keeps its arms.
	 */
	private void stalk(final SnufferRenderState state) {
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		float phase = state.walkAnimationPos * 0.6662F * 0.7F;
		this.rightLeg.xRot = Mth.cos(phase) * 0.95F * speed;
		this.leftLeg.xRot = Mth.cos(phase + Mth.PI) * 0.95F * speed;
		if (state.swingAnimation <= 0.0F) {
			this.rightArm.xRot = Mth.cos(phase + Mth.PI - 0.5F) * 0.35F * speed - 0.15F;
			this.leftArm.xRot = Mth.cos(phase - 0.5F) * 0.35F * speed - 0.15F;
			this.rightArm.zRot = 0.1F;
			this.leftArm.zRot = -0.1F;
		}
		// the hunch: the body leans into every step, more as it hurries
		this.body.xRot += 0.28F + 0.12F * speed;
		this.body.zRot += Mth.sin(phase) * 0.06F * speed;
	}

	/**
	 * 燈搖: the lantern hangs forward like a lamp on a hook. It bobs twice a stride, lags behind a turn and swings
	 * out against it, and in still air drifts slowly.
	 */
	private void lantern(final SnufferRenderState state) {
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		float phase = state.walkAnimationPos * 0.6662F * 0.7F;
		this.head.xRot += 0.12F + this.body.xRot * 0.4F + Mth.cos(phase * 2.0F - 0.6F) * 0.07F * speed;
		this.head.zRot += Mth.sin(state.ageInTicks * 0.07F) * 0.05F * (1.0F - speed) + Mth.sin(phase - 0.6F) * 0.08F * speed;
		this.head.zRot += Mth.clamp(-state.turnSpeed * 0.02F, -0.3F, 0.3F);
	}

	/** The spear sits in the claws, at the end of the long arm. */
	@Override
	public void translateToHand(final SnufferRenderState state, final HumanoidArm arm, final PoseStack poseStack) {
		super.translateToHand(state, arm, poseStack);
		poseStack.translate(0.0F, HAND_DROP, 0.0F);
	}
}
