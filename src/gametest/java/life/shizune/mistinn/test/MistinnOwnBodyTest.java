package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.client.view.OwnBody;
import life.shizune.mistinn.client.view.ViewMotion;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * 自己的身體: through their own eyes a hunter has a body, and their hands hang at their sides until they have work in
 * front of the eye: a swing brings them up at once, and they sink back down when it is over. Seen from outside, or
 * as a spectator, nothing changes.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnOwnBodyTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		viewMoves();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			BlockPos stage = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().above(12).immutable();
				for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, -1, 8))) {
					player.level().setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
				}
				player.setGameMode(GameType.SURVIVAL);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON)));
				return pos;
			});
			look(context, world, stage, 0.0F);
			context.waitTicks(10);
			context.runOnClient(mc -> {
				check(OwnBody.active(), "through their own eyes, the hunter's body is there");
				check(OwnBody.raised(1.0F) == 0.0F, "and their hands hang at their sides: " + OwnBody.raised(1.0F));
			});
			context.takeScreenshot("ownbody-00-forward");
			look(context, world, stage, 55.0F);
			context.waitTicks(5);
			context.takeScreenshot("ownbody-01-down");
			look(context, world, stage, 85.0F);
			context.waitTicks(5);
			context.takeScreenshot("ownbody-02-feet");

			// a swing brings the hands up at once, and they sink back when it is done
			look(context, world, stage, 10.0F);
			context.waitTicks(3);
			context.runOnClient(mc -> mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), true));
			context.waitTicks(2);
			context.runOnClient(mc -> check(OwnBody.raised(1.0F) == 1.0F, "a swing brings the hands up in front of the eye: " + OwnBody.raised(1.0F)));
			context.takeScreenshot("ownbody-03-swing");
			context.waitTicks(40);
			context.runOnClient(mc -> check(OwnBody.raised(1.0F) == 0.0F, "and they hang at the sides again afterwards: " + OwnBody.raised(1.0F)));

			// dropped from five blocks up, the landing is felt
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 200, 4, false, false));
				player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY() + 5.0, stage.getZ() + 0.5, Set.of(), 0.0F, 30.0F, true);
			});
			boolean felt = false;
			for (int i = 0; i < 40 && !felt; i++) {
				context.waitTicks(1);
				felt = context.computeOnClient(mc -> ViewMotion.landing() > 0.3F);
			}
			check(felt, "a five-block drop is felt in the view");

			runsLikeARunner(context, world, stage);

			treatsWithOwnHands(context, world, stage);

			// seen from outside nothing is left out
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(3);
			context.runOnClient(mc -> check(!OwnBody.active(), "seen from behind, the body is drawn whole as ever"));
			context.takeScreenshot("ownbody-04-behind");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			world.getServer().runCommand("gamemode spectator @a");
			context.waitTicks(3);
			context.runOnClient(mc -> check(!OwnBody.active(), "a spectator has no body"));
			world.getServer().runCommand("gamemode survival @a");
		}
	}

	/** The view rolls from foot to foot and nods at each footfall, more at a sprint; a landing dips it, harder from higher. */
	private static void viewMoves() {
		float[] still = ViewMotion.stride(1.3F, 0.0F, 1.0F);
		check(still[0] == 0.0F && still[1] == 0.0F && still[2] == 0.0F, "standing still, the view is still");
		float quarter = Mth.HALF_PI / 0.6662F;
		float[] left = ViewMotion.stride(quarter, 1.0F, 1.0F);
		float[] right = ViewMotion.stride(quarter * 3.0F, 1.0F, 1.0F);
		check(left[0] > 0.9F && right[0] < -0.9F, "sprinting, it rolls one way on one foot and the other on the next: " + left[0] + ", " + right[0]);
		check(left[2] > 0.03F && right[2] > 0.03F, "and drops at each footfall: " + left[2]);
		float[] walk = ViewMotion.stride(quarter, 1.0F, 0.0F);
		check(walk[0] > 0.0F && walk[0] < left[0] * 0.5F, "walking moves it far less: " + walk[0]);
		check(ViewMotion.impact(0.5F) == 0.0F, "a step down is no landing");
		check(ViewMotion.impact(3.0F) > 0.0F && ViewMotion.impact(3.0F) < ViewMotion.impact(8.0F) && ViewMotion.impact(8.0F) == 1.0F, "a longer fall lands harder");
		check(ViewMotion.dip(0.05F) > 0.5F && ViewMotion.dip(0.3F) < ViewMotion.dip(0.1F) && ViewMotion.dip(ViewMotion.LANDING) == 0.0F,
			"the dip comes fast and is gone in a third of a second");
	}

	/**
	 * At a sprint the body leans into the run and both hands are carried further forward than at a walk, in the same
	 * stride; nothing sinks into the floor. Stills from the side and through the hunter's own eyes.
	 */
	private static void runsLikeARunner(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		look(context, world, stage, 0.0F);
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
		context.waitTicks(5);
		float quarter = Mth.HALF_PI / 0.6662F;
		float[][] got = new float[2][];
		for (int i = 0; i < 2; i++) {
			Float run = i == 0 ? null : 1.0F;
			got[i] = context.computeOnClient(mc -> {
				java.util.List<ClipProbe.Box> boxes = ClipProbe.boxes(mc, mc.player, net.minecraft.world.phys.Vec3.ZERO, 0.0F, state -> {
					state.walkAnimationSpeed = 1.0F;
					state.walkAnimationPos = quarter;
					state.setData(life.shizune.mistinn.client.view.RunPose.KEY, run);
					state.setData(OwnBody.KEY, null);
				}, false, null, null);
				float hands = 0.0F;
				int arms = 0;
				float head = 0.0F;
				for (ClipProbe.Box box : boxes) {
					if (box.path().equals("/right_arm") || box.path().equals("/left_arm")) {
						hands += box.center().z;
						arms++;
					}
					if (box.path().equals("/head")) {
						head = box.center().z;
					}
				}
				return new float[] {hands / Math.max(1, arms), head, ClipProbe.reach(boxes, -1.0F)};
			});
		}
		check(got[1][1] > got[0][1] + 0.1F, "at a sprint the body leans into the run: head " + got[1][1] + " against " + got[0][1]);
		check(got[1][0] > got[0][0] + 0.05F, "and the arms are carried forward: " + got[1][0] + " against " + got[0][0]);
		check(got[1][2] > -0.08F, "nothing sinks into the floor: lowest " + got[1][2]);

		// stills: every runner on screen held at one point of the stride
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
			.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5, Set.of(), 60.0F, 10.0F, true));
		for (int i = 0; i < 3; i++) {
			float at = quarter * (0.5F + i);
			context.runOnClient(mc -> life.shizune.mistinn.client.view.RunPose.preview = new float[] {1.0F, at});
			context.waitTicks(3);
			context.takeScreenshot("ownbody-run-behind-" + i);
		}
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		look(context, world, stage, 35.0F);
		for (int i = 0; i < 3; i++) {
			float at = quarter * (0.5F + i);
			context.runOnClient(mc -> life.shizune.mistinn.client.view.RunPose.preview = new float[] {1.0F, at});
			context.waitTicks(3);
			context.takeScreenshot("ownbody-run-eyes-" + i);
		}
		context.runOnClient(mc -> life.shizune.mistinn.client.view.RunPose.preview = null);
	}

	/**
	 * A wound is treated and a plate mended with the body's own hands: the view tips down to the work, the hands stay
	 * the body's (nothing is raised in front of the eye) and the view comes back level when it is over. Stills of every
	 * use at three points.
	 */
	private static void treatsWithOwnHands(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		String[][] uses = {
			{"inject", "potent_injector", "60"}, {"pills", "painkillers", "40"}, {"tourniquet", "tourniquet", "80"}, {"splint", "splint", "120"},
			{"surgery", "surgery_kit", "160"}, {"armor_repair", "armor_repair_kit_4", "100"}, {"helmet_repair", "helmet_repair_kit_6", "100"},
		};
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		look(context, world, stage, 5.0F);
		for (String[] use : uses) {
			life.shizune.mistinn.medical.Treatment treatment = java.util.Arrays.stream(life.shizune.mistinn.medical.Treatment.values())
				.filter(t -> t.id.equals(use[0])).findFirst().orElseThrow();
			net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(life.shizune.mistinn.Mistinn.id(use[1]));
			int ticks = Integer.parseInt(use[2]);
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item)));
			context.waitTicks(8);
			context.runOnClient(mc -> life.shizune.mistinn.client.medical.TreatmentClient.received(new life.shizune.mistinn.medical.TreatmentService.TreatmentPayload(
				mc.player.getId(), treatment.ordinal(), ticks, net.minecraft.world.item.Item.getId(item), life.shizune.mistinn.medical.TreatmentService.RESULT_RUNNING)));
			float most = 0.0F;
			for (int i = 0; i < 3; i++) {
				float p = 0.25F + 0.25F * i;
				context.runOnClient(mc -> life.shizune.mistinn.client.medical.TreatmentClient.preview = p);
				context.waitTicks(3);
				most = Math.max(most, context.computeOnClient(mc -> {
					check(OwnBody.active(), use[0] + ": the body is there while it is treated");
					check(OwnBody.raised(1.0F) == 0.0F, use[0] + ": nothing is raised in front of the eye: " + OwnBody.raised(1.0F));
					return Math.abs(life.shizune.mistinn.client.medical.TreatmentClient.camera(
						life.shizune.mistinn.client.medical.TreatmentClient.sample(mc.player.getId()), 1)[0]);
				}));
				context.takeScreenshot("ownbody-treat-" + use[0] + "-" + i);
			}
			check(most > 15.0F, use[0] + ": the view turns to the work: " + most);
			context.runOnClient(mc -> {
				life.shizune.mistinn.client.medical.TreatmentClient.preview = -1.0F;
				life.shizune.mistinn.client.medical.TreatmentClient.received(new life.shizune.mistinn.medical.TreatmentService.TreatmentPayload(
					mc.player.getId(), -1, 0, 0, life.shizune.mistinn.medical.TreatmentService.RESULT_DONE));
			});
			context.waitTicks(20);
			context.runOnClient(mc -> check(life.shizune.mistinn.client.medical.TreatmentClient.sample(mc.player.getId()) == null,
				use[0] + ": and the view is the hunter's own again afterwards"));
		}
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
	}

	private static void look(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage, final float pitch) {
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
			.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5, Set.of(), 0.0F, pitch, true));
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
