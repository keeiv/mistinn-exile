package life.shizune.mistinn.test;

import java.util.Map;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The mod's chests give what their tables say, in the amounts they say: salt comes in handfuls, never one
 * grain at a time. (A table written in an older format has its amounts quietly dropped by the game.)
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnLootTablesTest implements FabricClientGameTest {
	/** Each chest table, and how much salt one stack of it holds, least to most. */
	private static final Map<String, int[]> SALT = Map.of(
		"chests/lighthouse", new int[] {2, 8},
		"chests/salt_cairn", new int[] {4, 12},
		"chests/anchor_wreck", new int[] {3, 8},
		"chests/watchpost", new int[] {4, 10},
		"chests/drop_station", new int[] {2, 6},
		"chests/spawn_station", new int[] {1, 3});
	private static final int ROLLS = 80;

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
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				RandomSource random = RandomSource.create(42L);
				SALT.forEach((path, range) -> {
					LootTable table = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Mistinn.id(path)));
					LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
					int stacks = 0;
					int most = 0;
					for (int i = 0; i < ROLLS; i++) {
						for (ItemStack stack : table.getRandomItems(params, random)) {
							if (stack.is(MistinnItems.SALT)) {
								stacks++;
								most = Math.max(most, stack.getCount());
								check(stack.getCount() >= range[0] && stack.getCount() <= range[1],
									path + " gives salt " + range[0] + " to " + range[1] + " at a time, not " + stack.getCount());
							}
						}
					}
					check(stacks > 0, path + " gives salt at all");
					check(most > 1, path + " gives salt in handfuls, not grain by grain: at most " + most);
				});
			});
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
