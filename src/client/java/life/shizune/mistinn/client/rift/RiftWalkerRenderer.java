package life.shizune.mistinn.client.rift;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.rift.RiftWalker;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** A rift walker in the slim player model, drawn a little too tall, its cracks glowing. */
public class RiftWalkerRenderer extends HumanoidMobRenderer<RiftWalker, AvatarRenderState, PlayerModel> {
	private static final Identifier[] SKINS = new Identifier[RiftWalker.LOOKS];
	private static final Identifier[] GLOWS = new Identifier[RiftWalker.LOOKS];
	private static final PlayerSkin[] PLAYER_SKINS = new PlayerSkin[RiftWalker.LOOKS];
	/** How much taller than a person it stands. */
	public static final float TALL = 1.16F;

	static {
		for (int i = 0; i < RiftWalker.LOOKS; i++) {
			SKINS[i] = Mistinn.id("textures/entity/rift/rift_walker_" + (i + 1) + ".png");
			GLOWS[i] = Mistinn.id("textures/entity/rift/rift_walker_" + (i + 1) + "_glow.png");
		}
	}

	public RiftWalkerRenderer(final EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.5F);
		this.addLayer(new RiftGlowLayer(this, state -> GLOWS[look(state)]));
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new State();
	}

	private static int look(final AvatarRenderState state) {
		return state instanceof State walker ? walker.look : 0;
	}

	@Override
	public void extractRenderState(final RiftWalker entity, final AvatarRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		int look = Math.floorMod(entity.look(), RiftWalker.LOOKS);
		if (state instanceof State walker) {
			walker.look = look;
		}
		if (PLAYER_SKINS[look] == null) {
			PLAYER_SKINS[look] = PlayerSkin.insecure(new ClientAsset.ResourceTexture(SKINS[look], SKINS[look]), null, null, PlayerModelType.SLIM);
		}
		state.skin = PLAYER_SKINS[look];
		state.showHat = true;
		state.showJacket = true;
		state.showLeftPants = true;
		state.showRightPants = true;
		state.showLeftSleeve = true;
		state.showRightSleeve = true;
	}

	@Override
	protected void scale(final AvatarRenderState state, final PoseStack poseStack) {
		poseStack.scale(TALL, TALL, TALL);
	}

	@Override
	public Identifier getTextureLocation(final AvatarRenderState state) {
		return SKINS[look(state)];
	}

	/** The player model's state, and which look it wears. */
	public static final class State extends AvatarRenderState {
		public int look;
	}
}
