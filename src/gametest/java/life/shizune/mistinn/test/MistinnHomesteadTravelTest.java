package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.client.worldmap.ExploredMapScreen;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.HomesteadTravel;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The way home: between homesteads it is quick and free; the way closes behind for a while; from
 * the wild it takes a homeward chip and standing still, and moving breaks it (the chip kept); the
 * hurt cannot set off; the map marks the homesteads and picks them under the pointer.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnHomesteadTravelTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set day");
			context.waitTicks(20);

			// two homesteads, one near and one far
			Settlements.Settlement[] home = new Settlements.Settlement[2];
			BlockPos[] wild = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
				for (int i = 0; i < 2; i++) {
					BlockPos column = player.blockPosition().offset(i == 0 ? 40 : -40, 0, i == 0 ? 0 : 360);
					level.getChunk(column.getX() >> 4, column.getZ() >> 4);
					Domain domain = TerraService.settleAt(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column), 60);
					Settlements.Settlement built = Settlements.buildNow(level, domain);
					check(built != null, "settlement " + i + " is built");
					check(Settlements.light(level, built.hub(), player), "and its hub woken");
					home[i] = Settlements.of(server, domain.id());
				}
				wild[0] = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(-30, 0, -40));
			});

			// from one homestead to another: at once, free, and the way closes behind
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				stand(server, home[0].hub().offset(3, 0, 0).atY(home[0].deck() + 1));
				check(HomesteadTravel.ask(player, home[1].domain()) == HomesteadTravel.Result.GONE, "between homesteads the way is open");
				check(life.shizune.mistinn.achievement.AchievementService.has(player, life.shizune.mistinn.achievement.Achievement.TRAVEL), "and the way home is remembered");
				double there = Math.sqrt(player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(home[1].hub())));
				check(there < 6.0, "and the hunter is there: " + there);
				check(HomesteadTravel.ask(player, home[0].domain()) == HomesteadTravel.Result.COOLING, "not again so soon");
				player.setAttached(HomesteadTravel.READY_AT, 0L);
				check(HomesteadTravel.ask(player, home[1].domain()) == HomesteadTravel.Result.ALREADY_THERE, "nor to where one stands");
			});

			// from the wild: a chip and standing still; moving breaks it and keeps the chip
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				stand(server, wild[0]);
				check(HomesteadTravel.ask(player, home[0].domain()) == HomesteadTravel.Result.NO_CHIP, "from the wild it takes a homeward chip");
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.LATTICE_CHIP, 2));
				check(HomesteadTravel.ask(player, home[0].domain()) == HomesteadTravel.Result.CHANNELING, "with one the way is sought");
			});
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(HomesteadTravel.channeling(player), "still seeking");
				stand(server, wild[0].offset(3, 0, 0));
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(!HomesteadTravel.channeling(player), "moving breaks it");
				check(player.getMainHandItem().getCount() == 2, "and the chip is kept");
				check(HomesteadTravel.ask(player, home[0].domain()) == HomesteadTravel.Result.CHANNELING, "sought again");
			});
			context.waitTicks(HomesteadTravel.CHANNEL + 10);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(home[0].hub())) < 36.0, "standing still, the hunter comes home");
				check(player.getMainHandItem().getCount() == 1, "spending one chip");
				// the hurt cannot set off
				player.setAttached(HomesteadTravel.READY_AT, 0L);
				stand(server, wild[0]);
				player.removeEffect(MobEffects.RESISTANCE);
				player.hurtServer(server.overworld(), server.overworld().damageSources().generic(), 1.0F);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
				check(HomesteadTravel.ask(player, home[1].domain()) == HomesteadTravel.Result.HURT, "not while lately hurt");
			});

			// the map: the homesteads marked, picked under the pointer
			world.getServer().runOnServer(server -> {
				player(server).setGameMode(GameType.CREATIVE);
				stand(server, home[0].hub().offset(-20, 6, -20).atY(home[0].deck() + 6));
			});
			context.waitTicks(60);
			context.runOnClient(mc -> mc.gui.setScreen(new ExploredMapScreen()));
			context.waitForScreen(ExploredMapScreen.class);
			context.waitTicks(10);
			check(context.computeOnClient(mc -> {
				ExploredMapScreen screen = (ExploredMapScreen) mc.gui.screen();
				double[] at = screen.toScreen(home[0].hub().getX() + 0.5, home[0].hub().getZ() + 0.5);
				Settlements.Settlement under = screen.homeUnder(at[0], at[1]);
				return under != null && under.domain() == home[0].domain();
			}), "the map marks the homestead under the pointer");
			context.takeScreenshot(TestScreenshotOptions.of("map").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("map")));
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	private static void stand(final MinecraftServer server, final BlockPos at) {
		player(server).teleportTo(server.overworld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		player(server).setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
