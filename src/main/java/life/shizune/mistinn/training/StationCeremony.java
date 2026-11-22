package life.shizune.mistinn.training;

import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.station.StationBroadcast;
import life.shizune.mistinn.station.StationHand;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import life.shizune.mistinn.world.PodPlate;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * The drop, the end of the course. First the station sees 3411 off: everyone gathers under the
 * crane, 3411 takes the pod, Helm calls the drop, the bell rings and the pod goes down. Then it is
 * the hunter's turn: once they have chosen where to go, the bell rings three times, and with every
 * ring there are fewer people on the deck, until the screen goes dark. The clamps let go and the
 * seat falls from the crane into the sea, and goes under; only then is the hunter sent on, to
 * {@link StationArrival}.
 */
public final class StationCeremony {
	/** The tower bell, where the station's rings come from. */
	private static final BlockPos BELL = new BlockPos(20, 99, -100);
	/** Under the pod, where it disappears into the fog. */
	private static final BlockPos BELOW_POD = new BlockPos(-27, 84, -129);
	private static final BlockPos GATE = new BlockPos(-27, 91, -122);
	private static final BlockPos SEAT = new BlockPos(-27, 91, -129);
	/** Where each of them stands to watch, facing the pod. */
	private static final Map<StaffRole, BlockPos> SPOTS = Map.ofEntries(
		Map.entry(StaffRole.QUARTERMASTER, new BlockPos(-40, 91, -113)),
		Map.entry(StaffRole.COOK, new BlockPos(-38, 91, -113)),
		Map.entry(StaffRole.SMITH, new BlockPos(-36, 91, -113)),
		Map.entry(StaffRole.INSTRUCTOR, new BlockPos(-34, 91, -113)),
		Map.entry(StaffRole.SCOUT, new BlockPos(-32, 91, -113)),
		Map.entry(StaffRole.DOCTOR, new BlockPos(-30, 91, -113)),
		Map.entry(StaffRole.ARCHIVIST, new BlockPos(-24, 91, -113)),
		Map.entry(StaffRole.BELL_KEEPER, new BlockPos(-22, 91, -113)),
		Map.entry(StaffRole.RADIO_OPERATOR, new BlockPos(-20, 91, -113)),
		Map.entry(StaffRole.DOCK_WORKER_1, new BlockPos(-40, 91, -110)),
		Map.entry(StaffRole.DOCK_WORKER_2, new BlockPos(-37, 91, -110)),
		Map.entry(StaffRole.DOCK_WORKER_3, new BlockPos(-22, 91, -110)),
		Map.entry(StaffRole.DOCK_WORKER_4, new BlockPos(-19, 91, -110)));
	/** Who is gone from the deck after each of the three rings. */
	private static final List<Set<StaffRole>> THINNING = List.of(
		EnumSet.of(StaffRole.DOCK_WORKER_1, StaffRole.DOCK_WORKER_2, StaffRole.DOCK_WORKER_3, StaffRole.DOCK_WORKER_4),
		EnumSet.of(StaffRole.COOK, StaffRole.SMITH, StaffRole.SCOUT, StaffRole.QUARTERMASTER, StaffRole.RADIO_OPERATOR),
		EnumSet.of(StaffRole.INSTRUCTOR, StaffRole.DOCTOR, StaffRole.ARCHIVIST, StaffRole.BELL_KEEPER));
	public static final int FAREWELL_TICKS = 160;
	private static final int[] RINGS = {0, 50, 100};
	/** When the clamps let go of the seat, after the third bell has died away. */
	public static final int RELEASE = 112;
	/** Half the fall's pull: the seat drops {@code FALL * t * t} blocks in t ticks, as anything falls. */
	private static final double FALL = 0.04;
	/** The top of the sea around the station, and the lowest the hunter's eyes go under it (the floor is at 52). */
	private static final double SURFACE = 63.0;
	private static final double LOWEST_EYE = 54.0;
	/** How quickly the water slows the seat, and how long the hunter goes down before being sent on. */
	private static final double DRAG = 0.08;
	public static final int SINK_TICKS = 52;
	/** Sent on regardless by now, should the sea never be reached. */
	private static final int GIVE_UP = RELEASE + 160;
	/** The number plate on the crane's pod, by its mouth, facing the deck. */
	public static final BlockPos PLATE = new BlockPos(-29, 92, -125);

	private static final Map<UUID, Integer> FAREWELL = new HashMap<>();
	private static final Set<UUID> SEEN_OFF = new HashSet<>();
	private static final Map<UUID, Drop> DROPS = new HashMap<>();
	/** Roles gone from the station for good this session: the staff spawner leaves their posts empty. */
	private static final Set<StaffRole> DEPARTED = EnumSet.noneOf(StaffRole.class);

	/**
	 * A drop under way: where it goes, how long it has run, the seat, how far the hunter's eyes sit
	 * above it, where it was let go from, and when and where it reached the sea (-1 before then).
	 */
	private record Drop(WorldMode next, int ticks, UUID seat, double eye, double top, int splash, double splashY) {
		Drop next(final int ticks, final double top, final int splash, final double splashY) {
			return new Drop(this.next, ticks, this.seat, this.eye, top, splash, splashY);
		}
	}

	private StationCeremony() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(DropPayload.TYPE, DropPayload.CODEC);
	}

	/** Whether 3411 has gone, so the seat is the hunter's. */
	public static boolean seenOff(final ServerPlayer hunter) {
		return SEEN_OFF.contains(hunter.getUUID());
	}

	/** Whether the hunter is being dropped right now. */
	public static boolean dropping(final ServerPlayer hunter) {
		return DROPS.containsKey(hunter.getUUID());
	}

	static boolean departed(final StaffRole role) {
		return DEPARTED.contains(role);
	}

	/** The hunter chose where to go: the three rings begin. */
	static void drop(final ServerPlayer hunter, final WorldMode next) {
		Entity seat = hunter.getVehicle();
		if (DROPS.containsKey(hunter.getUUID()) || seat == null) {
			return;
		}
		DROPS.put(hunter.getUUID(), new Drop(next, 0, seat.getUUID(), hunter.getEyeY() - seat.getY(), seat.getY(), -1, 0.0));
		plate(hunter.level(), PodPlate.HUNTER);
		ServerPlayNetworking.send(hunter, new DropPayload(RELEASE));
	}

	static void tick(final ServerPlayer hunter, final ServerLevel range) {
		UUID id = hunter.getUUID();
		Drop drop = DROPS.get(id);
		if (drop != null) {
			dropTick(hunter, range, drop);
			return;
		}
		TrainingLesson lesson = hunter.getAttached(TrainingService.LESSON);
		boolean top = hunter.getY() >= TrainingLesson.BOARD.target.getY() - 2;
		if (top && range.getGameTime() % 10 == 0 && (lesson == TrainingLesson.REPORT || lesson == TrainingLesson.BOARD || lesson == TrainingLesson.DEPLOY)) {
			// the pod at the crane is 3411's until it has gone down, then the next one is the hunter's
			plate(range, SEEN_OFF.contains(id) || FAREWELL.getOrDefault(id, 0) > 100 ? PodPlate.HUNTER : PodPlate.ROOMMATE);
		}
		if (lesson != TrainingLesson.BOARD || SEEN_OFF.contains(id) || !top) {
			return;
		}
		int t = FAREWELL.merge(id, 1, Integer::sum) - 1;
		if (t == 0) {
			gather(range);
			plate(range, PodPlate.ROOMMATE);
		}
		StationHand roommate = find(range, StaffRole.ROOMMATE);
		if (t == 0) {
			if (roommate != null) {
				place(roommate, GATE, 180.0F);
				roommate.setCustomName(Component.translatable(StaffRole.ROOMMATE.key() + ".farewell"));
				roommate.setCustomNameVisible(true);
			}
		} else if (t == 40 && roommate != null) {
			// in the seat, facing back out at the deck
			place(roommate, SEAT, 0.0F);
		} else if (t == 60) {
			StationBroadcast.say(hunter, StationBroadcast.FAREWELL);
		} else if (t == 100) {
			range.playSound(null, SEAT, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.2F, 0.6F);
			range.playSound(null, SEAT, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.5F, 0.5F);
			ring(range);
			range.sendParticles(ParticleTypes.CLOUD, BELOW_POD.getX() + 0.5, BELOW_POD.getY(), BELOW_POD.getZ() + 0.5, 60, 2.0, 1.5, 2.0, 0.02);
			if (roommate != null) {
				roommate.discard();
			}
			DEPARTED.add(StaffRole.ROOMMATE);
			// the crane brings the next pod up: the hunter's
			plate(range, PodPlate.HUNTER);
		} else if (t == FAREWELL_TICKS) {
			StationBroadcast.say(hunter, StationBroadcast.YOUR_TURN);
			SEEN_OFF.add(id);
			FAREWELL.remove(id);
		}
	}

	private static void dropTick(final ServerPlayer hunter, final ServerLevel range, final Drop drop) {
		int t = drop.ticks();
		for (int i = 0; i < RINGS.length; i++) {
			if (t == RINGS[i]) {
				ring(range);
				for (StaffRole role : THINNING.get(i)) {
					StationHand hand = find(range, role);
					if (hand != null) {
						hand.discard();
					}
					DEPARTED.add(role);
				}
			}
		}
		Entity seat = range.getEntity(drop.seat());
		if (seat == null) {
			// the seat is lost: the hunter is sent on as they are
			DROPS.remove(hunter.getUUID());
			TrainingService.complete(hunter, drop.next());
			return;
		}
		if (hunter.getVehicle() != seat) {
			// back into the seat: no one gets out of a falling pod
			hunter.teleportTo(range, seat.getX(), seat.getY(), seat.getZ(), Set.of(), hunter.getYRot(), hunter.getXRot(), true);
			hunter.startRiding(seat, true, true);
		}
		double top = drop.top();
		int splash = drop.splash();
		double splashY = drop.splashY();
		if (t == RELEASE) {
			top = seat.getY();
			range.playSound(null, seat.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
			range.playSound(null, seat.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.5F, 0.5F);
			range.playSound(null, seat.blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.5F, 0.5F);
		} else if (t > RELEASE) {
			double y;
			if (splash < 0) {
				int fallen = t - RELEASE;
				y = top - FALL * fallen * fallen;
				if (y + drop.eye() <= SURFACE) {
					splash = t;
					splashY = y;
					double x = seat.getX();
					double z = seat.getZ();
					range.playSound(null, seat.blockPosition().atY((int) SURFACE), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 2.0F, 0.7F);
					range.playSound(null, seat.blockPosition().atY((int) SURFACE), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.5F, 0.5F);
					range.sendParticles(ParticleTypes.SPLASH, x, SURFACE, z, 160, 1.6, 0.2, 1.6, 0.4);
					range.sendParticles(ParticleTypes.CLOUD, x, SURFACE + 0.5, z, 30, 1.2, 0.3, 1.2, 0.05);
				}
			} else {
				// the water takes the fall and lets the seat sink, slower and slower, short of the floor
				double depth = splashY + drop.eye() - LOWEST_EYE;
				y = splashY - depth * (1.0 - Math.exp(-DRAG * (t - splash)));
				if ((t - splash) % 2 == 0) {
					range.sendParticles(ParticleTypes.BUBBLE, seat.getX(), y + drop.eye() - 0.6, seat.getZ(), 6, 0.5, 0.3, 0.5, 0.05);
				}
			}
			seat.setPos(seat.getX(), y, seat.getZ());
		}
		if (splash >= 0 && t >= splash + SINK_TICKS || t >= GIVE_UP) {
			DROPS.remove(hunter.getUUID());
			TrainingService.complete(hunter, drop.next());
			return;
		}
		DROPS.put(hunter.getUUID(), drop.next(t + 1, top, splash, splashY));
	}

	/**
	 * Writes the number on the crane pod's plate, putting the plate up first if it is not there:
	 * the station's own, in glowing gold on dark wood.
	 */
	private static void plate(final ServerLevel range, final String number) {
		SignText text = PodPlate.text(PodPlate.STATION_KEY, number, DyeColor.YELLOW, true);
		if (!range.getBlockState(PLATE).is(Blocks.DARK_OAK_WALL_SIGN)) {
			range.setBlock(PLATE, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH), 3);
		}
		if (range.getBlockEntity(PLATE) instanceof SignBlockEntity sign && !sign.getText(SignTextSlot.FRONT).equals(text)) {
			sign.setText(text, SignTextSlot.FRONT);
			sign.setWaxed(true);
			sign.setChanged();
			range.sendBlockUpdated(PLATE, range.getBlockState(PLATE), range.getBlockState(PLATE), 3);
		}
	}

	/**
	 * Everyone comes up to the deck to see the drop. Whoever's part of the station is not loaded
	 * comes up all the same: they are set down on the deck, and the one left at the post is let go
	 * when it loads ({@link TrainingService}).
	 */
	private static void gather(final ServerLevel range) {
		for (Map.Entry<StaffRole, BlockPos> spot : SPOTS.entrySet()) {
			bring(range, spot.getKey(), spot.getValue(), 180.0F);
		}
		bring(range, StaffRole.ROOMMATE, GATE, 180.0F);
	}

	private static void bring(final ServerLevel range, final StaffRole role, final BlockPos spot, final float yaw) {
		if (DEPARTED.contains(role)) {
			return;
		}
		StationHand hand = find(range, role);
		if (hand == null) {
			hand = life.shizune.mistinn.registry.MistinnEntities.STATION_HAND.create(range, net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
			if (hand == null) {
				return;
			}
			hand.setRole(role);
			hand.setCustomName(Component.translatable(role.key()));
			place(hand, spot, yaw);
			hand.addTag(StationHand.GATHERED);
			range.addFreshEntity(hand);
			return;
		}
		hand.addTag(StationHand.GATHERED);
		hand.getNavigation().stop();
		place(hand, spot, yaw);
	}

	private static void place(final StationHand hand, final BlockPos pos, final float yaw) {
		hand.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
		hand.setYHeadRot(yaw);
		hand.setYBodyRot(yaw);
	}

	private static StationHand find(final ServerLevel range, final StaffRole role) {
		return range.getEntitiesOfClass(StationHand.class, new AABB(BlockPos.ZERO).inflate(256.0), h -> h.role() == role)
			.stream().findFirst().orElse(null);
	}

	/** The tower bell, loud enough for the whole station. */
	private static void ring(final ServerLevel range) {
		range.playSound(null, BELL, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 8.0F, 0.6F);
	}

	/** Test and world-close hook. */
	public static void forgetAll() {
		FAREWELL.clear();
		SEEN_OFF.clear();
		DROPS.clear();
		DEPARTED.clear();
	}

	/** Server to client: the drop has begun; the seat is let go this many ticks from now. */
	public record DropPayload(int ticks) implements CustomPacketPayload {
		public static final Type<DropPayload> TYPE = new Type<>(Mistinn.id("training_drop"));
		public static final StreamCodec<ByteBuf, DropPayload> CODEC = ByteBufCodecs.VAR_INT.map(DropPayload::new, DropPayload::ticks);

		@Override
		public Type<DropPayload> type() {
			return TYPE;
		}
	}
}
