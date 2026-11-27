package life.shizune.mistinn.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The first-person view follows the swing a little: it leans into the strike and is carried
 * a few degrees past it by the follow-through (each clip's {@code camera} tracks), and a blow
 * that lands knocks it once, at the moment the weapon meets the target. Rolls and dives add
 * their own motion.
 * Hunters can turn the swing motion off; the setting is kept in the config folder.
 */
public final class CameraSwing {
	/** Time of the last hit, for the jolt. */
	private static long hitAt = Long.MIN_VALUE;
	/** Whether the coming jolt is a heavy blow's. */
	private static boolean heavyHit;

	private CameraSwing() {
	}

	public static boolean enabled() {
		return life.shizune.mistinn.client.ClientSettings.on(life.shizune.mistinn.client.ClientSettings.SWING_CAMERA);
	}

	public static void toggle() {
		life.shizune.mistinn.client.ClientSettings.set(life.shizune.mistinn.client.ClientSettings.SWING_CAMERA, !enabled());
	}

	/**
	 * Called when the local player's blow lands. The hit is dealt on the click, but the weapon
	 * only meets the target at the swing's contact beat: the jolt waits for it.
	 */
	public static void hit() {
		heavyHit = false;
		long delay = 0L;
		LocalPlayer player = Minecraft.getInstance().player;
		WeaponStyle style = player == null ? null : WeaponStyle.of(player.getMainHandItem());
		if (style != null) {
			float ticks = WeaponSwings.contact(style) * player.getMainHandItem().getAttackAnimation().duration();
			delay = (long) (ticks * 50_000_000L);
		}
		hitAt = System.nanoTime() + delay;
	}

	/** Called when the local player lets a heavy blow go: a harder jolt at its contact. */
	public static void heavy(final WeaponStyle style) {
		int ticks = life.shizune.mistinn.weapon.HeavyService.shape(style, null).landTicks();
		hitAt = System.nanoTime() + ticks * 50_000_000L;
		heavyHit = true;
	}

	/** The local hunter's guard met a blow: a sharp jolt for a parry, a lighter one for a block. */
	public static void met(final boolean parried) {
		hitAt = System.nanoTime();
		heavyHit = parried;
	}

	/** Adds the swing and jolt to the view; called while vanilla tilts the view for damage. */
	public static void apply(final PoseStack pose, final float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		// rolls and dives move the view whatever the swing setting: it is where the body goes
		float[] extra = life.shizune.mistinn.client.move.RollClient.viewOffsets();
		// a slide takes the eye down near the floor
		float drop = life.shizune.mistinn.client.move.RollClient.viewDrop();
		if (drop > 0.0F) {
			pose.translate(0.0F, drop, 0.0F);
		}
		if (enabled()) {
			life.shizune.mistinn.client.view.ViewMotion.apply(pose, player, partialTicks);
		}
		float yaw = extra[0];
		float pitch = extra[1];
		float roll = extra[2];
		WeaponStyle style = WeaponStyle.of(player.getMainHandItem());
		float p = WeaponSwings.progress(player.getSwingAnimation(partialTicks));
		if (enabled() && style != null && p > 0.0F) {
			int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
			float[] sway = {0.0F, 0.0F, 0.0F};
			WeaponSwings.camera(style, p, side, sway);
			yaw += sway[0];
			pitch += sway[1];
			roll += sway[2];
		}
		HeavyClient.Pose heavyPose = HeavyClient.sample(player, partialTicks);
		if (enabled() && heavyPose != null) {
			int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
			float[] sway = {0.0F, 0.0F, 0.0F};
			HeavyClient.camera(heavyPose, side, sway);
			yaw += sway[0];
			pitch += sway[1];
			roll += sway[2];
		}
		// a hunter at work on themself looks down at their hands (or throws their head back to swallow)
		life.shizune.mistinn.client.medical.TreatmentClient.Pose work = life.shizune.mistinn.client.medical.TreatmentClient.sample(player.getId());
		if (work != null && life.shizune.mistinn.client.view.OwnBody.active()) {
			float[] look = life.shizune.mistinn.client.medical.TreatmentClient.camera(work, player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1);
			// never past straight down or straight up, wherever they were looking
			pitch += Mth.clamp(look[0], -80.0F - player.getXRot(), 80.0F - player.getXRot());
			yaw += look[1];
		}
		float since = (System.nanoTime() - hitAt) / 1.0E9F;
		if (enabled() && since >= 0.0F && since < 0.25F) {
			// the blade bites: the view is knocked down once and settles, without ringing
			float k = 1.0F - since / 0.25F;
			float kick = Mth.sin(Math.min(since / 0.04F, 1.0F) * Mth.HALF_PI) * k * k;
			float strength = heavyHit ? 2.6F : 1.0F;
			pitch += 1.6F * kick * strength;
			roll += 0.6F * kick * strength;
		} else if (since >= 0.25F) {
			heavyHit = false;
		}
		if (yaw != 0.0F || pitch != 0.0F || roll != 0.0F) {
			pose.rotateDegrees(Axis.ZP, roll);
			pose.rotateDegrees(Axis.XP, pitch);
			pose.rotateDegrees(Axis.YP, yaw);
		}
	}
}
