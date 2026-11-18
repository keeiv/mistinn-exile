package life.shizune.mistinn.test;

import life.shizune.mistinn.block.GravestoneBlockEntity;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.residue.DeathMark;
import life.shizune.mistinn.residue.ResidueService;
import life.shizune.mistinn.salt.SaltService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Plays the core loop in a real client: hold each weapon and swing it (screenshots in first and
 * third person), rest at a golden bell, carry salt, die, come back at the bell, find the
 * gravestone on the minimap and take the salt back.
 */
@SuppressWarnings("UnstableApiUsage")
public class MistinnClientGameTest implements FabricClientGameTest {
	private static final String[] WEAPONS = {"anchor_blade", "bell_greatsword", "mist_spear", "salt_fang"};

	@Override
	public void runTest(final ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			context.waitTicks(40);

			// ── Weapons: held models and swings ─────────────────────
			for (String weapon : WEAPONS) {
				world.getServer().runCommand("item replace entity @a weapon.mainhand with mistinn:" + weapon);
				context.waitTicks(10);
				context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
				context.waitTicks(5);
				context.takeScreenshot("weapon-" + weapon + "-held");
				context.runOnClient(mc -> mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false));
				context.waitTicks(3);
				context.takeScreenshot("weapon-" + weapon + "-swing");
				context.waitTicks(20);
				context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				context.waitTicks(5);
				context.runOnClient(mc -> mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false));
				context.waitTicks(3);
				context.takeScreenshot("weapon-" + weapon + "-swing-third");
				context.waitTicks(20);
			}
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// ── Golden bell: rest, then die and come back ───────────
			BlockPos bellPos = context.computeOnClient(mc -> mc.player.blockPosition().offset(3, 0, 0));
			world.getServer().runCommand("setblock " + bellPos.getX() + " " + bellPos.getY() + " " + bellPos.getZ() + " mistinn:golden_bell");
			world.getServer().runCommand("setblock " + (bellPos.getX() + 2) + " " + bellPos.getY() + " " + bellPos.getZ() + " mistinn:ore_bell");
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.teleportTo(player.level(), bellPos.getX() - 1.5, bellPos.getY(), bellPos.getZ() + 0.5, java.util.Set.of(), -90.0F, 20.0F, true);
				life.shizune.mistinn.bell.BellService.rest(player, bellPos, false);
				SaltService.set(player, 1234);
			});
			context.waitTicks(30);
			context.takeScreenshot("bell-rest");

			// walk away so the bell is off the map's centre, then die
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.teleportTo(bellPos.getX() + 20.5, player.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bellPos.getX() + 20, bellPos.getZ() + 12), bellPos.getZ() + 12.5);
			});
			context.waitTicks(20);
			world.getServer().runCommand("kill @a");
			context.waitForScreen(DeathScreen.class);
			DeathMark mark = context.computeOnClient(mc -> mc.player.getAttached(MistinnAttachments.DEATH_MARK));
			check(mark != null, "the client knows where the hunter fell");
			int residue = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				GlobalPos at = player.getAttached(MistinnAttachments.DEATH_MARK).pos();
				check(server.overworld().getBlockState(at.pos()).is(MistinnBlocks.GRAVESTONE), "a gravestone stands where the hunter fell");
				GravestoneBlockEntity stone = (GravestoneBlockEntity) server.overworld().getBlockEntity(at.pos());
				check(SaltService.get(player) == 0, "the salt left the hunter");
				return stone.salt();
			});
			check(residue == 1234, "the gravestone holds all 1234 salt, got " + residue);

			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(40);
			connection.waitForChunksRender();
			double distanceToBell = context.computeOnClient(mc -> Math.sqrt(mc.player.blockPosition().distSqr(bellPos)));
			check(distanceToBell < 3.0, "the hunter came back at the bell, distance " + distanceToBell);
			context.takeScreenshot("respawn-at-bell-minimap");

			// ── Take the residue back ───────────────────────────────
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos stone = player.getAttached(MistinnAttachments.DEATH_MARK).pos().pos();
				player.teleportTo(player.level(), stone.getX() - 1.5, stone.getY(), stone.getZ() + 0.5, java.util.Set.of(), -90.0F, 25.0F, true);
			});
			context.waitTicks(40);
			context.takeScreenshot("gravestone");
			int recovered = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos stone = player.getAttached(MistinnAttachments.DEATH_MARK).pos().pos();
				ResidueService.open(player, stone);
				check(server.overworld().getBlockState(stone).isAir(), "the gravestone is gone once opened");
				check(player.getAttached(MistinnAttachments.DEATH_MARK) == null, "the map mark is cleared");
				return SaltService.get(player);
			});
			check(recovered == 1234, "all salt came back, got " + recovered);
			context.waitTicks(10);
			context.takeScreenshot("residue-recovered");
		}
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
