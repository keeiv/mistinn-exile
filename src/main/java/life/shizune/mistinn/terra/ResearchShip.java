package life.shizune.mistinn.terra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * 科研船: on many of the highlands' great lakes the crystal tillers keep a research ship at anchor in the middle, a survey
 * vessel, its hull painted teal under a white deck and cabin of mist pine: a hold under the deck, a laboratory cabin aft,
 * a mast, a sampling crane over its side.
 * Its crew (two researchers and two of the ship's wardens) keep to their deck and leave people alone, warn off whoever
 * comes near, and fight anyone who boards: the ship carries the tillers' instruments and stores, the laboratory's chest
 * always a detector among them.
 * <p>
 * A lake's ship comes once the whole of the lake's middle is dug, and only if all of it is open water (nothing of anyone's
 * left standing there): old worlds' lakes get theirs too.
 */
public final class ResearchShip {
	/** How far from the middle of its deck a ship's crew keep. */
	public static final int DECK = 9;
	/** The part of the lakes that have a ship on them (for tests, all). */
	public static double chance = 0.7;
	/** How near (flat) a hunter comes before the crew warn them off, and how often they do. */
	public static final double WARN = 16.0;
	public static final long WARN_EVERY = 6000L;
	/** How long the crew fight a boarder. */
	public static final long FOUGHT = Tiller.GRUDGE;
	public static final ResourceKey<LootTable> LAB_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Mistinn.id("chests/research_ship_lab"));
	public static final ResourceKey<LootTable> HOLD_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Mistinn.id("chests/research_ship_hold"));
	/** The hull, stern to bow along its length, and how far round it the water must be open. */
	public static final int STERN = -11;
	public static final int BOW = 12;
	private static final int ROOM = 6;
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	private static final double WATCHED = 64.0;

	/** A lake's ship: its highland, the middle of its waterline, which way it lies, and whether it came at all. */
	public record Ship(int domain, BlockPos at, boolean alongX, boolean present) {
		public static final Codec<Ship> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Ship::domain),
			BlockPos.CODEC.fieldOf("at").forGetter(Ship::at),
			Codec.BOOL.fieldOf("along_x").forGetter(Ship::alongX),
			Codec.BOOL.fieldOf("present").forGetter(Ship::present)
		).apply(i, Ship::new));

		/** Where a place on the ship lies: {@code u} along it (toward the bow), {@code v} across it. */
		BlockPos pos(final int u, final int y, final int v) {
			return this.alongX ? new BlockPos(this.at.getX() + u, y, this.at.getZ() + v) : new BlockPos(this.at.getX() - v, y, this.at.getZ() + u);
		}

		/** Toward the bow, and across the ship toward its +v side. */
		Direction bow() {
			return this.alongX ? Direction.EAST : Direction.SOUTH;
		}

		Direction side() {
			return this.alongX ? Direction.SOUTH : Direction.WEST;
		}

		/** Whether a hunter is aboard: over the hull, from its hold up to its masthead. */
		public boolean aboard(final ServerPlayer player) {
			BlockPos at = player.blockPosition();
			int u = this.alongX ? at.getX() - this.at.getX() : at.getZ() - this.at.getZ();
			int v = this.alongX ? at.getZ() - this.at.getZ() : this.at.getX() - at.getX();
			return beam(u) >= Math.abs(v) && at.getY() >= this.at.getY() - 1 && at.getY() <= this.at.getY() + 12;
		}
	}

	/** The world's research ships (and the lakes that have none), once settled. */
	public static final AttachmentType<List<Ship>> SHIPS = AttachmentRegistry.<List<Ship>>builder()
		.persistent(Ship.CODEC.listOf())
		.buildAndRegister(Mistinn.id("research_ships"));

	private static final Map<UUID, Long> WARNED = new HashMap<>();

	private ResearchShip() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ResearchShip::watch);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WARNED.clear());
	}

	/** A highland's ship (or its lake's want of one), if settled. */
	public static @Nullable Ship of(final ServerLevel level, final int domain) {
		for (Ship ship : level.getAttachedOrElse(SHIPS, List.of())) {
			if (ship.domain() == domain) {
				return ship;
			}
		}
		return null;
	}

	/** How far either side of its middle line the hull reaches at a place along it (below 0: not the hull). */
	public static int beam(final int u) {
		if (u < STERN || u > BOW) {
			return -1;
		}
		if (u == STERN) {
			return 3;
		}
		if (u <= 7) {
			return 4;
		}
		return switch (u) {
			case 8, 9 -> 3;
			case 10 -> 2;
			case 11 -> 1;
			default -> 0;
		};
	}

	/** Whether a place of the hull is on its outside: its wall below, its rail above. */
	private static boolean edge(final int u, final int v) {
		int across = Math.abs(v);
		return across == beam(u) || beam(u - 1) < across || beam(u + 1) < across;
	}

	// ── Coming to a lake ──────────────────────────────────────────

	/**
	 * Settles whether a highland's lake has its ship, once the lake's middle is all dug: if it is all open water, a ship
	 * comes (or not, as the lake's lot falls), and either way the lake is settled for good.
	 */
	public static void consider(final ServerLevel level, final Domain domain) {
		if (!TerraLake.holds(domain) || of(level, domain.id()) != null) {
			return;
		}
		TerraLake.Lake lake = TerraLake.placed(level, domain.id());
		if (lake == null) {
			return;
		}
		Ship ship = new Ship(domain.id(), new BlockPos(lake.x(), lake.level(), lake.z()), TerraShaper.hash(domain.id(), 17, 4127) < 0.5, true);
		// every chunk under it loaded and dug
		BlockPos a = ship.pos(STERN - ROOM, 0, -ROOM);
		BlockPos b = ship.pos(BOW + ROOM, 0, ROOM);
		for (int cx = Math.min(a.getX(), b.getX()) >> 4; cx <= Math.max(a.getX(), b.getX()) >> 4; cx++) {
			for (int cz = Math.min(a.getZ(), b.getZ()) >> 4; cz <= Math.max(a.getZ(), b.getZ()) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null || chunk.getAttachedOrElse(TerraLake.DUG, 0) < TerraLake.VERSION) {
					return;
				}
			}
		}
		boolean open = open(level, ship);
		boolean comes = open && TerraShaper.hash(domain.id(), 23, (int) level.getSeed()) < chance;
		if (comes) {
			build(level, ship.at(), ship.alongX());
		}
		List<Ship> next = new ArrayList<>(level.getAttachedOrElse(SHIPS, List.of()));
		next.add(new Ship(ship.domain(), ship.at(), ship.alongX(), comes));
		level.setAttached(SHIPS, List.copyOf(next));
	}

	/** Whether the water a ship would lie on is all open: deep enough under it, clear over it, and round it. */
	private static boolean open(final ServerLevel level, final Ship ship) {
		int water = ship.at().getY();
		for (int u = STERN - ROOM; u <= BOW + ROOM; u++) {
			for (int v = -ROOM; v <= ROOM; v++) {
				for (int y = water - 3; y <= water + 3; y++) {
					BlockState state = level.getBlockState(ship.pos(u, y, v));
					if (y <= water ? !state.is(Blocks.WATER) : !state.isAir()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	// ── The ship ──────────────────────────────────────────────────

	/** Builds a research ship with the middle of its waterline here, and puts its crew aboard. */
	public static void build(final ServerLevel level, final BlockPos at, final boolean alongX) {
		Ship ship = new Ship(0, at, alongX, true);
		int water = at.getY();
		int deck = water + 2;
		List<BlockPos> joined = new ArrayList<>();
		BlockState planks = MistinnBlocks.MIST_PINE_PLANKS.defaultBlockState();
		BlockState rim = MistinnBlocks.STRIPPED_MIST_PINE_LOG.defaultBlockState();
		// the hull painted the tillers' teal below a white deck and cabin, as their survey ships are
		BlockState paint = Blocks.DYED_TERRACOTTA.cyan().defaultBlockState();
		BlockState rail = MistinnBlocks.MIST_PINE_FENCE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		// the hull: its bottom, its walls round a hold, its deck, its rail (open at the gangways amidships)
		for (int u = STERN; u <= BOW; u++) {
			int beam = beam(u);
			for (int v = -beam; v <= beam; v++) {
				boolean edge = edge(u, v);
				set(level, ship.pos(u, water - 2, v), paint);
				for (int y = water - 1; y <= water + 1; y++) {
					set(level, ship.pos(u, y, v), edge ? paint : air);
				}
				// ladders up the side at the gangways, out of the water to the deck
				if (Math.abs(v) == 4 && (u == 0 || u == 1)) {
					Direction out = v > 0 ? ship.side() : ship.side().getOpposite();
					for (int y = water; y <= deck; y++) {
						set(level, ship.pos(u, y, v + Integer.signum(v)), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, out)
							.setValue(LadderBlock.WATERLOGGED, y <= water));
					}
				}
				set(level, ship.pos(u, deck, v), edge ? rim : planks);
				if (edge && !(Math.abs(v) == 4 && (u == 0 || u == 1))) {
					joined.add(set(level, ship.pos(u, deck + 1, v), rail));
				}
			}
		}
		cabin(level, ship, deck, joined);
		hold(level, ship, water, deck);
		rigging(level, ship, deck, joined);
		// rails and panes join up with what is round them
		for (BlockPos pos : joined) {
			level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), FLAGS);
		}
		crew(level, ship, deck);
	}

	/** The laboratory aft: plank walls on log corners, windows, a door toward the bow, a slab roof; a bench of instruments inside. */
	private static void cabin(final ServerLevel level, final Ship ship, final int deck, final List<BlockPos> joined) {
		BlockState planks = MistinnBlocks.MIST_PINE_PLANKS.defaultBlockState();
		BlockState corner = MistinnBlocks.STRIPPED_MIST_PINE_LOG.defaultBlockState();
		for (int u = -9; u <= -3; u++) {
			for (int v = -3; v <= 3; v++) {
				boolean endWall = u == -9 || u == -3;
				boolean sideWall = Math.abs(v) == 3;
				for (int y = deck + 1; y <= deck + 3; y++) {
					BlockState state;
					if (endWall && sideWall) {
						state = corner;
					} else if (endWall || sideWall) {
						boolean window = y == deck + 2 && (sideWall ? u == -8 || u == -6 || u == -4 : Math.abs(v) == 2);
						state = window ? Blocks.GLASS_PANE.defaultBlockState() : planks;
						if (window) {
							joined.add(ship.pos(u, y, v));
						}
					} else {
						state = Blocks.AIR.defaultBlockState();
					}
					set(level, ship.pos(u, y, v), state);
				}
			}
		}
		for (int u = -10; u <= -2; u++) {
			for (int v = -4; v <= 4; v++) {
				set(level, ship.pos(u, deck + 4, v), MistinnBlocks.MIST_PINE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
			}
		}
		// the door, toward the bow
		BlockState door = MistinnBlocks.MIST_PINE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, ship.bow().getOpposite())
			.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
		set(level, ship.pos(-3, deck + 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, ship.pos(-3, deck + 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		// the bench: a brewing stand and a crystal sample on it; the chest, the chart table, a lantern overhead
		for (int u = -8; u <= -5; u++) {
			set(level, ship.pos(u, deck + 1, -2), MistinnBlocks.MIST_PINE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
		}
		set(level, ship.pos(-7, deck + 2, -2), Blocks.BREWING_STAND.defaultBlockState());
		set(level, ship.pos(-5, deck + 2, -2), MistinnBlocks.TERRA_CRYSTAL_CLUSTER.defaultBlockState());
		chest(level, ship.pos(-8, deck + 1, 2), ship.side().getOpposite(), LAB_LOOT);
		set(level, ship.pos(-6, deck + 1, 2), Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		set(level, ship.pos(-4, deck + 1, 2), Blocks.CRAFTING_TABLE.defaultBlockState());
		set(level, ship.pos(-6, deck + 3, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
	}

	/** The hold: a hatch and a ladder down, the stores in chests and barrels, a lantern, and one piece of the tillers' better gear. */
	private static void hold(final ServerLevel level, final Ship ship, final int water, final int deck) {
		set(level, ship.pos(1, deck, -3), MistinnBlocks.MIST_PINE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
			.setValue(TrapDoorBlock.FACING, ship.side()));
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, ship.side());
		for (int y = water - 1; y <= water + 1; y++) {
			set(level, ship.pos(1, y, -3), ladder);
		}
		chest(level, ship.pos(-6, water - 1, 2), ship.side().getOpposite(), HOLD_LOOT);
		BlockPos stores = chest(level, ship.pos(5, water - 1, 2), ship.side().getOpposite(), HOLD_LOOT);
		set(level, ship.pos(5, water - 1, -2), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
		BlockPos barrel = set(level, ship.pos(6, water - 1, -2), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
		RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), barrel, HOLD_LOOT);
		set(level, ship.pos(-2, water - 1, 0), Blocks.LANTERN.defaultBlockState());
		if (level.getBlockEntity(stores) instanceof ChestBlockEntity box) {
			box.unpackLootTable(null);
			List<Item> gear = MistinnItems.gearOfTier(level.getRandom().nextFloat() < 0.35F ? 5 : 4);
			for (int slot = 0; slot < box.getContainerSize() && !gear.isEmpty(); slot++) {
				if (box.getItem(slot).isEmpty()) {
					box.setItem(slot, new ItemStack(gear.get(level.getRandom().nextInt(gear.size()))));
					break;
				}
			}
		}
	}

	/** The mast with its yard and masthead lantern, the sampling crane over the side with its chain, a lantern at the bow. */
	private static void rigging(final ServerLevel level, final Ship ship, final int deck, final List<BlockPos> joined) {
		BlockState mast = MistinnBlocks.MIST_PINE_LOG.defaultBlockState();
		for (int y = deck + 1; y <= deck + 8; y++) {
			set(level, ship.pos(3, y, 0), mast);
		}
		Direction.Axis across = ship.alongX() ? Direction.Axis.Z : Direction.Axis.X;
		for (int v = -3; v <= 3; v++) {
			if (v != 0) {
				set(level, ship.pos(3, deck + 7, v), MistinnBlocks.MIST_PINE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, across));
			}
		}
		set(level, ship.pos(3, deck + 9, 0), Blocks.LANTERN.defaultBlockState());
		BlockState rail = MistinnBlocks.MIST_PINE_FENCE.defaultBlockState();
		for (int y = deck + 1; y <= deck + 4; y++) {
			joined.add(set(level, ship.pos(7, y, 3), rail));
		}
		joined.add(set(level, ship.pos(7, deck + 4, 4), rail));
		joined.add(set(level, ship.pos(7, deck + 4, 5), rail));
		for (int y = deck + 3; y > ship.at().getY(); y--) {
			set(level, ship.pos(7, y, 5), Blocks.IRON_CHAIN.defaultBlockState());
		}
		set(level, ship.pos(BOW, deck + 2, 0), Blocks.LANTERN.defaultBlockState());
	}

	/** The crew: a researcher in the laboratory and one on deck, a warden at the bow and one by the hatch. */
	private static void crew(final ServerLevel level, final Ship ship, final int deck) {
		Tiller.Role[] roles = {Tiller.Role.RESEARCHER, Tiller.Role.RESEARCHER, Tiller.Role.SHIP_WARDEN, Tiller.Role.SHIP_WARDEN};
		int[][] spots = {{-6, 0}, {-1, 2}, {7, -1}, {0, -2}};
		BlockPos home = ship.pos(0, deck + 1, 0);
		for (int i = 0; i < roles.length; i++) {
			BlockPos at = ship.pos(spots[i][0], deck + 1, spots[i][1]);
			Tiller tiller = MistinnEntities.TILLER.create(level, EntitySpawnReason.STRUCTURE);
			if (tiller != null) {
				tiller.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
				tiller.settle(roles[i], home);
				level.addFreshEntity(tiller);
			}
		}
	}

	private static BlockPos set(final ServerLevel level, final BlockPos pos, final BlockState state) {
		level.setBlock(pos, state, FLAGS);
		return pos;
	}

	private static BlockPos chest(final ServerLevel level, final BlockPos pos, final Direction facing, final ResourceKey<LootTable> loot) {
		set(level, pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
		RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos, loot);
		return pos;
	}

	// ── The crew's watch ──────────────────────────────────────────

	/** Twice a second: whoever boards a ship is fought by its crew; whoever comes near is warned off first. */
	private static void watch(final MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		// now and then, a lake whose ship is not settled yet is looked at again (its middle may only now be all loaded)
		if (server.getTickCount() % 200 == 0 && !TerraLake.dormant) {
			for (Domain domain : life.shizune.mistinn.domain.DomainService.of(server, Domain.Kind.TERRA)) {
				TerraLake.Lake lake = TerraLake.placed(level, domain.id());
				if (lake != null && of(level, domain.id()) == null && level.getChunkSource().getChunkNow(lake.x() >> 4, lake.z() >> 4) != null) {
					consider(level, domain);
				}
			}
		}
		List<Ship> ships = level.getAttachedOrElse(SHIPS, List.of());
		if (ships.isEmpty() || level.players().isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Ship ship : ships) {
			if (!ship.present() || !level.isLoaded(ship.at())) {
				continue;
			}
			List<Tiller> crew = null;
			for (ServerPlayer player : level.players()) {
				if (player.isCreative() || player.isSpectator() || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(ship.at())) > WATCHED * WATCHED) {
					continue;
				}
				if (crew == null) {
					crew = level.getEntitiesOfClass(Tiller.class, new net.minecraft.world.phys.AABB(ship.at()).inflate(40.0), t -> t.role().ship && t.isAlive());
				}
				if (crew.isEmpty()) {
					break;
				}
				boolean fought = crew.stream().anyMatch(t -> t.holdsAgainst(player));
				if (ship.aboard(player)) {
					for (Tiller tiller : crew) {
						tiller.affront(player, now + FOUGHT);
					}
					if (!fought) {
						player.sendSystemMessage(Component.translatable("message.mistinn.ship.boarded").withStyle(ChatFormatting.RED));
					}
				} else if (!fought && Math.hypot(player.getX() - ship.at().getX(), player.getZ() - ship.at().getZ()) < WARN) {
					Long last = WARNED.get(player.getUUID());
					if (last == null || now - last > WARN_EVERY) {
						WARNED.put(player.getUUID(), now);
						player.sendSystemMessage(Component.translatable("message.mistinn.ship.warning").withStyle(ChatFormatting.GRAY));
					}
				}
			}
		}
	}

	/** For tests: whether a block entity of the ship's stands where it should (its laboratory chest). */
	public static @Nullable BlockEntity lab(final ServerLevel level, final BlockPos at, final boolean alongX) {
		return level.getBlockEntity(new Ship(0, at, alongX, true).pos(-8, at.getY() + 3, 2));
	}

	/** For tests: the stores chest in the hold, with the gear put in it. */
	public static @Nullable BlockEntity stores(final ServerLevel level, final BlockPos at, final boolean alongX) {
		return level.getBlockEntity(new Ship(0, at, alongX, true).pos(5, at.getY() - 1, 2));
	}

	/** For tests: a ship's place on the deck, {@code u} along and {@code v} across. */
	public static BlockPos deck(final BlockPos at, final boolean alongX, final int u, final int v) {
		return new Ship(0, at, alongX, true).pos(u, at.getY() + 3, v);
	}

	/** For tests: a ship standing here, to watch. */
	public static void watch(final ServerLevel level, final int domain, final BlockPos at, final boolean alongX) {
		List<Ship> next = new ArrayList<>(level.getAttachedOrElse(SHIPS, List.of()));
		next.add(new Ship(domain, at, alongX, true));
		level.setAttached(SHIPS, List.copyOf(next));
	}
}
