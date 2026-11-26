package life.shizune.mistinn.client.terra;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.terra.CrystalDrake;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Draws a crystal drake, its eyes, core gem and seams of light glowing in the dark. */
public class CrystalDrakeRenderer extends MobRenderer<CrystalDrake, CrystalDrakeRenderState, CrystalDrakeModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/terra/crystal_drake.png");
	private static final RenderType GLOW = RenderTypes.eyes(Mistinn.id("textures/entity/terra/crystal_drake_glow.png"));

	public CrystalDrakeRenderer(final EntityRendererProvider.Context context) {
		super(context, new CrystalDrakeModel(context.bakeLayer(CrystalDrakeModel.LAYER)), 1.4F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public CrystalDrakeRenderState createRenderState() {
		return new CrystalDrakeRenderState();
	}

	@Override
	public void extractRenderState(final CrystalDrake entity, final CrystalDrakeRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.aloft = entity.aloft(partialTicks);
		state.swoop = entity.swoop(partialTicks);
		state.bite = entity.bite(partialTicks);
		// pitched along its flight: nose down into a dive, up as it climbs
		double rise = entity.getDeltaMovement().y;
		double across = entity.getDeltaMovement().horizontalDistance();
		state.dive = state.aloft * Mth.clamp((float) -Math.atan2(rise, Math.max(0.05, across)), -0.6F, 0.9F);
	}

	@Override
	public Identifier getTextureLocation(final CrystalDrakeRenderState state) {
		return TEXTURE;
	}
}
