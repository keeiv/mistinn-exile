package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.multiplayer.RoomHosting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps vanilla's verification and disconnect messages for ordinary server connections. */
@Mixin(targets = "net.minecraft.server.network.ServerLoginPacketListenerImpl$1")
public abstract class RoomLoginWorkerMixin {
	@Shadow(remap = false)
	@Final
	private ServerLoginPacketListenerImpl this$0;

	@Redirect(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;isSingleplayer()Z"),
		require = 2, allow = 2)
	private boolean mistinn$allowOfflineFallback(final MinecraftServer server) {
		return server.isSingleplayer() && !((RoomHosting.Login) this.this$0).mistinn$requiresVerifiedGuest();
	}
}
