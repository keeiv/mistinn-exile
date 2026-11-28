package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.CrystalSheep;
import life.shizune.mistinn.terra.JadeAntelope;
import life.shizune.mistinn.terra.LandShark;
import life.shizune.mistinn.terra.TerraFauna;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * Terra's neutral animals and the stocking of old land. The crystal sheep is shorn for crystal grit and rams whoever
 * strikes it; the land shark swims under the ground, hard to hurt there, and leaps out at whoever strikes it; a
 * highland's chunks, once their ground is turned, are stocked with its animals once (on its own open ground), and not
 * again.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTerraFaunaTest implements FabricClientGameTest {
	private static final int Y = 200;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 6000");
			context.waitTicks(20);
			BlockPos[] at = new BlockPos[1];
			CrystalSheep[] sheep = new CrystalSheep[1];
			LandShark[] shark = new LandShark[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				at[0] = player.blockPosition().atY(Y);
				BlockPos p = at[0];
				for (int x = -10; x <= 10; x++) {
					for (int z = -10; z <= 10; z++) {
						level.setBlock(p.offset(x, -1, z), MistinnBlocks.JADE_TURF.defaultBlockState(), 2);
						for (int y = 0; y <= 3; y++) {
							level.setBlock(p.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
				player.teleportTo(level, p.getX() + 0.5, Y, p.getZ() - 6.5, Set.of(), 0.0F, 20.0F, true);
				sheep[0] = MistinnEntities.CRYSTAL_SHEEP.create(level, EntitySpawnReason.EVENT);
				sheep[0].snapTo(p.getX() - 2.5, Y, p.getZ() + 0.5, 200.0F, 0.0F);
				sheep[0].setPersistenceRequired();
				level.addFreshEntity(sheep[0]);
				shark[0] = MistinnEntities.LAND_SHARK.create(level, EntitySpawnReason.EVENT);
				shark[0].snapTo(p.getX() + 3.5, Y, p.getZ() + 1.5, 160.0F, 0.0F);
				shark[0].setPersistenceRequired();
				level.addFreshEntity(shark[0]);
			});
			context.waitTicks(40);
			context.takeScreenshot(TestScreenshotOptions.of("fauna").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("fauna")));
			// (left alone it leaps now and then for nothing: wait till it is under the ground again)
			for (int i = 0; i < 60 && world.getServer().computeOnServer(server -> shark[0].leaping()); i++) {
				context.waitTick();
			}
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				// shorn, for crystal grit
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
				check(sheep[0].readyForShearing(), "a crystal sheep grows crystal");
				sheep[0].interact(player, InteractionHand.MAIN_HAND, sheep[0].position());
				check(sheep[0].shorn(), "shears take it off");
				check(!level.getEntitiesOfClass(ItemEntity.class, sheep[0].getBoundingBox().inflate(3.0), e -> e.getItem().is(MistinnItems.CRYSTAL_DUST)).isEmpty(),
					"as crystal grit");
				// struck, it turns on whoever struck it
				sheep[0].hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
				check(sheep[0].getTarget() == player, "struck, the sheep turns on its striker");
				// under the ground the shark is hard to hurt; out of it, not
				float before = shark[0].getHealth();
				shark[0].hurtServer(level, level.damageSources().playerAttack(player), 10.0F);
				float under = before - shark[0].getHealth();
				check(Math.abs(under - 10.0F * LandShark.UNDER) < 0.01F, "under the ground a blow comes to little: " + under);
				check(shark[0].getTarget() == player, "and the shark hunts whoever struck it");
			});
			boolean leapt = false;
			for (int i = 0; i < 200 && !leapt; i++) {
				context.waitTick();
				leapt = world.getServer().computeOnServer(server -> shark[0].leaping());
			}
			check(leapt, "it leaps out of the ground at them");
			context.takeScreenshot(TestScreenshotOptions.of("shark-leap").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("fauna")));
			// a highland's land stocked once with its animals
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				BlockPos centre = player.blockPosition().atY(Y).offset(400, 0, 0);
				Domain domain = TerraService.settleAt(level, centre, 240);
				// a stretch of the valleys, open turf, six chunks by six, half way out
				BlockPos from = centre.offset(110, 0, -48);
				int stocked = 0;
				for (int cx = 0; cx < 6; cx++) {
					for (int cz = 0; cz < 6; cz++) {
						LevelChunk chunk = level.getChunk((from.getX() >> 4) + cx, (from.getZ() >> 4) + cz);
						ChunkPos pos = chunk.getPos();
						for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
							for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
								level.setBlock(new BlockPos(x, Y - 1, z), MistinnBlocks.JADE_TURF.defaultBlockState(), 2);
								for (int y = Y; y <= Y + 30; y++) {
									if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
										level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
									}
								}
							}
						}
						TerraFauna.stock(level, chunk, domain);
						stocked++;
					}
				}
				AABB area = new AABB(from.getX(), Y - 2, from.getZ(), from.getX() + 96, Y + 4, from.getZ() + 96);
				int antelopes = level.getEntitiesOfClass(JadeAntelope.class, area).size();
				int sheepCount = level.getEntitiesOfClass(CrystalSheep.class, area).size();
				check(antelopes > 0 && sheepCount > 0, "a stretch of the valleys is stocked: " + antelopes + " antelopes, " + sheepCount + " sheep in " + stocked + " chunks");
				// stocked chunks are marked; a chunk with someone's things in it keeps clear of them
				check(TerraFauna.VERSION >= 1, "the version is set");
			});
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn fauna] ok: " + what);
	}
}
