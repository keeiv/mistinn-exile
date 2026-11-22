package life.shizune.mistinn.enemy;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/** The vanilla model families, and how long and how far they wind up a melee blow. */
public enum EnemyRig {
	UNDEAD(7, 0.45F), HUSK(8, 0.45F), DROWNED(7, 0.45F),
	SKELETON(6, 0.40F), ILLAGER(6, 0.45F), PIGLIN(6, 0.45F),
	ENDERMAN(5, 0.50F), CREAKING(8, 0.40F), WARDEN(10, 0.60F),
	CREEPER(0, 0.0F), SPIDER(5, 0.55F), RAVAGER(10, 0.60F),
	HOGLIN(8, 0.60F), MOUNT(0, 0.0F), GEL(0, 0.0F), VERMIN(3, 0.30F),
	GHAST(0, 0.0F), PHANTOM(0, 0.0F), VEX(0, 0.0F), WITHER(0, 0.0F),
	BLAZE(0, 0.0F), BREEZE(0, 0.0F), SHULKER(0, 0.0F), GUARDIAN(0, 0.0F), NAUTILUS(0, 0.0F),
	DRAGON(0, 0.0F), QUADRUPED(6, 0.50F), BEE(0, 0.0F);

	private final int windupTicks;
	private final float lunge;

	EnemyRig(final int windupTicks, final float lunge) {
		this.windupTicks = windupTicks;
		this.lunge = lunge;
	}

	/** Ticks a melee blow is held back while the creature winds up; 0 for no windup. */
	public int windupTicks() {
		return this.windupTicks;
	}

	/** How far, in blocks, the creature lunges when the wound-up blow is released. */
	public float lunge() {
		return this.lunge;
	}


	/**
	 * Whether the creature can tell where its target is. Wardens are blind in vanilla (no line of sight at all)
	 * and track by vibration, so they always know; everyone else needs to see the target.
	 */
	public static boolean perceives(final net.minecraft.world.entity.Mob mob, final net.minecraft.world.entity.Entity target) {
		return mob instanceof net.minecraft.world.entity.monster.warden.Warden || mob.hasLineOfSight(target);
	}

	/**
	 * The vanilla creature a creature of the mod fights like (its rig, its finisher, its windup cry):
	 * the salt corpse is a husk underneath. Any other creature is its own kin.
	 */
	public static Identifier kin(final EntityType<?> type) {
		Identifier id = EntityType.getKey(type);
		return id.equals(life.shizune.mistinn.Mistinn.id("salt_corpse")) ? Identifier.withDefaultNamespace("husk") : id;
	}

	public static @Nullable EnemyRig of(final EntityType<?> type) {
		Identifier id = kin(type);
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
