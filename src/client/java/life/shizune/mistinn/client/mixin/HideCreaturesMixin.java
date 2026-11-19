package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.death.DeathScene;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In the death scene the hunter is alone: no creature, monster or not, is drawn. */
@Mixin(EntityRenderDispatcher.class)
public abstract class HideCreaturesMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void mistinn$alone(final E entity, final Frustum frustum, final double x, final double y, final double z, final float partial, final CallbackInfoReturnable<Boolean> cir) {
		if (DeathScene.hides(entity)) {
			cir.setReturnValue(false);
		}
	}
}
