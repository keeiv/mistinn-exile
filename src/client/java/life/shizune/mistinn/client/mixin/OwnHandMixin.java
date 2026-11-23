package life.shizune.mistinn.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.medical.TreatmentClient;
import life.shizune.mistinn.client.view.OwnArms;
import life.shizune.mistinn.client.view.OwnBody;
import life.shizune.mistinn.client.view.OwnHand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With the hunter's own body in view, the hand pass draws nothing: it only works out where each hand and what it
 * holds should be in front of the eye, for the body's own arms to reach there ({@link OwnArms}). Applied after the
 * mixins that move a held thing through a swing or a heavy blow, so every motion is in what it finds. A treatment worked
 * in the hands is laid out here too: the tool where its clip puts it, and the other arm held out to be worked on.
 */
@Mixin(value = FirstPersonHandsAndItemsRenderer.class, priority = 1100)
public abstract class OwnHandMixin {
	/** The view's space for the hand now being worked out: the pose before anything is moved for it. Render thread only. */
	@Unique
	private PoseStack.@Nullable Pose mistinn$hands;
	/** The treatment the hand now being worked out holds the tool of; null for any other hand. */
	@Unique
	private TreatmentClient.@Nullable InHands mistinn$work;
	/** How the tool of the treatment under way sat in the hand when plainly held, for after the last of a stack is used. */
	@Unique
	private @Nullable Matrix4f mistinn$held;
	/** The hand now being worked out was found, so the hand pass draws nothing for it. */
	@Unique
	private boolean mistinn$found;

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void mistinn$work(
		final PlayerRenderState playerState,
		final FirstPersonHandsAndItemsRenderState state,
		final float partialTicks,
		final float xRot,
		final InteractionHand hand,
		final float attack,
		final ItemStack itemStack,
		final float inverseArmHeight,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final CallbackInfo ci
	) {
		this.mistinn$work = null;
		this.mistinn$hands = null;
		this.mistinn$found = false;
		LocalPlayer player = Minecraft.getInstance().player;
		if (!mistinn$ours(playerState, state) || player == null) {
			return;
		}
		TreatmentClient.InHands work = TreatmentClient.inHands(player);
		if (work == null) {
			if (hand == InteractionHand.MAIN_HAND) {
				this.mistinn$held = null;
			}
			return;
		}
		int side = mistinn$side(playerState, work.hand());
		if (hand != work.hand()) {
			// the other arm is held out to be worked on: what it held is put away
			if (work.presents()) {
				ci.cancel();
			}
			return;
		}
		InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		OwnArms.Present present = TreatmentClient.other(work, side);
		if (present != null) {
			OwnArms.present(otherHand, present);
		}
		if (itemStack.is(work.item())) {
			this.mistinn$work = work;
			return;
		}
		Matrix4f held = this.mistinn$held;
		if (itemStack.isEmpty() && held != null) {
			// the last of the stack is used up: the hand finishes its motion without it, instead of hanging at once
			Matrix4f tool = TreatmentClient.tool(work, held, side);
			OwnArms.hold(hand, mistinn$hold(tool, TreatmentClient.grip(work.clip()), work));
			ci.cancel();
		}
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
	private void mistinn$settle(
		final PlayerRenderState playerState,
		final FirstPersonHandsAndItemsRenderState state,
		final float partialTicks,
		final float xRot,
		final InteractionHand hand,
		final float attack,
		final ItemStack itemStack,
		final float inverseArmHeight,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final CallbackInfo ci
	) {
		if (!mistinn$ours(playerState, state)) {
			return;
		}
		this.mistinn$hands = poseStack.last().copy();
		if (!itemStack.isEmpty()) {
			poseStack.translate(mistinn$side(playerState, hand) * OwnHand.VIEW_X, OwnHand.VIEW_Y, OwnHand.VIEW_Z);
		}
	}

	@Inject(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
	)
	private void mistinn$find(
		final PlayerRenderState playerState,
		final FirstPersonHandsAndItemsRenderState state,
		final float partialTicks,
		final float xRot,
		final InteractionHand hand,
		final float attack,
		final ItemStack itemStack,
		final float inverseArmHeight,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final CallbackInfo ci
	) {
		PoseStack.Pose hands = this.mistinn$hands;
		TreatmentClient.InHands work = this.mistinn$work;
		this.mistinn$work = null;
		if (hands == null) {
			return;
		}
		ItemStackRenderState item = hand == InteractionHand.MAIN_HAND ? state.mainHandRenderState : state.offHandRenderState;
		ItemLayersAccessor layers = (ItemLayersAccessor) item;
		if (layers.mistinn$layerCount() == 0) {
			return;
		}
		// from the item's model to the view's space: how the item is set in the hand, then everything that moved it
		ItemLayerAccessor layer = (ItemLayerAccessor) layers.mistinn$layers()[0];
		OwnHand.Grip grip = layer.mistinn$special() == null ? OwnHand.Grip.of(itemStack) : OwnHand.Grip.around(layer.mistinn$corners().get());
		if (grip == null) {
			return;
		}
		PoseStack.Pose display = new PoseStack.Pose();
		layer.mistinn$applyTransform(display);
		Matrix4f model = new Matrix4f(hands.pose()).invert().mul(poseStack.last().pose()).mul(display.pose());
		if (work != null) {
			// a treatment: the tool goes where its clip puts it, and the hand closes where the clip says it is held
			this.mistinn$held = new Matrix4f(model);
			OwnArms.hold(hand, mistinn$hold(TreatmentClient.tool(work, model, mistinn$side(playerState, hand)), TreatmentClient.grip(work.clip()), work));
		} else {
			OwnArms.hold(hand, mistinn$hold(model, grip, null));
		}
		this.mistinn$found = true;
	}

	/** What the hand pass found is drawn by the body's own hand, not here. */
	@WrapWithCondition(
		method = "submitArmWithItem",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
	)
	private boolean mistinn$drawItem(final ItemStackRenderState item, final PoseStack poseStack, final SubmitNodeCollector collector, final int light, final int overlay,
		final int outline) {
		boolean found = this.mistinn$found;
		this.mistinn$found = false;
		return !found;
	}

	/** An empty hand at work (a punch, a block placed with a bare hand): the body's own fist goes where the game's would. */
	@WrapWithCondition(
		method = "renderPlayerArm",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;renderPlayerHand(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;)V")
	)
	private boolean mistinn$drawBareArm(final FirstPersonHandsAndItemsRenderer renderer, final PoseStack poseStack, final SubmitNodeCollector collector, final int light,
		final HumanoidArm arm, final PlayerRenderState playerState) {
		PoseStack.Pose hands = this.mistinn$hands;
		if (hands == null || playerState.avatarRenderState == null) {
			return true;
		}
		// the game draws the arm from its shoulder, slanted a little outward
		int sign = arm == HumanoidArm.RIGHT ? 1 : -1;
		Matrix4f model = new Matrix4f(hands.pose()).invert().mul(poseStack.last().pose())
			.translate(-sign * 5.0F / 16.0F, 2.0F / 16.0F, 0.0F).rotateZ(sign * 0.1F);
		// raised into the view as far as a held thing is
		Vector3f fist = model.transformPosition(new Vector3f(-sign / 16.0F, OwnHand.REACH, 0.0F)).add(sign * OwnHand.VIEW_X, OwnHand.VIEW_Y, OwnHand.VIEW_Z);
		Vector3f front = model.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F)).normalize();
		InteractionHand hand = arm == playerState.avatarRenderState.mainArm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		OwnArms.hold(hand, new OwnArms.Hold(fist, front, null, null));
		return false;
	}

	/** What a hand holds, as found: where the fist closes on it and which way it runs on, and the tool's tip if it is one. */
	@Unique
	private static OwnArms.Hold mistinn$hold(final Matrix4f model, final OwnHand.Grip grip, final TreatmentClient.@Nullable InHands work) {
		Vector3f fist = model.transformPosition(new Vector3f(grip.x(), grip.y(), grip.z()));
		Vector3f along = model.transformDirection(new Vector3f(grip.alongX(), grip.alongY(), grip.alongZ())).normalize();
		Vector3f tip = work == null || !work.presents() ? null : model.transformPosition(TreatmentClient.point(work.clip()));
		return new OwnArms.Hold(fist, along, model, tip);
	}

	/** Whether this hand pass is the hunter's own body's to stand in for: their body is in view and nothing in hand is a map. */
	@Unique
	private static boolean mistinn$ours(final PlayerRenderState playerState, final FirstPersonHandsAndItemsRenderState state) {
		return OwnBody.active() && playerState.avatarRenderState != null && !playerState.avatarRenderState.isInvisible
			&& !state.mainHandItem.has(DataComponents.MAP_ID) && !state.offHandItem.has(DataComponents.MAP_ID);
	}

	/** 1 if {@code hand} is the hunter's right arm, -1 if their left. */
	@Unique
	private static int mistinn$side(final PlayerRenderState playerState, final InteractionHand hand) {
		HumanoidArm main = playerState.avatarRenderState.mainArm;
		return (hand == InteractionHand.MAIN_HAND ? main : main.getOpposite()) == HumanoidArm.RIGHT ? 1 : -1;
	}
}
