package life.shizune.mistinn.client.path;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.path.ApostleMove;
import life.shizune.mistinn.path.RustApostle;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * 3412．鏽錨使徒 in the player's shape, wearing the skin of whoever is looking: the hunter is you, as you would be if no
 * one moved you. The armour they carry is not drawn. They move by their own choreography ({@link ApostleModel}); the
 * whole body is carried by a move's root (a roll's turn, a leap's rise and flip, a spin) here.
 */
public class RustApostleRenderer extends HumanoidMobRenderer<RustApostle, ApostleRenderState, ApostleModel> {
	/** For the reel: a move held at a tick on every apostle, instead of what they are doing. */
	public static @Nullable ApostleMove previewMove;
	public static float previewTicks;
	public static @Nullable WeaponStyle previewStyle;
	/** For the reel's calibration: the arms' tracks set outright. */
	public static float @Nullable [] previewArms;

	private final ApostleModel wide;
	private final ApostleModel slim;

	public RustApostleRenderer(final EntityRendererProvider.Context context) {
		super(context, new ApostleModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		this.wide = this.model;
		this.slim = new ApostleModel(context.bakeLayer(ModelLayers.PLAYER_SLIM));
	}

	/** The looker's own skin, as they chose it: not the tillers' clothes the way may put on them. */
	public static PlayerSkin own() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null) {
			return DefaultPlayerSkin.getDefaultSkin();
		}
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		if (info == null) {
			return DefaultPlayerSkin.get(mc.player.getUUID());
		}
		PathSkins.bypass = true;
		try {
			return info.getSkin();
		} finally {
			PathSkins.bypass = false;
		}
	}

	@Override
	public ApostleRenderState createRenderState() {
		return new ApostleRenderState();
	}

	@Override
	public void extractRenderState(final RustApostle entity, final ApostleRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.skin = own();
		state.style = WeaponStyle.of(entity.getMainHandItem());
		state.move = entity.move();
		state.drawn = entity.drawn();
		state.moveTicks = state.move == null ? 0.0F
			: Math.max(0.0F, (float) (entity.level().getGameTime() - entity.moveStart()) + partialTicks - 1.0F);
		if (previewMove != null) {
			state.move = previewMove;
			state.moveTicks = previewTicks;
		}
		if (previewStyle != null) {
			state.style = previewStyle;
			state.drawn = true;
		}
		state.pose();
		if (previewArms != null) {
			System.arraycopy(previewArms, 0, state.arms, 0, previewArms.length);
			state.posed = true;
			state.twoHanded = false;
		}
	}

	@Override
	protected void setupRotations(final ApostleRenderState state, final PoseStack pose, final float bodyRot, final float entityScale) {
		super.setupRotations(state, pose, bodyRot, entityScale);
		if (state.move != null) {
			float[] r = state.root;
			Poser.root(r[0], r[1], r[2], r[3], r[4], r[5], state.pivot, pose, 1, entityScale);
		}
	}

	@Override
	public void submit(final ApostleRenderState state, final PoseStack poseStack, final SubmitNodeCollector collector, final CameraRenderState camera) {
		this.model = state.skin.model() == PlayerModelType.SLIM ? this.slim : this.wide;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(final ApostleRenderState state) {
		return state.skin.body().texturePath();
	}
}
