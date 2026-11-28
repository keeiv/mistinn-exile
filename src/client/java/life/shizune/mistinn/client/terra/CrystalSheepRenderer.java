package life.shizune.mistinn.client.terra;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.terra.CrystalSheep;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Draws a crystal sheep; a lamb is a little over half its size. */
public class CrystalSheepRenderer extends MobRenderer<CrystalSheep, CrystalSheepRenderState, CrystalSheepModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/terra/crystal_sheep.png");

	public CrystalSheepRenderer(final EntityRendererProvider.Context context) {
		super(context, new CrystalSheepModel(context.bakeLayer(CrystalSheepModel.LAYER)), 0.6F);
	}

	@Override
	public CrystalSheepRenderState createRenderState() {
		return new CrystalSheepRenderState();
	}

	@Override
	public void extractRenderState(final CrystalSheep entity, final CrystalSheepRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.shorn = entity.shorn();
		state.angry = entity.isAggressive();
	}

	@Override
	protected void scale(final CrystalSheepRenderState state, final PoseStack poseStack) {
		if (state.isBaby) {
			poseStack.scale(0.55F, 0.55F, 0.55F);
		}
	}

	@Override
	public Identifier getTextureLocation(final CrystalSheepRenderState state) {
		return TEXTURE;
	}
}
