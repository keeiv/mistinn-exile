package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.enemy.NoiseService;
import life.shizune.mistinn.enemy.StaggerService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.tool.ThrowService;
import life.shizune.mistinn.tool.ThrownTool;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Throwables: a salt bomb thrown leaves the hand and flies; bursting, it staggers and hurts the
 * creatures near it and not those further off; a flare lights the ground where it lies and shows
 * the creatures in reach, and goes out when it has burned; a bell lure draws an idle creature.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnThrowablesTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		int burned = ThrowService.flareTicks;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set midnight");
			BlockPos[] floor = new BlockPos[1];
			world.getServer().runOnServer(server -> floor[0] = platform(server));
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();

			// thrown: one leaves the hand and flies
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.SALT_BOMB, 3));
				MistinnItems.SALT_BOMB.use(server.overworld(), player, InteractionHand.MAIN_HAND);
				check(player.getMainHandItem().getCount() == 2, "one bomb leaves the hand");
				check(!server.overworld().getEntitiesOfClass(ThrownTool.class, player.getBoundingBox().inflate(4.0)).isEmpty(), "and flies");
				check(player.getCooldowns().isOnCooldown(player.getMainHandItem()), "and the next waits a second");
			});

			// the burst: near creatures rocked back and hurt, a far one untouched
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Zombie near = zombie(level, floor[0].offset(2, 0, 6));
				Zombie far = zombie(level, floor[0].offset(-6, 0, 6));
				Vec3 at = Vec3.atBottomCenterOf(floor[0].offset(1, 0, 6));
				ThrowService.land(level, ThrowService.Kind.SALT_BOMB, at, floor[0].offset(1, 0, 6), null, player);
				check(StaggerService.staggered(near) && near.getHealth() < near.getMaxHealth(), "the near one is rocked back and hurt");
				check(!StaggerService.staggered(far) && far.getHealth() == far.getMaxHealth(), "the far one is untouched");
				check(!life.shizune.mistinn.achievement.AchievementService.has(player, life.shizune.mistinn.achievement.Achievement.SALT_BOMB_THREE), "one rocked back is not yet salt hail");
				near.discard();
				far.discard();
				// three at once: salt hail
				java.util.List<Zombie> three = java.util.List.of(zombie(level, floor[0].offset(2, 0, 6)), zombie(level, floor[0].offset(0, 0, 6)),
					zombie(level, floor[0].offset(1, 0, 7)));
				ThrowService.land(level, ThrowService.Kind.SALT_BOMB, at, floor[0].offset(1, 0, 6), null, player);
				check(life.shizune.mistinn.achievement.AchievementService.has(player, life.shizune.mistinn.achievement.Achievement.SALT_BOMB_THREE), "three rocked back by one bomb is salt hail");
				three.forEach(Zombie::discard);
			});

			// a flare: light where it lies, the creatures in reach shown, and out when it has burned
			ThrowService.flareTicks = 60;
			int[] shown = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				Zombie zombie = zombie(level, floor[0].offset(8, 0, 4));
				shown[0] = zombie.getId();
				BlockPos air = floor[0].offset(0, 0, 4);
				ThrowService.land(level, ThrowService.Kind.FLARE, Vec3.atBottomCenterOf(air), air, null, player(server));
				check(level.getBlockState(air).is(Blocks.LIGHT), "the flare lights the ground where it lies");
				check(zombie.hasEffect(MobEffects.GLOWING), "and shows the creature in reach");
				player(server).addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
			});
			context.waitTicks(20);
			context.takeScreenshot("mistinn-flare");
			context.waitTicks(60);
			world.getServer().runOnServer(server -> {
				check(!server.overworld().getBlockState(floor[0].offset(0, 0, 4)).is(Blocks.LIGHT), "burned out, it goes dark");
				server.overworld().getEntity(shown[0]).discard();
			});

			// a bell lure: an idle creature comes to look
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				Zombie idle = zombie(level, floor[0].offset(-8, 0, -8));
				idle.setNoAi(false);
				BlockPos air = floor[0].offset(4, 0, -4);
				ThrowService.land(level, ThrowService.Kind.BELL_LURE, Vec3.atBottomCenterOf(air), air, null, player(server));
				NoiseService.Heard heard = NoiseService.heard(idle);
				check(heard != null && heard.at().distanceTo(Vec3.atBottomCenterOf(air)) < 3.0, "the idle creature heard the bell: " + heard);
				check(ThrowService.lingering() >= 1, "and it keeps ringing");
			});
		} finally {
			ThrowService.flareTicks = burned;
		}
	}

	/** A stone floor high up, the hunter on it. */
	private static BlockPos platform(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		BlockPos centre = player.blockPosition().above(40).immutable();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-12, -1, -12), centre.offset(12, -1, 12))) {
			level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		}
		player.teleportTo(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, Set.of(), 0.0F, 30.0F, true);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
		return centre;
	}

	private static Zombie zombie(final ServerLevel level, final BlockPos at) {
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		zombie.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
		zombie.setNoAi(true);
		level.addFreshEntity(zombie);
		return zombie;
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
