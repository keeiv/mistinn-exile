package life.shizune.mistinn.client.keeper;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.keeper.Snuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/**
 * 熄燈人 drawn with its own model: the lantern is its head. The lantern in the head slot is not
 * drawn; it only says which flame burns (a lantern while lit, a soul lantern once the room goes
 * dark), and the glow layer lights the flame in that colour. The hook is its held item.
 */
public class SnufferRenderer extends HumanoidMobRenderer<Snuffer, SnufferRenderState, SnufferModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/snuffer.png");
	private static final RenderType FLAME = RenderTypes.eyes(Mistinn.id("textures/entity/snuffer_glow.png"));
	private static final RenderType SOUL_FLAME = RenderTypes.eyes(Mistinn.id("textures/entity/snuffer_glow_soul.png"));

	public SnufferRenderer(final EntityRendererProvider.Context context) {
		super(context, new SnufferModel(context.bakeLayer(SnufferModel.LAYER)), 0.7F);
		this.addLayer(new FlameLayer(this));
	}

	@Override
	public SnufferRenderState createRenderState() {
		return new SnufferRenderState();
	}

	@Override
	public void extractRenderState(final Snuffer entity, final SnufferRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.soulFlame = entity.getItemBySlot(EquipmentSlot.HEAD).is(Items.SOUL_LANTERN);
		var move = entity.currentMove();
		state.move = move == null ? null : move.id();
		state.moveLength = move == null ? 0 : move.length();
		// a held move (in the air, waiting to land) keeps its pose instead of creeping on between ticks
		state.moveTicks = entity.moveTicks() + (entity.moveHeld() ? 0.0F : partialTicks * entity.moveRate());
		// standing with nothing to do, it now and then tilts its lantern, shifts its weight or heaves a breath
		boolean still = move == null && state.walkAnimationSpeed < 0.05F;
		state.idle = still ? SnufferModel.idle(entity.getId(), state.ageInTicks) : null;
		state.idleTicks = SnufferModel.idleTicks(state.ageInTicks);
		state.turnSpeed = net.minecraft.util.Mth.wrapDegrees(entity.yBodyRot - entity.yBodyRotO);
		// the model already is the lantern
		state.headItem.clear();
	}

	/** The whole body sinks into the slam's crouch and the stagger's hunch. */
	@Override
	protected void setupRotations(final SnufferRenderState state, final PoseStack poseStack, final float bodyRot, final float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		Clip clip = SnufferModel.clip(state);
		if (clip != null) {
			Poser.root(clip, SnufferModel.progress(state), poseStack, 1, entityScale);
		}
	}

	@Override
	public Identifier getTextureLocation(final SnufferRenderState state) {
		return TEXTURE;
	}

	@Override
	protected void scale(final SnufferRenderState state, final PoseStack poseStack) {
		// about three blocks from the 39-pixel frame (ring to soles), bent to a little under, and narrower than tall
		poseStack.scale(1.12F, 1.22F, 1.12F);
	}

	/** 燈焰: the flame and the light on the lantern floor, full bright, warm or cold. */
	private static final class FlameLayer extends RenderLayer<SnufferRenderState, SnufferModel> {
		FlameLayer(final RenderLayerParent<SnufferRenderState, SnufferModel> parent) {
			super(parent);
		}

		@Override
		public void submit(final PoseStack poseStack, final SubmitNodeCollector collector, final int light, final SnufferRenderState state, final float yRot, final float xRot) {
			collector.order(1).submitModel(this.getParentModel(), state, poseStack, state.soulFlame ? SOUL_FLAME : FLAME, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		}
	}
}
