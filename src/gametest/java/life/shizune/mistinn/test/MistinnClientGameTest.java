package life.shizune.mistinn.test;

import life.shizune.mistinn.block.GravestoneBlockEntity;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
		// screenshots in Traditional Chinese, without chat lines over them
		boolean[] reloaded = {false};
		context.runOnClient(mc -> {
			mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
			mc.options.languageCode = "zh_tw";
			mc.getLanguageManager().setSelected("zh_tw");
			mc.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
		});
		context.waitFor(mc -> reloaded[0], 1200);
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

			// ── Grid inventory, tiers and gear ──────────────────────
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				GridService.set(player, GridInventory.EMPTY.withBackpack(new ItemStack(MistinnItems.BACKPACKS.get(3))));
				check(GridService.insert(player, new ItemStack(Items.DIAMOND, 5)), "diamonds go in");
				check(GridService.insert(player, new ItemStack(Items.STICK, 8)), "sticks go in");
				check(GridService.insert(player, new ItemStack(Items.DIAMOND_ORE)), "diamond ore goes in");
				check(GridService.insert(player, new ItemStack(Items.IRON_SWORD)), "a sword goes in");
				check(GridService.insert(player, new ItemStack(MistinnItems.ANCHOR_BLADE)), "the anchor blade goes in");
				check(GridService.insert(player, new ItemStack(Items.GOLDEN_APPLE, 3)), "apples go in");
				GridInventory inv = GridService.get(player);
				check(inv.pockets().getFirst().stack().is(Items.DIAMOND), "a 1x1 diamond lands in the first pocket");
				check(inv.items(Area.BACKPACK).stream().noneMatch(p -> p.stack().is(Items.DIAMOND)), "all diamonds stacked in one pocket");
				check(inv.items(Area.BACKPACK).stream().anyMatch(p -> p.stack().is(Items.STICK)), "a 1x2 stick cannot fit a 1x1 pocket, so it goes to the backpack");
				Placed ore = inv.items(Area.BACKPACK).stream().filter(p -> p.stack().is(Items.DIAMOND_ORE)).findFirst().orElseThrow();
				check(ore.width() == 2 && ore.height() == 2, "diamond ore takes 2x2");
				check(!GridService.insert(player, new ItemStack(MistinnItems.MIST_SPEAR)), "a 1x5 spear does not fit a backpack whose longest side is 4");

				GridService.set(player, GridService.get(player).with(Area.SAFE, java.util.List.of(new Placed(0, 0, 0, false, new ItemStack(MistinnItems.RUST_GOD_SALT)))));
				player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(MistinnItems.HELMETS.get(5)));
				player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(MistinnItems.ARMORS.get(6)));
				player.getInventory().setItem(0, new ItemStack(MistinnItems.BELL_GREATSWORD));
				player.getInventory().setItem(1, new ItemStack(Items.BREAD, 12));
			});
			context.waitTicks(10);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("gear-worn");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(10);
			context.takeScreenshot("grid-inventory");

			// pick the ore up and put it down turned elsewhere, through the menu's cursor
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				java.util.List<Placed> pack = GridService.get(player).items(Area.BACKPACK);
				int oreIndex = -1;
				for (int i = 0; i < pack.size(); i++) {
					if (pack.get(i).stack().is(Items.DIAMOND_ORE)) {
						oreIndex = i;
					}
				}
				GridService.pick(player, Area.BACKPACK, oreIndex);
				check(player.containerMenu.getCarried().is(Items.DIAMOND_ORE), "the ore is on the cursor");
			});
			context.waitTicks(5);
			context.takeScreenshot("grid-carrying");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				GridService.place(player, Area.BACKPACK, 2, 2, 1, false);
				check(player.containerMenu.getCarried().isEmpty(), "the ore was put down");
				Placed ore = GridService.get(player).items(Area.BACKPACK).stream().filter(p -> p.stack().is(Items.DIAMOND_ORE)).findFirst().orElseThrow();
				check(ore.section() == 2 && ore.x() == 2 && ore.y() == 1, "the ore sits where it was put: " + ore);
			});
			context.waitTicks(5);
			context.takeScreenshot("grid-moved");
			context.setScreen(() -> null);

			// death: pockets and backpack fall, the safe box stays
			world.getServer().runCommand("kill @a");
			context.waitForScreen(DeathScreen.class);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				GridInventory inv = GridService.get(player);
				check(inv.pockets().isEmpty() && inv.backpack().isEmpty(), "pockets and backpack were dropped");
				check(inv.safe().size() == 1 && inv.safe().getFirst().stack().is(MistinnItems.RUST_GOD_SALT), "the safe box kept its salt block");
			});
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
		}
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
