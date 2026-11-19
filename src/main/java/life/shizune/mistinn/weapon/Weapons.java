package life.shizune.mistinn.weapon;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Every weapon the mod adds, with its branch and the sounds it makes. Ordinary weapons pair the
 * branch's swing with their material's impact; the named weapons have sounds of their own.
 */
public final class Weapons {
	/** {@code material} is null for the named weapons (錨刃, 沉鐘大劍, ...). */
	public record Profile(WeaponStyle style, @Nullable WeaponMaterial material, SoundEvent swing, SoundEvent hit, float pitch) {
	}

	private static final Map<Item, Profile> PROFILES = new IdentityHashMap<>();

	private Weapons() {
	}

	public static void add(final Item item, final Profile profile) {
		PROFILES.put(item, profile);
	}

	public static @Nullable Profile profile(final ItemStack stack) {
		return stack.isEmpty() ? null : PROFILES.get(stack.getItem());
	}

	public static @Nullable Profile profile(final Item item) {
		return PROFILES.get(item);
	}
}
