package life.shizune.mistinn.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.tier.Tier;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 背包與胸掛: the worn backpack on the back and the chest rig over the front of the chest,
 * drawn on every player. Both hang from the body part, so they lean, twist and crouch with it
 * (through swings, rolls and falls too), and sit just outside a chestplate (which stands one
 * pixel off the body). The backpack bounces with the stride. Each tier has its own shape:
 * the backpack grows and gains pockets, a bedroll and a frame; the rig goes from a bandolier and
 * a work belt to a harness with a row of magazine pouches.
 */
public class GearLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("gear"), "main");
	/** Which backpack and rig a player wears, by tier index; absent when none. */
	public static final RenderStateDataKey<Integer> BACKPACK = RenderStateDataKey.create(() -> "mistinn:backpack");
	public static final RenderStateDataKey<Integer> RIG = RenderStateDataKey.create(() -> "mistinn:rig");

	private static final int TIERS = Tier.values().length;
	/** Rig harness by tier from II: top of the pouch panel, its height, pouch count, pouch height. */
	private static final int[][] HARNESS = {{5, 5, 3, 3}, {4, 6, 3, 3}, {4, 6, 3, 4}, {4, 6, 4, 3}, {4, 6, 4, 3}};

	private final GearModel backpack;
	private final GearModel rig;

	public GearLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> parent, final EntityRendererProvider.Context context) {
		super(parent);
		this.backpack = new GearModel(context.bakeLayer(LAYER), "backpack_", BACKPACK);
		this.rig = new GearModel(context.bakeLayer(LAYER), "rig_", RIG);
	}

	/**
	 * One mesh holds every tier of both: each model shows only its own kind, and only the tier
	 * the player wears. Texture layout per tier is the vanilla box layout on a 64x32 sheet.
	 */
	public static LayerDefinition definition() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		for (int t = 0; t < TIERS; t++) {
			body.addOrReplaceChild("backpack_" + t, backpack(t), PartPose.ZERO);
			addRig(body, t);
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	private static CubeListBuilder backpack(final int tier) {
		CubeListBuilder cubes = CubeListBuilder.create();
		switch (tier) {
			case 0 -> cubes.texOffs(0, 0).addBox(-2.5F, 2.0F, 3.0F, 5, 7, 3).texOffs(0, 16).addBox(-1.0F, 1.0F, 4.0F, 2, 1, 1);
			case 1 -> cubes.texOffs(0, 0).addBox(-3.0F, 1.5F, 3.0F, 6, 8, 3);
			case 2 -> cubes.texOffs(0, 0).addBox(-3.5F, 1.0F, 3.0F, 7, 9, 3).texOffs(0, 16).addBox(-2.5F, 5.0F, 6.0F, 5, 4, 1);
			case 3, 4 -> cubes.texOffs(0, 0).addBox(-4.0F, 0.5F, 3.0F, 8, 10, 4).texOffs(0, 16).addBox(-3.0F, 5.0F, 7.0F, 6, 4, 1);
			default -> cubes.texOffs(0, 0).addBox(-4.0F, 0.5F, 3.0F, 8, 11, 4).texOffs(0, 16).addBox(-3.0F, 6.0F, 7.0F, 6, 4, 1);
		}
		if (tier == 3) {
			// 錨衛: two issued field pockets, fitted to the side of the pack
			cubes.texOffs(32, 0).addBox(-5.5F, 4.0F, 3.5F, 2, 4, 3);
			cubes.texOffs(44, 0).addBox(3.5F, 4.0F, 3.5F, 2, 4, 3);
		} else if (tier == 4) {
			// 霧行: a bedroll strapped across the top
			cubes.texOffs(32, 0).addBox(-4.5F, -1.5F, 3.5F, 9, 2, 3);
		} else if (tier == 5) {
			// 鐘守: an even metal frame
			cubes.texOffs(32, 8).addBox(-5.0F, -2.0F, 3.5F, 1, 14, 1);
			cubes.texOffs(36, 8).addBox(4.0F, -2.0F, 3.5F, 1, 14, 1);
		} else if (tier == 6) {
			// 鏽神: an uneven frame held against the lid with a lashed crosspiece
			cubes.texOffs(32, 8).addBox(-5.0F, -3.0F, 5.5F, 1, 15, 1);
			cubes.texOffs(36, 8).addBox(4.0F, -1.0F, 5.5F, 1, 13, 1);
			cubes.texOffs(42, 22).addBox(-4.0F, 1.0F, 7.0F, 8, 1, 1);
		}
		return cubes;
	}

	private static void addRig(final PartDefinition body, final int tier) {
		if (tier == 0) {
			// 彈帶: one belt of cartridges slung from shoulder to hip, crossing front and back
			PartDefinition sling = body.addOrReplaceChild("rig_0", CubeListBuilder.create(), PartPose.ZERO);
			CubeListBuilder belt = CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -8.0F, -0.5F, 2, 16, 1);
			sling.addOrReplaceChild("front", belt, PartPose.offsetAndRotation(0.0F, 6.0F, -3.4F, 0.0F, 0.0F, 0.75F));
			sling.addOrReplaceChild("back", belt, PartPose.offsetAndRotation(0.0F, 6.0F, 3.4F, 0.0F, 0.0F, -0.75F));
			return;
		}
		CubeListBuilder cubes = CubeListBuilder.create();
		if (tier == 1) {
			// 腰掛: a belt round the waist with two pouches in front
			cubes.texOffs(0, 0).addBox(-4.5F, 9.0F, -3.0F, 9, 2, 6);
			cubes.texOffs(0, 16).addBox(-3.5F, 7.5F, -4.0F, 3, 3, 1);
			cubes.texOffs(0, 16).addBox(0.5F, 7.5F, -4.0F, 3, 3, 1);
		} else if (tier == 3) {
			// The square pouches sit low on the chest, clear of the armour's anchor.
			shoulderStraps(cubes, 6);
			cubes.texOffs(0, 0).addBox(-4.0F, 6.0F, -4.0F, 8, 4, 1);
			cubes.texOffs(40, 0).addBox(-4.0F, 6.5F, -5.0F, 2, 3, 1);
			cubes.texOffs(40, 0).addBox(2.0F, 6.5F, -5.0F, 2, 3, 1);
			cubes.texOffs(48, 0).addBox(-2.0F, 8.0F, -5.0F, 4, 2, 1);
		} else if (tier == 6) {
			// Bone pouches on the sides leave the breastbone visible.
			shoulderStraps(cubes, 6);
			cubes.texOffs(0, 0).addBox(-4.0F, 5.0F, -5.0F, 3, 5, 2);
			cubes.texOffs(10, 0).addBox(1.0F, 6.0F, -5.0F, 3, 4, 2);
			cubes.texOffs(40, 0).addBox(-3.0F, 9.0F, -3.8F, 6, 1, 1);
		} else {
			int[] h = HARNESS[tier - 2];
			int top = h[0];
			cubes.texOffs(0, 0).addBox(-4.0F, top, -4.0F, 8, h[1], 1);
			shoulderStraps(cubes, top);
			float step = 8.0F / h[2];
			for (int p = 0; p < h[2]; p++) {
				cubes.texOffs(40, 0).addBox(-4.0F + p * step + (step - 2.0F) / 2.0F, top + 1.0F, -5.0F, 2, h[3], 1);
			}
		}
		body.addOrReplaceChild("rig_" + tier, cubes, PartPose.ZERO);
	}

	private static void shoulderStraps(final CubeListBuilder cubes, final int frontLength) {
		for (float x : new float[] {-3.0F, 2.0F}) {
			cubes.texOffs(20, 0).addBox(x, 0.0F, -4.0F, 1, frontLength, 1);
			cubes.texOffs(0, 16).addBox(x, -1.0F, -4.0F, 1, 1, 8);
			cubes.texOffs(28, 0).addBox(x, 0.0F, 3.0F, 1, 9, 1);
		}
	}

	@Override
	public void submit(final PoseStack poseStack, final SubmitNodeCollector collector, final int light, final AvatarRenderState state, final float yRot, final float xRot) {
		Identifier pack = texture("backpack_", state.getData(BACKPACK));
		if (pack != null) {
			coloredCutoutModelCopyLayerRender(this.backpack, pack, poseStack, collector, light, state, -1, 2);
		}
		Identifier rig = texture("rig_", state.getData(RIG));
		if (rig != null) {
			coloredCutoutModelCopyLayerRender(this.rig, rig, poseStack, collector, light, state, -1, 2);
		}
	}

	private static @Nullable Identifier texture(final String kind, final @Nullable Integer tier) {
		if (tier == null || tier < 0 || tier >= TIERS) {
			return null;
		}
		return Mistinn.id("textures/entity/gear/" + kind + Tier.values()[tier].id() + ".png");
	}

	/**
	 * The body with one kind of gear on it. Models are drawn after the frame is collected, from
	 * the render state, so which tier shows is decided here rather than when submitting.
	 */
	private static final class GearModel extends HumanoidModel<AvatarRenderState> {
		private final ModelPart[] own = new ModelPart[TIERS];
		private final ModelPart[] other = new ModelPart[TIERS];
		private final RenderStateDataKey<Integer> key;

		GearModel(final ModelPart root, final String kind, final RenderStateDataKey<Integer> key) {
			super(root);
			this.key = key;
			String otherKind = kind.equals("rig_") ? "backpack_" : "rig_";
			for (int t = 0; t < TIERS; t++) {
				this.own[t] = this.body.getChild(kind + t);
				this.other[t] = this.body.getChild(otherKind + t);
			}
		}

		@Override
		public void setupAnim(final AvatarRenderState state) {
			super.setupAnim(state);
			Integer tier = state.getData(this.key);
			for (int t = 0; t < TIERS; t++) {
				this.own[t].visible = tier != null && tier == t;
				this.other[t].visible = false;
			}
			if (this.key == BACKPACK && tier != null && tier >= 0 && tier < TIERS) {
				// 晃動: the pack is thrown up off the back with each footfall and lands again; it
				// only ever swings away from the back, never into it
				float speed = Math.min(state.walkAnimationSpeed, 1.0F);
				float step = Math.abs(net.minecraft.util.Mth.sin(state.walkAnimationPos * 0.6662F - 0.4F));
				this.own[tier].xRot = 0.06F * step * speed;
				this.own[tier].y = -0.6F * step * speed;
			}
			this.head.visible = false;
			this.hat.visible = false;
			this.rightArm.visible = false;
			this.leftArm.visible = false;
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
		}
	}
}
