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
	public static final SoundEvent SCYTHE_SWING = register("weapon.scythe.swing");
	/** 蓄滿: a heavy blow is fully charged. */
	public static final SoundEvent CHARGE_FULL = register("player.charge_full");
	/** 成就達成 */
	public static final SoundEvent ACHIEVEMENT = register("ui.achievement");
	/** 熄燈鉤鐮: the hook bites and drags. */
	public static final SoundEvent HOOK_PULL = register("weapon.snuffer_hook.pull");
	public static final SoundEvent HIT_WOOD = register("weapon.hit.wood");
	public static final SoundEvent HIT_STONE = register("weapon.hit.stone");
	public static final SoundEvent HIT_IRON = register("weapon.hit.iron");
	public static final SoundEvent HIT_DIAMOND = register("weapon.hit.diamond");
	public static final SoundEvent HIT_NETHERITE = register("weapon.hit.netherite");
	public static final SoundEvent GUIDE_OPEN = register("item.guide.open");
	public static final SoundEvent SKILL_UNLOCK = register("ui.skill.unlock");
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
	public static final SoundEvent SNUFFER_SWEEP = register("entity.snuffer.sweep");
	public static final SoundEvent SNUFFER_SLAM = register("entity.snuffer.slam");
	public static final SoundEvent SNUFFER_HOOK = register("entity.snuffer.hook");
	public static final SoundEvent SNUFFER_SLIDE = register("entity.snuffer.slide");
	public static final SoundEvent SNUFFER_RUSH = register("entity.snuffer.rush");
	public static final SoundEvent SNUFFER_STEP_LEFT = register("entity.snuffer.step_left");
	public static final SoundEvent SNUFFER_STEP_RIGHT = register("entity.snuffer.step_right");
	public static final SoundEvent SNUFFER_BACK_STEP = register("entity.snuffer.back_step");
	public static final SoundEvent SNUFFER_CIRCLE = register("entity.snuffer.circle");
	public static final SoundEvent SNUFFER_BACK_JUMP = register("entity.snuffer.back_jump");
	public static final SoundEvent SNUFFER_DOUBLE_SWEEP = register("entity.snuffer.double_sweep");
	public static final SoundEvent SNUFFER_SPIN = register("entity.snuffer.spin");
	public static final SoundEvent SNUFFER_THRUST = register("entity.snuffer.thrust");
	public static final SoundEvent SNUFFER_TRIPLE_THRUST = register("entity.snuffer.triple_thrust");
	public static final SoundEvent SNUFFER_OVERHEAD = register("entity.snuffer.overhead");
	public static final SoundEvent SNUFFER_UPSWING = register("entity.snuffer.upswing");
	public static final SoundEvent SNUFFER_LEG_SWEEP = register("entity.snuffer.leg_sweep");
	public static final SoundEvent SNUFFER_BUTT = register("entity.snuffer.butt");
	public static final SoundEvent SNUFFER_LANTERN_BASH = register("entity.snuffer.lantern_bash");
	public static final SoundEvent SNUFFER_GRAB_THROW = register("entity.snuffer.grab_throw");
	public static final SoundEvent SNUFFER_SLAM_WAVE = register("entity.snuffer.slam_wave");
	public static final SoundEvent SNUFFER_HOOK_SLAM = register("entity.snuffer.hook_slam");
	public static final SoundEvent SNUFFER_HOOK_THRUST = register("entity.snuffer.hook_thrust");
	public static final SoundEvent SNUFFER_CHAIN_SWEEP = register("entity.snuffer.chain_sweep");
	public static final SoundEvent SNUFFER_TWIRL = register("entity.snuffer.twirl");
	public static final SoundEvent SNUFFER_LANTERN_SWING = register("entity.snuffer.lantern_swing");
	public static final SoundEvent SNUFFER_CHAIN_PULL = register("entity.snuffer.chain_pull");
	public static final SoundEvent SNUFFER_CROUCH = register("entity.snuffer.crouch");
	public static final SoundEvent SNUFFER_SNUFF = register("entity.snuffer.snuff");
	public static final SoundEvent SNUFFER_MIST_AMBUSH = register("entity.snuffer.mist_ambush");
	public static final SoundEvent SNUFFER_SOUL_SWEEP = register("entity.snuffer.soul_sweep");
	// 沉鐘
	public static final SoundEvent SUNKEN_BELL_RING = register("entity.sunken_bell.ring");
	public static final SoundEvent SUNKEN_BELL_RING_CRACKED = register("entity.sunken_bell.ring_cracked");
	public static final SoundEvent SUNKEN_BELL_TOLL = register("entity.sunken_bell.toll");
	public static final SoundEvent SUNKEN_BELL_DOUBLE_TOLL = register("entity.sunken_bell.double_toll");
	public static final SoundEvent SUNKEN_BELL_DEEP_TOLL = register("entity.sunken_bell.deep_toll");
	public static final SoundEvent SUNKEN_BELL_HUM = register("entity.sunken_bell.hum");
	public static final SoundEvent SUNKEN_BELL_CLAPPER_SWEEP = register("entity.sunken_bell.clapper_sweep");
	public static final SoundEvent SUNKEN_BELL_CLAPPER_SMASH = register("entity.sunken_bell.clapper_smash");
	public static final SoundEvent SUNKEN_BELL_RAM = register("entity.sunken_bell.bell_ram");
	public static final SoundEvent SUNKEN_BELL_CHAIN_LASH = register("entity.sunken_bell.chain_lash");
	public static final SoundEvent SUNKEN_BELL_UNDERTOW = register("entity.sunken_bell.undertow");
	public static final SoundEvent SUNKEN_BELL_WADE = register("entity.sunken_bell.wade");
	public static final SoundEvent SUNKEN_BELL_AMBIENT = register("entity.sunken_bell.ambient");
	public static final SoundEvent SUNKEN_BELL_HURT = register("entity.sunken_bell.hurt");
	public static final SoundEvent SUNKEN_BELL_DEATH = register("entity.sunken_bell.death");
	public static final SoundEvent SUNKEN_BELL_STEP = register("entity.sunken_bell.step");
	public static final SoundEvent SUNKEN_BELL_DROP = register("entity.sunken_bell.bell_drop");
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
	public static final SoundEvent SLIDE = register("player.slide");
	public static final SoundEvent LAND = register("player.land");
	// the body
	public static final SoundEvent LIMB_DESTROYED = register("body.limb_destroyed");
	public static final SoundEvent HEARTBEAT = register("body.heartbeat");
	// the radio at the station
	public static final SoundEvent RADIO_STATIC = register("block.radio.static");
	public static final SoundEvent RADIO_TUNE = register("block.radio.tune");
	// the shore at the title, and the Snuffer's fight and its turn into darkness
	public static final SoundEvent MUSIC_SHORE = register("music.shore");
	public static final SoundEvent MUSIC_KEEPER = register("music.keeper");
	public static final SoundEvent MUSIC_KEEPER_TURN = register("music.keeper_turn");
	public static final SoundEvent MUSIC_KEEPER_DARK = register("music.keeper_dark");

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
