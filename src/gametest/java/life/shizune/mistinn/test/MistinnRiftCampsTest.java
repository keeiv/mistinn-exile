package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.rift.RiftService;
import life.shizune.mistinn.rift.RiftShaper;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Camps the rift took, on its seared ground: a tent's charred frame, a cold fire, and the pack
 * left behind, salt in it always and more besides.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnRiftCampsTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		// a camp in every chunk of the rift's ground, so there are some to find
		RiftShaper.camps = 25.0;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set day");
			context.waitTicks(20);
			world.getServer().runOnServer(server -> RiftService.tearAt(server.overworld(), player(server).blockPosition().offset(20, 0, 0)));
			for (int i = 0; i < 80; i++) {
				context.waitTicks(10);
				if (world.getServer().computeOnServer(server -> DomainService.waiting() == 0) && i > 6) {
					break;
				}
			}
			context.waitTicks(100);

			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				List<BlockPos> packs = new ArrayList<>();
				int cx = player.blockPosition().getX() >> 4;
				int cz = player.blockPosition().getZ() >> 4;
				for (int dx = -4; dx <= 4; dx++) {
					for (int dz = -4; dz <= 4; dz++) {
						if (!level.hasChunk(cx + dx, cz + dz)) {
							continue;
						}
						for (BlockEntity entity : level.getChunk(cx + dx, cz + dz).getBlockEntities().values()) {
							if (entity instanceof BarrelBlockEntity barrel && RiftShaper.CAMP_LOOT.equals(barrel.getLootTable())) {
								packs.add(barrel.getBlockPos());
							}
						}
					}
				}
				check(!packs.isEmpty(), "camps the rift took lie on its ground, each with a pack left behind");
				BlockPos pack = packs.getFirst();
				BlockPos fire = pack.offset(-1, 0, 1);
				check(level.getBlockState(fire).is(Blocks.CAMPFIRE) && !level.getBlockState(fire).getValue(CampfireBlock.LIT), "by a cold fire");
				check(level.getBlockState(pack.offset(-3, 0, -1)).is(MistinnBlocks.CHARRED_LOG), "under a tent's charred frame");

				LootTable table = server.reloadableRegistries().getLootTable(RiftShaper.CAMP_LOOT);
				LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
				RandomSource random = RandomSource.create(11L);
				int salt = 0;
				int more = 0;
				for (int i = 0; i < 40; i++) {
					for (ItemStack stack : table.getRandomItems(params, random)) {
						if (stack.is(MistinnItems.SALT)) {
							salt++;
						} else {
							more++;
						}
					}
				}
				check(salt == 40, "every pack holds salt: " + salt);
				check(more >= 80, "and two things more: " + more);
			});
		} finally {
			RiftShaper.camps = 1.0;
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
