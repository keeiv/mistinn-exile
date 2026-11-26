package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.client.guide.HunterGuideScreen;
import life.shizune.mistinn.enemy.NoiseService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.supply.SupplyService;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Supply drops: an order is paid for in salt and only one may be on its way; a hunter short of
 * salt is turned away; the barrel comes down near the hunter with what was bought in it, loud
 * enough that a zombie nearby hears it, and the minimap marks it; from the guide's supply page an
 * offer is chosen with one click and ordered with the second.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnSupplyTest implements FabricClientGameTest {
	private static final int DELAY = SupplyService.FALL + 40;

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
			world.getServer().runOnServer(server -> {
				player(server).addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
				// unseen, so the zombie below has nothing to chase and is free to listen
				player(server).addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100000, 0, false, false));
				SupplyService.fixedDelay = DELAY;
			});
			context.waitTicks(20);

			// short of salt: turned away, nothing taken
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				SaltService.set(player, 30);
				check(SupplyService.order(player, "tourniquet") == SupplyService.Result.POOR, "thirty salt does not buy two tourniquets");
				check(SaltService.get(player) == 30 && !player.hasAttached(SupplyService.ORDER), "and nothing is taken or ordered");
				check(SupplyService.order(player, "no_such_thing") == SupplyService.Result.UNKNOWN, "the station does not sell what it does not have");
			});

			// ordered: paid for, and a second order waits for the first
			BlockPos[] zombieAt = new BlockPos[1];
			Zombie[] zombie = new Zombie[1];
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				SaltService.set(player, 1000);
				check(SupplyService.order(player, "tourniquet") == SupplyService.Result.ORDERED, "two tourniquets ordered");
				check(SaltService.get(player) == 940, "for sixty salt: " + SaltService.get(player));
				check(SupplyService.order(player, "flare") == SupplyService.Result.PENDING, "one order at a time");
				check(SaltService.get(player) == 940, "and the second is not charged");
				// a zombie within earshot of wherever it lands, idle
				ServerLevel level = server.overworld();
				zombieAt[0] = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(0, 0, 6));
				zombie[0] = EntityTypes.ZOMBIE.spawn(level, zombieAt[0], EntitySpawnReason.COMMAND);
				zombie[0].addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100000, 0, false, false));
				zombie[0].addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100000, 9, false, false));
			});
			context.waitTicks(5);
			check(context.computeOnClient(mc -> mc.player.hasAttached(SupplyService.ORDER)), "the client knows an order is on its way");

			// the streak begins: the spot is chosen and marked
			context.waitTicks(DELAY - SupplyService.FALL + 5);
			BlockPos at = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				SupplyService.Drop drop = player.getAttached(SupplyService.DROP);
				check(drop != null, "the drop is marked while it falls");
				double distance = Math.hypot(drop.at().getX() + 0.5 - player.getX(), drop.at().getZ() + 0.5 - player.getZ());
				check(distance <= SupplyService.FAR + 2, "it comes down near the hunter: " + distance);
				check(!player.level().getBlockState(drop.at()).is(Blocks.BARREL), "not landed yet");
				// the zombie is on no errand yet
				NoiseService.forget(zombie[0]);
				zombie[0].setTarget(null);
				// someone plants a sign right where it will land: the barrel must not break it
				player.level().setBlock(drop.at(), Blocks.OAK_SIGN.defaultBlockState(), 3);
				return drop.at();
			});

			// landed: the barrel holds what was bought, and the zombie heard it
			context.waitTicks(SupplyService.FALL + 5);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = server.overworld();
				check(!player.hasAttached(SupplyService.ORDER), "the order is done");
				check(level.getBlockState(at).is(Blocks.OAK_SIGN), "the sign in the way is left standing");
				SupplyService.Drop drop = player.getAttached(SupplyService.DROP);
				check(drop != null && drop.at().equals(at.above()), "the minimap marks the barrel where it came to rest: " + (drop == null ? null : drop.at()));
				check(level.getBlockState(at.above()).is(Blocks.BARREL), "a barrel on top of it");
				check(level.getBlockEntity(at.above()) instanceof BarrelBlockEntity barrel && barrel.countItem(MistinnItems.TOURNIQUET) == 2,
					"with two tourniquets in it");
				check(zombie[0].distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at)) > SupplyService.HEARD * SupplyService.HEARD
					|| NoiseService.heard(zombie[0]) != null, "a zombie within earshot comes to look");
			});

			// emptied, the mark goes
			world.getServer().runOnServer(server -> {
				if (server.overworld().getBlockEntity(at.above()) instanceof BarrelBlockEntity barrel) {
					barrel.clearContent();
				}
			});
			context.waitTicks(25);
			check(world.getServer().computeOnServer(server -> player(server).getAttached(SupplyService.DROP) == null), "an empty barrel is no longer marked");

			// from the guide: a click chooses, the second orders
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), player.getX(), player.getY(), player.getZ(), Set.of(), 0.0F, 60.0F, true);
			});
			world.getServer().runCommand("item replace entity @a weapon.mainhand with mistinn:hunter_guide");
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(HunterGuideScreen.class);
			HunterGuideScreen screen = context.computeOnClient(mc -> (HunterGuideScreen) mc.gui.screen());
			click(context, context.computeOnClient(mc -> screen.tabCentre(HunterGuideScreen.Tab.SUPPLY)));
			check(context.computeOnClient(mc -> screen.tab()) == HunterGuideScreen.Tab.SUPPLY, "the supply bookmark opens the station's list");
			int flare = SupplyService.index("flare");
			double[] row = context.computeOnClient(mc -> screen.offerCentre(flare));
			click(context, row);
			context.waitTicks(3);
			check(world.getServer().computeOnServer(server -> !player(server).hasAttached(SupplyService.ORDER)), "one click only chooses");
			context.takeScreenshot(TestScreenshotOptions.of("guide-supply-armed").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("guide")));
			click(context, row);
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				SupplyService.Order order = player.getAttached(SupplyService.ORDER);
				check(order != null && order.offer().equals("flare"), "the second click orders the flares");
				check(SaltService.get(player) == 940 - SupplyService.OFFERS.get(flare).price(), "and pays for them: " + SaltService.get(player));
			});
			context.waitTicks(5);
			context.takeScreenshot(TestScreenshotOptions.of("guide-supply").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("guide")));
			context.runOnClient(mc -> mc.gui.setScreen(null));
			// let it land, so nothing is left falling
			context.waitTicks(DELAY + 10);
			context.takeScreenshot(TestScreenshotOptions.of("supply-landed").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("guide")));
			check(world.getServer().computeOnServer(server -> !player(server).hasAttached(SupplyService.ORDER)), "the flares land too");
		} finally {
			SupplyService.fixedDelay = 0;
		}
	}

	private static void click(final ClientGameTestContext context, final double[] gui) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(gui[0] * scale, gui[1] * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
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
