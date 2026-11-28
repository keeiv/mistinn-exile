package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.GateSentry;
import life.shizune.mistinn.terra.JadeAntelope;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The jade-horn antelope, Terra's grazer: it comes of itself on jade turf in the light, not on bare stone; jade grass is
 * its food and two fed it have a fawn; one struck, the herd near it bolts too; brought down it leaves leather. The gate
 * sentry's loot is read as this version reads loot tables (it leaves its salvage).
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnJadeAntelopeTest implements FabricClientGameTest {
	private static final int Y = 200;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 6000");
			context.waitTicks(20);
			BlockPos[] at = new BlockPos[1];
			JadeAntelope[] pair = new JadeAntelope[2];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				at[0] = player.blockPosition().atY(Y);
				BlockPos p = at[0];
				for (int x = -12; x <= 12; x++) {
					for (int z = -12; z <= 12; z++) {
						level.setBlock(p.offset(x, -1, z), MistinnBlocks.JADE_TURF.defaultBlockState(), 2);
						for (int y = 0; y <= 3; y++) {
							level.setBlock(p.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
				level.setBlock(p.offset(8, -1, 8), Blocks.STONE.defaultBlockState(), 2);
				RandomSource random = RandomSource.create(1L);
				check(JadeAntelope.checkSpawnRules(MistinnEntities.JADE_ANTELOPE, level, EntitySpawnReason.NATURAL, p, random), "it comes of itself on jade turf in the light");
				check(!JadeAntelope.checkSpawnRules(MistinnEntities.JADE_ANTELOPE, level, EntitySpawnReason.NATURAL, p.offset(8, 0, 8), random), "not on bare stone");
				// a pair, fed
				for (int i = 0; i < 2; i++) {
					pair[i] = MistinnEntities.JADE_ANTELOPE.create(level, EntitySpawnReason.EVENT);
					pair[i].snapTo(p.getX() + 0.5 + i * 1.5, Y, p.getZ() + 0.5, 0.0F, 0.0F);
					level.addFreshEntity(pair[i]);
				}
				check(pair[0].isFood(new ItemStack(MistinnItems.JADE_GRASS)) && !pair[0].isFood(new ItemStack(Items.WHEAT)), "jade grass is its food");
				pair[0].setInLove(player);
				pair[1].setInLove(player);
				player.teleportTo(level, p.getX() + 0.5, Y + 1.0, p.getZ() - 5.5, Set.of(), 0.0F, 20.0F, true);
			});
			boolean fawn = false;
			for (int i = 0; i < 300 && !fawn; i++) {
				context.waitTick();
				fawn = world.getServer().computeOnServer(server -> server.overworld()
					.getEntitiesOfClass(JadeAntelope.class, new AABB(at[0]).inflate(16.0), JadeAntelope::isBaby).size() > 0);
			}
			check(fawn, "two fed jade grass have a fawn");
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("antelope").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("antelope")));
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				// one struck: the herd bolts with it
				pair[0].hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
				check(pair[1].getLastHurtByMob() == player, "one struck, the herd near it bolts too");
				// brought down: its hide
				pair[1].kill(level);
				check(dropped(level, pair[1].blockPosition(), Items.LEATHER), "brought down, it leaves leather");
				// the gate sentry leaves its salvage
				GateSentry sentry = MistinnEntities.GATE_SENTRY.create(level, EntitySpawnReason.EVENT);
				sentry.snapTo(at[0].getX() - 6.5, Y, at[0].getZ() - 6.5, 0.0F, 0.0F);
				level.addFreshEntity(sentry);
				sentry.kill(level);
				check(dropped(level, sentry.blockPosition(), MistinnItems.SALVAGE.get(3)) && dropped(level, sentry.blockPosition(), MistinnItems.SALVAGE.get(6)),
					"a gate sentry leaves copper gears and a lattice fragment");
			});
		}
	}

	private static boolean dropped(final ServerLevel level, final BlockPos at, final net.minecraft.world.item.Item item) {
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3.0));
		return items.stream().anyMatch(e -> e.getItem().is(item));
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn antelope] ok: " + what);
	}
}
