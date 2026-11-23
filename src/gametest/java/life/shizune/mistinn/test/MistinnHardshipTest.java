package life.shizune.mistinn.test;

import java.util.List;
import life.shizune.mistinn.block.GravestoneBlockEntity;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridLogic;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.food.HungerCap;
import life.shizune.mistinn.food.StationFood;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.hardship.RustAnchorDeath;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnComponents;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.residue.Kept;
import life.shizune.mistinn.residue.ResidueService;
import life.shizune.mistinn.salt.SaltService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A Rust Anchor world end to end: the choice reaches the world and the client, blows land
 * harder, only the station's food can be eaten, the safe box is 3x3, a death wears the carried gear down and keeps only part of the
 * salt, and the hunter comes back weak and hungry.
 */
public class MistinnHardshipTest implements FabricClientGameTest {
	private static final int LEFT = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet") || Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		// the choice made on the hardship screen, as the player would make it
		Hardship.choose(Hardship.RUST_ANCHOR);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				check(Hardship.of(server) == Hardship.RUST_ANCHOR, "the world is a Rust Anchor world");
				check(server.getWorldData().getDifficulty() == Difficulty.HARD, "and plays on hard");
				check(GridService.get(player(server)).safeSize() == GridInventory.RUST_ANCHOR_SAFE_SIZE, "the hunter keeps a 3x3 safe box");
				GridInventory inv = GridService.get(player(server));
				check(GridLogic.fits(inv.shape(Area.SAFE), List.of(), 0, 2, 2, 1, 1, -1), "the safe box has a third row and column");
			});
			check(context.computeOnClient(mc -> mc.player.getAttached(Hardship.SEEN) == Hardship.RUST_ANCHOR), "the client knows the world's hardship");

			// the hunter screen makes room for the larger safe box
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				GridService.set(player, GridService.get(player).with(Area.SAFE, List.of(
					new Placed(0, 0, 0, false, new ItemStack(Items.IRON_SWORD)),
					new Placed(0, 2, 2, false, new ItemStack(Items.DIAMOND, 3)))));
			});
			context.waitTicks(4);
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(10);
			context.takeScreenshot("hardship-safe-box");
			context.setScreen(() -> null);

			// blows land harder: a plain hit on the torso takes a quarter more
			float lost = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				float before = BodyService.get(player).get(BodyPart.TORSO);
				player.hurtServer(server.overworld(), player.damageSources().generic(), 4.0F);
				return before - BodyService.get(player).get(BodyPart.TORSO);
			});
			float expected = 4.0F * BodyService.DAMAGE_SCALE * Hardship.RUST_ANCHOR.damageTaken;
			check(Math.abs(lost - expected) < 0.01F, "a 4-point hit takes " + expected + " from the torso: " + lost);

			// ── Food ─────────────────────────────────────────────────
			// ordinary food goes damp in the hunter's hands, anywhere they keep it; the station's does not
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.getInventory().setItem(5, new ItemStack(Items.BREAD, 3));
				player.getInventory().setItem(6, new ItemStack(MistinnItems.FOODS.get(StationFood.PIG_FAT), 2));
				GridService.set(player, GridService.get(player).with(Area.POCKETS, List.of(new Placed(0, 0, 0, false, new ItemStack(Items.APPLE)))));
			});
			context.waitTicks(2);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				ItemStack bread = player.getInventory().getItem(5);
				check(bread.is(MistinnItems.DAMP_FOOD) && bread.getCount() == 3, "bread in hand goes damp, all three: " + bread);
				check(player.getInventory().getItem(6).is(MistinnItems.FOODS.get(StationFood.PIG_FAT)), "the station's pig fat does not");
				check(GridService.get(player).pockets().getFirst().stack().is(MistinnItems.DAMP_FOOD), "an apple in a pocket goes damp too");
				// in a normal world nothing spoils: the same hunter, the world's hardship turned for a moment
				server.overworld().setAttached(Hardship.WORLD, Hardship.NORMAL);
				player.getInventory().setItem(7, new ItemStack(Items.COOKED_BEEF));
			});
			context.waitTicks(3);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.getInventory().getItem(7).is(Items.COOKED_BEEF), "in a normal world a steak stays a steak");
				server.overworld().setAttached(Hardship.WORLD, Hardship.RUST_ANCHOR);
			});
			context.waitTicks(3);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.getInventory().getItem(7).is(MistinnItems.DAMP_FOOD), "and in Rust Anchor it goes damp");
				// a damp meal takes a drumstick for good
				player.getFoodData().setFoodLevel(4);
				new ItemStack(MistinnItems.DAMP_FOOD).finishUsingItem(server.overworld(), player);
				check(HungerCap.of(player) == HungerCap.FULL - HungerCap.SPOIL, "a damp meal takes a drumstick: " + HungerCap.of(player));
				player.getFoodData().setFoodLevel(HungerCap.FULL);
			});
			context.waitTicks(2);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				int cap = HungerCap.FULL - HungerCap.SPOIL;
				check(player.getFoodData().getFoodLevel() == cap, "the stomach holds no more than its cap: " + player.getFoodData().getFoodLevel());
				check(!player.canEat(false), "and a full one cannot eat");
				for (int i = 0; i < 10; i++) {
					new ItemStack(MistinnItems.DAMP_FOOD).finishUsingItem(server.overworld(), player);
				}
				check(HungerCap.of(player) == HungerCap.FLOOR, "it shrinks no further than four drumsticks: " + HungerCap.of(player));
				// the station's food fills it as far as it goes
				player.getFoodData().setFoodLevel(2);
				new ItemStack(MistinnItems.FOODS.get(StationFood.PIG_FAT)).finishUsingItem(server.overworld(), player);
				check(player.getFoodData().getFoodLevel() == 4, "pig fat feeds two points: " + player.getFoodData().getFoodLevel());
				check(player.canEat(false), "and there is room for more");
				for (int slot = 5; slot <= 7; slot++) {
					player.getInventory().setItem(slot, ItemStack.EMPTY);
				}
				GridService.set(player, GridService.get(player).with(Area.POCKETS, List.of()));
			});
			context.waitTicks(3);
			check(context.computeOnClient(mc -> HungerCap.of(mc.player) == HungerCap.FLOOR), "the client sees the shrunken stomach");
			context.takeScreenshot("hardship-hunger-bar");

			// animals give the station's scraps instead of their meat; one that dies burning, cooked ones
			BlockPos field = world.getServer().computeOnServer(server -> player(server).blockPosition());
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				net.minecraft.world.entity.animal.pig.Pig pig = net.minecraft.world.entity.EntityTypes.PIG.create(server.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				pig.snapTo(field.getX() + 4.5, field.getY(), field.getZ() + 0.5, 0.0F, 0.0F);
				server.overworld().addFreshEntity(pig);
				pig.hurtServer(server.overworld(), player.damageSources().playerAttack(player), 100.0F);
				net.minecraft.world.entity.animal.pig.Pig burning = net.minecraft.world.entity.EntityTypes.PIG.create(server.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				burning.snapTo(field.getX() - 4.5, field.getY(), field.getZ() + 0.5, 0.0F, 0.0F);
				server.overworld().addFreshEntity(burning);
				burning.igniteForSeconds(5.0F);
				burning.hurtServer(server.overworld(), player.damageSources().playerAttack(player), 100.0F);
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				var drops = server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(field).inflate(8.0));
				check(drops.stream().noneMatch(d -> d.getItem().is(Items.PORKCHOP) || d.getItem().is(Items.COOKED_PORKCHOP)), "no pork drops: " + drops);
				check(drops.stream().anyMatch(d -> d.getItem().is(MistinnItems.FOODS.get(StationFood.PIG_FAT))), "a pig gives pig fat");
				check(drops.stream().anyMatch(d -> d.getItem().is(MistinnItems.FOODS.get(StationFood.PORK_CRACKLINGS))), "a burning pig gives cracklings");
				// picked up, the station's food stays as it is
				ServerPlayer player = player(server);
				drops.stream().filter(d -> d.getItem().is(MistinnItems.FOODS.get(StationFood.PIG_FAT))).findFirst().ifPresent(d -> d.teleportTo(player.getX(), player.getY(), player.getZ()));
			});
			context.waitTicks(30);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				// a pickup goes to the hotbar or into the grid
				Item fat = MistinnItems.FOODS.get(StationFood.PIG_FAT);
				int slot = player.getInventory().findSlotMatchingItem(new ItemStack(fat));
				GridInventory grid = GridService.get(player);
				boolean inGrid = java.util.Arrays.stream(Area.values()).flatMap(a -> grid.items(a).stream()).anyMatch(placed -> placed.stack().is(fat));
				check(slot >= 0 || inGrid, "the hunter picked the pig fat up and it is still pig fat");
				if (slot >= 0) {
					player.getInventory().setItem(slot, ItemStack.EMPTY);
				}
				GridService.set(player, grid.with(Area.POCKETS, List.of()));
				// a cake is ordinary food: it cannot be eaten from the block
				BlockPos cake = field.offset(0, 0, 3);
				server.overworld().setBlockAndUpdate(cake, net.minecraft.world.level.block.Blocks.CAKE.defaultBlockState());
				var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(cake), net.minecraft.core.Direction.UP, cake, false);
				var result = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(player, server.overworld(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
				check(result == net.minecraft.world.InteractionResult.FAIL, "a cake cannot be eaten: " + result);
				server.overworld().setBlockAndUpdate(cake, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			});

			// a death wears down what was carried and keeps only part of the salt
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
				ItemStack worn = new ItemStack(Items.IRON_SWORD);
				worn.setDamageValue(200);
				player.getInventory().setItem(2, worn);
				player.getInventory().setItem(3, new ItemStack(MistinnItems.SURGERY_KIT));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				ItemStack pack = new ItemStack(MistinnItems.BACKPACKS.get(6));
				pack.set(MistinnComponents.BACKPACK_CONTENTS, List.of(new Placed(0, 0, 0, false, new ItemStack(Items.IRON_PICKAXE))));
				GridService.set(player, GridService.get(player).withBackpack(pack));
				SaltService.set(player, 100);
			});
			world.getServer().runCommand("kill @a");
			context.waitForScreen(DeathScreen.class);
			world.getServer().runOnServer(server -> ResidueService.placeNow());
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				BlockPos at = player.getAttached(MistinnAttachments.DEATH_MARK).pos().pos();
				check(server.overworld().getBlockState(at).is(MistinnBlocks.GRAVESTONE), "a gravestone stands where the hunter fell");
				GravestoneBlockEntity stone = (GravestoneBlockEntity) server.overworld().getBlockEntity(at);
				check(stone.salt() == 60, "the stone keeps six tenths of the salt: " + stone.salt());
				List<Kept> kept = stone.items();
				check(damage(kept, 0) == 150, "a whole sword loses six tenths of its durability: " + damage(kept, 0));
				check(damage(kept, 2) == 249, "a worn sword is left with one point, not broken: " + damage(kept, 2));
				check(damage(kept, 3) == 0, "a surgery kit's doses are not gear: " + damage(kept, 3));
				int chestWear = Math.round(Items.IRON_CHESTPLATE.getDefaultInstance().getMaxDamage() * RustAnchorDeath.WEAR);
				check(kept.stream().anyMatch(k -> k.stack().is(Items.IRON_CHESTPLATE) && k.stack().getDamageValue() == chestWear), "the worn chestplate too");
				ItemStack pack = kept.stream().filter(k -> k.where().equals(Kept.BACKPACK)).findFirst().orElseThrow().stack();
				ItemStack pick = pack.get(MistinnComponents.BACKPACK_CONTENTS).getFirst().stack();
				check(pick.getDamageValue() == Math.round(pick.getMaxDamage() * RustAnchorDeath.WEAR), "and what the backpack held: " + pick.getDamageValue());
				GridInventory grid = GridService.get(player);
				check(grid.safeSize() == 3 && grid.safe().stream().anyMatch(p -> p.stack().is(Items.IRON_SWORD) && p.stack().getDamageValue() == 0),
					"the safe box keeps its size and its sword untouched");
			});

			// back from the dead: half of every part and an empty stomach
			context.waitFor(mc -> mc.gui.screen() instanceof life.shizune.mistinn.client.death.MissionEndScreen, 400);
			// the death scene plays out before the choices can be taken: 5.6 seconds from the fall
			context.waitFor(mc -> life.shizune.mistinn.client.death.DeathScene.seconds() > life.shizune.mistinn.client.death.DeathScene.SCREEN + 1.8F, 400);
			context.waitTicks(5);
			context.takeScreenshot("hardship-mission-end");
			returnToMission(context);
			context.waitFor(mc -> mc.player != null && mc.player.isAlive(), 200);
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				float half = BodyService.max(player) * RustAnchorDeath.RESPAWN_BODY;
				for (BodyPart part : BodyPart.values()) {
					// natural recovery may have given a point back since
					float now = BodyService.get(player).get(part);
					check(now >= half - 0.01F && now <= half + 2.01F, part.id + " comes back at half: " + now);
				}
				check(player.getFoodData().getFoodLevel() == RustAnchorDeath.RESPAWN_FOOD, "and hungry: " + player.getFoodData().getFoodLevel());
				check(GridService.get(player).safeSize() == 3, "the safe box is still 3x3");
				check(HungerCap.of(player) == HungerCap.FULL, "and the stomach is whole again");
			});
		}
	}

	/** Damage of the item kept from a hotbar slot. */
	private static int damage(final List<Kept> kept, final int slot) {
		return kept.stream().filter(k -> k.where().equals(Kept.SLOT) && k.index() == slot).findFirst().orElseThrow().stack().getDamageValue();
	}

	private static void returnToMission(final ClientGameTestContext context) {
		double[] at = context.computeOnClient(mc -> {
			double[] gui = ((life.shizune.mistinn.client.death.MissionEndScreen) mc.gui.screen()).choiceCentre(0);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		// the first move a window sees is only taken as where the pointer starts: move once before
		context.getInput().setCursorPos(at[0] - 40.0, at[1] - 40.0);
		context.waitTicks(2);
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(LEFT);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
		System.out.println("[mistinn hardship] ok: " + message);
	}
}
