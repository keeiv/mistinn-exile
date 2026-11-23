package life.shizune.mistinn.hardship;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.weapon.Weapons;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Rust Anchor's weapon rule: what a hunter brings from outside is poor work by the station's
 * measure. Ordinary weapons (all but netherite) hit for half and wear out two and a half times
 * as fast; the station's own weapons wear a little faster and hit a little harder.
 */
public final class RustAnchorWeapons {
	/** Whose weapon it is, by Rust Anchor's measure. */
	public enum Kind {
		/** An ordinary weapon from outside, netherite excepted. */
		ORDINARY(2.5F, -0.5),
		/** One of the station's own. */
		STATION(1.15F, 0.05),
		/** Anything else, netherite included: as it is. */
		NONE(1.0F, 0.0);

		/** Durability a use takes, as a multiple of what it would. */
		public final float wear;
		/** The change to the hunter's attack while it is held, as a share of the total. */
		public final double attack;

		Kind(final float wear, final double attack) {
			this.wear = wear;
			this.attack = attack;
		}
	}

	private static final Identifier ATTACK_ID = Mistinn.id("rust_anchor_weapon");

	private RustAnchorWeapons() {
	}

	public static Kind kind(final ItemStack stack) {
		if (stack.isEmpty()) {
			return Kind.NONE;
		}
		if (Weapons.profile(stack) != null) {
			return Kind.STATION;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (!id.getNamespace().equals("minecraft") || id.getPath().startsWith("netherite_")) {
			return Kind.NONE;
		}
		boolean weapon = stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS)
			|| stack.is(Items.TRIDENT) || stack.is(Items.MACE) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
		return weapon ? Kind.ORDINARY : Kind.NONE;
	}

	/**
	 * The durability a use takes from this item in a Rust Anchor world: the usual amount scaled by
	 * its kind, the fraction rounded up or down at random so the average is exact.
	 */
	public static int wear(final ItemStack stack, final int amount, final RandomSource random) {
		Kind kind = kind(stack);
		if (amount <= 0 || kind == Kind.NONE) {
			return amount;
		}
		float scaled = amount * kind.wear;
		int whole = (int) Math.floor(scaled);
		return whole + (random.nextFloat() < scaled - whole ? 1 : 0);
	}

	/** Every tick: the weapon in hand sets the hunter's attack, in a Rust Anchor world only. */
	public static void tick(final MinecraftServer server) {
		boolean rust = Hardship.of(server) == Hardship.RUST_ANCHOR;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
			if (attack == null) {
				continue;
			}
			double change = rust ? kind(player.getMainHandItem()).attack : 0.0;
			AttributeModifier current = attack.getModifier(ATTACK_ID);
			if (change == 0.0) {
				if (current != null) {
					attack.removeModifier(ATTACK_ID);
				}
			} else if (current == null || current.amount() != change) {
				attack.addOrUpdateTransientModifier(new AttributeModifier(ATTACK_ID, change, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}

	public static void init() {
		ServerTickEvents.START_SERVER_TICK.register(RustAnchorWeapons::tick);
	}
}
