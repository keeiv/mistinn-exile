package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.CrystalLizard;
import life.shizune.mistinn.terra.TerraService;
import life.shizune.mistinn.terra.TerraShaper;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ruins of the old lattice towers in the vein waste: a chest of what was left on a broken floor,
 * by the lattice core, and lizards lying still among the rubble; the chest gives crystal grit, and
 * sometimes more.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTerraRuinsTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		// a ruin in every chunk of the vein waste, so there are some to find
		TerraShaper.ruins = 40.0;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set day");
			context.waitTicks(20);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				// the hunter out in the vein waste of a highland whose heart is a way off
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player(server).blockPosition().offset(75, 0, 0));
				TerraService.settleAt(level, centre, 90);
			});
			for (int i = 0; i < 80; i++) {
				context.waitTicks(10);
				if (world.getServer().computeOnServer(server -> DomainService.waiting() == 0) && i > 3) {
					break;
				}
			}
			context.waitTicks(60);

			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				List<BlockPos> chests = new ArrayList<>();
				int cx = player.blockPosition().getX() >> 4;
				int cz = player.blockPosition().getZ() >> 4;
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						if (!level.hasChunk(cx + dx, cz + dz)) {
							continue;
						}
						for (BlockEntity entity : level.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
							if (entity instanceof ChestBlockEntity chest && TerraShaper.RUIN_LOOT.equals(chest.getLootTable())) {
								chests.add(chest.getBlockPos());
							}
						}
					}
				}
				check(!chests.isEmpty(), "ruins stand in the vein waste, each with a chest of what was left");
				BlockPos chest = chests.getFirst();
				check(level.getBlockState(chest.below()).is(MistinnBlocks.VEIN_STONE_BRICKS) || level.getBlockState(chest.below()).is(MistinnBlocks.VEIN_STONE_POLISHED)
					|| level.getBlockState(chest.below()).is(MistinnBlocks.VEIN_STONE), "on a floor of vein stone");
				check(level.getBlockState(chest.offset(1, 1, -1)).is(MistinnBlocks.LATTICE_CRYSTAL), "by the lattice core still standing");
				long still = level.getEntitiesOfClass(CrystalLizard.class, new AABB(chest).inflate(4.0), CrystalLizard::isStill).size();
				check(still >= 2, "with lizards lying still among the rubble: " + still);
				// opening it is remembered
				player.teleportTo(level, chest.getX() + 0.5, chest.getY() + 1.0, chest.getZ() + 2.5, java.util.Set.of(), 180.0F, 30.0F, true);
				player.gameMode.useItemOn(player, level, player.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(chest), net.minecraft.core.Direction.UP, chest, false));
				check(life.shizune.mistinn.achievement.AchievementService.has(player, life.shizune.mistinn.achievement.Achievement.RUIN_OPENED),
					"opening a ruin's chest is remembered");

				// what the chest holds
				LootTable table = server.reloadableRegistries().getLootTable(TerraShaper.RUIN_LOOT);
				LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
				RandomSource random = RandomSource.create(7L);
				int dust = 0;
				int more = 0;
				for (int i = 0; i < 40; i++) {
					for (ItemStack stack : table.getRandomItems(params, random)) {
						if (stack.is(MistinnItems.CRYSTAL_DUST)) {
							dust++;
						} else {
							more++;
						}
					}
				}
				check(dust == 40, "every chest holds crystal grit: " + dust);
				check(more >= 40, "and something more besides: " + more);
			});
		} finally {
			TerraShaper.ruins = 1.0;
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
