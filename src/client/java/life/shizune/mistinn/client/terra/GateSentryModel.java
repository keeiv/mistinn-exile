package life.shizune.mistinn.client.terra;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.terra.GateSentry;
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
 * 閘衛機兵的模型: a squat machine leaning forward, a helmet of a head sunk between great shoulder plates, long arms ending
 * in fists like hammers, short thick legs, and the lattice crystal caged on its back. The boxes between the GENERATED
 * markers come from the gate sentry's model spec together with its 128x128 texture layout; do not edit them by hand.
 * <p>
 * It lumbers, rolling a little from side to side. Knelt at its post it sits back on its shins, slumped, its fists on the
 * ground. For the slam both fists go up over its head and come down before it, and stay there a moment, the crystal on
 * its back pushed out as it vents; for the charge it crouches with its arms swung back, and runs bent low.
 */
public class GateSentryModel extends EntityModel<GateSentryRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("gate_sentry"), "main");

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart core;
	private final ModelPart armRight;
	private final ModelPart armLeft;
	private final ModelPart forearmRight;
	private final ModelPart forearmLeft;
	private final ModelPart legRight;
	private final ModelPart legLeft;
	private final ModelPart shinRight;
	private final ModelPart shinLeft;

	public GateSentryModel(final ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.core = this.body.getChild("core");
		this.armRight = this.body.getChild("arm_right");
		this.armLeft = this.body.getChild("arm_left");
		this.forearmRight = this.armRight.getChild("forearm_right");
		this.forearmLeft = this.armLeft.getChild("forearm_left");
		this.legRight = root.getChild("leg_right");
		this.legLeft = root.getChild("leg_left");
		this.shinRight = this.legRight.getChild("shin_right");
		this.shinLeft = this.legLeft.getChild("shin_left");
	}

	public static LayerDefinition createBodyLayer() {
		// BEGIN GENERATED LAYER
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			// pelvis
			.texOffs(42, 45).addBox(-6.0F, -3.0F, -4.0F, 12.0F, 6.0F, 8.0F)
			// torso
			.texOffs(0, 0).addBox(-8.0F, -17.0F, -5.0F, 16.0F, 14.0F, 11.0F)
			// chest_plate
			.texOffs(62, 77).addBox(-6.0F, -16.0F, -6.0F, 12.0F, 8.0F, 1.0F, new CubeDeformation(0.02F))
			// vent
			.texOffs(98, 89).addBox(-4.0F, -7.0F, -6.0F, 8.0F, 3.0F, 1.0F, new CubeDeformation(0.02F))
			// back_frame
			.texOffs(50, 62).addBox(-5.0F, -16.0F, 6.0F, 10.0F, 11.0F, 2.0F)
			// stack_right
			.texOffs(116, 77).addBox(-4.5F, -20.0F, 5.0F, 2.0F, 4.0F, 2.0F)
			// stack_left
			.texOffs(0, 89).addBox(2.5F, -20.0F, 5.0F, 2.0F, 4.0F, 2.0F)
			// pipe_right
			.texOffs(79, 96).addBox(-9.0F, -13.0F, 6.0F, 4.0F, 1.0F, 1.0F)
			// pipe_left
			.texOffs(90, 96).addBox(5.0F, -13.0F, 6.0F, 4.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.2094F, 0.0F, 0.0F));
		body.addOrReplaceChild("head", CubeListBuilder.create()
			// skull
			.texOffs(83, 45).addBox(-4.0F, -5.0F, -5.0F, 8.0F, 6.0F, 8.0F)
			// brow
			.texOffs(43, 96).addBox(-4.5F, -5.5F, -5.5F, 9.0F, 2.0F, 1.0F, new CubeDeformation(0.02F))
			// visor
			.texOffs(64, 96).addBox(-3.0F, -2.0F, -5.5F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0.02F))
			// jaw
			.texOffs(0, 96).addBox(-3.0F, 0.0F, -5.5F, 6.0F, 2.0F, 2.0F, new CubeDeformation(0.02F))
			// rod
			.texOffs(93, 89).addBox(-4.5F, -8.0F, 1.0F, 1.0F, 4.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, -15.0F, -3.0F, -0.2094F, 0.0F, 0.0F));
		body.addOrReplaceChild("core", CubeListBuilder.create()
			// core_crystal
			.texOffs(89, 77).addBox(-2.5F, -3.0F, 0.5F, 5.0F, 6.0F, 3.0F)
			// core_cap_top
			.texOffs(9, 89).addBox(-3.5F, -4.0F, 0.0F, 7.0F, 1.0F, 4.0F, new CubeDeformation(0.02F))
			// core_cap_bottom
			.texOffs(32, 89).addBox(-3.5F, 3.0F, 0.0F, 7.0F, 1.0F, 4.0F, new CubeDeformation(0.02F))
			// core_bar_right
			.texOffs(106, 77).addBox(-3.5F, -3.0F, 3.0F, 1.0F, 6.0F, 1.0F)
			// core_bar_left
			.texOffs(111, 77).addBox(2.5F, -3.0F, 3.0F, 1.0F, 6.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, -11.0F, 7.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition armRight = body.addOrReplaceChild("arm_right", CubeListBuilder.create()
			// pauldron_right
			.texOffs(54, 26).addBox(-5.0F, -4.0F, -5.0F, 8.0F, 6.0F, 10.0F, new CubeDeformation(0.02F))
			// upper_arm_right
			.texOffs(0, 45).addBox(-1.5F, 1.0F, -3.0F, 4.0F, 10.0F, 6.0F),
			PartPose.offsetAndRotation(-11.0F, -15.0F, 0.0F, -0.2094F, 0.0F, 0.0F));
		armRight.addOrReplaceChild("forearm_right", CubeListBuilder.create()
			// forearm_right_core
			.texOffs(0, 26).addBox(-2.5F, 0.0F, -4.0F, 5.0F, 10.0F, 8.0F)
			// fist_right
			.texOffs(55, 0).addBox(-3.5F, 10.0F, -5.0F, 7.0F, 8.0F, 10.0F)
			// knuckle_right
			.texOffs(55, 89).addBox(-4.0F, 12.0F, -6.0F, 8.0F, 4.0F, 1.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(0.5F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition armLeft = body.addOrReplaceChild("arm_left", CubeListBuilder.create()
			// pauldron_left
			.texOffs(91, 26).addBox(-3.0F, -4.0F, -5.0F, 8.0F, 6.0F, 10.0F, new CubeDeformation(0.02F))
			// upper_arm_left
			.texOffs(21, 45).addBox(-2.5F, 1.0F, -3.0F, 4.0F, 10.0F, 6.0F),
			PartPose.offsetAndRotation(11.0F, -15.0F, 0.0F, -0.2094F, 0.0F, 0.0F));
		armLeft.addOrReplaceChild("forearm_left", CubeListBuilder.create()
			// forearm_left_core
			.texOffs(27, 26).addBox(-2.5F, 0.0F, -4.0F, 5.0F, 10.0F, 8.0F)
			// fist_left
			.texOffs(90, 0).addBox(-3.5F, 10.0F, -5.0F, 7.0F, 8.0F, 10.0F)
			// knuckle_left
			.texOffs(74, 89).addBox(-4.0F, 12.0F, -6.0F, 8.0F, 4.0F, 1.0F, new CubeDeformation(0.02F)),
			PartPose.offsetAndRotation(-0.5F, 11.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition legRight = root.addOrReplaceChild("leg_right", CubeListBuilder.create()
			// thigh_right
			.texOffs(0, 62).addBox(-2.5F, 0.0F, -3.5F, 5.0F, 7.0F, 7.0F),
			PartPose.offsetAndRotation(-4.5F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		legRight.addOrReplaceChild("shin_right", CubeListBuilder.create()
			// knee_right
			.texOffs(17, 96).addBox(-2.5F, -1.0F, -4.0F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.02F))
			// shin_right_core
			.texOffs(75, 62).addBox(-2.0F, 0.0F, -3.0F, 4.0F, 6.0F, 6.0F)
			// foot_right
			.texOffs(0, 77).addBox(-3.0F, 6.0F, -6.0F, 6.0F, 2.0F, 9.0F),
			PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		PartDefinition legLeft = root.addOrReplaceChild("leg_left", CubeListBuilder.create()
			// thigh_left
			.texOffs(25, 62).addBox(-2.5F, 0.0F, -3.5F, 5.0F, 7.0F, 7.0F),
			PartPose.offsetAndRotation(4.5F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		legLeft.addOrReplaceChild("shin_left", CubeListBuilder.create()
			// knee_left
			.texOffs(30, 96).addBox(-2.5F, -1.0F, -4.0F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0.02F))
			// shin_left_core
			.texOffs(96, 62).addBox(-2.0F, 0.0F, -3.0F, 4.0F, 6.0F, 6.0F)
			// foot_left
			.texOffs(31, 77).addBox(-3.0F, 6.0F, -6.0F, 6.0F, 2.0F, 9.0F),
			PartPose.offsetAndRotation(0.0F, 7.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 128, 128);
		// END GENERATED LAYER
	}

	private static float ease(final float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	@Override
	public void setupAnim(final GateSentryRenderState state) {
		super.setupAnim(state);
		float awake = 1.0F - state.kneel;
		float pos = state.walkAnimationPos * 0.5F;
		float speed = Math.min(1.0F, state.walkAnimationSpeed) * awake;
		float age = state.moveAge;

		// how the move it is in bends it: its arms, its lean, how low it is
		float arms = 0.0F;
		float forearms = 0.0F;
		float lean = 0.0F;
		float low = 0.0F;
		float crouch = 0.0F;
		float vent = 0.0F;
		boolean running = false;
		switch (state.move) {
			case SLAM_WINDUP -> {
				float up = ease(age / (GateSentry.SLAM_WINDUP * 0.7F));
				arms = -2.75F * up;
				forearms = -0.35F * up;
				lean = -0.3F * up;
				// it trembles as it holds them up
				lean += Mth.sin(state.ageInTicks * 2.2F) * 0.015F * up;
			}
			case SLAM -> {
				float down = Mth.clamp(age / GateSentry.SLAM_DOWN, 0.0F, 1.0F);
				arms = Mth.lerp(down, -2.75F, -1.05F);
				forearms = Mth.lerp(down, -0.35F, 0.0F);
				lean = Mth.lerp(down, -0.3F, 0.5F);
				low = 2.5F * down;
				crouch = 0.4F * down;
			}
			case RECOVER -> {
				float held = ease((GateSentry.RECOVER - age) / 10.0F);
				arms = -1.05F * held;
				lean = 0.5F * held;
				low = 2.5F * held;
				crouch = 0.4F * held;
				vent = held;
			}
			case RUSH_WINDUP -> {
				float set = ease(age / (GateSentry.RUSH_WINDUP * 0.6F));
				arms = 0.75F * set;
				lean = 0.45F * set;
				low = 2.0F * set;
				crouch = 0.6F * set;
			}
			case RUSH -> {
				arms = 0.85F;
				lean = 0.55F;
				low = 1.0F;
				running = true;
			}
			case STUCK -> {
				float held = ease((GateSentry.STUCK - age) / 10.0F);
				arms = -0.6F * held;
				lean = 0.6F * held;
				low = 1.5F * held;
				crouch = 0.3F * held;
				this.head.zRot += Mth.sin(age * 0.5F) * 0.12F * held;
			}
			default -> {
			}
		}

		// walking: heavy steps, rolling from side to side; running: bent low, fast
		float step = running ? Mth.cos(state.ageInTicks * 0.9F) * 0.9F : Mth.cos(pos) * 0.55F * speed;
		this.legRight.xRot += step;
		this.legLeft.xRot -= step;
		this.shinRight.xRot += Math.max(0.0F, -step) * 0.6F;
		this.shinLeft.xRot += Math.max(0.0F, step) * 0.6F;
		this.body.zRot += Mth.sin(pos) * 0.05F * speed;
		this.body.y += Math.abs(Mth.sin(pos)) * 0.7F * speed;
		this.armRight.xRot += -step * 0.25F + arms;
		this.armLeft.xRot += step * 0.25F + arms;
		this.forearmRight.xRot += forearms;
		this.forearmLeft.xRot += forearms;
		this.body.xRot += lean;
		this.body.y += low;
		this.legRight.xRot -= crouch;
		this.legLeft.xRot -= crouch;
		this.shinRight.xRot += crouch * 1.4F;
		this.shinLeft.xRot += crouch * 1.4F;
		this.legRight.z -= crouch * 2.0F;
		this.legLeft.z -= crouch * 2.0F;
		this.legRight.y += low * 0.6F;
		this.legLeft.y += low * 0.6F;

		// the crystal hums in its cage; venting after a slam, it is pushed out
		this.core.z += Mth.sin(state.ageInTicks * 0.25F) * 0.15F * awake + vent * 1.2F;

		// knelt: sat back on its shins, slumped forward, head down, fists on the ground before it
		float kneel = state.kneel;
		this.body.y += kneel * 7.0F;
		this.body.xRot += kneel * 0.3F;
		this.legRight.y += kneel * 7.0F;
		this.legLeft.y += kneel * 7.0F;
		this.shinRight.xRot += kneel * 1.5F;
		this.shinLeft.xRot += kneel * 1.5F;
		this.armRight.xRot -= kneel * 0.45F;
		this.armLeft.xRot -= kneel * 0.45F;
		this.forearmRight.xRot -= kneel * 0.3F;
		this.forearmLeft.xRot -= kneel * 0.3F;
		this.head.xRot += kneel * 0.4F;

		// the head follows its gaze, as far as its collar lets it
		this.head.yRot += state.yRot * Mth.DEG_TO_RAD * awake;
		this.head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F * awake;
	}
}
