package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.client.grid.GridSprites;
import life.shizune.mistinn.expedition.Cache;
import life.shizune.mistinn.expedition.DamGround;
import life.shizune.mistinn.expedition.Salvage;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.Carried;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.ItemSizes;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.tier.Tier;
import life.shizune.mistinn.tier.Tiers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The forebears' salvage: eighteen pieces, each at the tier and in the footprint its table gives, the bigger ones drawn
 * over their footprint in the grid. None of it goes in the safe box, by any way in; a pocket takes it. The tillers buy
 * it at its worth. The dam's crates always hold some, its safe only the finer pieces. A picture of all of it, searched.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnSalvageTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		check(MistinnItems.SALVAGE.size() == 18 && Salvage.KINDS.size() == 18, "eighteen pieces");
		check(context.computeOnClient(mc -> {
			for (int i = 0; i < Salvage.KINDS.size(); i++) {
				Salvage.Kind kind = Salvage.KINDS.get(i);
				if (kind.w() * kind.h() > 1 && GridSprites.of(MistinnItems.SALVAGE.get(i)) == null) {
					return false;
				}
			}
			return true;
		}), "every piece bigger than a cell has its grid art");
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			context.runOnClient(mc -> mc.options.guiScale().set(2));
			context.waitTicks(20);
			BlockPos[] barrel = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				for (int i = 0; i < Salvage.KINDS.size(); i++) {
					Salvage.Kind kind = Salvage.KINDS.get(i);
					ItemStack stack = new ItemStack(MistinnItems.SALVAGE.get(i));
					int[] size = ItemSizes.of(stack);
					check(size[0] == kind.w() && size[1] == kind.h() && Tiers.of(stack) == kind.tier() && Salvage.value(stack) == kind.value(),
						kind.id() + ": " + kind.tier().label + ", " + kind.w() + "x" + kind.h() + ", " + kind.value() + " salt");
				}
			});
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				ItemStack lens = new ItemStack(MistinnItems.SALVAGE.getLast());
				player.containerMenu.setCarried(lens.copy());
				GridService.place(player, Area.SAFE, 0, 0, 0, false);
				GridService.placeOne(player, Area.SAFE, 0, 0, 0, false);
				check(GridService.get(player).items(Area.SAFE).isEmpty() && !player.containerMenu.getCarried().isEmpty(),
					"none of it goes in the safe box, put or put one by one");
				ItemStack carried = player.containerMenu.getCarried().copy();
				player.containerMenu.setCarried(ItemStack.EMPTY);
				ItemStack hotbar = player.getInventory().getItem(8).copy();
				player.getInventory().setItem(8, carried);
				GridService.swap(player, Area.SAFE, 0, 0, 0, 8, false);
				check(GridService.get(player).items(Area.SAFE).isEmpty() && Salvage.is(player.getInventory().getItem(8)), "nor swapped in from the hotbar");
				player.getInventory().setItem(8, hotbar);
				player.containerMenu.setCarried(new ItemStack(MistinnItems.SALVAGE.getFirst()));
				GridService.place(player, Area.POCKETS, 3, 0, 0, false);
				check(player.containerMenu.getCarried().isEmpty(), "a pocket takes it");
				// nor in a pack: a pack does not go in the grid at all, salvage in it or not
				ItemStack pack = new ItemStack(MistinnItems.BACKPACKS.getFirst());
				pack.set(life.shizune.mistinn.registry.MistinnComponents.BACKPACK_CONTENTS,
					List.of(new life.shizune.mistinn.grid.Placed(0, 0, 0, false, new ItemStack(MistinnItems.SALVAGE.getLast()))));
				player.containerMenu.setCarried(pack);
				GridService.place(player, Area.SAFE, 0, 0, 0, false);
				check(GridService.get(player).items(Area.SAFE).isEmpty() && !player.containerMenu.getCarried().isEmpty(), "nor in a pack put in the safe box");
				player.containerMenu.setCarried(ItemStack.EMPTY);
				// the tillers buy it all at its worth
				Carried.give(player, new ItemStack(MistinnItems.SALVAGE.getFirst(), 2));
				Carried.give(player, lens.copy());
				int salt = SaltService.get(player);
				int paid = Salvage.sell(player);
				check(paid == 3 * 2 + 700 && SaltService.get(player) - salt == paid, "sold for its worth: " + paid);
				check(Carried.count(player, MistinnItems.SALVAGE.getFirst()) == 0 && Carried.count(player, MistinnItems.SALVAGE.getLast()) == 0, "and it is gone");

				// the dam's containers
				ServerLevel level = server.overworld();
				BlockPos far = player.blockPosition().offset(300, 0, 0);
				level.getChunk(far.getX() >> 4, far.getZ() >> 4);
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, far);
				barrel[0] = ground;
				level.setBlock(ground, Blocks.BARREL.defaultBlockState(), 3);
				RandomSource random = RandomSource.create(5L);
				for (int roll = 0; roll < 20; roll++) {
					Cache.stock(level, ground, DamGround.CRATE, 6, 4, random);
					Cache crate = level.getBlockEntity(ground).getAttached(Cache.CACHE);
					check(crate.entries().size() >= 3 && crate.entries().stream().anyMatch(e -> Salvage.is(e.stack())), "a crate holds salvage (" + roll + ")");
					Cache.stock(level, ground, DamGround.STRONGBOX, 4, 4, random);
					Cache safe = level.getBlockEntity(ground).getAttached(Cache.CACHE);
					check(safe.entries().stream().filter(e -> Salvage.is(e.stack())).allMatch(e -> Tiers.of(e.stack()).ordinal() >= Tier.III.ordinal())
						&& safe.entries().stream().anyMatch(e -> Salvage.is(e.stack())), "the safe only the finer pieces (" + roll + ")");
				}
				// all of it, searched, for the picture
				List<ItemStack> all = new ArrayList<>();
				MistinnItems.SALVAGE.forEach(item -> all.add(new ItemStack(item)));
				Cache packed = Cache.pack(9, 8, all);
				List<Cache.Entry> found = new ArrayList<>();
				packed.entries().forEach(e -> found.add(new Cache.Entry(e.x(), e.y(), e.w(), e.h(), e.stack(), true)));
				check(found.size() == 18, "all eighteen fit a nine by eight");
				level.getBlockEntity(ground).setAttached(Cache.CACHE, new Cache(9, 8, List.copyOf(found), 0, 0L));
				player.teleportTo(level, ground.getX() + 0.5, ground.getY(), ground.getZ() + 2.5, Set.of(), 180.0F, 30.0F, true);
			});
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("salvage-searched").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("salvage")));
			context.runOnClient(mc -> {
				mc.gui.setScreen(null);
				mc.options.guiScale().set(0);
			});
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn salvage] ok: " + what);
	}
}
