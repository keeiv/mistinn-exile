package life.shizune.mistinn.client.mixin;

import com.mojang.authlib.GameProfile;
import life.shizune.mistinn.client.multiplayer.RoomHosting;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class RoomLoginMixin implements RoomHosting.Login {
	@Shadow
	@Final
	private Connection connection;

	@Override
	public boolean mistinn$requiresVerifiedGuest() {
		return Boolean.TRUE.equals(((RoomConnectionChannelAccessor) this.connection).mistinn$getRoomChannel()
			.attr(RoomHosting.VERIFIED_GUEST).get());
	}

	@Redirect(method = "handleHello", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/MinecraftServer;getSingleplayerProfile()Lcom/mojang/authlib/GameProfile;"))
	private @Nullable GameProfile mistinn$localOwnerProfile(final MinecraftServer server) {
		// Only vanilla connections can use the integrated server's owner-profile shortcut.
		return mistinn$requiresVerifiedGuest() ? null : server.getSingleplayerProfile();
	}

	@Redirect(method = "handleHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"))
	private boolean mistinn$requireAccountChallenge(final MinecraftServer server) {
		return mistinn$requiresVerifiedGuest() || server.usesAuthentication();
	}
}
