package life.shizune.mistinn.client.shore;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.shore.SaltCorpse;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HuskRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * 鹽屍 drawn as a husk in its own salt-crusted skin. Knocked down, or lying dormant, it lies on
 * its side as the dead do, and turns back up onto its feet when it rises.
 */
public class SaltCorpseRenderer extends HuskRenderer {
	private static final Identifier TEXTURE = Mistinn.id("textures/entity/salt_corpse.png");

	public SaltCorpseRenderer(final EntityRendererProvider.Context context) {
		super(context);
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
