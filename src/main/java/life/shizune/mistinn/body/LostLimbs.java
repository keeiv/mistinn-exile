package life.shizune.mistinn.body;

import com.mojang.serialization.Codec;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.registry.MistinnSounds;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

/**
 * 斷肢: in a Rust Anchor world, out past the station, an arm or a leg can be lost for good, until
 * death. A lost limb stays at nothing whatever mends the body, and it counts as a destroyed part.
 * A lost leg takes the sprint, the jump, the roll and the slide; both leave only crawling. A
 * lost arm holds nothing; without the working arm there is no striking or digging.
 */
public final class LostLimbs {
	/** A dead limb struck this hard again may come off. */
	public static final float SEVER_DEAD = 12.0F;
	public static final float SEVER_DEAD_CHANCE = 0.35F;
	/** A blow that carries this far past what a limb had left may take it off. */
	public static final float SEVER_THROUGH = 20.0F;
	public static final float SEVER_THROUGH_CHANCE = 0.5F;

	/** Which parts are lost, one bit each by {@link BodyPart#ordinal()}. Seen by everyone, so it can be drawn; gone with death. */
	public static final AttachmentType<Integer> LOST = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("lost_limbs"));

	private LostLimbs() {
	}

	public static int mask(final Player player) {
		return player.getAttachedOrElse(LOST, 0);
	}

	public static boolean lost(final int mask, final BodyPart part) {
		return (mask & 1 << part.ordinal()) != 0;
	}

	public static boolean lost(final Player player, final BodyPart part) {
		return lost(mask(player), part);
	}

	/** How many legs are lost: 0, 1 or 2. */
	public static int legs(final Player player) {
		int mask = mask(player);
		return (lost(mask, BodyPart.LEFT_LEG) ? 1 : 0) + (lost(mask, BodyPart.RIGHT_LEG) ? 1 : 0);
	}

	/** How many arms are lost: 0, 1 or 2. */
	public static int arms(final Player player) {
		int mask = mask(player);
		return (lost(mask, BodyPart.LEFT_ARM) ? 1 : 0) + (lost(mask, BodyPart.RIGHT_ARM) ? 1 : 0);
	}

	/** The arm on a side of the body. */
	public static BodyPart arm(final HumanoidArm side) {
		return side == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
	}

	/** Whether the arm that would hold what is in this hand is lost. */
	public static boolean handLost(final Player player, final InteractionHand hand) {
		HumanoidArm side = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		return lost(player, arm(side));
	}

	/** A body as it can be: lost limbs at nothing, whatever tried to mend them. */
	public static Body hold(final Player player, final Body body) {
		int mask = mask(player);
		if (mask == 0) {
			return body;
		}
		Body held = body;
		for (BodyPart part : BodyPart.values()) {
			if (lost(mask, part) && held.get(part) > 0.0F) {
				held = held.with(part, 0.0F);
			}
		}
		return held;
	}

	/** A body as treatment sees it: a lost limb is past treating, so it is left out as if whole. */
	public static Body treatable(final Player player, final Body body) {
		int mask = mask(player);
		Body seen = body;
		for (BodyPart part : BodyPart.values()) {
			if (lost(mask, part)) {
				seen = seen.with(part, BodyPart.MAX);
			}
		}
		return seen;
	}

	/** Whether limbs can be lost here: a Rust Anchor world, away from the station. */
	public static boolean applies(final Player player) {
		return BodyService.applies(player) && Hardship.rustAnchorAfield(player);
	}

	/**
	 * After a hit has landed: a blow or a crush may take off a limb it reached, either one that
	 * was already dead or one it went clean through. {@code amount} is the part health the hit
	 * took, before it was spread; {@code before} the body as it was.
	 */
	public static void afterHit(final ServerPlayer player, final BodyService.Hit hit, final DamageSource source, final float amount, final Body before) {
		boolean blow = source.getDirectEntity() != null || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_EXPLOSION);
		if (!blow || !applies(player)) {
			return;
		}
		java.util.List<BodyPart> reached = new java.util.ArrayList<>();
		float each = amount;
		if (hit.whole()) {
			java.util.Collections.addAll(reached, BodyPart.values());
			each = amount / BodyPart.values().length;
		} else if (hit.legs()) {
			reached.add(BodyPart.LEFT_LEG);
			reached.add(BodyPart.RIGHT_LEG);
			each = amount / 2.0F;
		} else if (hit.part() != null) {
			reached.add(hit.part());
		}
		RandomSource random = player.getRandom();
		for (BodyPart part : reached) {
			if (!(part.isArm() || part.isLeg()) || lost(player, part) || !player.isAlive()) {
				continue;
			}
			float left = before.get(part);
			boolean off = left <= 0.0F
				? each >= SEVER_DEAD && random.nextFloat() < SEVER_DEAD_CHANCE
				: each - left >= SEVER_THROUGH && random.nextFloat() < SEVER_THROUGH_CHANCE;
			if (off) {
				lose(player, part);
			}
		}
	}

	/** The limb is gone, until death. */
	public static void lose(final ServerPlayer player, final BodyPart part) {
		if (!(part.isArm() || part.isLeg()) || lost(player, part)) {
			return;
		}
		player.setAttached(LOST, mask(player) | 1 << part.ordinal());
		WoundService.set(player, WoundService.get(player).with(part, Wounds.Wound.NONE));
		BodyService.set(player, BodyService.get(player));
		BodyService.applyPenalties(player, BodyService.get(player));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.LIMB_DESTROYED, SoundSource.PLAYERS, 1.0F, 0.6F);
		player.level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
			player.getX(), player.getY() + (part.isLeg() ? 0.4 : 1.2), player.getZ(), 30, 0.25, 0.25, 0.25, 0.1);
		player.sendSystemMessage(Component.translatable("message.mistinn.limb.lost", WoundService.partName(part)).withColor(0xFFD0564B), true);
	}

	/** Things that take both hands: a bow, a crossbow, a trident, and binding, splinting or operating on a wound. */
	public static boolean twoHanded(final ItemStack stack) {
		return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof TridentItem
			|| stack.is(MistinnItems.TOURNIQUET) || stack.is(MistinnItems.SPLINT) || stack.is(MistinnItems.SURGERY_KIT);
	}

	/** Whether this hunter cannot use what is in this hand: its arm is lost, or it takes two hands and one is. */
	public static boolean refuses(final Player player, final InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		return !stack.isEmpty() && (handLost(player, hand) || twoHanded(stack) && arms(player) > 0);
	}

	public static void init() {
		// without the working arm there is no striking and no digging
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> handLost(player, InteractionHand.MAIN_HAND) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> handLost(player, InteractionHand.MAIN_HAND) ? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> !handLost(player, InteractionHand.MAIN_HAND));
		// a lost hand holds nothing to use
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!refuses(player, hand)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server) {
				server.sendSystemMessage(Component.translatable(handLost(player, hand) ? "message.mistinn.limb.no_hand" : "message.mistinn.limb.two_hands"), true);
			}
			return InteractionResult.FAIL;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> refuses(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		// no sprint on a lost leg (the client stops it too)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isSprinting() && legs(player) > 0) {
					player.setSprinting(false);
				}
			}
		});
	}
}
