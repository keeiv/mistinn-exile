package life.shizune.mistinn.team;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.ToIntFunction;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.LostLimbs;
import life.shizune.mistinn.registry.MistinnSounds;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 倒地: with another hunter still on their feet, a blow that would kill a hunter puts them on the
 * ground instead. They cannot get up, fight or use anything, the creatures leave them be, and they
 * have a minute before they bleed out; every hit they take meanwhile costs them ten seconds of it.
 * A teammate brings them back in ten seconds, kneeling beside them (F) or dragging them along (H).
 * Alone, or with everyone down, a hunter dies as they always did; a finisher's last blow, the void
 * and {@code /kill} kill outright.
 */
public final class DownService {
	/** Ticks a downed hunter lasts with no help. */
	public static final int BLEED_TICKS = 60 * 20;
	/** Ticks a rescue takes, either way: fixed, nothing shortens it. */
	public static final int RESCUE_TICKS = 10 * 20;
	/** What one hit takes off the time a downed hunter has left. */
	public static final int HIT_PENALTY = 10 * 20;
	/** Ticks between two hits that each cost time: a fire or a fall of blows counts once a second. */
	public static final int HIT_GAP = 20;
	/** How near a teammate must be to start a rescue, and to keep one going in place. */
	public static final double REACH = 2.5;
	/** How far behind the rescuer a dragged hunter is held, and how far they may trail before it breaks off. */
	public static final double DRAG_BEHIND = 1.1;
	public static final double DRAG_BREAK = 4.0;
	/** Share of a part's ceiling every part under it comes back to when rescued. */
	public static final float REVIVED_SHARE = 0.25F;
	/** A rescuer dragging someone walks this much slower. */
	public static final double DRAG_SLOW = -0.4;

	public static final ResourceKey<DamageType> BLED_OUT = ResourceKey.create(Registries.DAMAGE_TYPE, Mistinn.id("bled_out"));
	private static final Identifier HOLD = Mistinn.id("downed_hold");
	private static final Identifier DRAGGING = Mistinn.id("dragging");

	/**
	 * A downed hunter: the ticks they have left, the game time they went down, the last game time a
	 * hit cost them time, who is rescuing them (an entity id, -1 for no one) and how far along.
	 */
	public record Down(int left, long since, long lastHit, int rescuer, int progress, boolean dragging) {
		/** Only the time left is saved: a hunter who comes back is still down, and no one is helping yet. */
		public static final Codec<Down> CODEC = Codec.INT.xmap(left -> new Down(left, 0L, 0L, -1, 0, false), Down::left);
		public static final StreamCodec<ByteBuf, Down> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Down::left,
			ByteBufCodecs.VAR_LONG, Down::since,
			ByteBufCodecs.VAR_LONG, Down::lastHit,
			ByteBufCodecs.VAR_INT, Down::rescuer,
			ByteBufCodecs.VAR_INT, Down::progress,
			ByteBufCodecs.BOOL, Down::dragging,
			Down::new);

		Down left(final int value) {
			return new Down(value, this.since, this.lastHit, this.rescuer, this.progress, this.dragging);
		}

		Down helped(final int by, final int done, final boolean drag) {
			return new Down(this.left, this.since, this.lastHit, by, done, drag);
		}
	}

	/** Sent to everyone, so a downed hunter is drawn lying and their teammates are offered the rescue. */
	public static final AttachmentType<Down> DOWN = AttachmentRegistry.<Down>builder()
		.persistent(Down.CODEC)
		.syncWith(Down.STREAM_CODEC, AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("down"));

	/** How many other hunters are on their feet, counted from the player list. */
	public static final ToIntFunction<ServerPlayer> STANDING = DownService::othersStanding;
	/** What {@link #goesDown} counts with; tests put a teammate here that the player list cannot hold. */
	public static ToIntFunction<ServerPlayer> standing = STANDING;

	private DownService() {
	}

	// ── Asking ────────────────────────────────────────────────────

	public static boolean isDown(final @Nullable Entity entity) {
		return entity instanceof Player player && player.hasAttached(DOWN);
	}

	public static @Nullable Down get(final Player player) {
		return player.getAttached(DOWN);
	}

	/** Damage that kills a hunter where they stand, down or not: a finisher's last blow, the void, {@code /kill}. */
	public static boolean killsOutright(final @Nullable DamageSource source) {
		return source != null && (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || life.shizune.mistinn.enemy.ExecutionService.wasExecuted(source));
	}

	/** Whether a blow (or a drain, with no source) that would kill this hunter puts them down instead. */
	public static boolean goesDown(final ServerPlayer player, final @Nullable DamageSource source) {
		return BodyService.applies(player) && !isDown(player) && !killsOutright(source) && standing.applyAsInt(player) > 0;
	}

	private static int othersStanding(final ServerPlayer player) {
		int count = 0;
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other != player && other.isAlive() && !other.isSpectator() && BodyService.applies(other) && !isDown(other)) {
				count++;
			}
		}
		return count;
	}

	// ── Going down and getting up ─────────────────────────────────

	/**
	 * The body a hunter goes down with: parts given out are held at the last of their strength, so it
	 * is one short of killing. Lost limbs stay lost.
	 */
	public static Body held(final ServerPlayer player, final Body body) {
		Body held = body;
		for (BodyPart part : BodyPart.values()) {
			if (!held.lethal()) {
				break;
			}
			if (held.destroyed(part) && !LostLimbs.lost(player, part)) {
				held = held.with(part, 1.0F);
			}
		}
		return held;
	}

	/** Puts the hunter on the ground. Called by {@link BodyService#set} for the blow that would have killed them. */
	public static void knockDown(final ServerPlayer player) {
		long now = player.level().getGameTime();
		player.setAttached(DOWN, new Down(BLEED_TICKS, now, now, -1, 0, false));
		hold(player);
		life.shizune.mistinn.medical.TreatmentService.interrupt(player);
		life.shizune.mistinn.weapon.HeavyService.breakCharge(player);
		player.stopUsingItem();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.LIMB_DESTROYED, SoundSource.PLAYERS, 1.0F, 0.8F);
		tellEveryone(player.level().getServer(), Component.translatable("message.mistinn.down.fell", player.getDisplayName()).withStyle(ChatFormatting.RED));
	}

	/** Back on their feet: every part under a quarter of its ceiling comes back to a quarter. */
	public static void revive(final ServerPlayer player, final @Nullable ServerPlayer by) {
		if (!isDown(player)) {
			return;
		}
		if (by != null && by != player) {
			life.shizune.mistinn.ledger.LedgerService.tell(by, "revive", false);
		}
		player.removeAttached(DOWN);
		unhold(player);
		Body body = BodyService.get(player);
		float floor = BodyService.max(player) * REVIVED_SHARE;
		for (BodyPart part : BodyPart.values()) {
			if (body.get(part) < floor && !LostLimbs.lost(player, part)) {
				body = body.with(part, floor);
			}
		}
		BodyService.set(player, body);
		if (by != null) {
			stopDragging(by);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 0.7F);
		tellEveryone(player.level().getServer(), by == null
			? Component.translatable("message.mistinn.down.up", player.getDisplayName())
			: Component.translatable("message.mistinn.down.rescued", by.getDisplayName(), player.getDisplayName()));
	}

	/** Out of time, or no one left to help: the hunter dies of it. */
	public static void bleedOut(final ServerPlayer player) {
		if (!isDown(player)) {
			return;
		}
		player.removeAttached(DOWN);
		unhold(player);
		ServerLevel level = player.level();
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BLED_OUT));
		player.setHealth(0.0F);
		player.die(source);
	}

	/** A hit taken while down: no part gives out further, but it costs time, at most once a second. */
	public static void hitWhileDown(final ServerPlayer player) {
		Down down = get(player);
		if (down == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - down.lastHit() < HIT_GAP) {
			return;
		}
		player.setAttached(DOWN, new Down(down.left() - HIT_PENALTY, down.since(), now, down.rescuer(), down.progress(), down.dragging()));
	}

	// ── Rescuing ──────────────────────────────────────────────────

	/**
	 * {@code rescuer} starts bringing {@code casualty} back, in place or dragging them; false when they
	 * may not (too far, already helped by someone else, or down themselves).
	 */
	public static boolean startRescue(final ServerPlayer rescuer, final ServerPlayer casualty, final boolean drag) {
		Down down = get(casualty);
		if (down == null || rescuer == casualty || isDown(rescuer) || !rescuer.isAlive() || rescuer.isSpectator()
			|| rescuer.level() != casualty.level() || rescuer.distanceTo(casualty) > REACH) {
			return false;
		}
		if (down.rescuer() >= 0 && down.rescuer() != rescuer.getId()) {
			return false;
		}
		casualty.setAttached(DOWN, down.helped(rescuer.getId(), down.rescuer() == rescuer.getId() ? down.progress() : 0, drag));
		if (drag) {
			slowDragging(rescuer);
		} else {
			stopDragging(rescuer);
		}
		return true;
	}

	/**
	 * A rescuer's key. F held starts the rescue in place, and F let go ends it (a drag goes on: the
	 * key may be let go before the server has even heard it was dragging); H starts or stops dragging.
	 */
	public static void handle(final ServerPlayer rescuer, final RescuePayload payload) {
		if (rescuer.level().getEntity(payload.casualty()) instanceof ServerPlayer casualty) {
			handle(rescuer, casualty, payload.action());
		}
	}

	/** {@link #handle(ServerPlayer, RescuePayload)} for the casualty itself. */
	public static void handle(final ServerPlayer rescuer, final ServerPlayer casualty, final int action) {
		Down down = get(casualty);
		switch (action) {
			case RescuePayload.HOLD -> startRescue(rescuer, casualty, false);
			case RescuePayload.DRAG -> {
				if (down != null && down.rescuer() == rescuer.getId() && down.dragging()) {
					stopRescue(rescuer, casualty);
				} else {
					startRescue(rescuer, casualty, true);
				}
			}
			default -> {
				if (down != null && down.rescuer() == rescuer.getId() && !down.dragging()) {
					stopRescue(rescuer, casualty);
				}
			}
		}
	}

	/** A rescuer who swings at something lets go of whoever they were helping. */
	public static void letGo(final ServerPlayer rescuer) {
		for (ServerPlayer other : rescuer.level().getServer().getPlayerList().getPlayers()) {
			letGo(rescuer, other);
		}
	}

	/** {@link #letGo(ServerPlayer)} for one casualty. */
	public static void letGo(final ServerPlayer rescuer, final ServerPlayer casualty) {
		Down down = get(casualty);
		if (down != null && down.rescuer() == rescuer.getId()) {
			stopRescue(rescuer, casualty);
		}
	}

	/** The rescuer lets go: what was done is lost. */
	public static void stopRescue(final ServerPlayer rescuer, final ServerPlayer casualty) {
		Down down = get(casualty);
		if (down != null && down.rescuer() == rescuer.getId()) {
			casualty.setAttached(DOWN, down.helped(-1, 0, false));
		}
		stopDragging(rescuer);
	}

	// ── Every tick ────────────────────────────────────────────────

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(RescuePayload.TYPE, RescuePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RescuePayload.TYPE, (payload, context) -> handle(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(DownService::tickAll);
		ServerPlayerEvents.JOIN.register(player -> {
			Down down = get(player);
			if (down != null) {
				// back where they lay, with the time they had; the hold is a transient modifier: put it back
				long now = player.level().getGameTime();
				player.setAttached(DOWN, new Down(down.left(), now, now, -1, 0, false));
				hold(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			newPlayer.removeAttached(DOWN);
			unhold(newPlayer);
		});
		// a downed hunter cannot fight or use anything; a rescuer who swings lets go
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (isDown(player)) {
				return InteractionResult.FAIL;
			}
			if (player instanceof ServerPlayer rescuer) {
				letGo(rescuer);
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> isDown(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> isDown(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> isDown(player) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	private static void tickAll(final MinecraftServer server) {
		List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
		for (ServerPlayer player : players) {
			if (isDown(player)) {
				tick(player);
			}
		}
	}

	/** One tick of a downed hunter: the time runs down, and a rescue goes on, or breaks off. */
	public static void tick(final ServerPlayer casualty) {
		Down down = get(casualty);
		if (down == null) {
			return;
		}
		if (!casualty.isAlive() || !BodyService.applies(casualty)) {
			casualty.removeAttached(DOWN);
			unhold(casualty);
			return;
		}
		if (down.left() <= 0 || standing.applyAsInt(casualty) <= 0) {
			bleedOut(casualty);
			return;
		}
		ServerPlayer rescuer = down.rescuer() >= 0 && casualty.level().getEntity(down.rescuer()) instanceof ServerPlayer p ? p : null;
		int progress = down.progress();
		int by = down.rescuer();
		boolean dragging = down.dragging();
		if (by >= 0 && (rescuer == null || !rescuer.isAlive() || isDown(rescuer) || rescuer.isSpectator()
			|| rescuer.distanceTo(casualty) > (dragging ? DRAG_BREAK : REACH))) {
			if (rescuer != null) {
				stopDragging(rescuer);
			}
			rescuer = null;
			by = -1;
			progress = 0;
			dragging = false;
		}
		if (rescuer != null) {
			progress++;
			if (dragging) {
				drag(rescuer, casualty);
			}
			if (progress >= RESCUE_TICKS) {
				revive(casualty, rescuer);
				return;
			}
		}
		casualty.setAttached(DOWN, new Down(down.left() - 1, down.since(), down.lastHit(), by, progress, dragging));
	}

	/** The dragged hunter is pulled along to a point just behind the rescuer, by their own motion so it stays smooth. */
	private static void drag(final ServerPlayer rescuer, final ServerPlayer casualty) {
		float yaw = rescuer.getYRot() * Mth.DEG_TO_RAD;
		Vec3 behind = rescuer.position().add(Mth.sin(yaw) * DRAG_BEHIND, 0.0, -Mth.cos(yaw) * DRAG_BEHIND);
		Vec3 pull = behind.subtract(casualty.position());
		Vec3 flat = new Vec3(pull.x, 0.0, pull.z);
		if (flat.lengthSqr() < 0.04) {
			return;
		}
		Vec3 step = flat.scale(0.35);
		if (step.length() > 0.6) {
			step = step.normalize().scale(0.6);
		}
		casualty.setDeltaMovement(step.x, casualty.getDeltaMovement().y, step.z);
		casualty.syncVelocity = true;
	}

	// ── Holding still ─────────────────────────────────────────────

	private static void hold(final ServerPlayer player) {
		modifier(player.getAttribute(Attributes.MOVEMENT_SPEED), HOLD, -1.0);
		modifier(player.getAttribute(Attributes.JUMP_STRENGTH), HOLD, -1.0);
	}

	private static void unhold(final ServerPlayer player) {
		remove(player.getAttribute(Attributes.MOVEMENT_SPEED), HOLD);
		remove(player.getAttribute(Attributes.JUMP_STRENGTH), HOLD);
	}

	private static void slowDragging(final ServerPlayer rescuer) {
		modifier(rescuer.getAttribute(Attributes.MOVEMENT_SPEED), DRAGGING, DRAG_SLOW);
	}

	private static void stopDragging(final ServerPlayer rescuer) {
		remove(rescuer.getAttribute(Attributes.MOVEMENT_SPEED), DRAGGING);
	}

	private static void modifier(final @Nullable AttributeInstance instance, final Identifier id, final double amount) {
		if (instance != null && !instance.hasModifier(id)) {
			instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void remove(final @Nullable AttributeInstance instance, final Identifier id) {
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	private static void tellEveryone(final MinecraftServer server, final Component message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(message);
		}
	}

	// ── The rescuer's keys ────────────────────────────────────────

	/** A rescuer's key: hold F (start and stop), or H to start or stop dragging. */
	public record RescuePayload(int action, int casualty) implements CustomPacketPayload {
		public static final int HOLD = 0;
		public static final int RELEASE = 1;
		public static final int DRAG = 2;
		public static final Type<RescuePayload> TYPE = new Type<>(Mistinn.id("rescue"));
		public static final StreamCodec<ByteBuf, RescuePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RescuePayload::action,
			ByteBufCodecs.VAR_INT, RescuePayload::casualty,
			RescuePayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
