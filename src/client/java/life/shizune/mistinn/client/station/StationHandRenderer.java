package life.shizune.mistinn.client.station;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.station.StationHand;
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

/** Draws a member of the staff in the player model, wide or slim armed as their skin was painted. */
public class StationHandRenderer extends HumanoidMobRenderer<StationHand, AvatarRenderState, PlayerModel> {
	private static final Map<StaffRole, PlayerSkin> SKINS = new EnumMap<>(StaffRole.class);
	private final PlayerModel wide;
	private final PlayerModel slim;

	public StationHandRenderer(final EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wide = this.model;
		this.slim = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new State();
	}

	private static StaffRole role(final AvatarRenderState state) {
		return state instanceof State staff ? staff.role : StaffRole.DOCK_WORKER_1;
	}

	@Override
	public void extractRenderState(final StationHand entity, final AvatarRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (state instanceof State staff) {
			staff.role = entity.role();
		}
		// the player model draws what the state's skin names, not the renderer's texture
		state.skin = skin(entity.role());
		state.showHat = true;
		state.showJacket = true;
		state.showLeftPants = true;
		state.showRightPants = true;
		state.showLeftSleeve = true;
		state.showRightSleeve = true;
	}

	/** This member's skin, as the player model reads it. */
	public static PlayerSkin skin(final StaffRole role) {
		return SKINS.computeIfAbsent(role, r -> PlayerSkin.insecure(new ClientAsset.ResourceTexture(r.texture(), r.texture()), null, null,
			r.slim ? PlayerModelType.SLIM : PlayerModelType.WIDE));
	}

	@Override
	public void submit(final AvatarRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera) {
		this.model = role(state).slim ? this.slim : this.wide;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(final AvatarRenderState state) {
		return role(state).texture();
	}

	/** The player model's state, and whose skin it wears. */
	public static final class State extends AvatarRenderState {
		public StaffRole role = StaffRole.DOCK_WORKER_1;
	}
}
