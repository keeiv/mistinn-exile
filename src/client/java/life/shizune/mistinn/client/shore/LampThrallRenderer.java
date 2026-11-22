package life.shizune.mistinn.client.shore;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.shore.LampThrall;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** 燈奴 drawn on its own bent, hooded frame ({@link LampThrallModel}), its lantern in its long hand. */
public class LampThrallRenderer extends AbstractZombieRenderer<Zombie, ZombieRenderState, LampThrallModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/lamp_thrall.png");

	public LampThrallRenderer(final EntityRendererProvider.Context context) {
		// a lamp thrall is never a child: the baby model is the grown one
		super(context, new LampThrallModel(context.bakeLayer(LampThrallModel.LAYER)), new LampThrallModel(context.bakeLayer(LampThrallModel.LAYER)),
			ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), LampThrallModel::new),
			ArmorModelSet.bake(ModelLayers.ZOMBIE_BABY_ARMOR, context.getModelSet(), LampThrallModel::new));
	}

	/** The zombie's state, and whether its lantern is flaring: the warning before it rushes. */
	public static class State extends ZombieRenderState {
		public boolean flaring;
	}

	@Override
	public ZombieRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(final Zombie entity, final ZombieRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (state instanceof State thrall) {
			thrall.flaring = entity instanceof LampThrall lamp && lamp.state() == LampThrall.FLARE;
		}
	}

	@Override
	public Identifier getTextureLocation(final ZombieRenderState state) {
		return TEXTURE;
	}
}
