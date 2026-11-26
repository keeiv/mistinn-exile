package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.block.RedstoneSoilBlock;
import life.shizune.mistinn.client.team.MarkerClient;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.team.MarkerService;
import life.shizune.mistinn.team.TeamService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The team's marker beacons, shown to every hunter, and the redstone soil that breeds redstone. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnMarkerTest implements FabricClientGameTest {
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
			world.getServer().runOnServer(MistinnMarkerTest::bothAreCraftedFromPlainThings);
			BlockPos beacon = world.getServer().computeOnServer(MistinnMarkerTest::aPlantedBeaconIsTheTeams);
			context.waitTicks(5);
			check(context.computeOnClient(mc -> MarkerClient.markers().size()) == 1, "every hunter is sent the beacon");
			context.waitTicks(20);
			context.takeScreenshot("mistinn-marker-beacon");
			world.getServer().runOnServer(server -> aDyeRecolours(server, beacon));
			world.getServer().runOnServer(server -> breakingItForgetsIt(server, beacon));
			context.waitTicks(5);
			check(context.computeOnClient(mc -> MarkerClient.markers().isEmpty()), "and every hunter forgets it");
			world.getServer().runOnServer(MistinnMarkerTest::theOldestGoesPastTheLimit);
			world.getServer().runOnServer(MistinnMarkerTest::soilRipensAndGivesRedstone);
			world.getServer().runOnServer(MistinnMarkerTest::showcase);
			context.waitTicks(30);
			context.takeScreenshot("mistinn-marker-blocks");
		}
	}

	private static void bothAreCraftedFromPlainThings(final MinecraftServer server) {
		for (String id : List.of("marker_beacon", "redstone_soil")) {
			ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Mistinn.id(id));
			check(server.getRecipeManager().byKey(key).isPresent(), id + " has a recipe");
		}
	}

	private static BlockPos aPlantedBeaconIsTheTeams(final MinecraftServer server) {
		ServerPlayer player = player(server);
		ServerLevel level = server.overworld();
		player.teleportTo(level, player.getX(), player.getY(), player.getZ(), Set.of(), 0.0F, 0.0F, true);
		BlockPos pos = player.blockPosition().offset(0, 0, 8);
		level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
		BlockState state = MistinnBlocks.MARKER_BEACON.defaultBlockState();
		level.setBlockAndUpdate(pos, state);
		ItemStack stack = new ItemStack(MistinnItems.MARKER_BEACON);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("營地"));
		MistinnBlocks.MARKER_BEACON.setPlacedBy(level, pos, state, player, stack);
		List<MarkerService.Marker> markers = MarkerService.markers(server);
		check(markers.size() == 1, "the beacon is kept by the world: " + markers);
		MarkerService.Marker marker = markers.getFirst();
		check(marker.pos().equals(pos) && marker.name().equals("營地"), "where it stands, with the name the anvil gave it: " + marker);
		check(marker.colour() == TeamService.colour(player), "in its planter's colour");
		return pos;
	}

	private static void aDyeRecolours(final MinecraftServer server, final BlockPos pos) {
		ServerPlayer player = player(server);
		ServerLevel level = server.overworld();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.pick(DyeColor.RED), 2));
		BlockState state = level.getBlockState(pos);
		state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		// (the dye's text colour carries an alpha the beacon does not keep)
		check(MarkerService.markers(server).getFirst().colour() == (DyeColor.RED.getTextColor() & 0xFFFFFF), "a dye gives the beacon its colour: "
			+ Integer.toHexString(MarkerService.markers(server).getFirst().colour()));
		check(player.getMainHandItem().getCount() == 1, "and is used up");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	private static void breakingItForgetsIt(final MinecraftServer server, final BlockPos pos) {
		server.overworld().removeBlock(pos, false);
		check(MarkerService.markers(server).isEmpty(), "a beacon taken down is gone from the list");
	}

	private static void theOldestGoesPastTheLimit(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (int i = 0; i <= MarkerService.MAX_MARKERS; i++) {
			MarkerService.placed(level, new BlockPos(1000 + i, 70, 1000), "b" + i, 0xFFFFFF);
		}
		List<MarkerService.Marker> markers = MarkerService.markers(server);
		check(markers.size() == MarkerService.MAX_MARKERS && markers.getFirst().name().equals("b1"), "past the limit the oldest beacon goes first");
		for (MarkerService.Marker marker : markers) {
			MarkerService.removed(level, marker.pos());
		}
	}

	private static void soilRipensAndGivesRedstone(final MinecraftServer server) {
		ServerPlayer player = player(server);
		ServerLevel level = server.overworld();
		BlockPos pos = player.blockPosition().offset(3, -1, 0);
		level.setBlockAndUpdate(pos, MistinnBlocks.REDSTONE_SOIL.defaultBlockState());
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		check(level.getBlockState(pos).useWithoutItem(level, player, hit) == InteractionResult.PASS, "unripe soil gives nothing");
		RandomSource random = RandomSource.create(7L);
		int ticks = 0;
		while (level.getBlockState(pos).getValue(RedstoneSoilBlock.AGE) < RedstoneSoilBlock.RIPE && ticks < 200) {
			level.getBlockState(pos).randomTick(level, pos, random);
			ticks++;
		}
		BlockState ripe = level.getBlockState(pos);
		check(ripe.getValue(RedstoneSoilBlock.AGE) == RedstoneSoilBlock.RIPE, "the soil ripens by itself");
		check(!ripe.isRandomlyTicking() && ripe.getLightEmission() == 7, "ripe, it stops growing and glows");
		ripe.useWithoutItem(level, player, hit);
		int redstone = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0), e -> e.getItem().is(Items.REDSTONE)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
		check(redstone >= RedstoneSoilBlock.YIELD && redstone <= RedstoneSoilBlock.YIELD + RedstoneSoilBlock.EXTRA, "a harvest gives two or three redstone: " + redstone);
		check(level.getBlockState(pos).getValue(RedstoneSoilBlock.AGE) == 0, "and the soil starts again");
	}

	/** The beacon and both kinds of soil in the open, in front of the hunter, for the screenshot. */
	private static void showcase(final MinecraftServer server) {
		ServerPlayer player = player(server);
		ServerLevel level = server.overworld();
		BlockPos at = player.blockPosition().above(40).immutable();
		for (BlockPos floor : BlockPos.betweenClosed(at.offset(-6, -1, -2), at.offset(6, -1, 8))) {
			level.setBlockAndUpdate(floor, Blocks.GRASS_BLOCK.defaultBlockState());
		}
		player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0.0F, 25.0F, true);
		level.setBlockAndUpdate(at.offset(0, 0, 3), MistinnBlocks.MARKER_BEACON.defaultBlockState());
		level.setBlockAndUpdate(at.offset(-2, -1, 3), MistinnBlocks.REDSTONE_SOIL.defaultBlockState());
		level.setBlockAndUpdate(at.offset(2, -1, 3), MistinnBlocks.REDSTONE_SOIL.defaultBlockState().setValue(RedstoneSoilBlock.AGE, RedstoneSoilBlock.RIPE));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.MARKER_BEACON));
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
