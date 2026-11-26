package life.shizune.mistinn.client.rift;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.rift.RiftHerald;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Draws the rift herald in its own model, the rift's light burning in its mask, its cracks, its crystal and its ring. */
public class RiftHeraldRenderer extends MobRenderer<RiftHerald, RiftHeraldRenderState, RiftHeraldModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/rift/rift_herald.png");
	private static final RenderType GLOW = RenderTypes.eyes(Mistinn.id("textures/entity/rift/rift_herald_glow.png"));

	public RiftHeraldRenderer(final EntityRendererProvider.Context context) {
		super(context, new RiftHeraldModel(context.bakeLayer(RiftHeraldModel.LAYER)), 0.8F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public RiftHeraldRenderState createRenderState() {
		return new RiftHeraldRenderState();
	}

	@Override
	public void extractRenderState(final RiftHerald entity, final RiftHeraldRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.castKind = entity.cast();
		state.cast = entity.casting(partialTicks);
		state.attack = entity.isSwinging() ? entity.getSwingAnimation(partialTicks) : 0.0F;
	}

	@Override
	public Identifier getTextureLocation(final RiftHeraldRenderState state) {
		return TEXTURE;
	}
}
