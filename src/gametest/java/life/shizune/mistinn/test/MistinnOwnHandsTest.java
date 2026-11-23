package life.shizune.mistinn.test;

import java.nio.file.Path;
import java.util.Set;
import life.shizune.mistinn.client.view.OwnHand;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * 自己的手: films what a hunter sees of their own hands, tick by tick, through the real keys: a swing, a block mined,
 * food eaten, a bow drawn, an injector used. Nothing is asserted; the frames in {@code hands/} are for judging the
 * motion by eye, and where each fist was on the screen is printed with them. Runs only when picked with {@code -PonlyTest=MistinnOwnHandsTest}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnOwnHandsTest implements FabricClientGameTest {
	private static final Path OUT = Path.of("hands");

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (!getClass().getSimpleName().equals(System.getProperty("mistinn.only"))) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			BlockPos stage = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().above(12).immutable();
				for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, -1, 8))) {
					player.level().setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
				}
				// a wall to mine, two blocks ahead
				for (BlockPos wall : BlockPos.betweenClosed(pos.offset(-1, 0, 2), pos.offset(1, 2, 2))) {
					player.level().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
				}
				player.setGameMode(GameType.SURVIVAL);
				return pos;
			});
			context.runOnClient(mc -> mc.mouseHandler.grabMouse());

			for (WeaponStyle style : WeaponStyle.values()) {
				hold(world, new ItemStack(MistinnItems.WEAPONS.get(style).get(WeaponMaterial.IRON)), ItemStack.EMPTY);
				look(context, world, stage, 180.0F, 0.0F);
				click(context, true);
				film(context, style.id, style.swingTicks + 6, 1);
			}

			// a heavy blow: drawn back while the use key is held, let go when it is full
			hold(world, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON)), ItemStack.EMPTY);
			look(context, world, stage, 180.0F, 0.0F);
			context.runOnClient(mc -> mc.options.keyUse.setDown(true));
			film(context, "heavy", 10, 2);
			context.runOnClient(mc -> mc.options.keyUse.setDown(false));
			film(context, "heavy_blow", 12, 1);

			hold(world, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.TORCH));
			look(context, world, stage, 0.0F, 10.0F);
			context.runOnClient(mc -> mc.options.keyAttack.setDown(true));
			film(context, "mine", 10, 1);
			context.runOnClient(mc -> mc.options.keyAttack.setDown(false));

			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(6));
			hold(world, new ItemStack(Items.BREAD), ItemStack.EMPTY);
			look(context, world, stage, 180.0F, 0.0F);
			context.runOnClient(mc -> mc.options.keyUse.setDown(true));
			film(context, "eat", 6, 6);
			context.runOnClient(mc -> mc.options.keyUse.setDown(false));

			hold(world, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 8));
			look(context, world, stage, 180.0F, 0.0F);
			context.runOnClient(mc -> mc.options.keyUse.setDown(true));
			film(context, "bow", 5, 6);
			context.runOnClient(mc -> mc.options.keyUse.setDown(false));

			// things that are not flat pictures: a shield raised, a trident drawn back, a block, a crossbow
			hold(world, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON)), new ItemStack(Items.SHIELD));
			look(context, world, stage, 180.0F, 0.0F);
			context.runOnClient(mc -> mc.options.keyUse.setDown(true));
			film(context, "shield", 4, 4);
			context.runOnClient(mc -> mc.options.keyUse.setDown(false));

			hold(world, new ItemStack(Items.TRIDENT), ItemStack.EMPTY);
			look(context, world, stage, 180.0F, 0.0F);
			context.runOnClient(mc -> mc.options.keyUse.setDown(true));
			film(context, "trident", 4, 4);
			context.runOnClient(mc -> mc.options.keyUse.setDown(false));

			hold(world, new ItemStack(Items.STONE), ItemStack.EMPTY);
			look(context, world, stage, 180.0F, 0.0F);
			click(context, true);
			film(context, "block", 4, 1);

			hold(world, new ItemStack(Items.CROSSBOW), new ItemStack(Items.ARROW, 8));
			look(context, world, stage, 180.0F, 0.0F);
			context.runOnClient(mc -> mc.options.keyUse.setDown(true));
			film(context, "crossbow", 4, 6);
			context.runOnClient(mc -> mc.options.keyUse.setDown(false));

			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				life.shizune.mistinn.body.Body body = life.shizune.mistinn.body.BodyService.get(player);
				for (life.shizune.mistinn.body.BodyPart part : life.shizune.mistinn.body.BodyPart.values()) {
					body = body.with(part, life.shizune.mistinn.body.BodyService.max(player) * 0.5F);
				}
				life.shizune.mistinn.body.BodyService.set(player, body);
			});
			hold(world, new ItemStack(MistinnItems.POTENT_INJECTOR), ItemStack.EMPTY);
			look(context, world, stage, 180.0F, 0.0F);
			click(context, false);
			film(context, "inject", 11, 6);
		}
	}

	private static void hold(final TestSingleplayerContext world, final ItemStack main, final ItemStack off) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.setItemInHand(InteractionHand.MAIN_HAND, main);
			player.setItemInHand(InteractionHand.OFF_HAND, off);
		});
	}

	private static void look(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage, final float yaw, final float pitch) {
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
			.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5, Set.of(), yaw, pitch, true));
		context.waitTicks(30);
	}

	/** One press of the attack or the use key, as the game hears a mouse click. */
	private static void click(final ClientGameTestContext context, final boolean attack) {
		context.runOnClient(mc -> KeyMapping.click(KeyMappingHelper.getBoundKeyOf(attack ? mc.options.keyAttack : mc.options.keyUse)));
	}

	private static void film(final ClientGameTestContext context, final String name, final int frames, final int every) {
		for (int i = 0; i < frames; i++) {
			context.waitTicks(every);
			String frame = name + "-" + (i < 10 ? "0" + i : Integer.toString(i));
			context.takeScreenshot(TestScreenshotOptions.of(frame).disableCounterPrefix().withDestinationDir(OUT));
			String fists = context.computeOnClient(mc -> {
				float aspect = (float) mc.getWindow().getWidth() / mc.getWindow().getHeight();
				return place(OwnHand.seen(InteractionHand.MAIN_HAND, aspect)) + " off " + place(OwnHand.seen(InteractionHand.OFF_HAND, aspect));
			});
			System.out.println("[hands] " + frame + " main " + fists);
		}
	}

	private static String place(final OwnHand.Seen at) {
		if (at == null) {
			return "none";
		}
		return String.format(java.util.Locale.ROOT, "(%.2f, %.2f)%s%s", at.fistX(), at.fistY(), at.fistInView() ? "" : " OUT", at.whole() ? "" : " STUMP");
	}
}
