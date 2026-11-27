package life.shizune.mistinn.client.achievement;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.client.ledger.Ledger;
import life.shizune.mistinn.client.ledger.LedgerClient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 兩面成就: once earned, an achievement that reads by how the hunter has played, as their own ledger has it (across
 * worlds): a kind hunter is seen as kind, a cruel one told so drily, anyone else plainly. Until it is earned it reads as
 * what it asks. The words are kept as {@code <description>.kind}, {@code .cruel} and {@code .plain}; the server's own
 * description (in the chat's announcement) is the plain condition.
 */
public final class TwoSided {
	private TwoSided() {
	}

	/** The words for this achievement now, or null when it reads as written. */
	public static @Nullable Component description(final Identifier id) {
		if (!id.getNamespace().equals(Mistinn.MOD_ID) || !id.getPath().startsWith("hunt/")) {
			return null;
		}
		Achievement achievement = Achievement.byId(id.getPath().substring("hunt/".length()));
		String side = achievement == null ? null : side(achievement, LedgerClient.ledger());
		if (side == null || !ClientAchievements.has(achievement.id)) {
			return null;
		}
		return Component.translatable(achievement.descriptionKey() + "." + side);
	}

	/** Which side an achievement reads on for this ledger, or null for one that is not two-sided. */
	public static @Nullable String side(final Achievement achievement, final Ledger ledger) {
		return switch (achievement) {
			case DROPPED_AGAIN -> ledger.kindStory() ? "kind" : ledger.cruelStory() ? "cruel" : "plain";
			case PATH_AGAIN, TILLERS_KNOWN -> ledger.kindPath() ? "kind" : ledger.cruelPath() ? "cruel" : "plain";
			default -> null;
		};
	}
}
