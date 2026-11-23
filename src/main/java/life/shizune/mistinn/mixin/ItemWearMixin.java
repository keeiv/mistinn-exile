package life.shizune.mistinn.mixin;

import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.hardship.RustAnchorWeapons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** In a Rust Anchor world a hunter's weapons wear by their kind ({@link RustAnchorWeapons}). */
@Mixin(ItemStack.class)
public abstract class ItemWearMixin {
	@ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int mistinn$rustAnchorWear(final int amount, final int same, final ServerLevel level, final @Nullable ServerPlayer player) {
		if (player == null || !Hardship.rustAnchor(player)) {
			return amount;
		}
		return RustAnchorWeapons.wear((ItemStack) (Object) this, amount, level.getRandom());
	}
}
