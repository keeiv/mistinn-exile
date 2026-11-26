package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.CrystalDrake;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Crystal drakes: one takes the air and circles, stoops on a hunter and bites; its facets glance
 * shots and crack under a greatsword; with no quarry it comes down to rest and rises when someone
 * comes; brought down, it shatters into crystal grit and is remembered.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnCrystalDrakeTest implements FabricClientGameTest {
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
			world.getServer().runOnServer(server -> player(server).addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 3, false, false)));
			context.waitTicks(20);

			CrystalDrake[] drake = new CrystalDrake[1];
			double[] start = new double[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				drake[0] = MistinnEntities.CRYSTAL_DRAKE.spawn(level, player.blockPosition().offset(12, 1, 0), EntitySpawnReason.COMMAND);
				drake[0].setPersistenceRequired();
				start[0] = drake[0].getY();
			});

			// it takes the air
			context.waitTicks(80);
			world.getServer().runOnServer(server -> {
				check(drake[0].isAloft(), "the drake is aloft");
				check(drake[0].getY() > start[0] + 3.0, "and has climbed: " + (drake[0].getY() - start[0]));
			});
			photo(context, world, drake[0], 9.0, "drake-flight");

			// it stoops on the hunter and bites
			boolean bitten = false;
			for (int i = 0; i < 80 && !bitten; i++) {
				context.waitTicks(10);
				bitten = world.getServer().computeOnServer(server -> player(server).getHealth() < player(server).getMaxHealth());
			}
			check(bitten, "the drake dives and bites");

			// its facets
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.BELL_GREATSWORD));
				check(CrystalDrake.facets(level.damageSources().playerAttack(player)) == CrystalDrake.CRACK, "a greatsword cracks its facets");
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				check(CrystalDrake.facets(level.damageSources().playerAttack(player)) == 1.0F, "a fist does what it does");
				Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
				check(CrystalDrake.facets(level.damageSources().arrow(arrow, player)) == CrystalDrake.GLANCE, "an arrow glances off");
			});

			// alone, it comes down to rest
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runOnServer(server -> {
				CrystalDrake.restless = 20;
				drake[0].setTarget(null);
			});
			boolean resting = false;
			for (int i = 0; i < 60 && !resting; i++) {
				context.waitTicks(10);
				resting = world.getServer().computeOnServer(server -> drake[0].mode() == CrystalDrake.Mode.REST && drake[0].onGround());
			}
			check(resting, "with no one to hunt it comes down to rest");
			context.waitTicks(30);
			photo(context, world, drake[0], 7.0, "drake-rest");

			// someone comes: it rises
			world.getServer().runCommand("gamemode survival @a");
			boolean rising = false;
			for (int i = 0; i < 20 && !rising; i++) {
				context.waitTicks(10);
				rising = world.getServer().computeOnServer(server -> drake[0].isAloft() && drake[0].mode() != CrystalDrake.Mode.REST);
			}
			check(rising, "a hunter near and it takes the air again");

			// brought down: crystal grit, and remembered
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				drake[0].hurtServer(level, level.damageSources().playerAttack(player), 10000.0F);
				check(!drake[0].isAlive(), "the drake falls");
				check(AchievementService.has(player, Achievement.DRAKE_SHATTERED), "shattering one is remembered");
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				int grit = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(drake[0].blockPosition()).inflate(6.0),
					e -> e.getItem().is(MistinnItems.CRYSTAL_DUST)).stream().mapToInt(e -> e.getItem().getCount()).sum();
				check(grit >= 2, "it shatters into crystal grit: " + grit);
			});
		} finally {
			CrystalDrake.restless = CrystalDrake.RESTLESS;
		}
	}

	/** Holds the drake still a moment and looks at it from {@code reach} blocks off, a little below. */
	private static void photo(final ClientGameTestContext context, final TestSingleplayerContext world, final CrystalDrake drake, final double reach,
		final String name) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			drake.setNoAi(true);
			Vec3 at = drake.position();
			Vec3 from = new Vec3(at.x - reach * 0.8, Math.max(player.getY(), at.y - 2.0), at.z - reach * 0.6);
			double dx = at.x - from.x;
			double dz = at.z - from.z;
			double dy = at.y + 0.8 - (from.y + 1.62);
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
			player.teleportTo(server.overworld(), from.x, from.y, from.z, Set.of(), yaw, pitch, true);
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
		});
		context.waitTicks(20);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("drake")));
		world.getServer().runOnServer(server -> drake.setNoAi(false));
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
