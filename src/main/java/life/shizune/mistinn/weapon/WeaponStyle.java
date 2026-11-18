package life.shizune.mistinn.weapon;

import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;
import org.jspecify.annotations.Nullable;

/**
 * The weapon branches. Each one has its own numbers, swing shape (drawn by the client mixins)
 * and sounds.
 */
public enum WeaponStyle {
	/** Anchor Blade: a balanced straight sword, diagonal slash. */
	SLASH(6.0, 1.6, 0.0, SwingAnimationType.WHACK, 7),
	/** Sunken Bell Greatsword: slow, heavy, overhead cleave. */
	CLEAVE(11.0, 0.7, 0.5, SwingAnimationType.WHACK, 15),
	/** Mistwalker Spear: long reach, straight thrust. */
	THRUST(7.0, 1.1, 1.5, SwingAnimationType.WHACK, 9),
	/** Salt Fang: short reach, quick cross cuts. */
	FLURRY(4.0, 2.6, -0.4, SwingAnimationType.WHACK, 4);

	/** Damage the item adds on top of the player's base of 1. */
	public final double damage;
	/** Attacks per second. */
	public final double attacksPerSecond;
	/** Extra reach in blocks over the default 3. */
	public final double reach;
	/** Always WHACK: the client mixins replace the drawn swing for every branch. */
	public final SwingAnimationType animation;
	/** Swing length in ticks. */
	public final int swingTicks;

	WeaponStyle(final double damage, final double attacksPerSecond, final double reach, final SwingAnimationType animation, final int swingTicks) {
		this.damage = damage;
		this.attacksPerSecond = attacksPerSecond;
		this.reach = reach;
		this.animation = animation;
		this.swingTicks = swingTicks;
	}

	public SoundEvent swingSound() {
		return switch (this) {
			case SLASH -> MistinnSounds.ANCHOR_BLADE_SWING;
			case CLEAVE -> MistinnSounds.BELL_GREATSWORD_SWING;
			case THRUST -> MistinnSounds.MIST_SPEAR_SWING;
			case FLURRY -> MistinnSounds.SALT_FANG_SWING;
		};
	}

	public SoundEvent hitSound() {
		return switch (this) {
			case SLASH -> MistinnSounds.ANCHOR_BLADE_HIT;
			case CLEAVE -> MistinnSounds.BELL_GREATSWORD_HIT;
			case THRUST -> MistinnSounds.MIST_SPEAR_HIT;
			case FLURRY -> MistinnSounds.SALT_FANG_HIT;
		};
	}

	public static @Nullable WeaponStyle of(final ItemStack stack) {
		return stack.isEmpty() ? null : of(stack.getItem());
	}

	public static @Nullable WeaponStyle of(final Item item) {
		if (item == MistinnItems.ANCHOR_BLADE) {
			return SLASH;
		}
		if (item == MistinnItems.BELL_GREATSWORD) {
			return CLEAVE;
		}
		if (item == MistinnItems.MIST_SPEAR) {
			return THRUST;
		}
		if (item == MistinnItems.SALT_FANG) {
			return FLURRY;
		}
		return null;
	}
}
