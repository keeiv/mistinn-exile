package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.weapon.ElementArrow;
import life.shizune.mistinn.weapon.Shock;
import life.shizune.mistinn.weapon.SkeletonAmmo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * The blast, frost and shock arrows: arrows a bow can loose; the shock jolts the weapon out of a hand (a hunter's and a
 * creature's); the frost leaves one slowed and shivering; the blast goes off and breaks no block. And a skeleton
 * carrying such arrows looses them.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnArrowsTest implements FabricClientGameTest {
	private static final int Y = 200;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 6000");
			context.waitTicks(20);
			BlockPos[] at = new BlockPos[1];
			Zombie[] zombies = new Zombie[3];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
				at[0] = player.blockPosition().atY(Y);
				BlockPos p = at[0];
				for (int x = -12; x <= 12; x++) {
					for (int z = -12; z <= 12; z++) {
						level.setBlock(p.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
						for (int y = 0; y <= 5; y++) {
							level.setBlock(p.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
				for (ElementArrow.Kind kind : ElementArrow.Kind.values()) {
					check(new ItemStack(kind.item()).is(ItemTags.ARROWS), kind.id + " is an arrow a bow can loose");
				}
				// a hunter jolted lets go of their weapon
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
				Shock.jolt(level, player);
				check(player.getMainHandItem().isEmpty(), "jolted, the hunter's hand lets go");
				check(!level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3.0), e -> e.getItem().is(Items.IRON_SWORD)).isEmpty(),
					"and the sword lands nearby");
				// three zombies holding swords, still, in a row
				for (int i = 0; i < 3; i++) {
					zombies[i] = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
					zombies[i].snapTo(p.getX() + 0.5 + (i - 1) * 6, Y, p.getZ() + 6.5, 180.0F, 0.0F);
					zombies[i].setNoAi(true);
					zombies[i].setPersistenceRequired();
					zombies[i].setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
					level.addFreshEntity(zombies[i]);
				}
				// a pillar behind the blast's mark
				level.setBlock(p.offset(-6, 0, 9), Blocks.STONE.defaultBlockState(), 3);
				// loosed at them from a few blocks off (the hunter stands by, or the creatures would be gone, so far from anyone)
				player.teleportTo(level, p.getX() + 0.5, Y, p.getZ() - 2.5, Set.of(), 0.0F, 0.0F, true);
				ElementArrow.Kind[] kinds = {ElementArrow.Kind.BLAST, ElementArrow.Kind.FROST, ElementArrow.Kind.SHOCK};
				for (int i = 0; i < 3; i++) {
					ElementArrow arrow = new ElementArrow(kinds[i], player, level, new ItemStack(kinds[i].item()), new ItemStack(Items.BOW));
					arrow.setPos(zombies[i].getX(), zombies[i].getY() + 1.2, zombies[i].getZ() - 3.0);
					arrow.shoot(0.0, 0.0, 1.0, 2.5F, 0.0F);
					level.addFreshEntity(arrow);
				}
			});
			context.waitTicks(8);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				check(zombies[0].getHealth() < zombies[0].getMaxHealth() || !zombies[0].isAlive(), "the blast arrow goes off on what it strikes");
				check(level.getBlockState(at[0].offset(-6, 0, 9)).is(Blocks.STONE), "and breaks no block");
				check(zombies[1].hasEffect(MobEffects.SLOWNESS) && zombies[1].getTicksFrozen() > 0, "the frost arrow leaves it slowed and shivering");
				check(zombies[2].getMainHandItem().isEmpty(), "the shock arrow jolts the sword out of its hand");
				// a skeleton carrying frost arrows looses them
				Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.EVENT);
				skeleton.snapTo(at[0].getX() + 0.5, Y, at[0].getZ() - 8.5, 0.0F, 0.0F);
				skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
				level.addFreshEntity(skeleton);
				SkeletonAmmo.give(skeleton, ElementArrow.Kind.FROST);
				ServerPlayer player = player(server);
				player.teleportTo(level, at[0].getX() + 0.5, Y, at[0].getZ() - 1.5, Set.of(), 0.0F, 0.0F, true);
				skeleton.setTarget(player);
			});
			boolean loosed = false;
			for (int i = 0; i < 200 && !loosed; i++) {
				context.waitTick();
				loosed = world.getServer().computeOnServer(server -> !server.overworld().getEntitiesOfClass(ElementArrow.class,
					new AABB(at[0]).inflate(20.0), a -> a.kind() == ElementArrow.Kind.FROST && a.getOwner() instanceof Skeleton).isEmpty());
			}
			check(loosed, "a skeleton carrying frost arrows looses them");
			boolean frozen = false;
			for (int i = 0; i < 300 && !frozen; i++) {
				context.waitTick();
				frozen = world.getServer().computeOnServer(server -> player(server).getTicksFrozen() > 0 && player(server).hasEffect(MobEffects.SLOWNESS));
			}
			check(frozen, "and one finds the hunter: slowed and shivering");
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn arrows] ok: " + what);
	}
}
