package life.shizune.mistinn.client.shore;

import life.shizune.mistinn.Mistinn;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

/** 燈奴 drawn in the zombie's frame, hooded, its lantern in its hand. */
public class LampThrallRenderer extends ZombieRenderer {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/lamp_thrall.png");

	public LampThrallRenderer(final EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public Identifier getTextureLocation(final ZombieRenderState state) {
		return TEXTURE;
	}
}
