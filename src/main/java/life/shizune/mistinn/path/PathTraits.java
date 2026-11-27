package life.shizune.mistinn.path;

import java.util.Random;
import life.shizune.mistinn.Mistinn;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Each of the settlement's people is someone: one thing they are good at and one they are not,
 * the same for the same tiller every time (drawn from the world and their place in the line). A
 * walker may also, kneeling at a memorial, take up the strength of one who fell before them, once.
 */
public final class PathTraits {
	/** Under this light, the dark: the fearful slow in it and the night-eyed see in it. */
	public static final int DARK = 4;

	/** What a tiller is good at. */
	public enum Strength {
		STRONG("strong"), SWIFT("swift"), TOUGH("tough"), HANDY("handy"), NIGHT_EYED("night_eyed");

		public final String id;

		Strength(final String id) {
			this.id = id;
		}

		public Component title() {
			return Component.translatable("path.mistinn.trait." + this.id);
		}
	}

	/** What a tiller is not. */
	public enum Flaw {
		FRAIL("frail"), CLUMSY("clumsy"), FEARFUL("fearful"), FUMBLING("fumbling");

		public final String id;

		Flaw(final String id) {
			this.id = id;
		}

		public Component title() {
			return Component.translatable("path.mistinn.flaw." + this.id);
		}
	}

	private static final Identifier OWN = Mistinn.id("path_strength");
	private static final Identifier LEGACY = Mistinn.id("path_legacy");
	private static final Identifier FLAW = Mistinn.id("path_flaw");

	private PathTraits() {
	}

	private static Random draw(final long seed, final int walker) {
		return new Random(seed ^ (walker + 1L) * 0x9E3779B97F4A7C15L);
	}

	/** A tiller's strength, by the world and their place in the line. */
	public static Strength strength(final long seed, final int walker) {
		return Strength.values()[draw(seed, walker).nextInt(Strength.values().length)];
	}

	/** A tiller's flaw: never the undoing of their own strength (a strong arm is not also a frail one). */
	public static Flaw flaw(final long seed, final int walker) {
		Random random = draw(seed, walker);
		Strength strength = Strength.values()[random.nextInt(Strength.values().length)];
		Flaw flaw;
		do {
			flaw = Flaw.values()[random.nextInt(Flaw.values().length)];
		} while (strength == Strength.STRONG && flaw == Flaw.FRAIL || strength == Strength.SWIFT && flaw == Flaw.CLUMSY
			|| strength == Strength.HANDY && flaw == Flaw.FUMBLING || strength == Strength.NIGHT_EYED && flaw == Flaw.FEARFUL);
		return flaw;
	}

	/** What a world's line draws its people from. */
	public static long seed(final ServerPlayer player, final PathService.Line line) {
		return player.level().getServer().overworld().getSeed() ^ line.domain() * 31L;
	}

	/** Puts the walker's strength, flaw and any strength taken up at a memorial on the hunter (and takes off what was there). */
	public static void apply(final ServerPlayer player, final PathService.Line line) {
		long seed = seed(player, line);
		Strength own = strength(seed, line.walker());
		Strength legacy = line.legacy() >= 0 ? strength(seed, line.legacy()) : null;
		Flaw flaw = flaw(seed, line.walker());
		set(player, OWN, own);
		set(player, LEGACY, legacy == own ? null : legacy);
		switch (flaw) {
			case FRAIL -> modify(player, FLAW, Attributes.ATTACK_DAMAGE, -0.12, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			case CLUMSY -> modify(player, FLAW, Attributes.MOVEMENT_SPEED, -0.07, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			case FUMBLING -> modify(player, FLAW, Attributes.BLOCK_BREAK_SPEED, -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			case FEARFUL -> clear(player, FLAW);
		}
	}

	/** Whether a hunter carries a walker's own strength on their body (a night-eyed one carries it only in the dark). */
	public static boolean carries(final ServerPlayer player) {
		for (Holder<Attribute> attribute : java.util.List.of(Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED, Attributes.ARMOR, Attributes.BLOCK_BREAK_SPEED)) {
			AttributeInstance instance = player.getAttribute(attribute);
			if (instance != null && instance.getModifier(OWN) != null) {
				return true;
			}
		}
		return false;
	}

	/** Takes everything a walker was off a hunter (the way is over, or the world does not walk it). */
	public static void strip(final ServerPlayer player) {
		clear(player, OWN);
		clear(player, LEGACY);
		clear(player, FLAW);
	}

	private static void set(final ServerPlayer player, final Identifier id, final Strength strength) {
		clear(player, id);
		if (strength == null) {
			return;
		}
		switch (strength) {
			case STRONG -> modify(player, id, Attributes.ATTACK_DAMAGE, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			case SWIFT -> modify(player, id, Attributes.MOVEMENT_SPEED, 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			case TOUGH -> modify(player, id, Attributes.ARMOR, 3.0, AttributeModifier.Operation.ADD_VALUE);
			case HANDY -> modify(player, id, Attributes.BLOCK_BREAK_SPEED, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			case NIGHT_EYED -> {
				// seen to in the dark, each second (see darkness)
			}
		}
	}

	private static void modify(final ServerPlayer player, final Identifier id, final Holder<Attribute> attribute, final double amount,
		final AttributeModifier.Operation operation) {
		clear(player, id);
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}

	private static void clear(final ServerPlayer player, final Identifier id) {
		for (Holder<Attribute> attribute : java.util.List.of(Attributes.ATTACK_DAMAGE, Attributes.MOVEMENT_SPEED, Attributes.ARMOR, Attributes.BLOCK_BREAK_SPEED)) {
			AttributeInstance instance = player.getAttribute(attribute);
			if (instance != null) {
				instance.removeModifier(id);
			}
		}
	}

	/** Each second: in the dark the fearful slow, and the night-eyed (by birth or by a memorial) see. */
	public static void darkness(final ServerPlayer player, final PathService.Line line) {
		if (player.isSpectator() || player.isCreative()) {
			return;
		}
		long seed = seed(player, line);
		boolean dark = player.level().getMaxLocalRawBrightness(player.blockPosition()) <= DARK;
		if (!dark) {
			return;
		}
		if (flaw(seed, line.walker()) == Flaw.FEARFUL) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, false, false, true));
		}
		boolean nightEyed = strength(seed, line.walker()) == Strength.NIGHT_EYED || line.legacy() >= 0 && strength(seed, line.legacy()) == Strength.NIGHT_EYED;
		if (nightEyed) {
			MobEffectInstance seeing = player.getEffect(MobEffects.NIGHT_VISION);
			if (seeing == null || seeing.getDuration() < 220) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, true));
			}
		}
	}
}
