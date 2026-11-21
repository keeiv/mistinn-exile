package life.shizune.mistinn.residue;

import life.shizune.mistinn.block.GravestoneBlockEntity;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridRules;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.salt.SaltService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The death penalty. When a hunter falls, all the salt they carry turns into a wisp of residue
 * (殘香) held by a gravestone where they fell, and the spot is marked on their map. Everything
 * else they carried (worn gear, hotbar, pockets, chest rig and backpack; the safe box stays on
 * them) goes into the same stone rather than onto the ground. Using the stone gives it all
 * back. Falling again first leaves the old residue to fade: only the newest stone can be
 * opened, and an old one crumbles, its salt lost and its things spilled where it stood.
 */
public final class ResidueService {
	private static final int SEARCH_RADIUS = 3;

	private ResidueService() {
	}

	/** What each hunter who is dying carried, taken off them before vanilla could drop it. */
	private static final java.util.Map<java.util.UUID, java.util.List<Kept>> CARRIED = new java.util.HashMap<>();

	/**
	 * Called as a hunter dies, before vanilla drops their inventory: takes everything they carry
	 * but the safe box, to be kept in the gravestone. Items with the curse of vanishing vanish,
	 * as they would have.
	 */
	public static void collect(final ServerPlayer player) {
		if (!GridRules.usesGrid(player) || player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return;
		}
		java.util.List<Kept> kept = new java.util.ArrayList<>(CARRIED.getOrDefault(player.getUUID(), java.util.List.of()));
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.removeItemNoUpdate(slot);
			if (!stack.isEmpty() && !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				kept.add(new Kept(Kept.SLOT, slot, stack));
			}
		}
		GridInventory grid = GridService.get(player);
		for (Placed placed : grid.pockets()) {
			kept.add(new Kept(Kept.POCKET, 0, placed.stack().copy()));
		}
		if (!grid.mask().isEmpty()) {
			kept.add(new Kept(Kept.MASK, 0, grid.mask().copy()));
		}
		if (!grid.rig().isEmpty()) {
			kept.add(new Kept(Kept.RIG, 0, grid.rig().copy()));
		}
		if (!grid.backpack().isEmpty()) {
			kept.add(new Kept(Kept.BACKPACK, 0, grid.backpack().copy()));
		}
		GridService.set(player, new GridInventory(java.util.List.of(), grid.safe(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
		CARRIED.put(player.getUUID(), kept);
	}

	public static void onDeath(final ServerPlayer player) {
		ServerLevel level = player.level();
		java.util.List<Kept> items = java.util.List.copyOf(CARRIED.getOrDefault(player.getUUID(), java.util.List.of()));
		CARRIED.remove(player.getUUID());
		int salt = SaltService.get(player);
		int serial = player.getAttachedOrElse(MistinnAttachments.RESIDUE_SERIAL, 0) + 1;
		BlockPos pos = findSpot(level, player.blockPosition());

		player.setAttached(MistinnAttachments.RESIDUE_SERIAL, serial);
		player.setAttached(MistinnAttachments.DEATH_MARK, new DeathMark(GlobalPos.of(level.dimension(), pos), serial, salt));
		SaltService.set(player, 0);
		// the stone rises once the body has turned to smoke, at the end of the death scene
		PENDING.add(new Stone(level, pos, player.getStringUUID(), player.getPlainTextName(), salt, serial, items, STONE_DELAY));
	}

	/** Ticks from death to the gravestone: the length of the client's death scene, to the smoke. */
	public static final int STONE_DELAY = 70;

	private record Stone(ServerLevel level, BlockPos pos, String owner, String name, int salt, int serial, java.util.List<Kept> items, int ticks) {
	}

	private static final java.util.List<Stone> PENDING = new java.util.ArrayList<>();

	/** Called every server tick: raises the stones whose time has come. */
	public static void tick(final net.minecraft.server.MinecraftServer server) {
		for (int i = PENDING.size() - 1; i >= 0; i--) {
			Stone stone = PENDING.get(i);
			if (stone.ticks() > 0) {
				PENDING.set(i, new Stone(stone.level(), stone.pos(), stone.owner(), stone.name(), stone.salt(), stone.serial(), stone.items(), stone.ticks() - 1));
				continue;
			}
			PENDING.remove(i);
			if (!place(stone)) {
				// nowhere safe to stand yet; try again in a second
				PENDING.add(new Stone(stone.level(), stone.pos(), stone.owner(), stone.name(), stone.salt(), stone.serial(), stone.items(), RETRY));
			}
		}
	}

	private static final int RETRY = 20;

	/**
	 * Raises the pending stones at once: for tests and photo scenes that cannot wait, and when
	 * the server stops, so no salt is lost with the queue.
	 */
	public static void placeNow() {
		PENDING.removeIf(ResidueService::place);
	}

	/**
	 * Raises one stone. The spot was chosen at death; if something has been put there since,
	 * a new spot is found and the owner's map mark follows it. Never replaces a solid block or
	 * one holding contents. Returns false when there is no safe spot yet.
	 */
	private static boolean place(final Stone stone) {
		ServerLevel level = stone.level();
		BlockPos pos = stone.pos();
		if (!canHold(level, pos)) {
			pos = findSpot(level, pos);
			if (!canHold(level, pos)) {
				return false;
			}
			moveMark(level, stone, pos);
		}
		level.setBlockAndUpdate(pos, MistinnBlocks.GRAVESTONE.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof GravestoneBlockEntity entity) {
			entity.fill(stone.owner(), stone.name(), stone.salt(), stone.serial(), stone.items());
		}
		level.playSound(null, pos, MistinnSounds.RESIDUE_FORM, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 40, 0.3, 0.6, 0.3, 0.02);
		return true;
	}

	/** Points the owner's map mark at the stone's new spot, if it still marks this stone. */
	private static void moveMark(final ServerLevel level, final Stone stone, final BlockPos pos) {
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(stone.owner()));
		if (owner == null) {
			return;
		}
		DeathMark mark = owner.getAttached(MistinnAttachments.DEATH_MARK);
		if (mark != null && mark.serial() == stone.serial()) {
			owner.setAttached(MistinnAttachments.DEATH_MARK, new DeathMark(GlobalPos.of(level.dimension(), pos), mark.serial(), mark.salt()));
		}
	}

	/**
	 * The hunter uses a gravestone. Only its owner can take the salt back, and only from the
	 * newest stone; any other stone of theirs has faded and crumbles.
	 *
	 * @return true when the stone was handled
	 */
	public static boolean open(final ServerPlayer player, final BlockPos pos) {
		ServerLevel level = player.level();
		if (!(level.getBlockEntity(pos) instanceof GravestoneBlockEntity stone)) {
			return false;
		}
		boolean owner = stone.owner().equals(player.getStringUUID());
		if (!owner) {
			if (player.isCreative() && player.isShiftKeyDown()) {
				crumble(level, pos);
				return true;
			}
			player.sendSystemMessage(Component.translatable("message.mistinn.residue.not_yours", stone.ownerName()), true);
			return true;
		}

		DeathMark mark = player.getAttached(MistinnAttachments.DEATH_MARK);
		boolean newest = mark != null && mark.serial() == stone.serial();
		if (newest) {
			SaltService.add(player, stone.salt());
			int things = giveBack(player, stone.items());
			player.removeAttached(MistinnAttachments.DEATH_MARK);
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.playSound(null, pos, MistinnSounds.RESIDUE_RECOVER, SoundSource.PLAYERS, 1.0F, 1.0F);
			level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 30, 0.3, 0.5, 0.3, 0.06);
			player.sendSystemMessage(things > 0
				? Component.translatable("message.mistinn.residue.recovered_items", stone.salt(), things)
				: Component.translatable("message.mistinn.residue.recovered", stone.salt()), true);
			life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.Achievement.RESIDUE);
		} else {
			crumble(level, pos);
			player.sendSystemMessage(Component.translatable("message.mistinn.residue.faded"), true);
		}
		return true;
	}

	/**
	 * Gives a hunter back what their stone kept: worn pieces first (so the rig and backpack are
	 * there again for what goes into them), each to the slot it came from when that is free;
	 * the rest into the grid or any free slot, and what still does not fit at their feet.
	 * Returns how many stacks were given back.
	 */
	public static int giveBack(final ServerPlayer player, final java.util.List<Kept> items) {
		Inventory inventory = player.getInventory();
		java.util.List<ItemStack> loose = new java.util.ArrayList<>();
		for (Kept kept : items) {
			ItemStack stack = kept.stack().copy();
			GridInventory grid = GridService.get(player);
			boolean placed = switch (kept.where()) {
				case Kept.MASK -> {
					boolean free = grid.mask().isEmpty();
					if (free) {
						GridService.set(player, grid.withMask(stack));
					}
					yield free;
				}
				case Kept.RIG -> {
					boolean free = grid.rig().isEmpty();
					if (free) {
						GridService.set(player, grid.withRig(stack));
					}
					yield free;
				}
				case Kept.BACKPACK -> {
					boolean free = grid.backpack().isEmpty();
					if (free) {
						GridService.set(player, grid.withBackpack(stack));
						GridService.applyWeight(player);
					}
					yield free;
				}
				case Kept.SLOT -> {
					boolean free = kept.index() >= 0 && kept.index() < inventory.getContainerSize() && inventory.getItem(kept.index()).isEmpty();
					if (free) {
						inventory.setItem(kept.index(), stack);
					}
					yield free;
				}
				default -> false;
			};
			if (!placed) {
				loose.add(stack);
			}
		}
		for (ItemStack stack : loose) {
			GridService.insert(player, stack);
			if (!stack.isEmpty() && !inventory.add(stack)) {
				player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		player.containerMenu.broadcastChanges();
		return items.size();
	}

	/** An old or foreign stone breaks: its salt is lost, its things spill where it stood. */
	private static void crumble(final ServerLevel level, final BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof GravestoneBlockEntity stone) {
			for (Kept kept : stone.items()) {
				Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, kept.stack().copy());
			}
		}
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		level.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.01);
	}

	/**
	 * Where the gravestone goes: the death spot if it is free, otherwise the nearest free spot
	 * around it, and in the void the surface of the column.
	 */
	static BlockPos findSpot(final ServerLevel level, final BlockPos death) {
		int minY = level.getMinY() + 1;
		int maxY = level.getMaxY() - 1;
		BlockPos start = death;
		if (death.getY() < minY || death.getY() > maxY) {
			int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, death.getX(), death.getZ());
			start = new BlockPos(death.getX(), Math.max(minY, Math.min(surface, maxY)), death.getZ());
		}
		for (int r = 0; r <= SEARCH_RADIUS; r++) {
			for (int dy = 0; dy <= SEARCH_RADIUS; dy++) {
				for (int sign : new int[] {1, -1}) {
					for (int dx = -r; dx <= r; dx++) {
						for (int dz = -r; dz <= r; dz++) {
							if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
								continue;
							}
							BlockPos candidate = start.offset(dx, dy * sign, dz);
							if (candidate.getY() >= minY && candidate.getY() <= maxY && canHold(level, candidate)) {
								return candidate;
							}
						}
					}
				}
			}
		}
		return start;
	}

	private static boolean canHold(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return (state.isAir() || state.canBeReplaced()) && level.getBlockEntity(pos) == null && !state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
	}
}
