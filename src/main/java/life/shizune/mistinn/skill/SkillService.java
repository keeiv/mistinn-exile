package life.shizune.mistinn.skill;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.stamina.StaminaService;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a hunter has learned and what it does for them. The skills are saved on the player and
 * kept through death. Strength and speed act through vanilla attributes (put back on every join
 * and respawn); the other effects are asked for by the services they change.
 */
public final class SkillService {
	/** Stamina a hunter holds with no constitution. */
	public static final float BASE_STAMINA = 100.0F;
	/** Stamina a hunter can ever hold. */
	public static final float STAMINA_CEILING = 160.0F;

	/** The first minor node's price in salt; each one bought makes the next dearer by {@link #MINOR_STEP}. */
	public static final int MINOR_PRICE = 200;
	public static final int MINOR_STEP = 100;

	private static final Identifier STRENGTH_ID = Mistinn.id("skill_strength");
	private static final Identifier SPEED_ID = Mistinn.id("skill_speed");
	private static final Identifier SWING_ID = Mistinn.id("skill_swing");

	private SkillService() {
	}

	// ── State ─────────────────────────────────────────────────────

	public static Skills get(final Player player) {
		return player.getAttachedOrElse(MistinnAttachments.SKILLS, Skills.EMPTY);
	}

	public static int stat(final Player player, final Stat stat) {
		return get(player).stat(stat);
	}

	/** Stores the skills and brings every effect in line with them. */
	public static void set(final ServerPlayer player, final Skills skills) {
		player.setAttached(MistinnAttachments.SKILLS, skills);
		refresh(player);
	}

	/** Puts the attribute modifiers back and keeps parts and stamina within their ceilings. */
	public static void refresh(final ServerPlayer player) {
		modifier(player, Attributes.ATTACK_DAMAGE, STRENGTH_ID, damageScale(stat(player, Stat.STRENGTH)) - 1.0);
		modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, moveScale(stat(player, Stat.SPEED)) - 1.0);
		modifier(player, Attributes.ATTACK_SPEED, SWING_ID, swingScale(stat(player, Stat.SPEED)) - 1.0);
		BodyService.fitCeiling(player);
		StaminaService.fitCeiling(player);
	}

	private static void modifier(final ServerPlayer player, final Holder<Attribute> attribute, final Identifier id, final double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (Math.abs(amount) < 1.0E-9) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	public static void register() {
		// the modifiers are transient: every join and every respawn puts them back
		ServerPlayerEvents.JOIN.register(SkillService::refresh);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// the saved copy of the skills reaches the new body only after this event: take them from the old one now
			newPlayer.setAttached(MistinnAttachments.SKILLS, get(oldPlayer));
			refresh(newPlayer);
		});
		PayloadTypeRegistry.serverboundPlay().register(UnlockPayload.TYPE, UnlockPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(UnlockResultPayload.TYPE, UnlockResultPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(UnlockPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			Result result = unlock(player, payload.id());
			if (ServerPlayNetworking.canSend(player, UnlockResultPayload.TYPE)) {
				ServerPlayNetworking.send(player, new UnlockResultPayload(payload.id(), result.ordinal()));
			}
		});
	}

	// ── Unlocking ─────────────────────────────────────────────────

	/** What became of a request to unlock a node. */
	public enum Result {
		UNLOCKED,
		/** Already learned: nothing is charged. */
		ALREADY,
		/** The node before it is not learned yet. */
		LOCKED_PARENT,
		/** A major node, and no rust god salt anywhere on the hunter. */
		NO_RUST_SALT,
		/** A minor node, and not enough salt. */
		NO_SALT,
		/** No such node. */
		UNKNOWN;

		public static Result byId(final int id) {
			Result[] values = values();
			return id >= 0 && id < values.length ? values[id] : UNKNOWN;
		}
	}

	/** Salt the node costs this hunter now: minor nodes grow dearer the more are bought; major nodes take a rust god salt instead. */
	public static int price(final Player player, final String id) {
		return price(get(player), id);
	}

	public static int price(final Skills skills, final String id) {
		SkillTree.Node node = SkillTree.node(id);
		if (node == null || node.major()) {
			return 0;
		}
		return MINOR_PRICE + MINOR_STEP * skills.minorCount();
	}

	/**
	 * Unlocks a node if the hunter may: the node before it learned, and the price paid (one rust
	 * god salt for a major node, from the hands, the hotbar or the grid; salt for a minor node).
	 * Requests are handled one after another on the server, so a double click is charged once.
	 */
	public static Result unlock(final ServerPlayer player, final String id) {
		SkillTree.Node node = SkillTree.node(id);
		if (node == null) {
			return Result.UNKNOWN;
		}
		Skills skills = get(player);
		if (skills.has(id)) {
			return Result.ALREADY;
		}
		if (node.parent() != null && !skills.has(node.parent())) {
			return Result.LOCKED_PARENT;
		}
		if (node.major()) {
			if (!takeRustSalt(player)) {
				return Result.NO_RUST_SALT;
			}
		} else {
			int price = price(skills, id);
			if (SaltService.get(player) < price) {
				return Result.NO_SALT;
			}
			SaltService.add(player, -price);
		}
		set(player, skills.with(id));
		life.shizune.mistinn.achievement.AchievementService.learned(player);
		return Result.UNLOCKED;
	}

	/** Takes one rust god salt: from the main hand, the off hand, the hotbar, then the grid. */
	private static boolean takeRustSalt(final ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(MistinnItems.RUST_GOD_SALT)) {
				held.shrink(1);
				return true;
			}
		}
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(MistinnItems.RUST_GOD_SALT)) {
				stack.shrink(1);
				player.getInventory().setChanged();
				return true;
			}
		}
		GridInventory grid = GridService.get(player);
		for (Area area : new Area[] {Area.POCKETS, Area.RIG, Area.BACKPACK, Area.SAFE}) {
			List<Placed> items = grid.items(area);
			for (int i = 0; i < items.size(); i++) {
				Placed placed = items.get(i);
				if (!placed.stack().is(MistinnItems.RUST_GOD_SALT)) {
					continue;
				}
				List<Placed> next = new ArrayList<>(items);
				ItemStack left = placed.stack().copyWithCount(placed.stack().getCount() - 1);
				if (left.isEmpty()) {
					next.remove(i);
				} else {
					next.set(i, new Placed(placed.section(), placed.x(), placed.y(), placed.rotated(), left));
				}
				GridService.set(player, grid.with(area, next));
				return true;
			}
		}
		return false;
	}

	// ── Payloads ──────────────────────────────────────────────────

	/** The hunter asks to unlock a node (from the guide's skill page). */
	public record UnlockPayload(String id) implements CustomPacketPayload {
		public static final Type<UnlockPayload> TYPE = new Type<>(Mistinn.id("unlock_skill"));
		public static final StreamCodec<ByteBuf, UnlockPayload> CODEC = ByteBufCodecs.stringUtf8(64).map(UnlockPayload::new, UnlockPayload::id);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The answer to an {@link UnlockPayload}: a {@link Result} by ordinal. */
	public record UnlockResultPayload(String id, int result) implements CustomPacketPayload {
		public static final Type<UnlockResultPayload> TYPE = new Type<>(Mistinn.id("unlock_skill_result"));
		public static final StreamCodec<ByteBuf, UnlockResultPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), UnlockResultPayload::id,
			ByteBufCodecs.VAR_INT, UnlockResultPayload::result,
			UnlockResultPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// ── Each stat, per point ──────────────────────────────────────

	/** Strength: melee damage, 3% per point. */
	public static double damageScale(final int strength) {
		return 1.0 + 0.03 * strength;
	}

	/** Speed: walking and running, 1% per point. */
	public static double moveScale(final int speed) {
		return 1.0 + 0.01 * speed;
	}

	/** Speed: swings, 1.5% per point. */
	public static double swingScale(final int speed) {
		return 1.0 + 0.015 * speed;
	}

	/** Speed: the speed a roll starts with, 1.5% per point. */
	public static float rollScale(final int speed) {
		return 1.0F + 0.015F * speed;
	}

	/** Speed: a tick more dodge for every five points. */
	public static int dodgeBonus(final int speed) {
		return speed / 5;
	}

	/** Constitution: each part holds 2 more per point, up to {@link BodyPart#CEILING}. */
	public static float partCeiling(final int constitution) {
		return Math.min(BodyPart.CEILING, BodyPart.MAX + 2.0F * constitution);
	}

	/** Constitution: stamina holds 3 more per point, up to {@link #STAMINA_CEILING}. */
	public static float staminaCeiling(final int constitution) {
		return Math.min(STAMINA_CEILING, BASE_STAMINA + 3.0F * constitution);
	}

	/** Constitution: what is left of the chance that a hit opens a wound or breaks a bone, 3% less per point. */
	public static float woundScale(final int constitution) {
		return Math.max(0.0F, 1.0F - 0.03F * constitution);
	}

	/** Intellect: treatment and repair take 2.5% less time per point, never less than half. */
	public static float treatmentScale(final int intellect) {
		return Math.max(0.5F, 1.0F - 0.025F * intellect);
	}

	/** Intellect: treatment heals 3% more per point. */
	public static float healScale(final int intellect) {
		return 1.0F + 0.03F * intellect;
	}

	// ── On a hunter ───────────────────────────────────────────────

	public static float partCeiling(final Player player) {
		return partCeiling(stat(player, Stat.CONSTITUTION));
	}

	public static float staminaCeiling(final Player player) {
		return staminaCeiling(stat(player, Stat.CONSTITUTION));
	}

	public static float woundChance(final Player player, final float chance) {
		return chance * woundScale(stat(player, Stat.CONSTITUTION));
	}

	public static int treatmentTicks(final Player player, final int ticks) {
		return Math.max(1, Math.round(ticks * treatmentScale(stat(player, Stat.INTELLECT))));
	}

	public static float treatmentAmount(final Player player, final float amount) {
		return amount * healScale(stat(player, Stat.INTELLECT));
	}

	public static float rollSpeedScale(final Player player) {
		return rollScale(stat(player, Stat.SPEED));
	}

	public static int rollDodgeBonus(final Player player) {
		return dodgeBonus(stat(player, Stat.SPEED));
	}
}
