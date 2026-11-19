package life.shizune.mistinn.test;

import life.shizune.mistinn.block.GravestoneBlockEntity;
import life.shizune.mistinn.body.BellSalveItem;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.client.grid.GridPanel;
import life.shizune.mistinn.keeper.LampStandBlock;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.Structures;
import life.shizune.mistinn.client.grid.HunterScreen;
import life.shizune.mistinn.body.BodyGear;
import life.shizune.mistinn.grid.GridLogic;
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
	private static final String[] WEAPONS = {"anchor_blade", "bell_greatsword", "mist_spear", "salt_fang", "iron_hammer", "stone_blade"};

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (Boolean.getBoolean("mistinn.photo")) {
			return; // photo mode only shoots pictures; see MistinnPhotoTest
		}
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

			// ── Arrival: spawn station, clear air, the guide ────────
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				net.minecraft.server.level.ServerLevel level = server.overworld();
				check(level.getAttachedOrElse(life.shizune.mistinn.world.MistinnWorld.SPAWN_STATION, false), "a drop station was built at the world spawn");
				BlockPos feet = player.blockPosition();
				check(level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty(), "the hunter does not spawn inside a wall: " + feet);
				BlockPos under = feet.below();
				net.minecraft.world.level.block.state.BlockState floor = level.getBlockState(under);
				check(floor.is(net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE) || floor.is(net.minecraft.world.level.block.Blocks.CRACKED_DEEPSLATE_BRICKS)
					|| floor.is(net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE) || floor.is(net.minecraft.world.level.block.Blocks.OAK_PLANKS)
					|| floor.is(net.minecraft.world.level.block.Blocks.MOSS_CARPET), "the hunter stands on the station's pad, found " + floor);
				boolean hasGuide = false;
				for (int i = 0; i < 9; i++) {
					hasGuide |= player.getInventory().getItem(i).is(MistinnItems.HUNTER_GUIDE);
				}
				hasGuide |= GridService.get(player).pockets().stream().anyMatch(p -> p.stack().is(MistinnItems.HUNTER_GUIDE));
				check(hasGuide, "every hunter arrives with the guide");
			});
			context.takeScreenshot("arrival");

			// ── Every material weapon exists, with its branch and sounds ──
			for (life.shizune.mistinn.weapon.WeaponStyle style : life.shizune.mistinn.weapon.WeaponStyle.values()) {
				for (life.shizune.mistinn.weapon.WeaponMaterial material : life.shizune.mistinn.weapon.WeaponMaterial.values()) {
					net.minecraft.world.item.Item item = MistinnItems.WEAPONS.get(style).get(material);
					life.shizune.mistinn.weapon.Weapons.Profile profile = life.shizune.mistinn.weapon.Weapons.profile(item);
					check(profile != null && profile.style() == style && profile.material() == material, "profile of " + item);
				}
			}

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
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof HunterScreen), "the inventory key opens the hunter screen");

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
				GridService.place(player, Area.BACKPACK, 3, 2, 1, false);
				check(player.containerMenu.getCarried().isEmpty(), "the ore was put down");
				Placed ore = GridService.get(player).items(Area.BACKPACK).stream().filter(p -> p.stack().is(Items.DIAMOND_ORE)).findFirst().orElseThrow();
				check(ore.section() == 3 && ore.x() == 2 && ore.y() == 1, "the ore sits where it was put: " + ore);
			});
			context.waitTicks(5);
			context.takeScreenshot("grid-moved");

			// the same moves with the real mouse: click to pick up and put down, drag to move,
			// drag onto the hotbar
			mouse(context, Area.POCKETS, 0, 0, 0);
			context.getInput().pressMouse(LEFT);
			context.waitTicks(4);
			carried(world, Items.DIAMOND, "a left click picks the diamonds up");
			mouse(context, Area.POCKETS, 2, 0, 0);
			context.getInput().pressMouse(LEFT);
			context.waitTicks(4);
			at(world, Area.POCKETS, 2, Items.DIAMOND, "a left click puts them down in the third pocket");

			// a click as a hand makes it: the button stays down for a few ticks, long enough for the
			// pick-up to reach the cursor before the release; the release must not throw it away
			mouse(context, Area.POCKETS, 2, 0, 0);
			context.getInput().holdMouse(LEFT);
			context.waitTicks(4);
			context.getInput().releaseMouse(LEFT);
			context.waitTicks(4);
			carried(world, Items.DIAMOND, "a slow click keeps the diamonds on the cursor");
			context.getInput().pressMouse(LEFT);
			context.waitTicks(4);
			at(world, Area.POCKETS, 2, Items.DIAMOND, "a second click puts them back");

			mouse(context, Area.POCKETS, 2, 0, 0);
			context.getInput().holdMouse(LEFT);
			context.waitTicks(4);
			mouse(context, Area.POCKETS, 3, 0, 0);
			context.getInput().releaseMouse(LEFT);
			context.waitTicks(4);
			at(world, Area.POCKETS, 3, Items.DIAMOND, "dragging moves them to the fourth pocket");

			mouse(context, Area.POCKETS, 3, 0, 0);
			context.getInput().holdMouse(LEFT);
			context.waitTicks(4);
			double[] hotbar = context.computeOnClient(mc -> {
				var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.gui.screen();
				net.minecraft.world.inventory.Slot slot = screen.getMenu().slots.stream()
					.filter(s -> s.container instanceof net.minecraft.world.entity.player.Inventory && s.getContainerSlot() == 8).findFirst().orElseThrow();
				double[] gui = GridPanel.slotCentre(slot);
				double scale = mc.getWindow().getGuiScale();
				return new double[] {gui[0] * scale, gui[1] * scale};
			});
			context.getInput().setCursorPos(hotbar[0], hotbar[1]);
			context.waitTicks(2);
			context.getInput().releaseMouse(LEFT);
			context.waitTicks(4);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				check(player.getInventory().getItem(8).is(Items.DIAMOND), "dragging out of the grid onto the hotbar: " + player.getInventory().getItem(8));
				check(player.containerMenu.getCarried().isEmpty(), "nothing is left on the cursor");
			});

			// and back: from the hotbar slot onto the first pocket
			context.getInput().setCursorPos(hotbar[0], hotbar[1]);
			context.waitTicks(2);
			context.getInput().holdMouse(LEFT);
			context.waitTicks(4);
			mouse(context, Area.POCKETS, 0, 0, 0);
			context.getInput().releaseMouse(LEFT);
			context.waitTicks(4);
			at(world, Area.POCKETS, 0, Items.DIAMOND, "dragging from the hotbar onto a pocket");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				check(player.getInventory().getItem(8).isEmpty(), "the hotbar slot was emptied");
				check(player.containerMenu.getCarried().isEmpty(), "nothing is left on the cursor after the drag in");
			});

			// ── Chest rig, mask and leg gear ────────────────────────
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				GridService.set(player, GridService.get(player).withRig(new ItemStack(MistinnItems.RIGS.get(4))));
				player.getInventory().setItem(7, new ItemStack(MistinnItems.RIGS.get(2)));
				player.getInventory().setItem(6, new ItemStack(MistinnItems.MASKS.get(4)));
				check(GridService.insert(player, new ItemStack(Items.IRON_INGOT, 3)), "ingots go in");
				GridInventory inv = GridService.get(player);
				check(inv.items(Area.RIG).stream().anyMatch(pl -> pl.stack().is(Items.IRON_INGOT)) || inv.pockets().stream().anyMatch(pl -> pl.stack().is(Items.IRON_INGOT)),
					"pickups go to pockets, then the rig, before the backpack: " + inv.items(Area.RIG));
				check(inv.items(Area.BACKPACK).stream().noneMatch(pl -> pl.stack().is(Items.IRON_INGOT)), "not into the backpack while the rig has room");
			});
			context.waitTicks(4);
			context.takeScreenshot("hunter-rig");
			// wear the tier II rig through the screen: pick it from the hotbar, click the rig socket
			double[] slot7 = slotCentre(context, 7);
			context.getInput().setCursorPos(slot7[0], slot7[1]);
			context.waitTicks(2);
			context.getInput().pressMouse(LEFT);
			context.waitTicks(4);
			carried(world, MistinnItems.RIGS.get(2), "the rig is on the cursor");
			socket(context, GridPanel.Socket.RIG);
			context.getInput().pressMouse(LEFT);
			context.waitTicks(4);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				check(GridService.get(player).rig().is(MistinnItems.RIGS.get(2)), "the tier II rig is worn");
				check(player.containerMenu.getCarried().is(MistinnItems.RIGS.get(4)), "the old rig, with what it held, is on the cursor");
				check(GridService.get(player).items(Area.RIG).stream().noneMatch(pl -> pl.stack().is(Items.IRON_INGOT)), "its contents went with it");
			});
			context.getInput().setCursorPos(slot7[0], slot7[1]);
			context.waitTicks(2);
			context.getInput().pressMouse(LEFT);
			context.waitTicks(4);

			// right-click the helmet slot for the mask slot, then put the mask on
			double[] head = context.computeOnClient(mc -> {
				var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.gui.screen();
				double[] gui = GridPanel.slotCentre(screen.getMenu().slots.get(5));
				double scale = mc.getWindow().getGuiScale();
				return new double[] {gui[0] * scale, gui[1] * scale};
			});
			context.getInput().setCursorPos(head[0], head[1]);
			context.waitTicks(2);
			context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitTicks(2);
			check(context.computeOnClient(mc -> ((HunterScreen) mc.gui.screen()).showsMask()), "right-clicking the helmet slot shows the mask slot");
			double[] slot6 = slotCentre(context, 6);
			context.getInput().setCursorPos(slot6[0], slot6[1]);
			context.waitTicks(2);
			context.getInput().pressMouse(LEFT);
			context.waitTicks(4);
			socket(context, GridPanel.Socket.MASK);
			context.getInput().pressMouse(LEFT);
			context.waitTicks(4);
			context.takeScreenshot("hunter-mask");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				check(GridService.get(player).mask().is(MistinnItems.MASKS.get(4)), "the mask is worn");
				check(BodyGear.faceArmor(player) == 3.0F, "a tier IV mask gives the face 3 armour");
				check(player.containerMenu.getCarried().isEmpty(), "nothing left on the cursor");
			});

			// a layout change leaves an item outside its compartment: it is placed again, not lost
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				GridInventory inv = GridService.get(player);
				java.util.List<Placed> pack = new java.util.ArrayList<>(inv.items(Area.BACKPACK));
				pack.add(new Placed(0, 9, 9, false, new ItemStack(Items.LAPIS_LAZULI, 7)));
				GridService.set(player, inv.with(Area.BACKPACK, pack));
			});
			context.waitTicks(25);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				GridInventory inv = GridService.get(player);
				int lapis = 0;
				for (Area area : Area.values()) {
					for (Placed placed : inv.items(area)) {
						check(GridLogic.fits(inv.shape(area), java.util.List.of(), placed.section(), placed.x(), placed.y(), placed.width(), placed.height(), -1), "every item lies inside its compartment: " + placed);
						if (placed.stack().is(Items.LAPIS_LAZULI)) {
							lapis += placed.stack().getCount();
						}
					}
				}
				for (int i = 0; i < 9; i++) {
					if (player.getInventory().getItem(i).is(Items.LAPIS_LAZULI)) {
						lapis += player.getInventory().getItem(i).getCount();
					}
				}
				check(lapis == 7, "all 7 lapis were placed again, found " + lapis);
			});
			context.setScreen(() -> null);

			// the hidden vanilla storage slots never keep anything
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().getInventory().setItem(20, new ItemStack(Items.EMERALD, 4)));
			context.waitTicks(3);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				check(player.getInventory().getItem(20).isEmpty(), "a hidden slot was emptied");
				GridInventory inv = GridService.get(player);
				boolean inGrid = java.util.stream.Stream.of(Area.POCKETS, Area.BACKPACK).flatMap(a -> inv.items(a).stream()).anyMatch(pl -> pl.stack().is(Items.EMERALD));
				boolean onHotbar = java.util.stream.IntStream.range(0, 9).anyMatch(i -> player.getInventory().getItem(i).is(Items.EMERALD));
				check(inGrid || onHotbar, "the emeralds moved somewhere the hunter can reach");
			});

			// death: pockets and backpack fall, the safe box stays
			world.getServer().runCommand("kill @a");
			context.waitForScreen(DeathScreen.class);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				GridInventory inv = GridService.get(player);
				check(inv.pockets().isEmpty() && inv.backpack().isEmpty(), "pockets and backpack were dropped");
				check(inv.rig().isEmpty() && inv.mask().isEmpty(), "the rig and mask were dropped too");
				check(inv.safe().size() == 1 && inv.safe().getFirst().stack().is(MistinnItems.RUST_GOD_SALT), "the safe box kept its salt block");
			});
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);

			// ── Body parts ──────────────────────────────────────────
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = server.overworld();
				for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					player.setItemSlot(slot, ItemStack.EMPTY);
				}
				check(BodyService.get(player).equals(Body.FULL), "a respawned hunter is whole");

				// a low blow from the side lands on a leg; leggings and boots soften it (a fall
				// bypasses armour in vanilla and still does)
				var blow = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
					.getOrThrow(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK);
				player.snapTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
				var low = player.position().add(-2.0, 0.2, 0.0);
				player.hurtServer(level, new net.minecraft.world.damagesource.DamageSource(blow, low), 4.0F);
				float bare = BodyService.get(player).get(BodyPart.RIGHT_LEG);
				check(bare == 56.0F, "a low hit from the right takes 24 from the right leg: " + BodyService.get(player));
				BodyService.restoreAll(player);
				player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(MistinnItems.LEGGINGS.get(5)));
				player.setItemSlot(EquipmentSlot.FEET, new ItemStack(MistinnItems.BOOTS.get(5)));
				player.damageCooldownTime = 0;
				player.hurtServer(level, new net.minecraft.world.damagesource.DamageSource(blow, low), 4.0F);
				float guarded = BodyService.get(player).get(BodyPart.RIGHT_LEG);
				check(guarded > bare, "leggings and boots soften it: " + guarded + " against " + bare);
				check(player.getItemBySlot(EquipmentSlot.LEGS).getDamageValue() > 0, "and wear down");
				check(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 0, "a leg hit does not wear the body armour");
				player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
				player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
				BodyService.restoreAll(player);
				player.damageCooldownTime = 0;
				player.hurtServer(level, player.damageSources().fall(), 4.0F);
				Body body = BodyService.get(player);
				check(body.get(BodyPart.LEFT_LEG) == 68.0F && body.get(BodyPart.RIGHT_LEG) == 68.0F && body.get(BodyPart.TORSO) == 80.0F,
					"a fall lands on both legs, 4 x 6 split in two: " + body);
				check(player.getHealth() > 0.0F && player.getHealth() < player.getMaxHealth(), "vanilla health follows the parts");

				player.damageCooldownTime = 0;
				player.hurtServer(level, player.damageSources().drown(), 1.0F);
				check(BodyService.get(player).get(BodyPart.TORSO) == 74.0F, "drowning hurts the torso: " + BodyService.get(player));

				player.heal(2.0F);
				body = BodyService.get(player);
				check(body.total() == 6 * 80.0F - 24 - 6 + 12, "healing mends 2 x 6 points: " + body);
				check(body.get(BodyPart.LEFT_LEG) > 68.0F && body.get(BodyPart.RIGHT_LEG) > 68.0F, "most hurt parts mend first: " + body);

				double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
				BodyService.set(player, BodyService.get(player).with(BodyPart.LEFT_LEG, 0.0F));
				check(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed * 0.8, "a destroyed leg slows the hunter");
				player.heal(20.0F);
				check(BodyService.get(player).destroyed(BodyPart.LEFT_LEG), "ordinary healing cannot bring a destroyed part back");
				BodyService.salve(player, BellSalveItem.REVIVE, 0.0F);
				check(BodyService.get(player).get(BodyPart.LEFT_LEG) == BellSalveItem.REVIVE, "the bell salve brings it back");
				check(player.getAttributeValue(Attributes.MOVEMENT_SPEED) >= speed * 0.99, "and the leg works again");

				// where hits land: high from the front is the face, from behind the crown
				player.snapTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
				var front = player.position().add(0, 0, 2);
				var behind = player.position().add(0, 0, -2);
				var random = net.minecraft.util.RandomSource.create(1);
				check(BodyService.locate(player, 0.9, front, random).face(), "a high hit from the front is the face");
				check(!BodyService.locate(player, 0.9, behind, random).face(), "from behind it is the crown");
				check(BodyService.locate(player, 0.2, player.position().add(-2, 0, 0), random).part() == BodyPart.RIGHT_LEG, "a low hit from the right side is the right leg");

				// overflow: a destroyed torso passes drowning on to the rest
				BodyService.set(player, Body.FULL.with(BodyPart.TORSO, 0.0F).with(BodyPart.HEAD, 0.0F));
				player.damageCooldownTime = 0;
				player.hurtServer(level, player.damageSources().drown(), 100.0F);
				check(player.isDeadOrDying(), "a third destroyed part kills: " + BodyService.get(player));
			});
			context.waitForScreen(DeathScreen.class);
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				check(BodyService.get(player).equals(Body.FULL), "whole again after respawning");
				BodyService.set(player, Body.FULL.with(BodyPart.HEAD, 0.0F).with(BodyPart.LEFT_ARM, 30.0F).with(BodyPart.RIGHT_LEG, 12.0F));
			});
			context.waitTicks(5);
			context.takeScreenshot("body-hud");
			world.getServer().runOnServer(server -> BodyService.restoreAll(server.getPlayerList().getPlayers().getFirst()));

			// ── The lighthouse and its keeper ───────────────────────
			// peaceful removes monsters and zeroes their blows: fight on normal
			world.getServer().runCommand("difficulty normal");
			BlockPos room = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos at = player.blockPosition().offset(30, 0, 0);
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
				return Structures.lighthouse(new Builder(level, new BlockPos(at.getX(), y, at.getZ()), level.getRandom()));
			});
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				var keepers = level.getEntitiesOfClass(Snuffer.class, new net.minecraft.world.phys.AABB(room).inflate(8));
				check(keepers.size() == 1, "the lamp room holds one keeper, found " + keepers.size());
				Snuffer keeper = keepers.getFirst();
				check(keeper.lamps(level).size() == 4, "four lamp stands around the room");
				check(level.getBlockState(room.offset(0, -LAMP_DROP, 7)).is(MistinnBlocks.GOLDEN_BELL), "a golden bell waits by the door");
				player.teleportTo(level, room.getX() + 0.5, room.getY(), room.getZ() - 3.5, java.util.Set.of(), 0.0F, 10.0F, true);
				for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					player.setItemSlot(slot, ItemStack.EMPTY);
				}
				GridService.set(player, GridService.get(player).withMask(ItemStack.EMPTY));

				BodyService.restoreAll(player);
				player.damageCooldownTime = 0;
				keeper.strikeNow(level, player, Snuffer.Move.SWEEP);
				Body body = BodyService.get(player);
				check(body.get(BodyPart.TORSO) < 80.0F || body.get(BodyPart.LEFT_ARM) < 80.0F || body.get(BodyPart.RIGHT_ARM) < 80.0F, "the sweep cuts the torso or an arm: " + body);
				check(body.get(BodyPart.HEAD) == 80.0F && body.get(BodyPart.LEFT_LEG) == 80.0F && body.get(BodyPart.RIGHT_LEG) == 80.0F, "and nothing else: " + body);

				BodyService.restoreAll(player);
				player.damageCooldownTime = 0;
				keeper.strikeNow(level, player, Snuffer.Move.SLAM);
				body = BodyService.get(player);
				check(body.get(BodyPart.LEFT_LEG) < 80.0F && body.get(BodyPart.RIGHT_LEG) < 80.0F && body.get(BodyPart.TORSO) == 80.0F, "the slam takes the legs: " + body);

				BodyService.restoreAll(player);
				player.damageCooldownTime = 0;
				keeper.strikeNow(level, player, Snuffer.Move.HOOK);
				body = BodyService.get(player);
				float bareFace = body.get(BodyPart.HEAD);
				check(bareFace < 80.0F && body.get(BodyPart.TORSO) == 80.0F, "the hook strikes the face: " + body);
				BodyService.restoreAll(player);
				GridService.set(player, GridService.get(player).withMask(new ItemStack(MistinnItems.MASKS.get(6))));
				player.damageCooldownTime = 0;
				keeper.strikeNow(level, player, Snuffer.Move.HOOK);
				check(BodyService.get(player).get(BodyPart.HEAD) > bareFace, "a mask softens the blow to the face: " + BodyService.get(player).get(BodyPart.HEAD) + " against " + bareFace);
				BodyService.restoreAll(player);
			});
			// look at it from across the room
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				Snuffer keeper = level.getEntitiesOfClass(Snuffer.class, new net.minecraft.world.phys.AABB(room).inflate(16)).getFirst();
				// hold it still in the middle of the room, seen from the side, clear of the great lamp
				keeper.setNoAi(true);
				keeper.snapTo(room.getX() + 0.5, room.getY(), room.getZ() + 3.5, 90.0F, 0.0F);
				keeper.setYHeadRot(90.0F);
				player.teleportTo(level, room.getX() - 4.5, room.getY(), room.getZ() + 3.5, java.util.Set.of(), -90.0F, -12.0F, true);
			});
			context.waitTicks(20);
			context.takeScreenshot("snuffer");
			world.getServer().runOnServer(server -> server.overworld().getEntitiesOfClass(Snuffer.class, new net.minecraft.world.phys.AABB(room).inflate(16)).getFirst().setNoAi(false));

			// below half health the room goes dark; four lit lamps throw it off balance
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				Snuffer keeper = level.getEntitiesOfClass(Snuffer.class, new net.minecraft.world.phys.AABB(room).inflate(16)).getFirst();
				keeper.setHealth(Snuffer.MAX_HEALTH * 0.45F);
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				Snuffer keeper = level.getEntitiesOfClass(Snuffer.class, new net.minecraft.world.phys.AABB(room).inflate(16)).getFirst();
				check(keeper.dark(), "below half health the keeper goes dark");
				check(player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS), "and the hunter is in darkness");
				for (BlockPos lamp : keeper.lamps(level)) {
					check(!level.getBlockState(lamp).getValue(LampStandBlock.LIT), "every lamp stand is out");
					level.setBlockAndUpdate(lamp, level.getBlockState(lamp).setValue(LampStandBlock.LIT, true));
				}
			});
			context.waitTicks(3);
			context.takeScreenshot("snuffer-dark");
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				Snuffer keeper = level.getEntitiesOfClass(Snuffer.class, new net.minecraft.world.phys.AABB(room).inflate(16)).getFirst();
				check(keeper.staggered(), "four lit lamps stagger the keeper");
				float before = keeper.getHealth();
				keeper.damageCooldownTime = 0;
				keeper.hurtServer(level, level.damageSources().generic(), 10.0F);
				check(Math.abs(before - keeper.getHealth() - 15.0F) < 0.01F, "a staggered keeper takes half again as much: " + (before - keeper.getHealth()));

				int salt = SaltService.get(player);
				keeper.damageCooldownTime = 0;
				keeper.hurtServer(level, player.damageSources().playerAttack(player), 10000.0F);
				check(keeper.isDeadOrDying(), "the keeper falls");
				check(SaltService.get(player) >= salt + 3000, "and leaves 3000 salt to the hunter");
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(room).inflate(10));
				check(drops.stream().anyMatch(e -> e.getItem().is(MistinnItems.RUST_GOD_SALT)), "the Rust God's salt block drops");
			});
			world.getServer().runCommand("difficulty peaceful");
		}
	}

	/** The golden bell stands on the ground; the lamp room floor is this far above it. */
	private static final int LAMP_DROP = Structures.LAMP_ROOM;

	/** The left button as the game numbers it; GLFW's 0 is not the left button here. */
	private static final int LEFT = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;

	/** Window position of a hotbar slot of the open screen. */
	private static double[] slotCentre(final ClientGameTestContext context, final int hotbarIndex) {
		return context.computeOnClient(mc -> {
			var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.gui.screen();
			net.minecraft.world.inventory.Slot slot = screen.getMenu().slots.stream()
				.filter(s -> s.container instanceof net.minecraft.world.entity.player.Inventory && s.getContainerSlot() == hotbarIndex).findFirst().orElseThrow();
			double[] gui = GridPanel.slotCentre(slot);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
	}

	/** Moves the real cursor over a socket. */
	private static void socket(final ClientGameTestContext context, final GridPanel.Socket socket) {
		double[] at = context.computeOnClient(mc -> {
			double[] gui = GridPanel.socketCentre(socket);
			check(gui != null, "the screen shows the " + socket + " socket");
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
	}

	/** Moves the real cursor over a grid cell. */
	private static void mouse(final ClientGameTestContext context, final Area area, final int section, final int cx, final int cy) {
		double[] at = context.computeOnClient(mc -> {
			double[] gui = GridPanel.cellCentre(area, section, cx, cy);
			check(gui != null, "the grid panel shows " + area + " " + section);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
	}

	private static void carried(final TestSingleplayerContext world, final net.minecraft.world.item.Item item, final String what) {
		world.getServer().runOnServer(server -> {
			ItemStack carried = server.getPlayerList().getPlayers().getFirst().containerMenu.getCarried();
			check(carried.is(item), what + ", cursor holds " + carried);
		});
	}

	private static void at(final TestSingleplayerContext world, final Area area, final int section, final net.minecraft.world.item.Item item, final String what) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			GridInventory inv = GridService.get(player);
			check(inv.items(area).stream().anyMatch(p -> p.section() == section && p.stack().is(item)), what + ": " + inv.items(area) + ", cursor " + player.containerMenu.getCarried());
		});
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
