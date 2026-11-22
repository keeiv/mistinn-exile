package life.shizune.mistinn.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.shore.SaltCorpse;
import life.shizune.mistinn.tier.Tier;
import life.shizune.mistinn.tier.Tiers;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.Structures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 越往上越好: a lighthouse keeps its stores on three floors, and the higher a hunter climbs, the better
 * what they find. A barrel of odds and ends at the foot of the stair, the keeper's supplies on the landing
 * halfway up, and in the lamp room, past the keeper, a chest of the best of it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnLighthouseLootTest implements FabricClientGameTest {
	private static final String BASE = "chests/lighthouse_base";
	private static final String LANDING = "chests/lighthouse";
	private static final String LAMP = "chests/lighthouse_lamp";
	private static final int ROLLS = 200;

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
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				// where each floor's stores stand: the loot table, and the height above the ground
				long[] seeds = {3L, 7L, 11L};
				for (int i = 0; i < seeds.length; i++) {
					long seed = seeds[i];
					int x = (int) server.getPlayerList().getPlayers().getFirst().getX() + 40 * (i + 1);
					int z = (int) server.getPlayerList().getPlayers().getFirst().getZ();
					level.getChunk(x >> 4, z >> 4);
					BlockPos floor = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
					Structures.lighthouse(new Builder(level, floor, RandomSource.create(seed)));
					level.getEntitiesOfClass(SaltCorpse.class, new AABB(floor).inflate(16.0)).forEach(SaltCorpse::discard);
					level.getEntitiesOfClass(Snuffer.class, new AABB(floor).inflate(40.0)).forEach(Snuffer::discard);
					Map<String, Integer> found = new HashMap<>();
					for (BlockPos at : BlockPos.betweenClosed(floor.offset(-4, 1, -7), floor.offset(4, Structures.LAMP_ROOM + 2, 7))) {
						if (level.getBlockEntity(at) instanceof RandomizableContainerBlockEntity container && container.getLootTable() != null) {
							found.put(container.getLootTable().identifier().getPath(), at.getY() - floor.getY());
						}
					}
					check(found.containsKey(BASE) && found.get(BASE) <= 3, "at the foot of the stair, a barrel of odds and ends (seed " + seed + "): " + found);
					check(found.containsKey(LANDING) && found.get(LANDING) > 3 && found.get(LANDING) < Structures.LAMP_ROOM, "on the landing halfway up, the keeper's supplies (seed " + seed + "): " + found);
					check(found.containsKey(LAMP) && found.get(LAMP) > Structures.LAMP_ROOM, "in the lamp room, past the keeper, a chest (seed " + seed + "): " + found);
				}
				// the higher, the better
				double base = meanTier(server, level, BASE, 1);
				double landing = meanTier(server, level, LANDING, 2);
				double lamp = meanTier(server, level, LAMP, 3);
				check(base < landing && landing < lamp, "the higher the floor, the better its stores: " + base + " < " + landing + " < " + lamp);
				check(maxTier(server, level, BASE, 4).ordinal() <= Tier.II.ordinal(), "at the foot of the stair, nothing better than II");
				check(lamp >= Tier.III.ordinal(), "in the lamp room, III and better as a rule: " + lamp);
			});
		}
	}

	private static List<ItemStack> roll(final net.minecraft.server.MinecraftServer server, final ServerLevel level, final String path, final long seed) {
		LootTable table = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Mistinn.id(path)));
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
		RandomSource random = RandomSource.create(seed);
		List<ItemStack> all = new java.util.ArrayList<>();
		for (int i = 0; i < ROLLS; i++) {
			all.addAll(table.getRandomItems(params, random));
		}
		check(!all.isEmpty(), path + " gives something");
		return all;
	}

	/** The average tier of what a table gives that has a tier at all (salt and bread have none). */
	private static double meanTier(final net.minecraft.server.MinecraftServer server, final ServerLevel level, final String path, final long seed) {
		int sum = 0;
		int count = 0;
		for (ItemStack stack : roll(server, level, path, seed)) {
			Tier tier = Tiers.of(stack);
			if (tier.ordinal() > Tier.N.ordinal()) {
				sum += tier.ordinal();
				count++;
			}
		}
		check(count > 0, path + " gives some gear");
		return (double) sum / count;
	}

	private static Tier maxTier(final net.minecraft.server.MinecraftServer server, final ServerLevel level, final String path, final long seed) {
		Tier most = Tier.N;
		for (ItemStack stack : roll(server, level, path, seed)) {
			Tier tier = Tiers.of(stack);
			if (tier.ordinal() > most.ordinal()) {
				most = tier;
			}
		}
		return most;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
