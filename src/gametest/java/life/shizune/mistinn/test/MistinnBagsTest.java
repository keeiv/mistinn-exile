package life.shizune.mistinn.test;

import java.util.List;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridLogic;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.medical.StarterKit;
import life.shizune.mistinn.registry.MistinnComponents;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** No bag in a bag, old saves' bags taken out, and the starter medicine below Rust Anchor. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnBagsTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			context.waitTicks(10);
			world.getServer().runOnServer(MistinnBagsTest::aNewHunterHasTheStarterKit);
			world.getServer().runOnServer(MistinnBagsTest::noBagGoesIntoAGrid);
			world.getServer().runOnServer(MistinnBagsTest::anOldSavesBagsAreTakenOut);
			world.getServer().runOnServer(MistinnBagsTest::aBagPutOnGivesUpTheBagInside);
			world.getServer().runOnServer(MistinnBagsTest::rustAnchorGivesNoKit);
		}
	}

	private static void aNewHunterHasTheStarterKit(final MinecraftServer server) {
		ServerPlayer player = player(server);
		check(player.getAttachedOrElse(StarterKit.GIVEN, false), "the kit was given on arrival");
		check(count(player, MistinnItems.TOURNIQUET) == 2 && count(player, MistinnItems.SPLINT) == 1
			&& count(player, MistinnItems.PAINKILLERS) == 1 && count(player, MistinnItems.POTENT_INJECTOR) == 1, "two tourniquets, a splint, painkillers and an injector");
		StarterKit.joined(player);
		check(count(player, MistinnItems.TOURNIQUET) == 2, "and only once");
	}

	private static void noBagGoesIntoAGrid(final MinecraftServer server) {
		for (Item bag : MistinnItems.BACKPACKS) {
			check(!GridLogic.canStore(new ItemStack(bag)), "an empty backpack is no longer stored in a grid: " + bag);
		}
		for (Item rig : MistinnItems.RIGS) {
			check(!GridLogic.canStore(new ItemStack(rig)), "nor an empty rig: " + rig);
		}
		check(GridLogic.canStore(new ItemStack(MistinnItems.MASKS.getFirst())) && GridLogic.canStore(new ItemStack(Items.BREAD)), "masks and everything else still are");
		ServerPlayer player = player(server);
		GridService.set(player, GridService.get(player).with(Area.POCKETS, List.of()));
		ItemStack bag = new ItemStack(MistinnItems.BACKPACKS.getFirst());
		check(!GridService.insert(player, bag) && bag.getCount() == 1, "picking one up does not put it in a pocket");
	}

	private static void anOldSavesBagsAreTakenOut(final MinecraftServer server) {
		ServerPlayer player = player(server);
		player.getInventory().clearContent();
		ItemStack worn = new ItemStack(MistinnItems.BACKPACKS.get(2));
		worn.set(MistinnComponents.BACKPACK_CONTENTS, List.of(new Placed(0, 0, 0, false, new ItemStack(MistinnItems.RIGS.get(1))),
			new Placed(0, 2, 0, false, new ItemStack(Items.BREAD))));
		GridInventory old = new GridInventory(List.of(new Placed(0, 0, 0, false, new ItemStack(MistinnItems.BACKPACKS.get(1)))),
			List.of(new Placed(0, 0, 0, false, new ItemStack(MistinnItems.RIGS.get(2)))), worn, ItemStack.EMPTY, ItemStack.EMPTY, GridInventory.SAFE_SIZE);
		GridService.set(player, old);
		check(GridService.unnest(player) == 3, "three bags kept inside the grid are taken out");
		GridInventory now = GridService.get(player);
		for (Area area : Area.values()) {
			for (Placed placed : now.items(area)) {
				check(GridLogic.canStore(placed.stack()), "no bag is left in " + area + ": " + placed.stack());
			}
		}
		check(now.backpack().is(MistinnItems.BACKPACKS.get(2)), "the worn backpack stays worn");
		check(now.items(Area.BACKPACK).size() == 1 && now.items(Area.BACKPACK).getFirst().stack().is(Items.BREAD), "with the rest of what it held");
		check(player.getInventory().countItem(MistinnItems.BACKPACKS.get(1)) == 1 && player.getInventory().countItem(MistinnItems.RIGS.get(1)) == 1
			&& player.getInventory().countItem(MistinnItems.RIGS.get(2)) == 1, "the bags are on the hotbar now");
		check(GridService.unnest(player) == 0, "and there is nothing more to take out");
	}

	private static void aBagPutOnGivesUpTheBagInside(final MinecraftServer server) {
		ServerPlayer player = player(server);
		player.getInventory().clearContent();
		ItemStack bag = new ItemStack(MistinnItems.BACKPACKS.get(3));
		bag.set(MistinnComponents.BACKPACK_CONTENTS, List.of(new Placed(0, 0, 0, false, new ItemStack(MistinnItems.RIGS.getFirst()))));
		player.containerMenu.setCarried(bag);
		GridService.backpackSlot(player);
		check(GridService.get(player).backpack().is(MistinnItems.BACKPACKS.get(3)), "the bag from the chest is put on");
		check(GridService.get(player).items(Area.BACKPACK).isEmpty(), "and the rig it held from an old save comes out");
		check(player.getInventory().countItem(MistinnItems.RIGS.getFirst()) == 1, "onto the hotbar");
	}

	private static void rustAnchorGivesNoKit(final MinecraftServer server) {
		ServerPlayer player = player(server);
		Hardship before = Hardship.of(server);
		server.overworld().setAttached(Hardship.WORLD, Hardship.RUST_ANCHOR);
		player.removeAttached(StarterKit.GIVEN);
		int tourniquets = count(player, MistinnItems.TOURNIQUET);
		StarterKit.joined(player);
		check(count(player, MistinnItems.TOURNIQUET) == tourniquets, "Rust Anchor sends no one off with medicine");
		check(player.getAttachedOrElse(StarterKit.GIVEN, false), "and owes none later");
		server.overworld().setAttached(Hardship.WORLD, before);
	}

	private static int count(final ServerPlayer player, final Item item) {
		int total = player.getInventory().countItem(item);
		GridInventory grid = GridService.get(player);
		for (Area area : Area.values()) {
			for (Placed placed : grid.items(area)) {
				if (placed.stack().is(item)) {
					total += placed.stack().getCount();
				}
			}
		}
		return total;
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
