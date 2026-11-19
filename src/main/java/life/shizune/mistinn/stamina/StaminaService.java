package life.shizune.mistinn.stamina;

import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 精力: what swings, sprints and rolls are paid with. Spending stops recovery for a moment; at
 * zero the hunter is spent, cannot sprint, and recovers only after a longer pause.
 */
public final class StaminaService {
	public static final float MAX = 100.0F;
	/** Recovery per tick once it starts. */
	public static final float REGEN = 1.4F;
	/** Ticks after spending before recovery starts; longer when spent to nothing. */
	public static final int REGEN_DELAY = 20;
	public static final int SPENT_DELAY = 40;
	/** Sprinting costs this per tick. */
	public static final float SPRINT = 0.22F;
	public static final float ROLL = 22.0F;
	public static final float DIVE = 28.0F;
	/** A swing with no weapon of ours. */
	public static final float FIST = 4.0F;

	private StaminaService() {
	}

	/** What a swing of this weapon costs: the heavier the weapon, the more. */
	public static float swingCost(final ItemStack stack) {
		WeaponStyle style = WeaponStyle.of(stack);
		if (style == null) {
			return FIST;
		}
		return switch (style) {
			case FLURRY -> 7.0F;
			case SLASH -> 11.0F;
			case THRUST -> 13.0F;
			case CLEAVE -> 22.0F;
			case SMASH -> 24.0F;
		};
	}

	public static boolean applies(final Player player) {
		return BodyService.applies(player);
	}

	public static float get(final Player player) {
		return player.getAttachedOrElse(MistinnAttachments.STAMINA, MAX);
	}

	private static void set(final ServerPlayer player, final float value) {
		float clamped = Math.max(0.0F, Math.min(MAX, value));
		if (clamped != get(player)) {
			player.setAttached(MistinnAttachments.STAMINA, clamped);
		}
	}

	/** Pays {@code cost}; going below zero empties the bar and delays recovery further. */
	public static void spend(final ServerPlayer player, final float cost) {
		float left = get(player) - cost;
		set(player, left);
		DELAY.put(player.getUUID(), left <= 0.0F ? SPENT_DELAY : REGEN_DELAY);
	}

	/** Whether the hunter has at least {@code cost} to spend (rolls need the full price). */
	public static boolean has(final Player player, final float cost) {
		return get(player) >= cost;
	}

	private static final java.util.Map<java.util.UUID, Integer> DELAY = new java.util.HashMap<>();

	public static void tick(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!applies(player) || player.isDeadOrDying()) {
				continue;
			}
			if (player.isSprinting()) {
				spend(player, SPRINT * legStrain(player));
				if (get(player) <= 0.0F) {
					player.setSprinting(false);
				}
				continue;
			}
			int delay = DELAY.getOrDefault(player.getUUID(), 0);
			if (delay > 0) {
				DELAY.put(player.getUUID(), delay - 1);
				continue;
			}
			if (get(player) < MAX) {
				Body body = BodyService.get(player);
				// a destroyed torso breathes badly
				float rate = body.destroyed(BodyPart.TORSO) ? REGEN * 0.6F : REGEN;
				set(player, get(player) + rate);
			}
		}
	}

	/** Sprinting on a destroyed leg costs half again as much per dead leg. */
	private static float legStrain(final ServerPlayer player) {
		Body body = BodyService.get(player);
		int dead = (body.destroyed(BodyPart.LEFT_LEG) ? 1 : 0) + (body.destroyed(BodyPart.RIGHT_LEG) ? 1 : 0);
		return 1.0F + 0.5F * dead;
	}
}
