package life.shizune.mistinn.client.mixin;

import com.mojang.authlib.GameProfile;
import life.shizune.mistinn.client.skin.SyncedSkins;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player who sent their own skin wears it, wherever the game asks the player list for skins. */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoSkinMixin {
	@Shadow
	public abstract GameProfile getProfile();

	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void mistinn$sentSkin(final CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin sent = SyncedSkins.get(getProfile().id());
		if (sent != null) {
			cir.setReturnValue(sent);
		}
	}
}
