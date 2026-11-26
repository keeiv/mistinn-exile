package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.client.terra.SquallClient;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.Squall;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * A crystal squall over a Terra highland: it gathers (the hunter's client is told, and the haze
 * comes in), then breaks; a hunter out under the open sky is cut and their helmet worn, one under
 * a roof is not; grit comes down round the hunter; it passes, the client clears, and the highland
 * keeps calm a while.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnCrystalSquallTest implements FabricClientGameTest {
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
			Domain[] terra = new Domain[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				// the hunter inside the highland, but too far from its heart to wake the crown elder
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(110, 0, 0));
				terra[0] = TerraService.settleAt(level, centre, 140);
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 1, false, false));
				check(Squall.storm(server, terra[0].id()) == null, "no squall comes of itself in a test world");
			});
			context.waitTicks(60);
			BlockPos[] open = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				// a clear patch of ground where the hunter stands
				BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, player.blockPosition());
				for (int x = -3; x <= 3; x++) {
					for (int z = -3; z <= 3; z++) {
						for (int y = 0; y <= 12; y++) {
							level.setBlockAndUpdate(at.offset(x, y, z), Blocks.AIR.defaultBlockState());
						}
						level.setBlockAndUpdate(at.offset(x, -1, z), Blocks.STONE.defaultBlockState());
					}
				}
				open[0] = at;
				player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				Squall.gather(level, terra[0], 40);
				check(Squall.storm(server, terra[0].id()).coming(), "a squall gathers");
			});
			context.waitTicks(25);
			check(world.getServer().computeOnServer(server -> Squall.sky(player(server)) == Squall.Stage.GATHERING), "the hunter is told it gathers");
			check(context.computeOnClient(mc -> SquallClient.haze() > 0.0F), "and the haze begins to come in");

			// it breaks: out under the open sky the hunter is cut, and the helmet worn
			context.waitTicks(30);
			check(world.getServer().computeOnServer(server -> Squall.sky(player(server)) == Squall.Stage.RAGING), "it breaks");
			float[] health = new float[1];
			world.getServer().runOnServer(server -> {
				player(server).removeEffect(MobEffects.REGENERATION);
				health[0] = player(server).getHealth();
			});
			context.waitTicks(Squall.CUT_EVERY * 2 + 5);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(Squall.exposed(server.overworld(), player), "the hunter stands under the open sky");
				check(player.getHealth() < health[0], "and the squall cuts them: " + health[0] + " -> " + player.getHealth());
				check(player.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() > 0, "and wears their helmet");
				check(player.getLastDamageSource() != null && player.getLastDamageSource().is(Squall.DAMAGE), "with grit");
			});

			// under a roof, nothing
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				for (int x = -2; x <= 2; x++) {
					for (int z = -2; z <= 2; z++) {
						level.setBlockAndUpdate(open[0].offset(x, 3, z), Blocks.OAK_PLANKS.defaultBlockState());
					}
				}
			});
			context.waitTicks(Squall.CUT_EVERY * 2 + 5);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(!Squall.exposed(server.overworld(), player), "under a roof the hunter is sheltered");
				// (the last hurt is forgotten after two seconds, and the squall cuts every two)
				check(player.getLastDamageSource() == null || !player.getLastDamageSource().is(Squall.DAMAGE), "and the squall does not cut them");
			});

			// grit comes down round the hunter
			boolean fell = false;
			for (int i = 0; i < 20 && !fell; i++) {
				context.waitTicks(Squall.FALL_EVERY);
				fell = world.getServer().computeOnServer(server -> !server.overworld().getEntitiesOfClass(ItemEntity.class,
					new AABB(player(server).blockPosition()).inflate(Squall.FALL_FAR + 2), e -> e.getItem().is(MistinnItems.CRYSTAL_DUST)
						|| e.getItem().is(MistinnItems.LATTICE_CHIP)).isEmpty());
			}
			check(fell, "flakes of the lattice come down round the hunter");
			world.getServer().runOnServer(server -> {
				life.shizune.mistinn.terra.CrystalDrake drake = life.shizune.mistinn.registry.MistinnEntities.CRYSTAL_DRAKE.spawn(server.overworld(),
					player(server).blockPosition().offset(6, 10, 0), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				check(drake.inSquall(), "the drakes of the highland know the squall is on them");
				drake.discard();
			});
			check(context.computeOnClient(mc -> SquallClient.haze() > 0.5F), "and the haze is thick");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), player.getX(), player.getY(), player.getZ(), Set.of(), 30.0F, -8.0F, true);
			});
			context.waitTicks(10);
			context.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("squall").disableCounterPrefix()
				.withDestinationDir(java.nio.file.Path.of("terra")));

			// it passes, and the highland keeps calm a while
			world.getServer().runOnServer(server -> Squall.stop(server.overworld(), terra[0]));
			context.waitTicks(45);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				Squall.Storm storm = Squall.storm(server, terra[0].id());
				check(!storm.coming(), "the squall passes");
				check(storm.next() >= level.getGameTime() + Squall.CALM - 100, "and the highland keeps calm a while");
				check(Squall.sky(player(server)) == Squall.Stage.CALM, "the hunter is told it has passed");
			});
			context.waitTicks(80);
			check(context.computeOnClient(mc -> SquallClient.haze() < 0.5F), "and the haze clears");
		}
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
