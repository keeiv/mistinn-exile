package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.stamina.StaminaService;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * 熄燈人的失衡: full heavy blows, one on another, throw the keeper off balance as the four lit lamps do.
 * Three in a row, each within ten seconds of the last; a half-charged blow does not count, and a keeper
 * left alone long enough finds its feet again.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnSnufferPoiseTest implements FabricClientGameTest {
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
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("time set noon");
			BlockPos stage = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				BlockPos pos = player.blockPosition().above(12).immutable();
				for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-10, -1, -10), pos.offset(10, -1, 10))) {
					player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				}
				for (BlockPos air : BlockPos.betweenClosed(pos.offset(-10, 0, -10), pos.offset(10, 8, 10))) {
					player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
				}
				player.setGameMode(GameType.SURVIVAL);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 600, 4, false, false));
				return pos;
			});
			context.waitTicks(10);

			// two full blows: it groans, it keeps its feet
			blow(context, world, stage, 1.0F);
			blow(context, world, stage, 1.0F);
			world.getServer().runOnServer(server -> {
				Snuffer keeper = MistinnBossTest.keeper(server, stage);
				check(keeper.poise() == 2, "two full blows are counted: " + keeper.poise());
				check(!keeper.staggered(), "and it keeps its feet");
			});
			// a half-charged blow does not count
			blow(context, world, stage, 0.6F);
			world.getServer().runOnServer(server -> check(MistinnBossTest.keeper(server, stage).poise() == 2, "a half-charged blow is not counted"));
			// the third full blow throws it off balance
			blow(context, world, stage, 1.0F);
			world.getServer().runOnServer(server -> {
				Snuffer keeper = MistinnBossTest.keeper(server, stage);
				check(keeper.staggered(), "the third full blow in a row throws it off balance");
				check(keeper.poise() == 0, "and the count starts again");
			});
			// off balance, more blows do not count toward the next
			blow(context, world, stage, 1.0F);
			world.getServer().runOnServer(server -> check(MistinnBossTest.keeper(server, stage).poise() == 0, "a keeper already off balance takes no count"));
			until(context, world, server -> !MistinnBossTest.keeper(server, stage).staggered(), Snuffer.STAGGER_TICKS + 20, "it finds its feet");

			// left alone long enough, it finds its feet again: the count is lost
			blow(context, world, stage, 1.0F);
			blow(context, world, stage, 1.0F);
			context.waitTicks(Snuffer.POISE_WINDOW + 10);
			world.getServer().runOnServer(server -> check(MistinnBossTest.keeper(server, stage).poise() == 0, "ten quiet seconds and the count is lost"));
			blow(context, world, stage, 1.0F);
			world.getServer().runOnServer(server -> {
				Snuffer keeper = MistinnBossTest.keeper(server, stage);
				check(keeper.poise() == 1 && !keeper.staggered(), "so the next full blow is only the first again");
				MistinnBossTest.clear(server, stage);
			});
		}
	}

	/** The hunter, in front of the keeper and facing it, lets go a heavy blow charged this far, and it lands. */
	private static void blow(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage, final float charge) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Snuffer keeper = MistinnBossTest.keeper(server, stage);
			// it is not killed by the test's blows, and it stays where it is struck
			keeper.setHealth(keeper.getMaxHealth());
			keeper.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5, 0.0F, 0.0F);
			keeper.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 2.3, Set.of(), 180.0F, 10.0F, true);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON)));
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
			int ticks = Math.round(HeavyService.chargeTicks(player, WeaponStyle.SLASH) * charge);
			check(HeavyService.release(player, player.getMainHandItem(), ticks), "the heavy blow is let go at " + charge);
		});
		// the blow is on its way when let go; give it time to land
		context.waitTicks(15);
	}

	private static void until(final ClientGameTestContext context, final TestSingleplayerContext world,
		final java.util.function.Predicate<MinecraftServer> condition, final int ticks, final String what) {
		for (int i = 0; i < ticks; i++) {
			if (world.getServer().computeOnServer(condition::test)) {
				return;
			}
			context.waitTicks(1);
		}
		throw new AssertionError("timed out waiting until " + what);
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
