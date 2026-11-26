package life.shizune.mistinn.achievement;

import org.jspecify.annotations.Nullable;

/**
 * 成就: what is worth remembering, in the order the tab lists them. Each is an advancement of the same id under
 * {@code data/mistinn/advancement/hunt}; {@code reward} is the salt the first earning pays.
 */
public enum Achievement {
	FIRST_KILL("first_kill", 50),
	SIX_BRANCHES("six_branches", 600),
	HEAVY_THREE("heavy_three", 200),
	BREAK_WINDUP("break_windup", 200),
	PERFECT_ROLL("perfect_roll", 150),
	SLIDE_KICK("slide_kick", 150),
	FIRST_SKILL("first_skill", 100),
	STAT_FIVE("stat_five", 300),
	ALL_STATS("all_stats", 200),
	SPECIAL_WEAPON("special_weapon", 400),
	SNUFFER(AchievementService.SNUFFER, 1000),
	SUNKEN_BELL(AchievementService.SUNKEN_BELL, 1200),
	RUST_WHEEL(AchievementService.RUST_WHEEL, 1500),
	PIRATE_SHIP(AchievementService.PIRATE_SHIP, 1200),
	FLEET_BREAKER(AchievementService.FLEET_BREAKER, 2000),
	SHORE_HELD(AchievementService.SHORE_HELD, 400),
	SHARPSHOOTER(AchievementService.SHARPSHOOTER, 300),
	ENDING_ANCHOR(AchievementService.ENDING_ANCHOR, 2000),
	ENDING_INN(AchievementService.ENDING_INN, 2000),
	REVIVE_LIMB("revive_limb", 150),
	BELL("bell", 50),
	RESIDUE("residue", 100),
	RICH("rich", 0);

	public final String id;
	public final int reward;

	Achievement(final String id, final int reward) {
		this.id = id;
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
