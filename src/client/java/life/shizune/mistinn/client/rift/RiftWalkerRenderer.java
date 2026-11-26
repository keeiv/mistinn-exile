package life.shizune.mistinn.client.rift;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.rift.RiftWalker;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** A rift walker in its own hunched, long-armed model, in one of its looks, its cracks, eyes and crystal glowing. */
public class RiftWalkerRenderer extends MobRenderer<RiftWalker, RiftWalkerRenderState, RiftWalkerModel> {
	private static final Identifier[] SKINS = new Identifier[RiftWalker.LOOKS];
	private static final Identifier[] GLOWS = new Identifier[RiftWalker.LOOKS];

	static {
		for (int i = 0; i < RiftWalker.LOOKS; i++) {
			SKINS[i] = Mistinn.id("textures/entity/rift/rift_walker_" + (i + 1) + ".png");
			GLOWS[i] = Mistinn.id("textures/entity/rift/rift_walker_" + (i + 1) + "_glow.png");
		}
	}

	public RiftWalkerRenderer(final EntityRendererProvider.Context context) {
		super(context, new RiftWalkerModel(context.bakeLayer(RiftWalkerModel.LAYER)), 0.5F);
		this.addLayer(new RiftGlowLayer<>(this, state -> GLOWS[state.look]));
	}

	@Override
	public RiftWalkerRenderState createRenderState() {
		return new RiftWalkerRenderState();
	}

	@Override
	public void extractRenderState(final RiftWalker entity, final RiftWalkerRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.look = Math.floorMod(entity.look(), RiftWalker.LOOKS);
		state.attack = entity.isSwinging() ? entity.getSwingAnimation(partialTicks) : 0.0F;
		state.aloft = !entity.onGround() && entity.getTarget() != null;
		state.twitch = entity.getId() * 7.3F;
	}

	@Override
	public Identifier getTextureLocation(final RiftWalkerRenderState state) {
		return SKINS[state.look];
	}
}
