package life.shizune.mistinn.client.irismixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.compat.ShaderIds;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When Iris turns a shader pack's block list into ids for each block state, the mod's blocks the pack does not name are
 * told to it as the vanilla blocks they are like ({@link ShaderIds}). Applied only where Iris is installed.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.materialmap.BlockMaterialMapping", remap = false)
public abstract class IrisBlockIdsMixin {
	@Inject(method = "createBlockStateIdMap", at = @At("RETURN"), remap = false, require = 0)
	private static void mistinn$tellOurBlocks(final Int2ObjectLinkedOpenHashMap<?> blocks, final Int2ObjectLinkedOpenHashMap<?> tags,
		final CallbackInfoReturnable<Object2IntMap<BlockState>> result) {
		try {
			int told = ShaderIds.alias(result.getReturnValue());
			Mistinn.LOGGER.info("Told the shader pack {} of the mod's block states{}", told,
				ShaderIds.complementary(result.getReturnValue()) ? " (Complementary's slots)" : "");
		} catch (RuntimeException e) {
			// a pack read some other way: leave it as it is
			Mistinn.LOGGER.warn("Could not tell the shader pack of the mod's blocks", e);
		}
	}
}
