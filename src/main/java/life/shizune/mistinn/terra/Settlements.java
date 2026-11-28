package life.shizune.mistinn.terra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.block.CrystalSproutBlock;
import life.shizune.mistinn.block.HubCoreBlock;
import life.shizune.mistinn.block.ConduitBlock;
import life.shizune.mistinn.block.HubSpireBlock;
import life.shizune.mistinn.block.RefineryBlock;
import life.shizune.mistinn.block.RelayPylonBlock;
import life.shizune.mistinn.block.RoofVentBlock;
import life.shizune.mistinn.block.TurretBlock;
import life.shizune.mistinn.block.WorkbenchBlock;
import life.shizune.mistinn.block.WorkshopWallBlock;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The crystal tillers' settlements: one in the terraced valley of every Terra highland. A round
 * deck plaza on a stone footing, the hub core at its middle ringed with lamp posts, three relay
 * pylons with conduits running in to the hub, a workshop with windows, a crystal field with its
 * water channel, cargo crates; a keeper of trade, a warden and two tillers. It is built whole,
 * once, when all the ground under it has loaded and taken the highland's shape, and never where
 * someone has already put things.
 * <p>
 * A settlement whose hub core has been given crystal cores is the hunters' homestead.
 */
public final class Settlements {
	/** How many crystal cores wake a hub. */
	public static final int CORES = 3;
	/** How many blocks tall a relay pylon stands, base and head included. */
	public static final int PYLON = 7;
	/** The deck's radius, and the ground cleared round it. */
	public static final int DECK = 11;
	public static final int CLEAR = 14;
	/** How far into the highland it stands, as a part of the radius. */
	public static final double OUT = 0.5;
	/** How far round it the settlement's people keep. */
	public static final int HOME = 12;
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	/** A settlement: its highland, its middle, the deck's height, its hub, and whether (and when) its hub was woken. */
	public record Settlement(int domain, BlockPos centre, int deck, BlockPos hub, boolean lit, long litAt) {
		public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Settlement::domain),
			BlockPos.CODEC.fieldOf("centre").forGetter(Settlement::centre),
			Codec.INT.fieldOf("deck").forGetter(Settlement::deck),
			BlockPos.CODEC.fieldOf("hub").forGetter(Settlement::hub),
			Codec.BOOL.fieldOf("lit").forGetter(Settlement::lit),
			Codec.LONG.fieldOf("lit_at").forGetter(Settlement::litAt)
		).apply(i, Settlement::new));
		public static final StreamCodec<ByteBuf, Settlement> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Settlement::domain,
			BlockPos.STREAM_CODEC, Settlement::centre,
			ByteBufCodecs.VAR_INT, Settlement::deck,
			BlockPos.STREAM_CODEC, Settlement::hub,
			ByteBufCodecs.BOOL, Settlement::lit,
			ByteBufCodecs.VAR_LONG, Settlement::litAt,
			Settlement::new);

		public Settlement lit(final long when) {
			return new Settlement(this.domain, this.centre, this.deck, this.hub, true, when);
		}
	}

	/** Every settlement built, and the highlands where one could not be. */
	public record Ledger(List<Settlement> built, List<Integer> skipped) {
		public static final Ledger EMPTY = new Ledger(List.of(), List.of());
		public static final Codec<Ledger> CODEC = RecordCodecBuilder.create(i -> i.group(
			Settlement.CODEC.listOf().fieldOf("built").forGetter(Ledger::built),
			Codec.INT.listOf().fieldOf("skipped").forGetter(Ledger::skipped)
		).apply(i, Ledger::new));
	}

	public static final AttachmentType<Ledger> LEDGER = AttachmentRegistry.<Ledger>builder()
		.persistent(Ledger.CODEC)
		.buildAndRegister(Mistinn.id("settlements"));
	/** The settlements as every client sees them: for the maps. */
	public static final AttachmentType<List<Settlement>> VIEW = AttachmentRegistry.<List<Settlement>>builder()
		.syncWith(Settlement.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("settlement_view"));

	private Settlements() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 == 0) {
				tick(server);
			}
		});
		// the maps' view is not kept with the world: it is drawn again from the ledger each time the world opens
		ServerLifecycleEvents.SERVER_STARTED.register(server -> show(server, ledger(server)));
	}

	public static Ledger ledger(final MinecraftServer server) {
		return server.overworld().getAttachedOrElse(LEDGER, Ledger.EMPTY);
	}

	private static void write(final MinecraftServer server, final Ledger ledger) {
		server.overworld().setAttached(LEDGER, ledger);
		show(server, ledger);
	}

	/** Tells every client of the settlements as the ledger has them, if they have not heard it already. */
	private static void show(final MinecraftServer server, final Ledger ledger) {
		List<Settlement> view = server.overworld().getAttached(VIEW);
		if (view == null ? !ledger.built().isEmpty() : !view.equals(ledger.built())) {
			server.overworld().setAttached(VIEW, List.copyOf(ledger.built()));
		}
	}

	public static @Nullable Settlement of(final MinecraftServer server, final int domain) {
		for (Settlement settlement : ledger(server).built()) {
			if (settlement.domain() == domain) {
				return settlement;
			}
		}
		return null;
	}

	/** The settlements whose hubs are awake: the hunters' homesteads. */
	public static List<Settlement> homesteads(final MinecraftServer server) {
		return ledger(server).built().stream().filter(Settlement::lit).toList();
	}

	/** Where a highland's settlement stands: halfway out, in a direction of its own. */
	public static BlockPos site(final Domain domain) {
		double angle = TerraShaper.hash(domain.id(), 7, 913) * Math.PI * 2.0;
		double out = domain.radius() * OUT;
		return new BlockPos(domain.centre().getX() + (int) Math.round(Math.cos(angle) * out), 0, domain.centre().getZ() + (int) Math.round(Math.sin(angle) * out));
	}

	private static void tick(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		Ledger ledger = ledger(server);
		for (Domain domain : DomainService.of(server, Domain.Kind.TERRA)) {
			if (ledger.skipped().contains(domain.id()) || of(server, domain.id()) != null) {
				continue;
			}
			BlockPos site = site(domain);
			if (!ready(level, domain, site)) {
				continue;
			}
			Settlement built = build(level, domain, site);
			List<Settlement> all = new ArrayList<>(ledger.built());
			List<Integer> skipped = new ArrayList<>(ledger.skipped());
			if (built == null) {
				skipped.add(domain.id());
			} else {
				all.add(built);
			}
			ledger = new Ledger(all, skipped);
			write(server, ledger);
		}
	}

	/** Whether every chunk under the settlement is loaded and has taken the highland's shape. */
	private static boolean ready(final ServerLevel level, final Domain domain, final BlockPos site) {
		int reach = CLEAR + 1;
		for (int cx = (site.getX() - reach) >> 4; cx <= (site.getX() + reach) >> 4; cx++) {
			for (int cz = (site.getZ() - reach) >> 4; cz <= (site.getZ() + reach) >> 4; cz++) {
				if (level.getChunkSource().getChunkNow(cx, cz) == null || !DomainService.worked(level.getServer(), domain.id(), new ChunkPos(cx, cz))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Builds one settlement, or nothing (and says so) if someone has already put things where it would stand. */
	static @Nullable Settlement build(final ServerLevel level, final Domain domain, final BlockPos site) {
		int deck = deckHeight(level, site);
		int reach = CLEAR + 1;
		for (int cx = (site.getX() - reach) >> 4; cx <= (site.getX() + reach) >> 4; cx++) {
			for (int cz = (site.getZ() - reach) >> 4; cz <= (site.getZ() + reach) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk != null && chunk.getBlockEntities().keySet().stream().anyMatch(pos -> inTheWay(pos, site, deck))) {
					return null;
				}
			}
		}
		double turn = TerraShaper.hash(domain.id(), 11, 917) * Math.PI * 2.0;
		footing(level, site, deck);
		hub(level, site, deck);
		for (int i = 0; i < 3; i++) {
			pylon(level, site, deck, turn + i * Math.PI * 2.0 / 3.0);
		}
		workshop(level, along(site, turn + Math.PI / 3.0, 7), deck, site);
		field(level, site, deck, turn + Math.PI);
		level.setBlock(along(site, turn + Math.PI * 5.0 / 3.0, 7).atY(deck + 1), MistinnBlocks.REFINERY.defaultBlockState(), FLAGS);
		crates(level, along(site, turn + Math.PI * 5.0 / 3.0, 10), deck);
		people(level, site, deck);
		return new Settlement(domain.id(), site.atY(deck), deck, site.atY(deck + 1), false, 0L);
	}

	/** How far under the deck its footing can reach, and how far over it the clearing goes. */
	static final int FOOTING = 10;
	static final int CLEARED = 14;

	/**
	 * Whether something made by someone (a block entity) at {@code at} stands where a settlement with its deck at
	 * {@code deck} would be built: within its round, between the bottom of its footing and the top of its clearing.
	 * A trial chamber or a mineshaft deep under the highland is not in the way; a chest on the ground is.
	 */
	public static boolean inTheWay(final BlockPos at, final BlockPos site, final int deck) {
		return Math.hypot(at.getX() - site.getX(), at.getZ() - site.getZ()) <= CLEAR + 1 && at.getY() >= deck - FOOTING && at.getY() <= deck + CLEARED;
	}

	/** The deck stands at the middle height of the ground under it, so it is cut in on one side and built up on the other. */
	private static int deckHeight(final ServerLevel level, final BlockPos site) {
		List<Integer> heights = new ArrayList<>();
		for (int dx = -DECK; dx <= DECK; dx += 3) {
			for (int dz = -DECK; dz <= DECK; dz += 3) {
				if (dx * dx + dz * dz <= DECK * DECK) {
					heights.add(level.getHeight(Heightmap.Types.OCEAN_FLOOR, site.getX() + dx, site.getZ() + dz) - 1);
				}
			}
		}
		int[] sorted = heights.stream().mapToInt(Integer::intValue).sorted().toArray();
		return sorted[sorted.length / 2];
	}

	/** Clears the ground over the deck, builds the stone footing under it, and lays the plates. */
	private static void footing(final ServerLevel level, final BlockPos site, final int deck) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -CLEAR; dx <= CLEAR; dx++) {
			for (int dz = -CLEAR; dz <= CLEAR; dz++) {
				double off = Math.hypot(dx, dz);
				if (off > CLEAR) {
					continue;
				}
				int x = site.getX() + dx;
				int z = site.getZ() + dz;
				// over the deck everything goes; round it, the trees, crystal and growth, so nothing hangs over the plaza
				for (int y = deck + 1; y <= deck + CLEARED; y++) {
					BlockState state = level.getBlockState(at.set(x, y, z));
					if (!state.isAir() && (off <= DECK + 1 || clearable(state))) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
					}
				}
				if (off > DECK) {
					continue;
				}
				level.setBlock(at.set(x, deck, z), (off > DECK - 0.8 ? MistinnBlocks.FACILITY_DECK_EDGE : MistinnBlocks.FACILITY_DECK).defaultBlockState(), FLAGS);
				for (int y = deck - 1, depth = 0; depth < FOOTING; y--, depth++) {
					BlockState below = level.getBlockState(at.set(x, y, z));
					if (!below.isAir() && below.getFluidState().isEmpty() && !below.canBeReplaced()) {
						break;
					}
					level.setBlock(at, MistinnBlocks.VEIN_STONE_BRICKS.defaultBlockState(), FLAGS);
				}
			}
		}
	}

	/** What the clearing round the deck takes away: what grew there, never what was built. */
	private static boolean clearable(final BlockState state) {
		return state.canBeReplaced() || state.is(MistinnBlocks.MIST_PINE_LOG)
			|| state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock && !state.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
			|| state.is(MistinnBlocks.LATTICE_CRYSTAL) || state.is(MistinnBlocks.TERRA_CRYSTAL_BLOCK) || state.is(MistinnBlocks.TERRA_CRYSTAL_CLUSTER)
			|| state.is(MistinnBlocks.TERRA_CRYSTAL_LARGE_BUD) || state.is(MistinnBlocks.TERRA_CRYSTAL_MEDIUM_BUD) || state.is(MistinnBlocks.TERRA_CRYSTAL_SMALL_BUD)
			|| state.is(MistinnBlocks.MIST_REED) || state.is(MistinnBlocks.CRYSTAL_SPROUT);
	}

	/** The hub core on a hazard-striped plinth, ringed with four lamp posts. */
	private static void hub(final ServerLevel level, final BlockPos site, final int deck) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlock(site.offset(dx, 0, dz).atY(deck), MistinnBlocks.HAZARD_STRIPE.defaultBlockState(), FLAGS);
			}
		}
		level.setBlock(site.atY(deck + 1), MistinnBlocks.HUB_CORE.defaultBlockState(), FLAGS);
		level.setBlock(site.atY(deck + 2), MistinnBlocks.HUB_SPIRE.defaultBlockState(), FLAGS);
	}

	/** A relay pylon out on the deck, and a conduit running from it in towards the hub. */
	private static void pylon(final ServerLevel level, final BlockPos site, final int deck, final double angle) {
		BlockPos foot = along(site, angle, 8);
		// a base on its four struts, the lattice mast, and the head with its crossarms and emitter
		for (int y = 1; y <= PYLON; y++) {
			RelayPylonBlock.Part part = y == 1 ? RelayPylonBlock.Part.BASE : y == PYLON ? RelayPylonBlock.Part.HEAD : RelayPylonBlock.Part.MAST;
			level.setBlock(foot.atY(deck + y), MistinnBlocks.RELAY_PYLON.defaultBlockState().setValue(RelayPylonBlock.PART, part), FLAGS);
		}
		for (int r = 7; r >= 3; r--) {
			BlockPos cell = along(site, angle, r).atY(deck + 1);
			BlockPos next = along(site, angle, r - 1);
			Direction.Axis axis = Math.abs(next.getX() - cell.getX()) >= Math.abs(next.getZ() - cell.getZ()) ? Direction.Axis.X : Direction.Axis.Z;
			if (level.getBlockState(cell).isAir()) {
				level.setBlock(cell, MistinnBlocks.FACILITY_CONDUIT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis), FLAGS);
			}
		}
	}

	/** A small workshop: panelled walls on corner posts, a window in each wall, the doorway facing the hub under a canopy, a flat roof. */
	private static void workshop(final ServerLevel level, final BlockPos middle, final int deck, final BlockPos site) {
		Direction door = Direction.getApproximateNearest(site.getX() - middle.getX(), 0, site.getZ() - middle.getZ());
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				for (int y = 1; y <= 3; y++) {
					BlockPos at = middle.offset(dx, 0, dz).atY(deck + y);
					BlockState state = Blocks.AIR.defaultBlockState();
					if (wall) {
						boolean doorway = dx == door.getStepX() * 2 && dz == door.getStepZ() * 2;
						boolean window = y == 2 && (Math.abs(dx) == 2 && dz == 0 || Math.abs(dz) == 2 && dx == 0);
						boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
						// each piece shows its detail on the side it faces: outward
						Direction face = Math.abs(dz) == 2 ? (dz > 0 ? Direction.SOUTH : Direction.NORTH) : (dx > 0 ? Direction.EAST : Direction.WEST);
						WorkshopWallBlock.Part part = corner ? WorkshopWallBlock.Part.CORNER : doorway ? WorkshopWallBlock.Part.LINTEL
							: window ? WorkshopWallBlock.Part.WINDOW : WorkshopWallBlock.Part.WALL;
						if (doorway && y <= 2) {
							state = Blocks.AIR.defaultBlockState();
						} else {
							state = MistinnBlocks.WORKSHOP_WALL.defaultBlockState().setValue(WorkshopWallBlock.FACING, doorway ? door : face)
								.setValue(WorkshopWallBlock.PART, part);
						}
					}
					level.setBlock(at, state, FLAGS);
				}
				// a flat roof of deck plates, edged, with the fan unit standing on it
				boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				level.setBlock(middle.offset(dx, 0, dz).atY(deck + 4), (rim ? MistinnBlocks.FACILITY_DECK_EDGE : MistinnBlocks.FACILITY_DECK).defaultBlockState(), FLAGS);
			}
		}
		// the bench at the back, facing the doorway
		level.setBlock(middle.offset(-door.getStepX(), 0, -door.getStepZ()).atY(deck + 1),
			MistinnBlocks.WORKBENCH.defaultBlockState().setValue(WorkbenchBlock.FACING, door), FLAGS);
		level.setBlock(middle.atY(deck + 3), MistinnBlocks.FACILITY_LAMP.defaultBlockState(), FLAGS);
		level.setBlock(middle.offset(-door.getStepX(), 0, -door.getStepZ()).atY(deck + 5), MistinnBlocks.ROOF_VENT.defaultBlockState(), FLAGS);
		// hazard plates on the deck before the doorway
		BlockPos step = middle.offset(door.getStepX() * 3, 0, door.getStepZ() * 3);
		Direction side = door.getClockWise();
		for (int s = -1; s <= 1; s++) {
			level.setBlock(step.offset(side.getStepX() * s, 0, side.getStepZ() * s).atY(deck), MistinnBlocks.HAZARD_STRIPE.defaultBlockState(), FLAGS);
		}
	}

	/** The crystal field: three rows of farmland round a water channel, planted with sprouts, edged in stone. */
	private static void field(final ServerLevel level, final BlockPos site, final int deck, final double angle) {
		BlockPos middle = along(site, angle, 7);
		boolean alongX = Math.abs(Math.cos(angle)) < Math.abs(Math.sin(angle));
		for (int a = -3; a <= 3; a++) {
			for (int b = -2; b <= 2; b++) {
				BlockPos at = (alongX ? middle.offset(a, 0, b) : middle.offset(b, 0, a)).atY(deck);
				if (Math.abs(b) == 2 || Math.abs(a) == 3) {
					level.setBlock(at, MistinnBlocks.VEIN_STONE_BRICKS.defaultBlockState(), FLAGS);
				} else if (b == 0) {
					level.setBlock(at, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
				} else {
					level.setBlock(at, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), FLAGS);
					level.setBlock(at.above(), MistinnBlocks.CRYSTAL_SPROUT.defaultBlockState()
						.setValue(CrystalSproutBlock.AGE, Math.floorMod(a * 3 + b, CrystalSproutBlock.MAX + 1)), FLAGS);
				}
			}
		}
	}

	private static void crates(final ServerLevel level, final BlockPos at, final int deck) {
		level.setBlock(at.atY(deck + 1), MistinnBlocks.FACILITY_CRATE.defaultBlockState(), FLAGS);
		level.setBlock(at.east().atY(deck + 1), MistinnBlocks.FACILITY_CRATE.defaultBlockState(), FLAGS);
		level.setBlock(at.atY(deck + 2), MistinnBlocks.FACILITY_CRATE.defaultBlockState(), FLAGS);
	}

	/** The settlement's people: the keeper of trade by the hub, the warden, and two tillers by the field. */
	private static void people(final ServerLevel level, final BlockPos site, final int deck) {
		Tiller.Role[] roles = {Tiller.Role.ELDER, Tiller.Role.WARDEN, Tiller.Role.TILLER_1, Tiller.Role.TILLER_2};
		int[][] spots = {{2, 0}, {0, -4}, {-4, 2}, {3, 4}};
		for (int i = 0; i < roles.length; i++) {
			BlockPos at = site.offset(spots[i][0], 0, spots[i][1]).atY(deck + 1);
			Tiller tiller = MistinnEntities.TILLER.create(level, EntitySpawnReason.STRUCTURE);
			if (tiller != null) {
				tiller.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
				tiller.settle(roles[i], site.atY(deck + 1));
				level.addFreshEntity(tiller);
			}
		}
	}

	static BlockPos along(final BlockPos from, final double angle, final double distance) {
		return new BlockPos(from.getX() + (int) Math.round(Math.cos(angle) * distance), from.getY(), from.getZ() + (int) Math.round(Math.sin(angle) * distance));
	}

	// ── The homestead ─────────────────────────────────────────────

	/** A hunter wakes a settlement's hub: it becomes the hunters' homestead. False if this hub is no settlement's or is already awake. */
	public static boolean light(final ServerLevel level, final BlockPos hub, final ServerPlayer hunter) {
		MinecraftServer server = level.getServer();
		Ledger ledger = ledger(server);
		List<Settlement> all = new ArrayList<>(ledger.built());
		for (int i = 0; i < all.size(); i++) {
			Settlement settlement = all.get(i);
			if (!settlement.hub().equals(hub) || settlement.lit()) {
				continue;
			}
			all.set(i, settlement.lit(level.getGameTime()));
			write(server, new Ledger(all, ledger.skipped()));
			level.setBlock(hub, level.getBlockState(hub).setValue(HubCoreBlock.LIT, true), Block.UPDATE_ALL);
			wake(level, hub);
			level.sendParticles(ParticleTypes.END_ROD, hub.getX() + 0.5, hub.getY() + 1.0, hub.getZ() + 0.5, 60, 0.6, 1.2, 0.6, 0.06);
			level.playSound(null, hub, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 1.2F);
			level.playSound(null, hub, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 0.8F);
			for (ServerPlayer player : level.players()) {
				player.sendSystemMessage(Component.translatable("message.mistinn.hub.lit", hunter.getDisplayName()).withStyle(ChatFormatting.AQUA));
			}
			AchievementService.grant(hunter, AchievementService.HOMESTEAD);
			return true;
		}
		return false;
	}

	/** The hub woken: its spire, the relay pylons, their cables, the refinery and the workshop's fan light too. */
	private static void wake(final ServerLevel level, final BlockPos hub) {
		for (BlockPos at : BlockPos.betweenClosed(hub.offset(-DECK, 0, -DECK), hub.offset(DECK, PYLON + 1, DECK))) {
			BlockState state = level.getBlockState(at);
			if (state.is(MistinnBlocks.FACILITY_CONDUIT) && !state.getValue(ConduitBlock.LIT)) {
				level.setBlock(at, state.setValue(ConduitBlock.LIT, true), Block.UPDATE_ALL);
			} else if (state.is(MistinnBlocks.TURRET) && !state.getValue(TurretBlock.LIT)) {
				level.setBlock(at, state.setValue(TurretBlock.LIT, true), Block.UPDATE_ALL);
			} else if (state.is(MistinnBlocks.WORKBENCH) && !state.getValue(WorkbenchBlock.LIT)) {
				level.setBlock(at, state.setValue(WorkbenchBlock.LIT, true), Block.UPDATE_ALL);
			} else if (state.is(MistinnBlocks.WORKSHOP_WALL) && !state.getValue(WorkshopWallBlock.LIT)) {
				level.setBlock(at, state.setValue(WorkshopWallBlock.LIT, true), Block.UPDATE_ALL);
			} else if (state.is(MistinnBlocks.ROOF_VENT) && !state.getValue(RoofVentBlock.LIT)) {
				level.setBlock(at, state.setValue(RoofVentBlock.LIT, true), Block.UPDATE_ALL);
			} else if (state.is(MistinnBlocks.REFINERY) && !state.getValue(RefineryBlock.LIT)) {
				level.setBlock(at, state.setValue(RefineryBlock.LIT, true), Block.UPDATE_ALL);
			} else if (state.is(MistinnBlocks.HUB_SPIRE) && !state.getValue(HubSpireBlock.LIT)) {
				level.setBlock(at, state.setValue(HubSpireBlock.LIT, true), Block.UPDATE_ALL);
			} else if (state.is(MistinnBlocks.RELAY_PYLON) && !state.getValue(RelayPylonBlock.LIT)) {
				level.setBlock(at, state.setValue(RelayPylonBlock.LIT, true), Block.UPDATE_ALL);
			}
		}
	}

	/** For tests: the settlement under a highland built now, wherever the ground stands. */
	public static @Nullable Settlement buildNow(final ServerLevel level, final Domain domain) {
		Settlement built = build(level, domain, site(domain));
		if (built != null) {
			Ledger ledger = ledger(level.getServer());
			List<Settlement> all = new ArrayList<>(ledger.built());
			all.add(built);
			write(level.getServer(), new Ledger(all, ledger.skipped()));
		}
		return built;
	}
}
