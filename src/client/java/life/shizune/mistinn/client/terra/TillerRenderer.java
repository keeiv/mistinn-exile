package life.shizune.mistinn.client.terra;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.terra.Tiller;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Draws one of the crystal tillers in the player model, slim or wide armed as their skin was painted. */
public class TillerRenderer extends HumanoidMobRenderer<Tiller, AvatarRenderState, PlayerModel> {
	private static final Map<Tiller.Role, PlayerSkin> SKINS = new EnumMap<>(Tiller.Role.class);
	private final PlayerModel wide;
	private final PlayerModel slim;

	public TillerRenderer(final EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wide = this.model;
		this.slim = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
	}

	private static Identifier texture(final Tiller.Role role) {
		return Mistinn.id("textures/entity/terra/" + role.id + ".png");
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new State();
	}

	private static Tiller.Role role(final AvatarRenderState state) {
		return state instanceof State tiller ? tiller.role : Tiller.Role.TILLER_1;
	}

	@Override
	public void extractRenderState(final Tiller entity, final AvatarRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		Tiller.Role role = entity.role();
		if (state instanceof State tiller) {
			tiller.role = role;
		}
		state.skin = SKINS.computeIfAbsent(role, r -> PlayerSkin.insecure(new ClientAsset.ResourceTexture(texture(r), texture(r)), null, null,
			r.slim ? PlayerModelType.SLIM : PlayerModelType.WIDE));
		state.showHat = true;
		state.showJacket = true;
		state.showLeftPants = true;
		state.showRightPants = true;
		state.showLeftSleeve = true;
		state.showRightSleeve = true;
	}

	@Override
	public void submit(final AvatarRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera) {
		this.model = role(state).slim ? this.slim : this.wide;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(final AvatarRenderState state) {
		return texture(role(state));
	}

	/** The player model's state, and who it is. */
	public static final class State extends AvatarRenderState {
		public Tiller.Role role = Tiller.Role.TILLER_1;
	}
}
