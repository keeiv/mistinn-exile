package life.shizune.mistinn.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.client.enemy.ExecutionView;
import life.shizune.mistinn.client.move.RollClient;
import life.shizune.mistinn.client.view.OwnBody;
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
 * Turns a rolling player's whole body: it faces the way it rolls and goes forward over its
 * curled back, staying on the ground through the turn ({@code anim/move/roll}). A weak arm
 * makes the roll lean to that side; with both arms gone it cannot complete the turn and rocks
 * back up off a shoulder. A dive throws the body forward flat onto hands and chest
 * ({@code anim/move/dive}). Also carries the worn mask, backpack and rig to the renderer.
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
		ExecutionView.Pose execution = ExecutionView.sample(entity, partialTicks);
		state.setData(ExecutionView.KEY, execution);
		// a downed hunter lies on their side: not the swimmer's crawl their low pose would otherwise draw
		Float down = death == null && execution == null ? life.shizune.mistinn.client.team.DownPose.sample(entity, partialTicks) : null;
		state.setData(life.shizune.mistinn.client.team.DownPose.KEY, down);
		if (down != null) {
			state.swimAmount = 0.0F;
			state.isCrouching = false;
		}
		if (death != null || execution != null) {
			state.deathTime = 0.0F;
			state.hasRedOverlay = false;
		}
		state.setData(life.shizune.mistinn.client.view.RunPose.KEY, death == null && execution == null && down == null && state.getData(RollClient.KEY) == null
			? life.shizune.mistinn.client.view.RunPose.sample(entity) : null);
		// through their own eyes: nothing worn on the head is drawn, nor what a hand holds up in front of the eye
		boolean own = OwnBody.inWorldPass() && entity == net.minecraft.client.Minecraft.getInstance().player && OwnBody.active();
		state.setData(OwnBody.KEY, own ? Boolean.TRUE : null);
		state.setData(OwnBody.HANDS_UP, null);
		float raised = own ? OwnBody.raised(partialTicks) : 0.0F;
		state.setData(OwnBody.RAISED, own ? raised : null);
		if (raised > 0.0F) {
			// working in front of the eye, the body turns square to where the hunter looks
			state.bodyRot += state.yRot * raised;
			state.yRot *= 1.0F - raised;
		}
		if (own) {
			state.headEquipment = net.minecraft.world.item.ItemStack.EMPTY;
			state.headItem.clear();
			state.heldOnHead.clear();
			state.wornHeadType = null;
			state.setData(life.shizune.mistinn.client.gear.MaskLayer.KEY, null);
			// a map held up is drawn by the game's own hands in front of the eye: those arms are left off the body
			OwnBody.Hands up = OwnBody.handsUp(net.minecraft.client.Minecraft.getInstance().player, partialTicks);
			state.setData(OwnBody.HANDS_UP, up);
			if (up != null) {
				boolean right = state.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT;
				(right ? state.rightHandItemState : state.leftHandItemState).clear();
				if (up.off()) {
					(right ? state.leftHandItemState : state.rightHandItemState).clear();
				}
			}
		}
	}

	/**
	 * The body stands a little behind the eye, so the hunter looks down their own chest and never out of it; while the
	 * arms work in front of the eye it steps up under it.
	 */
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void mistinn$standBack(final AvatarRenderState state, final PoseStack pose, final float bodyRot, final float entityScale, final CallbackInfo ci) {
		if (Boolean.TRUE.equals(state.getData(OwnBody.KEY))) {
			Float raised = state.getData(OwnBody.RAISED);
			pose.translate(0.0F, 0.0F, OwnBody.back(raised == null ? 0.0F : raised, state.xRot) * entityScale);
		}
		Float run = state.getData(life.shizune.mistinn.client.view.RunPose.KEY);
		if (run != null) {
			// leaning into the run about the feet, and down a little at each footfall
			float[] root = life.shizune.mistinn.client.view.RunPose.root(state, run);
			pose.translate(0.0F, -root[1] * entityScale, 0.0F);
			pose.rotateDegrees(Axis.XP, -root[0]);
		}
	}

	@ModifyVariable(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float mistinn$faceTheRoll(final float bodyRot, final AvatarRenderState state) {
		ExecutionView.Pose execution = state.getData(ExecutionView.KEY);
		if (execution != null) {
			return execution.yaw();
		}
		RollClient.Pose pose = state.getData(RollClient.KEY);
		return pose == null ? bodyRot : pose.yaw();
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void mistinn$tumble(final AvatarRenderState state, final PoseStack pose, final float bodyRot, final float entityScale, final CallbackInfo ci) {
		life.shizune.mistinn.client.death.DeathScene.Pose death = state.getData(life.shizune.mistinn.client.death.DeathScene.KEY);
		ExecutionView.Pose execution = state.getData(ExecutionView.KEY);
		if (execution != null && (death == null || death.seconds() < life.shizune.mistinn.client.death.DeathScene.GONE)) {
			ExecutionView.root(execution, pose, entityScale);
			return;
		}
		if (death != null) {
			mistinn$fall(death, pose);
			return;
		}
		Float down = state.getData(life.shizune.mistinn.client.team.DownPose.KEY);
		if (down != null) {
			life.shizune.mistinn.client.team.DownPose.root(down, pose, entityScale);
			return;
		}
		RollClient.Pose roll = state.getData(RollClient.KEY);
		if (roll == null) {
			return;
		}
		float t = roll.t();
		if (roll.kind() == RollService.Kind.SLIDE) {
			Poser.root(Clip.get("move/slide"), t, pose, 1, entityScale);
		} else if (roll.kind() == RollService.Kind.ROLL) {
			boolean armless = roll.leftArm() <= 0.0F && roll.rightArm() <= 0.0F;
			Poser.root(Clip.get(armless ? "move/roll_armless" : "move/roll"), t, pose, 1, entityScale);
			// a weak arm gives way under the turn: the roll leans over to that side
			float lopsided = roll.leftArm() - roll.rightArm();
			if (!armless && lopsided != 0.0F) {
				pose.translate(0.0F, 0.9F * entityScale, 0.0F);
				pose.rotateDegrees(Axis.ZP, -24.0F * lopsided * Mth.sin(t * Mth.PI));
				pose.translate(0.0F, -0.9F * entityScale, 0.0F);
			}
		} else {
			Poser.root(Clip.get("move/dive"), t, pose, 1, entityScale);
		}
	}

	/**
	 * The body in the death scene, from its clip: killed by a creature, it recoils, kneels and
	 * topples onto its side; any other death is a stagger and a fall onto the face. Gone with
	 * the smoke.
	 */
	private static void mistinn$fall(final life.shizune.mistinn.client.death.DeathScene.Pose death, final PoseStack pose) {
		float s = death.seconds();
		if (s >= life.shizune.mistinn.client.death.DeathScene.GONE) {
			pose.scale(0.0F, 0.0F, 0.0F);
			return;
		}
		Poser.root(Clip.get(death.kneel() ? "death/kneel" : "death/collapse"), s / life.shizune.mistinn.client.death.DeathScene.GONE, pose, death.side(), 1.0F);
	}
}
