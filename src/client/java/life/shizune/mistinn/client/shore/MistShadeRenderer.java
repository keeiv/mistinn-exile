package life.shizune.mistinn.client.shore;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.shore.MistShade;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * 霧影 drawn see-through on its own legless frame ({@link MistShadeModel}), as much of it as there is: it
 * comes out of the mist, wavers, thins away.
 */
public class MistShadeRenderer extends AbstractZombieRenderer<Zombie, ZombieRenderState, MistShadeModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/mist_shade.png");

	public MistShadeRenderer(final EntityRendererProvider.Context context) {
		// a shade is never a child: the baby model is the grown one
		super(context, new MistShadeModel(context.bakeLayer(MistShadeModel.LAYER)), new MistShadeModel(context.bakeLayer(MistShadeModel.LAYER)),
			ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), MistShadeModel::new),
			ArmorModelSet.bake(ModelLayers.ZOMBIE_BABY_ARMOR, context.getModelSet(), MistShadeModel::new));
		this.shadowRadius = 0.15F;
	}

	/** The zombie's state, and how much of the shade is there. */
	public static class State extends ZombieRenderState {
		public float presence = 1.0F;
	}

	@Override
	public ZombieRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(final Zombie entity, final ZombieRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (state instanceof State shade) {
			shade.presence = entity instanceof MistShade mist ? mist.presence(partialTicks) : 1.0F;
		}
	}

	@Override
	protected RenderType getRenderType(final ZombieRenderState state, final boolean bodyVisible, final boolean translucent, final boolean glowing) {
		return RenderTypes.entityTranslucent(TEXTURE);
	}

	@Override
	protected int getModelTint(final ZombieRenderState state) {
		float presence = state instanceof State shade ? shade.presence : 1.0F;
		return (int) (Mth.clamp(presence, 0.0F, 1.0F) * 255.0F) << 24 | 0xFFFFFF;
	}

	@Override
	public Identifier getTextureLocation(final ZombieRenderState state) {
		return TEXTURE;
	}
}
