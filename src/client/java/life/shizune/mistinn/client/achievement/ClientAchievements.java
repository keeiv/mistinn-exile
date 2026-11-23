package life.shizune.mistinn.client.achievement;

import life.shizune.mistinn.achievement.AchievementService;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.jspecify.annotations.Nullable;

/**
 * 成就（客戶端）: the local hunter's achievements, as the game's advancements. They are read from what the server
 * sent, and shown on the game's own advancements screen, opened on the hunter's tab.
 */
public final class ClientAchievements {
	private ClientAchievements() {
	}

	private static @Nullable ClientAdvancements advancements() {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		return connection == null ? null : connection.getAdvancements();
	}

	/** Whether the local hunter has this achievement, or this mark toward one. */
	public static boolean has(final String id) {
		ClientAdvancements advancements = advancements();
		if (advancements == null) {
			return false;
		}
		AchievementService.Target target = AchievementService.target(id);
		AdvancementHolder holder = advancements.get(target.advancement());
		AdvancementProgress progress = holder == null ? null : advancements.progress().get(holder);
		if (progress == null) {
			return false;
		}
		if (target.criterion() == null) {
			return progress.isDone();
		}
		CriterionProgress criterion = progress.getCriterion(target.criterion());
		return criterion != null && criterion.isDone();
	}

	/** Opens the advancements screen on the hunter's tab; closing it goes back to {@code back}. */
	public static void open(final Minecraft mc, final @Nullable Screen back) {
		ClientAdvancements advancements = advancements();
		if (advancements == null) {
			return;
		}
		AdvancementHolder root = advancements.get(AchievementService.ROOT);
		if (root != null) {
			advancements.setSelectedTab(root, true);
		}
		mc.gui.setScreen(new AdvancementsScreen(advancements, back));
	}
}
