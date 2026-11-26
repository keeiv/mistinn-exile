package life.shizune.mistinn.naval;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 火槍 and 手槍, the pirates' guns, taken from them. Hold the use key to raise and aim (the shot is
 * steadier the longer it is held, up to a point), let go to fire one paper cartridge from the
 * hotbar, the offhand or the pockets, rig and backpack. Then it must be reloaded. A long gun reaches
 * far and hits hard; a pistol comes up quicker.
 */
public class FlintlockItem extends Item {
	/** Ticks the gun must be held up before letting go fires it. */
	private final int aim;
	private final int reload;
	private final float damage;
	private final double spread;
	private final double range;

	public FlintlockItem(final Properties properties, final int aim, final int reload, final float damage, final double spread, final double range) {
		super(properties);
		this.aim = aim;
		this.reload = reload;
		this.damage = damage;
		this.spread = spread;
		this.range = range;
	}

	public static FlintlockItem musket(final Properties properties) {
		return new FlintlockItem(properties, 14, 60, 15.0F, 0.004, 48.0);
	}

	public static FlintlockItem pistol(final Properties properties) {
		return new FlintlockItem(properties, 6, 40, 10.0F, 0.012, 28.0);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		player.startUsingItem(hand);
		if (!level.isClientSide()) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(final ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	@Override
	public int getUseDuration(final ItemStack stack, final LivingEntity user) {
		return 72000;
	}

	@Override
	public boolean releaseUsing(final ItemStack stack, final Level level, final LivingEntity user, final int remaining) {
		int held = this.getUseDuration(stack, user) - remaining;
		if (held < this.aim || !(level instanceof ServerLevel server) || !(user instanceof ServerPlayer player)) {
			return false;
		}
		if (!player.hasInfiniteMaterials() && !takeCartridge(player)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 1.0F, 1.2F);
			player.sendOverlayMessage(Component.translatable("message.mistinn.flintlock.empty"));
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		// steadier the longer it was held, up to twice the aim
		double steadiness = Math.min(1.0, (held - this.aim) / (double) Math.max(1, this.aim));
		GunShot.fire(server, player, eye.add(look.scale(0.5)), eye.add(look.scale(this.range)), this.damage,
			this.spread * (1.6 - 0.6 * steadiness), this.range);
		player.getCooldowns().addCooldown(stack, this.reload);
		stack.hurtAndBreak(1, player, user.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		return true;
	}

	/** Spends one paper cartridge: from the hotbar or the offhand first, then the pockets, the rig and the backpack. */
	static boolean takeCartridge(final ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(MistinnItems.PAPER_CARTRIDGE)) {
				stack.shrink(1);
				return true;
			}
		}
		GridInventory grid = GridService.get(player);
		for (Area area : new Area[] {Area.POCKETS, Area.RIG, Area.BACKPACK}) {
			List<Placed> items = new ArrayList<>(grid.items(area));
			for (int i = 0; i < items.size(); i++) {
				ItemStack there = items.get(i).stack();
				if (there.is(MistinnItems.PAPER_CARTRIDGE)) {
					if (there.getCount() <= 1) {
						items.remove(i);
					} else {
						items.set(i, items.get(i).withStack(there.copyWithCount(there.getCount() - 1)));
					}
					GridService.set(player, grid.with(area, items));
					return true;
				}
			}
		}
		return false;
	}
}
