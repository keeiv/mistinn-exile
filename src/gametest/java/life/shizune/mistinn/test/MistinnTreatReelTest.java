package life.shizune.mistinn.test;

import java.nio.file.Path;
import java.util.Set;
import life.shizune.mistinn.client.medical.TreatmentClient;
import life.shizune.mistinn.client.view.OwnHand;
import life.shizune.mistinn.medical.Treatment;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * 處置的手: films what a hunter sees of their own hands while they treat themself, a few ticks apart: an injector used
 * through the real key until the last one is gone, the same broken off by changing what is held, and every other
 * treatment from start to end. Nothing is asserted; the frames in {@code treat/} are for judging the motion by eye,
 * and where each fist was on the screen, and how far the tool's working point was from the forearm worked on, is
 * printed with them. Runs only when picked with {@code -PonlyTest=MistinnTreatReelTest}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTreatReelTest implements FabricClientGameTest {
	private static final Path OUT = Path.of("treat");

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (!getClass().getSimpleName().equals(System.getProperty("mistinn.only"))) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().above(12).immutable();
				for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, -1, 8))) {
					player.level().setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
				}
				player.setGameMode(GameType.SURVIVAL);
				player.teleportTo(server.overworld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), 180.0F, 0.0F, true);
			});
			context.runOnClient(mc -> mc.mouseHandler.grabMouse());
			context.waitTicks(30);

			// through the real key, the last injector of the stack: it is gone from the hand when the dose is in
			wound(world);
			hold(world, new ItemStack(MistinnItems.POTENT_INJECTOR), new ItemStack(Items.TORCH));
			context.waitTicks(10);
			click(context);
			film(context, "inject", 26, 3);

			// broken off: something else is taken in hand while the needle is in
			wound(world);
			hold(world, new ItemStack(MistinnItems.POTENT_INJECTOR, 4), ItemStack.EMPTY);
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().getInventory().setItem(1, new ItemStack(Items.IRON_SWORD)));
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			context.waitTicks(10);
			click(context);
			film(context, "switch-a", 8, 3);
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
			film(context, "switch-b", 8, 2);
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			context.waitTicks(20);

			Object[][] uses = {
				{Treatment.PILLS, MistinnItems.PAINKILLERS, 40}, {Treatment.TOURNIQUET, MistinnItems.TOURNIQUET, 40},
				{Treatment.SPLINT, MistinnItems.SPLINT, 60}, {Treatment.SURGERY, MistinnItems.SURGERY_KIT, 80},
			};
			for (Object[] use : uses) {
				Treatment treatment = (Treatment) use[0];
				Item item = (Item) use[1];
				int ticks = (Integer) use[2];
				hold(world, new ItemStack(item), ItemStack.EMPTY);
				context.waitTicks(10);
				context.runOnClient(mc -> TreatmentClient.received(new TreatmentService.TreatmentPayload(
					mc.player.getId(), treatment.ordinal(), ticks, Item.getId(item), TreatmentService.RESULT_RUNNING)));
				film(context, treatment.id, ticks / 2, 2);
				context.runOnClient(mc -> TreatmentClient.received(new TreatmentService.TreatmentPayload(mc.player.getId(), -1, 0, 0, TreatmentService.RESULT_DONE)));
				film(context, treatment.id + "-end", 6, 2);
				context.waitTicks(10);
			}
		}
	}

	/** Half of every part's health gone, so there is something to treat. */
	private static void wound(final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			life.shizune.mistinn.body.Body body = life.shizune.mistinn.body.BodyService.get(player);
			for (life.shizune.mistinn.body.BodyPart part : life.shizune.mistinn.body.BodyPart.values()) {
				body = body.with(part, life.shizune.mistinn.body.BodyService.max(player) * 0.5F);
			}
			life.shizune.mistinn.body.BodyService.set(player, body);
		});
	}

	private static void hold(final TestSingleplayerContext world, final ItemStack main, final ItemStack off) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.setItemInHand(InteractionHand.MAIN_HAND, main);
			player.setItemInHand(InteractionHand.OFF_HAND, off);
		});
	}

	/** One press of the use key, as the game hears a mouse click. */
	private static void click(final ClientGameTestContext context) {
		context.runOnClient(mc -> KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mc.options.keyUse)));
	}

	private static void film(final ClientGameTestContext context, final String name, final int frames, final int every) {
		for (int i = 0; i < frames; i++) {
			context.waitTicks(every);
			String frame = name + "-" + (i < 10 ? "0" + i : Integer.toString(i));
			context.takeScreenshot(TestScreenshotOptions.of(frame).disableCounterPrefix().withDestinationDir(OUT));
			String seen = context.computeOnClient(mc -> {
				float aspect = (float) mc.getWindow().getWidth() / mc.getWindow().getHeight();
				return place(OwnHand.seen(InteractionHand.MAIN_HAND, aspect)) + " off " + place(OwnHand.seen(InteractionHand.OFF_HAND, aspect))
					+ String.format(java.util.Locale.ROOT, " point %.2f from the other forearm, %.2f from its own",
						OwnHand.fromForearm(InteractionHand.OFF_HAND), OwnHand.fromForearm(InteractionHand.MAIN_HAND));
			});
			System.out.println("[treat] " + frame + " main " + seen);
		}
	}

	private static String place(final OwnHand.Seen at) {
		if (at == null) {
			return "none";
		}
		return String.format(java.util.Locale.ROOT, "(%.2f, %.2f)%s%s", at.fistX(), at.fistY(), at.fistInView() ? "" : " OUT", at.whole() ? "" : " STUMP");
	}
}
