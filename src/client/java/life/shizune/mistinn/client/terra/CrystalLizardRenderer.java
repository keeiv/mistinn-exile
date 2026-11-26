package life.shizune.mistinn.client.terra;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.terra.CrystalLizard;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Draws a crystal lizard, its eyes and the seams and tips of its shards glowing in the dark. */
public class CrystalLizardRenderer extends MobRenderer<CrystalLizard, CrystalLizardRenderState, CrystalLizardModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/terra/crystal_lizard.png");
	private static final RenderType GLOW = RenderTypes.eyes(Mistinn.id("textures/entity/terra/crystal_lizard_glow.png"));

	public CrystalLizardRenderer(final EntityRendererProvider.Context context) {
		super(context, new CrystalLizardModel(context.bakeLayer(CrystalLizardModel.LAYER)), 0.5F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public CrystalLizardRenderState createRenderState() {
		return new CrystalLizardRenderState();
	}

	@Override
	public void extractRenderState(final CrystalLizard entity, final CrystalLizardRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.still = entity.still(partialTicks);
		state.bite = entity.bite(partialTicks);
	}

	@Override
	public Identifier getTextureLocation(final CrystalLizardRenderState state) {
		return TEXTURE;
	}
}
