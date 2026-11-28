package life.shizune.mistinn.client.terra;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.terra.LandShark;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Draws a land shark. Swimming through the ground it is drawn sunk into it, so only its crystal fin stands above the
 * turf (the ground hides the rest); leaping, it is drawn whole.
 */
public class LandSharkRenderer extends MobRenderer<LandShark, LandSharkRenderState, LandSharkModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/terra/land_shark.png");
	/** How deep it is drawn in the ground while it swims, in blocks. */
	private static final float SUNK = 0.72F;

	public LandSharkRenderer(final EntityRendererProvider.Context context) {
		super(context, new LandSharkModel(context.bakeLayer(LandSharkModel.LAYER)), 0.0F);
	}

	@Override
	public LandSharkRenderState createRenderState() {
		return new LandSharkRenderState();
	}

	@Override
	public void extractRenderState(final LandShark entity, final LandSharkRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.out = entity.out(partialTicks);
	}

	@Override
	protected void scale(final LandSharkRenderState state, final PoseStack poseStack) {
		// under the ground but for its fin; out of it, whole
		poseStack.translate(0.0F, -(1.0F - state.out) * SUNK, 0.0F);
	}

	@Override
	public Identifier getTextureLocation(final LandSharkRenderState state) {
		return TEXTURE;
	}
}
