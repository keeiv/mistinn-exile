package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.death.DeathScene;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The death scene turns the body to smoke itself, later; vanilla's puff a second in would spoil it. */
@Mixin(LivingEntity.class)
public abstract class DeathPoofMixin {
	/** The entity event vanilla sends when a dead body vanishes. */
	private static final byte POOF = 60;

	@Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
	private void mistinn$noPoof(final byte id, final CallbackInfo ci) {
		if (id == POOF && DeathScene.active() && (Object) this == Minecraft.getInstance().player) {
			ci.cancel();
		}
	}
}
