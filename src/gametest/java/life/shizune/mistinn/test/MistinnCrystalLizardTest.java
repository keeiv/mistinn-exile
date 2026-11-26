package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.CrystalLizard;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The crystal lizards: left alone they lie still like an outcrop; a hunter a fair way off does not
 * wake them, one who comes close wakes the whole pack; a bite wears every piece of armour; poison
 * does nothing to them; they come of themselves on open ground, though not where it is crowded;
 * and they shatter into crystal grit.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnCrystalLizardTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set day");
			context.waitTicks(20);

			// three lizards, settled flat on the ground a way off
			List<CrystalLizard> pack = new ArrayList<>();
			Vec3[] where = new Vec3[3];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				for (int i = 0; i < 3; i++) {
					BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(12 + i * 2, 0, i * 2 - 2));
					CrystalLizard lizard = MistinnEntities.CRYSTAL_LIZARD.spawn(level, at, EntitySpawnReason.COMMAND);
					lizard.setPersistenceRequired();
					lizard.lieStill(2000);
					pack.add(lizard);
					where[i] = lizard.position();
				}
			});
			context.waitTicks(60);
			world.getServer().runOnServer(server -> {
				for (int i = 0; i < 3; i++) {
					CrystalLizard lizard = pack.get(i);
					check(lizard.isStill(), "the lizard lies still");
					check(lizard.position().distanceTo(where[i]) < 0.6, "and does not wander: " + lizard.position().distanceTo(where[i]));
				}
			});
			photo(context, world, pack.get(1));

			// a hunter a fair way off does not wake them
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 2, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 3, false, false));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				Vec3 at = pack.get(0).position().add(-11.0, 0.0, 0.0);
				BlockPos ground = server.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
				player.teleportTo(server.overworld(), at.x, ground.getY(), at.z, Set.of(), -90.0F, 0.0F, true);
			});
			context.waitTicks(60);
			world.getServer().runOnServer(server -> {
				for (CrystalLizard lizard : pack) {
					check(lizard.isStill() && lizard.getTarget() == null, "a hunter eleven blocks off does not wake them");
				}
			});

			// one who comes close wakes the whole pack
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				Vec3 at = pack.get(0).position().add(-4.5, 0.0, 0.0);
				BlockPos ground = server.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
				player.teleportTo(server.overworld(), at.x, ground.getY(), at.z, Set.of(), -90.0F, 0.0F, true);
			});
			boolean woke = false;
			for (int i = 0; i < 30 && !woke; i++) {
				context.waitTicks(5);
				woke = world.getServer().computeOnServer(server -> pack.stream().allMatch(l -> !l.isStill() && l.getTarget() == player(server)));
			}
			String how = world.getServer().computeOnServer(server -> {
				StringBuilder out = new StringBuilder();
				for (CrystalLizard l : pack) {
					out.append(String.format(" [still=%s target=%s d=%.1f]", l.isStill(), l.getTarget() == null ? "none" : l.getTarget().getName().getString(),
						l.distanceTo(player(server))));
				}
				return out.toString();
			});
			check(woke, "the whole pack springs at the hunter who came close:" + how);

			// a bite wears the armour
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
				int before = chest.getDamageValue();
				player.setInvulnerableTime(0);
				pack.get(0).doHurtTarget(level, player);
				int worn = player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() - before;
				check(worn >= CrystalLizard.WEAR, "a bite wears the chestplate at least " + CrystalLizard.WEAR + ": " + worn);
			});

			// lifeless: poison does nothing
			world.getServer().runOnServer(server -> {
				CrystalLizard lizard = pack.get(1);
				lizard.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
				check(!lizard.hasEffect(MobEffects.POISON), "poison does nothing to it");
			});

			// they come of themselves on open ground, not where it is crowded
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos open = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player(server).blockPosition().offset(-120, 0, 60));
				boolean any = false;
				for (int i = 0; i < 40 && !any; i++) {
					any = CrystalLizard.checkSpawnRules(MistinnEntities.CRYSTAL_LIZARD, level, EntitySpawnReason.NATURAL, open, level.getRandom());
				}
				check(any, "they may come of themselves on open ground, by day");
				BlockPos crowded = pack.get(0).blockPosition();
				for (int i = 0; i < 7; i++) {
					CrystalLizard extra = MistinnEntities.CRYSTAL_LIZARD.spawn(level, crowded.offset(i - 3, 0, 4), EntitySpawnReason.COMMAND);
					extra.setNoAi(true);
				}
				boolean crowd = false;
				for (int i = 0; i < 40; i++) {
					crowd |= CrystalLizard.checkSpawnRules(MistinnEntities.CRYSTAL_LIZARD, level, EntitySpawnReason.NATURAL, crowded, level.getRandom());
				}
				check(!crowd, "but not where nine already keep the stone");
			});

			// they shatter into crystal grit
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				CrystalLizard lizard = pack.get(2);
				lizard.hurtServer(level, level.damageSources().playerAttack(player(server)), 10000.0F);
				check(!lizard.isAlive(), "the lizard shatters");
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				int grit = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(pack.get(2).blockPosition()).inflate(4.0),
					e -> e.getItem().is(MistinnItems.CRYSTAL_DUST)).stream().mapToInt(e -> e.getItem().getCount()).sum();
				check(grit >= 1, "into crystal grit: " + grit);
			});
		}
	}

	/** Looks at the still pack from a few blocks off, a little above. */
	private static void photo(final ClientGameTestContext context, final TestSingleplayerContext world, final CrystalLizard lizard) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			Vec3 at = lizard.position();
			Vec3 from = new Vec3(at.x - 2.6, at.y + 1.0, at.z - 2.0);
			double dx = at.x - from.x;
			double dz = at.z - from.z;
			double dy = at.y + 0.3 - (from.y + 1.62);
			player.teleportTo(server.overworld(), from.x, from.y, from.z, Set.of(), (float) Math.toDegrees(Math.atan2(-dx, dz)),
				(float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), true);
		});
		context.waitTicks(20);
		context.takeScreenshot(TestScreenshotOptions.of("lizards").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("terra")));
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
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
