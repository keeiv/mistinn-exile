package life.shizune.mistinn.achievement;

import org.jspecify.annotations.Nullable;

/**
 * 成就: what is worth remembering, in the order the guide lists them. {@code icon} is the cell of
 * {@code textures/gui/guide/achievements.png} (4 by 4, 16 px each); {@code reward} the salt the
 * first earning pays.
 */
public enum Achievement {
	FIRST_KILL("first_kill", 0, 50),
	SIX_BRANCHES("six_branches", 1, 600),
	HEAVY_THREE("heavy_three", 2, 200),
	BREAK_WINDUP("break_windup", 3, 200),
	PERFECT_ROLL("perfect_roll", 4, 150),
	SLIDE_KICK("slide_kick", 5, 150),
	FIRST_SKILL("first_skill", 6, 100),
	STAT_FIVE("stat_five", 7, 300),
	ALL_STATS("all_stats", 8, 200),
	SPECIAL_WEAPON("special_weapon", 9, 400),
	SNUFFER(AchievementService.SNUFFER, 10, 1000),
	REVIVE_LIMB("revive_limb", 11, 150),
	BELL("bell", 12, 50),
	RESIDUE("residue", 13, 100),
	RICH("rich", 14, 0);

	public final String id;
	public final int icon;
	public final int reward;

	Achievement(final String id, final int icon, final int reward) {
		this.id = id;
		this.icon = icon;
		this.reward = reward;
	}

	public String key() {
		return "achievement.mistinn." + this.id;
	}

	public String descriptionKey() {
		return "achievement.mistinn." + this.id + ".desc";
	}

	public static @Nullable Achievement byId(final String id) {
		for (Achievement achievement : values()) {
			if (achievement.id.equals(id)) {
				return achievement;
			}
		}
		return null;
	}
}
