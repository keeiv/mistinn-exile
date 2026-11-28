package life.shizune.mistinn.test;

import java.util.HashSet;
import java.util.Set;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.skill.Masteries;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.SkillTree;
import life.shizune.mistinn.skill.Skills;
import life.shizune.mistinn.skill.Stat;
import life.shizune.mistinn.terra.CrystalDrake;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The four later major skills, each closing a row after its last minor node: 千鈞 (strength), 初心本我 (speed),
 * 久經沙場 (constitution), 裝備大師 (intellect). They add no stat point. Hardened, a blow does less; a gear master's helmet and chest piece wear half,
 * legs and boots a quarter less; one as they began guards a little longer and ripostes off a blow turned aside. And the
 * crystal drakes come of themselves far more rarely: never within reach of another.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnMasteriesTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			context.waitTicks(20);
			Object[] cowAt = new Object[2];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				// the tree
				for (String id : new String[] {SkillTree.THOUSAND_WEIGHT, SkillTree.TRUE_SELF, SkillTree.BATTLE_HARDENED, SkillTree.GEAR_MASTER}) {
					SkillTree.Node node = SkillTree.node(id);
					check(node != null && node.major() && node.parent().equals(node.stat().id + "_" + SkillTree.BRANCH), id + " closes its row");
				}
				Set<String> all = new HashSet<>();
				SkillTree.NODES.forEach(node -> all.add(node.id()));
				Skills everything = new Skills(all);
				check(everything.stat(Stat.SPEED) == SkillTree.BRANCH && everything.minorCount() == SkillTree.BRANCH * Stat.values().length,
					"a major node adds no point to its row's stat");
				SkillService.set(player, new Skills(Set.of(SkillTree.ROOT)));
				check(SkillService.unlock(player, SkillTree.BATTLE_HARDENED) == SkillService.Result.LOCKED_PARENT, "not before the row's last minor node");
				Set<String> row = new HashSet<>(Set.of(SkillTree.ROOT));
				for (int i = 1; i <= SkillTree.BRANCH; i++) {
					row.add(Stat.CONSTITUTION.id + "_" + i);
				}
				SkillService.set(player, new Skills(row));
				check(SkillService.unlock(player, SkillTree.BATTLE_HARDENED) == SkillService.Result.NO_RUST_SALT, "and it takes a rust god salt");

				// 千鈞
				check(Masteries.heavyCost(player) == 1.0F && Masteries.heavyBlow(player, 1.0F) == 1.0F, "without it, a heavy blow is as it was");
				SkillService.set(player, new Skills(Set.of(SkillTree.ROOT, SkillTree.THOUSAND_WEIGHT)));
				check(Masteries.heavyCost(player) == Masteries.HEAVY_COST, "a thousand weight: a heavy blow costs less breath");
				check(Masteries.heavyBlow(player, 1.0F) == Masteries.FULL_BLOW && Masteries.heavyBlow(player, 0.6F) == 1.0F,
					"and only one charged all the way lands harder");

				// 久經沙場
				check(Masteries.hardened(player) == 1.0F, "unhardened, a blow is what it is");
				SkillService.set(player, new Skills(Set.of(SkillTree.ROOT, SkillTree.BATTLE_HARDENED)));
				check(Masteries.hardened(player) == Masteries.HARDENED, "hardened, it does less");

				// 裝備大師
				check(Masteries.units(2.5F, 0.4F) == 3 && Masteries.units(2.5F, 0.6F) == 2 && Masteries.units(0.5F, 0.9F) == 0, "a share of wear rounds as often as it comes to");
				int[] plain = wear(level, player, false);
				int[] master = wear(level, player, true);
				check(plain[0] == 10 && plain[1] == 10, "unskilled, a heavy blow wears the helmet and the boots alike: " + plain[0] + " " + plain[1]);
				check(master[0] == 5, "a gear master's helmet wears half: " + master[0]);
				check(master[1] >= 7 && master[1] <= 8, "and their boots a quarter less: " + master[1]);

				// 初心本我
				SkillService.set(player, new Skills(Set.of(SkillTree.ROOT)));
				check(Masteries.perfectGuard(player, 5) == 5, "the guard as it is");
				Masteries.parried(player);
				check(!Masteries.riposteReady(player), "and no riposte");
				SkillService.set(player, new Skills(Set.of(SkillTree.ROOT, SkillTree.TRUE_SELF)));
				check(Masteries.perfectGuard(player, 5) == 5 + Masteries.GUARD_LONGER, "as they began, the guard turns a blow aside a little later");
				Masteries.parried(player);
				check(Masteries.riposteReady(player), "a blow turned aside readies the riposte");
				Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.EVENT);
				cow.snapTo(player.getX() + 2.0, player.getY(), player.getZ(), 0.0F, 0.0F);
				cow.setNoAi(true);
				level.addFreshEntity(cow);
				cowAt[0] = cow;
				cowAt[1] = cow.getHealth();
				cow.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
			});
			context.waitTicks(2);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Cow cow = (Cow) cowAt[0];
				float before = (Float) cowAt[1];
				float dealt = before - cow.getHealth();
				check(Math.abs(dealt - 4.0F * (1.0F + Masteries.RIPOSTE)) < 0.01F, "the riposte goes in deeper: " + dealt);
				check(!Masteries.riposteReady(player), "once");
				cow.discard();
				Cow fresh = EntityTypes.COW.create(level, EntitySpawnReason.EVENT);
				fresh.snapTo(player.getX() + 2.0, player.getY(), player.getZ(), 0.0F, 0.0F);
				fresh.setNoAi(true);
				level.addFreshEntity(fresh);
				cowAt[0] = fresh;
				cowAt[1] = fresh.getHealth();
				fresh.hurtServer(level, level.damageSources().playerAttack(player), 2.0F);
			});
			context.waitTicks(2);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Cow cow = (Cow) cowAt[0];
				check(Math.abs((Float) cowAt[1] - cow.getHealth() - 2.0F) < 0.01F, "the next cut is a cut");
				cow.discard();

				// the drakes: never within reach of another
				BlockPos sky = player.blockPosition().atY(200);
				CrystalDrake drake = MistinnEntities.CRYSTAL_DRAKE.create(level, EntitySpawnReason.EVENT);
				drake.snapTo(sky.getX() + 30.5, 200.0, sky.getZ() + 0.5, 0.0F, 0.0F);
				drake.setNoAi(true);
				level.addFreshEntity(drake);
				RandomSource random = RandomSource.create(3L);
				int near = 0;
				for (int i = 0; i < 200; i++) {
					near += CrystalDrake.checkSpawnRules(MistinnEntities.CRYSTAL_DRAKE, level, EntitySpawnReason.NATURAL, sky, random) ? 1 : 0;
				}
				check(near == 0, "no drake comes of itself within reach of another: " + near);
				drake.discard();
				int alone = 0;
				for (int i = 0; i < 600; i++) {
					alone += CrystalDrake.checkSpawnRules(MistinnEntities.CRYSTAL_DRAKE, level, EntitySpawnReason.NATURAL, sky, random) ? 1 : 0;
				}
				check(alone > 0 && alone < 600 / 6, "and alone, only now and then: " + alone + " of 600");
			});
		}
	}

	/** What a heavy blow (forty) wears from a fresh diamond helmet and boots, unskilled or as a gear master. */
	private static int[] wear(final ServerLevel level, final ServerPlayer player, final boolean master) {
		SkillService.set(player, new Skills(master ? Set.of(SkillTree.ROOT, SkillTree.GEAR_MASTER) : Set.of(SkillTree.ROOT)));
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		Masteries.wear(player, level.damageSources().generic(), 40.0F, EquipmentSlot.HEAD, EquipmentSlot.FEET);
		int[] worn = {player.getItemBySlot(EquipmentSlot.HEAD).getDamageValue(), player.getItemBySlot(EquipmentSlot.FEET).getDamageValue()};
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		return worn;
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn masteries] ok: " + what);
	}
}
