package life.shizune.mistinn.client.keeper;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.keeper.SunkenBell;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/** 沉鐘 drawn with its own model; the clapper is part of its arm, not a held item. */
public class SunkenBellRenderer extends HumanoidMobRenderer<SunkenBell, SunkenBellRenderState, SunkenBellModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/sunken_bell.png");

	public SunkenBellRenderer(final EntityRendererProvider.Context context) {
		super(context, new SunkenBellModel(context.bakeLayer(SunkenBellModel.LAYER)), 0.9F);
	}

	@Override
	public SunkenBellRenderState createRenderState() {
		return new SunkenBellRenderState();
	}

	@Override
	public void extractRenderState(final SunkenBell entity, final SunkenBellRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		var move = entity.currentMove();
		state.move = move == null ? null : move.id();
		state.moveLength = move == null ? 0 : move.length();
		state.moveTicks = entity.moveTicks() + (entity.moveHeld() ? 0.0F : partialTicks);
		boolean still = move == null && state.walkAnimationSpeed < 0.05F;
		state.idle = still ? SunkenBellModel.idle(entity.getId(), state.ageInTicks) : null;
		state.idleTicks = SunkenBellModel.idleTicks(state.ageInTicks);
		state.cracked = entity.cracked();
		state.headItem.clear();
	}

	/** The whole body sinks into the toll's lift and the stagger's sag. */
	@Override
	protected void setupRotations(final SunkenBellRenderState state, final PoseStack poseStack, final float bodyRot, final float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		Clip clip = SunkenBellModel.clip(state);
		if (clip != null) {
			Poser.root(clip, SunkenBellModel.progress(state), poseStack, 1, entityScale);
		}
	}

	@Override
	public Identifier getTextureLocation(final SunkenBellRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(final SunkenBellRenderState state, final PoseStack poseStack) {
		// a little under three blocks from the 44-pixel frame, bell and all
		poseStack.scale(1.05F, 1.05F, 1.05F);
	}
}
