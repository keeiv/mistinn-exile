package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.CrystalDrake;
import life.shizune.mistinn.terra.CrystalElder;
import life.shizune.mistinn.terra.Elders;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The crown elder: it takes the air when a hunter comes near a highland's heart; it calls the
 * lattice up under its quarry; badly hurt it calls two drakes; brought down it leaves crystal
 * cores enough to wake a hub, is remembered, and the crown grows another later.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnCrystalElderTest implements FabricClientGameTest {
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
			int[] id = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 2, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 3, false, false));
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(30, 0, 0));
				id[0] = TerraService.settleAt(level, centre, 60).id();
			});

			// a hunter near the heart: the elder takes the air
			CrystalElder[] elder = new CrystalElder[1];
			for (int i = 0; i < 40 && elder[0] == null; i++) {
				context.waitTicks(20);
				elder[0] = world.getServer().computeOnServer(server -> {
					List<CrystalElder> found = server.overworld().getEntitiesOfClass(CrystalElder.class, new AABB(player(server).blockPosition()).inflate(120.0));
					return found.isEmpty() ? null : found.getFirst();
				});
			}
			check(elder[0] != null, "the crown elder takes the air");
			check(world.getServer().computeOnServer(server -> Elders.crown(server, id[0]).out()), "and the crown knows it is out");
			context.waitTicks(40);
			photo(context, world, elder[0]);

			// it calls the lattice up under its quarry
			boolean ringed = false;
			for (int i = 0; i < 60 && !ringed; i++) {
				context.waitTicks(10);
				ringed = world.getServer().computeOnServer(server -> !elder[0].rings().isEmpty());
			}
			check(ringed, "rings of light mark where the lattice will erupt");
			context.waitTicks(CrystalElder.ERUPT_WARNING + 5);
			check(world.getServer().computeOnServer(server -> elder[0].rings().isEmpty()), "and it erupts");

			// badly hurt, it calls two drakes
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				elder[0].hurtServer(level, level.damageSources().playerAttack(player(server)), elder[0].getMaxHealth() * 0.6F);
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				check(elder[0].called(), "badly hurt, it calls for help");
				long drakes = server.overworld().getEntitiesOfClass(CrystalDrake.class, new AABB(elder[0].blockPosition()).inflate(20.0),
					d -> !(d instanceof CrystalElder)).size();
				check(drakes >= 2, "two drakes come: " + drakes);
			});

			// brought down: cores, remembered, and the crown will grow another
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				elder[0].hurtServer(level, level.damageSources().playerAttack(player), 100000.0F);
				check(!elder[0].isAlive(), "the elder falls");
				check(AchievementService.has(player, Achievement.ELDER_SLAIN), "the crown is remembered");
				Elders.Crown crown = Elders.crown(server, id[0]);
				check(!crown.out() && crown.next() > level.getGameTime(), "and another will grow later");
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				int cores = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(elder[0].blockPosition()).inflate(8.0),
					e -> e.getItem().is(MistinnItems.CRYSTAL_CORE)).stream().mapToInt(e -> e.getItem().getCount()).sum();
				check(cores >= 3, "it leaves cores enough to wake a hub: " + cores);
			});
		}
	}

	private static void photo(final ClientGameTestContext context, final TestSingleplayerContext world, final CrystalElder elder) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			elder.setNoAi(true);
			Vec3 at = elder.position();
			Vec3 from = new Vec3(at.x - 14.0, at.y + 1.0, at.z - 10.0);
			double dx = at.x - from.x;
			double dz = at.z - from.z;
			double dy = at.y + 2.0 - (from.y + 1.62);
			player.teleportTo(server.overworld(), from.x, from.y, from.z, Set.of(), (float) Math.toDegrees(Math.atan2(-dx, dz)),
				(float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), true);
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
		});
		context.waitTicks(20);
		context.takeScreenshot(TestScreenshotOptions.of("elder").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("drake")));
		world.getServer().runOnServer(server -> {
			elder.setNoAi(false);
			ServerPlayer player = player(server);
			BlockPos ground = server.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition());
			player.teleportTo(server.overworld(), ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		});
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
