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
 * a work belt through chest rigs with straps over the shoulders and round the sides, each with
 * its own pouches, to a full vest and a rig of bones. Each rig is built after its inventory
 * icon, on a 64x64 sheet of its own.
 */
public class GearLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("gear"), "main");
	public static final ModelLayerLocation RIG_LAYER = new ModelLayerLocation(Mistinn.id("gear"), "rig");
	/** Which backpack and rig a player wears, by its index in Gear's lists; absent when none. */
	public static final RenderStateDataKey<Integer> BACKPACK = RenderStateDataKey.create(() -> "mistinn:backpack");
	public static final RenderStateDataKey<Integer> RIG = RenderStateDataKey.create(() -> "mistinn:rig");

	/** The standard issue's shapes, one per tier; the lines' come after them, from {@link GearLines}. */
	private static final int TIERS = Tier.values().length;
	private static final int BACKPACKS = life.shizune.mistinn.gear.Gear.BACKPACKS.size();
	private static final int RIGS = life.shizune.mistinn.gear.Gear.RIGS.size();

	private final GearModel backpack;
	private final GearModel rig;

	public GearLayer(final RenderLayerParent<AvatarRenderState, PlayerModel> parent, final EntityRendererProvider.Context context) {
		super(parent);
		this.backpack = new GearModel(context.bakeLayer(LAYER), "backpack_", BACKPACK, BACKPACKS);
		this.rig = new GearModel(context.bakeLayer(RIG_LAYER), "rig_", RIG, RIGS);
	}

	/**
	 * Every tier of backpack in one mesh: the model shows only the tier the player wears. Texture
	 * layout per tier is the vanilla box layout on a 64x32 sheet.
	 */
	public static LayerDefinition definition() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		for (int t = 0; t < BACKPACKS; t++) {
			body.addOrReplaceChild("backpack_" + t, t < TIERS ? backpack(t) : GearLines.backpack(t - TIERS), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	/**
	 * Every tier of chest rig in one mesh, each on a 64x64 sheet of its own in the vanilla box
	 * layout. A rig sits outside a chestplate: over the shoulders above its top, round the sides
	 * outside its flanks, its pouches in front of it.
	 */
	public static LayerDefinition rigDefinition() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		rig0(body.addOrReplaceChild("rig_0", CubeListBuilder.create(), PartPose.ZERO));
		rig1(body.addOrReplaceChild("rig_1", CubeListBuilder.create(), PartPose.ZERO));
		rig2(body.addOrReplaceChild("rig_2", CubeListBuilder.create(), PartPose.ZERO));
		rig3(body.addOrReplaceChild("rig_3", CubeListBuilder.create(), PartPose.ZERO));
		rig4(body.addOrReplaceChild("rig_4", CubeListBuilder.create(), PartPose.ZERO));
		rig5(body.addOrReplaceChild("rig_5", CubeListBuilder.create(), PartPose.ZERO));
		rig6(body.addOrReplaceChild("rig_6", CubeListBuilder.create(), PartPose.ZERO));
		for (int t = TIERS; t < RIGS; t++) {
			body.addOrReplaceChild("rig_" + t, GearLines.rig(t - TIERS), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 64, 64);
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

	/** 鏽皮彈帶: an old leather bandolier from the left shoulder to the right hip, crossing front and back, cartridges in loops either side of its buckle, and two small pouches sewn on at the hip. */
	private static void rig0(final PartDefinition rig) {
		rig.addOrReplaceChild("front", CubeListBuilder.create()
			// belt
			.texOffs(0, 0).addBox(-1.0F, -8.0F, -0.5F, 2.0F, 16.0F, 1.0F)
			// shell 0
			.texOffs(37, 0).addBox(-0.5F, -7.5F, -1.5F, 1.0F, 1.0F, 1.0F)
			// shell 1
			.texOffs(42, 0).addBox(-0.5F, -5.5F, -1.5F, 1.0F, 1.0F, 1.0F)
			// shell 2
			.texOffs(47, 0).addBox(-0.5F, -3.5F, -1.5F, 1.0F, 1.0F, 1.0F)
			// shell 3
			.texOffs(52, 0).addBox(-0.5F, -1.5F, -1.5F, 1.0F, 1.0F, 1.0F)
			// buckle
			.texOffs(14, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 2.0F, 1.0F)
			// shell 4
			.texOffs(57, 0).addBox(-0.5F, 2.5F, -1.5F, 1.0F, 1.0F, 1.0F)
			// shell 5
			.texOffs(0, 18).addBox(-0.5F, 4.5F, -1.5F, 1.0F, 1.0F, 1.0F)
			// shell 6
			.texOffs(5, 18).addBox(-0.5F, 6.5F, -1.5F, 1.0F, 1.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 6.0F, -3.6F, 0.0F, 0.0F, 0.78F));
		rig.addOrReplaceChild("back", CubeListBuilder.create()
			// belt
			.texOffs(7, 0).addBox(-1.0F, -8.0F, -0.5F, 2.0F, 16.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, 6.0F, 3.6F, 0.0F, 0.0F, -0.78F));
		rig.addOrReplaceChild("hip", CubeListBuilder.create()
			// pouch a
			.texOffs(23, 0).addBox(-4.5F, 9.0F, -4.6F, 2.0F, 2.0F, 1.0F)
			// pouch b
			.texOffs(30, 0).addBox(-2.3F, 10.0F, -4.4F, 2.0F, 2.0F, 1.0F),
			PartPose.ZERO);
	}

	/** 碼頭工腰掛: a canvas belt round the waist with four pouches open at the top, two in front and one on each hip, and a hook. */
	private static void rig1(final PartDefinition rig) {
		rig.addOrReplaceChild("cubes", CubeListBuilder.create()
			// belt
			.texOffs(0, 0).addBox(-5.5F, 9.0F, -4.0F, 11.0F, 2.0F, 8.0F)
			// pouch fr
			.texOffs(0, 11).addBox(-4.0F, 7.5F, -6.0F, 3.0F, 3.0F, 2.0F)
			// pouch fl
			.texOffs(11, 11).addBox(1.0F, 7.5F, -6.0F, 3.0F, 3.0F, 2.0F)
			// pouch sr
			.texOffs(39, 0).addBox(-7.5F, 8.5F, -2.0F, 2.0F, 3.0F, 3.0F)
			// pouch sl
			.texOffs(50, 0).addBox(5.5F, 8.5F, -2.0F, 2.0F, 3.0F, 3.0F)
			// hook
			.texOffs(22, 11).addBox(5.6F, 11.0F, 2.0F, 1.0F, 2.0F, 1.0F),
			PartPose.ZERO);
	}

	/** 巡潮胸掛: shoulder straps crossing on the back, a panel of three upright magazine pouches with the signal lamp's square pouch above them, and straps round the sides. */
	private static void rig2(final PartDefinition rig) {
		rig.addOrReplaceChild("cubes", CubeListBuilder.create()
			// back cross
			.texOffs(43, 20).addBox(-2.0F, 3.0F, 3.0F, 4.0F, 3.0F, 1.0F)
			// back strap down
			.texOffs(27, 20).addBox(-0.5F, 6.0F, 3.0F, 1.0F, 4.0F, 1.0F)
			// panel
			.texOffs(39, 10).addBox(-4.0F, 5.0F, -4.0F, 8.0F, 5.0F, 1.0F)
			// mag r
			.texOffs(0, 20).addBox(-3.5F, 6.0F, -5.5F, 2.0F, 4.0F, 1.5F)
			// mag m
			.texOffs(9, 20).addBox(-1.0F, 6.0F, -5.5F, 2.0F, 4.0F, 1.5F)
			// mag l
			.texOffs(18, 20).addBox(1.5F, 6.0F, -5.5F, 2.0F, 4.0F, 1.5F)
			// lamp
			.texOffs(32, 20).addBox(-1.5F, 3.0F, -6.0F, 3.0F, 3.0F, 2.0F)
			// side r
			.texOffs(38, 0).addBox(-6.0F, 8.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// side l
			.texOffs(0, 10).addBox(5.0F, 8.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// side back
			.texOffs(0, 27).addBox(-5.0F, 8.0F, 3.0F, 10.0F, 1.0F, 1.0F),
			PartPose.ZERO);
		rig.addOrReplaceChild("over", CubeListBuilder.create()
			// strap front r
			.texOffs(19, 10).addBox(-3.0F, -1.0F, -4.0F, 1.0F, 6.0F, 1.0F)
			// strap over r
			.texOffs(0, 0).addBox(-3.0F, -2.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// strap front l
			.texOffs(24, 10).addBox(2.0F, -1.0F, -4.0F, 1.0F, 6.0F, 1.0F)
			// strap over l
			.texOffs(19, 0).addBox(2.0F, -2.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// strap back r
			.texOffs(29, 10).addBox(-3.0F, -1.0F, 3.0F, 1.0F, 5.0F, 1.0F)
			// strap back l
			.texOffs(34, 10).addBox(2.0F, -1.0F, 3.0F, 1.0F, 5.0F, 1.0F),
			PartPose.ZERO);
	}

	/** 錨衛胸掛: H-shaped straps, two square pouches high on the chest, a row of four slim pouches below and a pouch on the belt. */
	private static void rig3(final PartDefinition rig) {
		rig.addOrReplaceChild("cubes", CubeListBuilder.create()
			// back cross
			.texOffs(30, 20).addBox(-2.0F, 3.0F, 3.0F, 4.0F, 3.0F, 1.0F)
			// back strap down
			.texOffs(39, 10).addBox(-0.5F, 6.0F, 3.0F, 1.0F, 4.0F, 1.0F)
			// bar
			.texOffs(18, 26).addBox(-3.0F, 4.0F, -4.0F, 6.0F, 1.0F, 1.0F)
			// square r
			.texOffs(44, 10).addBox(-4.0F, 2.0F, -5.5F, 3.0F, 3.0F, 1.5F)
			// square l
			.texOffs(0, 20).addBox(1.0F, 2.0F, -5.5F, 3.0F, 3.0F, 1.5F)
			// panel
			.texOffs(11, 20).addBox(-4.0F, 6.0F, -4.0F, 8.0F, 4.0F, 1.0F)
			// slim 0
			.texOffs(41, 20).addBox(-3.75F, 6.5F, -5.0F, 1.5F, 3.0F, 1.0F)
			// slim 1
			.texOffs(48, 20).addBox(-1.75F, 6.5F, -5.0F, 1.5F, 3.0F, 1.0F)
			// slim 2
			.texOffs(55, 20).addBox(0.25F, 6.5F, -5.0F, 1.5F, 3.0F, 1.0F)
			// slim 3
			.texOffs(0, 26).addBox(2.25F, 6.5F, -5.0F, 1.5F, 3.0F, 1.0F)
			// belt pouch
			.texOffs(7, 26).addBox(-1.5F, 10.0F, -4.5F, 3.0F, 2.0F, 1.5F)
			// side r
			.texOffs(38, 0).addBox(-6.0F, 9.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// side l
			.texOffs(0, 10).addBox(5.0F, 9.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// side back
			.texOffs(33, 26).addBox(-5.0F, 9.0F, 3.0F, 10.0F, 1.0F, 1.0F),
			PartPose.ZERO);
		rig.addOrReplaceChild("over", CubeListBuilder.create()
			// strap front r
			.texOffs(19, 10).addBox(-3.0F, -1.0F, -4.0F, 1.0F, 6.0F, 1.0F)
			// strap over r
			.texOffs(0, 0).addBox(-3.0F, -2.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// strap front l
			.texOffs(24, 10).addBox(2.0F, -1.0F, -4.0F, 1.0F, 6.0F, 1.0F)
			// strap over l
			.texOffs(19, 0).addBox(2.0F, -2.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// strap back r
			.texOffs(29, 10).addBox(-3.0F, -1.0F, 3.0F, 1.0F, 5.0F, 1.0F)
			// strap back l
			.texOffs(34, 10).addBox(2.0F, -1.0F, 3.0F, 1.0F, 5.0F, 1.0F),
			PartPose.ZERO);
	}

	/** 霧行胸掛: a wide flat document pouch across the top and four long upright pouches under it. */
	private static void rig4(final PartDefinition rig) {
		rig.addOrReplaceChild("cubes", CubeListBuilder.create()
			// back cross
			.texOffs(34, 20).addBox(-2.0F, 3.0F, 3.0F, 4.0F, 3.0F, 1.0F)
			// back strap down
			.texOffs(29, 20).addBox(-0.5F, 6.0F, 3.0F, 1.0F, 4.0F, 1.0F)
			// panel
			.texOffs(19, 10).addBox(-4.0F, 4.0F, -4.0F, 8.0F, 6.0F, 1.0F)
			// admin
			.texOffs(45, 20).addBox(-3.0F, 3.0F, -5.5F, 6.0F, 2.0F, 1.5F)
			// long 0
			.texOffs(38, 10).addBox(-3.75F, 5.0F, -5.5F, 1.5F, 5.0F, 1.5F)
			// long 1
			.texOffs(47, 10).addBox(-1.75F, 5.0F, -5.5F, 1.5F, 5.0F, 1.5F)
			// long 2
			.texOffs(56, 10).addBox(0.25F, 5.0F, -5.5F, 1.5F, 5.0F, 1.5F)
			// long 3
			.texOffs(0, 20).addBox(2.25F, 5.0F, -5.5F, 1.5F, 5.0F, 1.5F)
			// side r
			.texOffs(38, 0).addBox(-6.0F, 9.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// side l
			.texOffs(0, 10).addBox(5.0F, 9.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// side back
			.texOffs(0, 28).addBox(-5.0F, 9.0F, 3.0F, 10.0F, 1.0F, 1.0F),
			PartPose.ZERO);
		rig.addOrReplaceChild("over", CubeListBuilder.create()
			// strap front r
			.texOffs(9, 20).addBox(-3.0F, -1.0F, -4.0F, 1.0F, 5.0F, 1.0F)
			// strap over r
			.texOffs(0, 0).addBox(-3.0F, -2.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// strap front l
			.texOffs(14, 20).addBox(2.0F, -1.0F, -4.0F, 1.0F, 5.0F, 1.0F)
			// strap over l
			.texOffs(19, 0).addBox(2.0F, -2.0F, -4.0F, 1.0F, 1.0F, 8.0F)
			// strap back r
			.texOffs(19, 20).addBox(-3.0F, -1.0F, 3.0F, 1.0F, 5.0F, 1.0F)
			// strap back l
			.texOffs(24, 20).addBox(2.0F, -1.0F, 3.0F, 1.0F, 5.0F, 1.0F),
			PartPose.ZERO);
	}

	/** 鐘守戰術背心: a vest over the front and back with shoulder pieces, four magazine pouches each with a small bell on its flap, a pouch on each side and a belt. */
	private static void rig5(final PartDefinition rig) {
		rig.addOrReplaceChild("cubes", CubeListBuilder.create()
			// vest front
			.texOffs(0, 0).addBox(-4.5F, 0.0F, -4.0F, 9.0F, 11.0F, 1.0F)
			// vest back
			.texOffs(21, 0).addBox(-4.5F, 0.0F, 3.0F, 9.0F, 11.0F, 1.0F)
			// mag 0
			.texOffs(21, 24).addBox(-4.0F, 4.0F, -5.5F, 2.0F, 4.0F, 1.5F)
			// mag 1
			.texOffs(30, 24).addBox(-2.0F, 4.0F, -5.5F, 2.0F, 4.0F, 1.5F)
			// mag 2
			.texOffs(39, 24).addBox(0.0F, 4.0F, -5.5F, 2.0F, 4.0F, 1.5F)
			// mag 3
			.texOffs(48, 24).addBox(2.0F, 4.0F, -5.5F, 2.0F, 4.0F, 1.5F)
			// bell 0
			.texOffs(22, 34).addBox(-3.5F, 3.5F, -6.0F, 1.0F, 1.0F, 1.0F)
			// bell 1
			.texOffs(27, 34).addBox(-1.5F, 3.5F, -6.0F, 1.0F, 1.0F, 1.0F)
			// bell 2
			.texOffs(32, 34).addBox(0.5F, 3.5F, -6.0F, 1.0F, 1.0F, 1.0F)
			// bell 3
			.texOffs(37, 34).addBox(2.5F, 3.5F, -6.0F, 1.0F, 1.0F, 1.0F)
			// side pouch r
			.texOffs(0, 34).addBox(-6.5F, 5.0F, -2.5F, 1.5F, 3.0F, 3.0F)
			// side pouch l
			.texOffs(11, 34).addBox(5.0F, 5.0F, -2.5F, 1.5F, 3.0F, 3.0F)
			// belt
			.texOffs(0, 13).addBox(-5.5F, 10.0F, -4.0F, 11.0F, 1.5F, 8.0F),
			PartPose.ZERO);
		rig.addOrReplaceChild("over", CubeListBuilder.create()
			// shoulder r
			.texOffs(39, 13).addBox(-4.5F, -2.0F, -4.0F, 2.0F, 1.0F, 8.0F)
			// shoulder l
			.texOffs(0, 24).addBox(2.5F, -2.0F, -4.0F, 2.0F, 1.0F, 8.0F),
			PartPose.ZERO);
	}

	/** 鏽神骸掛: bones from the shoulders, ribs across the chest, a breastbone, a spine with its vertebrae down the back and one small pouch. */
	private static void rig6(final PartDefinition rig) {
		rig.addOrReplaceChild("cubes", CubeListBuilder.create()
			// rib 0
			.texOffs(16, 14).addBox(-4.0F, 4.0F, -5.0F, 8.0F, 1.0F, 1.5F)
			// rib 1
			.texOffs(37, 14).addBox(-3.5F, 5.6F, -5.0F, 7.0F, 1.0F, 1.5F)
			// rib 2
			.texOffs(0, 23).addBox(-4.0F, 7.2F, -5.0F, 8.0F, 1.0F, 1.5F)
			// rib 3
			.texOffs(21, 23).addBox(-3.5F, 8.8F, -5.0F, 7.0F, 1.0F, 1.5F)
			// sternum
			.texOffs(0, 14).addBox(-0.5F, 3.5F, -5.5F, 1.0F, 6.5F, 1.0F)
			// spine
			.texOffs(0, 0).addBox(-0.5F, -1.0F, 3.0F, 1.0F, 12.0F, 1.0F)
			// vertebra 0
			.texOffs(40, 23).addBox(-1.0F, 1.0F, 3.5F, 2.0F, 1.0F, 1.0F)
			// vertebra 1
			.texOffs(47, 23).addBox(-1.0F, 3.5F, 3.5F, 2.0F, 1.0F, 1.0F)
			// vertebra 2
			.texOffs(54, 23).addBox(-1.0F, 6.0F, 3.5F, 2.0F, 1.0F, 1.0F)
			// vertebra 3
			.texOffs(0, 27).addBox(-1.0F, 8.5F, 3.5F, 2.0F, 1.0F, 1.0F)
			// pouch bone
			.texOffs(5, 14).addBox(-4.5F, 9.5F, -4.5F, 3.0F, 2.0F, 1.5F),
			PartPose.ZERO);
		rig.addOrReplaceChild("over", CubeListBuilder.create()
			// collar r
			.texOffs(5, 0).addBox(-3.5F, -2.0F, -4.0F, 1.5F, 1.0F, 8.0F)
			// collar l
			.texOffs(26, 0).addBox(2.0F, -2.0F, -4.0F, 1.5F, 1.0F, 8.0F)
			// hang r
			.texOffs(47, 0).addBox(-3.5F, -1.0F, -4.5F, 1.5F, 7.0F, 1.0F)
			// hang l
			.texOffs(54, 0).addBox(2.0F, -1.0F, -4.5F, 1.5F, 7.0F, 1.0F),
			PartPose.ZERO);
	}

	@Override
	public void submit(final PoseStack poseStack, final SubmitNodeCollector collector, final int light, final AvatarRenderState state, final float yRot, final float xRot) {
		Identifier pack = texture(life.shizune.mistinn.registry.MistinnItems.BACKPACKS, state.getData(BACKPACK));
		if (pack != null) {
			coloredCutoutModelCopyLayerRender(this.backpack, pack, poseStack, collector, light, state, -1, 2);
		}
		Identifier rig = texture(life.shizune.mistinn.registry.MistinnItems.RIGS, state.getData(RIG));
		if (rig != null) {
			coloredCutoutModelCopyLayerRender(this.rig, rig, poseStack, collector, light, state, -1, 2);
		}
	}

	/** The worn sheet of the piece at that index: named for its item, as {@code backpack_3} or {@code backpack_heavy_3}. */
	private static @Nullable Identifier texture(final java.util.List<net.minecraft.world.item.Item> items, final @Nullable Integer index) {
		if (index == null || index < 0 || index >= items.size()) {
			return null;
		}
		return Mistinn.id("textures/entity/gear/" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(items.get(index)).getPath() + ".png");
	}

	/**
	 * The body with one kind of gear on it. Models are drawn after the frame is collected, from
	 * the render state, so which tier shows is decided here rather than when submitting.
	 */
	private static final class GearModel extends HumanoidModel<AvatarRenderState> {
		private final ModelPart[] own;
		/** What of each piece runs up over the shoulders; null for one that has nothing there. */
		private final @Nullable ModelPart[] over;
		private final RenderStateDataKey<Integer> key;

		GearModel(final ModelPart root, final String kind, final RenderStateDataKey<Integer> key, final int count) {
			super(root);
			this.key = key;
			this.own = new ModelPart[count];
			this.over = new ModelPart[count];
			for (int t = 0; t < count; t++) {
				this.own[t] = this.body.getChild(kind + t);
				this.over[t] = this.own[t].hasChild("over") ? this.own[t].getChild("over") : null;
			}
		}

		@Override
		public void setupAnim(final AvatarRenderState state) {
			super.setupAnim(state);
			Integer tier = state.getData(this.key);
			for (int t = 0; t < this.own.length; t++) {
				this.own[t].visible = tier != null && tier == t;
				ModelPart over = this.over[t];
				if (over != null) {
					// through the wearer's own eyes what runs up over the shoulders is right under the eye: it is left out
					over.visible = !Boolean.TRUE.equals(state.getData(life.shizune.mistinn.client.view.OwnBody.KEY));
				}
			}
			if (this.key == BACKPACK && tier != null && tier >= 0 && tier < this.own.length) {
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
