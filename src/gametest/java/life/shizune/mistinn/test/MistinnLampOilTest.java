package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.block.GoldenBellBlock;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.shore.LampThrall;
import life.shizune.mistinn.shore.SaltCorpse;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.Structures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 燈油: lamp thralls carry it, and it lights the golden bell halfway up a lighthouse, whose lamp
 * has gone out. Unlit, the bell takes no one; lit with oil, it takes the hunter who lit it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnLampOilTest implements FabricClientGameTest {
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
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("difficulty normal");
			BlockPos bell = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				player.setGameMode(GameType.CREATIVE);
				ServerLevel level = server.overworld();
				int x = player.getBlockX() + 40;
				int z = player.getBlockZ();
				level.getChunk(x >> 4, z >> 4);
				BlockPos floor = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
				Structures.lighthouse(new Builder(level, floor, RandomSource.create(9L)));
				level.getEntitiesOfClass(SaltCorpse.class, new AABB(floor).inflate(16.0)).forEach(SaltCorpse::discard);
				level.getEntitiesOfClass(Snuffer.class, new AABB(floor).inflate(40.0)).forEach(Snuffer::discard);
				BlockPos found = null;
				for (BlockPos at : BlockPos.betweenClosed(floor.offset(-4, 11, -4), floor.offset(4, 13, 4))) {
					if (level.getBlockState(at).is(MistinnBlocks.GOLDEN_BELL) && !level.getBlockState(at).getValue(GoldenBellBlock.OILED)) {
						found = at.immutable();
					}
				}
				check(found != null, "halfway up the lighthouse stands a golden bell whose lamp has gone out");
				player.teleportTo(level, found.getX() + 0.5, found.getY(), found.getZ() - 1.5, Set.of(), 0.0F, 30.0F, true);
				return found;
			});
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = server.overworld();
				player.setGameMode(GameType.SURVIVAL);
				player.removeAttached(MistinnAttachments.BELL);
				use(player, bell, ItemStack.EMPTY);
				check(!player.hasAttached(MistinnAttachments.BELL), "unlit, the bell takes no one");
				check(level.getBlockState(bell).getLightEmission() == 0, "and gives no light");
				ItemStack oil = new ItemStack(MistinnItems.LAMP_OIL, 2);
				use(player, bell, oil);
				check(level.getBlockState(bell).getValue(GoldenBellBlock.OILED), "lamp oil lights it");
				check(player.getMainHandItem().getCount() == 1, "one flask of oil is used");
				check(GlobalPos.of(level.dimension(), bell).equals(player.getAttached(MistinnAttachments.BELL)), "and the hunter who lit it rests there");
				check(level.getBlockState(bell).getLightEmission() > 0, "lit, it gives its light");
				player.setGameMode(GameType.CREATIVE);
			});

			// lamp thralls carry the oil: some of them drop it, not all
			int[] dropped = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = server.overworld();
				int flasks = 0;
				for (int i = 0; i < 40; i++) {
					LampThrall thrall = MistinnEntities.LAMP_THRALL.create(level, EntitySpawnReason.COMMAND);
					thrall.setNoAi(true);
					thrall.snapTo(player.getX() + 3.0, player.getY(), player.getZ(), 0.0F, 0.0F);
					level.addFreshEntity(thrall);
					thrall.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
					for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, thrall.getBoundingBox().inflate(3.0))) {
						if (item.getItem().is(MistinnItems.LAMP_OIL)) {
							flasks += item.getItem().getCount();
						}
						item.discard();
					}
					thrall.discard();
				}
				return new int[] {flasks};
			});
			check(dropped[0] > 0 && dropped[0] < 40, "some lamp thralls drop their oil, not every one: " + dropped[0] + " of 40");
		}
	}

	private static void use(final ServerPlayer player, final BlockPos pos, final ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
		player.gameMode.useItemOn(player, player.level(), stack, InteractionHand.MAIN_HAND, hit);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
