package life.shizune.mistinn.client.terra;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.terra.GateSentry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws a gate sentry: its visor and its crystal lit while it is awake, dark while it kneels, flickering as it rises. */
public class GateSentryRenderer extends MobRenderer<GateSentry, GateSentryRenderState, GateSentryModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/terra/gate_sentry.png");
	private static final RenderType GLOW = RenderTypes.eyes(Mistinn.id("textures/entity/terra/gate_sentry_glow.png"));

	public GateSentryRenderer(final EntityRendererProvider.Context context) {
		super(context, new GateSentryModel(context.bakeLayer(GateSentryModel.LAYER)), 0.9F);
		this.addLayer(new LightLayer(this));
	}

	@Override
	public GateSentryRenderState createRenderState() {
		return new GateSentryRenderState();
	}

	@Override
	public void extractRenderState(final GateSentry entity, final GateSentryRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.kneel = entity.kneel(partialTicks);
		state.move = entity.move();
		state.moveAge = entity.moveAge(partialTicks);
	}

	@Override
	public Identifier getTextureLocation(final GateSentryRenderState state) {
		return TEXTURE;
	}

	/** Its lights: dark while it kneels; as it rises they catch, flickering, then hold. */
	private static final class LightLayer extends RenderLayer<GateSentryRenderState, GateSentryModel> {
		LightLayer(final RenderLayerParent<GateSentryRenderState, GateSentryModel> parent) {
			super(parent);
		}

		@Override
		public void submit(final PoseStack poseStack, final SubmitNodeCollector collector, final int light, final GateSentryRenderState state,
			final float yRot, final float xRot) {
			if (state.kneel > 0.95F) {
				return;
			}
			if (state.kneel > 0.25F && ((int) (state.ageInTicks * 2.0F)) % 3 == 0) {
				return;
			}
			collector.order(1).submitModel(this.getParentModel(), state, poseStack, GLOW, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		}
	}
}
