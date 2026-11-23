package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.food.HungerCap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The drumsticks damp food has taken for good are drawn mouldy on the hunger bar, from the left end in. */
@Mixin(Hud.class)
public abstract class HudFoodMixin {
	@Unique
	private static final Identifier LOST = Mistinn.id("hud/food_lost");

	@Inject(method = "extractFood", at = @At("TAIL"))
	private void mistinn$lostFood(final GuiGraphicsExtractor graphics, final Player player, final int top, final int right, final CallbackInfo ci) {
		int cap = HungerCap.of(player);
		// vanilla draws drumstick i (two food points) at right - i * 8 - 9; those at or past the cap are lost
		for (int i = cap / 2; i < HungerCap.FULL / 2; i++) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOST, right - i * 8 - 9, top, 9, 9);
		}
	}
}
