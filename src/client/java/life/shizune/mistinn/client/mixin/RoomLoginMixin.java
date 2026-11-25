package life.shizune.mistinn.client.mixin;

import com.mojang.authlib.GameProfile;
import life.shizune.mistinn.client.multiplayer.RoomGate;
import life.shizune.mistinn.client.multiplayer.RoomHosting;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A room's guest logs in with the name they chose, Microsoft account or not: the room key checked
 * by {@link RoomGate} keeps strangers out instead. A guest never gets the host's profile, and a
 * name already in the world is turned away.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class RoomLoginMixin implements RoomHosting.Login {
	@Shadow
	@Final
	private MinecraftServer server;

	@Shadow
	@Final
	private Connection connection;

	@Shadow
	private String requestedUsername;

	@Shadow
	public abstract void disconnect(Component reason);

	@Override
	public boolean mistinn$isGuest() {
		return this.server instanceof IntegratedServer && !this.connection.isMemoryConnection();
	}

	@Override
	public String mistinn$requestedName() {
		return this.requestedUsername == null ? "" : this.requestedUsername;
	}

	@Inject(method = "handleHello", at = @At("HEAD"), cancellable = true)
	private void mistinn$oneOfEachName(final ServerboundHelloPacket packet, final CallbackInfo ci) {
		if (!mistinn$isGuest()) {
			return;
		}
		String name = packet.name();
		GameProfile host = this.server.getSingleplayerProfile();
		boolean taken = host != null && host.name().equalsIgnoreCase(name)
			|| this.server.getPlayerList().getPlayers().stream().anyMatch(p -> p.getGameProfile().name().equalsIgnoreCase(name));
		if (taken) {
			disconnect(RoomGate.reason("same_name", name));
			ci.cancel();
		}
	}

	@Redirect(method = "handleHello", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/MinecraftServer;getSingleplayerProfile()Lcom/mojang/authlib/GameProfile;"))
	private @Nullable GameProfile mistinn$localOwnerProfile(final MinecraftServer owner) {
		// Only the host's own client, inside this process, may take the owner's profile by name.
		return mistinn$isGuest() ? null : owner.getSingleplayerProfile();
	}

	@Redirect(method = "handleHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"))
	private boolean mistinn$noAccountChallenge(final MinecraftServer owner) {
		// Guests from the offline launcher have no Microsoft session to prove: the room key does that job.
		return !mistinn$isGuest() && owner.usesAuthentication();
	}
}
