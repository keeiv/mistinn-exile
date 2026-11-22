package life.shizune.mistinn.training;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.world.MistinnWorld;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The hunter comes out of the dark falling through the sky of the world they chose: high over its
 * spawn, on a seat no one sees, looking up at first at the clouds going away, then turned over to
 * the station below, until they come down on its pad. The fall waits for the hunter's client to
 * have the new world in front of it, and a hunter who leaves mid-fall picks it up again where they
 * were. The story is told only once the hunter is on the ground and has come to.
 */
public final class StationArrival {
	public static final String SEAT_TAG = "mistinn.arrival_seat";
	/** How long the fall takes once the world is in front of the hunter. */
	public static final int TICKS = 170;
	/** How far above the pad the fall starts, and the least that is still worth a fall. */
	private static final double HEIGHT = 150.0;
	private static final double LEAST = 40.0;
	/** How long the fall waits for the client (loading the world, building the ground) before it starts all the same. */
	private static final int PATIENCE = 300;
	/** How long from landing until the story is told: the hunter comes to first. */
	public static final int SETTLE = 50;
	/** Up to the middle of the afternoon still counts as morning enough to be dropped into. */
	private static final long LATEST = 9000L;

	private static final Map<UUID, Arrival> ARRIVALS = new HashMap<>();

	/** A fall under way, then the moments after it (from {@code settled} = 0 on). */
	private static final class Arrival {
		private final UUID seat;
		private final BlockPos stand;
		private final double top;
		private final double height;
		private final boolean story;
		private boolean ready;
		private int waited;
		private int ticks;
		private int settled = -1;

		private Arrival(final UUID seat, final BlockPos stand, final double top, final boolean story) {
			this.seat = seat;
			this.stand = stand;
			this.top = top;
			this.height = top - stand.getY();
			this.story = story;
		}
	}

	private StationArrival() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(ArrivalPayload.TYPE, ArrivalPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ReadyPayload.TYPE, ReadyPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ReadyPayload.TYPE, (payload, context) -> {
			Arrival arrival = ARRIVALS.get(context.player().getUUID());
			if (arrival != null) {
				arrival.ready = true;
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(StationArrival::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ARRIVALS.remove(handler.getPlayer().getUUID()));
		// nothing hurts the hunter while the drop has them: not the pod's floor going by, not the sea, not the landing
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer hunter && (StationCeremony.dropping(hunter) || falling(hunter))));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ARRIVALS.clear());
	}

	/** Whether the hunter is falling, or has just landed and not yet come to. */
	public static boolean arriving(final ServerPlayer hunter) {
		return ARRIVALS.containsKey(hunter.getUUID());
	}

	private static boolean falling(final ServerPlayer hunter) {
		Arrival arrival = ARRIVALS.get(hunter.getUUID());
		return arrival != null && arrival.settled < 0;
	}

	/**
	 * Starts the hunter's fall into the overworld, over the spawn's pad, on a seat that comes down
	 * with them; the plate goes up on the pod there first if the world is older than plates. Hunters
	 * are dropped in the morning. False if there is no room for a fall or no seat, and the caller
	 * sets the hunter down on the pad instead.
	 */
	static boolean begin(final ServerPlayer hunter, final ServerLevel overworld, final boolean story) {
		BlockPos stand = overworld.getRespawnData().pos();
		MistinnWorld.ensurePlate(overworld, stand);
		double top = Math.min(stand.getY() + HEIGHT, overworld.getMaxY() - 4.0);
		if (top - stand.getY() < LEAST) {
			return false;
		}
		morning(overworld);
		overworld.getChunkAt(stand);
		ArmorStand seat = EntityTypes.ARMOR_STAND.create(overworld, EntitySpawnReason.STRUCTURE);
		if (seat == null) {
			return false;
		}
		seat.addTag(SEAT_TAG);
		seat.setInvisible(true);
		seat.setNoGravity(true);
		seat.setPermanentlyInvulnerable(true);
		seat.setSilent(true);
		seat.noPhysics = true;
		seat.snapTo(stand.getX() + 0.5, top, stand.getZ() + 0.5, 0.0F, 0.0F);
		overworld.addFreshEntity(seat);
		if (!hunter.teleportTo(overworld, seat.getX(), seat.getY(), seat.getZ(), Set.of(), 0.0F, -80.0F, true)
			|| !hunter.startRiding(seat, true, true)) {
			seat.discard();
			return false;
		}
		ARRIVALS.put(hunter.getUUID(), new Arrival(seat.getUUID(), stand, top, story));
		ServerPlayNetworking.send(hunter, new ArrivalPayload(stand, (float) top));
		return true;
	}

	/** First light: a drop never comes down into the dark. */
	private static void morning(final ServerLevel overworld) {
		long time = Math.floorMod(overworld.getOverworldClockTime(), 24000L);
		if (time < LATEST) {
			return;
		}
		overworld.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).get(WorldClocks.OVERWORLD)
			.ifPresent(clock -> overworld.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.DAY));
	}

	private static void tick(final MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		for (ServerPlayer hunter : server.getPlayerList().getPlayers()) {
			Arrival arrival = ARRIVALS.get(hunter.getUUID());
			if (arrival == null) {
				Entity vehicle = hunter.getVehicle();
				if (vehicle != null && vehicle.entityTags().contains(SEAT_TAG) && hunter.level() == overworld) {
					resume(hunter, overworld, vehicle);
				}
				continue;
			}
			if (arrival.settled >= 0) {
				if (++arrival.settled >= SETTLE) {
					ARRIVALS.remove(hunter.getUUID());
					if (arrival.story) {
						StoryService.beginAfterTraining(server);
					}
				}
				continue;
			}
			Entity seat = overworld.getEntity(arrival.seat);
			if (seat == null || hunter.level() != overworld) {
				land(hunter, overworld, arrival, seat);
				continue;
			}
			if (hunter.getVehicle() != seat) {
				// back onto the seat: a hunter who let go up here would not be landing
				hunter.teleportTo(overworld, seat.getX(), seat.getY(), seat.getZ(), Set.of(), hunter.getYRot(), hunter.getXRot(), true);
				hunter.startRiding(seat, true, true);
			}
			if (!arrival.ready && ++arrival.waited < PATIENCE) {
				continue;
			}
			arrival.ready = true;
			arrival.ticks++;
			// falling faster and faster all the way down
			double s = Math.min(1.0, arrival.ticks / (double) TICKS);
			seat.setPos(seat.getX(), arrival.top - arrival.height * (0.3 * s + 0.7 * s * s), seat.getZ());
			if (arrival.ticks >= TICKS) {
				land(hunter, overworld, arrival, seat);
			}
		}
	}

	/** A hunter found on the fall's seat with no fall under way (they left mid-fall): it goes on from where they are. */
	private static void resume(final ServerPlayer hunter, final ServerLevel overworld, final Entity seat) {
		BlockPos stand = overworld.getRespawnData().pos();
		boolean story = WorldMode.of(overworld.getServer()) == WorldMode.STORY && StoryService.get(hunter) == null;
		Arrival arrival = new Arrival(seat.getUUID(), stand, Math.max(seat.getY(), stand.getY() + 1.0), story);
		ARRIVALS.put(hunter.getUUID(), arrival);
		if (arrival.height < LEAST) {
			land(hunter, overworld, arrival, seat);
			return;
		}
		ServerPlayNetworking.send(hunter, new ArrivalPayload(stand, (float) arrival.top));
	}

	/** Down on the pad, facing the pod, with a blow that is heard across the clearing. */
	private static void land(final ServerPlayer hunter, final ServerLevel overworld, final Arrival arrival, final @Nullable Entity seat) {
		hunter.stopRiding();
		if (seat != null) {
			seat.discard();
		}
		BlockPos stand = arrival.stand;
		hunter.teleportTo(overworld, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), 0.0F, 8.0F, true);
		hunter.resetFallDistance();
		hunter.setDeltaMovement(Vec3.ZERO);
		overworld.playSound(null, stand, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.6F);
		overworld.playSound(null, stand, SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.5F, 0.6F);
		overworld.sendParticles(ParticleTypes.POOF, stand.getX() + 0.5, stand.getY() + 0.1, stand.getZ() + 0.5, 30, 1.0, 0.05, 1.0, 0.05);
		arrival.settled = 0;
	}

	/** Server to client: the fall has begun over this stand, from this height. */
	public record ArrivalPayload(BlockPos stand, float top) implements CustomPacketPayload {
		public static final Type<ArrivalPayload> TYPE = new Type<>(Mistinn.id("training_arrival"));
		public static final StreamCodec<ByteBuf, ArrivalPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ArrivalPayload::stand, ByteBufCodecs.FLOAT, ArrivalPayload::top, ArrivalPayload::new);

		@Override
		public Type<ArrivalPayload> type() {
			return TYPE;
		}
	}

	/** Client to server: the new world is on screen; the fall can start. */
	public record ReadyPayload() implements CustomPacketPayload {
		public static final Type<ReadyPayload> TYPE = new Type<>(Mistinn.id("training_arrival_ready"));
		public static final StreamCodec<ByteBuf, ReadyPayload> CODEC = StreamCodec.unit(new ReadyPayload());

		@Override
		public Type<ReadyPayload> type() {
			return TYPE;
		}
	}
}
