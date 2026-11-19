package life.shizune.mistinn.registry;

import life.shizune.mistinn.Mistinn;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Every sound is synthesised by tools/make_assets.py; the ids match assets/mistinn/sounds.json. */
public final class MistinnSounds {
	public static final SoundEvent ANCHOR_BLADE_SWING = register("weapon.anchor_blade.swing");
	public static final SoundEvent ANCHOR_BLADE_HIT = register("weapon.anchor_blade.hit");
	public static final SoundEvent BELL_GREATSWORD_SWING = register("weapon.bell_greatsword.swing");
	public static final SoundEvent BELL_GREATSWORD_HIT = register("weapon.bell_greatsword.hit");
	public static final SoundEvent MIST_SPEAR_SWING = register("weapon.mist_spear.swing");
	public static final SoundEvent MIST_SPEAR_HIT = register("weapon.mist_spear.hit");
	public static final SoundEvent SALT_FANG_SWING = register("weapon.salt_fang.swing");
	public static final SoundEvent SALT_FANG_HIT = register("weapon.salt_fang.hit");
	public static final SoundEvent HAMMER_SWING = register("weapon.hammer.swing");
	public static final SoundEvent HIT_WOOD = register("weapon.hit.wood");
	public static final SoundEvent HIT_STONE = register("weapon.hit.stone");
	public static final SoundEvent HIT_IRON = register("weapon.hit.iron");
	public static final SoundEvent HIT_DIAMOND = register("weapon.hit.diamond");
	public static final SoundEvent HIT_NETHERITE = register("weapon.hit.netherite");
	public static final SoundEvent GUIDE_OPEN = register("item.guide.open");
	public static final SoundEvent GOLDEN_BELL_REST = register("block.golden_bell.rest");
	public static final SoundEvent ORE_BELL_BREAK = register("block.ore_bell.break");
	public static final SoundEvent RESIDUE_FORM = register("residue.form");
	public static final SoundEvent RESIDUE_RECOVER = register("residue.recover");
	public static final SoundEvent SALT_GAIN = register("salt.gain");

	private MistinnSounds() {
	}

	private static SoundEvent register(final String name) {
		Identifier id = Mistinn.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
		// Loading the class registers the fields above.
	}
}
