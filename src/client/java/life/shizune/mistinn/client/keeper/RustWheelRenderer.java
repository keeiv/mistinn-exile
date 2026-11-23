package life.shizune.mistinn.client.keeper;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.keeper.RustWheel;
import life.shizune.mistinn.keeper.WheelMoves;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * 鏽輪 drawn with its own model. Winding up for a run it rocks back on its yoke; tipping onto its rim for the sweep it
 * leans over; sinking for the hop it squats; stuck, it shudders against the stone.
 */
public class RustWheelRenderer extends MobRenderer<RustWheel, RustWheelRenderState, RustWheelModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/rust_wheel.png");

	public RustWheelRenderer(final EntityRendererProvider.Context context) {
		super(context, new RustWheelModel(context.bakeLayer(RustWheelModel.LAYER)), 1.4F);
	}

	@Override
	public RustWheelRenderState createRenderState() {
		return new RustWheelRenderState();
	}

	@Override
	public void extractRenderState(final RustWheel entity, final RustWheelRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.turned = entity.turned(partialTicks);
		var move = entity.currentMove();
		state.move = move == null ? null : move.id();
		state.moveTicks = entity.moveTicks() + (entity.moveHeld() ? 0.0F : partialTicks);
		state.moveWindup = move == null ? 0 : move.windup();
		state.stuck = move != null && "stuck".equals(move.id());
		state.broken = entity.broken();
		state.momentum = entity.momentum();
	}

	@Override
	protected void setupRotations(final RustWheelRenderState state, final PoseStack poseStack, final float bodyRot, final float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (state.move == null) {
			return;
		}
		float windup = Math.max(1, state.moveWindup);
		float t = Mth.clamp(state.moveTicks / windup, 0.0F, 1.0F);
		if (WheelMoves.spinsUp(state.move) && state.moveTicks < windup) {
			// rocked back onto its yoke, the rim lifting off the floor in front
			poseStack.rotateDegrees(Axis.XP, (-10.0F * Mth.sin(t * Mth.PI * 0.5F)));
		} else if ("rim_sweep".equals(state.move)) {
			float lean = state.moveTicks < windup ? t : Math.max(0.0F, 1.0F - (state.moveTicks - windup) / 10.0F);
			poseStack.rotateDegrees(Axis.ZP, (25.0F * lean));
		} else if ("hop_slam".equals(state.move) && state.moveTicks < windup) {
			// sinking on its springs
			poseStack.scale(1.0F + 0.06F * t, 1.0F - 0.12F * t, 1.0F + 0.06F * t);
		} else if (state.stuck) {
			poseStack.rotateDegrees(Axis.ZP, (2.5F * Mth.sin(state.ageInTicks * 2.3F)));
			poseStack.rotateDegrees(Axis.XP, (6.0F));
		}
	}

	@Override
	public Identifier getTextureLocation(final RustWheelRenderState state) {
		return TEXTURE;
	}
}
