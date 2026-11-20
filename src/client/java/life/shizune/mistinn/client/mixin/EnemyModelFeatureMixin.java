package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.enemy.EnemyMotion;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Deferred meshes are posed again when drawn; apply the same motion to the body and its equipment. */
@Mixin(ModelFeatureRenderer.class)
public abstract class EnemyModelFeatureMixin {
	@Redirect(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V"))
	private <S> void mistinn$poseEnemy(final Model<S> model, final S state) {
		model.setupAnim(state);
		EnemyMotion.apply(model, state);
	}
}
