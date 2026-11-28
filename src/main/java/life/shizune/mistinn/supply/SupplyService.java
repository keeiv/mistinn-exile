package life.shizune.mistinn.supply;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.Supplier;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.bounty.BountyService;
import life.shizune.mistinn.enemy.NoiseService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 空投補給: salt spent at the anchor station brings a barrel of supplies down from the sky near
 * the hunter half a minute to a minute later, one order at a time. The barrel falls trailing
 * smoke and fire for the last five seconds, and lands loud enough that whatever is listening
 * nearby comes to look; it smokes where it lies, and the minimap marks it, until it is emptied.
 */
public final class SupplyService {
	/** What the station sells: an id for the wire, the item and how many, and the price in salt. */
	public record Offer(String id, Supplier<Item> item, int count, int price) {
		public ItemStack stack() {
			return new ItemStack(this.item.get(), this.count);
		}
	}

	public static final List<Offer> OFFERS = List.of(
		new Offer("tourniquet", () -> MistinnItems.TOURNIQUET, 2, 60),
		new Offer("splint", () -> MistinnItems.SPLINT, 1, 50),
		new Offer("painkillers", () -> MistinnItems.PAINKILLERS, 1, 120),
		new Offer("potent_injector", () -> MistinnItems.POTENT_INJECTOR, 1, 400),
		new Offer("surgery_kit", () -> MistinnItems.SURGERY_KIT, 1, 600),
		new Offer("paper_cartridge", () -> MistinnItems.PAPER_CARTRIDGE, 6, 80),
		new Offer("salt_bomb", () -> MistinnItems.SALT_BOMB, 2, 100),
		new Offer("flare", () -> MistinnItems.FLARE, 2, 120),
		new Offer("bell_lure", () -> MistinnItems.BELL_LURE, 1, 100),
		new Offer("armor_repair_kit", () -> MistinnItems.ARMOR_REPAIR_KITS.getFirst(), 1, 300),
		new Offer("helmet_repair_kit", () -> MistinnItems.HELMET_REPAIR_KITS.getFirst(), 1, 250));

	/** How long an order takes, at the least and the most. */
	public static final int DELAY_MIN = 600;
	public static final int DELAY_MAX = 1200;
	/** How long the barrel is seen falling, and from how high. */
	public static final int FALL = 100;
	public static final int HEIGHT = 64;
	/** How far from the hunter it comes down, at the least and the most. */
	public static final int NEAR = 8;
	public static final int FAR = 16;
	/** How far off the landing is heard. */
	public static final double HEARD = 32.0;
	/** How long the minimap marks a barrel nobody empties. */
	public static final long MARKED = 2400L;

	/** An order on its way: what, and when it lands. */
	public record Order(String offer, long due) {
		public static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("offer").forGetter(Order::offer),
			Codec.LONG.fieldOf("due").forGetter(Order::due)
		).apply(i, Order::new));
		public static final StreamCodec<ByteBuf, Order> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), Order::offer,
			ByteBufCodecs.VAR_LONG, Order::due,
			Order::new);
	}

	/** Where a barrel is coming down or lies, when it lands, and until when the minimap shows it. */
	public record Drop(BlockPos at, long lands, long until) {
		public static final Codec<Drop> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("at").forGetter(Drop::at),
			Codec.LONG.fieldOf("lands").forGetter(Drop::lands),
			Codec.LONG.fieldOf("until").forGetter(Drop::until)
		).apply(i, Drop::new));
		public static final StreamCodec<ByteBuf, Drop> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Drop::at,
			ByteBufCodecs.VAR_LONG, Drop::lands,
			ByteBufCodecs.VAR_LONG, Drop::until,
			Drop::new);
	}

	public static final AttachmentType<Order> ORDER = AttachmentRegistry.<Order>builder()
		.persistent(Order.CODEC)
		.syncWith(Order.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("supply_order"));
	public static final AttachmentType<Drop> DROP = AttachmentRegistry.<Drop>builder()
		.persistent(Drop.CODEC)
		.syncWith(Drop.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("supply_drop"));

	/** What the station says to an order. */
	public enum Result {
		ORDERED, PENDING, POOR, UNKNOWN, NOWHERE;

		public static Result byId(final int id) {
			Result[] all = values();
			return id >= 0 && id < all.length ? all[id] : UNKNOWN;
		}
	}

	/** For tests: every order takes exactly this long (0: half a minute to a minute). */
	public static int fixedDelay;

	private SupplyService() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(OrderPayload.TYPE, OrderPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OrderResultPayload.TYPE, OrderResultPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OrderPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			Result result = order(player, payload.offer());
			if (ServerPlayNetworking.canSend(player, OrderResultPayload.TYPE)) {
				ServerPlayNetworking.send(player, new OrderResultPayload(payload.offer(), result.ordinal()));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SupplyService::tick);
	}

	public static @Nullable Offer offer(final String id) {
		for (Offer offer : OFFERS) {
			if (offer.id().equals(id)) {
				return offer;
			}
		}
		return null;
	}

	/** Where an offer stands on the list, or -1. */
	public static int index(final String id) {
		for (int i = 0; i < OFFERS.size(); i++) {
			if (OFFERS.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	/** The hunter orders {@code id}: paid for now, on its way at once if nothing else is. */
	public static Result order(final ServerPlayer player, final String id) {
		Offer offer = offer(id);
		if (offer == null) {
			return Result.UNKNOWN;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null || WorldMode.of(server) == WorldMode.TRAINING || player.level().dimension() != Level.OVERWORLD) {
			return Result.NOWHERE;
		}
		if (player.hasAttached(ORDER)) {
			return Result.PENDING;
		}
		if (SaltService.get(player) < offer.price()) {
			return Result.POOR;
		}
		SaltService.add(player, -offer.price());
		int delay = fixedDelay > 0 ? fixedDelay : DELAY_MIN + player.getRandom().nextInt(DELAY_MAX - DELAY_MIN + 1);
		player.setAttached(ORDER, new Order(offer.id(), server.overworld().getGameTime() + delay));
		player.sendSystemMessage(Component.translatable("message.mistinn.supply.ordered", offer.stack().getHoverName(), offer.count(), offer.price()));
		return Result.ORDERED;
	}

	private static void tick(final MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Order order = player.getAttached(ORDER);
			if (order != null) {
				carry(player, order, now);
			}
			Drop drop = player.getAttached(DROP);
			if (drop != null && now % 10 == 0) {
				lies(player, drop, now);
			}
		}
	}

	/** An order on its way: its streak begins, falls, and lands. */
	private static void carry(final ServerPlayer player, final Order order, final long now) {
		if (now < order.due() - FALL) {
			return;
		}
		ServerLevel level = player.level();
		Drop drop = player.getAttached(DROP);
		boolean falling = drop != null && drop.lands() == order.due();
		if (!falling) {
			// not while the hunter is somewhere the station cannot reach: it waits for them
			if (level.dimension() != Level.OVERWORLD || player.isSpectator() || !player.isAlive()) {
				player.setAttached(ORDER, new Order(order.offer(), now + FALL + 200));
				return;
			}
			// a whole streak, even for an order that came due while the hunter was away
			long lands = Math.max(order.due(), now + FALL);
			BlockPos at = spot(level, player);
			player.setAttached(ORDER, new Order(order.offer(), lands));
			player.setAttached(DROP, new Drop(at, lands, lands + MARKED));
			level.playSound(null, at.getX() + 0.5, at.getY() + HEIGHT, at.getZ() + 0.5, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.BLOCKS, 6.0F, 0.5F);
			int distance = (int) Math.round(Math.hypot(at.getX() + 0.5 - player.getX(), at.getZ() + 0.5 - player.getZ()));
			player.sendSystemMessage(Component.translatable("message.mistinn.supply.falling", BountyService.direction(player, at), distance)
				.withStyle(ChatFormatting.GOLD));
			return;
		}
		ServerLevel overworld = level.getServer().overworld();
		if (now < order.due()) {
			// the streak: smoke and fire coming down on the spot
			double fell = 1.0 - (order.due() - now) / (double) FALL;
			double y = drop.at().getY() + HEIGHT * (1.0 - fell);
			Vec3 c = Vec3.atBottomCenterOf(drop.at());
			overworld.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, c.x, y + 1.0, c.z, 1, 0.1, 0.4, 0.1, 0.0);
			overworld.sendParticles(ParticleTypes.FLAME, true, true, c.x, y, c.z, 4, 0.2, 0.3, 0.2, 0.02);
			overworld.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, c.x, y + 0.5, c.z, 2, 0.2, 0.3, 0.2, 0.01);
			return;
		}
		Offer offer = offer(order.offer());
		player.removeAttached(ORDER);
		if (offer != null) {
			land(overworld, player, drop, offer);
		}
	}

	/** Somewhere near the hunter, on open ground under the sky; if nowhere is, above them. */
	static BlockPos spot(final ServerLevel level, final ServerPlayer player) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < 32; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = NEAR + random.nextDouble() * (FAR - NEAR);
			BlockPos column = BlockPos.containing(player.getX() + Math.sin(angle) * distance, 0.0, player.getZ() + Math.cos(angle) * distance);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (fits(level, top) && level.canSeeSky(top) && Math.abs(top.getY() - player.getBlockY()) <= 12) {
				return top;
			}
		}
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition());
	}

	private static boolean fits(final ServerLevel level, final BlockPos at) {
		BlockPos below = at.below();
		return level.isInWorldBounds(at) && level.getBlockState(at).canBeReplaced() && level.getFluidState(at).isEmpty()
			&& level.getFluidState(below).isEmpty() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	/** The barrel comes down: placed and filled, heard far off. */
	private static void land(final ServerLevel level, final ServerPlayer player, final Drop drop, final Offer offer) {
		// whatever was put there meanwhile, it comes down on top; it never breaks anything
		BlockPos at = free(level, player, drop.at());
		Vec3 c;
		if (at == null) {
			// nowhere to stand: the crate burst, and what was in it lies on the ground
			c = Vec3.atBottomCenterOf(drop.at());
			level.addFreshEntity(new ItemEntity(level, c.x, c.y + 0.5, c.z, offer.stack()));
			player.removeAttached(DROP);
		} else {
			level.setBlock(at, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), 3);
			if (level.getBlockEntity(at) instanceof BarrelBlockEntity barrel) {
				barrel.setItem(13, offer.stack());
			}
			if (!at.equals(drop.at())) {
				player.setAttached(DROP, new Drop(at, drop.lands(), drop.until()));
			}
			c = Vec3.atBottomCenterOf(at);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, true, true, c.x, c.y + 0.5, c.z, 2, 0.4, 0.2, 0.4, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, c.x, c.y + 0.5, c.z, 16, 0.8, 0.3, 0.8, 0.02);
		level.sendParticles(ParticleTypes.POOF, true, true, c.x, c.y + 0.3, c.z, 24, 1.0, 0.2, 1.0, 0.05);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.5F, 1.4F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.6F, 0.6F);
		NoiseService.hear(level, c, HEARD);
		player.sendSystemMessage(Component.translatable("message.mistinn.supply.landed"));
	}

	/**
	 * Where the barrel may stand: the spot, or the first of the few above it that is empty or
	 * holds only something that gives way (grass, snow), with no block entity, no water, and
	 * nothing the hunter may not touch there; null if none is.
	 */
	static @Nullable BlockPos free(final ServerLevel level, final ServerPlayer player, final BlockPos at) {
		for (int up = 0; up < 8; up++) {
			BlockPos p = at.above(up);
			BlockState state = level.getBlockState(p);
			if (level.isInWorldBounds(p) && (state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty())
				&& level.getBlockEntity(p) == null && level.mayInteract(player, p)) {
				return p;
			}
		}
		return null;
	}

	/** A barrel down: it smokes until emptied, and the mark goes when it is, or in time. */
	private static void lies(final ServerPlayer player, final Drop drop, final long now) {
		if (now < drop.lands()) {
			return;
		}
		ServerLevel level = player.level().getServer().overworld();
		boolean there = level.isLoaded(drop.at()) && level.getBlockEntity(drop.at()) instanceof BarrelBlockEntity barrel && !barrel.isEmpty();
		if (now <= drop.until() && level.isLoaded(drop.at()) && !there) {
			// the crate emptied before it was taken back: what the station sent down reached a hunter
			life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.Achievement.SUPPLY_DROP);
		}
		if (now > drop.until() || level.isLoaded(drop.at()) && !there) {
			player.removeAttached(DROP);
			return;
		}
		if (there && now % 20 == 0) {
			Vec3 c = Vec3.atBottomCenterOf(drop.at());
			level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, c.x, c.y + 1.1, c.z, 1, 0.1, 0.1, 0.1, 0.01);
		}
	}

	/** Seconds until an order lands, at {@code now}, rounded up. */
	public static int secondsLeft(final Order order, final long now) {
		return Math.max(0, Mth.ceil((order.due() - now) / 20.0));
	}

	// ── Payloads ──────────────────────────────────────────────────

	/** The guide asks the station for an offer. */
	public record OrderPayload(String offer) implements CustomPacketPayload {
		public static final Type<OrderPayload> TYPE = new Type<>(Mistinn.id("supply_order"));
		public static final StreamCodec<ByteBuf, OrderPayload> CODEC = ByteBufCodecs.stringUtf8(64).map(OrderPayload::new, OrderPayload::offer);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The station's answer to an {@link OrderPayload}: a {@link Result} by ordinal. */
	public record OrderResultPayload(String offer, int result) implements CustomPacketPayload {
		public static final Type<OrderResultPayload> TYPE = new Type<>(Mistinn.id("supply_order_result"));
		public static final StreamCodec<ByteBuf, OrderResultPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), OrderResultPayload::offer,
			ByteBufCodecs.VAR_INT, OrderResultPayload::result,
			OrderResultPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
