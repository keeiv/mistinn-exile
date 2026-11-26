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
	/** Hidden until earned; see {@link ApostleService}. */
	APOSTLE(AchievementService.APOSTLE, 5000),
	PIRATE_SHIP(AchievementService.PIRATE_SHIP, 1200),
	FLEET_BREAKER(AchievementService.FLEET_BREAKER, 2000),
	SHORE_HELD(AchievementService.SHORE_HELD, 400),
	SHARPSHOOTER(AchievementService.SHARPSHOOTER, 300),
	ELITE_SLAIN(AchievementService.ELITE_SLAIN, 300),
	ELITE_TRIPLE(AchievementService.ELITE_TRIPLE, 900),
	TIDE(AchievementService.TIDE, 500),
	BOUNTY(AchievementService.BOUNTY, 500),
	RIFT_SEALED(AchievementService.RIFT_SEALED, 600),
	DRAKE_SHATTERED(AchievementService.DRAKE_SHATTERED, 400),
	HOMESTEAD(AchievementService.HOMESTEAD, 500),
	ELDER_SLAIN(AchievementService.ELDER_SLAIN, 1000),
	ELDER_STUNNED(AchievementService.ELDER_STUNNED, 300),
	SIEGE_HELD(AchievementService.SIEGE_HELD, 600),
	ALPHA_SLAIN(AchievementService.ALPHA_SLAIN, 400),
	LIZARD_TAMED(AchievementService.LIZARD_TAMED, 200),
	RUIN_OPENED(AchievementService.RUIN_OPENED, 300),
	RIFT_CALLED(AchievementService.RIFT_CALLED, 300),
	ENDING_ANCHOR(AchievementService.ENDING_ANCHOR, 2000),
	ENDING_INN(AchievementService.ENDING_INN, 2000),
	SEA_SEEN(AchievementService.SEA_SEEN, 2000),
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
