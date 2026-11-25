package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.multiplayer.RelayWatch;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Picks the relay's address out of the line the relay mod writes to chat ({@link RelayWatch}). */
@Mixin(ChatComponent.class)
public abstract class RelayChatMixin {
	@Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true)
	private void mistinn$relayAddress(final Component message, final CallbackInfo ci) {
		if (RelayWatch.onChat(message)) {
			ci.cancel();
		}
	}
}
