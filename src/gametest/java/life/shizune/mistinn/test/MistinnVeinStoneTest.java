package life.shizune.mistinn.test;

import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Vein stone as a mason and a smith use it: four make four vein stone bricks (and four bricks four polished), the
 * stonecutter cuts it to either; and it serves where cobblestone does, for stone tools and the furnace.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnVeinStoneTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				Item vein = MistinnItems.VEIN_STONE;
				Item bricks = MistinnItems.VEIN_STONE_BRICKS;
				craft(level, MistinnItems.VEIN_STONE_BRICKS, 4, vein, vein, Items.AIR, vein, vein, Items.AIR);
				craft(level, MistinnItems.VEIN_STONE_POLISHED, 4, bricks, bricks, Items.AIR, bricks, bricks, Items.AIR);
				for (String cut : List.of("vein_stone_bricks_from_vein_stone_stonecutting", "vein_stone_polished_from_vein_stone_stonecutting",
					"vein_stone_polished_from_vein_stone_bricks_stonecutting")) {
					check(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Mistinn.id(cut))).isPresent(), "the stonecutter: " + cut);
				}
				// as cobblestone does
				Item stick = Items.STICK;
				Item none = Items.AIR;
				craft(level, Items.STONE_PICKAXE, 1, vein, vein, vein, none, stick, none, none, stick, none);
				craft(level, Items.STONE_AXE, 1, vein, vein, none, vein, stick, none, none, stick, none);
				craft(level, Items.STONE_SWORD, 1, none, vein, none, none, vein, none, none, stick, none);
				craft(level, Items.STONE_SHOVEL, 1, none, vein, none, none, stick, none, none, stick, none);
				craft(level, Items.STONE_HOE, 1, vein, vein, none, none, stick, none, none, stick, none);
				craft(level, Items.FURNACE, 1, vein, vein, vein, vein, none, vein, vein, vein, vein);
			});
		}
	}

	/** Fills a crafting grid row by row (three a row; AIR for a gap) and checks what, and how many, it makes. */
	private static void craft(final ServerLevel level, final Item expected, final int count, final Item... grid) {
		int rows = (grid.length + 2) / 3;
		List<ItemStack> stacks = new java.util.ArrayList<>();
		for (Item item : grid) {
			stacks.add(item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
		}
		while (stacks.size() < rows * 3) {
			stacks.add(ItemStack.EMPTY);
		}
		CraftingInput input = CraftingInput.of(3, rows, stacks);
		ItemStack made = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, (ResourceKey<Recipe<?>>) null)
			.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
		check(made.is(expected) && made.getCount() == count, "the grid makes " + count + " " + BuiltInRegistries.ITEM.getKey(expected).getPath()
			+ ": " + made.getCount() + " " + BuiltInRegistries.ITEM.getKey(made.getItem()).getPath());
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn vein stone] ok: " + what);
	}
}
