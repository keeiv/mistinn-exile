package life.shizune.mistinn.expedition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.ItemSizes;
import life.shizune.mistinn.tier.Tiers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * 搜索: what a container in an expedition's ground holds, laid out in a grid as in a hunter's pack, and how far it has
 * been searched. Each thing in it has its place and its footprint from the start, but what it is stays unknown until it
 * is searched: one at a time, in reading order, while someone has the container open. A thing takes longer the more
 * cells it takes and the finer it is ({@link #ticks}). What is searched stays searched; what is half searched keeps its
 * progress when the container is closed. Things can be taken out, never put in.
 */
public record Cache(int width, int height, List<Entry> entries, int progress, long last) {
	/** One thing in it: its top-left cell and footprint as it lies, the stack (empty once taken), and whether it has been searched. */
	public record Entry(int x, int y, int w, int h, ItemStack stack, boolean searched) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("x").forGetter(Entry::x),
			Codec.INT.fieldOf("y").forGetter(Entry::y),
			Codec.INT.fieldOf("w").forGetter(Entry::w),
			Codec.INT.fieldOf("h").forGetter(Entry::h),
			ItemStack.OPTIONAL_CODEC.fieldOf("stack").forGetter(Entry::stack),
			Codec.BOOL.fieldOf("searched").forGetter(Entry::searched)
		).apply(i, Entry::new));

		Entry holding(final ItemStack now) {
			return new Entry(this.x, this.y, this.w, this.h, now, this.searched);
		}

		Entry found() {
			return new Entry(this.x, this.y, this.w, this.h, this.stack, true);
		}
	}

	public static final Codec<Cache> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("width").forGetter(Cache::width),
		Codec.INT.fieldOf("height").forGetter(Cache::height),
		Entry.CODEC.listOf().fieldOf("entries").forGetter(Cache::entries),
		Codec.INT.optionalFieldOf("progress", 0).forGetter(Cache::progress),
		Codec.LONG.optionalFieldOf("last", 0L).forGetter(Cache::last)
	).apply(i, Cache::new));
	/** On the container's block entity: saved with its chunk. */
	public static final AttachmentType<Cache> CACHE = AttachmentRegistry.<Cache>builder()
		.persistent(CODEC)
		.buildAndRegister(Mistinn.id("cache"));

	/** How long one thing takes to search, in ticks: 8, and 6 for every cell it takes, and 10 for every tier above scrap. */
	public static int ticks(final ItemStack stack, final int cells) {
		return 8 + 6 * cells + 10 * Tiers.of(stack).ordinal();
	}

	/** The next thing to search, in reading order, or -1 when everything has been. */
	public int next() {
		for (int i = 0; i < this.entries.size(); i++) {
			Entry entry = this.entries.get(i);
			if (!entry.searched() && !entry.stack().isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	/** How long the thing being searched takes, in ticks (0 when nothing is left to search). */
	public int needed() {
		int next = this.next();
		if (next < 0) {
			return 0;
		}
		Entry entry = this.entries.get(next);
		return ticks(entry.stack(), entry.w() * entry.h());
	}

	/** A tick of searching at game time {@code now} (at most one a tick, however many have it open). */
	public Cache advanced(final long now) {
		int next = this.next();
		if (now == this.last || next < 0) {
			return this;
		}
		if (this.progress + 1 >= this.needed()) {
			List<Entry> after = new ArrayList<>(this.entries);
			after.set(next, after.get(next).found());
			return new Cache(this.width, this.height, List.copyOf(after), 0, now);
		}
		return new Cache(this.width, this.height, this.entries, this.progress + 1, now);
	}

	/** The same, with the {@code index}th thing now {@code stack} (part or all of it taken). */
	public Cache holding(final int index, final ItemStack stack) {
		List<Entry> after = new ArrayList<>(this.entries);
		after.set(index, after.get(index).holding(stack));
		return new Cache(this.width, this.height, List.copyOf(after), this.progress, this.last);
	}

	/**
	 * Lays these things out in a {@code width} by {@code height} grid: the biggest first, each at the first place it fits
	 * reading from the top left, upright or else on its side. What does not fit is left out.
	 */
	public static Cache pack(final int width, final int height, final List<ItemStack> stacks) {
		boolean[][] used = new boolean[height][width];
		List<ItemStack> order = new ArrayList<>(stacks.stream().filter(s -> !s.isEmpty()).toList());
		order.sort(Comparator.comparingInt((ItemStack s) -> -ItemSizes.of(s)[0] * ItemSizes.of(s)[1]));
		List<Entry> placed = new ArrayList<>();
		for (ItemStack stack : order) {
			int[] size = ItemSizes.of(stack);
			Entry entry = place(used, stack, size[0], size[1]);
			if (entry == null && size[0] != size[1]) {
				entry = place(used, stack, size[1], size[0]);
			}
			if (entry != null) {
				placed.add(entry);
			}
		}
		placed.sort(Comparator.comparingInt(Entry::y).thenComparingInt(Entry::x));
		return new Cache(width, height, List.copyOf(placed), 0, 0L);
	}

	private static Entry place(final boolean[][] used, final ItemStack stack, final int w, final int h) {
		for (int y = 0; y + h <= used.length; y++) {
			for (int x = 0; x + w <= used[0].length; x++) {
				if (free(used, x, y, w, h)) {
					for (int dy = 0; dy < h; dy++) {
						for (int dx = 0; dx < w; dx++) {
							used[y + dy][x + dx] = true;
						}
					}
					return new Entry(x, y, w, h, stack.copy(), false);
				}
			}
		}
		return null;
	}

	private static boolean free(final boolean[][] used, final int x, final int y, final int w, final int h) {
		for (int dy = 0; dy < h; dy++) {
			for (int dx = 0; dx < w; dx++) {
				if (used[y + dy][x + dx]) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Fills the container at {@code pos} afresh: rolls its loot table and lays what comes out in a grid of the given size.
	 * The container's own slots are emptied; from now on it opens as a search.
	 */
	public static void stock(final ServerLevel level, final BlockPos pos, final ResourceKey<LootTable> table, final int width, final int height,
		final RandomSource random) {
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity == null) {
			return;
		}
		if (entity instanceof Container container) {
			container.clearContent();
		}
		if (entity instanceof RandomizableContainer randomizable) {
			randomizable.setLootTable(null);
		}
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).create(LootContextParamSets.CHEST);
		List<ItemStack> stacks = level.getServer().reloadableRegistries().getLootTable(table).getRandomItems(params, random.nextLong());
		entity.setAttached(CACHE, pack(width, height, stacks));
		entity.setChanged();
	}
}
