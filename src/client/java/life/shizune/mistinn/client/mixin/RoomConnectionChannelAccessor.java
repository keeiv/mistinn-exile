package life.shizune.mistinn.client.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reaches a connection's channel, where a room's listener marks the guests it let in. */
@Mixin(Connection.class)
public interface RoomConnectionChannelAccessor {
	@Accessor("channel")
	Channel mistinn$getRoomChannel();
}
