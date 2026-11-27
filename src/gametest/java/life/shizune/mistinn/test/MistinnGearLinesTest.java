package life.shizune.mistinn.test;

import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.BodyGear;
import life.shizune.mistinn.enemy.NoiseService;
import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.gear.GearBurden;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.tier.Tier;
import life.shizune.mistinn.tier.Tiers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The light and heavy lines: every new piece is there at its tier with a recipe (and among a keeper's spoils); the light gear
 * makes the hunter quieter and eases their breath, the heavy the other way round, slows their swing
 * and weighs on their feet (the rig too); a heavy helmet's visor guards the face; the tooltip says
 * which line made a piece and what it costs; the worn sets on a hunter, for a look.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnGearLinesTest implements FabricClientGameTest {
	private static final float EPSILON = 1.0E-4F;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set noon");

			// every piece, at its tier, with its recipe
			world.getServer().runOnServer(server -> {
				check(MistinnItems.HELMETS.size() == 21 && MistinnItems.ARMORS.size() == 21 && MistinnItems.BACKPACKS.size() == 21 && MistinnItems.RIGS.size() == 14,
					"three helmets, armours and packs and two rigs a tier");
				checkLine(server, MistinnItems.HELMETS, Gear.HELMETS.stream().map(g -> new Piece(g.tier(), g.line())).toList(), "helmet");
				checkLine(server, MistinnItems.ARMORS, Gear.ARMORS.stream().map(g -> new Piece(g.tier(), g.line())).toList(), "armor");
				checkLine(server, MistinnItems.BACKPACKS, Gear.BACKPACKS.stream().map(g -> new Piece(g.tier(), g.line())).toList(), "backpack");
				checkLine(server, MistinnItems.RIGS, Gear.RIGS.stream().map(g -> new Piece(g.tier(), g.line())).toList(), "rig");
				// what a keeper may leave: every line's pieces of the tier
				check(MistinnItems.gearOfTier(3).size() == 11 && MistinnItems.gearOfTier(3).contains(item("armor_heavy_3"))
					&& MistinnItems.gearOfTier(3).contains(item("helmet_light_3")) && MistinnItems.gearOfTier(3).contains(item("rig_heavy_3")),
					"tier III's spoils: three helmets and armours, two rigs, a mask, leggings, boots: " + MistinnItems.gearOfTier(3).size());
				check(MistinnItems.gearOfTier(6).size() == 9, "tier VI's: no leggings or boots: " + MistinnItems.gearOfTier(6).size());
				// the N tier's leather-and-string recipes, where the lines could most easily be taken for each other
				ServerLevel level = server.overworld();
				craft(level, "backpack_light_n", Items.STRING, Items.STRING, Items.STRING, Items.LEATHER, Items.LEATHER, Items.LEATHER);
				craft(level, "backpack_heavy_n", Items.STRING, Items.STRING, Items.STRING, Items.LEATHER, Items.CHEST, Items.LEATHER, Items.LEATHER, Items.LEATHER, Items.LEATHER);
				craft(level, "rig_heavy_n", Items.STRING, Items.AIR, Items.STRING, Items.LEATHER, Items.CHEST, Items.LEATHER, Items.LEATHER, Items.STRING, Items.LEATHER);
			});

			// what the lines cost: nothing worn, then the heavy set, then the light (worn pieces' attributes take hold on the next tick)
			double[] bare = new double[2];
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(Math.abs(GearBurden.noise(player) - 1.0F) < EPSILON && Math.abs(GearBurden.stamina(player) - 1.0F) < EPSILON, "bare, nothing changes");
				bare[0] = NoiseService.radius(player, NoiseService.Noise.SPRINT);
				bare[1] = player.getAttributeValue(Attributes.ATTACK_SPEED);
				wear(player, "helmet_heavy_6", "armor_heavy_6", "backpack_heavy_6", "rig_heavy_6");
			});
			context.waitTicks(2);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				double quiet = bare[0];
				double swing = bare[1];
				check(Math.abs(GearBurden.noise(player) - 1.33F) < EPSILON, "the heavy set is a third louder: " + GearBurden.noise(player));
				check(Math.abs(GearBurden.stamina(player) - 0.73F) < EPSILON, "and stamina comes back at 73%: " + GearBurden.stamina(player));
				check(Math.abs(NoiseService.radius(player, NoiseService.Noise.SPRINT) - quiet * 1.33) < 0.01, "a sprint carries a third further");
				check(player.getAttributeValue(Attributes.ATTACK_SPEED) < swing * 0.92, "the heavy helmet and armour slow the swing: " + player.getAttributeValue(Attributes.ATTACK_SPEED));
				AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
				check(Math.abs(weight(speed) + 0.07) < 1.0E-6, "the coffin and the heavy rig weigh 7% between them: " + weight(speed));
				check(GridService.get(player).shape(life.shizune.mistinn.grid.Area.BACKPACK).cells() == 56, "the coffin holds 56");
				check(BodyGear.faceArmor(player) == 1.0F, "the visor guards the face with no mask: " + BodyGear.faceArmor(player));
				check(GearBurden.set(player) == Gear.Line.HEAVY && GearBurden.wounds(player) == 0.75F, "the whole heavy set turns a quarter of the wounds");
				wear(player, "helmet_light_6", "armor_light_6", "backpack_light_6", null);
			});
			// a second and more in a whole set: what is watched for once a second
			context.waitTicks(25);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
				// the pieces' own 20% quieter and 15% easier, and the whole set's 15% and 10% more
				check(GearBurden.set(player) == Gear.Line.LIGHT && GearBurden.wounds(player) == 1.0F, "a whole light set");
				check(life.shizune.mistinn.achievement.AchievementService.has(player, life.shizune.mistinn.achievement.Achievement.FULL_SET), "a whole set worn a while is remembered: fully kitted");
				check(Math.abs(GearBurden.noise(player) - 0.65F) < EPSILON, "the light set is a third quieter: " + GearBurden.noise(player));
				check(Math.abs(GearBurden.stamina(player) - 1.25F) < EPSILON, "and stamina comes back a quarter faster: " + GearBurden.stamina(player));
				check(weight(speed) == 0.0, "and weighs nothing: " + weight(speed));
				check(BodyGear.faceArmor(player) == 0.0F, "an open helm leaves the face to the mask");
				// a heavy helmet over light armour is no set at all
				player.setItemSlot(EquipmentSlot.HEAD, item("helmet_heavy_6").getDefaultInstance());
				check(GearBurden.set(player) == null, "a mixed set is no set");
			});

			// the tooltip: the line, and what it costs
			check(context.computeOnClient(mc -> {
				List<Component> lines = item("backpack_heavy_6").getDefaultInstance().getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
					TooltipFlag.Default.NORMAL);
				return has(lines, "tooltip.mistinn.line.heavy") && has(lines, "tooltip.mistinn.noise") && has(lines, "tooltip.mistinn.stamina_regen")
					&& has(lines, "tooltip.mistinn.capacity") && has(lines, "tooltip.mistinn.set.heavy");
			}), "a heavy pack's tooltip names its line, its room and its costs");
			check(context.computeOnClient(mc -> {
				List<Component> lines = item("helmet_heavy_3").getDefaultInstance().getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
					TooltipFlag.Default.NORMAL);
				return has(lines, "tooltip.mistinn.face_armor") && has(lines, "tooltip.mistinn.swing");
			}), "a heavy helmet's names its visor and its slower swing");

			// worn, for a look: the heavy set from the front, then the light set from behind
			world.getServer().runOnServer(server -> wear(player(server), "helmet_heavy_5", "armor_heavy_5", "backpack_heavy_5", "rig_heavy_5"));
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("mistinn-gear-heavy");
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);
			context.takeScreenshot("mistinn-gear-heavy-back");
			// the coffin and the heaviest rig in the inventory: every compartment fits on the screen
			world.getServer().runOnServer(server -> wear(player(server), "helmet_heavy_6", "armor_heavy_6", "backpack_heavy_6", "rig_heavy_6"));
			context.waitTicks(5);
			context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(context.computeOnClient(mc -> mc.player)));
			context.waitTicks(5);
			context.takeScreenshot("mistinn-gear-coffin-grid");
			context.setScreen(() -> null);
			world.getServer().runOnServer(server -> wear(player(server), "helmet_light_4", "armor_light_4", "backpack_light_4", null));
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.takeScreenshot("mistinn-gear-light");
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		}
	}

	private record Piece(Tier tier, Gear.Line line) {
	}

	private static void checkLine(final MinecraftServer server, final List<Item> items, final List<Piece> pieces, final String kind) {
		for (int i = 0; i < items.size(); i++) {
			Piece piece = pieces.get(i);
			String name = piece.line().item(kind, piece.tier());
			Item item = items.get(i);
			check(BuiltInRegistries.ITEM.getKey(item).equals(Mistinn.id(name)), kind + " " + i + " is " + name + ": " + BuiltInRegistries.ITEM.getKey(item));
			check(Tiers.of(new ItemStack(item)) == piece.tier(), name + " is tier " + piece.tier());
			check(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Mistinn.id(name))).isPresent(), name + " has a recipe");
		}
	}

	/** Fills a crafting grid row by row (three a row; AIR for a gap) and checks what it makes. */
	private static void craft(final ServerLevel level, final String expected, final Item... grid) {
		int rows = (grid.length + 2) / 3;
		List<ItemStack> stacks = new java.util.ArrayList<>();
		for (Item item : grid) {
			stacks.add(item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
		}
		while (stacks.size() < rows * 3) {
			stacks.add(ItemStack.EMPTY);
		}
		CraftingInput input = CraftingInput.of(3, rows, stacks);
		Item made = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, (ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>) null)
			.map(holder -> holder.value().assemble(input).getItem()).orElse(Items.AIR);
		check(made == item(expected), "the grid makes " + expected + ": " + BuiltInRegistries.ITEM.getKey(made));
	}

	private static void wear(final ServerPlayer player, final String helmet, final String armor, final String pack, final String rig) {
		player.setItemSlot(EquipmentSlot.HEAD, item(helmet).getDefaultInstance());
		player.setItemSlot(EquipmentSlot.CHEST, item(armor).getDefaultInstance());
		GridService.set(player, GridService.get(player).withBackpack(item(pack).getDefaultInstance())
			.withRig(rig == null ? ItemStack.EMPTY : item(rig).getDefaultInstance()));
		GridService.applyWeight(player);
	}

	private static double weight(final AttributeInstance speed) {
		net.minecraft.world.entity.ai.attributes.AttributeModifier weight = speed.getModifier(Mistinn.id("backpack_weight"));
		return weight == null ? 0.0 : weight.amount();
	}

	private static Item item(final String name) {
		return BuiltInRegistries.ITEM.getValue(Mistinn.id(name));
	}

	private static boolean has(final List<Component> lines, final String key) {
		for (Component line : lines) {
			if (line.getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(key)) {
				return true;
			}
			for (Component sibling : line.getSiblings()) {
				if (sibling.getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(key)) {
					return true;
				}
			}
		}
		return false;
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
