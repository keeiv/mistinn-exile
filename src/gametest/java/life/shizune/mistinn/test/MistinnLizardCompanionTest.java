package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.CrystalLizard;
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
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * A lizard companion: crept up on crouching, a still lizard does not stir; given grit it is tamed;
 * then it does not go for its hunter, its hunter's blows pass it by, it goes for a creature that
 * hurts its hunter, it follows and slips to their side when left far behind, it stays when told,
 * and it remembers its hunter across a save.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnLizardCompanionTest implements FabricClientGameTest {
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
			context.waitTicks(20);
			BlockPos[] floor = new BlockPos[1];
			CrystalLizard[] lizard = new CrystalLizard[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 3, false, false));
				// open, level ground
				floor[0] = player.blockPosition().below();
				for (int x = -40; x <= 40; x++) {
					for (int z = -12; z <= 12; z++) {
						for (int y = 0; y <= 4; y++) {
							level.setBlockAndUpdate(floor[0].offset(x, y, z), (y == 0 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
						}
					}
				}
				player.teleportTo(level, floor[0].getX() + 0.5, floor[0].getY() + 1.0, floor[0].getZ() + 0.5, Set.of(), 90.0F, 0.0F, true);
			});
			// crouched first, as the hunter's own client says, and only then does the lizard lie there
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				check(player(server).isShiftKeyDown(), "the hunter crouches");
				lizard[0] = MistinnEntities.CRYSTAL_LIZARD.spawn(server.overworld(), floor[0].offset(-3, 1, 0), EntitySpawnReason.COMMAND);
				lizard[0].setPersistenceRequired();
				lizard[0].lieStill(2000);
			});
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				check(lizard[0].isStill() && lizard[0].getTarget() == null, "crept up on crouching, the still lizard does not stir");
			});

			// grit offered: tamed
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				CrystalLizard.tameOdds = 1;
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.CRYSTAL_DUST, 4));
				player.interactOn(lizard[0], InteractionHand.MAIN_HAND, lizard[0].position());
				CrystalLizard.tameOdds = 3;
				check(lizard[0].tamed() && player.getUUID().equals(lizard[0].ownerId()), "given grit, it is tamed");
				check(player.getMainHandItem().getCount() == 3, "and takes one");
			});
			context.getInput().releaseKey(options -> options.keyShift);
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				check(lizard[0].getTarget() == null, "it does not go for its hunter");
				float health = lizard[0].getHealth();
				lizard[0].hurtServer(level, level.damageSources().playerAttack(player), 6.0F);
				check(lizard[0].getHealth() == health, "and its hunter's blows pass it by");
			});

			// a wild lizard springing at the hunter does not take the companion with it
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				CrystalLizard wild = MistinnEntities.CRYSTAL_LIZARD.spawn(level, floor[0].offset(-6, 1, 2), EntitySpawnReason.COMMAND);
				wild.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
				lizard[0].setTarget(player);
				check(lizard[0].getTarget() == null, "the companion is never set on its hunter, not even by its pack");
				wild.discard();
			});
			world.getServer().runCommand("difficulty peaceful");
			context.waitTicks(5);
			check(world.getServer().computeOnServer(server -> lizard[0].isAlive() && !lizard[0].isRemoved()), "and peace does not clear it away");
			world.getServer().runCommand("difficulty normal");

			// a creature that hurts its hunter: the lizard goes for it
			Zombie[] zombie = new Zombie[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				zombie[0] = net.minecraft.world.entity.EntityTypes.ZOMBIE.spawn(level, floor[0].offset(4, 1, 3), EntitySpawnReason.COMMAND);
				zombie[0].setNoAi(true);
				player.hurtServer(level, level.damageSources().mobAttack(zombie[0]), 1.0F);
			});
			boolean guarded = false;
			for (int i = 0; i < 20 && !guarded; i++) {
				context.waitTicks(5);
				guarded = world.getServer().computeOnServer(server -> lizard[0].getTarget() == zombie[0]);
			}
			check(guarded, "it goes for what hurts its hunter");
			world.getServer().runOnServer(server -> {
				zombie[0].discard();
				lizard[0].setTarget(null);
			});

			// left far behind, it slips to its hunter's side
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), floor[0].getX() + 35.5, floor[0].getY() + 1.0, floor[0].getZ() + 0.5, Set.of(), 90.0F, 0.0F, true);
			});
			boolean beside = false;
			for (int i = 0; i < 30 && !beside; i++) {
				context.waitTicks(5);
				beside = world.getServer().computeOnServer(server -> lizard[0].distanceTo(player(server)) < CrystalLizard.FOLLOW);
			}
			check(beside, "left far behind, it comes to its hunter's side");

			// a quarry its hunter marks, it goes for; going home along the way, it comes too
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Zombie marked = net.minecraft.world.entity.EntityTypes.ZOMBIE.spawn(level, player.blockPosition().offset(-8, 0, 0), EntitySpawnReason.COMMAND);
				marked.setNoAi(true);
				check(life.shizune.mistinn.team.PingService.ping(player, new life.shizune.mistinn.team.PingService.PingPayload(marked.position().add(0.0, 1.0, 0.0),
					marked.getId())), "the hunter marks a quarry");
				check(lizard[0].getTarget() == marked, "and the lizard goes for it");
				marked.discard();
				lizard[0].setTarget(null);
				java.util.List<CrystalLizard> with = CrystalLizard.companionsOf(player, 16.0, false);
				check(with.contains(lizard[0]), "keeping with its hunter");
				player.teleportTo(level, floor[0].getX() - 30.5, floor[0].getY() + 1.0, floor[0].getZ() + 0.5, Set.of(), 90.0F, 0.0F, true);
				CrystalLizard.bring(player, with);
				check(lizard[0].distanceTo(player) < 3.0F, "and comes along when the hunter is taken home: " + lizard[0].distanceTo(player));
			});

			// told to stay, it stays
			world.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.isShiftKeyDown(), "the hunter crouches");
				player.interactOn(lizard[0], InteractionHand.MAIN_HAND, lizard[0].position());
				check(lizard[0].staying() && lizard[0].isStill(), "told to stay, it settles where it is");
				player.teleportTo(server.overworld(), floor[0].getX() + 10.5, floor[0].getY() + 1.0, floor[0].getZ() + 0.5, Set.of(), 90.0F, 0.0F, true);
			});
			context.getInput().releaseKey(options -> options.keyShift);
			double[] where = new double[1];
			world.getServer().runOnServer(server -> where[0] = lizard[0].getX());
			context.waitTicks(80);
			world.getServer().runOnServer(server -> check(Math.abs(lizard[0].getX() - where[0]) < 1.0 && lizard[0].isStill(), "and keeps there"));

			// across a save it knows its hunter
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
					net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
				lizard[0].saveWithoutId(out);
				CrystalLizard again = MistinnEntities.CRYSTAL_LIZARD.create(level, EntitySpawnReason.LOAD);
				again.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
				check(again.tamed() && player(server).getUUID().equals(again.ownerId()) && again.staying(), "across a save it knows its hunter, and stays");
				again.discard();
			});
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
