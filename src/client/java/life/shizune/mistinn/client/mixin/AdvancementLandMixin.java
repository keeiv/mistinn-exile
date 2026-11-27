package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.path.PathService;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The achievements whose words on the advancements screen are not fixed. 大地 reads by how many fell: on a world
 * walking the path of longing whose way reached the sea over some of the fallen, its description weighs that many.
 * The two-sided ones read by the hunter's ledger ({@link life.shizune.mistinn.client.achievement.TwoSided}).
 */
@Mixin(AdvancementWidget.class)
public abstract class AdvancementLandMixin {
	@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/DisplayInfo;description()Lnet/minecraft/network/chat/Component;"))
	private Component mistinn$land(final DisplayInfo display, final Minecraft minecraft, final AdvancementNode node, final DisplayInfo same) {
		Component sided = life.shizune.mistinn.client.achievement.TwoSided.description(node.holder().id());
		if (sided != null) {
			return sided;
		}
		if (node.holder().id().equals(Mistinn.id("hunt/" + life.shizune.mistinn.achievement.AchievementService.THE_LAND)) && minecraft.player != null) {
			PathService.View view = minecraft.player.getAttached(PathService.VIEW);
			if (view != null && view.stage() == PathService.Step.ENDED && view.fallen() > 0) {
				return PathService.land(view.fallen());
			}
		}
		return display.description();
	}
}
