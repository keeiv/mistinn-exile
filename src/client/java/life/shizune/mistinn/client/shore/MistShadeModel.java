package life.shizune.mistinn.client.shore;

import life.shizune.mistinn.Mistinn;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * 霧影的模型: what is left of a lost hunter in the mist. A hood with a stiff brim over a face that is only a
 * hollow and two pale points, a rag at the jaw, a short mantle on the shoulders, a coat belted at the waist.
 * Below the belt it has no legs: the coat narrows into a skirt of panels and two trailing tails of mist that
 * drift back as it moves and never reach the ground, with loose wisps hanging from the hem and the sleeves.
 * The forearms are bare and thin, with long fingers. The wisps fade out toward their ends in the texture
 * itself, so even a shade fully there thins away at its hem. It keeps the humanoid bone names, so the
 * zombie's walk swings the tails and the enemy motion still poses it.
 * The boxes between the BODY markers are generated together with the 128x128 texture layout.
 */
public class MistShadeModel extends ZombieModel<ZombieRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("mist_shade"), "main");

	/** How far, in radians, the tails of mist trail out behind standing and at a full walk, and how far they drift. */
	private static final float TRAIL_STILL = 0.12F;
	private static final float TRAIL_MOVING = 0.45F;
	private static final float DRIFT = 0.08F;
	private static final int HEM_WISPS = 7;

	/** The skirt, the wisps at its hem and the sleeves; null and empty on the armour layers, which have none. */
	private final @Nullable ModelPart skirt;
	private final ModelPart[] wisps;

	public MistShadeModel(final ModelPart root) {
		super(root);
		this.skirt = this.body.hasChild("skirt") ? this.body.getChild("skirt") : null;
		if (this.skirt == null) {
			this.wisps = new ModelPart[0];
			return;
		}
		this.wisps = new ModelPart[HEM_WISPS + 2];
		for (int i = 0; i < HEM_WISPS; i++) {
			this.wisps[i] = this.body.getChild("hem_wisp_" + (i + 1));
		}
		this.wisps[HEM_WISPS] = this.rightArm.getChild("sleeve_wisp_right");
		this.wisps[HEM_WISPS + 1] = this.leftArm.getChild("sleeve_wisp_left");
	}

	/**
	 * Its tails of mist do not stride as legs do: they trail out behind it, further the faster it goes, and drift
	 * on their own; the skirt flares back as it moves, and the loose wisps at the hem and the sleeves stir.
	 */
	@Override
	public void setupAnim(final ZombieRenderState state) {
		super.setupAnim(state);
		if (this.skirt == null) {
			return;
		}
		float walk = Math.min(state.walkAnimationSpeed, 1.0F);
		float time = state.ageInTicks;
		float trail = TRAIL_STILL + TRAIL_MOVING * walk;
		this.rightLeg.xRot = trail + DRIFT * Mth.sin(time * 0.11F);
		this.leftLeg.xRot = trail + DRIFT * Mth.sin(time * 0.11F + 1.9F);
		this.rightLeg.yRot = 0.0F;
		this.leftLeg.yRot = 0.0F;
		this.rightLeg.zRot = 0.04F * Mth.sin(time * 0.07F);
		this.leftLeg.zRot = -0.04F * Mth.sin(time * 0.07F + 0.8F);
		this.skirt.xRot += 0.12F * walk;
		for (int i = 0; i < this.wisps.length; i++) {
			this.wisps[i].xRot += 0.10F * Mth.sin(time * 0.15F + i * 1.3F) + 0.15F * walk;
		}
	}

	public static LayerDefinition definition() {
		// BODY: generated
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(38, 0).addBox(-3.5F, -7.0F, -3.4F, 7.0F, 7.0F, 6.8F)
			.texOffs(60, 38).addBox(-2.6F, -5.8F, -3.6F, 5.2F, 4.8F, 0.2F, new CubeDeformation(0.05F))
			.texOffs(31, 65).addBox(-2.0F, -4.3F, -3.75F, 1.2F, 0.9F, 0.2F)
			.texOffs(38, 65).addBox(0.8F, -4.3F, -3.75F, 1.2F, 0.9F, 0.2F)
			.texOffs(91, 59).addBox(-3.0F, -2.0F, -3.9F, 6.0F, 1.8F, 0.5F), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create()
			.texOffs(29, 17).addBox(-4.3F, -7.8F, -4.4F, 8.6F, 1.0F, 8.7F)
			.texOffs(66, 17).addBox(-4.3F, -6.9F, 3.4F, 8.6F, 7.3F, 0.9F, new CubeDeformation(0.05F))
			.texOffs(0, 0).addBox(-4.3F, -6.9F, -4.4F, 0.9F, 7.3F, 7.8F, new CubeDeformation(0.15F))
			.texOffs(19, 0).addBox(3.4F, -6.9F, -4.4F, 0.9F, 7.3F, 7.8F, new CubeDeformation(0.15F))
			.texOffs(106, 59).addBox(-4.5F, -7.0F, -5.0F, 9.0F, 1.1F, 0.8F)
			.texOffs(26, 29).addBox(-1.8F, -7.2F, 4.1F, 3.6F, 5.2F, 1.0F)
			.texOffs(71, 46).addBox(-1.2F, -2.3F, 4.7F, 2.4F, 3.9F, 0.8F)
			.texOffs(40, 59).addBox(-0.6F, 1.4F, 5.2F, 1.2F, 2.6F, 0.6F), PartPose.ZERO);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(101, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 7.0F, 4.0F)
			.texOffs(0, 17).addBox(-4.3F, 6.6F, -2.3F, 8.6F, 5.4F, 4.6F)
			.texOffs(87, 17).addBox(-4.6F, -0.6F, -2.7F, 9.2F, 2.0F, 5.4F)
			.texOffs(75, 38).addBox(-4.5F, 10.2F, -2.5F, 9.0F, 1.0F, 5.0F)
			.texOffs(47, 59).addBox(-3.6F, 10.4F, -3.1F, 2.0F, 2.2F, 0.8F), PartPose.ZERO);
		body.addOrReplaceChild("hem_wisp_7", CubeListBuilder.create()
			.texOffs(86, 59).addBox(-0.45F, 0.0F, -0.25F, 0.9F, 2.4F, 0.5F), PartPose.offsetAndRotation(-2.1494F, 18.4F, -0.5691F, -0.3142F, 4.1148F, 0.0F));
		body.addOrReplaceChild("hem_wisp_6", CubeListBuilder.create()
			.texOffs(33, 59).addBox(-0.6F, 0.0F, -0.25F, 1.2F, 4.0F, 0.5F), PartPose.offsetAndRotation(-0.1964F, 18.4F, -1.3946F, -0.5236F, 3.2172F, 0.0F));
		body.addOrReplaceChild("hem_wisp_5", CubeListBuilder.create()
			.texOffs(28, 59).addBox(-0.45F, 0.0F, -0.25F, 0.9F, 3.2F, 0.5F), PartPose.offsetAndRotation(1.9045F, 18.4F, -0.7934F, -0.4189F, 2.3196F, 0.0F));
		body.addOrReplaceChild("hem_wisp_4", CubeListBuilder.create()
			.texOffs(79, 59).addBox(-0.6F, 0.0F, -0.25F, 1.2F, 2.4F, 0.5F), PartPose.offsetAndRotation(2.5713F, 18.4F, 0.7817F, -0.3142F, 1.422F, 0.0F));
		body.addOrReplaceChild("hem_wisp_3", CubeListBuilder.create()
			.texOffs(23, 59).addBox(-0.45F, 0.0F, -0.25F, 0.9F, 4.0F, 0.5F), PartPose.offsetAndRotation(1.3018F, 18.4F, 2.1447F, -0.5236F, 0.5244F, 0.0F));
		body.addOrReplaceChild("hem_wisp_2", CubeListBuilder.create()
			.texOffs(16, 59).addBox(-0.6F, 0.0F, -0.25F, 1.2F, 3.2F, 0.5F), PartPose.offsetAndRotation(-0.9479F, 18.4F, 2.2692F, -0.4189F, -0.3732F, 0.0F));
		body.addOrReplaceChild("hem_wisp_1", CubeListBuilder.create()
			.texOffs(74, 59).addBox(-0.45F, 0.0F, -0.25F, 0.9F, 2.4F, 0.5F), PartPose.offsetAndRotation(-2.4839F, 18.4F, 1.0615F, -0.3142F, -1.2708F, 0.0F));
		body.addOrReplaceChild("strap", CubeListBuilder.create()
			.texOffs(0, 65).addBox(-4.1F, 3.5F, -0.1F, 8.2F, 1.4F, 0.2F), PartPose.offsetAndRotation(0.0F, 5.5F, -2.15F, 0.0F, 0.0F, 0.6981F));
		PartDefinition skirt = body.addOrReplaceChild("skirt", CubeListBuilder.create(), PartPose.offset(0.0F, 11.4F, 0.0F));
		skirt.addOrReplaceChild("skirt_10_2", CubeListBuilder.create()
			.texOffs(9, 59).addBox(-0.884F, -1.953F, -0.25F, 1.768F, 3.906F, 0.5F), PartPose.offsetAndRotation(1.024F, 5.625F, -2.259F, 3.1026F, 0.2272F, -3.1154F));
		skirt.addOrReplaceChild("skirt_10_1", CubeListBuilder.create()
			.texOffs(0, 59).addBox(-1.012F, -1.972F, -0.25F, 2.025F, 3.944F, 0.5F), PartPose.offsetAndRotation(1.286F, 1.875F, -2.544F, 3.0259F, 0.2129F, -3.0614F));
		skirt.addOrReplaceChild("skirt_9_2", CubeListBuilder.create()
			.texOffs(118, 53).addBox(-1.072F, -1.958F, -0.25F, 2.144F, 3.916F, 0.5F), PartPose.offsetAndRotation(2.518F, 5.625F, -1.278F, 3.1088F, 0.7754F, -3.0731F));
		skirt.addOrReplaceChild("skirt_9_1", CubeListBuilder.create()
			.texOffs(62, 46).addBox(-1.313F, -2.024F, -0.25F, 2.625F, 4.048F, 0.5F), PartPose.offsetAndRotation(3.206F, 1.875F, -1.455F, 3.0494F, 0.7424F, -2.9356F));
		skirt.addOrReplaceChild("skirt_8_2", CubeListBuilder.create()
			.texOffs(109, 53).addBox(-1.171F, -1.962F, -0.25F, 2.343F, 3.924F, 0.5F), PartPose.offsetAndRotation(3.312F, 5.625F, 0.0F, -0.1129F, 1.5708F, 0.0F));
		skirt.addOrReplaceChild("skirt_8_1", CubeListBuilder.create()
			.texOffs(53, 46).addBox(-1.466F, -2.055F, -0.25F, 2.933F, 4.11F, 0.5F), PartPose.offsetAndRotation(4.162F, 1.875F, 0.0F, -0.3277F, 1.5708F, 0.0F));
		skirt.addOrReplaceChild("skirt_7_2", CubeListBuilder.create()
			.texOffs(100, 53).addBox(-1.072F, -1.958F, -0.25F, 2.144F, 3.916F, 0.5F), PartPose.offsetAndRotation(2.518F, 5.625F, 1.278F, -0.0328F, 0.7754F, 0.0685F));
		skirt.addOrReplaceChild("skirt_7_1", CubeListBuilder.create()
			.texOffs(44, 46).addBox(-1.313F, -2.024F, -0.25F, 2.625F, 4.048F, 0.5F), PartPose.offsetAndRotation(3.206F, 1.875F, 1.455F, -0.0922F, 0.7424F, 0.206F));
		skirt.addOrReplaceChild("skirt_6_2", CubeListBuilder.create()
			.texOffs(93, 53).addBox(-0.884F, -1.953F, -0.25F, 1.768F, 3.906F, 0.5F), PartPose.offsetAndRotation(1.024F, 5.625F, 2.259F, -0.039F, 0.2272F, 0.0262F));
		skirt.addOrReplaceChild("skirt_6_1", CubeListBuilder.create()
			.texOffs(84, 53).addBox(-1.012F, -1.972F, -0.25F, 2.025F, 3.944F, 0.5F), PartPose.offsetAndRotation(1.286F, 1.875F, 2.544F, -0.1156F, 0.2129F, 0.0801F));
		skirt.addOrReplaceChild("skirt_5_2", CubeListBuilder.create()
			.texOffs(77, 53).addBox(-0.884F, -1.953F, -0.25F, 1.768F, 3.906F, 0.5F), PartPose.offsetAndRotation(-0.962F, 5.625F, 2.069F, -0.039F, -0.2272F, -0.0262F));
		skirt.addOrReplaceChild("skirt_5_1", CubeListBuilder.create()
			.texOffs(68, 53).addBox(-1.013F, -1.972F, -0.25F, 2.025F, 3.944F, 0.5F), PartPose.offsetAndRotation(-1.224F, 1.875F, 2.354F, -0.1156F, -0.2129F, -0.0801F));
		skirt.addOrReplaceChild("skirt_4_2", CubeListBuilder.create()
			.texOffs(59, 53).addBox(-1.071F, -1.958F, -0.25F, 2.143F, 3.916F, 0.5F), PartPose.offsetAndRotation(-2.68F, 5.625F, 1.396F, -0.0328F, -0.7754F, -0.0685F));
		skirt.addOrReplaceChild("skirt_4_1", CubeListBuilder.create()
			.texOffs(35, 46).addBox(-1.312F, -2.024F, -0.25F, 2.625F, 4.048F, 0.5F), PartPose.offsetAndRotation(-3.368F, 1.875F, 1.572F, -0.0922F, -0.7424F, -0.206F));
		skirt.addOrReplaceChild("skirt_3_2", CubeListBuilder.create()
			.texOffs(50, 53).addBox(-1.171F, -1.962F, -0.25F, 2.343F, 3.924F, 0.5F), PartPose.offsetAndRotation(-3.113F, 5.625F, 0.0F, -0.1129F, -1.5708F, 0.0F));
		skirt.addOrReplaceChild("skirt_3_1", CubeListBuilder.create()
			.texOffs(26, 46).addBox(-1.466F, -2.055F, -0.25F, 2.933F, 4.11F, 0.5F), PartPose.offsetAndRotation(-3.963F, 1.875F, 0.0F, -0.3277F, -1.5708F, 0.0F));
		skirt.addOrReplaceChild("skirt_2_2", CubeListBuilder.create()
			.texOffs(41, 53).addBox(-1.071F, -1.958F, -0.25F, 2.143F, 3.916F, 0.5F), PartPose.offsetAndRotation(-2.68F, 5.625F, -1.396F, 3.1088F, -0.7754F, 3.0731F));
		skirt.addOrReplaceChild("skirt_2_1", CubeListBuilder.create()
			.texOffs(17, 46).addBox(-1.312F, -2.024F, -0.25F, 2.625F, 4.048F, 0.5F), PartPose.offsetAndRotation(-3.368F, 1.875F, -1.572F, 3.0494F, -0.7424F, 2.9356F));
		skirt.addOrReplaceChild("skirt_1_2", CubeListBuilder.create()
			.texOffs(34, 53).addBox(-0.884F, -1.953F, -0.25F, 1.768F, 3.906F, 0.5F), PartPose.offsetAndRotation(-0.962F, 5.625F, -2.069F, 3.1026F, -0.2272F, 3.1154F));
		skirt.addOrReplaceChild("skirt_1_1", CubeListBuilder.create()
			.texOffs(25, 53).addBox(-1.013F, -1.972F, -0.25F, 2.025F, 3.944F, 0.5F), PartPose.offsetAndRotation(-1.224F, 1.875F, -2.354F, 3.0259F, -0.2129F, 3.0614F));
		PartDefinition mantle = body.addOrReplaceChild("mantle", CubeListBuilder.create(), PartPose.offset(0.0F, -0.5F, 0.0F));
		mantle.addOrReplaceChild("mantle_10", CubeListBuilder.create()
			.texOffs(51, 38).addBox(-1.322F, -2.534F, -0.25F, 2.644F, 5.068F, 0.5F), PartPose.offsetAndRotation(1.715F, 2.5F, -3.329F, -2.9877F, 0.2056F, 3.1054F));
		mantle.addOrReplaceChild("mantle_9", CubeListBuilder.create()
			.texOffs(40, 38).addBox(-1.735F, -2.55F, -0.25F, 3.471F, 5.1F, 0.5F), PartPose.offsetAndRotation(4.328F, 2.5F, -1.94F, -3.0186F, 0.7206F, 3.0469F));
		mantle.addOrReplaceChild("mantle_8", CubeListBuilder.create()
			.texOffs(29, 38).addBox(-1.946F, -2.56F, -0.25F, 3.892F, 5.12F, 0.5F), PartPose.offsetAndRotation(5.55F, 2.5F, 0.0F, 0.2165F, 1.5708F, 0.0F));
		mantle.addOrReplaceChild("mantle_7", CubeListBuilder.create()
			.texOffs(18, 38).addBox(-1.735F, -2.55F, -0.25F, 3.471F, 5.1F, 0.5F), PartPose.offsetAndRotation(4.328F, 2.5F, 1.94F, 0.123F, 0.7206F, -0.0947F));
		mantle.addOrReplaceChild("mantle_6", CubeListBuilder.create()
			.texOffs(9, 38).addBox(-1.322F, -2.534F, -0.25F, 2.644F, 5.068F, 0.5F), PartPose.offsetAndRotation(1.715F, 2.5F, 3.329F, 0.1539F, 0.2056F, -0.0362F));
		mantle.addOrReplaceChild("mantle_5", CubeListBuilder.create()
			.texOffs(0, 38).addBox(-1.322F, -2.534F, -0.25F, 2.644F, 5.068F, 0.5F), PartPose.offsetAndRotation(-1.653F, 2.5F, 3.138F, 0.1539F, -0.2056F, 0.0362F));
		mantle.addOrReplaceChild("mantle_4", CubeListBuilder.create()
			.texOffs(112, 29).addBox(-1.735F, -2.55F, -0.25F, 3.47F, 5.1F, 0.5F), PartPose.offsetAndRotation(-4.49F, 2.5F, 2.057F, 0.123F, -0.7206F, 0.0947F));
		mantle.addOrReplaceChild("mantle_3", CubeListBuilder.create()
			.texOffs(101, 29).addBox(-1.946F, -2.56F, -0.25F, 3.892F, 5.12F, 0.5F), PartPose.offsetAndRotation(-5.35F, 2.5F, 0.0F, 0.2165F, -1.5708F, 0.0F));
		mantle.addOrReplaceChild("mantle_2", CubeListBuilder.create()
			.texOffs(90, 29).addBox(-1.735F, -2.55F, -0.25F, 3.47F, 5.1F, 0.5F), PartPose.offsetAndRotation(-4.49F, 2.5F, -2.057F, -3.0186F, -0.7206F, -3.0469F));
		mantle.addOrReplaceChild("mantle_1", CubeListBuilder.create()
			.texOffs(81, 29).addBox(-1.322F, -2.534F, -0.25F, 2.644F, 5.068F, 0.5F), PartPose.offsetAndRotation(-1.653F, 2.5F, -3.138F, -2.9877F, -0.2056F, -3.1054F));
		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(67, 0).addBox(-2.4F, -2.2F, -1.6F, 3.2F, 8.7F, 3.2F)
			.texOffs(104, 38).addBox(-2.7F, 5.8F, -1.9F, 3.8F, 1.6F, 3.8F)
			.texOffs(37, 29).addBox(-1.8F, 7.0F, -1.0F, 2.0F, 4.4F, 2.0F)
			.texOffs(80, 46).addBox(-2.0F, 11.2F, -1.2F, 2.4F, 1.8F, 2.4F)
			.texOffs(54, 59).addBox(-0.3F, 12.8F, -1.1F, 0.6F, 2.6F, 0.6F)
			.texOffs(93, 46).addBox(-1.1F, 12.8F, -1.1F, 0.6F, 3.2F, 0.6F)
			.texOffs(59, 59).addBox(-1.9F, 12.8F, -1.1F, 0.6F, 2.6F, 0.6F), PartPose.offset(-5.0F, 2.0F, 0.0F));
		rightArm.addOrReplaceChild("sleeve_wisp_right", CubeListBuilder.create()
			.texOffs(98, 46).addBox(-1.0F, 0.0F, -0.3F, 2.0F, 3.7F, 0.6F), PartPose.offsetAndRotation(-0.8F, 6.8F, 1.6F, 0.2618F, 0.0F, 0.0F));
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
			.texOffs(84, 0).addBox(-0.8F, -2.2F, -1.6F, 3.2F, 8.7F, 3.2F)
			.texOffs(0, 46).addBox(-1.1F, 5.8F, -1.9F, 3.8F, 1.6F, 3.8F)
			.texOffs(46, 29).addBox(-0.2F, 7.0F, -1.0F, 2.0F, 4.4F, 2.0F)
			.texOffs(105, 46).addBox(-0.4F, 11.2F, -1.2F, 2.4F, 1.8F, 2.4F)
			.texOffs(64, 59).addBox(-0.3F, 12.8F, -1.1F, 0.6F, 2.6F, 0.6F)
			.texOffs(118, 46).addBox(0.5F, 12.8F, -1.1F, 0.6F, 3.2F, 0.6F)
			.texOffs(69, 59).addBox(1.3F, 12.8F, -1.1F, 0.6F, 2.6F, 0.6F), PartPose.offset(5.0F, 2.0F, 0.0F));
		leftArm.addOrReplaceChild("sleeve_wisp_left", CubeListBuilder.create()
			.texOffs(0, 53).addBox(-1.0F, 0.0F, -0.3F, 2.0F, 3.7F, 0.6F), PartPose.offsetAndRotation(0.8F, 6.8F, 1.6F, 0.2618F, 0.0F, 0.0F));
		PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
			.texOffs(0, 29).addBox(-1.5F, -0.2F, -1.5F, 3.0F, 4.4F, 3.0F), PartPose.offset(-1.9F, 12.0F, 0.0F));
		rightLeg.addOrReplaceChild("wisp_right_4", CubeListBuilder.create()
			.texOffs(21, 65).addBox(-0.35F, -0.1F, -0.3F, 0.7F, 1.9F, 0.7F), PartPose.offsetAndRotation(0.0F, 9.2F, 0.9F, -0.5585F, 0.0F, 0.0F));
		rightLeg.addOrReplaceChild("wisp_right_3", CubeListBuilder.create()
			.texOffs(7, 53).addBox(-0.7F, -0.1F, -0.7F, 1.4F, 2.7F, 1.4F), PartPose.offsetAndRotation(0.0F, 6.8F, 0.5F, -0.384F, 0.0F, 0.0F));
		rightLeg.addOrReplaceChild("wisp_right_2", CubeListBuilder.create()
			.texOffs(55, 29).addBox(-1.1F, -0.2F, -0.9F, 2.2F, 3.2F, 2.2F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, -0.2094F, 0.0F, 0.0F));
		PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
			.texOffs(13, 29).addBox(-1.5F, -0.2F, -1.5F, 3.0F, 4.4F, 3.0F), PartPose.offset(1.9F, 12.0F, 0.0F));
		leftLeg.addOrReplaceChild("wisp_left_4", CubeListBuilder.create()
			.texOffs(26, 65).addBox(-0.35F, -0.1F, -0.3F, 0.7F, 1.9F, 0.7F), PartPose.offsetAndRotation(0.0F, 9.2F, 0.9F, -0.5585F, 0.0F, 0.0F));
		leftLeg.addOrReplaceChild("wisp_left_3", CubeListBuilder.create()
			.texOffs(16, 53).addBox(-0.7F, -0.1F, -0.7F, 1.4F, 2.7F, 1.4F), PartPose.offsetAndRotation(0.0F, 6.8F, 0.5F, -0.384F, 0.0F, 0.0F));
		leftLeg.addOrReplaceChild("wisp_left_2", CubeListBuilder.create()
			.texOffs(68, 29).addBox(-1.1F, -0.2F, -0.9F, 2.2F, 3.2F, 2.2F), PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, -0.2094F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 128, 128);
		// BODY END
	}
}
