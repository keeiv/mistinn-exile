package life.shizune.mistinn.test;

import java.util.List;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.WoundService;
import life.shizune.mistinn.elite.Affix;
import life.shizune.mistinn.elite.EliteService;
import life.shizune.mistinn.enemy.ExecutionService;
import life.shizune.mistinn.salt.SaltService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Elites: a creature that comes of itself may be one (and one summoned never is); each affix does
 * what it says (body, bleeding, the fog step, the burst, the rally); a hulking one does not begin a
 * finisher; a fighting elite's life shows on a bar; brought down it pays its affixes' salt and the
 * achievements.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnEliteTest implements FabricClientGameTest {
	private static final double EPSILON = 1.0E-4;

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
			world.getServer().runCommand("time set midnight");
			world.getServer().runOnServer(MistinnEliteTest::platform);
			context.waitTicks(40);
			world.getConnection().waitForChunksRender();

			// the odds: more by night, more far out
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos spawn = level.getRespawnData().pos();
				check(Math.abs(EliteService.chance(level, spawn) - 0.05F) < EPSILON, "5% by night at spawn: " + EliteService.chance(level, spawn));
				check(Math.abs(EliteService.chance(level, spawn.offset(2500, 0, 0)) - 0.09F) < EPSILON, "and 9% far out");
			});

			// one that comes of itself is an elite when the chance comes up; one summoned never is
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				EliteService.certain = true;
				EntityTypes.SKELETON.spawn(level, player.blockPosition().offset(6, 0, 6), EntitySpawnReason.NATURAL);
				EntityTypes.SKELETON.spawn(level, player.blockPosition().offset(-6, 0, 6), EntitySpawnReason.COMMAND);
			});
			context.waitTicks(2);
			world.getServer().runOnServer(server -> {
				EliteService.certain = false;
				ServerPlayer player = player(server);
				List<Skeleton> skeletons = server.overworld().getEntitiesOfClass(Skeleton.class, player.getBoundingBox().inflate(12.0));
				check(skeletons.size() == 2, "two skeletons: " + skeletons.size());
				Skeleton natural = skeletons.stream().filter(s -> s.getX() > player.getX()).findFirst().orElseThrow();
				Skeleton summoned = skeletons.stream().filter(s -> s.getX() < player.getX()).findFirst().orElseThrow();
				int affixes = EliteService.affixes(natural).size();
				check(EliteService.isElite(natural) && affixes >= 1 && affixes <= 3, "the skeleton that came of itself is an elite: " + affixes);
				check(natural.getHealth() == natural.getMaxHealth() && natural.getMaxHealth() >= 30.0F, "with half again its life, full: " + natural.getMaxHealth());
				check(natural.getCustomName() != null && natural.getCustomName().getContents() instanceof TranslatableContents name
					&& name.getKey().equals("elite.mistinn.name"), "and its name: " + natural.getCustomName());
				check(!EliteService.isElite(summoned), "the summoned one is not");
				skeletons.forEach(s -> s.discard());
			});

			// the body: swift, ironclad and hulking at once
			int[] zombieId = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Zombie zombie = zombie(level, player, 3.0, 0.0, false);
				EliteService.make(zombie, List.of(Affix.SWIFT, Affix.IRONCLAD, Affix.HULKING));
				EliteService.make(zombie, List.of(Affix.SWIFT, Affix.IRONCLAD, Affix.HULKING));
				check(Math.abs(zombie.getMaxHealth() - 46.0F) < EPSILON && zombie.getHealth() == zombie.getMaxHealth(),
					"20 life, half again and four fifths again, made once: " + zombie.getMaxHealth());
				check(Math.abs(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.23 * 1.2) < EPSILON, "a fifth faster on the whole");
				check(Math.abs(zombie.getAttributeValue(Attributes.ARMOR) - 10.0) < EPSILON, "8 more armour");
				check(Math.abs(zombie.getAttributeValue(Attributes.SCALE) - 1.3) < EPSILON, "a third larger");
				zombie.setTarget(player);
				zombieId[0] = zombie.getId();
			});
			context.waitTicks(25);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				Zombie zombie = (Zombie) server.overworld().getEntity(zombieId[0]);
				ServerBossEvent bar = EliteService.bar(zombie);
				check(bar != null && bar.getPlayers().contains(player), "an elite after a hunter shows its life on a bar");
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
			});
			check(context.computeOnClient(mc -> mc.level.getEntity(zombieId[0]) != null && mc.level.getEntity(zombieId[0]).hasAttached(EliteService.ELITE)),
				"the client knows it for an elite");
			context.waitTicks(5);
			context.takeScreenshot("mistinn-elite");

			// a hulking elite does not begin a finisher where a plain zombie would
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BodyService.set(player, BodyService.get(player).with(BodyPart.LEFT_LEG, 0.0F).with(BodyPart.RIGHT_LEG, 0.0F));
				Zombie big = (Zombie) level.getEntity(zombieId[0]);
				big.setNoAi(false);
				big.snapTo(player.getX(), player.getY(), player.getZ() + 1.2, 180.0F, 0.0F);
				check(!ExecutionService.begins(player, level.damageSources().mobAttack(big)), "a hulking elite does not begin a finisher");
				big.discard();
				Zombie plain = zombie(level, player, 1.2, 0.0, true);
				check(ExecutionService.begins(player, level.damageSources().mobAttack(plain)), "where a plain zombie would");
				ExecutionService.release(player);
				plain.discard();
				BodyService.restoreAll(player);
			});
			context.waitTicks(30);

			// bloodthirsty: its blow bleeds and heals it
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.removeEffect(MobEffects.RESISTANCE);
				WoundService.clearAll(player);
				Zombie zombie = zombie(level, player, 1.5, 0.0, false);
				EliteService.make(zombie, List.of(Affix.BLOODTHIRSTY));
				zombie.setHealth(10.0F);
				player.hurtServer(level, level.damageSources().mobAttack(zombie), 4.0F);
				boolean bleeding = false;
				for (BodyPart part : BodyPart.values()) {
					bleeding |= WoundService.get(player).get(part).bleeding();
				}
				check(bleeding, "a bloodthirsty elite's blow bleeds");
				check(zombie.getHealth() > 10.0F, "and heals it: " + zombie.getHealth());
				zombie.discard();
				WoundService.clearAll(player);
				BodyService.restoreAll(player);
			});

			// mistwalker: struck, it steps out of the fog beside or behind the hunter
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Zombie zombie = zombie(level, player, 2.5, 0.0, false);
				EliteService.make(zombie, List.of(Affix.MISTWALKER));
				Vec3 before = zombie.position();
				EliteService.certain = true;
				zombie.hurtServer(level, level.damageSources().playerAttack(player), 2.0F);
				EliteService.certain = false;
				Vec3 toward = zombie.position().subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize();
				Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
				check(zombie.isAlive() && zombie.position().distanceTo(before) > 1.5, "it went into the fog: " + before + " to " + zombie.position());
				check(toward.dot(look) < 0.5, "and came out beside or behind: " + toward.dot(look));
				zombie.discard();
			});

			// warchief: its kind round it go faster, harder, and after its quarry
			int[] band = new int[2];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Zombie chief = zombie(level, player, 4.0, 0.0, false);
				EliteService.make(chief, List.of(Affix.WARCHIEF));
				chief.setTarget(player);
				band[0] = zombie(level, player, 6.0, 3.0, false).getId();
				band[1] = zombie(level, player, 6.0, -3.0, false).getId();
			});
			context.waitTicks(45);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				for (int id : band) {
					Zombie zombie = (Zombie) level.getEntity(id);
					check(zombie.hasEffect(MobEffects.SPEED) && zombie.hasEffect(MobEffects.STRENGTH), "the band is driven on");
					check(zombie.getTarget() == player, "and after the warchief's quarry");
				}
				level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(16.0)).forEach(z -> z.discard());
			});

			// brought down: the salt of its kind times its affixes and one, and the achievements
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Zombie zombie = zombie(level, player, 3.0, 0.0, false);
				EliteService.make(zombie, List.of(Affix.SWIFT, Affix.IRONCLAD, Affix.HULKING));
				check(SaltService.rewardFor(zombie) == 46 * 4, "46 life times four: " + SaltService.rewardFor(zombie));
				java.util.Set<Achievement> had = java.util.EnumSet.noneOf(Achievement.class);
				for (Achievement achievement : Achievement.values()) {
					if (AchievementService.has(player, achievement.id)) {
						had.add(achievement);
					}
				}
				int before = SaltService.get(player);
				zombie.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
				// the kill pays its salt, and every achievement it earned pays its own
				int earned = 0;
				for (Achievement achievement : Achievement.values()) {
					if (!had.contains(achievement) && AchievementService.has(player, achievement.id)) {
						earned += achievement.reward;
					}
				}
				int gained = SaltService.get(player) - before;
				check(!zombie.isAlive() && gained - earned == 46 * 4, "the kill paid " + (gained - earned) + " besides " + earned + " for achievements");
				check(AchievementService.has(player, AchievementService.ELITE_SLAIN), "an elite hunter");
				check(AchievementService.has(player, AchievementService.ELITE_TRIPLE), "and a triple threat");
			});

			// volatile: a breath after it dies, it bursts
			float[] body = new float[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BodyService.restoreAll(player);
				body[0] = BodyService.get(player).total();
				Zombie zombie = zombie(level, player, 1.5, 0.0, false);
				EliteService.make(zombie, List.of(Affix.VOLATILE));
				zombie.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
				check(EliteService.bursting() == 1, "it will burst");
			});
			context.waitTicks(10);
			check(world.getServer().computeOnServer(server -> EliteService.bursting() == 1 && BodyService.get(player(server)).total() == body[0]),
				"not yet");
			context.waitTicks(30);
			world.getServer().runOnServer(server -> {
				check(EliteService.bursting() == 0, "it burst");
				check(BodyService.get(player(server)).total() < body[0], "and it hurt: " + body[0] + " to " + BodyService.get(player(server)).total());
			});
		} finally {
			EliteService.certain = false;
		}
	}

	/** A stone floor high in the air, the hunter in the middle of it facing south, hurt but never killed. */
	private static void platform(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos centre = new BlockPos(spawn.getX(), 200, spawn.getZ());
		for (int dx = -12; dx <= 12; dx++) {
			for (int dz = -12; dz <= 12; dz++) {
				level.setBlock(centre.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
			}
		}
		ServerPlayer player = player(server);
		player.teleportTo(level, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
	}

	/** A grown zombie {@code ahead} blocks before the hunter and {@code side} to their right, facing them. */
	private static Zombie zombie(final ServerLevel level, final ServerPlayer player, final double ahead, final double side, final boolean ai) {
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		Vec3 at = player.position().add(look.scale(ahead)).add(right.scale(side));
		zombie.snapTo(at.x, at.y, at.z, player.getYRot() + 180.0F, 0.0F);
		zombie.setNoAi(!ai);
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
