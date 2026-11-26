package life.shizune.mistinn.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.client.grid.GridPanel;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The game's own inventory controls on the grid: where things go first, right-click halves and
 * single items, trading places, number keys and the offhand key, Q and Ctrl+Q, shift-click into an
 * open chest or onto the body, and a double click gathering a stack.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnGridControlsTest implements FabricClientGameTest {
	private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
	private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
	private static final SimpleContainer CHEST = new SimpleContainer(27);
	private static final Item[] FILLERS = {Items.STONE, Items.DIRT, Items.COBBLESTONE, Items.SAND, Items.GRAVEL, Items.OAK_LOG, Items.GLASS, Items.CLAY, Items.TUFF};

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			context.waitTicks(10);
			world.getServer().runOnServer(MistinnGridControlsTest::pickupsGoToTheHotbarFirst);
			world.getServer().runOnServer(MistinnGridControlsTest::rightClickHalvesAndPutsOneDown);
			world.getServer().runOnServer(MistinnGridControlsTest::aDifferentItemTradesPlaces);
			world.getServer().runOnServer(MistinnGridControlsTest::qThrowsOneAndCtrlQTheStack);
			world.getServer().runOnServer(MistinnGridControlsTest::numberKeysAndTheOffhandTrade);
			world.getServer().runOnServer(MistinnGridControlsTest::shiftClickWearsArmourOrGoesToTheHotbar);
			world.getServer().runOnServer(MistinnGridControlsTest::aDoubleClickGathers);
			world.getServer().runOnServer(MistinnGridControlsTest::aChestShiftClickComesToTheHotbarFirst);
			world.getServer().runOnServer(MistinnGridControlsTest::shiftClickOnABagPutsItOn);
			world.getServer().runOnServer(MistinnGridControlsTest::sortingLaysItOutAfresh);
			context.waitTicks(5);
			keysOnTheHunterScreen(context, world);
			shiftClickIntoAnOpenChest(context, world);
		}
	}

	private static void pickupsGoToTheHotbarFirst(final MinecraftServer server) {
		ServerPlayer player = reset(server);
		ItemStack flint = new ItemStack(Items.FLINT, 3);
		check(GridService.store(player, flint) && player.getInventory().getItem(0).is(Items.FLINT), "an empty hotbar slot comes before the pockets");
		fillHotbar(player);
		GridService.store(player, new ItemStack(Items.FEATHER));
		check(has(player, Area.POCKETS, Items.FEATHER), "with the hotbar full, the pockets: " + GridService.get(player).pockets());
		pockets(player, Items.STRING, Items.BONE, Items.PAPER, Items.SLIME_BALL);
		GridService.store(player, new ItemStack(Items.CLAY_BALL));
		check(has(player, Area.BACKPACK, Items.CLAY_BALL) && !has(player, Area.RIG, Items.CLAY_BALL), "with the pockets full, the backpack before the rig");
		player.getInventory().setItem(8, ItemStack.EMPTY);
		GridService.store(player, new ItemStack(Items.CLAY_BALL, 5));
		check(count(player, Area.BACKPACK, Items.CLAY_BALL) == 6 && player.getInventory().getItem(8).isEmpty(),
			"a matching stack in the grid is topped up before an empty hotbar slot is taken");
		check(player.getInventory().add(new ItemStack(Items.GLOWSTONE_DUST, 2)) && player.getInventory().getItem(8).is(Items.GLOWSTONE_DUST),
			"what is picked up from the ground goes the same way");
	}

	private static void rightClickHalvesAndPutsOneDown(final MinecraftServer server) {
		ServerPlayer player = reset(server);
		pockets(player, new ItemStack(Items.ARROW, 5));
		GridService.half(player, Area.POCKETS, 0);
		check(player.containerMenu.getCarried().getCount() == 3 && count(player, Area.POCKETS, Items.ARROW) == 2, "right-click takes the larger half");
		GridService.placeOne(player, Area.POCKETS, 1, 0, 0, false);
		check(player.containerMenu.getCarried().getCount() == 2 && pocket(player, 1).getCount() == 1, "right-click on an empty pocket puts one down");
		GridService.placeOne(player, Area.POCKETS, 0, 0, 0, false);
		check(player.containerMenu.getCarried().getCount() == 1 && pocket(player, 0).getCount() == 3, "and one onto a matching stack");
		player.containerMenu.setCarried(ItemStack.EMPTY);
	}

	private static void aDifferentItemTradesPlaces(final MinecraftServer server) {
		ServerPlayer player = reset(server);
		pockets(player, Items.STICK);
		player.containerMenu.setCarried(new ItemStack(Items.BREAD, 2));
		GridService.place(player, Area.POCKETS, 0, 0, 0, false);
		check(pocket(player, 0).is(Items.BREAD) && pocket(player, 0).getCount() == 2 && player.containerMenu.getCarried().is(Items.STICK),
			"putting bread on a stick: the bread goes in, the stick comes onto the cursor");
		player.containerMenu.setCarried(ItemStack.EMPTY);
	}

	private static void qThrowsOneAndCtrlQTheStack(final MinecraftServer server) {
		ServerPlayer player = reset(server);
		pockets(player, new ItemStack(Items.ARROW, 4));
		GridService.drop(player, Area.POCKETS, 0, false);
		check(pocket(player, 0).getCount() == 3, "Q throws one");
		GridService.drop(player, Area.POCKETS, 0, true);
		check(pocket(player, 0).isEmpty(), "Ctrl+Q the rest");
	}

	private static void numberKeysAndTheOffhandTrade(final MinecraftServer server) {
		ServerPlayer player = reset(server);
		pockets(player, Items.APPLE, null, null, Items.FEATHER);
		Inventory inventory = player.getInventory();
		inventory.setItem(0, new ItemStack(Items.TORCH));
		GridService.swap(player, Area.POCKETS, 0, 0, 0, 0, false);
		check(inventory.getItem(0).is(Items.APPLE) && pocket(player, 0).is(Items.TORCH), "a number key trades the pocket with that hotbar slot");
		GridService.swap(player, Area.POCKETS, 0, 0, 0, Inventory.SLOT_OFFHAND, false);
		check(player.getOffhandItem().is(Items.TORCH) && pocket(player, 0).isEmpty(), "the offhand key moves it to the offhand");
		inventory.setItem(1, new ItemStack(Items.BREAD));
		GridService.swap(player, Area.POCKETS, 2, 0, 0, 1, false);
		check(pocket(player, 2).is(Items.BREAD) && inventory.getItem(1).isEmpty(), "over an empty pocket the hotbar's stack is put down there");
		inventory.setItem(2, new ItemStack(MistinnItems.BACKPACKS.get(2)));
		GridService.swap(player, Area.POCKETS, 3, 0, 0, 2, false);
		check(pocket(player, 3).is(Items.FEATHER) && inventory.getItem(2).is(MistinnItems.BACKPACKS.get(2)), "a bag on the hotbar never goes into the grid");
	}

	private static void shiftClickWearsArmourOrGoesToTheHotbar(final MinecraftServer server) {
		ServerPlayer player = reset(server);
		pockets(player, new ItemStack(MistinnItems.HELMETS.get(1)), Items.BREAD);
		GridService.quickMove(player, Area.POCKETS, 0);
		check(player.getItemBySlot(EquipmentSlot.HEAD).is(MistinnItems.HELMETS.get(1)) && pocket(player, 0).isEmpty(), "shift-click on a helmet puts it on");
		GridService.quickMove(player, Area.POCKETS, 0);
		check(player.getInventory().getItem(0).is(Items.BREAD) && GridService.get(player).pockets().isEmpty(), "and anything else onto the hotbar");
		fillHotbar(player);
		pockets(player, Items.APPLE);
		GridService.quickMove(player, Area.POCKETS, 0);
		check(pocket(player, 0).is(Items.APPLE), "with the hotbar full it stays where it lay");
		for (int slot = Inventory.getSelectionSize(); slot < Inventory.INVENTORY_SIZE; slot++) {
			check(player.getInventory().getItem(slot).isEmpty(), "and nothing is left in the hidden slots");
		}
	}

	private static void aDoubleClickGathers(final MinecraftServer server) {
		ServerPlayer player = reset(server);
		GridInventory inv = GridService.get(player);
		GridService.set(player, inv
			.with(Area.POCKETS, List.of(new Placed(0, 0, 0, false, new ItemStack(Items.ARROW, 10))))
			.with(Area.BACKPACK, List.of(new Placed(0, 0, 0, false, new ItemStack(Items.ARROW, 20))))
			.with(Area.SAFE, List.of(new Placed(0, 0, 0, false, new ItemStack(Items.ARROW, 7)))));
		player.getInventory().setItem(0, new ItemStack(Items.ARROW, 5));
		player.containerMenu.setCarried(new ItemStack(Items.ARROW, 1));
		GridService.collect(player);
		check(player.containerMenu.getCarried().getCount() == 36, "a double click gathers the pockets, the backpack and the hotbar: " + player.containerMenu.getCarried());
		check(count(player, Area.SAFE, Items.ARROW) == 7, "but leaves the safe box alone");
		player.containerMenu.setCarried(ItemStack.EMPTY);
	}

	private static void aChestShiftClickComesToTheHotbarFirst(final MinecraftServer server) {
		ServerPlayer player = reset(server);
		CHEST.clearContent();
		CHEST.setItem(0, new ItemStack(Items.COAL, 10));
		CHEST.setItem(1, new ItemStack(Items.CHARCOAL, 4));
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, CHEST), Component.literal("chest")));
		GridService.grabSlot(player, 0);
		check(player.getInventory().getItem(0).is(Items.COAL) && CHEST.getItem(0).isEmpty(), "shift-click from a chest: the hotbar first");
		fillHotbar(player);
		GridService.grabSlot(player, 1);
		check(has(player, Area.POCKETS, Items.CHARCOAL) && CHEST.getItem(1).isEmpty(), "then the pockets");
		int index = GridService.get(player).pockets().indexOf(GridService.get(player).pockets().stream().filter(p -> p.stack().is(Items.CHARCOAL)).findFirst().orElseThrow());
		GridService.quickMove(player, Area.POCKETS, index);
		check(CHEST.countItem(Items.CHARCOAL) == 4 && !has(player, Area.POCKETS, Items.CHARCOAL), "shift-click on a grid item puts it into the open chest");
		player.closeContainer();
	}

	private static void shiftClickOnABagPutsItOn(final MinecraftServer server) {
		ServerPlayer player = reset(server);
		GridService.set(player, GridService.get(player).withBackpack(ItemStack.EMPTY));
		player.getInventory().setItem(3, new ItemStack(MistinnItems.BACKPACKS.get(2)));
		int slot = player.inventoryMenu.slots.stream().filter(s -> s.container == player.getInventory() && s.getContainerSlot() == 3).findFirst().orElseThrow().index;
		GridService.grabSlot(player, slot);
		check(GridService.get(player).backpack().is(MistinnItems.BACKPACKS.get(2)) && player.getInventory().getItem(3).isEmpty(),
			"shift-click on a backpack on the hotbar puts it on when none is worn");
		player.getInventory().setItem(3, new ItemStack(MistinnItems.BACKPACKS.get(1)));
		GridService.grabSlot(player, slot);
		check(player.getInventory().getItem(3).is(MistinnItems.BACKPACKS.get(1)), "with one worn, the second stays on the hotbar");
	}

	private static void sortingLaysItOutAfresh(final MinecraftServer server) {
		ServerPlayer player = reset(server);
		GridService.set(player, GridService.get(player).with(Area.BACKPACK, List.of(
			new Placed(0, 0, 0, false, new ItemStack(Items.ARROW, 10)),
			new Placed(0, 1, 0, false, new ItemStack(Items.IRON_SWORD)),
			new Placed(0, 2, 0, false, new ItemStack(Items.DIRT, 5)),
			new Placed(0, 3, 2, false, new ItemStack(Items.ARROW, 20)))));
		GridService.sort(player, Area.BACKPACK);
		List<Placed> pack = GridService.get(player).items(Area.BACKPACK);
		check(pack.size() == 3, "the two arrow stacks are one after sorting: " + pack);
		check(count(player, Area.BACKPACK, Items.ARROW) == 30 && count(player, Area.BACKPACK, Items.DIRT) == 5
			&& count(player, Area.BACKPACK, Items.IRON_SWORD) == 1, "and nothing is lost: " + pack);
		var shape = GridService.get(player).shape(Area.BACKPACK);
		for (int i = 0; i < pack.size(); i++) {
			Placed placed = pack.get(i);
			check(life.shizune.mistinn.grid.GridLogic.fits(shape, pack, placed.section(), placed.x(), placed.y(), placed.width(), placed.height(), i),
				"every item lies inside the backpack, on no other: " + placed);
		}
		check(pack.getFirst().stack().is(Items.DIRT), "the largest is laid first: " + pack.getFirst());
	}

	/** F, a number key, right-click and Q with the mouse over a pocket on the hunter screen. */
	private static void keysOnTheHunterScreen(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = reset(server);
			pockets(player, Items.APPLE, Items.BREAD, new ItemStack(Items.ARROW, 6), null);
		});
		context.waitTicks(3);
		context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
		context.waitTicks(5);
		mouse(context, Area.POCKETS, 0);
		context.getInput().pressKey(InputConstants.KEY_F);
		context.waitTicks(4);
		world.getServer().runOnServer(server -> check(player(server).getOffhandItem().is(Items.APPLE), "F over a pocket: the apple goes to the offhand"));
		mouse(context, Area.POCKETS, 1);
		context.getInput().pressKey(InputConstants.KEY_2);
		context.waitTicks(4);
		world.getServer().runOnServer(server -> check(player(server).getInventory().getItem(1).is(Items.BREAD), "2 over a pocket: the bread goes to the second hotbar slot"));
		mouse(context, Area.POCKETS, 2);
		context.getInput().pressMouse(RIGHT);
		context.waitTicks(4);
		world.getServer().runOnServer(server -> check(player(server).containerMenu.getCarried().getCount() == 3, "right-click takes half the arrows"));
		mouse(context, Area.POCKETS, 3);
		context.getInput().pressMouse(RIGHT);
		context.waitTicks(4);
		world.getServer().runOnServer(server -> check(pocket(player(server), 3).getCount() == 1, "right-click puts one in the empty pocket"));
		mouse(context, Area.POCKETS, 2);
		context.getInput().pressMouse(LEFT);
		context.waitTicks(4);
		world.getServer().runOnServer(server -> check(pocket(player(server), 2).getCount() == 5 && player(server).containerMenu.getCarried().isEmpty(), "the rest goes back on its stack"));
		context.getInput().pressKey(InputConstants.KEY_Q);
		context.waitTicks(4);
		world.getServer().runOnServer(server -> check(pocket(player(server), 2).getCount() == 4, "Q over the pocket throws one"));
		context.takeScreenshot("mistinn-grid-controls");
		// with something in the backpack and the rig, their sort buttons show
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			GridService.set(player, GridService.get(player).with(Area.BACKPACK, List.of(
				new Placed(0, 0, 0, false, new ItemStack(Items.ARROW, 10)), new Placed(0, 3, 2, false, new ItemStack(Items.ARROW, 20))))
				.with(Area.RIG, List.of(new Placed(0, 0, 0, false, new ItemStack(Items.BREAD, 2)))));
		});
		context.waitTicks(4);
		context.takeScreenshot("mistinn-grid-sort-buttons");
		context.setScreen(() -> null);
		context.waitTicks(3);
	}

	/** Shift-click on a pocket beside an open chest moves it into the chest. */
	private static void shiftClickIntoAnOpenChest(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = reset(server);
			CHEST.clearContent();
			pockets(player, new ItemStack(Items.COAL, 8));
			player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, CHEST), Component.literal("chest")));
		});
		context.waitTicks(6);
		mouse(context, Area.POCKETS, 0);
		// the test input sends no modifier keys with a click, so the panel is handed the shift-click itself
		context.runOnClient(mc -> {
			var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.gui.screen();
			double[] gui = GridPanel.cellCentre(Area.POCKETS, 0, 0, 0);
			check(GridPanel.click(screen.getMenu(), gui[0], gui[1], GridPanel.LEFT, true, false), "the shift-click lands on the panel");
			GridPanel.release(screen.getMenu(), gui[0], gui[1], GridPanel.LEFT);
		});
		context.waitTicks(4);
		world.getServer().runOnServer(server -> {
			check(CHEST.countItem(Items.COAL) == 8, "shift-click beside a chest moves the coal into it: " + CHEST.countItem(Items.COAL));
			check(GridService.get(player(server)).pockets().isEmpty(), "and out of the pocket");
			player(server).closeContainer();
		});
		context.waitTicks(3);
	}

	// ── Helpers ───────────────────────────────────────────────────

	/** Empty hands and hotbar, a tier I backpack and a tier N rig worn, nothing carried. */
	private static ServerPlayer reset(final MinecraftServer server) {
		ServerPlayer player = player(server);
		player.getInventory().clearContent();
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		player.containerMenu.setCarried(ItemStack.EMPTY);
		GridService.set(player, GridInventory.EMPTY.withBackpack(new ItemStack(MistinnItems.BACKPACKS.get(1))).withRig(new ItemStack(MistinnItems.RIGS.getFirst())));
		return player;
	}

	private static void fillHotbar(final ServerPlayer player) {
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			player.getInventory().setItem(i, new ItemStack(FILLERS[i]));
		}
	}

	/** Fills the four pockets in order, null for empty. */
	private static void pockets(final ServerPlayer player, final Object... things) {
		List<Placed> placed = new ArrayList<>();
		for (int i = 0; i < things.length; i++) {
			ItemStack stack = things[i] instanceof Item item ? new ItemStack(item) : things[i] instanceof ItemStack s ? s : ItemStack.EMPTY;
			if (!stack.isEmpty()) {
				placed.add(new Placed(i, 0, 0, false, stack));
			}
		}
		GridService.set(player, GridService.get(player).with(Area.POCKETS, placed));
	}

	private static ItemStack pocket(final ServerPlayer player, final int section) {
		return GridService.get(player).pockets().stream().filter(p -> p.section() == section).map(Placed::stack).findFirst().orElse(ItemStack.EMPTY);
	}

	private static boolean has(final ServerPlayer player, final Area area, final Item item) {
		return count(player, area, item) > 0;
	}

	private static int count(final ServerPlayer player, final Area area, final Item item) {
		return GridService.get(player).items(area).stream().filter(p -> p.stack().is(item)).mapToInt(p -> p.stack().getCount()).sum();
	}

	private static void mouse(final ClientGameTestContext context, final Area area, final int section) {
		double[] at = context.computeOnClient(mc -> {
			double[] gui = GridPanel.cellCentre(area, section, 0, 0);
			check(gui != null, "the grid panel shows " + area + " " + section);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
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
