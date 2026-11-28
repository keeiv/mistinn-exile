package life.shizune.mistinn.expedition;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.tier.Tier;
import life.shizune.mistinn.tier.Tiers;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Searching one of a ground's containers: its grid on the left, the hunter's own beside it (drawn by the grid panel),
 * the hotbar below. A thing not yet searched is an empty slot as far as anything that reads slots can tell (taking,
 * dragging, shift-clicking, number keys, gathering by double click), so nothing of it reaches the hunter, not even its
 * name; its footprint comes with the opening, and whether it has been searched with the menu's data. Nothing can be put
 * into it.
 */
public final class SearchMenu extends AbstractContainerMenu {
	/** Pixels a cell, on screen. */
	public static final int CELL = 16;
	/** Where the grid starts, inside the screen. */
	public static final int LEFT = 8;
	public static final int TOP = 18;
	/** The data: three twelve-bit masks of what has been searched (the menu's data travels as shorts), the progress, and what is needed. */
	private static final int MASKS = 3;
	private static final int BITS = 12;
	private static final int DATA = MASKS + 2;
	/** The most things a cache can hold. */
	public static final int MOST = MASKS * BITS;

	/** One thing's place and footprint, as the hunter is shown it. */
	public record Footprint(int x, int y, int w, int h) {
		public static final StreamCodec<ByteBuf, Footprint> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Footprint::x,
			ByteBufCodecs.VAR_INT, Footprint::y,
			ByteBufCodecs.VAR_INT, Footprint::w,
			ByteBufCodecs.VAR_INT, Footprint::h,
			Footprint::new);
	}

	/** What the hunter's screen is told on opening: the grid's size and every thing's footprint. */
	public record View(int width, int height, List<Footprint> footprints) {
		public static final StreamCodec<ByteBuf, View> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, View::width,
			ByteBufCodecs.VAR_INT, View::height,
			Footprint.STREAM_CODEC.apply(ByteBufCodecs.list(MOST)), View::footprints,
			View::new);

		static View of(final Cache cache) {
			List<Footprint> footprints = new ArrayList<>();
			for (Cache.Entry entry : cache.entries()) {
				footprints.add(new Footprint(entry.x(), entry.y(), entry.w(), entry.h()));
			}
			return new View(cache.width(), cache.height(), List.copyOf(footprints));
		}
	}

	public static final ExtendedMenuType<SearchMenu, View> TYPE = Registry.register(BuiltInRegistries.MENU, Mistinn.id("search"),
		new ExtendedMenuType<>(SearchMenu::new, View.STREAM_CODEC));

	private final View view;
	private final ContainerData data;
	private final @Nullable BlockEntity source;

	/** On the hunter's side. */
	public SearchMenu(final int id, final Inventory inventory, final View view) {
		this(id, inventory, view, new SimpleContainer(Math.max(1, view.footprints().size())), new SimpleContainerData(DATA), null);
	}

	private SearchMenu(final int id, final Inventory inventory, final View view, final Container items, final ContainerData data,
		final @Nullable BlockEntity source) {
		super(TYPE, id);
		this.view = view;
		this.data = data;
		this.source = source;
		for (int i = 0; i < view.footprints().size(); i++) {
			Footprint at = view.footprints().get(i);
			this.addSlot(new CacheSlot(items, i, LEFT + at.x() * CELL, TOP + at.y() * CELL, source == null));
		}
		int below = TOP + view.height() * CELL + 14;
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			this.addSlot(new Slot(inventory, i, LEFT + i * 18, below));
		}
		this.addDataSlots(data);
	}

	/** The grid and footprints. */
	public View view() {
		return this.view;
	}

	/** How many things the cache holds. */
	public int things() {
		return this.view.footprints().size();
	}

	/** Whether the {@code index}th thing has been searched. */
	public boolean searched(final int index) {
		return (this.data.get(index / BITS) >> (index % BITS) & 1) != 0;
	}

	/** Ticks into the thing being searched, and how many it needs. */
	public int progress() {
		return this.data.get(MASKS);
	}

	public int needed() {
		return this.data.get(MASKS + 1);
	}

	/** The thing being searched now, or -1: the first not searched that is still there. */
	public int searching() {
		for (int i = 0; i < this.things(); i++) {
			if (!this.searched(i)) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void broadcastChanges() {
		if (this.source != null && this.source.getLevel() instanceof ServerLevel level) {
			Cache before = this.source.getAttached(Cache.CACHE);
			if (before != null) {
				Cache after = before.advanced(level.getGameTime());
				if (after != before) {
					this.source.setAttached(Cache.CACHE, after);
					this.source.setChanged();
					int found = before.next();
					if (after.next() != found && found >= 0) {
						found(level, before.entries().get(found).stack());
					}
				}
			}
		}
		super.broadcastChanges();
	}

	/** A thing turns up: a soft sound, a brighter one for a finer thing. */
	private void found(final ServerLevel level, final ItemStack stack) {
		Tier tier = Tiers.of(stack);
		var at = this.source.getBlockPos();
		level.playSound(null, at, SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		if (tier.ordinal() >= Tier.IV.ordinal()) {
			level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.8F + 0.1F * tier.ordinal());
		}
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.source == null || Container.stillValidBlockEntity(this.source, player);
	}

	@Override
	public ItemStack quickMoveStack(final Player player, final int index) {
		// out of the cache, into the hunter's hotbar or grid; nothing goes the other way
		if (index < 0 || index >= this.things() || !(player instanceof ServerPlayer hunter)) {
			return ItemStack.EMPTY;
		}
		Slot slot = this.slots.get(index);
		if (!slot.hasItem() || !slot.mayPickup(player)) {
			return ItemStack.EMPTY;
		}
		ItemStack moving = slot.getItem().copy();
		GridService.store(hunter, moving);
		slot.set(moving);
		return ItemStack.EMPTY;
	}

	/** A slot of the cache: empty until searched, never filled from outside. */
	public static final class CacheSlot extends Slot {
		private final boolean drawn;

		CacheSlot(final Container container, final int index, final int x, final int y, final boolean drawn) {
			super(container, index, x, y);
			this.drawn = drawn;
		}

		@Override
		public boolean mayPlace(final ItemStack stack) {
			return false;
		}

		@Override
		public boolean isActive() {
			// the search screen draws and handles these itself, by their footprints
			return !this.drawn;
		}
	}

	// ── the server's side ────────────────────────────────────────

	/** The cache, as slots: what is not yet searched reads as nothing. */
	private static final class CacheItems implements Container {
		private final BlockEntity source;

		CacheItems(final BlockEntity source) {
			this.source = source;
		}

		private Cache cache() {
			Cache cache = this.source.getAttached(Cache.CACHE);
			return cache == null ? new Cache(1, 1, List.of(), 0, 0L) : cache;
		}

		@Override
		public int getContainerSize() {
			return this.cache().entries().size();
		}

		@Override
		public boolean isEmpty() {
			return this.cache().entries().stream().allMatch(e -> !e.searched() || e.stack().isEmpty());
		}

		@Override
		public ItemStack getItem(final int slot) {
			List<Cache.Entry> entries = this.cache().entries();
			if (slot < 0 || slot >= entries.size() || !entries.get(slot).searched()) {
				return ItemStack.EMPTY;
			}
			return entries.get(slot).stack();
		}

		@Override
		public ItemStack removeItem(final int slot, final int count) {
			ItemStack held = this.getItem(slot);
			if (held.isEmpty() || count <= 0) {
				return ItemStack.EMPTY;
			}
			ItemStack left = held.copy();
			ItemStack taken = left.split(count);
			this.put(slot, left);
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(final int slot) {
			ItemStack held = this.getItem(slot);
			if (!held.isEmpty()) {
				this.put(slot, ItemStack.EMPTY);
			}
			return held;
		}

		@Override
		public void setItem(final int slot, final ItemStack stack) {
			List<Cache.Entry> entries = this.cache().entries();
			if (slot >= 0 && slot < entries.size() && entries.get(slot).searched()) {
				this.put(slot, stack);
			}
		}

		private void put(final int slot, final ItemStack stack) {
			this.source.setAttached(Cache.CACHE, this.cache().holding(slot, stack.copy()));
			this.source.setChanged();
		}

		@Override
		public void setChanged() {
			// a stack changed where it lies: keep the copy the cache holds the same
			Cache cache = this.cache();
			this.source.setAttached(Cache.CACHE, new Cache(cache.width(), cache.height(), List.copyOf(cache.entries()), cache.progress(), cache.last()));
			this.source.setChanged();
		}

		@Override
		public boolean stillValid(final Player player) {
			return Container.stillValidBlockEntity(this.source, player);
		}

		@Override
		public void clearContent() {
		}
	}

	/** What has been searched, and how far the search has got. */
	private static final class CacheData implements ContainerData {
		private final BlockEntity source;

		CacheData(final BlockEntity source) {
			this.source = source;
		}

		@Override
		public int get(final int index) {
			Cache cache = this.source.getAttached(Cache.CACHE);
			if (cache == null) {
				return 0;
			}
			if (index < MASKS) {
				int mask = 0;
				for (int bit = 0; bit < BITS; bit++) {
					int entry = index * BITS + bit;
					if (entry < cache.entries().size() && cache.entries().get(entry).searched()) {
						mask |= 1 << bit;
					}
				}
				return mask;
			}
			return index == MASKS ? cache.progress() : cache.needed();
		}

		@Override
		public void set(final int index, final int value) {
		}

		@Override
		public int getCount() {
			return DATA;
		}
	}

	// ── opening ──────────────────────────────────────────────────

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer hunter)) {
				return InteractionResult.PASS;
			}
			BlockEntity entity = level.getBlockEntity(hit.getBlockPos());
			if (entity == null || !entity.hasAttached(Cache.CACHE) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			open(hunter, entity);
			return InteractionResult.SUCCESS;
		});
	}

	/** Opens a cache for a hunter. */
	public static void open(final ServerPlayer hunter, final BlockEntity entity) {
		Cache cache = entity.getAttached(Cache.CACHE);
		if (cache == null) {
			return;
		}
		hunter.openMenu(new ExtendedMenuProvider<View>() {
			@Override
			public View getScreenOpeningData(final ServerPlayer player) {
				return View.of(cache);
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable("container.mistinn.search");
			}

			@Override
			public AbstractContainerMenu createMenu(final int id, final Inventory inventory, final Player player) {
				return new SearchMenu(id, inventory, View.of(cache), new CacheItems(entity), new CacheData(entity), entity);
			}
		});
		hunter.level().playSound(null, entity.getBlockPos(), SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.6F, 0.9F);
	}
}
