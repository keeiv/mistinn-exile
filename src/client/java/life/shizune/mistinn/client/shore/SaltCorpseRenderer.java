package life.shizune.mistinn.client.shore;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.shore.SaltCorpse;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * 鹽屍 drawn on its own dried, salt-grown frame ({@link SaltCorpseModel}). Knocked down, or lying dormant,
 * it lies on its side as the dead do, and turns back up onto its feet when it rises.
 */
public class SaltCorpseRenderer extends AbstractZombieRenderer<Zombie, ZombieRenderState, SaltCorpseModel> {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/salt_corpse.png");

	public SaltCorpseRenderer(final EntityRendererProvider.Context context) {
		// a salt corpse is never a child: the baby model is the grown one
		super(context, new SaltCorpseModel(context.bakeLayer(SaltCorpseModel.LAYER)), new SaltCorpseModel(context.bakeLayer(SaltCorpseModel.LAYER)),
			ArmorModelSet.bake(ModelLayers.HUSK_ARMOR, context.getModelSet(), SaltCorpseModel::new),
			ArmorModelSet.bake(ModelLayers.HUSK_BABY_ARMOR, context.getModelSet(), SaltCorpseModel::new));
	}

	/** The husk's state, and how far over the corpse lies. */
	public static class State extends ZombieRenderState {
		public float lying;
	}

	@Override
	public ZombieRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(final Zombie entity, final ZombieRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (state instanceof State corpse) {
			corpse.lying = entity instanceof SaltCorpse salt ? salt.lying(partialTicks) : 0.0F;
		}
	}

	@Override
	protected void setupRotations(final ZombieRenderState state, final PoseStack poseStack, final float bodyRot, final float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (state instanceof State corpse && corpse.lying > 0.0F) {
			// over onto its side about its feet, as a creature falls when it dies
			poseStack.rotateDegrees(Axis.ZP, corpse.lying * this.getFlipDegrees());
		}
	}

	@Override
	public Identifier getTextureLocation(final ZombieRenderState state) {
		return TEXTURE;
	}
}
