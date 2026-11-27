package life.shizune.mistinn.client.path;

import life.shizune.mistinn.client.anim.WeaponSwings;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;

/**
 * The player's shape (its layer, sleeves and all), moved by 3412's choreography ({@link ApostleRenderState#pose()}): the weapon arm, the other hand
 * (closed on the grip of a two-handed branch while the grip track says so), the torso's turn and lean and the legs'
 * step, the way a hunter's own blows are posed; and the head. The legs walk underneath whatever the stance holds.
 */
public class ApostleModel extends HumanoidModel<ApostleRenderState> {
	private static final float D = Mth.DEG_TO_RAD;

	public ApostleModel(final ModelPart root) {
		super(root);
	}

	@Override
	public void setupAnim(final ApostleRenderState apostle) {
		super.setupAnim(apostle);
		if (!apostle.posed) {
			return;
		}
		float[] a = apostle.arms;
		float bodyY = a[6] * D;
		this.body.yRot = bodyY;
		this.body.xRot += a[7] * D;
		this.body.zRot = apostle.limbs[2] * D;
		// the shoulders ride on the turning torso
		this.rightArm.z = Mth.sin(bodyY) * 5.0F * apostle.ageScale;
		this.rightArm.x = -Mth.cos(bodyY) * 5.0F * apostle.ageScale;
		this.leftArm.z = -Mth.sin(bodyY) * 5.0F * apostle.ageScale;
		this.leftArm.x = Mth.cos(bodyY) * 5.0F * apostle.ageScale;
		this.rightArm.xRot = a[0] * D;
		this.rightArm.yRot = a[1] * D + bodyY;
		this.rightArm.zRot = a[2] * D;
		this.leftArm.xRot = a[3] * D;
		this.leftArm.yRot = a[4] * D + bodyY;
		this.leftArm.zRot = a[5] * D;
		if (apostle.twoHanded) {
			WeaponSwings.grip(this, this.rightArm, this.leftArm, 1, Mth.clamp(a[ApostleRenderState.GRIP], 0.0F, 1.0F));
		}
		// the left leg steps into the blow, the right braces
		this.leftLeg.xRot += a[8] * D;
		this.rightLeg.xRot += a[9] * D;
		this.rightLeg.zRot = apostle.limbs[3] * D;
		this.leftLeg.zRot = apostle.limbs[4] * D;
		this.head.xRot += apostle.limbs[0] * D;
		this.head.yRot += apostle.limbs[1] * D;
	}
}
