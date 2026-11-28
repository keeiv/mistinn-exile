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
	TWELVE_SLAIN(AchievementService.TWELVE_SLAIN, 12),
	/** Hidden until earned. */
	THIRTEEN_SLAIN(AchievementService.THIRTEEN_SLAIN, 13),
	XIRANG(AchievementService.XIRANG, 3000),
	THE_LAND(AchievementService.THE_LAND, 1500),
	REVIVE_LIMB("revive_limb", 150),
	BELL("bell", 50),
	RESIDUE("residue", 100),
	RICH("rich", 0),
	/** 看穿: a blow parried the moment it lands. */
	PARRY_PERFECT("parry_perfect", 200),
	/** 博物: every creature of the guide's bestiary met. */
	BESTIARY_FULL("bestiary_full", 800),
	/** 鹽霰: three or more rocked back by one salt bomb. */
	SALT_BOMB_THREE("salt_bomb_three", 200),
	/** 拉一把: a downed teammate got back on their feet. */
	RESCUE("rescue", 300),
	/** 站上的回禮: a supply drop collected. */
	SUPPLY_DROP("supply_drop", 150),
	/** 整裝: a whole light or heavy set of one tier worn. */
	FULL_SET("full_set", 300),
	/** 留下的木桶: a lost camp's barrel opened. */
	CAMP_BARREL("camp_barrel", 200),
	/** 晶田豐收: a homestead grown to its third step. */
	HOMESTEAD_GROWN("homestead_grown", 800),
	/** 歸途: from one homestead to another by the map. */
	TRAVEL("travel", 200),
	/** 接過: a fallen tiller's strength taken up. */
	INHERITED("inherited", 300),
	/**
	 * 又一次: dropped into a story again, having reached its end in another world. The two-sided ones are hidden until
	 * earned (the game remembering across worlds is not told before it shows), and pay no salt: they rest on what the
	 * hunter's own client says of its ledger, so they are a keepsake, not a reward.
	 */
	DROPPED_AGAIN("dropped_again", 0),
	/** 又回到晶田: out on the tillers' way again, having walked it to an end in another world. */
	PATH_AGAIN("path_again", 0),
	/** 似曾相識: known by a tiller, in a world that is not the way, as one who walked it. */
	TILLERS_KNOWN("tillers_known", 0),
	/** 受託: a commission of the tillers done. */
	COMMISSION_DONE("commission_done", 200),
	/** 撤出: out of an expedition alive, the first time. */
	EXTRACTED("extracted", 200),
	/** 量天鏡: out of an expedition with Shi-da's sky lens. */
	SKY_LENS("sky_lens", 500),
	/** 遺物: a ground's relic shown to an elder. */
	RELIC_SHOWN("relic_shown", 300);

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
