package life.shizune.mistinn.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Moves a slot on screen. Only the hunter inventory screen uses it, on the client, to lay the
 * player's own slots out its way; it puts them back when it closes. The server never reads slot
 * positions.
 */
@Mixin(Slot.class)
public interface SlotAccessor {
	@Mutable
	@Accessor("x")
	void mistinn$setX(int x);

	@Mutable
	@Accessor("y")
	void mistinn$setY(int y);
}
