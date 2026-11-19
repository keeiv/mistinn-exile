package life.shizune.mistinn.client.mixin;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What the vanilla death screen was opened with, for the mission-end screen that replaces it. */
@Mixin(DeathScreen.class)
public interface DeathScreenAccessor {
	@Accessor("causeOfDeath")
	@Nullable Component mistinn$cause();

	@Accessor("hardcore")
	boolean mistinn$hardcore();
}
