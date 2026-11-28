package life.shizune.mistinn.client.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The boss bars showing, so what sits at the top of the screen can make room below them. */
@Mixin(BossHealthOverlay.class)
public interface BossOverlayAccessor {
	@Accessor("events")
	Map<UUID, LerpingBossEvent> mistinn$events();
}
