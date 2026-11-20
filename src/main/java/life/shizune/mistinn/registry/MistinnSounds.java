package life.shizune.mistinn.registry;

import life.shizune.mistinn.Mistinn;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** The mod's sound events; the ids match assets/mistinn/sounds.json, the sources are listed in CREDITS.md. */
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
	// timed uses of the medical and repair items: CC0 foley (see CREDITS.md) and noise-modelled friction
	public static final SoundEvent INJECT_CAP = register("treatment.inject.cap");
	public static final SoundEvent INJECT_STAB = register("treatment.inject.stab");
	public static final SoundEvent INJECT_PUSH = register("treatment.inject.push");
	public static final SoundEvent INJECT_OUT = register("treatment.inject.out");
	public static final SoundEvent INJECT_FINISH = register("treatment.inject.finish");
	public static final SoundEvent SURGERY_OPEN = register("treatment.surgery.open");
	public static final SoundEvent SURGERY_CUT = register("treatment.surgery.cut");
	public static final SoundEvent SURGERY_STITCH = register("treatment.surgery.stitch");
	public static final SoundEvent SURGERY_CLINK = register("treatment.surgery.clink");
	public static final SoundEvent SURGERY_TEAR = register("treatment.surgery.tear");
	public static final SoundEvent SURGERY_FINISH = register("treatment.surgery.finish");
	public static final SoundEvent REPAIR_OPEN = register("treatment.repair.open");
	public static final SoundEvent REPAIR_PLATE = register("treatment.repair.plate");
	public static final SoundEvent REPAIR_HAMMER = register("treatment.repair.hammer");
	public static final SoundEvent REPAIR_STRAP = register("treatment.repair.strap");
	public static final SoundEvent REPAIR_FINISH = register("treatment.repair.finish");
	public static final SoundEvent PILLS_CAP = register("treatment.pills.cap");
	public static final SoundEvent PILLS_RATTLE = register("treatment.pills.rattle");
	public static final SoundEvent PILLS_POUR = register("treatment.pills.pour");
	public static final SoundEvent PILLS_SWALLOW = register("treatment.pills.swallow");
	public static final SoundEvent PILLS_FINISH = register("treatment.pills.finish");
	/** A use broken off: the kit fumbled back into the pouch. */
	public static final SoundEvent TREATMENT_INTERRUPT = register("treatment.interrupt");
	/** The painkillers wear off: a breath drawn in through the teeth as the pain comes back. */
	public static final SoundEvent PAINKILLER_WEAR_OFF = register("treatment.wear_off");
	// 熄燈人, the Lighthouse Keeper
	public static final SoundEvent SNUFFER_AMBIENT = register("entity.snuffer.ambient");
	public static final SoundEvent SNUFFER_HURT = register("entity.snuffer.hurt");
	public static final SoundEvent SNUFFER_DEATH = register("entity.snuffer.death");
	public static final SoundEvent SNUFFER_STEP = register("entity.snuffer.step");
	// the grid inventory, heard only by its owner
	public static final SoundEvent GRID_PICKUP = register("ui.grid.pickup");
	public static final SoundEvent GRID_PLACE = register("ui.grid.place");
	public static final SoundEvent GRID_ROTATE = register("ui.grid.rotate");
	public static final SoundEvent GRID_REJECT = register("ui.grid.reject");
	// putting gear on: padded cloth (N, I), plates (II and up), a backpack, a chest rig
	public static final Holder<SoundEvent> EQUIP_CLOTH = registerHolder("gear.equip.cloth");
	public static final Holder<SoundEvent> EQUIP_PLATE = registerHolder("gear.equip.plate");
	public static final SoundEvent EQUIP_BACKPACK = register("gear.equip.backpack");
	public static final SoundEvent EQUIP_RIG = register("gear.equip.rig");
	// rolls and dives
	public static final SoundEvent ROLL = register("player.roll");
	public static final SoundEvent DIVE = register("player.dive");
	public static final SoundEvent LAND = register("player.land");
	// the body
	public static final SoundEvent LIMB_DESTROYED = register("body.limb_destroyed");
	public static final SoundEvent HEARTBEAT = register("body.heartbeat");
	// the radio at the station
	public static final SoundEvent RADIO_STATIC = register("block.radio.static");
	public static final SoundEvent RADIO_TUNE = register("block.radio.tune");
	// ambience: wind and surf in the fog, wind in a dead lighthouse
	public static final SoundEvent AMBIENT_FOG = register("ambient.fog");
	public static final SoundEvent AMBIENT_LIGHTHOUSE = register("ambient.lighthouse");

	private MistinnSounds() {
	}

	private static SoundEvent register(final String name) {
		Identifier id = Mistinn.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** For the places that take a holder, such as an equippable's equip sound. */
	private static Holder<SoundEvent> registerHolder(final String name) {
		Identifier id = Mistinn.id(name);
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
		// Loading the class registers the fields above.
	}
}
