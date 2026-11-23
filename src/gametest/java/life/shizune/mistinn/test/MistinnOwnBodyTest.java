package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.medical.TreatmentClient;
import life.shizune.mistinn.client.view.OwnBody;
import life.shizune.mistinn.client.view.OwnHand;
import life.shizune.mistinn.client.view.ViewMotion;
import life.shizune.mistinn.medical.Treatment;
import life.shizune.mistinn.medical.TreatmentService;
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
import net.minecraft.world.item.Item;
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
	/** The lowest a working fist may be on the screen (-1 is its bottom edge): above the hotbar and the bars over it. */
	private static final float HAND_FLOOR = -0.82F;

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

			// with a torch in the off hand, a swing brings that arm up too: the body's own arm, holding the torch up in view
			context.runOnClient(mc -> check(OwnHand.seen(InteractionHand.OFF_HAND, aspect(mc)) == null, "hanging, no hand is at work"));
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
				.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(net.minecraft.world.item.Items.TORCH)));
			look(context, world, stage, 60.0F);
			context.waitTicks(5);
			context.runOnClient(mc -> {
				mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), true);
			});
			// the body's arms follow what the hand pass found a frame behind: a few ticks for both to be up
			context.waitTicks(2);
			for (int i = 0; i < 5 && context.computeOnClient(mc -> OwnHand.seen(InteractionHand.OFF_HAND, aspect(mc)) == null); i++) {
				context.waitTicks(1);
			}
			context.runOnClient(mc -> {
				check(OwnBody.handsUp(mc.player, 1.0F) == null, "the body's own arms do it, none is left off it");
				OwnHand.Seen off = OwnHand.seen(InteractionHand.OFF_HAND, aspect(mc));
				check(off != null && OwnHand.seen(InteractionHand.MAIN_HAND, aspect(mc)) != null, "a swing with a torch in the off hand brings both arms up: " + off);
			});
			context.takeScreenshot("ownbody-05-offhand-swing");
			context.waitTicks(40);
			context.runOnClient(mc -> check(OwnBody.raised(1.0F) == 0.0F && OwnHand.seen(InteractionHand.OFF_HAND, aspect(mc)) == null, "and both hang again afterwards"));
			context.takeScreenshot("ownbody-06-offhand-hanging");
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
			context.waitTicks(3);
			context.runOnClient(mc -> {
				mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), true);
			});
			context.waitTicks(2);
			for (int i = 0; i < 5 && context.computeOnClient(mc -> OwnHand.seen(InteractionHand.MAIN_HAND, aspect(mc)) == null); i++) {
				context.waitTicks(1);
			}
			context.runOnClient(mc -> {
				check(OwnHand.seen(InteractionHand.MAIN_HAND, aspect(mc)) != null, "the swing brings the main hand up");
				check(OwnHand.seen(InteractionHand.OFF_HAND, aspect(mc)) == null, "an empty off hand stays at the side");
			});
			context.waitTicks(40);

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

			handsHoldWhatTheySwing(context, world, stage);

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
	 * A swing is the hunter's own hand at work: through every branch's swing the fist that holds the weapon stays inside
	 * the view above the bars, and the arm runs out of the view before it ends, so no cut-off arm is seen. Held in the
	 * left hand, the same is seen on the left.
	 */
	private static void handsHoldWhatTheySwing(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		look(context, world, stage, 0.0F);
		for (WeaponStyle style : WeaponStyle.values()) {
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
				.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.WEAPONS.get(style).get(WeaponMaterial.IRON))));
			context.waitTicks(25);
			context.runOnClient(mc -> {
				check(OwnHand.seen(InteractionHand.MAIN_HAND, aspect(mc)) == null, style + ": hanging at the side, no hand is drawn before the eye");
				mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), true);
			});
			// the first two ticks carry the hand up from the side
			context.waitTicks(3);
			int inView = 0;
			int ticks = style.swingTicks - 3;
			for (int i = 0; i < ticks; i++) {
				context.waitTicks(1);
				int tick = i + 3;
				inView += context.computeOnClient(mc -> {
					OwnHand.Seen seen = OwnHand.seen(InteractionHand.MAIN_HAND, aspect(mc));
					check(seen != null, style + ": the hand is drawn through the swing, tick " + tick);
					check(seen.whole(), style + ": the arm runs out of the view, tick " + tick + ": " + seen);
					check(OwnBody.handsUp(mc.player, 1.0F) == null, style + ": and it is the body's own arm, tick " + tick);
					return seen.fistInView() && seen.fistY() > HAND_FLOOR ? 1 : 0;
				});
			}
			check(inView == ticks, style + ": the fist stays in view through the swing: " + inView + " of " + ticks + " ticks");
			context.waitTicks(20);
		}

		// in the left hand the same swing is seen on the left
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
			.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON))));
		context.runOnClient(mc -> {
			mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.LEFT);
			mc.options.broadcastOptions();
		});
		context.waitTicks(25);
		context.runOnClient(mc -> mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), true));
		context.waitTicks(3);
		for (int i = 3; i < WeaponStyle.SLASH.swingTicks; i++) {
			int tick = i;
			context.runOnClient(mc -> {
				OwnHand.Seen seen = OwnHand.seen(InteractionHand.MAIN_HAND, aspect(mc));
				check(seen != null && seen.fistInView() && seen.whole(), "left-handed, the hand is drawn whole through a swing, tick " + tick + ": " + seen);
				check(seen.fistX() < 0.0F || seen.endX() < 0.0F, "and its arm comes in from the left, tick " + tick + ": " + seen);
			});
			if (i == 4) {
				context.takeScreenshot("ownbody-07-left-handed-swing");
			}
			context.waitTicks(1);
		}
		context.runOnClient(mc -> {
			mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.RIGHT);
			mc.options.broadcastOptions();
		});
		context.waitTicks(40);
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
			.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON))));
		context.waitTicks(5);
	}

	private static float aspect(final net.minecraft.client.Minecraft mc) {
		return (float) mc.getWindow().getWidth() / mc.getWindow().getHeight();
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
	 * A wound is treated with the hands up in front of the eye: the hand that holds the tool works on the other arm, held
	 * out for it, never on itself; both arms run out of the view before they end, and the view stays level. A plate is
	 * mended on the body: there the view tips down to the work and nothing is raised. Stills of every use at three points.
	 */
	private static void treatsWithOwnHands(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		String[][] uses = {
			{"inject", "potent_injector", "60", "hands"}, {"pills", "painkillers", "40", "hands"}, {"tourniquet", "tourniquet", "80", "hands"},
			{"splint", "splint", "120", "hands"}, {"surgery", "surgery_kit", "160", "hands"},
			{"armor_repair", "armor_repair_kit_4", "100", "body"}, {"helmet_repair", "helmet_repair_kit_6", "100", "body"},
		};
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		look(context, world, stage, 5.0F);
		for (String[] use : uses) {
			Treatment treatment = java.util.Arrays.stream(Treatment.values()).filter(t -> t.id.equals(use[0])).findFirst().orElseThrow();
			Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(life.shizune.mistinn.Mistinn.id(use[1]));
			int ticks = Integer.parseInt(use[2]);
			boolean inHands = use[3].equals("hands");
			begin(context, world, treatment, item, ticks);
			float most = 0.0F;
			for (int i = 0; i < 3; i++) {
				// a use that loops is filmed across one turn of its cycle a second in, the others across their whole length
				float loop = TreatmentClient.clip(treatment).number("loop", 0.0F);
				float p = loop > 0.0F ? (1.0F + loop * i / 3.0F) / (ticks / 20.0F) : 0.25F + 0.25F * i;
				boolean middle = i == 1;
				context.runOnClient(mc -> TreatmentClient.preview = p);
				context.waitTicks(3);
				most = Math.max(most, context.computeOnClient(mc -> {
					check(OwnBody.active(), use[0] + ": the body is there while it is treated");
					if (inHands) {
						worksOnTheOtherArm(mc, use[0], middle);
					} else {
						check(OwnBody.raised(1.0F) == 0.0F, use[0] + ": nothing is raised in front of the eye: " + OwnBody.raised(1.0F));
					}
					return Math.abs(TreatmentClient.camera(TreatmentClient.sample(mc.player.getId()), 1)[0]);
				}));
				context.takeScreenshot("ownbody-treat-" + use[0] + "-" + i);
			}
			if (inHands) {
				check(most < 15.0F, use[0] + ": the view stays level, the work is brought up to it: " + most);
			} else {
				check(most > 15.0F, use[0] + ": the view turns to the work: " + most);
				Clip work = Clip.find("use/" + use[0] + "_1p");
				check(work != null && work.has("own.right_arm_x") && work.has("own.item_x"), use[0] + ": the hands make the work's own short beat");
			}
			if (use[0].equals("surgery")) {
				// crouched, the body is still there and the work still in the hands
				context.runOnClient(mc -> mc.options.keyShift.setDown(true));
				context.waitTicks(8);
				context.runOnClient(mc -> {
					check(OwnBody.active() && mc.player.isCrouching(), "crouched, the body is still the hunter's own");
					worksOnTheOtherArm(mc, "surgery, crouched", false);
				});
				context.takeScreenshot("ownbody-treat-surgery-crouched");
				context.runOnClient(mc -> mc.options.keyShift.setDown(false));
				context.waitTicks(8);
			}
			if (use[0].equals("inject")) {
				// the last one used up mid-motion: the hands finish it rather than a bare arm cutting in
				context.runOnClient(mc -> TreatmentClient.preview = 0.5F);
				world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
				context.waitTicks(6);
				context.runOnClient(mc -> {
					check(mc.player.getMainHandItem().isEmpty(), "the injector is gone from the hand");
					check(OwnHand.seen(InteractionHand.MAIN_HAND, aspect(mc)) != null && OwnHand.seen(InteractionHand.OFF_HAND, aspect(mc)) != null,
						"used up, both arms are still where the motion has them");
				});
				context.takeScreenshot("ownbody-treat-inject-used-up");
			}
			end(context, use[0]);
		}

		// left-handed, the left hand works on the right arm
		context.runOnClient(mc -> {
			mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.LEFT);
			mc.options.broadcastOptions();
		});
		context.waitTicks(10);
		begin(context, world, Treatment.INJECT, net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(life.shizune.mistinn.Mistinn.id("potent_injector")), 60);
		context.runOnClient(mc -> TreatmentClient.preview = 0.5F);
		context.waitTicks(3);
		context.runOnClient(mc -> {
			worksOnTheOtherArm(mc, "inject, left-handed", true);
			OwnHand.Seen tool = OwnHand.seen(InteractionHand.MAIN_HAND, aspect(mc));
			OwnHand.Seen held = OwnHand.seen(InteractionHand.OFF_HAND, aspect(mc));
			check(tool.fistX() < held.fistX() && held.endX() > 0.0F, "left-handed, the hand that works is on the left and the arm worked on runs off to the right: " + tool + ", " + held);
		});
		context.takeScreenshot("ownbody-treat-inject-left-handed");
		end(context, "inject, left-handed");
		context.runOnClient(mc -> {
			mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.RIGHT);
			mc.options.broadcastOptions();
		});
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
		context.waitTicks(10);
	}

	/** Puts a treatment's item in the hunter's hand and starts its use as the server would announce it. */
	private static void begin(final ClientGameTestContext context, final TestSingleplayerContext world, final Treatment treatment, final Item item, final int ticks) {
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item)));
		context.waitTicks(8);
		context.runOnClient(mc -> TreatmentClient.received(new TreatmentService.TreatmentPayload(
			mc.player.getId(), treatment.ordinal(), ticks, Item.getId(item), TreatmentService.RESULT_RUNNING)));
	}

	/** Ends the use under way as the server would, and checks the view is given back. */
	private static void end(final ClientGameTestContext context, final String name) {
		context.runOnClient(mc -> {
			TreatmentClient.preview = -1.0F;
			TreatmentClient.received(new TreatmentService.TreatmentPayload(mc.player.getId(), -1, 0, 0, TreatmentService.RESULT_DONE));
		});
		context.waitTicks(20);
		context.runOnClient(mc -> {
			check(TreatmentClient.sample(mc.player.getId()) == null, name + ": and the view is the hunter's own again afterwards");
			check(OwnBody.raised(1.0F) == 0.0F, name + ": with the hands down at the hunter's sides again: " + OwnBody.raised(1.0F));
		});
	}

	/**
	 * The hands are up and at work as drawn: the arm that holds the tool is whole and its fist in view; if the use is done
	 * on the other arm, that arm is held out whole too, the body leaves both out, and the tool's working point is nearer
	 * the middle of the other forearm than of its own. At the {@code middle} of the use it is on that forearm.
	 */
	private static void worksOnTheOtherArm(final net.minecraft.client.Minecraft mc, final String name, final boolean middle) {
		check(OwnBody.raised(1.0F) == 1.0F, name + ": the hands are up in front of the eye: " + OwnBody.raised(1.0F));
		TreatmentClient.InHands work = TreatmentClient.inHands(mc.player);
		check(work != null, name + ": it is a use worked in the hands");
		OwnHand.Seen tool = OwnHand.seen(InteractionHand.MAIN_HAND, aspect(mc));
		check(tool != null, name + ": the hand that holds the tool is drawn");
		check(tool.whole() && tool.fistInView(), name + ": its fist is in view and its arm runs out of it: " + tool);
		if (!work.presents()) {
			return;
		}
		OwnHand.Seen held = OwnHand.seen(InteractionHand.OFF_HAND, aspect(mc));
		check(held != null && held.whole(), name + ": the other arm is held out, and runs out of the view: " + held);
		check(OwnBody.handsUp(mc.player, 1.0F) == null, name + ": both are the body's own arms");
		float other = OwnHand.fromForearm(InteractionHand.OFF_HAND);
		float own = OwnHand.fromForearm(InteractionHand.MAIN_HAND);
		check(other < own, name + ": the tool works on the other arm, not on the one that holds it: " + other + " against " + own);
		if (middle) {
			check(other < OwnHand.THICK * 0.75F, name + ": and at the middle of the use it is on that arm: " + other);
		}
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
