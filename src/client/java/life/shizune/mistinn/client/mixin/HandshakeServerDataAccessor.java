package life.shizune.mistinn.client.mixin;

import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Which server a login is going to, so the room key answer can pick the right invite. */
@Mixin(ClientHandshakePacketListenerImpl.class)
public interface HandshakeServerDataAccessor {
	@Accessor("serverData")
	ServerData mistinn$serverData();
}
