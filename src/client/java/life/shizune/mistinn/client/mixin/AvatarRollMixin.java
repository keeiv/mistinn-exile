package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.client.move.RollClient;
import life.shizune.mistinn.stamina.RollService;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns a rolling player's whole body: it faces the way it rolls and tumbles forward about the
 * hips. Weak arms make the roll lopsided; with both arms gone it cannot complete the turn and
 * rolls over a shoulder. A dive throws the body flat. Also carries the worn mask, backpack and
 * rig to the renderer.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRollMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void mistinn$rollState(final Avatar entity, final AvatarRenderState state, final float partialTicks, final CallbackInfo ci) {
		state.setData(RollClient.KEY, RollClient.sample(entity.getId()));
		int mask = entity.getAttachedOrElse(life.shizune.mistinn.registry.MistinnAttachments.WORN_MASK, -1);
		state.setData(life.shizune.mistinn.client.gear.MaskLayer.KEY, mask >= 0 ? mask : null);
		int pack = entity.getAttachedOrElse(life.shizune.mistinn.registry.MistinnAttachments.WORN_BACKPACK, -1);
		state.setData(life.shizune.mistinn.client.gear.GearLayer.BACKPACK, pack >= 0 ? pack : null);
		int rig = entity.getAttachedOrElse(life.shizune.mistinn.registry.MistinnAttachments.WORN_RIG, -1);
		state.setData(life.shizune.mistinn.client.gear.GearLayer.RIG, rig >= 0 ? rig : null);
		// the local hunter's death is the scene's to draw, not vanilla's red tilt
		life.shizune.mistinn.client.death.DeathScene.Pose death = entity == net.minecraft.client.Minecraft.getInstance().player ? life.shizune.mistinn.client.death.DeathScene.pose() : null;
		state.setData(life.shizune.mistinn.client.death.DeathScene.KEY, death);
		if (death != null) {
			state.deathTime = 0.0F;
			state.hasRedOverlay = false;
		}
	}

	@ModifyVariable(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mistinn$faceTheRoll(final float bodyRot, final AvatarRenderState state) {
		RollClient.Pose pose = state.getData(RollClient.KEY);
		return pose == null ? bodyRot : pose.yaw();
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void mistinn$tumble(final AvatarRenderState state, final PoseStack pose, final float bodyRot, final float entityScale, final CallbackInfo ci) {
		life.shizune.mistinn.client.death.DeathScene.Pose death = state.getData(life.shizune.mistinn.client.death.DeathScene.KEY);
		if (death != null) {
			mistinn$fall(death, pose);
			return;
		}
		RollClient.Pose roll = state.getData(RollClient.KEY);
		if (roll == null) {
			return;
		}
		float t = roll.t();
		float hips = 0.9F * entityScale;
		if (roll.kind() == RollService.Kind.ROLL) {
			boolean armless = roll.leftArm() <= 0.0F && roll.rightArm() <= 0.0F;
			float eased = t * t * (3.0F - 2.0F * t);
			float turn = 360.0F * eased * (armless ? 0.75F : 1.0F);
			if (armless && t > 0.75F) {
				// rolled onto the shoulder, the body heaves itself back up
				turn = 270.0F + 90.0F * (t - 0.75F) / 0.25F;
			}
			float lopsided = roll.rightArm() - roll.leftArm();
			float lean = (armless ? 30.0F : 28.0F * lopsided) * Mth.sin(t * Mth.PI);
			pose.translate(0.0F, -0.35F * Mth.sin(t * Mth.PI) * entityScale, 0.0F);
			pose.translate(0.0F, hips, 0.0F);
			pose.rotateDegrees(Axis.ZP, lean);
			pose.rotateDegrees(Axis.XP, turn);
			pose.translate(0.0F, -hips, 0.0F);
		} else {
			// thrown flat, held there, then pushed back up
			float down = t < 0.35F ? t / 0.35F : t > 0.75F ? 1.0F - (t - 0.75F) / 0.25F : 1.0F;
			pose.translate(0.0F, 0.15F * entityScale, 0.0F);
			pose.rotateDegrees(Axis.XP, 85.0F * down);
		}
	}

	/**
	 * The body in the death scene. Killed by a creature: down onto one knee, leaning on a hand,
	 * then over onto its side. Any other death: a stagger, then face down. Gone with the smoke.
	 */
	private static void mistinn$fall(final life.shizune.mistinn.client.death.DeathScene.Pose death, final PoseStack pose) {
		float s = death.seconds();
		if (s >= life.shizune.mistinn.client.death.DeathScene.GONE) {
			pose.scale(0.0F, 0.0F, 0.0F);
			return;
		}
		float down = smooth(s / 0.6F);
		float fall = smooth((s - life.shizune.mistinn.client.death.DeathScene.FALL) / 0.55F);
		if (death.kneel()) {
			// sunk onto the knee, then raised to lie on the ground rather than in it
			pose.translate(0.0F, -0.42F * down * (1.0F - fall) + 0.3F * fall, 0.0F);
			// over onto the side, pivoting where the body meets the ground
			pose.rotateDegrees(Axis.ZP, death.side() * 88.0F * fall);
			pose.rotateDegrees(Axis.XP, -14.0F * down * (1.0F - fall));
		} else {
			float stagger = s < 0.9F ? Mth.sin(s * 9.0F) * 7.0F * (1.0F - s / 0.9F) : 0.0F;
			float forward = smooth((s - 0.9F) / 0.5F);
			pose.rotateDegrees(Axis.ZP, stagger);
			pose.rotateDegrees(Axis.XP, -88.0F * forward);
		}
	}

	private static float smooth(final float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}
}
