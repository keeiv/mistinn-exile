package life.shizune.mistinn.client.irismixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.lang.reflect.Constructor;
import java.util.Properties;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.compat.ShaderIds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When Iris reads a shader pack's entity and item lists, the mod's the pack does not name are told to it as the vanilla
 * ones they are like: the mist pine boats as boats, so no water shows inside them, and its lights held in the hand in the
 * nearest vanilla light's colour ({@link ShaderIds}). Applied only where Iris is installed.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.IdMap", remap = false)
public abstract class IrisNamedIdsMixin {
	@Inject(method = "parseIdMap", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private static void mistinn$tellOurNamed(final Properties properties, final String prefix, final String file,
		final CallbackInfoReturnable<Object2IntMap<Object>> result) {
		boolean entities = "entity.".equals(prefix);
		if (!entities && !"item.".equals(prefix)) {
			return;
		}
		try {
			Object2IntMap<Object> ids = result.getReturnValue();
			// the pack's own keys are Iris's namespaced ids: make ours the same way
			Constructor<?> make = Class.forName("net.irisshaders.iris.shaderpack.materialmap.NamespacedId").getConstructor(String.class, String.class);
			Object2IntOpenHashMap<Object> more = new Object2IntOpenHashMap<>(ids);
			more.defaultReturnValue(ids.defaultReturnValue());
			java.util.function.BiFunction<String, String, Object> key = (namespace, name) -> {
				try {
					return make.newInstance(namespace, name);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			};
			int told = entities ? ShaderIds.aliasEntities(more, key) : ShaderIds.aliasItems(more, key);
			if (told > 0) {
				result.setReturnValue(Object2IntMaps.unmodifiable(more));
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			Mistinn.LOGGER.warn("Could not tell the shader pack of the mod's entities or items", e);
		}
	}
}
