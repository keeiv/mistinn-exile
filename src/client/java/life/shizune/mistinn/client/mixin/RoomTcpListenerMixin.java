package life.shizune.mistinn.client.mixin;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import life.shizune.mistinn.client.multiplayer.RoomHosting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerConnectionListener.class)
public abstract class RoomTcpListenerMixin {
	@Shadow
	@Final
	private MinecraftServer server;

	@Redirect(method = "startTcpServerListener",
		at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/ServerBootstrap;bind()Lio/netty/channel/ChannelFuture;", remap = false))
	private ChannelFuture mistinn$bindRoomListener(final ServerBootstrap bootstrap) {
		if (this.server instanceof RoomHosting room && room.mistinn$isPublishingRoom()) {
			// A pending login keeps this listener's policy even after the room closes or reopens.
			bootstrap.childAttr(RoomHosting.VERIFIED_GUEST, true);
		}
		return bootstrap.bind();
	}
}
