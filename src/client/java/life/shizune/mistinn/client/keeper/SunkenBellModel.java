package life.shizune.mistinn.client.keeper;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * 沉鐘的模型: a drowned bell-ringer, broad and short in the leg, his head and shoulders sealed in a great bronze bell with
 * barnacles on it and kelp hanging from its rim, one dim eye glimpsed under the rim. An anchor chain is wound across his
 * chest and back and round his forearms, rags hang from a rope belt, a small anchor is strapped to his back, and shackles
 * close round his ankles. In his right fist is the bell's tongue, the clapper, its iron bulb resting on the floor in front
 * of him. He wades rather than walks; each move plays its clip ({@code anim/bell/<move>}) from the tell through the blow to
 * the recovery, and standing idle he now and then rocks his bell, drags the clapper or heaves a breath.
 * The boxes between the BODY markers are generated together with the 128x256 texture layout.
 */
public class SunkenBellModel extends HumanoidModel<SunkenBellRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("sunken_bell"), "main");
	/** The idles it may play while standing, and how often it plays one (ticks per chance). */
	public static final String[] IDLES = {"idle_sway", "idle_drag", "idle_breathe"};
	public static final int IDLE_PERIOD = 100;
	public static final int IDLE_LENGTH = 60;
	/** For the pose sheet: every bell keeper holds this clip at this progress (null: off). */
	public static @Nullable String previewClip;
	public static float previewProgress;
	/**
	 * How far the clapper is tipped forward from the fist: far enough that the back edge of its bulb, six pixels across,
	 * rests on the floor in front of the feet instead of sinking into it.
	 */
	private static final float CLAPPER_REST = -1.36F;

	private final ModelPart clapper;

	public SunkenBellModel(final ModelPart root) {
		super(root);
		this.clapper = this.rightArm.getChild("clapper");
	}

	public static LayerDefinition definition() {
		// BODY: generated
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(17, 54).addBox(-4.5F, -14.5F, -4.5F, 9.0F, 2.0F, 9.0F)
			.texOffs(65, 120).addBox(-2.5F, -17.5F, -1.0F, 1.0F, 3.0F, 2.0F)
			.texOffs(72, 120).addBox(1.5F, -17.5F, -1.0F, 1.0F, 3.0F, 2.0F)
			.texOffs(93, 129).addBox(-2.5F, -18.5F, -1.0F, 5.0F, 1.0F, 2.0F)
			.texOffs(0, 41).addBox(-5.5F, -9.0F, -5.5F, 11.0F, 1.0F, 11.0F)
			.texOffs(108, 129).addBox(4.0F, -2.0F, -7.6F, 2.0F, 2.0F, 1.0F)
			.texOffs(45, 135).addBox(4.0F, -4.0F, -7.2F, 1.0F, 1.0F, 1.0F)
			.texOffs(24, 129).addBox(-7.6F, -1.0F, 2.0F, 1.6F, 2.0F, 2.0F, new CubeDeformation(0.1F))
			.texOffs(33, 129).addBox(6.6F, -3.0F, 1.0F, 1.0F, 2.0F, 2.0F)
			.texOffs(50, 135).addBox(-3.0F, -11.0F, 4.2F, 2.0F, 1.0F, 1.0F)
			.texOffs(115, 129).addBox(1.0F, -1.0F, 6.4F, 2.0F, 2.0F, 1.0F)
			.texOffs(90, 110).addBox(4.0F, 2.0F, -7.4F, 1.0F, 7.0F, 1.0F)
			.texOffs(55, 120).addBox(-3.0F, 2.0F, -7.6F, 1.0F, 5.0F, 1.0F)
			.texOffs(50, 120).addBox(-8.0F, 2.0F, -1.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.05F))
			.texOffs(85, 89).addBox(7.0F, 2.0F, 1.0F, 1.0F, 8.0F, 1.0F, new CubeDeformation(0.05F))
			.texOffs(95, 110).addBox(-4.0F, 2.0F, 6.4F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.1F))
			.texOffs(60, 120).addBox(3.0F, 2.0F, 6.6F, 1.0F, 5.0F, 1.0F)
			.texOffs(57, 135).addBox(0.0F, -2.0F, -6.0F, 2.0F, 1.0F, 1.0F), PartPose.offset(0.0F, -2.0F, 0.0F));
		head.addOrReplaceChild("bell_shard", CubeListBuilder.create()
			.texOffs(17, 129).addBox(-1.0F, -1.5F, -0.6F, 2.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(-4.0F, -2.5F, -8.0F, 0.0F, 0.0F, 0.3491F));
		head.addOrReplaceChild("bell_10_2", CubeListBuilder.create()
			.texOffs(13, 89).addBox(-2.617F, -4.099F, -0.5F, 5.234F, 8.198F, 1.0F), PartPose.offsetAndRotation(2.337F, -1.5F, -7.192F, -3.0326F, 0.3142F, -3.1416F));
		head.addOrReplaceChild("bell_10_1", CubeListBuilder.create()
			.texOffs(0, 89).addBox(-2.012F, -4.285F, -0.5F, 4.024F, 8.57F, 1.0F), PartPose.offsetAndRotation(1.796F, -9.5F, -5.528F, -2.8245F, 0.3142F, -3.1416F));
		head.addOrReplaceChild("bell_9_2", CubeListBuilder.create()
			.texOffs(0, 54).addBox(-2.617F, -4.099F, -0.5F, 5.235F, 8.198F, 1.0F), PartPose.offsetAndRotation(6.118F, -1.5F, -4.445F, -3.0326F, 0.9425F, -3.1416F));
		head.addOrReplaceChild("bell_9_1", CubeListBuilder.create()
			.texOffs(110, 78).addBox(-2.011F, -4.285F, -0.5F, 4.023F, 8.57F, 1.0F), PartPose.offsetAndRotation(4.702F, -9.5F, -3.417F, -2.8245F, 0.9425F, 3.1416F));
		head.addOrReplaceChild("bell_8_2", CubeListBuilder.create()
			.texOffs(95, 78).addBox(-2.617F, -4.099F, -0.5F, 5.235F, 8.198F, 1.0F), PartPose.offsetAndRotation(7.562F, -1.5F, 0.0F, 0.1089F, 1.5708F, 0.0F));
		head.addOrReplaceChild("bell_8_1", CubeListBuilder.create()
			.texOffs(82, 78).addBox(-2.011F, -4.285F, -0.5F, 4.023F, 8.57F, 1.0F), PartPose.offsetAndRotation(5.812F, -9.5F, 0.0F, 0.3171F, 1.5708F, 0.0F));
		head.addOrReplaceChild("bell_7_2", CubeListBuilder.create()
			.texOffs(104, 41).addBox(-2.617F, -4.099F, -0.5F, 5.235F, 8.198F, 1.0F), PartPose.offsetAndRotation(6.118F, -1.5F, 4.445F, 0.1089F, 0.9425F, 0.0F));
		head.addOrReplaceChild("bell_7_1", CubeListBuilder.create()
			.texOffs(69, 78).addBox(-2.011F, -4.285F, -0.5F, 4.023F, 8.57F, 1.0F), PartPose.offsetAndRotation(4.702F, -9.5F, 3.417F, 0.3171F, 0.9425F, 0.0F));
		head.addOrReplaceChild("bell_6_2", CubeListBuilder.create()
			.texOffs(54, 78).addBox(-2.617F, -4.099F, -0.5F, 5.234F, 8.198F, 1.0F), PartPose.offsetAndRotation(2.337F, -1.5F, 7.192F, 0.1089F, 0.3142F, 0.0F));
		head.addOrReplaceChild("bell_6_1", CubeListBuilder.create()
			.texOffs(41, 78).addBox(-2.012F, -4.285F, -0.5F, 4.024F, 8.57F, 1.0F), PartPose.offsetAndRotation(1.796F, -9.5F, 5.528F, 0.3171F, 0.3142F, 0.0F));
		head.addOrReplaceChild("bell_5_2", CubeListBuilder.create()
			.texOffs(26, 78).addBox(-2.617F, -4.099F, -0.5F, 5.234F, 8.198F, 1.0F), PartPose.offsetAndRotation(-2.337F, -1.5F, 7.192F, 0.1089F, -0.3142F, 0.0F));
		head.addOrReplaceChild("bell_5_1", CubeListBuilder.create()
			.texOffs(13, 78).addBox(-2.012F, -4.285F, -0.5F, 4.024F, 8.57F, 1.0F), PartPose.offsetAndRotation(-1.796F, -9.5F, 5.528F, 0.3171F, -0.3142F, 0.0F));
		head.addOrReplaceChild("bell_4_2", CubeListBuilder.create()
			.texOffs(87, 41).addBox(-2.618F, -4.099F, -0.5F, 5.235F, 8.198F, 1.0F), PartPose.offsetAndRotation(-6.118F, -1.5F, 4.445F, 0.1089F, -0.9425F, 0.0F));
		head.addOrReplaceChild("bell_4_1", CubeListBuilder.create()
			.texOffs(0, 78).addBox(-2.012F, -4.285F, -0.5F, 4.023F, 8.57F, 1.0F), PartPose.offsetAndRotation(-4.702F, -9.5F, 3.417F, 0.3171F, -0.9425F, 0.0F));
		head.addOrReplaceChild("bell_3_2", CubeListBuilder.create()
			.texOffs(103, 66).addBox(-2.617F, -4.099F, -0.5F, 5.235F, 8.198F, 1.0F), PartPose.offsetAndRotation(-7.563F, -1.5F, 0.0F, 0.1089F, -1.5708F, 0.0F));
		head.addOrReplaceChild("bell_3_1", CubeListBuilder.create()
			.texOffs(90, 66).addBox(-2.011F, -4.285F, -0.5F, 4.023F, 8.57F, 1.0F), PartPose.offsetAndRotation(-5.813F, -9.5F, 0.0F, 0.3171F, -1.5708F, 0.0F));
		head.addOrReplaceChild("bell_2_2", CubeListBuilder.create()
			.texOffs(70, 41).addBox(-2.618F, -4.099F, -0.5F, 5.235F, 8.198F, 1.0F), PartPose.offsetAndRotation(-6.118F, -1.5F, -4.445F, -3.0326F, -0.9425F, 3.1416F));
		head.addOrReplaceChild("bell_2_1", CubeListBuilder.create()
			.texOffs(77, 66).addBox(-2.012F, -4.285F, -0.5F, 4.023F, 8.57F, 1.0F), PartPose.offsetAndRotation(-4.702F, -9.5F, -3.417F, -2.8245F, -0.9425F, -3.1416F));
		head.addOrReplaceChild("bell_1_2", CubeListBuilder.create()
			.texOffs(62, 66).addBox(-2.617F, -4.099F, -0.5F, 5.234F, 8.198F, 1.0F), PartPose.offsetAndRotation(-2.337F, -1.5F, -7.192F, -3.0326F, -0.3142F, -3.1416F));
		head.addOrReplaceChild("bell_1_1", CubeListBuilder.create()
			.texOffs(49, 66).addBox(-2.012F, -4.285F, -0.5F, 4.024F, 8.57F, 1.0F), PartPose.offsetAndRotation(-1.796F, -9.5F, -5.528F, -2.8245F, -0.3142F, 3.1416F));
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-7.0F, -1.0F, -4.0F, 14.0F, 14.0F, 8.0F)
			.texOffs(90, 89).addBox(-6.0F, 4.0F, -5.0F, 12.0F, 8.0F, 1.0F)
			.texOffs(54, 54).addBox(-6.0F, -1.0F, 4.0F, 12.0F, 9.0F, 2.0F)
			.texOffs(83, 54).addBox(-9.0F, -1.0F, -3.0F, 4.0F, 5.0F, 6.0F, new CubeDeformation(0.05F))
			.texOffs(104, 54).addBox(5.0F, -1.0F, -3.0F, 4.0F, 5.0F, 6.0F, new CubeDeformation(0.05F))
			.texOffs(0, 66).addBox(-7.5F, 11.5F, -4.5F, 15.0F, 2.0F, 9.0F)
			.texOffs(100, 110).addBox(-5.0F, 13.0F, -4.8F, 10.0F, 7.0F, 1.0F)
			.texOffs(0, 100).addBox(-6.0F, 13.0F, 3.8F, 12.0F, 8.0F, 1.0F)
			.texOffs(28, 89).addBox(-7.5F, 13.0F, -2.0F, 1.0F, 6.0F, 4.0F, new CubeDeformation(0.05F))
			.texOffs(27, 100).addBox(6.5F, 13.0F, -2.0F, 1.0F, 5.0F, 4.0F, new CubeDeformation(0.05F))
			.texOffs(21, 23).addBox(-1.0F, 6.0F, 6.8F, 2.0F, 12.0F, 2.0F)
			.texOffs(40, 129).addBox(-4.0F, 8.0F, 6.8F, 8.0F, 1.0F, 2.0F, new CubeDeformation(0.05F))
			.texOffs(63, 129).addBox(-1.0F, 4.0F, 7.3F, 2.0F, 2.0F, 1.0F), PartPose.offset(0.0F, -2.0F, 0.0F));
		body.addOrReplaceChild("anchor_arm_l", CubeListBuilder.create()
			.texOffs(96, 120).addBox(0.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(0.0F, 18.0F, 7.8F, 0.0F, 0.0F, -0.5236F));
		body.addOrReplaceChild("anchor_arm_r", CubeListBuilder.create()
			.texOffs(79, 120).addBox(-5.0F, -1.0F, -1.0F, 5.0F, 2.0F, 2.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 18.0F, 7.8F, 0.0F, 0.0F, 0.5236F));
		body.addOrReplaceChild("chain_back", CubeListBuilder.create()
			.texOffs(0, 139).addBox(-9.0F, -0.5F, -0.5F, 18.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 6.5F, 6.3F, 0.0F, 0.0F, 0.6109F));
		body.addOrReplaceChild("chain_chest", CubeListBuilder.create()
			.texOffs(64, 135).addBox(-9.0F, -0.5F, -0.5F, 18.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(0.0F, 6.5F, -5.3F, 0.0F, 0.0F, -0.6109F));
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(45, 0).addBox(-2.5F, -2.0F, -3.0F, 5.0F, 11.0F, 6.0F)
			.texOffs(32, 23).addBox(-3.0F, 9.0F, -3.5F, 6.0F, 8.0F, 7.0F)
			.texOffs(38, 100).addBox(-3.5F, 14.0F, -4.0F, 7.0F, 1.0F, 8.0F)
			.texOffs(69, 100).addBox(-3.5F, 11.0F, -4.0F, 7.0F, 1.0F, 8.0F)
			.texOffs(39, 89).addBox(-2.5F, 17.0F, -3.0F, 5.0F, 4.0F, 6.0F)
			.texOffs(0, 135).addBox(-2.5F, 19.0F, -4.0F, 5.0F, 2.0F, 1.0F), PartPose.offset(-9.5F, 0.0F, 0.0F));
		rightArm.addOrReplaceChild("clapper", CubeListBuilder.create()
			.texOffs(31, 110).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 8.0F, 1.0F)
			.texOffs(113, 120).addBox(-1.5F, 8.0F, -2.0F, 3.0F, 2.0F, 3.0F)
			.texOffs(45, 41).addBox(-3.0F, 10.0F, -3.5F, 6.0F, 6.0F, 6.0F)
			.texOffs(0, 129).addBox(-2.0F, 16.0F, -2.5F, 4.0F, 1.0F, 4.0F)
			.texOffs(84, 129).addBox(-1.0F, -2.0F, -1.5F, 2.0F, 2.0F, 2.0F), PartPose.offsetAndRotation(0.0F, 19.0F, 0.0F, CLAPPER_REST, 0.0F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create()
			.texOffs(68, 0).addBox(-2.5F, -2.0F, -3.0F, 5.0F, 11.0F, 6.0F)
			.texOffs(59, 23).addBox(-3.0F, 9.0F, -3.5F, 6.0F, 8.0F, 7.0F)
			.texOffs(0, 110).addBox(-3.5F, 13.0F, -4.0F, 7.0F, 1.0F, 8.0F)
			.texOffs(62, 89).addBox(-2.5F, 17.0F, -3.0F, 5.0F, 4.0F, 6.0F)
			.texOffs(13, 135).addBox(-2.5F, 19.0F, -4.0F, 5.0F, 2.0F, 1.0F)
			.texOffs(72, 129).addBox(-0.5F, 21.0F, -0.5F, 1.0F, 2.0F, 1.0F)
			.texOffs(77, 129).addBox(-0.5F, 23.0F, -1.0F, 1.0F, 1.0F, 2.0F), PartPose.offset(9.5F, 0.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create()
			.texOffs(91, 0).addBox(-2.4F, -1.0F, -2.5F, 5.0F, 12.0F, 5.0F)
			.texOffs(36, 110).addBox(-2.9F, 11.0F, -4.0F, 6.0F, 2.0F, 6.5F)
			.texOffs(0, 120).addBox(-2.9F, 8.0F, -3.0F, 6.0F, 2.0F, 6.0F)
			.texOffs(26, 135).addBox(-3.4F, 10.0F, -0.5F, 1.0F, 2.0F, 1.0F)
			.texOffs(31, 135).addBox(-0.4F, 3.0F, -3.5F, 2.0F, 2.0F, 1.0F), PartPose.offset(-3.6F, 11.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create()
			.texOffs(0, 23).addBox(-2.6F, -1.0F, -2.5F, 5.0F, 12.0F, 5.0F)
			.texOffs(63, 110).addBox(-3.1F, 11.0F, -4.0F, 6.0F, 2.0F, 6.5F)
			.texOffs(25, 120).addBox(-3.1F, 8.0F, -3.0F, 6.0F, 2.0F, 6.0F)
			.texOffs(38, 135).addBox(-1.6F, 4.0F, -3.5F, 2.0F, 2.0F, 1.0F), PartPose.offset(3.6F, 11.0F, 0.0F));
		return LayerDefinition.create(mesh, 128, 256);
		// BODY END
	}

	/** The clip of the move it is in, or of an idle while it stands; null when it only wades. */
	public static @Nullable Clip clip(final SunkenBellRenderState state) {
		if (previewClip != null) {
			return Clip.find("bell/" + previewClip);
		}
		if (state.move != null) {
			return Clip.find("bell/" + state.move);
		}
		return state.idle == null ? null : Clip.find("bell/" + state.idle);
	}

	/** Progress through the clip: a move's spans the whole move, an idle's its own length. */
	public static float progress(final SunkenBellRenderState state) {
		if (previewClip != null) {
			return previewProgress;
		}
		if (state.move != null) {
			return state.moveLength <= 0 ? 0.0F : Mth.clamp(state.moveTicks / state.moveLength, 0.0F, 1.0F);
		}
		return Mth.clamp(state.idleTicks / IDLE_LENGTH, 0.0F, 1.0F);
	}

	/** The idle a bell keeper standing still plays at {@code age}, or null: picked by its id, one chance in four of nothing. */
	public static @Nullable String idle(final int id, final float age) {
		int cycle = (int) (age / IDLE_PERIOD);
		if (age - cycle * IDLE_PERIOD >= IDLE_LENGTH) {
			return null;
		}
		int h = (id * 31 + cycle) * 0x9E3779B9;
		h ^= h >>> 16;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		int pick = Math.floorMod(h, IDLES.length + 1);
		return pick < IDLES.length ? IDLES[pick] : null;
	}

	/** Ticks into the idle playing at {@code age}. */
	public static float idleTicks(final float age) {
		return age % IDLE_PERIOD;
	}

	@Override
	public void setupAnim(final SunkenBellRenderState state) {
		super.setupAnim(state);
		this.wade(state);
		Clip clip = clip(state);
		if (clip != null) {
			float p = progress(state);
			Poser.limbs(clip, p, this, 1, clip.weight(p));
		}
		// the clapper's bulb drags a little behind the fist as the arm swings
		this.clapper.xRot = CLAPPER_REST + Mth.sin(state.walkAnimationPos * 0.6662F * 0.6F) * 0.08F * Math.min(state.walkAnimationSpeed, 1.0F);
	}

	/**
	 * 涉水: a heavy, wide wade instead of a man's walk. Short strides from the hip, the whole body rolling from side to
	 * side over the planted foot, the free arm swinging out wide and the clapper dragged at the other side. A swing in
	 * progress keeps its arms.
	 */
	private void wade(final SunkenBellRenderState state) {
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		float phase = state.walkAnimationPos * 0.6662F * 0.6F;
		this.rightLeg.xRot = Mth.cos(phase) * 0.7F * speed;
		this.leftLeg.xRot = Mth.cos(phase + Mth.PI) * 0.7F * speed;
		this.rightLeg.zRot = 0.06F;
		this.leftLeg.zRot = -0.06F;
		if (state.swingAnimation <= 0.0F) {
			this.rightArm.xRot = Mth.cos(phase + Mth.PI) * 0.18F * speed - 0.1F;
			this.leftArm.xRot = Mth.cos(phase) * 0.4F * speed - 0.08F;
			this.rightArm.zRot = 0.12F;
			this.leftArm.zRot = -0.14F - Math.abs(Mth.sin(phase)) * 0.12F * speed;
		}
		// the roll over the planted foot, and a forward hunch that deepens as it hurries
		this.body.zRot += Mth.sin(phase) * 0.09F * speed;
		this.body.xRot += 0.1F + 0.1F * speed;
		// the bell sways a beat behind the body, and drifts in still air
		this.head.zRot += Mth.sin(phase - 0.7F) * 0.08F * speed + Mth.sin(state.ageInTicks * 0.05F) * 0.03F * (1.0F - speed);
		this.head.xRot = 0.05F + this.body.xRot * 0.5F;
		this.head.yRot *= 0.6F;
	}
}
