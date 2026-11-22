package life.shizune.mistinn.test;

import life.shizune.mistinn.grid.GridSounds;
import life.shizune.mistinn.grid.GridSounds.Material;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 放進背包的聲音: setting a thing down in the pack sounds like what it is made of, and each material's sound is in the
 * sound list the game loaded.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnGridSoundsTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		// an item's parts and tags are bound only once a world is loaded
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				is(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON), Material.METAL, "an iron blade");
				is(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.WOOD), Material.WOOD, "a wooden blade");
				is(Items.IRON_PICKAXE, Material.METAL, "an iron pickaxe");
				is(MistinnItems.HELMETS.get(3), Material.PLATE, "a tier III helmet");
				is(MistinnItems.HELMETS.get(1), Material.CLOTH, "a tier I cap");
				is(MistinnItems.BACKPACKS.get(1), Material.CLOTH, "a backpack");
				is(MistinnItems.RIGS.get(1), Material.CLOTH, "a chest rig");
				is(MistinnItems.SALT, Material.GRAIN, "salt");
				is(Items.POTION, Material.GLASS, "a potion");
				is(MistinnItems.HUNTER_GUIDE, Material.PAPER, "the hunter's guide");
				is(Items.OAK_PLANKS, Material.WOOD, "oak planks");
				is(Items.APPLE, Material.PLAIN, "an apple");
				for (Material material : Material.values()) {
					check(mc.getSoundManager().getSoundEvent(material.place().location()) != null, material + "'s sound is in the loaded sound list");
					check(mc.getSoundManager().getSoundEvent(material.pickup().location()) != null, material + "'s lifting sound is in the list too");
				}
				// lifted, a blade knocks as metal and a bottle clinks; cloth is the plain rustle it always was
				check(GridSounds.pickup(new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON))) == life.shizune.mistinn.registry.MistinnSounds.GRID_PICKUP_METAL,
					"an iron blade is lifted as metal");
				check(GridSounds.pickup(new ItemStack(Items.POTION)) == life.shizune.mistinn.registry.MistinnSounds.GRID_PICKUP_GLASS, "a potion as glass");
				check(GridSounds.pickup(new ItemStack(MistinnItems.BACKPACKS.get(1))) == life.shizune.mistinn.registry.MistinnSounds.GRID_PICKUP,
					"a backpack with the plain rustle");
			});
		}
	}

	private static void is(final Item item, final Material material, final String what) {
		Material got = GridSounds.material(new ItemStack(item));
		check(got == material, what + " sounds like " + material + " set down, not " + got);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
