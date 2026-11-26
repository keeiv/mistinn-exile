package life.shizune.mistinn.naval;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 霧海的海盜: when pirates come for a hunter at sea, the fight, and how it ends.
 *
 * <p>A hunter who sails far enough out that no land shows, long enough, may be found by a pirate
 * ship: it heaves to a stone's throw away, broadside on, and its crew comes for the salt they carry.
 * The guns fire while a gunner lives; the marksmen shoot from the quarterdeck; boarders wait on deck
 * for whoever climbs the ladders. When the captain falls the ship is taken: the crew go over the
 * side and the ship stays where it lies, the captain's chest in the cabin. Each ship taken makes the
 * next crews harder. If the hunters leave or fall, the ship and its crew wait where they are.
 *
 * <p>The world keeps its ships ({@link #SEA}); a hunter keeps when the pirates may next come
 * ({@link #NEXT}). The fight itself (the bar, the guns) is not saved: it picks up from the ships.
 */
public final class NavalService {
	/** How hard the crews get, ship by ship taken. */
	public static final int MAX_NOTORIETY = 4;
	/** Sailing out of sight of land this long, in ticks, before pirates may come; then a chance each second. */
	public static final int SEA_TIME = 900;
	public static final float CHANCE = 0.04F;
	/** After pirates come for a hunter, the next may come no sooner than this. */
	public static final long COOLDOWN = 48000L;
	/** No land within this many blocks counts as out at sea. */
	public static final int CLEAR = 24;
	/** A ship still to be taken keeps others this far off. */
	public static final double APART = 256.0;
	/** Hunters this near a ship take part in its fight; one this near wakes a ship that waits. */
	public static final double PART = 96.0;
	public static final double WAKE = 48.0;
	/** Ticks with no hunter near before the fight is set aside. */
	public static final int SET_ASIDE = 1200;
	/** The guns: first shot after the ship appears, the time between one broadside and the next, between guns. */
	public static final int FIRST_VOLLEY = 200;
	public static final int VOLLEY = 140;
	public static final int GUN_GAP = 8;
	/** Nearer or further than these, the guns are not used on a hunter. */
	public static final double GUN_NEAR = 8.0;
	public static final double GUN_FAR = 56.0;
	/** How many ship names (and captains) there are. */
	public static final int NAMES = 6;

	public static final int ACTIVE = 0;
	public static final int TAKEN = 1;
	public static final int LOST = 2;
	/** Still sailing in out of the fog: no blocks yet, no crew. */
	public static final int ARRIVING = 3;
	/** How long the ship takes to sail in, and how far out it is first seen. */
	public static final int APPROACH_TICKS = 220;
	public static final int APPROACH = 72;

	public static final ResourceKey<LootTable> CHEST = ResourceKey.create(Registries.LOOT_TABLE, Mistinn.id("chests/pirate_ship"));
	public static final ResourceKey<LootTable> HOLD = ResourceKey.create(Registries.LOOT_TABLE, Mistinn.id("chests/pirate_hold"));

	/** A ship of the fog sea in this world: where it lies, which it is, how hard its crew, and how its fight stands. */
	public record Ship(int id, BlockPos origin, int turn, int name, int notoriety, int state, Optional<UUID> captain, long since) {
		public static final Codec<Ship> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Ship::id),
			BlockPos.CODEC.fieldOf("origin").forGetter(Ship::origin),
			Codec.INT.fieldOf("turn").forGetter(Ship::turn),
			Codec.INT.fieldOf("name").forGetter(Ship::name),
			Codec.INT.optionalFieldOf("notoriety", 0).forGetter(Ship::notoriety),
			Codec.INT.optionalFieldOf("state", ACTIVE).forGetter(Ship::state),
			UUIDUtil.CODEC.optionalFieldOf("captain").forGetter(Ship::captain),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(Ship::since)
		).apply(i, Ship::new));

		public ShipFrame frame() {
			return new ShipFrame(this.origin, Rotation.values()[Math.floorMod(this.turn, 4)]);
		}

		Ship withState(final int state) {
			return new Ship(this.id, this.origin, this.turn, this.name, this.notoriety, state, this.captain, this.since);
		}

		Ship arrived(final UUID captain) {
			return new Ship(this.id, this.origin, this.turn, this.name, this.notoriety, ACTIVE, Optional.of(captain), this.since);
		}

		/** Whether its fight is still to be decided (sailing in, or heaved to with its captain alive). */
		public boolean unresolved() {
			return this.state == ACTIVE || this.state == ARRIVING;
		}

		public Component shipName() {
			return Component.translatable("naval.mistinn.ship." + this.name);
		}

		public Component captainName() {
			return Component.translatable("naval.mistinn.captain." + this.name);
		}
	}

	/** The world's ships, the next id, and how many have been taken (the crews' notoriety). */
	public record Sea(List<Ship> ships, int next, int taken) {
		public static final Sea EMPTY = new Sea(List.of(), 1, 0);
		public static final Codec<Sea> CODEC = RecordCodecBuilder.create(i -> i.group(
			Ship.CODEC.listOf().fieldOf("ships").forGetter(Sea::ships),
			Codec.INT.optionalFieldOf("next", 1).forGetter(Sea::next),
			Codec.INT.optionalFieldOf("taken", 0).forGetter(Sea::taken)
		).apply(i, Sea::new));
	}

	public static final AttachmentType<Sea> SEA = AttachmentRegistry.<Sea>builder()
		.persistent(Sea.CODEC)
		.buildAndRegister(Mistinn.id("pirate_sea"));
	/** The game time before which pirates do not come for this hunter again. */
	public static final AttachmentType<Long> NEXT = AttachmentRegistry.<Long>builder()
		.persistent(Codec.LONG)
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("pirates_next"));

	/** A fight under way: its bar, its guns, and how long it has gone quiet. */
	private static final class Battle {
		final ServerBossEvent bar;
		final int[] side = new int[2];
		final List<Pending> volley = new ArrayList<>();
		int age;
		int quiet;
		boolean asleep;
		int missing;
		/** The most crew seen standing, and how many the bar shows now. */
		int most;
		int shown = -1;

		Battle(final Ship ship) {
			this.bar = new ServerBossEvent(UUID.randomUUID(), barName(ship, 0, 0), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
			this.side[0] = FIRST_VOLLEY;
			this.side[1] = FIRST_VOLLEY;
		}
	}

	/** A gun about to fire at a hunter: in how many ticks, which gun, at whom. */
	private static final class Pending {
		int delay;
		final int gun;
		final UUID target;

		Pending(final int delay, final int gun, final UUID target) {
			this.delay = delay;
			this.gun = gun;
			this.target = target;
		}
	}

	private static final Map<UUID, Integer> AT_SEA = new HashMap<>();
	/** The ships under sail now, by id. */
	private static final Map<Integer, SailingShip> UNDER_SAIL = new HashMap<>();
	private static final Map<Integer, Battle> BATTLES = new HashMap<>();
	/** Where each ship lies, by id: what its crew reckon "aboard" from. */
	private static final Map<Integer, ShipFrame> FRAMES = new HashMap<>();
	/** For tests: whether a hunter's chance each second always comes up, and how long they must sail first. */
	public static boolean certain;
	public static int seaTime = SEA_TIME;

	private NavalService() {
	}

	public static void register() {
		NavalFx.register();
		NavalIntroPayload.register();
		ServerTickEvents.END_SERVER_TICK.register(NavalService::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BATTLES.values().forEach(battle -> battle.bar.removeAllPlayers());
			BATTLES.clear();
			FRAMES.clear();
			AT_SEA.clear();
			UNDER_SAIL.clear();
		});
	}

	// ── The world's ships ─────────────────────────────────────────

	public static Sea sea(final MinecraftServer server) {
		return server.overworld().getAttachedOrElse(SEA, Sea.EMPTY);
	}

	private static void save(final MinecraftServer server, final Sea sea) {
		server.overworld().setAttached(SEA, sea);
		FRAMES.clear();
		for (Ship ship : sea.ships()) {
			FRAMES.put(ship.id(), ship.frame());
		}
	}

	public static @Nullable Ship ship(final MinecraftServer server, final int id) {
		for (Ship ship : sea(server).ships()) {
			if (ship.id() == id) {
				return ship;
			}
		}
		return null;
	}

	/** Where a ship lies, for its crew; null when the ship is not known. */
	public static @Nullable ShipFrame frame(final int id) {
		return FRAMES.get(id);
	}

	/** The middle of a ship's deck, or {@code fallback} when the ship is not known. */
	public static Vec3 middle(final int id, final Vec3 fallback) {
		ShipFrame frame = FRAMES.get(id);
		return frame == null ? fallback : frame.middle();
	}

	// ── Each tick ─────────────────────────────────────────────────

	private static void tick(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (FRAMES.isEmpty() && !sea(server).ships().isEmpty()) {
			save(server, sea(server));
		}
		if (server.getTickCount() % 20 == 0) {
			for (ServerPlayer player : level.players()) {
				sail(level, player);
			}
		}
		for (Ship ship : List.copyOf(sea(server).ships())) {
			if (ship.state() == ACTIVE) {
				fight(level, ship);
			} else if (ship.state() == ARRIVING) {
				SailingShip sailing = UNDER_SAIL.get(ship.id());
				if ((sailing == null || sailing.isRemoved()) && level.isLoaded(ship.origin())) {
					// the world was closed while it sailed in: it is where it was going
					arrive(level, ship);
				}
			}
		}
	}

	/** A hunter at sea: how long they have sailed out of sight of land, and whether pirates come for them now. */
	private static void sail(final ServerLevel level, final ServerPlayer player) {
		if (!atSea(level, player)) {
			AT_SEA.remove(player.getUUID());
			return;
		}
		int sailed = AT_SEA.merge(player.getUUID(), 20, Integer::sum);
		if (sailed < seaTime || level.getGameTime() < player.getAttachedOrElse(NEXT, 0L)) {
			return;
		}
		if (!certain && level.getRandom().nextFloat() >= CHANCE) {
			return;
		}
		for (Ship ship : sea(level.getServer()).ships()) {
			if (ship.unresolved() && ship.frame().middle().distanceTo(player.position()) < APART) {
				return;
			}
		}
		if (encounter(level, player) == null) {
			// no open water to heave to in: try again a little later
			AT_SEA.put(player.getUUID(), seaTime - 300);
		}
	}

	/** Whether a hunter is out at sea in a boat, in a world and a moment where pirates may come. */
	public static boolean atSea(final ServerLevel level, final ServerPlayer player) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator() || level.getDifficulty() == Difficulty.PEACEFUL
			|| !level.dimension().equals(Level.OVERWORLD) || player.level() != level || WorldMode.of(level.getServer()) == WorldMode.TRAINING
			|| !(player.getVehicle() instanceof AbstractBoat boat) || !boat.isInWater()) {
			return false;
		}
		BlockPos at = player.blockPosition();
		if (!level.getBiome(at).is(BiomeTags.IS_OCEAN)) {
			return false;
		}
		return openSea(level, at.getX(), at.getZ(), CLEAR);
	}

	/** Whether no land rises above the sea within {@code radius} of a place (looked at on two rings). */
	public static boolean openSea(final Level level, final int x, final int z, final int radius) {
		int sea = level.getSeaLevel();
		for (int ring : new int[] {radius / 2, radius}) {
			for (int i = 0; i < 16; i++) {
				double angle = Math.PI * 2.0 * i / 16.0;
				int px = x + (int) Math.round(Math.cos(angle) * ring);
				int pz = z + (int) Math.round(Math.sin(angle) * ring);
				if (!level.hasChunk(px >> 4, pz >> 4) || level.getHeight(Heightmap.Types.WORLD_SURFACE, px, pz) > sea) {
					return false;
				}
			}
		}
		return true;
	}

	// ── A ship comes ──────────────────────────────────────────────

	/**
	 * Pirates come for a hunter: a ship is found a berth and comes sailing in out of the fog toward it
	 * (straight there when the way in is not open sea); when it is there it is raised and crewed and the
	 * fight begins. Null when there was no room.
	 */
	public static @Nullable Ship encounter(final ServerLevel level, final ServerPlayer player) {
		ShipFrame frame = heaveTo(level, player);
		if (frame == null) {
			return null;
		}
		MinecraftServer server = level.getServer();
		Sea sea = sea(server);
		int id = sea.next();
		int name = Math.floorMod(id * 7 + level.getRandom().nextInt(NAMES), NAMES);
		int notoriety = Math.min(MAX_NOTORIETY, sea.taken());
		Vec3 berth = frame.world(0.5, 0.0, 0.5);
		Vec3 bow = Vec3.atLowerCornerOf(frame.world(net.minecraft.core.Direction.EAST).getUnitVec3i());
		int approach = approach(level, berth, bow);
		Ship ship = new Ship(id, frame.origin(), frame.rotation().ordinal(), name, notoriety, ARRIVING, Optional.empty(), level.getGameTime());
		List<Ship> ships = new ArrayList<>(sea.ships());
		ships.add(ship);
		save(server, new Sea(ships, id + 1, sea.taken()));
		List<ServerPlayer> hunters = new ArrayList<>();
		for (ServerPlayer hunter : level.players()) {
			if (hunter.position().distanceTo(frame.middle()) < PART) {
				hunter.setAttached(NEXT, level.getGameTime() + COOLDOWN);
				AT_SEA.remove(hunter.getUUID());
				hunters.add(hunter);
			}
		}
		if (approach <= 0) {
			arrive(level, ship);
			hunters.forEach(hunter -> announceArrival(hunter, ship));
			return ship(server, id);
		}
		SailingShip sailing = MistinnEntities.SAILING_SHIP.create(level, EntitySpawnReason.EVENT);
		Vec3 start = berth.subtract(bow.scale(approach));
		sailing.sail(id, frame.rotation(), start, berth, APPROACH_TICKS);
		level.addFreshEntity(sailing);
		UNDER_SAIL.put(id, sailing);
		// the horn, from out in the fog where the ship comes from
		level.playSound(null, start.x, start.y + 8.0, start.z, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64.0F, 0.8F);
		for (ServerPlayer hunter : hunters) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(hunter, new NavalIntroPayload(sailing.getId(), name, APPROACH_TICKS));
		}
		return ship;
	}

	/** How far out the ship can be seen coming along its keel to its berth over open sea: the most of a few tries, or 0. */
	private static int approach(final ServerLevel level, final Vec3 berth, final Vec3 bow) {
		int sea = level.getSeaLevel();
		for (int distance : new int[] {APPROACH, APPROACH * 2 / 3, APPROACH / 3}) {
			boolean open = true;
			for (int k = 1; k <= 6 && open; k++) {
				Vec3 at = berth.subtract(bow.scale(distance * k / 6.0));
				int x = Mth.floor(at.x);
				int z = Mth.floor(at.z);
				open = level.hasChunk(x >> 4, z >> 4) && level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) <= sea;
			}
			if (open) {
				return distance;
			}
		}
		return 0;
	}

	/** The ship under sail has reached its berth: the real one takes its place, crewed, and a warning shot is fired. */
	public static void arrived(final ServerLevel level, final SailingShip sailing) {
		UNDER_SAIL.remove(sailing.ship());
		Ship ship = ship(level.getServer(), sailing.ship());
		if (ship != null && ship.state() == ARRIVING) {
			arrive(level, ship);
			Ship now = ship(level.getServer(), ship.id());
			for (ServerPlayer hunter : level.players()) {
				if (hunter.position().distanceTo(ship.frame().middle()) < PART) {
					announceArrival(hunter, now);
				}
			}
			ServerPlayer nearest = null;
			for (ServerPlayer hunter : level.players()) {
				if (!hunter.isSpectator() && hunter.isAlive() && (nearest == null
					|| hunter.distanceToSqr(ship.frame().middle()) < nearest.distanceToSqr(ship.frame().middle()))) {
					nearest = hunter;
				}
			}
			if (nearest != null && nearest.position().distanceTo(ship.frame().middle()) < GUN_FAR) {
				warn(level, ship.frame(), nearest);
			}
		}
	}

	/** Raises a ship that was sailing in, crews it, and starts its fight. */
	private static void arrive(final ServerLevel level, final Ship ship) {
		UUID captain = raise(level, ship.frame(), ship.id(), ship.name(), ship.notoriety());
		MinecraftServer server = level.getServer();
		Sea sea = sea(server);
		List<Ship> ships = new ArrayList<>();
		for (Ship other : sea.ships()) {
			ships.add(other.id() == ship.id() ? other.arrived(captain) : other);
		}
		save(server, new Sea(ships, sea.next(), sea.taken()));
	}

	/** A shot across the bow: it falls short, in front of the hunter, a column of spray between them and the ship. */
	private static void warn(final ServerLevel level, final ShipFrame frame, final ServerPlayer target) {
		int side = frame.local(target.position()).z >= 0.5 ? 0 : 3;
		ShipPlan.Gun gun = ShipPlan.guns().get(side + 1);
		Vec3 out = Vec3.atLowerCornerOf(frame.world(gun.out()).getUnitVec3i());
		Vec3 muzzle = frame.centre(gun.muzzle()).add(0.0, 0.5, 0.0).add(out.scale(0.9));
		Vec3 fallsShort = target.position().add(muzzle.subtract(target.position()).multiply(1.0, 0.0, 1.0).normalize().scale(7.0));
		CannonShot shot = MistinnEntities.CANNON_SHOT.create(level, EntitySpawnReason.TRIGGERED);
		if (shot == null) {
			return;
		}
		shot.setPos(muzzle);
		shot.aim(new Vec3(fallsShort.x, level.getSeaLevel() - 0.2, fallsShort.z), Mth.clamp((int) Math.round(muzzle.distanceTo(fallsShort) / 1.5), 16, 40));
		level.addFreshEntity(shot);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 8.0F, 0.5F);
		NavalFx.send(level, NavalFx.CANNON, muzzle, muzzle.add(out));
	}

	/**
	 * Where the ship heaves to: open water a stone's throw ahead of the hunter (or to one side),
	 * broadside on to them, its whole hull over water deep enough to float it.
	 */
	static @Nullable ShipFrame heaveTo(final ServerLevel level, final ServerPlayer player) {
		Vec3 heading = player.getVehicle() != null ? player.getVehicle().getDeltaMovement().multiply(1.0, 0.0, 1.0) : Vec3.ZERO;
		if (heading.lengthSqr() < 1.0E-4) {
			heading = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		double base = Math.atan2(heading.z, heading.x);
		int sea = level.getSeaLevel();
		for (int distance : new int[] {40, 36, 46, 32}) {
			for (int step : new int[] {0, 1, -1, 2, -2, 3, -3, 4, -4, 6}) {
				double angle = base + step * Math.PI / 8.0;
				int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
				int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
				// broadside on: the keel across the line to the hunter, the bow the way they sail
				double toX = player.getX() - x;
				double toZ = player.getZ() - z;
				Rotation turn;
				if (Math.abs(toX) > Math.abs(toZ)) {
					turn = heading.z >= 0.0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
				} else {
					turn = heading.x >= 0.0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
				}
				ShipFrame frame = new ShipFrame(new BlockPos(x, sea - 1, z), turn);
				if (frame.fits(level)) {
					return frame;
				}
			}
		}
		return null;
	}

	/** Puts a ship and its crew in the world; returns its captain. */
	public static UUID raise(final ServerLevel level, final ShipFrame frame, final int id, final int name, final int notoriety) {
		frame.place(level, CHEST, HOLD);
		FRAMES.put(id, frame);
		RandomSource random = level.getRandom();
		Pirate captain = enlist(level, frame, id, PirateRole.CAPTAIN, name % 2 == 0 ? PirateLook.CAPTAIN_1 : PirateLook.CAPTAIN_2, ShipPlan.CAPTAIN,
			frame.yaw(), notoriety);
		List<ShipPlan.Gun> guns = ShipPlan.guns();
		int gunners = notoriety >= 2 ? 3 : 2;
		int[] gunOrder = {1, 4, 0, 3};
		for (int i = 0; i < gunners; i++) {
			ShipPlan.Gun gun = guns.get(gunOrder[i]);
			enlist(level, frame, id, PirateRole.GUNNER, PirateLook.GUNNER, gun.crew(), frame.world(gun.out()).toYRot(), notoriety);
		}
		int marksmen = Math.min(ShipPlan.MARKSMAN_SPOTS.size(), 2 + notoriety / 2);
		for (int i = 0; i < marksmen; i++) {
			enlist(level, frame, id, PirateRole.MARKSMAN, PirateLook.MUSKETEER, ShipPlan.MARKSMAN_SPOTS.get(i), random.nextFloat() * 360.0F, notoriety);
		}
		PirateLook[] boarders = {PirateLook.CUTLASS_1, PirateLook.CUTLASS_2, PirateLook.DECKHAND};
		int count = Math.min(ShipPlan.BOARDER_SPOTS.size(), 2 + notoriety);
		for (int i = 0; i < count; i++) {
			enlist(level, frame, id, PirateRole.BOARDER, boarders[(i + name) % boarders.length], ShipPlan.BOARDER_SPOTS.get(i), random.nextFloat() * 360.0F,
				notoriety);
		}
		return captain.getUUID();
	}

	private static Pirate enlist(final ServerLevel level, final ShipFrame frame, final int id, final PirateRole role, final PirateLook look,
		final BlockPos spot, final float yaw, final int notoriety) {
		Pirate pirate = MistinnEntities.PIRATE.create(level, EntitySpawnReason.EVENT);
		Vec3 at = frame.centre(spot);
		pirate.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		pirate.setYHeadRot(yaw);
		pirate.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.EVENT, null);
		pirate.enlist(id, role, look, frame.world(spot), notoriety);
		level.addFreshEntity(pirate);
		return pirate;
	}

	/** What a hunter is told when the ship has heaved to: its name, and whose it is. */
	private static void announceArrival(final ServerPlayer hunter, final Ship ship) {
		hunter.sendSystemMessage(Component.translatable("message.mistinn.naval.arrive", ship.shipName(), ship.captainName()));
	}

	// ── The fight ─────────────────────────────────────────────────

	private static void fight(final ServerLevel level, final Ship ship) {
		ShipFrame frame = ship.frame();
		Vec3 middle = frame.middle();
		if (!level.isLoaded(BlockPos.containing(middle))) {
			return;
		}
		Battle battle = BATTLES.computeIfAbsent(ship.id(), id -> new Battle(ship));
		battle.age++;
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && player.position().distanceTo(middle) < (battle.asleep ? WAKE : PART)) {
				near.add(player);
			}
		}
		if (near.isEmpty()) {
			if (++battle.quiet > SET_ASIDE && !battle.asleep) {
				battle.asleep = true;
				battle.bar.removeAllPlayers();
			}
			return;
		}
		battle.quiet = 0;
		battle.asleep = false;
		Set<ServerPlayer> shown = new HashSet<>(battle.bar.getPlayers());
		for (ServerPlayer player : near) {
			if (!shown.remove(player)) {
				battle.bar.addPlayer(player);
			}
		}
		shown.forEach(battle.bar::removePlayer);

		crewOnBar(level, ship, frame, battle);
		Pirate captain = ship.captain().map(uuid -> level.getEntity(uuid) instanceof Pirate pirate ? pirate : null).orElse(null);
		if (captain != null && captain.isAlive()) {
			battle.missing = 0;
			battle.bar.setProgress(Mth.clamp(captain.getHealth() / captain.getMaxHealth(), 0.0F, 1.0F));
		} else if (++battle.missing > 200) {
			// the captain is gone with no one seeing him fall (a command, peaceful): the fight is over, with no prize
			end(level, ship, LOST);
			return;
		}
		guns(level, ship, frame, battle, near);
	}

	/** The bar names the ship and its captain, and counts the crew still standing: ◆ for each, ◇ for each lost. */
	private static void crewOnBar(final ServerLevel level, final Ship ship, final ShipFrame frame, final Battle battle) {
		if (battle.age % 10 != 1) {
			return;
		}
		int standing = level.getEntitiesOfClass(Pirate.class, frame.box().inflate(24.0), p -> p.isAlive() && p.ship() == ship.id() && !p.fleeing()).size();
		battle.most = Math.max(battle.most, standing);
		if (standing == battle.shown) {
			return;
		}
		battle.shown = standing;
		battle.bar.setName(barName(ship, standing, battle.most));
	}

	/**
	 * What a ship's bar reads: the ship and its captain, how hard the hunters' name among the
	 * pirates has made its crew (★ for each ship taken before it, ☆ up to the most), and the crew
	 * still standing (◆ for each, ◇ for each lost; nothing until any are counted).
	 */
	public static Component barName(final Ship ship, final int standing, final int most) {
		int notoriety = Mth.clamp(ship.notoriety(), 0, MAX_NOTORIETY);
		MutableComponent name = Component.empty().append(ship.shipName()).append(" · ").append(ship.captainName()).append("  ")
			.append(Component.translatable("hud.mistinn.naval.notoriety", "★".repeat(notoriety) + "☆".repeat(MAX_NOTORIETY - notoriety)));
		if (most > 0) {
			name.append("  ").append(Component.translatable("hud.mistinn.naval.crew", "◆".repeat(standing) + "◇".repeat(Math.max(0, most - standing))));
		}
		return name;
	}

	/** The broadsides: while a gunner lives, each side's guns fire in turn at a hunter off that side. */
	private static void guns(final ServerLevel level, final Ship ship, final ShipFrame frame, final Battle battle, final List<ServerPlayer> near) {
		for (Pending shot : List.copyOf(battle.volley)) {
			if (--shot.delay <= 0) {
				battle.volley.remove(shot);
				ServerPlayer target = level.getServer().getPlayerList().getPlayer(shot.target);
				if (target != null && target.isAlive() && target.level() == level) {
					fire(level, ship, frame, ShipPlan.guns().get(shot.gun), target);
				}
			}
		}
		for (int s = 0; s < 2; s++) {
			if (battle.side[s] > 0) {
				battle.side[s]--;
			}
		}
		boolean manned = !level.getEntitiesOfClass(Pirate.class, frame.box().inflate(4.0),
			p -> p.isAlive() && p.ship() == ship.id() && p.role() == PirateRole.GUNNER && !p.fleeing()).isEmpty();
		if (!manned) {
			return;
		}
		for (ServerPlayer player : near) {
			if (player.isCreative() || frame.aboard(player.position())) {
				continue;
			}
			double distance = player.position().distanceTo(frame.middle());
			if (distance < GUN_NEAR || distance > GUN_FAR) {
				continue;
			}
			int s = frame.local(player.position()).z >= 0.5 ? 0 : 1;
			if (battle.side[s] > 0) {
				continue;
			}
			battle.side[s] = Math.max(80, VOLLEY - 15 * ship.notoriety());
			// the three guns of that side, one after another
			int first = s == 0 ? 0 : 3;
			for (int g = 0; g < 3; g++) {
				battle.volley.add(new Pending(1 + g * GUN_GAP, first + g, player.getUUID()));
			}
		}
	}

	/** One gun fires at a hunter: a flash and a cloud from the gunport, and the shot on its arc to where they will be. */
	private static void fire(final ServerLevel level, final Ship ship, final ShipFrame frame, final ShipPlan.Gun gun, final ServerPlayer target) {
		Vec3 out = Vec3.atLowerCornerOf(frame.world(gun.out()).getUnitVec3i());
		Vec3 muzzle = frame.centre(gun.muzzle()).add(0.0, 0.5, 0.0).add(out.scale(0.9));
		Vec3 aim = target.position();
		double distance = aim.distanceTo(muzzle);
		int ticks = Mth.clamp((int) Math.round(distance / 1.5), 16, 40);
		// where they will be when it lands, give or take
		Entity moving = target.getVehicle() != null ? target.getVehicle() : target;
		RandomSource random = level.getRandom();
		double spread = 2.2 - 0.25 * ship.notoriety();
		Vec3 lead = aim.add(moving.getDeltaMovement().multiply(ticks, 0.0, ticks))
			.add(random.nextGaussian() * spread, 0.0, random.nextGaussian() * spread);
		CannonShot shot = MistinnEntities.CANNON_SHOT.create(level, EntitySpawnReason.TRIGGERED);
		if (shot == null) {
			return;
		}
		shot.setPos(muzzle);
		shot.aim(new Vec3(lead.x, Math.max(lead.y - 0.6, level.getSeaLevel() - 0.2), lead.z), ticks);
		level.addFreshEntity(shot);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 8.0F, 0.5F + random.nextFloat() * 0.1F);
		NavalFx.send(level, NavalFx.CANNON, muzzle, muzzle.add(out));
	}

	/** The captain is dead: the ship is taken. */
	public static void captainFell(final ServerLevel level, final Pirate captain) {
		Ship ship = ship(level.getServer(), captain.ship());
		if (ship != null && ship.state() == ACTIVE) {
			end(level, ship, TAKEN);
		}
	}

	private static void end(final ServerLevel level, final Ship ship, final int state) {
		MinecraftServer server = level.getServer();
		Sea sea = sea(server);
		List<Ship> ships = new ArrayList<>();
		for (Ship other : sea.ships()) {
			ships.add(other.id() == ship.id() ? other.withState(state) : other);
		}
		save(server, new Sea(ships, sea.next(), sea.taken() + (state == TAKEN ? 1 : 0)));
		Battle battle = BATTLES.remove(ship.id());
		List<ServerPlayer> hunters = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceTo(ship.frame().middle()) < PART) {
				hunters.add(player);
			}
		}
		if (battle != null) {
			battle.bar.removeAllPlayers();
		}
		// the crew give up the ship and go over the side, one after another
		int delay = 10;
		for (Pirate pirate : level.getEntitiesOfClass(Pirate.class, ship.frame().box().inflate(16.0), p -> p.ship() == ship.id() && p.isAlive())) {
			pirate.abandon(delay);
			delay += 6 + level.getRandom().nextInt(10);
		}
		if (state == TAKEN) {
			boolean third = sea(server).taken() >= 3;
			for (ServerPlayer hunter : hunters) {
				life.shizune.mistinn.achievement.AchievementService.grant(hunter, life.shizune.mistinn.achievement.AchievementService.PIRATE_SHIP);
				if (third) {
					life.shizune.mistinn.achievement.AchievementService.grant(hunter, life.shizune.mistinn.achievement.AchievementService.FLEET_BREAKER);
				}
				hunter.sendSystemMessage(Component.translatable("message.mistinn.naval.taken", ship.shipName()));
				level.playSound(null, hunter.getX(), hunter.getY(), hunter.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
			}
		}
	}

	/** Tells everyone in a ship's fight something. */
	public static void announce(final ServerLevel level, final int id, final String key) {
		ShipFrame frame = FRAMES.get(id);
		if (frame == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceTo(frame.middle()) < PART) {
				player.sendSystemMessage(Component.translatable(key));
			}
		}
	}

	/** Forgets every ship and fight, for tests. */
	public static void reset(final MinecraftServer server) {
		BATTLES.values().forEach(battle -> battle.bar.removeAllPlayers());
		BATTLES.clear();
		FRAMES.clear();
		AT_SEA.clear();
		UNDER_SAIL.values().forEach(Entity::discard);
		UNDER_SAIL.clear();
		server.overworld().removeAttached(SEA);
	}
}
