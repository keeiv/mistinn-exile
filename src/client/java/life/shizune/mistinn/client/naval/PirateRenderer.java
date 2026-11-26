package life.shizune.mistinn.client.naval;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import life.shizune.mistinn.naval.Pirate;
import life.shizune.mistinn.naval.PirateLook;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Draws a pirate in the player model, wide or slim armed as their skin was painted, the gun brought up two-handed to aim. */
public class PirateRenderer extends HumanoidMobRenderer<Pirate, AvatarRenderState, PlayerModel> {
	private static final Map<PirateLook, PlayerSkin> SKINS = new EnumMap<>(PirateLook.class);
	private final PlayerModel wide;
	private final PlayerModel slim;

	public PirateRenderer(final EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wide = this.model;
		this.slim = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new State();
	}

	private static PirateLook look(final AvatarRenderState state) {
		return state instanceof State pirate ? pirate.look : PirateLook.CUTLASS_1;
	}

	@Override
	public void extractRenderState(final Pirate entity, final AvatarRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (state instanceof State pirate) {
			pirate.look = entity.look();
		}
		state.skin = skin(entity.look());
		state.showHat = true;
		state.showJacket = true;
		state.showLeftPants = true;
		state.showRightPants = true;
		state.showLeftSleeve = true;
		state.showRightSleeve = true;
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(final Pirate entity, final HumanoidArm arm) {
		if (entity.aiming()) {
			// the gun up at the shoulder, both hands on it
			return HumanoidModel.ArmPose.CROSSBOW_HOLD;
		}
		return super.getArmPose(entity, arm);
	}

	/** This pirate's skin, as the player model reads it. */
	public static PlayerSkin skin(final PirateLook look) {
		return SKINS.computeIfAbsent(look, l -> PlayerSkin.insecure(new ClientAsset.ResourceTexture(l.texture(), l.texture()), null, null,
			l.slim ? PlayerModelType.SLIM : PlayerModelType.WIDE));
	}

	@Override
	public void submit(final AvatarRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera) {
		this.model = look(state).slim ? this.slim : this.wide;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(final AvatarRenderState state) {
		return look(state).texture();
	}

	/** The player model's state, and whose skin it wears. */
	public static final class State extends AvatarRenderState {
		public PirateLook look = PirateLook.CUTLASS_1;
	}
}
