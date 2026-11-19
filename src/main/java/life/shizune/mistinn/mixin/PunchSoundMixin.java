package life.shizune.mistinn.mixin;

import life.shizune.mistinn.weapon.Weapons;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plays the weapon's swing sound whenever a player swings it, hit or miss. The server learns of
 * every attack swing through the punch packet; the sound gets quieter the less charged it is.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PunchSoundMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePunch", at = @At("HEAD"))
	private void mistinn$swingSound(final ServerboundPunchPacket packet, final CallbackInfo ci) {
		if (!this.player.level().getServer().isSameThread()) {
			return;
		}
		Weapons.Profile weapon = Weapons.profile(this.player.getMainHandItem());
		if (weapon == null) {
			return;
		}
		float charge = this.player.getAttackStrengthScale(0.5F);
		if (charge < 0.25F) {
			return;
		}
		this.player.level().playSound(
			null,
			this.player.getX(),
			this.player.getEyeY() - 0.3,
			this.player.getZ(),
			weapon.swing(),
			SoundSource.PLAYERS,
			0.45F + 0.55F * charge,
			weapon.pitch() * (0.95F + this.player.getRandom().nextFloat() * 0.1F)
		);
	}
}
