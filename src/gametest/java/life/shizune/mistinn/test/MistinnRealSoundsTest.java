package life.shizune.mistinn.test;

import java.util.List;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * 腳步與裝備: walking on (and landing on) stone, wood, grass, gravel, sand, snow, wool or metal sounds like the ground it is, from the mod's
 * own recordings in place of the game's; a helmet is knocked on as a hard shell, a mask strapped on with a breath
 * through its filter.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnRealSoundsTest implements FabricClientGameTest {
	/** One step event of each ground the mod records anew. */
	private static final List<String> STEPS = List.of("block.stone.step", "block.deepslate.step", "block.wood.step", "block.grass.step",
		"block.gravel.step", "block.sand.step", "block.snow.step", "block.wool.step", "block.metal.step", "block.copper.step",
		// and landing on it from a height
		"block.stone.fall", "block.wood.fall", "block.grass.fall", "block.gravel.fall", "block.sand.fall", "block.metal.fall");

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		context.runOnClient(mc -> {
			RandomSource random = RandomSource.create(7L);
			for (String step : STEPS) {
				Identifier id = Identifier.withDefaultNamespace(step);
				check(BuiltInRegistries.SOUND_EVENT.containsKey(id), step + " is one of the game's own sounds");
				var events = mc.getSoundManager().getSoundEvent(id);
				check(events != null, step + " is still in the sound list");
				for (int i = 0; i < 8; i++) {
					Sound sound = events.getSound(random);
					check(sound.getLocation().getNamespace().equals("mistinn"), step + " walks on the mod's own recording, not " + sound.getLocation());
				}
			}
			check(mc.getSoundManager().getSoundEvent(MistinnSounds.EQUIP_MASK.location()) != null, "the mask has its own sound");
		});
		// an item's parts are bound only once a world is loaded
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> helmets());
		}
	}

	private static void helmets() {
		{
			var helmet = new ItemStack(MistinnItems.HELMETS.get(2)).get(DataComponents.EQUIPPABLE);
			check(helmet != null && helmet.equipSound().value() == MistinnSounds.EQUIP_HELMET.value(), "a tier II helmet goes on as a hard shell");
			var cap = new ItemStack(MistinnItems.HELMETS.get(1)).get(DataComponents.EQUIPPABLE);
			check(cap != null && cap.equipSound().value() == MistinnSounds.EQUIP_CLOTH.value(), "a tier I cap is cloth");
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
