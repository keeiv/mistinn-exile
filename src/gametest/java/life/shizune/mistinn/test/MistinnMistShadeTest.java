package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.enemy.EnemyRig;
import life.shizune.mistinn.enemy.WindupService;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.shore.MistShade;
import life.shizune.mistinn.shore.SaltCorpse;
import life.shizune.mistinn.shore.ShoreSpawner;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.Structures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * 霧影: only ever met in a lighthouse's mist, thinning away out of it; its touch blinds. As the
 * keeper's room goes dark, the keeper calls two up beside it, one blow each.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnMistShadeTest implements FabricClientGameTest {

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
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("difficulty normal");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			world.getServer().runOnServer(server -> check(EnemyRig.of(MistinnEntities.MIST_SHADE) == EnemyRig.UNDEAD, "it moves as the undead do"));

			// out of any mist: it thins away
			int lost = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				player.setGameMode(GameType.CREATIVE);
				MistShade shade = MistinnEntities.MIST_SHADE.create(server.overworld(), EntitySpawnReason.COMMAND);
				shade.snapTo(player.getX() + 4.0, player.getY(), player.getZ(), 0.0F, 0.0F);
				server.overworld().addFreshEntity(shade);
				return shade.getId();
			});
			context.waitTicks(MistShade.FADE_TICKS + 45);
			world.getServer().runOnServer(server -> check(server.overworld().getEntity(lost) == null, "out of the mist, a shade thins away to nothing"));

			BlockPos lighthouse = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = server.overworld();
				int x = player.getBlockX() + 260;
				int z = player.getBlockZ();
				level.getChunk(x >> 4, z >> 4);
				BlockPos floor = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
				BlockPos room = Structures.lighthouse(new Builder(level, floor, RandomSource.create(5L)));
				level.getEntitiesOfClass(SaltCorpse.class, new AABB(floor).inflate(16.0)).forEach(SaltCorpse::discard);
				return room;
			});
			context.waitTicks(4);

			// in the mist it stays, and its touch blinds
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = server.overworld();
				int x = lighthouse.getX() - 30;
				int z = lighthouse.getZ();
				level.getChunk(x >> 4, z >> 4);
				player.teleportTo(level, x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, Set.of(), -90.0F, 0.0F, true);
			});
			// the land there is held by the hunter now, so what is set down there stays loaded
			context.waitTicks(20);
			java.util.UUID held = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = server.overworld();
				player.setGameMode(GameType.SURVIVAL);
				MistShade shade = MistinnEntities.MIST_SHADE.create(level, EntitySpawnReason.COMMAND);
				shade.snapTo(player.getX() + 2.0, player.getY(), player.getZ(), 90.0F, 0.0F);
				level.addFreshEntity(shade);
				return shade.getUUID();
			});
			context.waitTicks(MistShade.FADE_TICKS + 45);
			context.takeScreenshot("shade-01-in-the-mist");
			world.getServer().runOnServer(server -> {
				MistShade shade = server.overworld().getEntity(held) instanceof MistShade found ? found : null;
				check(shade != null && shade.isAlive() && !shade.fading(), "in the mist, a shade stays");
				ServerPlayer player = player(server);
				player.removeEffect(MobEffects.BLINDNESS);
				check(WindupService.land(server.overworld(), shade, player), "its blow lands");
				check(player.hasEffect(MobEffects.BLINDNESS), "and its touch takes the hunter's sight");
				player.removeEffect(MobEffects.BLINDNESS);
				shade.discard();
				player.setGameMode(GameType.CREATIVE);
			});

			// the keeper's room goes dark: two shades come up beside it, one blow each
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				Snuffer keeper = level.getEntitiesOfClass(Snuffer.class, new AABB(lighthouse).inflate(16.0)).getFirst();
				player(server).teleportTo(level, lighthouse.getX() + 0.5, lighthouse.getY(), lighthouse.getZ() - 4.5, Set.of(), 0.0F, 0.0F, true);
				keeper.setHealth(keeper.getMaxHealth() / 2.0F - 1.0F);
			});
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				Snuffer keeper = level.getEntitiesOfClass(Snuffer.class, new AABB(lighthouse).inflate(16.0)).getFirst();
				check(keeper.dark(), "below half, the room goes dark");
				List<MistShade> clones = level.getEntitiesOfClass(MistShade.class, new AABB(lighthouse).inflate(12.0), s -> s.entityTags().contains(MistShade.CLONE_TAG));
				check(clones.size() == 2, "and two shades come up beside the keeper: " + clones.size());
				check(clones.stream().allMatch(s -> s.getMaxHealth() <= 1.0F), "frail as breath");
				clones.forEach(MistShade::discard);
				keeper.discard();
			});

			// the mist brings shades up too
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = server.overworld();
				int x = lighthouse.getX() - 50;
				int z = lighthouse.getZ();
				level.getChunk(x >> 4, z >> 4);
				player.teleportTo(level, x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, Set.of(), 0.0F, 0.0F, true);
				player.setGameMode(GameType.SURVIVAL);
			});
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				boolean brought = false;
				for (int i = 0; i < 20 && !brought; i++) {
					brought = ShoreSpawner.tryOnce(player, MistinnEntities.MIST_SHADE) instanceof MistShade;
				}
				check(brought, "the mist brings shades up");
				player.setGameMode(GameType.CREATIVE);
			});
		}
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
