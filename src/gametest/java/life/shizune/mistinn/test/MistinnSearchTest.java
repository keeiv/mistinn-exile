package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.client.expedition.SearchScreen;
import life.shizune.mistinn.expedition.Cache;
import life.shizune.mistinn.expedition.SearchMenu;
import life.shizune.mistinn.grid.Carried;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Searching a container: opened by right-clicking it as a hunter would, it is a search, not a barrel. What has not been
 * searched cannot be got at by any way a slot can be (taken, shift-clicked, swapped with the hotbar, thrown, grabbed by
 * the grid panel): it is not there to the hunter, though its footprint shows. Things are searched one at a time in
 * reading order, each as long as its cells and tier make it; once searched, a thing can be taken. What was searched, and
 * how far the search had got, survives the container's chunk unloading. Pictures of the three states.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnSearchTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 6000");
			context.runOnClient(mc -> mc.options.guiScale().set(2));
			context.waitTicks(20);
			BlockPos[] barrel = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos far = player.blockPosition().offset(300, 0, 0);
				level.getChunk(far.getX() >> 4, far.getZ() >> 4);
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, far);
				System.out.println("[mistinn search] the barrel stands at " + ground);
				barrel[0] = ground;
				level.setBlock(ground, Blocks.BARREL.defaultBlockState(), 3);
				BlockEntity entity = level.getBlockEntity(ground);
				entity.setAttached(Cache.CACHE, Cache.pack(6, 4, List.of(new ItemStack(MistinnItems.TOURNIQUET), new ItemStack(Items.SHIELD),
					new ItemStack(MistinnItems.SPLINT), new ItemStack(MistinnItems.SALT, 9), new ItemStack(Items.IRON_INGOT, 5),
					new ItemStack(MistinnItems.SURGERY_KIT))));
				Cache cache = entity.getAttached(Cache.CACHE);
				check(cache.entries().size() == 6, "everything fits in a six by four");
				check(cache.entries().getFirst().x() == 0 && cache.entries().getFirst().y() == 0, "laid out from the top left, the biggest first");
				// stand two blocks south, looking at it
				player.teleportTo(level, ground.getX() + 0.5, ground.getY(), ground.getZ() + 2.5, Set.of(), 180.0F, 30.0F, true);
			});
			context.waitTicks(20);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			String opened = context.computeOnClient(mc -> (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName())
				+ " aiming at " + mc.hitResult + " " + (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit ? hit.getBlockPos() : ""));
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof SearchScreen), "right-clicked, it opens as a search, not a barrel: " + opened);
			context.takeScreenshot(TestScreenshotOptions.of("search-unsearched").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("search")));
			int[] first = new int[1];
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.containerMenu instanceof SearchMenu, "the hunter has the search open");
				SearchMenu menu = (SearchMenu) player.containerMenu;
				BlockEntity entity = server.overworld().getBlockEntity(barrel[0]);
				Cache.Entry thing = entity.getAttached(Cache.CACHE).entries().getFirst();
				// what is left of the first one's search, from where it has got to (the picture took a few ticks)
				first[0] = Cache.ticks(thing.stack(), thing.w() * thing.h()) - entity.getAttached(Cache.CACHE).progress();
				check(menu.getSlot(0).getItem().isEmpty(), "what is not searched is not there to the hunter");
				int had = Carried.count(player, thing.stack().getItem());
				ItemStack hotbar = player.getInventory().getItem(0).copy();
				menu.clicked(0, 0, ContainerInput.PICKUP, player);
				check(menu.getCarried().isEmpty(), "it cannot be taken");
				menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
				menu.clicked(0, 0, ContainerInput.SWAP, player);
				menu.clicked(0, 1, ContainerInput.THROW, player);
				GridService.grabSlot(player, 0);
				check(Carried.count(player, thing.stack().getItem()) == had && ItemStack.matches(player.getInventory().getItem(0), hotbar),
					"nor shift-clicked, swapped or grabbed");
				check(server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(barrel[0]).inflate(8.0)).isEmpty(), "nor thrown");
				check(entity.getAttached(Cache.CACHE).entries().getFirst().stack().getCount() == thing.stack().getCount(), "and it is all still there");
			});
			check(first[0] > 8, "the first is not searched in a moment: " + first[0] + " ticks still to go");
			context.waitTicks(first[0] - 6);
			world.getServer().runOnServer(server -> check(!server.overworld().getBlockEntity(barrel[0]).getAttached(Cache.CACHE).entries().getFirst().searched(),
				"the first takes its time"));
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				Cache cache = server.overworld().getBlockEntity(barrel[0]).getAttached(Cache.CACHE);
				check(cache.entries().getFirst().searched() && !cache.entries().get(1).searched(), "then it is searched, and the next one is begun");
			});
			context.waitTicks(4);
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof SearchScreen screen && screen.getMenu().searched(0)
				&& !screen.getMenu().getSlot(0).getItem().isEmpty()), "the hunter's screen shows it");
			context.takeScreenshot(TestScreenshotOptions.of("search-searching").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("search")));
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				SearchMenu menu = (SearchMenu) player.containerMenu;
				ItemStack expected = server.overworld().getBlockEntity(barrel[0]).getAttached(Cache.CACHE).entries().getFirst().stack().copy();
				menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
				check(Carried.count(player, expected.getItem()) >= expected.getCount(), "once searched it can be taken: " + expected);
				check(server.overworld().getBlockEntity(barrel[0]).getAttached(Cache.CACHE).entries().getFirst().stack().isEmpty(), "and it is gone from the cache");
				player.closeContainer();
			});
			// the chunk unloads; what was searched stays searched
			Cache[] before = new Cache[1];
			world.getServer().runOnServer(server -> {
				before[0] = server.overworld().getBlockEntity(barrel[0]).getAttached(Cache.CACHE);
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), barrel[0].getX() + 900.5, barrel[0].getY() + 40.0, barrel[0].getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				player.getAbilities().mayfly = true;
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
			});
			boolean unloaded = false;
			for (int i = 0; i < 60 && !unloaded; i++) {
				context.waitTicks(20);
				unloaded = world.getServer().computeOnServer(server -> !server.overworld().getChunkSource().hasChunk(barrel[0].getX() >> 4, barrel[0].getZ() >> 4));
			}
			check(unloaded, "the container's chunk unloads");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), barrel[0].getX() + 0.5, barrel[0].getY(), barrel[0].getZ() + 2.5, Set.of(), 180.0F, 30.0F, true);
			});
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				Cache after = server.overworld().getBlockEntity(barrel[0]).getAttached(Cache.CACHE);
				check(after != null && after.entries().size() == before[0].entries().size(), "back, the cache is still there");
				for (int i = 0; i < after.entries().size(); i++) {
					check(after.entries().get(i).searched() == before[0].entries().get(i).searched()
						&& after.entries().get(i).stack().getCount() == before[0].entries().get(i).stack().getCount(), "thing " + i + " as it was");
				}
				check(after.progress() == before[0].progress(), "and the search got as far: " + after.progress());
			});
			context.getInput().pressKey(options -> options.keyUse);
			int[] rest = new int[1];
			world.getServer().runOnServer(server -> {
				Cache cache = server.overworld().getBlockEntity(barrel[0]).getAttached(Cache.CACHE);
				for (Cache.Entry thing : cache.entries()) {
					rest[0] += thing.searched() || thing.stack().isEmpty() ? 0 : Cache.ticks(thing.stack(), thing.w() * thing.h());
				}
			});
			context.waitTicks(rest[0] + 20);
			world.getServer().runOnServer(server -> {
				Cache cache = server.overworld().getBlockEntity(barrel[0]).getAttached(Cache.CACHE);
				check(cache.next() < 0, "left open, everything is searched in the end (" + rest[0] + " ticks)");
			});
			context.waitTicks(4);
			context.takeScreenshot(TestScreenshotOptions.of("search-done").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("search")));
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
		System.out.println("[mistinn search] ok: " + what);
	}
}
