package life.shizune.mistinn.enemy;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/** The vanilla model families, and the kind of contact they can use against a fallen hunter. */
public enum EnemyRig {
	UNDEAD(Finisher.GRAB), HUSK(Finisher.GRAB), DROWNED(Finisher.GRAB), SKELETON(Finisher.BLADE),
	ILLAGER(Finisher.BLADE), PIGLIN(Finisher.BLADE), ENDERMAN(Finisher.GRAB), CREAKING(Finisher.GRAB),
	WARDEN(Finisher.CRUSH), CREEPER(null), SPIDER(Finisher.POUNCE), RAVAGER(Finisher.GORE),
	HOGLIN(Finisher.GORE), MOUNT(null), GEL(Finisher.CRUSH), VERMIN(Finisher.POUNCE),
	GHAST(null), PHANTOM(Finisher.POUNCE), VEX(Finisher.BLADE), WITHER(null), BLAZE(null),
	BREEZE(null), SHULKER(null), GUARDIAN(null), NAUTILUS(null), DRAGON(null),
	QUADRUPED(Finisher.GORE), BEE(null);

	public enum Finisher { GRAB, BLADE, CRUSH, POUNCE, GORE }

	private final @Nullable Finisher finisher;

	EnemyRig(final @Nullable Finisher finisher) {
		this.finisher = finisher;
	}

	public @Nullable Finisher finisher() {
		return this.finisher;
	}

	public static @Nullable EnemyRig of(final EntityType<?> type) {
		Identifier id = EntityType.getKey(type);
		if (!id.getNamespace().equals("minecraft")) {
			return null;
		}
		return switch (id.getPath()) {
			case "zombie", "zombie_villager", "giant" -> UNDEAD;
			case "husk" -> HUSK;
			case "drowned" -> DROWNED;
			case "skeleton", "stray", "bogged", "wither_skeleton", "parched" -> SKELETON;
			case "evoker", "illusioner", "pillager", "vindicator", "witch" -> ILLAGER;
			case "piglin", "piglin_brute", "zombified_piglin" -> PIGLIN;
			case "enderman" -> ENDERMAN;
			case "creaking" -> CREAKING;
			case "warden" -> WARDEN;
			case "creeper" -> CREEPER;
			case "cave_spider", "spider" -> SPIDER;
			case "ravager" -> RAVAGER;
			case "hoglin", "zoglin" -> HOGLIN;
			case "camel_husk", "zombie_horse" -> MOUNT;
			case "magma_cube", "slime", "sulfur_cube" -> GEL;
			case "endermite", "silverfish" -> VERMIN;
			case "ghast" -> GHAST;
			case "phantom" -> PHANTOM;
			case "vex" -> VEX;
			case "wither" -> WITHER;
			case "blaze" -> BLAZE;
			case "breeze" -> BREEZE;
			case "shulker" -> SHULKER;
			case "guardian", "elder_guardian" -> GUARDIAN;
			case "zombie_nautilus" -> NAUTILUS;
			case "ender_dragon" -> DRAGON;
			case "wolf", "polar_bear" -> QUADRUPED;
			case "bee" -> BEE;
			default -> null;
		};
	}
}
