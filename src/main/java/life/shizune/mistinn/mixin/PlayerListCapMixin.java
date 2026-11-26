package life.shizune.mistinn.mixin;

import java.net.SocketAddress;
import life.shizune.mistinn.team.TeamService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A world takes three hunters at most, on any server: the fourth is turned away at the door. */
@Mixin(PlayerList.class)
public abstract class PlayerListCapMixin {
	@Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
	private void mistinn$capHunters(final SocketAddress address, final NameAndId nameAndId, final CallbackInfoReturnable<Component> cir) {
		if (cir.getReturnValue() != null) {
			return;
		}
		PlayerList list = (PlayerList) (Object) this;
		// the same hunter logging in again takes the place of their old connection, not a new one
		if (TeamService.refuses(list.getPlayerCount(), list.getPlayer(nameAndId.id()) != null)) {
			cir.setReturnValue(TeamService.fullReason());
		}
	}
}
