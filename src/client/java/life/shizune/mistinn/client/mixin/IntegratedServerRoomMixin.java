package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.multiplayer.RoomHosting;
import life.shizune.mistinn.story.WorldMode;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer.MultiplayerScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerRoomMixin implements RoomHosting {
	@Unique
	private Thread mistinn$roomPublisher;

	@Override
	public boolean mistinn$publishRoom(final int port) {
		IntegratedServer server = (IntegratedServer) (Object) this;
		this.mistinn$roomPublisher = Thread.currentThread();
		try {
			return server.publishServer(MultiplayerScope.LAN, port);
		} finally {
			this.mistinn$roomPublisher = null;
		}
	}

	@Override
	public boolean mistinn$isPublishingRoom() {
		return this.mistinn$roomPublisher == Thread.currentThread();
	}

	@Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At("HEAD"), cancellable = true)
	private void mistinn$blockTrainingPublish(final MultiplayerScope scope, final int port, final CallbackInfoReturnable<Boolean> cir) {
		if (scope != MultiplayerScope.OFF && WorldMode.of((IntegratedServer) (Object) this) == WorldMode.TRAINING) {
			cir.setReturnValue(false);
		}
	}
}
