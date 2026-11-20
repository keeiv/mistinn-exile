package life.shizune.mistinn.client.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Connection.class)
public interface RoomConnectionChannelAccessor {
	@Accessor("channel")
	Channel mistinn$getRoomChannel();
}
