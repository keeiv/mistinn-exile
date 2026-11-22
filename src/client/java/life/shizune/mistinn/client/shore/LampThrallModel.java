package life.shizune.mistinn.client.shore;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.Nullable;

/**
 * 燈奴的模型: a lamplighter bent nearly double. The spine leans forward from the hips, so the hooded head
 * hangs out in front of the chest and the shoulders sit forward of the hips; a hood with a peaked back and a
 * stiff brim shades the face down to two pale eyes, a short mantle sits on the shoulders, and a long cloak
 * hangs from them to the shins in twelve panels, each ending in a tatter. An oil can hangs at the right hip.
 * The arms are thin and longer than a man's, with bony wrists and three long fingers; the legs are thin, wrapped
 * at the knee and ankle. It keeps the humanoid bone names, so the zombie's walk and the enemy motion still pose it.
 * The boxes between the BODY markers are generated together with the 128x128 texture layout.
 */
public class LampThrallModel extends ZombieModel<ZombieRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Mistinn.id("lamp_thrall"), "main");
	/** How far the held lantern sits below where a man's hand would be: these arms are longer. */
	private static final float HAND_DROP = 2.5F / 16.0F;

	/** How far, in radians, the hem of the cloak swings out behind at a full walk, and the tatters with each step. */
	private static final float CLOAK_SWING = 0.22F;
	private static final float TATTER_SWING = 0.35F;
	private static final int TATTERS = 14;

	/** How far forward of the hips the shoulders sit; the attack swing would put them back over the hips. */
	private final float shoulderZ;
	/** The cloak and the tatters at its hem; null on the armour layers, which have no cloak. */
	private final @Nullable ModelPart cloak;
	private final ModelPart[] tatters;

	public LampThrallModel(final ModelPart root) {
		super(root);
		this.shoulderZ = this.rightArm.z;
		this.cloak = this.body.hasChild("spine") ? this.body.getChild("spine").getChild("cloak") : null;
		this.tatters = new ModelPart[this.cloak == null ? 0 : TATTERS];
		for (int i = 0; i < this.tatters.length; i++) {
			this.tatters[i] = this.cloak.getChild("cloak_tatter_" + (i + 1));
		}
	}

	/** Walking, the hem of the cloak swings out behind and the tatters swing with each step; standing, it barely stirs. */
	@Override
	public void setupAnim(final ZombieRenderState state) {
		super.setupAnim(state);
		if (this.cloak == null) {
			return;
		}
		float walk = Math.min(state.walkAnimationSpeed, 1.0F);
		float step = state.walkAnimationPos * 0.6662F;
		this.cloak.xRot += CLOAK_SWING * walk + 0.025F * Mth.sin(state.ageInTicks * 0.09F);
		for (int i = 0; i < this.tatters.length; i++) {
			this.tatters[i].xRot += TATTER_SWING * walk * Mth.sin(step + i * 0.7F) + 0.05F * Mth.sin(state.ageInTicks * 0.13F + i);
		}
	}

	@Override
	protected void setupAttackAnimation(final ZombieRenderState state) {
		super.setupAttackAnimation(state);
		// the same guard as the swing itself, which sets the shoulders back over the hips
		if (state.swingAnimation > 0.0F && state.currentSwing != null) {
			this.rightArm.z += this.shoulderZ;
			this.leftArm.z += this.shoulderZ;
		}
	}

	@Override
	public void translateToHand(final ZombieRenderState state, final HumanoidArm arm, final PoseStack poseStack) {
		super.translateToHand(state, arm, poseStack);
		poseStack.translate(0.0F, HAND_DROP, 0.0F);
	}

	public static LayerDefinition definition() {
		// BODY: generated
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		PartDefinition spine = body.addOrReplaceChild("spine", CubeListBuilder.create()
			.texOffs(34, 0).addBox(-3.0F, -10.0F, -1.5F, 6.0F, 10.0F, 3.0F)
			.texOffs(100, 0).addBox(-2.5F, -8.5F, -2.1F, 5.0F, 7.9F, 0.7F)
			.texOffs(93, 52).addBox(-3.3F, -3.0F, -1.8F, 6.6F, 1.0F, 3.6F)
			.texOffs(13, 65).addBox(-0.7F, -3.2F, -2.2F, 1.4F, 1.4F, 0.5F)
			.texOffs(91, 43).addBox(-3.4F, -11.0F, -2.0F, 6.8F, 2.0F, 4.0F)
			.texOffs(116, 52).addBox(-5.2F, -3.0F, -1.0F, 2.0F, 2.6F, 2.0F, new CubeDeformation(0.05F))
			.texOffs(20, 65).addBox(-5.4F, -4.2F, -0.3F, 0.8F, 1.2F, 0.6F)
			.texOffs(25, 65).addBox(-3.5F, -5.0F, -0.2F, 0.4F, 2.0F, 0.4F), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, 0.2793F, 0.0F, 0.0F));

		PartDefinition cloak = spine.addOrReplaceChild("cloak", CubeListBuilder.create(), PartPose.offset(0.0F, -9.6F, 0.4F));
		tatter(cloak, 14, 70, 70, 4.1235F, 19.0719F, -3.6475F, -0.0138F, -0.6981F, 0.0999F);
		tatter(cloak, 13, 63, 70, 5.5197F, 18.4361F, -2.1464F, -0.0136F, -1.074F, -0.1035F);
		tatter(cloak, 12, 56, 70, 6.4498F, 18.8701F, -0.5512F, -0.1092F, -1.45F, 0.0708F);
		tatter(cloak, 11, 49, 70, 6.0593F, 18.4786F, 1.1955F, -0.0912F, -1.8259F, 0.0879F);
		tatter(cloak, 10, 42, 70, 5.1011F, 19.0776F, 2.7817F, -0.0369F, -2.2018F, -0.0956F);
		tatter(cloak, 9, 35, 70, 3.3265F, 18.7117F, 3.9385F, 0.0401F, -2.5777F, -0.0768F);
		tatter(cloak, 8, 28, 70, 1.282F, 18.632F, 4.7089F, 0.0205F, -2.9536F, 0.0935F);
		tatter(cloak, 7, 21, 70, -1.2427F, 19.36F, 4.5522F, 0.0399F, 2.9536F, -0.1005F);
		tatter(cloak, 6, 14, 70, -3.442F, 18.7784F, 4.1098F, -0.1301F, 2.5777F, 0.0882F);
		tatter(cloak, 5, 7, 70, -5.0178F, 19.109F, 2.7824F, -0.0731F, 2.2018F, 0.0865F);
		tatter(cloak, 4, 0, 70, -6.1715F, 18.4221F, 1.1657F, -0.1055F, 1.8259F, -0.0746F);
		tatter(cloak, 3, 117, 65, -6.3069F, 18.8816F, -0.6271F, 0.0369F, 1.45F, 0.0073F);
		tatter(cloak, 2, 110, 65, -5.6373F, 19.1601F, -2.2864F, -0.0642F, 1.074F, -0.0721F);
		tatter(cloak, 1, 103, 65, -3.9519F, 19.338F, -3.505F, -0.0556F, 0.6981F, -0.0198F);

		panel(cloak, "cloak_12_3", 34, 43, -1.249F, -3.33F, 2.498F, 6.66F, 0.0F, 4.641F, 16.0F, -2.66F, -2.9828F, 0.7431F, 3.1075F);
		panel(cloak, "cloak_12_2", 25, 43, -1.038F, -3.295F, 2.076F, 6.59F, 0.0F, 3.909F, 9.6F, -2.054F, -3.0494F, 0.6958F, 3.1152F);
		panel(cloak, "cloak_12_1", 18, 43, -0.933F, -3.277F, 1.866F, 6.554F, 0.0F, 3.543F, 3.2F, -1.75F, -3.1116F, 0.6624F, 3.1315F);
		panel(cloak, "cloak_11_3", 9, 43, -1.356F, -3.328F, 2.712F, 6.656F, 0.0F, 5.522F, 16.0F, -1.058F, -3.0008F, 1.217F, 3.0995F);
		panel(cloak, "cloak_11_2", 0, 43, -1.144F, -3.294F, 2.289F, 6.588F, 0.0F, 4.62F, 9.6F, -0.805F, -3.0632F, 1.185F, 3.109F);
		panel(cloak, "cloak_11_1", 114, 34, -1.04F, -3.277F, 2.079F, 6.554F, 0.0F, 4.17F, 3.2F, -0.679F, -3.1168F, 1.1609F, 3.1291F);
		panel(cloak, "cloak_10_3", 105, 34, -1.371F, -3.328F, 2.742F, 6.656F, 0.0F, 5.854F, 16.0F, 0.622F, 0.138F, 1.3715F, -0.0432F);
		panel(cloak, "cloak_10_2", 96, 34, -1.16F, -3.294F, 2.319F, 6.588F, 0.0F, 4.931F, 9.6F, 0.48F, 0.0762F, 1.3523F, -0.0334F);
		panel(cloak, "cloak_10_1", 87, 34, -1.054F, -3.277F, 2.108F, 6.554F, 0.0F, 4.469F, 3.2F, 0.409F, 0.0239F, 1.3376F, -0.0128F);
		panel(cloak, "cloak_9_3", 78, 34, -1.287F, -3.329F, 2.573F, 6.658F, 0.0F, 4.866F, 16.0F, 2.147F, 0.1529F, 0.8666F, -0.037F);
		panel(cloak, "cloak_9_2", 69, 34, -1.076F, -3.295F, 2.152F, 6.59F, 0.0F, 4.071F, 9.6F, 1.633F, 0.0877F, 0.8193F, -0.0287F);
		panel(cloak, "cloak_9_1", 62, 34, -0.971F, -3.277F, 1.942F, 6.554F, 0.05F, 3.674F, 3.2F, 1.377F, 0.0283F, 0.7853F, -0.011F);
		panel(cloak, "cloak_8_3", 53, 34, -1.144F, -3.331F, 2.288F, 6.662F, 0.0F, 3.394F, 16.0F, 3.513F, 0.1734F, 0.4711F, -0.0249F);
		panel(cloak, "cloak_8_2", 46, 34, -0.93F, -3.295F, 1.861F, 6.59F, 0.0F, 2.858F, 9.6F, 2.712F, 0.103F, 0.4334F, -0.0193F);
		panel(cloak, "cloak_8_1", 39, 34, -0.825F, -3.277F, 1.649F, 6.554F, 0.0F, 2.591F, 3.2F, 2.312F, 0.0341F, 0.4077F, -0.0074F);
		panel(cloak, "cloak_7_3", 30, 34, -1.027F, -3.333F, 2.054F, 6.666F, 0.0F, 1.156F, 16.0F, 4.004F, 0.1869F, 0.1493F, -0.0087F);
		panel(cloak, "cloak_7_2", 23, 34, -0.806F, -3.296F, 1.613F, 6.592F, 0.0F, 0.967F, 9.6F, 3.047F, 0.1128F, 0.1357F, -0.0068F);
		panel(cloak, "cloak_7_1", 16, 34, -0.697F, -3.277F, 1.394F, 6.554F, 0.0F, 0.873F, 3.2F, 2.568F, 0.0377F, 0.1267F, -0.0026F);
		panel(cloak, "cloak_6_3", 7, 34, -1.027F, -3.333F, 2.053F, 6.666F, 0.0F, -1.196F, 16.0F, 4.2F, 0.1869F, -0.1493F, 0.0087F);
		panel(cloak, "cloak_6_2", 0, 34, -0.806F, -3.296F, 1.613F, 6.592F, 0.0F, -1.008F, 9.6F, 3.243F, 0.1128F, -0.1357F, 0.0068F);
		panel(cloak, "cloak_6_1", 120, 25, -0.697F, -3.277F, 1.393F, 6.554F, 0.0F, -0.913F, 3.2F, 2.764F, 0.0377F, -0.1267F, 0.0026F);
		panel(cloak, "cloak_5_3", 111, 25, -1.144F, -3.331F, 2.288F, 6.662F, 0.0F, -3.279F, 16.0F, 3.349F, 0.1734F, -0.4711F, 0.0249F);
		panel(cloak, "cloak_5_2", 104, 25, -0.93F, -3.295F, 1.861F, 6.59F, 0.0F, -2.744F, 9.6F, 2.548F, 0.103F, -0.4334F, 0.0193F);
		panel(cloak, "cloak_5_1", 97, 25, -0.824F, -3.277F, 1.648F, 6.554F, 0.0F, -2.476F, 3.2F, 2.148F, 0.0341F, -0.4077F, 0.0074F);
		panel(cloak, "cloak_4_3", 88, 25, -1.287F, -3.329F, 2.574F, 6.658F, 0.0F, -5.036F, 16.0F, 2.252F, 0.1529F, -0.8666F, 0.037F);
		panel(cloak, "cloak_4_2", 79, 25, -1.076F, -3.295F, 2.151F, 6.59F, 0.0F, -4.241F, 9.6F, 1.738F, 0.0877F, -0.8193F, 0.0287F);
		panel(cloak, "cloak_4_1", 72, 25, -0.971F, -3.277F, 1.942F, 6.554F, 0.0F, -3.844F, 3.2F, 1.482F, 0.0283F, -0.7853F, 0.011F);
		panel(cloak, "cloak_3_3", 63, 25, -1.371F, -3.328F, 2.741F, 6.656F, 0.0F, -5.656F, 16.0F, 0.593F, 0.138F, -1.3715F, 0.0432F);
		panel(cloak, "cloak_3_2", 54, 25, -1.159F, -3.294F, 2.319F, 6.588F, 0.0F, -4.733F, 9.6F, 0.451F, 0.0762F, -1.3523F, 0.0334F);
		panel(cloak, "cloak_3_1", 45, 25, -1.054F, -3.277F, 2.108F, 6.554F, 0.0F, -4.271F, 3.2F, 0.38F, 0.0239F, -1.3376F, 0.0128F);
		panel(cloak, "cloak_2_3", 36, 25, -1.356F, -3.328F, 2.712F, 6.656F, 0.0F, -5.715F, 16.0F, -1.11F, -3.0008F, -1.217F, -3.0995F);
		panel(cloak, "cloak_2_2", 27, 25, -1.144F, -3.294F, 2.289F, 6.588F, 0.0F, -4.814F, 9.6F, -0.857F, -3.0632F, -1.185F, -3.109F);
		panel(cloak, "cloak_2_1", 18, 25, -1.039F, -3.277F, 2.079F, 6.554F, 0.0F, -4.363F, 3.2F, -0.73F, -3.1168F, -1.1609F, -3.1291F);
		panel(cloak, "cloak_1_3", 9, 25, -1.249F, -3.33F, 2.498F, 6.66F, 0.0F, -4.484F, 16.0F, -2.536F, -2.9828F, -0.7431F, -3.1075F);
		panel(cloak, "cloak_1_2", 0, 25, -1.038F, -3.295F, 2.076F, 6.59F, 0.0F, -3.752F, 9.6F, -1.93F, -3.0494F, -0.6958F, -3.1152F);
		panel(cloak, "cloak_1_1", 119, 15, -0.933F, -3.277F, 1.866F, 6.554F, 0.05F, -3.386F, 3.2F, -1.626F, -3.1116F, -0.6624F, -3.1315F);

		PartDefinition mantle = spine.addOrReplaceChild("mantle", CubeListBuilder.create(), PartPose.offset(0.0F, -11.3F, 0.2F));
		panel(mantle, "mantle_10", 84, 52, -1.225F, -2.426F, 2.45F, 4.852F, 0.0F, 1.46F, 2.3F, -3.162F, -2.8341F, 0.2396F, 3.0899F);
		panel(mantle, "mantle_9", 73, 52, -1.505F, -2.465F, 3.011F, 4.93F, 0.0F, 3.701F, 2.3F, -1.866F, -2.8829F, 0.7965F, 3.0061F);
		panel(mantle, "mantle_8", 62, 52, -1.654F, -2.488F, 3.308F, 4.976F, 0.0F, 4.725F, 2.3F, 0.0F, 0.3917F, 1.5708F, 0.0F);
		panel(mantle, "mantle_7", 51, 52, -1.505F, -2.465F, 3.011F, 4.93F, 0.0F, 3.701F, 2.3F, 1.866F, 0.2587F, 0.7965F, -0.1355F);
		panel(mantle, "mantle_6", 42, 52, -1.225F, -2.426F, 2.45F, 4.852F, 0.0F, 1.46F, 2.3F, 3.162F, 0.3075F, 0.2396F, -0.0517F);
		panel(mantle, "mantle_5", 33, 52, -1.224F, -2.426F, 2.449F, 4.852F, 0.0F, -1.414F, 2.3F, 3.02F, 0.3075F, -0.2396F, 0.0517F);
		panel(mantle, "mantle_4", 22, 52, -1.505F, -2.465F, 3.011F, 4.93F, 0.0F, -3.823F, 2.3F, 1.954F, 0.2587F, -0.7965F, 0.1355F);
		panel(mantle, "mantle_3", 11, 52, -1.654F, -2.488F, 3.308F, 4.976F, 0.0F, -4.575F, 2.3F, 0.0F, 0.3917F, -1.5708F, 0.0F);
		panel(mantle, "mantle_2", 0, 52, -1.505F, -2.465F, 3.011F, 4.93F, 0.0F, -3.823F, 2.3F, -1.954F, -2.8829F, -0.7965F, -3.0061F);
		panel(mantle, "mantle_1", 114, 43, -1.224F, -2.426F, 2.449F, 4.852F, 0.0F, -1.414F, 2.3F, -3.02F, -2.8341F, -0.2396F, -3.0899F);

		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(53, 0).addBox(-2.5F, -5.2F, -3.0F, 5.0F, 5.0F, 5.0F)
			.texOffs(30, 65).addBox(-2.5F, -2.0F, -3.5F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0.05F))
			.texOffs(77, 70).addBox(-1.6F, -4.0F, -3.2F, 1.0F, 1.0F, 0.3F)
			.texOffs(82, 70).addBox(0.6F, -4.0F, -3.2F, 1.0F, 1.0F, 0.3F), PartPose.offset(0.0F, 1.4F, -3.4F));
		head.addOrReplaceChild("hat", CubeListBuilder.create()
			.texOffs(69, 15).addBox(-3.6F, -6.8F, -4.0F, 7.2F, 0.9F, 7.0F)
			.texOffs(100, 15).addBox(-3.6F, -5.9F, 2.1F, 7.2F, 6.3F, 0.9F)
			.texOffs(0, 0).addBox(2.7F, -5.9F, -4.0F, 0.9F, 6.3F, 6.1F)
			.texOffs(17, 0).addBox(-3.6F, -5.9F, -4.0F, 0.9F, 6.3F, 6.1F)
			.texOffs(60, 59).addBox(-1.0F, -6.4F, 2.8F, 2.0F, 1.4F, 2.2F)
			.texOffs(77, 65).addBox(-0.6F, -5.4F, 4.8F, 1.2F, 1.2F, 1.0F)
			.texOffs(84, 65).addBox(-3.8F, -6.9F, -4.6F, 7.6F, 1.1F, 0.8F)
			.texOffs(13, 15).addBox(-4.1F, -0.8F, -3.2F, 0.6F, 2.2F, 5.8F, new CubeDeformation(0.05F))
			.texOffs(28, 15).addBox(3.5F, -0.8F, -3.2F, 0.6F, 2.2F, 5.8F, new CubeDeformation(0.05F)), PartPose.ZERO);

		root.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(74, 0).addBox(-1.5F, -0.6F, -1.5F, 3.0F, 7.0F, 3.0F)
			.texOffs(0, 59).addBox(-1.7F, 6.2F, -1.7F, 3.4F, 1.0F, 3.4F)
			.texOffs(43, 43).addBox(-1.0F, 6.9F, -1.0F, 2.0F, 4.5F, 2.0F)
			.texOffs(17, 59).addBox(-1.2F, 11.2F, -1.2F, 2.4F, 1.7F, 2.4F)
			.texOffs(43, 65).addBox(0.5F, 12.8F, -1.1F, 0.6F, 1.6F, 0.6F)
			.texOffs(71, 59).addBox(-0.3F, 12.8F, -1.1F, 0.6F, 2.1F, 0.6F)
			.texOffs(48, 65).addBox(-1.1F, 12.8F, -1.1F, 0.6F, 1.6F, 0.6F), PartPose.offset(-5.0F, 2.6F, -2.8F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create()
			.texOffs(87, 0).addBox(-1.5F, -0.6F, -1.5F, 3.0F, 7.0F, 3.0F)
			.texOffs(30, 59).addBox(-1.7F, 6.2F, -1.7F, 3.4F, 1.0F, 3.4F)
			.texOffs(67, 43).addBox(-1.0F, 6.9F, -1.0F, 2.0F, 4.5F, 2.0F)
			.texOffs(47, 59).addBox(-1.2F, 11.2F, -1.2F, 2.4F, 1.7F, 2.4F)
			.texOffs(60, 65).addBox(-1.1F, 12.8F, -1.1F, 0.6F, 1.6F, 0.6F)
			.texOffs(102, 59).addBox(-0.3F, 12.8F, -1.1F, 0.6F, 2.1F, 0.6F)
			.texOffs(65, 65).addBox(0.5F, 12.8F, -1.1F, 0.6F, 1.6F, 0.6F), PartPose.offset(5.0F, 2.6F, -2.8F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create()
			.texOffs(113, 0).addBox(-1.3F, 0.0F, -1.2F, 2.6F, 5.5F, 2.4F)
			.texOffs(43, 15).addBox(-1.1F, 5.4F, -1.1F, 2.2F, 4.6F, 2.2F)
			.texOffs(52, 43).addBox(-1.5F, 9.7F, -2.0F, 3.0F, 2.3F, 3.4F)
			.texOffs(76, 59).addBox(-1.4F, 7.2F, -1.25F, 2.8F, 0.8F, 2.5F)
			.texOffs(89, 59).addBox(-1.45F, 2.7F, -1.35F, 2.9F, 0.8F, 2.7F)
			.texOffs(53, 65).addBox(-1.0F, 4.6F, -1.45F, 2.0F, 1.2F, 0.35F), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create()
			.texOffs(0, 15).addBox(-1.3F, 0.0F, -1.2F, 2.6F, 5.5F, 2.4F)
			.texOffs(56, 15).addBox(-1.1F, 5.4F, -1.1F, 2.2F, 4.6F, 2.2F)
			.texOffs(76, 43).addBox(-1.5F, 9.7F, -2.0F, 3.0F, 2.3F, 3.4F)
			.texOffs(107, 59).addBox(-1.4F, 7.2F, -1.25F, 2.8F, 0.8F, 2.5F)
			.texOffs(0, 65).addBox(-1.45F, 2.7F, -1.35F, 2.9F, 0.8F, 2.7F)
			.texOffs(70, 65).addBox(-1.0F, 4.6F, -1.45F, 2.0F, 1.2F, 0.35F), PartPose.offset(1.9F, 12.0F, 0.0F));
		return LayerDefinition.create(mesh, 128, 128);
		// BODY END
	}

	/** One tatter at the cloak's hem: a small hanging strip, numbered round from the front right. */
	private static void tatter(final PartDefinition cloak, final int n, final int u, final int v,
		final float x, final float y, final float z, final float xRot, final float yRot, final float zRot) {
		cloak.addOrReplaceChild("cloak_tatter_" + n, CubeListBuilder.create().texOffs(u, v).addBox(-0.65F, 0.0F, -0.2F, 1.3F, 2.0F, 0.4F),
			PartPose.offsetAndRotation(x, y, z, xRot, yRot, zRot));
	}

	/** One thin cloth panel of the cloak or the mantle, centred on its pivot. */
	private static void panel(final PartDefinition parent, final String name, final int u, final int v,
		final float left, final float top, final float width, final float height, final float grow,
		final float x, final float y, final float z, final float xRot, final float yRot, final float zRot) {
		parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(left, top, -0.3F, width, height, 0.6F, new CubeDeformation(grow)),
			PartPose.offsetAndRotation(x, y, z, xRot, yRot, zRot));
	}
}
