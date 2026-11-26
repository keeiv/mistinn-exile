package life.shizune.mistinn.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.client.grid.QuickWheel;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.grid.QuickUse;
import life.shizune.mistinn.medical.InjectorItem;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** The quick-use wheel: medicine and food used where they lie in the pockets, spent only when done, never doubled or lost. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnQuickUseTest implements FabricClientGameTest {
	private static final QuickUse.Spot FIRST = new QuickUse.Spot(Area.POCKETS, 0, 0, 0);
	private static final QuickUse.Spot SECOND = new QuickUse.Spot(Area.POCKETS, 1, 0, 0);
	private static final QuickUse.Spot FOURTH = new QuickUse.Spot(Area.POCKETS, 3, 0, 0);

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty easy");
			world.getServer().runCommand("time set noon");
			context.waitTicks(10);
			world.getServer().runOnServer(MistinnQuickUseTest::onlyUsableThingsAreOffered);
			anInjectionFromThePocket(context, world);
			takenAwayItBreaksOff(context, world);
			eatingFromThePocket(context, world);
			theBowlComesBack(context, world);
			world.getServer().runOnServer(MistinnQuickUseTest::fullHuntersDoNotEat);
			eatenAsFromTheHand(context, world);
			aRepairKitFromThePouch(context, world);
			theWheelPicksWithTheMouse(context, world);
			theMiddlePutsTheWheelAway(context, world);
			aScreenPutsTheWheelAway(context, world);
			context.getInput().pressKey(InputConstants.KEY_B);
			context.waitTicks(3);
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof life.shizune.mistinn.client.guide.HunterGuideScreen), "B opens the guide");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(2);
			deathMidUseDoublesNothing(context, world);
		}
	}

	private static void onlyUsableThingsAreOffered(final MinecraftServer server) {
		ServerPlayer player = player(server);
		pockets(player, MistinnItems.POTENT_INJECTOR, Items.BREAD, Items.STICK, null);
		check(QuickUse.spots(player).equals(List.of(FIRST, SECOND)), "the injector and the bread, not the stick: " + QuickUse.spots(player));
	}

	private static void anInjectionFromThePocket(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			BodyService.set(player, BodyService.get(player).with(BodyPart.TORSO, 20.0F));
			QuickUse.request(player, FIRST);
			check(TreatmentService.active(player), "the injection starts from the pocket");
			check(player.getMainHandItem().isEmpty(), "with nothing taken in hand");
			check(QuickUse.at(player, FIRST).is(MistinnItems.POTENT_INJECTOR), "and the injector still lies there while it runs");
		});
		context.waitTicks(InjectorItem.USE_TICKS + 10);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(!TreatmentService.active(player), "done");
			check(QuickUse.at(player, FIRST).isEmpty(), "the injector is spent where it lay");
			check(count(player, MistinnItems.POTENT_INJECTOR) == 0, "once");
		});
		context.waitTicks(40);
		world.getServer().runOnServer(server -> check(BodyService.get(player(server)).get(BodyPart.TORSO) > 20.0F, "and it heals"));
	}

	private static void takenAwayItBreaksOff(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			pockets(player, MistinnItems.POTENT_INJECTOR, null, null, null);
			BodyService.set(player, BodyService.get(player).with(BodyPart.TORSO, 20.0F));
			QuickUse.request(player, FIRST);
			check(TreatmentService.active(player), "started");
			// moved to another pocket mid-use
			pockets(player, null, null, null, MistinnItems.POTENT_INJECTOR);
		});
		context.waitTicks(3);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(!TreatmentService.active(player), "an item moved away breaks the use off");
			check(QuickUse.at(player, FOURTH).is(MistinnItems.POTENT_INJECTOR) && count(player, MistinnItems.POTENT_INJECTOR) == 1, "and is not spent");
		});
	}

	private static void eatingFromThePocket(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			pockets(player, null, new ItemStack(Items.BREAD, 3), null, null);
			player.getFoodData().setFoodLevel(10);
			QuickUse.request(player, SECOND);
			check(QuickUse.eating(player), "eating from the pocket starts");
		});
		context.waitTicks(40);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(!QuickUse.eating(player), "and ends");
			check(QuickUse.at(player, SECOND).getCount() == 2, "one bread eaten where it lay: " + QuickUse.at(player, SECOND));
			check(player.getFoodData().getFoodLevel() > 10, "and it fed the hunter: " + player.getFoodData().getFoodLevel());
		});
	}

	private static void theBowlComesBack(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			pockets(player, null, null, new ItemStack(Items.MUSHROOM_STEW), null);
			player.getFoodData().setFoodLevel(10);
			QuickUse.request(player, new QuickUse.Spot(Area.POCKETS, 2, 0, 0));
		});
		context.waitTicks(40);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(count(player, Items.MUSHROOM_STEW) == 0, "the stew is eaten");
			check(count(player, Items.BOWL) + player.getInventory().countItem(Items.BOWL) == 1, "and its one bowl comes back");
		});
	}

	private static void fullHuntersDoNotEat(final MinecraftServer server) {
		ServerPlayer player = player(server);
		pockets(player, null, new ItemStack(Items.BREAD), null, null);
		player.getFoodData().setFoodLevel(20);
		QuickUse.request(player, SECOND);
		check(!QuickUse.eating(player) && QuickUse.at(player, SECOND).getCount() == 1, "a full hunter does not eat");
	}

	/** Station food feeds as it does from the hand, and damp food spoils the stomach as it does from the hand. */
	private static void eatenAsFromTheHand(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			pockets(player, MistinnItems.FOODS.get(life.shizune.mistinn.food.StationFood.HARDTACK), null, null, null);
			player.getFoodData().setFoodLevel(8);
			QuickUse.request(player, FIRST);
		});
		// hardtack takes its time: longer than bread
		context.waitTicks(80);
		int[] cap = new int[1];
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(QuickUse.at(player, FIRST).isEmpty() && player.getFoodData().getFoodLevel() > 8, "station food feeds from the pouch");
			cap[0] = life.shizune.mistinn.food.HungerCap.of(player);
			pockets(player, MistinnItems.DAMP_FOOD, null, null, null);
			player.getFoodData().setFoodLevel(4);
			QuickUse.request(player, FIRST);
		});
		context.waitTicks(80);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(QuickUse.at(player, FIRST).isEmpty(), "the damp food is eaten");
			check(life.shizune.mistinn.food.HungerCap.of(player) < cap[0], "and spoils the stomach as from the hand: " + cap[0] + " -> " + life.shizune.mistinn.food.HungerCap.of(player));
		});
	}

	/** A helmet repair kit used from the pouch mends the worn helmet, as the user asked of the wheel. */
	private static void aRepairKitFromThePouch(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			ItemStack helmet = new ItemStack(MistinnItems.HELMETS.get(3));
			helmet.setDamageValue(helmet.getMaxDamage() / 2);
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
			pockets(player, MistinnItems.HELMET_REPAIR_KITS.getFirst(), null, null, null);
			QuickUse.request(player, FIRST);
			check(TreatmentService.active(player), "the repair starts from the pouch");
		});
		context.waitTicks(200);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(!TreatmentService.active(player), "done");
			check(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getDamageValue()
				< player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getMaxDamage() / 2, "the worn helmet is mended");
			check(QuickUse.at(player, FIRST).isEmpty(), "and the kit spent where it lay");
		});
	}

	/** The key held, the pick moved to the bread, the key let go: the bread is eaten. */
	private static void theWheelPicksWithTheMouse(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			pockets(player, Items.STICK, new ItemStack(Items.BREAD, 2), Items.APPLE, null);
			player.getFoodData().setFoodLevel(10);
		});
		context.waitTicks(5);
		context.getInput().holdKey(InputConstants.KEY_R);
		context.waitTicks(3);
		check(context.computeOnClient(mc -> QuickWheel.open()), "holding the key opens the wheel");
		// the test window takes no mouse motion (the view does not turn either), so the wheel is fed the motion the mixin would give it
		context.runOnClient(mc -> QuickWheel.mouse(0.0, -200.0));
		context.waitTicks(2);
		check(context.computeOnClient(mc -> QuickWheel.picked()) == 0, "pulling the mouse up picks the bread at the top");
		context.takeScreenshot("mistinn-quick-wheel");
		context.getInput().releaseKey(InputConstants.KEY_R);
		context.waitTicks(3);
		check(!context.computeOnClient(mc -> QuickWheel.open()), "letting go closes it");
		context.waitTicks(40);
		world.getServer().runOnServer(server -> check(QuickUse.at(player(server), SECOND).getCount() == 1, "and the bread it picked is eaten"));
	}

	/** The pick goes out to the bread and back to the middle: letting go there uses nothing. */
	private static void theMiddlePutsTheWheelAway(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			pockets(player, Items.STICK, new ItemStack(Items.BREAD, 2), Items.APPLE, null);
			player.getFoodData().setFoodLevel(10);
		});
		context.waitTicks(5);
		context.getInput().holdKey(InputConstants.KEY_R);
		context.waitTicks(3);
		check(context.computeOnClient(mc -> QuickWheel.open()), "the wheel opens");
		check(context.computeOnClient(mc -> QuickWheel.inMiddle()), "and the pick starts in the middle");
		context.runOnClient(mc -> QuickWheel.mouse(0.0, -200.0));
		context.waitTicks(2);
		check(context.computeOnClient(mc -> QuickWheel.picked()) == 0, "out to the bread");
		// the pick only reaches so far: bringing it back the length of its reach lands it in the middle
		context.runOnClient(mc -> QuickWheel.mouse(0.0, 120.0));
		context.waitTicks(2);
		check(context.computeOnClient(mc -> QuickWheel.inMiddle() && QuickWheel.picked() == -1), "and back to the middle");
		context.takeScreenshot("mistinn-quick-wheel-middle");
		context.getInput().releaseKey(InputConstants.KEY_R);
		context.waitTicks(3);
		check(!context.computeOnClient(mc -> QuickWheel.open()), "letting go in the middle puts the wheel away");
		context.waitTicks(40);
		world.getServer().runOnServer(server -> check(QuickUse.at(player(server), SECOND).getCount() == 2, "and nothing was eaten"));
	}

	/** A screen opening while the wheel is up puts it away without using what was picked. */
	private static void aScreenPutsTheWheelAway(final ClientGameTestContext context, final TestSingleplayerContext world) {
		context.getInput().holdKey(InputConstants.KEY_R);
		context.waitTicks(3);
		context.runOnClient(mc -> QuickWheel.mouse(0.0, -200.0));
		context.waitTicks(2);
		check(context.computeOnClient(mc -> QuickWheel.picked()) == 0, "the bread is picked");
		context.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
		context.waitTicks(3);
		check(!context.computeOnClient(mc -> QuickWheel.open()), "a screen puts the wheel away");
		context.getInput().releaseKey(InputConstants.KEY_R);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(40);
		world.getServer().runOnServer(server -> check(QuickUse.at(player(server), SECOND).getCount() == 2, "and the bread is still there"));
	}

	private static void deathMidUseDoublesNothing(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			pockets(player, MistinnItems.POTENT_INJECTOR, null, null, null);
			BodyService.set(player, BodyService.get(player).with(BodyPart.TORSO, 20.0F));
			QuickUse.request(player, FIRST);
			check(TreatmentService.active(player), "started");
			player.hurtServer(server.overworld(), server.overworld().damageSources().genericKill(), Float.MAX_VALUE);
		});
		context.waitTicks(20);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			// a hunter's things go into their gravestone
			life.shizune.mistinn.residue.ResidueService.placeNow();
			int dropped = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(8.0),
				e -> e.getItem().is(MistinnItems.POTENT_INJECTOR)).stream().mapToInt(e -> e.getItem().getCount()).sum();
			int kept = 0;
			for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(player.blockPosition().offset(-8, -8, -8), player.blockPosition().offset(8, 8, 8))) {
				if (server.overworld().getBlockEntity(pos) instanceof life.shizune.mistinn.block.GravestoneBlockEntity stone) {
					kept += stone.items().stream().filter(k -> k.stack().is(MistinnItems.POTENT_INJECTOR)).mapToInt(k -> k.stack().getCount()).sum();
				}
			}
			check(dropped + kept + count(player, MistinnItems.POTENT_INJECTOR) == 1, "dying mid-use the injector is neither spent nor doubled: dropped "
				+ dropped + ", in the gravestone " + kept);
		});
		context.runOnClient(mc -> mc.player.respawn());
		context.waitTicks(20);
	}

	// ── Helpers ───────────────────────────────────────────────────

	/** Fills the four pockets, null for empty. */
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

	private static int count(final ServerPlayer player, final Item item) {
		GridInventory grid = GridService.get(player);
		int total = 0;
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
