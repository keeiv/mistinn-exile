package life.shizune.mistinn.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The world's domains: where they are, how far each has spread, and the work of changing the
 * land under them. A domain spreads out from its centre over time; the ground it reaches is
 * reshaped column by column and its biome changed, in chunks that are loaded, and in the others
 * when they load. What each chunk has had done is remembered, so a chunk loaded again is not
 * reshaped again (whatever the hunters have built there since is left alone).
 */
public final class DomainService {
	/** How the land under one kind of domain is changed. */
	public interface Shaper {
		/** The biome a cell this far from the centre takes in this domain's phase, or null to leave it as it is. */
		@Nullable Holder<Biome> biome(ServerLevel level, Domain domain, double distance);

		/** Changes one column the spread has just reached. */
		void column(ServerLevel level, LevelChunk chunk, Domain domain, int x, int z, double distance);
	}

	/** What has been done to one chunk for one domain: reshaped out to {@code reached}, its biome set for {@code phase}. */
	public record Done(int domain, long chunk, int reached, int phase) {
		public static final Codec<Done> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Done::domain),
			Codec.LONG.fieldOf("chunk").forGetter(Done::chunk),
			Codec.INT.fieldOf("reached").forGetter(Done::reached),
			Codec.INT.fieldOf("phase").forGetter(Done::phase)
		).apply(i, Done::new));
	}

	/** Every domain in the world, the next number to give, and what each chunk has had done. */
	public record Book(int next, List<Domain> domains, List<Done> done) {
		public static final Book EMPTY = new Book(1, List.of(), List.of());
		public static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("next").forGetter(Book::next),
			Domain.CODEC.listOf().fieldOf("domains").forGetter(Book::domains),
			Done.CODEC.listOf().fieldOf("done").forGetter(Book::done)
		).apply(i, Book::new));
	}

	public static final AttachmentType<Book> BOOK = AttachmentRegistry.<Book>builder()
		.persistent(Book.CODEC)
		.buildAndRegister(Mistinn.id("domains"));
	/** The domains as every client sees them: for the sky, the maps and the air. */
	public static final AttachmentType<List<Domain>> VIEW = AttachmentRegistry.<List<Domain>>builder()
		.syncWith(Domain.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("domain_view"));

	/** How ragged a domain's edge is: a column can fall this much short of the radius, or reach this much past it. */
	public static final int EDGE = 7;
	/** How far from a block entity (someone's chest, bed, furnace) the land is never reshaped. */
	private static final int KEEP_CLEAR = 6;
	/** How many waiting chunks are worked through each tick. */
	private static final int PER_TICK = 4;

	/** How many blocks the change spreads each half second; for tests, faster. */
	public static int pace = 1;

	private static final Map<Domain.Kind, Shaper> SHAPERS = new EnumMap<>(Domain.Kind.class);
	private static final LongLinkedOpenHashSet WAITING = new LongLinkedOpenHashSet();

	private DomainService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DomainService::tick);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() == Level.OVERWORLD && touches(level, chunk.getPos())) {
				WAITING.add(chunk.getPos().pack());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WAITING.clear());
	}

	public static void shaper(final Domain.Kind kind, final Shaper shaper) {
		SHAPERS.put(kind, shaper);
	}

	// ── The book ──────────────────────────────────────────────────

	public static Book book(final MinecraftServer server) {
		return server.overworld().getAttachedOrElse(BOOK, Book.EMPTY);
	}

	public static List<Domain> all(final MinecraftServer server) {
		return book(server).domains();
	}

	public static @Nullable Domain get(final MinecraftServer server, final int id) {
		for (Domain domain : all(server)) {
			if (domain.id() == id) {
				return domain;
			}
		}
		return null;
	}

	public static List<Domain> of(final MinecraftServer server, final Domain.Kind kind) {
		List<Domain> found = new ArrayList<>();
		for (Domain domain : all(server)) {
			if (domain.kind() == kind) {
				found.add(domain);
			}
		}
		return found;
	}

	/** The domain covering this column, if any. */
	public static @Nullable Domain at(final MinecraftServer server, final double x, final double z) {
		for (Domain domain : all(server)) {
			if (domain.covers(x, z, 0.0)) {
				return domain;
			}
		}
		return null;
	}

	/** Whether a new domain here would come within {@code gap} blocks of another's edge. */
	public static boolean crowded(final MinecraftServer server, final BlockPos centre, final int radius, final int gap) {
		for (Domain domain : all(server)) {
			if (domain.distance(centre.getX() + 0.5, centre.getZ() + 0.5) < domain.radius() + radius + gap) {
				return true;
			}
		}
		return false;
	}

	/** A new domain, spreading from nothing. */
	public static Domain add(final MinecraftServer server, final Domain.Kind kind, final BlockPos centre, final int radius, final Domain.Phase phase) {
		Book book = book(server);
		long now = server.overworld().getGameTime();
		Domain domain = new Domain(book.next(), kind, centre, radius, phase, now, 0);
		List<Domain> domains = new ArrayList<>(book.domains());
		domains.add(domain);
		write(server, new Book(book.next() + 1, domains, book.done()));
		return domain;
	}

	/** Puts a changed domain back in the book. */
	public static void update(final MinecraftServer server, final Domain changed) {
		Book book = book(server);
		List<Domain> domains = new ArrayList<>(book.domains());
		for (int i = 0; i < domains.size(); i++) {
			if (domains.get(i).id() == changed.id()) {
				domains.set(i, changed);
			}
		}
		write(server, new Book(book.next(), domains, book.done()));
	}

	/** A domain enters a new phase: written, and every loaded chunk under it looks again (the biome may change). */
	public static Domain enter(final MinecraftServer server, final Domain domain, final Domain.Phase phase) {
		Domain changed = domain.phase(phase, server.overworld().getGameTime());
		update(server, changed);
		queueLoaded(server.overworld(), changed);
		return changed;
	}

	private static void write(final MinecraftServer server, final Book book) {
		ServerLevel level = server.overworld();
		level.setAttached(BOOK, book);
		// clients do not need to follow the spread, so they hear only of real changes
		List<Domain> view = new ArrayList<>(book.domains().size());
		for (Domain domain : book.domains()) {
			view.add(domain.spread(0));
		}
		if (!view.equals(level.getAttached(VIEW))) {
			level.setAttached(VIEW, List.copyOf(view));
		}
	}

	// ── Spreading and shaping ─────────────────────────────────────

	/** How far a domain's change reaches at this column: short of or past the radius, as the ragged edge has it. */
	public static double reach(final Domain domain, final int x, final int z) {
		double seed = domain.id() * 1.7;
		double edge = 3.0 * Math.sin(x * 0.113 + seed) + 2.5 * Math.sin(z * 0.131 + seed * 2.0) + 1.5 * Math.sin((x + z) * 0.071 + seed * 3.0);
		return Math.min(domain.spread(), domain.radius() + edge);
	}

	/** Whether the change has reached this column. */
	public static boolean reached(final Domain domain, final int x, final int z) {
		return domain.distance(x + 0.5, z + 0.5) <= reach(domain, x, z);
	}

	private static void tick(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (server.getTickCount() % 10 == 0) {
			for (Domain domain : all(server)) {
				int full = domain.radius() + EDGE;
				if (domain.spread() < full) {
					// two blocks a second, out from the centre
					Domain grown = domain.spread(Math.min(full, domain.spread() + pace));
					update(server, grown);
					queueRing(level, grown);
				}
			}
		}
		for (int i = 0; i < PER_TICK && !WAITING.isEmpty(); i++) {
			long packed = WAITING.removeFirstLong();
			LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
			if (chunk != null) {
				work(level, chunk);
			}
		}
	}

	private static boolean touches(final ServerLevel level, final ChunkPos pos) {
		for (Domain domain : all(level.getServer())) {
			if (overlaps(domain, pos, domain.radius() + EDGE)) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlaps(final Domain domain, final ChunkPos pos, final double reach) {
		double cx = Math.max(pos.getMinBlockX(), Math.min(domain.centre().getX(), pos.getMaxBlockX()));
		double cz = Math.max(pos.getMinBlockZ(), Math.min(domain.centre().getZ(), pos.getMaxBlockZ()));
		return Math.hypot(cx - domain.centre().getX(), cz - domain.centre().getZ()) <= reach;
	}

	private static double farthest(final Domain domain, final ChunkPos pos) {
		double dx = Math.max(Math.abs(pos.getMinBlockX() - domain.centre().getX()), Math.abs(pos.getMaxBlockX() + 1 - domain.centre().getX()));
		double dz = Math.max(Math.abs(pos.getMinBlockZ() - domain.centre().getZ()), Math.abs(pos.getMaxBlockZ() + 1 - domain.centre().getZ()));
		return Math.hypot(dx, dz);
	}

	/** Every loaded chunk the spread's front is passing through. */
	private static void queueRing(final ServerLevel level, final Domain domain) {
		int reach = domain.spread();
		int min = (domain.centre().getX() - reach) >> 4;
		int max = (domain.centre().getX() + reach) >> 4;
		int minZ = (domain.centre().getZ() - reach) >> 4;
		int maxZ = (domain.centre().getZ() + reach) >> 4;
		for (int cx = min; cx <= max; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				ChunkPos pos = new ChunkPos(cx, cz);
				// only the chunks the front is passing through: something in them at the front's distance
				if (overlaps(domain, pos, domain.spread()) && farthest(domain, pos) >= domain.spread() - 2
					&& level.getChunkSource().getChunkNow(cx, cz) != null) {
					WAITING.add(pos.pack());
				}
			}
		}
	}

	private static void queueLoaded(final ServerLevel level, final Domain domain) {
		int reach = domain.radius() + EDGE;
		for (int cx = (domain.centre().getX() - reach) >> 4; cx <= (domain.centre().getX() + reach) >> 4; cx++) {
			for (int cz = (domain.centre().getZ() - reach) >> 4; cz <= (domain.centre().getZ() + reach) >> 4; cz++) {
				if (overlaps(domain, new ChunkPos(cx, cz), reach) && level.getChunkSource().getChunkNow(cx, cz) != null) {
					WAITING.add(ChunkPos.pack(cx, cz));
				}
			}
		}
	}

	/** Brings one chunk up to date with every domain over it. */
	private static void work(final ServerLevel level, final LevelChunk chunk) {
		MinecraftServer server = level.getServer();
		Book book = book(server);
		long packed = chunk.getPos().pack();
		Map<Integer, Done> done = new HashMap<>();
		for (Done d : book.done()) {
			if (d.chunk() == packed) {
				done.put(d.domain(), d);
			}
		}
		List<Done> changed = new ArrayList<>();
		boolean biomes = false;
		for (Domain domain : book.domains()) {
			Shaper shaper = SHAPERS.get(domain.kind());
			if (shaper == null || !overlaps(domain, chunk.getPos(), domain.radius() + EDGE)) {
				continue;
			}
			Done before = done.get(domain.id());
			int from = before == null ? -1 : before.reached();
			int phase = before == null ? -1 : before.phase();
			if (domain.spread() > from) {
				shape(level, chunk, domain, shaper, from);
			}
			if (domain.spread() > from || phase != domain.phase().ordinal()) {
				biomes |= biome(level, chunk, domain, shaper);
				changed.add(new Done(domain.id(), packed, domain.spread(), domain.phase().ordinal()));
			}
		}
		if (!changed.isEmpty()) {
			List<Done> all = new ArrayList<>(book.done().size() + changed.size());
			for (Done d : book.done()) {
				boolean replaced = false;
				for (Done c : changed) {
					replaced |= c.domain() == d.domain() && c.chunk() == d.chunk();
				}
				if (!replaced) {
					all.add(d);
				}
			}
			all.addAll(changed);
			level.setAttached(BOOK, new Book(book.next(), book.domains(), all));
		}
		if (biomes) {
			chunk.markUnsaved();
			level.getChunkSource().chunkMap.resendBiomesForChunks(List.<ChunkAccess>of(chunk));
		}
	}

	/** Reshapes the columns of this chunk the spread has reached since {@code from}, away from anyone's things. */
	private static void shape(final ServerLevel level, final LevelChunk chunk, final Domain domain, final Shaper shaper, final int from) {
		LongArrayList kept = new LongArrayList();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				LevelChunk near = level.getChunkSource().getChunkNow(chunk.getPos().x() + dx, chunk.getPos().z() + dz);
				if (near != null) {
					for (BlockEntity entity : near.getBlockEntities().values()) {
						kept.add(entity.getBlockPos().asLong());
					}
				}
			}
		}
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		for (int x = x0; x < x0 + 16; x++) {
			for (int z = z0; z < z0 + 16; z++) {
				double distance = domain.distance(x + 0.5, z + 0.5);
				boolean now = distance <= reach(domain, x, z);
				boolean before = from >= 0 && distance <= reach(domain.spread(from), x, z);
				if (!now || before || nearKept(kept, x, z)) {
					continue;
				}
				shaper.column(level, chunk, domain, x, z, distance);
			}
		}
	}

	private static boolean nearKept(final LongArrayList kept, final int x, final int z) {
		for (int i = 0; i < kept.size(); i++) {
			long at = kept.getLong(i);
			if (Math.abs(BlockPos.getX(at) - x) <= KEEP_CLEAR && Math.abs(BlockPos.getZ(at) - z) <= KEEP_CLEAR) {
				return true;
			}
		}
		return false;
	}

	/** Sets the biome of every cell the spread has reached; true if any cell changed. */
	private static boolean biome(final ServerLevel level, final LevelChunk chunk, final Domain domain, final Shaper shaper) {
		boolean[] changed = new boolean[1];
		chunk.fillBiomesFromNoise((qx, qy, qz) -> {
			Holder<Biome> existing = chunk.getNoiseBiome(qx, qy, qz);
			int x = (qx << 2) + 2;
			int z = (qz << 2) + 2;
			if (!reached(domain, x, z)) {
				return existing;
			}
			Holder<Biome> target = shaper.biome(level, domain, domain.distance(x, z));
			if (target == null || target.equals(existing)) {
				return existing;
			}
			changed[0] = true;
			return target;
		});
		return changed[0];
	}

	/** For tests: how many chunks are waiting to be worked through. */
	public static int waiting() {
		return WAITING.size();
	}
}
