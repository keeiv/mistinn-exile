package life.shizune.mistinn.client.rift;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.rift.RiftHound;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Draws a rift hound, the rift's light burning in its eyes, its cracks and the edges of its shards. */
public class RiftHoundRenderer extends MobRenderer<RiftHound, RiftHoundRenderState, RiftHoundModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/rift/rift_hound.png");
	private static final RenderType GLOW = RenderTypes.eyes(Mistinn.id("textures/entity/rift/rift_hound_glow.png"));

	public RiftHoundRenderer(final EntityRendererProvider.Context context) {
		super(context, new RiftHoundModel(context.bakeLayer(RiftHoundModel.LAYER)), 0.6F);
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return GLOW;
			}
		});
	}

	@Override
	public RiftHoundRenderState createRenderState() {
		return new RiftHoundRenderState();
	}

	@Override
	public void extractRenderState(final RiftHound entity, final RiftHoundRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.crouch = entity.crouch(partialTicks);
		state.lunge = entity.lunge(partialTicks);
		state.bite = entity.bite(partialTicks);
	}

	@Override
	public Identifier getTextureLocation(final RiftHoundRenderState state) {
		return TEXTURE;
	}
}
