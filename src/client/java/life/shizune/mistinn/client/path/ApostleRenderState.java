package life.shizune.mistinn.client.path;

import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.path.ApostleMove;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * 3412．鏽錨使徒 as drawn: the player's shape and the looker's skin (not a player's state: the game would hand that to
 * the player's own renderer), posed by their choreography. Between moves the stance of the branch in
 * hand ({@code anim/apostle/ready_<branch>}) holds the arms, torso and weapon, breathing; a move
 * ({@code anim/apostle/<move>}) is laid over it, taking over each track it has (a step need only move the legs and
 * the body; a string of blows moves everything) and handing back at its ends, so no move starts or stops with a jump.
 * Worked out once per frame here, for the model, the held weapon and the body's root alike.
 */
public class ApostleRenderState extends HumanoidRenderState {
	/** Whose skin they wear: the looker's own. */
	public PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();
	/** The weapon's tracks, as a hunter's own blows have them ({@code third_person.*}). */
	public static final String[] ARMS = {"arm_x", "arm_y", "arm_z", "other_x", "other_y", "other_z", "body_y", "body_x", "front_leg", "back_leg", "item_x", "grip",
		"item_roll"};
	/** Where the weapon's turn and the grip sit among {@link #ARMS}. */
	public static final int ITEM = 10;
	public static final int GRIP = 11;
	/** The weapon turned about its own length, so a cut leads with the edge rather than the flat. */
	public static final int ROLL = 12;
	/** The rest of the body ({@code limbs.*}). */
	public static final String[] LIMBS = {"head_x", "head_y", "body_z", "right_leg_z", "left_leg_z"};
	/** The whole body ({@code root.*}). */
	public static final String[] ROOT = {"x", "y", "z", "yaw", "roll", "pitch"};

	/** Where the clips are read from; the reel may point it at another take to compare. */
	public static String folder = "apostle/";

	/** The move, or null between moves. */
	public @Nullable ApostleMove move;
	/** Ticks into the move, with the frame's part tick. */
	public float moveTicks;
	/** The branch in hand. */
	public @Nullable WeaponStyle style;
	/** Whether the blade is out yet: before it, they walk up carrying it low. */
	public boolean drawn = true;
	/** Whether anything poses them (a stance or a move). */
	public boolean posed;
	public boolean twoHanded;
	public final float[] arms = new float[ARMS.length];
	public final float[] limbs = new float[LIMBS.length];
	public final float[] root = new float[ROOT.length];
	public float pivot = 0.9F;

	/** Works the pose out: the stance, breathing, with the move over it. */
	public void pose() {
		Clip stance = this.style == null ? null : Clip.find(this.drawn ? folder + "ready_" + this.style.id : folder + "carry");
		Clip clip = this.move == null ? null : Clip.find(folder + this.move.id);
		this.posed = stance != null || clip != null;
		java.util.Arrays.fill(this.root, 0.0F);
		if (!this.posed) {
			return;
		}
		float breath = 0.0F;
		if (stance != null) {
			float length = stance.number("length", 60.0F);
			breath = this.ageInTicks % length / length;
		}
		float p = clip == null ? 0.0F : Mth.clamp(this.moveTicks / this.move.length, 0.0F, 1.0F);
		// a move done with its own branch owns the whole pose from first to last; a move done with whatever is in
		// hand (a step, a flick) takes over from the stance and hands back to it
		float w = clip == null ? 0.0F : clip.flag("own") ? 1.0F : clip.weight(p);
		for (int i = 0; i < ARMS.length; i++) {
			this.arms[i] = blend(stance, breath, clip, p, w, "third_person." + ARMS[i]);
		}
		// the weapon's turn in the hand is never blended (a twirl would unwind): a move's own branch sets it outright,
		// any other move turns it by whole turns from where the stance holds it
		if (clip != null && clip.has("third_person.item_x")) {
			this.arms[ITEM] = clip.at("third_person.item_x", p);
		} else if (clip != null && clip.has("third_person.item_dx")) {
			this.arms[ITEM] += clip.at("third_person.item_dx", p);
		}
		if (clip != null && clip.has("third_person.item_roll")) {
			this.arms[ROLL] = clip.at("third_person.item_roll", p);
		}
		for (int i = 0; i < LIMBS.length; i++) {
			this.limbs[i] = blend(stance, breath, clip, p, w, "limbs." + LIMBS[i]);
		}
		if (clip != null) {
			for (int i = 0; i < ROOT.length; i++) {
				this.root[i] = clip.at("root." + ROOT[i], p);
			}
			this.pivot = clip.number("pivot", 0.9F);
		}
		// how many hands are on it: the move's say when it has one ("hands"), else the stance's
		boolean stanceTwo = stance != null && stance.flag("two_handed");
		float hands = clip == null ? 0.0F : clip.number("hands", 0.0F);
		this.twoHanded = hands > 0.0F ? hands >= 2.0F : stanceTwo;
		// the other hand stays on the grip unless a clip lets it off
		float held = stance != null && stance.has("third_person.grip") ? stance.at("third_person.grip", breath) : 1.0F;
		this.arms[GRIP] = clip != null && clip.has("third_person.grip") ? Mth.lerp(w, held, clip.at("third_person.grip", p)) : held;
	}

	private static float blend(final @Nullable Clip stance, final float breath, final @Nullable Clip clip, final float p, final float w, final String track) {
		float base = stance == null ? 0.0F : stance.at(track, breath);
		if (clip == null || !clip.has(track)) {
			return base;
		}
		return Mth.lerp(w, base, clip.at(track, p));
	}

	public float arm(final String track) {
		for (int i = 0; i < ARMS.length; i++) {
			if (ARMS[i].equals(track)) {
				return this.arms[i];
			}
		}
		throw new IllegalArgumentException(track);
	}
}
