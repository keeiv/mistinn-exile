package life.shizune.mistinn.client.rift;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.rift.RiftHerald;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** The rift herald in the wide player model, half again as tall as a person, its crown and cracks glowing. */
public class RiftHeraldRenderer extends HumanoidMobRenderer<RiftHerald, AvatarRenderState, PlayerModel> {
	private static final Identifier SKIN = Mistinn.id("textures/entity/rift/rift_herald.png");
	private static final Identifier GLOW = Mistinn.id("textures/entity/rift/rift_herald_glow.png");
	private static PlayerSkin playerSkin;
	public static final float TALL = 1.45F;

	public RiftHeraldRenderer(final EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.8F);
		this.addLayer(new RiftGlowLayer(this, state -> GLOW));
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(final RiftHerald entity, final AvatarRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (playerSkin == null) {
			playerSkin = PlayerSkin.insecure(new ClientAsset.ResourceTexture(SKIN, SKIN), null, null, PlayerModelType.WIDE);
		}
		state.skin = playerSkin;
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
		return SKIN;
	}
}
