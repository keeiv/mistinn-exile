package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.multiplayer.RelayWatch;
import life.shizune.mistinn.client.multiplayer.RoomHosting;
import life.shizune.mistinn.room.RoomKey;
import life.shizune.mistinn.story.WorldMode;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer.MultiplayerScope;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opens a single-player world to friends as a room with a fresh key, and closes it again ({@link RoomHosting}). */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerRoomMixin implements RoomHosting {
	@Unique
	private volatile byte @Nullable [] mistinn$key;

	@Override
	public boolean mistinn$publishRoom(final int port) {
		return ((IntegratedServer) (Object) this).publishServer(MultiplayerScope.LAN, port);
	}

	@Override
	public byte @Nullable [] mistinn$roomKey() {
		byte[] key = this.mistinn$key;
		return key == null ? null : key.clone();
	}

	@Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At("HEAD"), cancellable = true)
	private void mistinn$blockTrainingPublish(final MultiplayerScope scope, final int port, final CallbackInfoReturnable<Boolean> cir) {
		if (scope != MultiplayerScope.OFF && WorldMode.of((IntegratedServer) (Object) this) == WorldMode.TRAINING) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At("RETURN"))
	private void mistinn$keyTheRoom(final MultiplayerScope scope, final int port, final CallbackInfoReturnable<Boolean> cir) {
		// Every opening gets a new key, so an old invite stops working once the room has been closed.
		if (cir.getReturnValueZ() && this.mistinn$key == null) {
			this.mistinn$key = RoomKey.newKey();
		}
	}

	@Inject(method = "unpublishServer", at = @At("RETURN"))
	private void mistinn$forgetTheKey(final CallbackInfoReturnable<Boolean> cir) {
		if (!((IntegratedServer) (Object) this).isPublished()) {
			this.mistinn$key = null;
			RelayWatch.closed();
		}
	}
}
