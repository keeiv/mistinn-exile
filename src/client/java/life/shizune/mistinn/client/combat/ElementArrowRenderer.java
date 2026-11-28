package life.shizune.mistinn.client.combat;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.weapon.ElementArrow;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/** Draws a blast, frost or shock arrow in flight, each with its own head. */
public class ElementArrowRenderer extends ArrowRenderer<ElementArrow, ArrowRenderState> {
	private final Identifier texture;

	public ElementArrowRenderer(final EntityRendererProvider.Context context, final ElementArrow.Kind kind) {
		super(context);
		this.texture = Mistinn.id("textures/entity/projectiles/" + kind.id + ".png");
	}

	@Override
	public ArrowRenderState createRenderState() {
		return new ArrowRenderState();
	}

	@Override
	protected Identifier getTextureLocation(final ArrowRenderState state) {
		return this.texture;
	}
}
