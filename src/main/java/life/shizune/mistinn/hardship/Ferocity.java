package life.shizune.mistinn.hardship;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.boss.Planner;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/**
 * How hard the creatures and keepers of a world fight. Easy and Normal fight as they were made to;
 * Rust Anchor fights with no thought for fairness: shorter tells, blows that come sooner and more
 * often, feints and held blows, a pack that crowds in, keepers that see at once and never let up.
 *
 * @param windup      a creature's windup, times
 * @param feint       the chance a creature's windup is a feint: it stops, then strikes from a short second windup
 * @param hold        the chance a creature holds its blow a few ticks past the end of its windup
 * @param attackGap   the time between a creature's melee blows, times
 * @param bowGap      the time between a skeleton's shots, times
 * @param chase       how far a released blow chases a hunter who backed off, times
 * @param strikers    how many of a pack go in to strike at once
 * @param hearing     how far a hunter's noises carry, times
 * @param speed       a hostile creature's speed, times; above 1 its follow range grows by half as well
 * @param tell        how fast a keeper's tell runs (1: as made)
 * @param swing       how fast a keeper's blows run
 * @param recover     how fast a keeper's recovery runs
 * @param sight       ticks between something happening and a keeper seeing it
 * @param replan      how often, in ticks, a keeper on the move thinks again
 * @param longHold    whether a keeper may also hold a blow for {@link Planner#LONG_DELAY} ticks
 * @param weights     what a keeper's planner values
 * @param poise       full heavy blows in a row that throw a keeper off balance
 * @param pursuit     how fast a keeper walks after its hunter
 */
public record Ferocity(float windup, float feint, float hold, float attackGap, float bowGap, float chase, int strikers, float hearing,
	float speed, float tell, float swing, float recover, int sight, int replan, boolean longHold, Planner.Weights weights, int poise,
	float pursuit) {
	/** The fight as it was made: every creature and keeper of Easy and Normal worlds. */
	public static final Ferocity MADE = new Ferocity(1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 2, 1.0F,
		1.0F, 1.0F, 1.0F, 1.0F, 4, 5, false, Planner.Weights.DEFAULT, 3, 1.0F);
	/** Rust Anchor. */
	public static final Ferocity MERCILESS = new Ferocity(0.55F, 0.3F, 0.25F, 0.5F, 0.55F, 1.8F, 3, 1.5F,
		1.15F, 1.5F, 1.2F, 1.8F, 1, 2, true, new Planner.Weights(1.0, 0.35, 0.3, 0.3, 0.25, 0.15, 1.0, 1.7, 0.6), 4, 1.35F);

	/** The modifier a Rust Anchor world puts on its hostile creatures' speed and follow range (not saved: put on at every load). */
	private static final Identifier HUNT = Mistinn.id("rust_anchor_hunt");

	/** Every hostile creature that comes into a Rust Anchor world moves faster and follows further; keepers keep their own pace. */
	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob instanceof Enemy && !(mob instanceof life.shizune.mistinn.keeper.Keeper)) {
				Ferocity ferocity = of(level);
				if (ferocity.speed() > 1.0F) {
					boost(mob.getAttribute(Attributes.MOVEMENT_SPEED), ferocity.speed() - 1.0F);
					boost(mob.getAttribute(Attributes.FOLLOW_RANGE), 0.5F);
				}
			}
		});
	}

	private static void boost(final @org.jspecify.annotations.Nullable AttributeInstance attribute, final float share) {
		if (attribute != null) {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(HUNT, share, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	public static Ferocity of(final Hardship hardship) {
		return hardship == Hardship.RUST_ANCHOR ? MERCILESS : MADE;
	}

	public static Ferocity of(final MinecraftServer server) {
		return of(Hardship.of(server));
	}

	/**
	 * The ferocity of the world a level belongs to. A client's level fights as made (the server decides), and so does
	 * the station's training ground: the course teaches how creatures and keepers fight, as {@link Hardship#rustAnchorAfield}
	 * keeps the harshest rules for the field.
	 */
	public static Ferocity of(final Level level) {
		MinecraftServer server = level.getServer();
		if (level.isClientSide() || server == null || level.dimension().equals(life.shizune.mistinn.training.TrainingGround.KEY)) {
			return MADE;
		}
		return of(server);
	}

	/** A creature's windup in ticks, from the windup its kind was made with: never shorter than two. */
	public int windupTicks(final int made) {
		return made <= 0 ? made : Math.max(2, Math.round(made * this.windup));
	}

	/** The second windup after a feint: quick, so the real blow comes almost at once. */
	public int afterFeintTicks(final int made) {
		return Math.max(2, Math.round(made * this.windup * 0.35F));
	}

	/** Ticks between two blows or shots, from the interval as made: never under four. */
	public static int gap(final int made, final float scale) {
		return scale == 1.0F ? made : Math.max(4, Math.round(made * scale));
	}
}
