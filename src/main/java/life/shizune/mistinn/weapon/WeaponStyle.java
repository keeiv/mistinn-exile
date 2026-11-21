package life.shizune.mistinn.weapon;

import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The weapon branches. Each has its own pace, reach, swing shape (drawn by the client mixins) and
 * swing sound; materials then set how hard it hits and how long it lasts.
 */
public enum WeaponStyle {
	/** 直刃: a balanced straight blade, diagonal slash. */
	SLASH("blade", 3.0, 1.6, 0.0, 12, 16),
	/** 大劍: slow and heavy, raised over the shoulder and brought down. */
	CLEAVE("greatsword", 6.0, 0.7, 0.5, 26, 26),
	/** 長槍: long reach, drawn back and thrust. */
	THRUST("spear", 3.5, 1.1, 1.0, 16, 18),
	/** 短刃: short reach, two quick crossing cuts. */
	FLURRY("dagger", 1.5, 2.6, -0.4, 8, 12),
	/** 戰鎚: a wide sideways swing that knocks foes back. */
	SMASH("hammer", 5.0, 0.8, 0.0, 23, 24),
	/** 鐮刀: a two-handed scythe, reaped up from low; its heavy blow drags foes in. */
	REAP("scythe", 4.5, 1.0, 0.8, 20, 20);

	/** Item id suffix: wooden_blade, iron_hammer, ... */
	public final String id;
	/** Damage a material weapon of this branch adds before its material bonus. */
	public final double baseDamage;
	public final double attacksPerSecond;
	/** Extra reach in blocks over the default 3. */
	public final double reach;
	/**
	 * Swing length in ticks: the whole motion, recovery included. Kept just under the attack
	 * cooldown, so the weapon is back at rest when the next blow is ready.
	 */
	public final int swingTicks;
	/** 蓄力: ticks the use key is held before a heavy blow of this branch is fully charged. */
	public final int chargeTicks;

	WeaponStyle(final String id, final double baseDamage, final double attacksPerSecond, final double reach, final int swingTicks, final int chargeTicks) {
		this.id = id;
		this.baseDamage = baseDamage;
		this.attacksPerSecond = attacksPerSecond;
		this.reach = reach;
		this.swingTicks = swingTicks;
		this.chargeTicks = chargeTicks;
	}

	public SoundEvent swingSound() {
		return switch (this) {
			case SLASH -> MistinnSounds.ANCHOR_BLADE_SWING;
			case CLEAVE -> MistinnSounds.BELL_GREATSWORD_SWING;
			case THRUST -> MistinnSounds.MIST_SPEAR_SWING;
			case FLURRY -> MistinnSounds.SALT_FANG_SWING;
			case SMASH -> MistinnSounds.HAMMER_SWING;
			case REAP -> MistinnSounds.SCYTHE_SWING;
		};
	}

	public static @Nullable WeaponStyle of(final ItemStack stack) {
		Weapons.Profile profile = Weapons.profile(stack);
		return profile == null ? null : profile.style();
	}

	public static @Nullable WeaponStyle of(final Item item) {
		Weapons.Profile profile = Weapons.profile(item);
		return profile == null ? null : profile.style();
	}
}
