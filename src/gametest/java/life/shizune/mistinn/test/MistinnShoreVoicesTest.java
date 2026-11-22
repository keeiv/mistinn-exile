package life.shizune.mistinn.test;

import java.lang.reflect.Method;
import java.util.List;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;

/**
 * 霧中小怪的聲音: the salt corpse, the lamp thrall and the mist shade each have voices of their own (breath, hurt, death
 * and, for those that walk, a step), not the husk's or the zombie's or the soul sand's; every one is in the sound list
 * the game loaded.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnShoreVoicesTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> voices(mc));
		}
	}

	private static void voices(final net.minecraft.client.Minecraft mc) {
		{
			for (var type : List.of(MistinnEntities.SALT_CORPSE, MistinnEntities.LAMP_THRALL, MistinnEntities.MIST_SHADE)) {
				LivingEntity entity = (LivingEntity) type.create(mc.level, EntitySpawnReason.COMMAND);
				String name = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
				List<String> voices = new java.util.ArrayList<>(List.of("getAmbientSound", "getDeathSound"));
				if (type != MistinnEntities.MIST_SHADE) {
					voices.add("getStepSound");
				}
				for (String voice : voices) {
					check(entity, name, voice, (SoundEvent) call(entity, voice));
				}
				check(entity, name, "getHurtSound", (SoundEvent) call(entity, "getHurtSound", mc.level.damageSources().generic()));
				check(mc.getSoundManager().getSoundEvent(life.shizune.mistinn.Mistinn.id("entity." + name + ".ambient")) != null,
					name + "'s breath is in the loaded sound list");
			}
		}
	}

	private static void check(final LivingEntity entity, final String name, final String voice, final SoundEvent sound) {
		Identifier id = sound.location();
		check(id.getNamespace().equals("mistinn") && id.getPath().startsWith("entity." + name + "."), name + " " + voice + " is its own, not " + id);
	}

	private static Object call(final Object target, final String name, final Object... args) {
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
			for (Method method : c.getDeclaredMethods()) {
				if (method.getName().equals(name) && method.getParameterCount() == args.length) {
					try {
						method.setAccessible(true);
						return method.invoke(target, args);
					} catch (ReflectiveOperationException e) {
						throw new AssertionError(e);
					}
				}
			}
		}
		throw new AssertionError("no " + name + " on " + target.getClass());
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
