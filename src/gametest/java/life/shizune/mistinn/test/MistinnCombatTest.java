package life.shizune.mistinn.test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.HeavyClient;
import life.shizune.mistinn.client.anim.SwordQi;
import life.shizune.mistinn.client.anim.WeaponSwings;
import life.shizune.mistinn.enemy.WindupService;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.Skills;
import life.shizune.mistinn.stamina.StaminaService;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.SpecialWeapon;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 蓄力重擊, the sword qi, the scythe and the special weapons. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnCombatTest implements FabricClientGameTest {
	private static final String[] PARTS = {"/head", "/body", "/right_arm", "/left_arm", "/right_leg", "/left_leg"};

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty easy");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			context.waitTicks(10);
			numbersAsWritten();
			context.runOnClient(MistinnCombatTest::clipsJoin);
			BlockPos arena = world.getServer().computeOnServer(MistinnCombatTest::arena);
			poseSandbox(context, world);
			heavyHitsTheWholeArc(context, world, arena);
			tooLittleChargeDoesNothing(context, world, arena);
			spearPiercesALine(context, world, arena);
			scytheDrawsThemIn(context, world, arena);
			daggerCutsTwice(context, world, arena);
			greatswordShakesTheGround(context, world, arena);
			hammerKnocksBack(context, world, arena);
			fullChargeBreaksAWindup(context, world, arena);
			realKeysChargeAndStrike(context, world, arena);
			noChargeWithoutStamina(context, world, arena);
			unfitSpecialWeapon(context, world, arena);
			snufferLeavesItsHook(context, world, arena);
			hookDragsTheFarthest(context, world, arena);
			lanternBursts(context, world, arena);
			maulTollsAllAround(context, world, arena);
			clapperBreaksEveryWindup(context, world, arena);
			refusedChargeLeavesTheOtherHand(context, world, arena);
			killGoesToTheBlowsWeapon(context, world, arena);
			slideMissPaysNothing(context, world, arena);
			heavyStaysInItsWorld(context, world, arena);
			rollBreaksTheCharge(context, world, arena);
			deadArmsLetNothingGo(context, world, arena);
			fireKillIsTheHunters(context, world, arena);
			frames(context, world, arena);
		}
	}

	// ── The numbers ───────────────────────────────────────────────

	private static void numbersAsWritten() {
		check(WeaponStyle.FLURRY.chargeTicks == 12 && WeaponStyle.SLASH.chargeTicks == 16 && WeaponStyle.THRUST.chargeTicks == 18
			&& WeaponStyle.REAP.chargeTicks == 20 && WeaponStyle.SMASH.chargeTicks == 24 && WeaponStyle.CLEAVE.chargeTicks == 26, "charge times by branch, as designed");
		check(near(HeavyService.damage(10.0, 1.0F, 0), 20.0), "a full charge doubles the blow");
		check(near(HeavyService.damage(10.0, 0.5F, 5), 18.0), "half a charge with five strength: 10 x 1.5 x 1.2");
		check(near(HeavyService.heavyScale(10), 1.4), "strength adds 4% a point to a heavy blow");
		check(near(HeavyService.burstDamage(4, 1.0F), 20.0), "the lantern's burst: (6 + 4) x 2");
		check(near(HeavyService.cost(new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON)), 1.0F), 22.0),
			"a full heavy slash costs twice the swing's 11 stamina");
		check(HeavyService.MIN_CHARGE == 0.3F, "under 30% charge nothing is let go");
		check(HeavyService.shape(WeaponStyle.FLURRY, null).hits() == 2, "the dagger cuts twice");
		check(HeavyService.shape(WeaponStyle.SMASH, SpecialWeapon.TIDE_MAUL).kind() == HeavyService.Kind.RING, "the tide maul tolls all around");
		check(HeavyService.shape(WeaponStyle.THRUST, SpecialWeapon.LANTERN_STAFF).kind() == HeavyService.Kind.BURST, "the lantern staff bursts");
		check(SpecialWeapon.TIDE_MAUL.requirements.size() == 2 && SpecialWeapon.SNUFFER_HOOK.requirements.getFirst().achievement() != null,
			"the maul asks for two stats, the hook for the Snuffer first");
		check(AchievementService.SUNKEN_BELL.equals(SpecialWeapon.BELL_CLAPPER.requirements.getFirst().achievement()), "the clapper asks for the Sunken Bell first");
	}

	// ── The animation sandbox ─────────────────────────────────────

	/** Every strike starts where its charge stance ends, and lands at the server's contact. */
	private static void clipsJoin(final Minecraft mc) {
		for (WeaponStyle style : WeaponStyle.values()) {
			Clip charge = HeavyClient.chargeClip(style);
			Clip strike = HeavyClient.strikeClip(style);
			for (String group : new String[] {"third_person", "first_person", "camera"}) {
				for (String track : charge.tracks(group)) {
					float end = charge.at(group + "." + track, 1.0F);
					float start = strike.at(group + "." + track, 0.0F);
					check(Math.abs(end - start) < 1.0E-3F, style + ": the strike starts where the stance ends on " + group + "." + track + ": " + end + " vs " + start);
				}
			}
			float contact = strike.number("contact", -1.0F);
			check(Math.abs(contact - HeavyService.shape(style, null).contact()) < 1.0E-3F, style + ": the strike lands where the server lands it: " + contact);
			check(Math.abs(strike.at("third_person.arm_x", 1.0F) + 18.0F) < 1.0E-3F, style + ": the strike ends at rest");
		}
		check(WeaponSwings.clip(WeaponStyle.REAP).flag("two_handed"), "the scythe is held in both hands");
	}

	/**
	 * Poses the hunter's model through every heavy blow and every swing, frame by frame, and measures
	 * it: nothing sinks into the floor, and neither arm runs into the head or the torso.
	 */
	private static void poseSandbox(final ClientGameTestContext context, final TestSingleplayerContext world) {
		List<String> faults = new ArrayList<>();
		for (WeaponStyle style : WeaponStyle.values()) {
			Item weapon = MistinnItems.WEAPONS.get(style).get(WeaponMaterial.IRON);
			world.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon)));
			context.waitTicks(3);
			List<String> found = context.computeOnClient(mc -> {
				List<String> out = new ArrayList<>();
				for (int i = 0; i <= 14; i++) {
					float p = i / 14.0F;
					measure(mc, style + " charge", p, out, state -> state.setData(HeavyClient.KEY, new HeavyClient.Pose(HeavyClient.Phase.CHARGE, style, p, p, 0.0F)));
				}
				for (int i = 0; i <= 28; i++) {
					float p = i / 28.0F;
					measure(mc, style + " strike", p, out, state -> state.setData(HeavyClient.KEY, new HeavyClient.Pose(HeavyClient.Phase.STRIKE, style, p, 1.0F, 0.0F)));
				}
				// every branch's light swing as well
				for (int i = 1; i <= 28; i++) {
					float p = i / 29.0F;
					measure(mc, style + " swing", p, out, state -> {
						HumanoidRenderState humanoid = (HumanoidRenderState) state;
						humanoid.currentSwing = new LivingEntity.SwingDescription(InteractionHand.MAIN_HAND, new ItemStack(weapon).getAttackAnimation(), style.swingTicks);
						humanoid.swingAnimation = p;
					});
				}
				return out;
			});
			faults.addAll(found);
		}
		world.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
		check(faults.isEmpty(), "every heavy blow and every swing stay clear of the floor, the head and the torso: " + faults);
	}

	private static void measure(final Minecraft mc, final String what, final float p, final List<String> faults,
		final java.util.function.Consumer<net.minecraft.client.renderer.entity.state.LivingEntityRenderState> prepare) {
		List<ClipProbe.Box> boxes = ClipProbe.boxes(mc, mc.player, Vec3.ZERO, 0.0F, prepare, false, null, null);
		float lowest = Float.MAX_VALUE;
		for (ClipProbe.Box box : boxes) {
			lowest = Math.min(lowest, bottom(box));
		}
		ClipProbe.Box head = part(boxes, "/head");
		ClipProbe.Box body = part(boxes, "/body");
		float worst = 0.0F;
		String where = "";
		for (String arm : new String[] {"/right_arm", "/left_arm"}) {
			ClipProbe.Box box = part(boxes, arm);
			// the shoulder joint sits against the torso by build; elbow to hand must stay out of both
			ClipProbe.Box forearm = slice(box, 1.0F / 3.0F, 2.0F / 3.0F);
			ClipProbe.Box shoulder = slice(box, -2.0F / 3.0F, 1.0F / 3.0F);
			float[] depths = {ClipProbe.depth(forearm, head), ClipProbe.depth(forearm, body), ClipProbe.depth(shoulder, head)};
			String[] names = {" forearm in head", " forearm in body", " upper arm in head"};
			for (int i = 0; i < depths.length; i++) {
				if (depths[i] > worst) {
					worst = depths[i];
					where = arm + names[i];
				}
			}
		}
		System.out.println(String.format("HEAVY %s p=%.3f lowest %.3f worst %.3f %s", what, p, lowest, worst, where));
		if (lowest < -MistinnClipTest.TOLERANCE) {
			faults.add(String.format("%s p=%.2f sinks %.3f", what, p, lowest));
		}
		if (worst > MistinnClipTest.TOLERANCE) {
			faults.add(String.format("%s p=%.2f %s by %.3f", what, p, where, worst));
		}
	}

	/**
	 * Part of an arm's box along its length: the arm's cube runs from 2 px above the shoulder pivot to 10
	 * below, its local +y toward the hand. {@code shift} moves the centre by that share of the half length,
	 * {@code keep} is the share of the length kept: (1/3, 2/3) is elbow to hand, (-2/3, 1/3) the upper arm.
	 */
	private static ClipProbe.Box slice(final ClipProbe.Box box, final float shift, final float keep) {
		org.joml.Vector3f centre = new org.joml.Vector3f(box.axes()[1]).mul(box.half()[1] * shift).add(box.center());
		float[] half = box.half().clone();
		half[1] *= keep;
		return new ClipProbe.Box(box.path(), centre, box.axes(), half);
	}

	private static float bottom(final ClipProbe.Box box) {
		return box.center().y - (box.half()[0] * Math.abs(box.axes()[0].y) + box.half()[1] * Math.abs(box.axes()[1].y) + box.half()[2] * Math.abs(box.axes()[2].y));
	}

	private static ClipProbe.Box part(final List<ClipProbe.Box> boxes, final String path) {
		for (ClipProbe.Box box : boxes) {
			if (box.path().equals(path)) {
				return box;
			}
		}
		throw new AssertionError("no " + path + " among " + boxes.stream().map(ClipProbe.Box::path).toList());
	}

	// ── On the arena ──────────────────────────────────────────────

	/** A flat stone floor high above the world, open air over it; the hunter stands at its near end facing +z. */
	private static BlockPos arena(final MinecraftServer server) {
		ServerPlayer player = player(server);
		BlockPos pos = player.blockPosition().above(30).immutable();
		for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, -1, 16))) {
			player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		}
		for (BlockPos air : BlockPos.betweenClosed(pos.offset(-8, 0, -8), pos.offset(8, 4, 16))) {
			player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
		}
		return pos;
	}

	/** Puts the hunter at the arena's centre facing +z, whole and rested, with this in hand. */
	private static void stand(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena, final Item weapon, final Skills skills) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			clearArena(server, arena);
			HeavyService.forgetAll();
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			player.getInventory().setSelectedSlot(0);
			player.setItemInHand(InteractionHand.MAIN_HAND, weapon == null ? ItemStack.EMPTY : new ItemStack(weapon));
			SkillService.set(player, skills);
			BodyService.restoreAll(player);
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
		});
		context.waitTicks(4);
	}

	private static void clearArena(final MinecraftServer server, final BlockPos arena) {
		for (var entity : server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new AABB(arena).inflate(20.0), e -> !(e instanceof ServerPlayer))) {
			entity.discard();
		}
	}

	/** A sturdy, still husk at (dx, dz) from the hunter, facing them; returns its id. */
	private static int husk(final TestSingleplayerContext world, final BlockPos arena, final double dx, final double dz) {
		return world.getServer().computeOnServer(server -> {
			Husk mob = EntityTypes.HUSK.create(server.overworld(), EntitySpawnReason.COMMAND);
			mob.setNoAi(true);
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
			mob.setHealth(200.0F);
			mob.snapTo(arena.getX() + 0.5 + dx, arena.getY(), arena.getZ() + 0.5 + dz, 180.0F, 0.0F);
			server.overworld().addFreshEntity(mob);
			return mob.getId();
		});
	}

	private static LivingEntity mob(final MinecraftServer server, final int id) {
		return (LivingEntity) server.overworld().getEntity(id);
	}

	private static float lost(final TestSingleplayerContext world, final int id) {
		return world.getServer().computeOnServer(server -> mob(server, id) == null ? 200.0F : 200.0F - mob(server, id).getHealth());
	}

	/** Lets a heavy blow go at {@code charge} of a full charge and waits for it to land (and any second cut). */
	private static void heavy(final ClientGameTestContext context, final TestSingleplayerContext world, final float charge) {
		boolean[] went = {false};
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			WeaponStyle style = WeaponStyle.of(player.getMainHandItem());
			int ticks = (int) Math.ceil(HeavyService.chargeTicks(player, style) * charge);
			went[0] = HeavyService.release(player, player.getMainHandItem(), ticks);
		});
		check(went[0], "the heavy blow is let go");
		context.waitTicks(14);
	}

	/**
	 * Lets a heavy blow go and waits just past its landing. The husks here have no AI, so they keep the
	 * push they were given without moving: the push is what is measured.
	 */
	private static void heavyLanded(final ClientGameTestContext context, final TestSingleplayerContext world, final float charge) {
		int[] land = {0};
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			WeaponStyle style = WeaponStyle.of(player.getMainHandItem());
			int ticks = (int) Math.ceil(HeavyService.chargeTicks(player, style) * charge);
			land[0] = HeavyService.shape(style, SpecialWeapon.of(player.getMainHandItem())).landTicks();
			check(HeavyService.release(player, player.getMainHandItem(), ticks), "the heavy blow is let go");
		});
		context.waitTicks(land[0] + 2);
	}

	/** The horizontal push a husk was given toward (positive) or away from (negative) the hunter. */
	private static double pull(final TestSingleplayerContext world, final int id) {
		return world.getServer().computeOnServer(server -> {
			LivingEntity mob = mob(server, id);
			Vec3 toward = player(server).position().subtract(mob.position()).multiply(1.0, 0.0, 1.0).normalize();
			return mob.getDeltaMovement().multiply(1.0, 0.0, 1.0).dot(toward);
		});
	}

	private static Item iron(final WeaponStyle style) {
		return MistinnItems.WEAPONS.get(style).get(WeaponMaterial.IRON);
	}

	private static void heavyHitsTheWholeArc(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SLASH), Skills.EMPTY);
		int left = husk(world, arena, -1.2, 2.0);
		int centre = husk(world, arena, 0.0, 2.5);
		int right = husk(world, arena, 1.2, 2.0);
		int behind = husk(world, arena, 0.0, -2.0);
		int far = husk(world, arena, 0.0, 6.0);
		context.waitTicks(2);
		float stamina = world.getServer().computeOnServer(server -> StaminaService.get(player(server)));
		int wear = world.getServer().computeOnServer(server -> player(server).getMainHandItem().getDamageValue());
		heavy(context, world, 1.0F);
		for (int id : new int[] {left, centre, right}) {
			check(lost(world, id) > 8.0F, "a full heavy slash hits everything in its arc, about 12 each: lost " + lost(world, id));
		}
		check(lost(world, behind) == 0.0F && lost(world, far) == 0.0F, "but nothing behind or out of reach: " + lost(world, behind) + ", " + lost(world, far));
		float spent = stamina - world.getServer().computeOnServer(server -> StaminaService.get(player(server)));
		check(spent > 21.0F && spent < 23.5F, "it costs twice the swing, 22 stamina: " + spent);
		int worn = world.getServer().computeOnServer(server -> player(server).getMainHandItem().getDamageValue()) - wear;
		check(worn == HeavyService.DURABILITY, "and two durability: " + worn);
		// a light blow hits only the one it is aimed at
		context.waitTicks(40);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			float[] before = {mob(server, left).getHealth(), mob(server, right).getHealth()};
			player.attack(mob(server, centre));
			check(mob(server, left).getHealth() == before[0] && mob(server, right).getHealth() == before[1], "a light swing hits only the one it is aimed at");
		});
	}

	private static void tooLittleChargeDoesNothing(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SLASH), Skills.EMPTY);
		int centre = husk(world, arena, 0.0, 2.5);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			float stamina = StaminaService.get(player);
			check(!HeavyService.release(player, player.getMainHandItem(), 3), "let go after 3 of 16 ticks, under 30%: nothing happens");
			check(StaminaService.get(player) == stamina && player.getMainHandItem().getDamageValue() == 0, "and nothing is spent");
		});
		context.waitTicks(10);
		check(lost(world, centre) == 0.0F, "and nothing is hit");
	}

	private static void spearPiercesALine(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.THRUST), Skills.EMPTY);
		int[] line = {husk(world, arena, 0.0, 2.0), husk(world, arena, 0.0, 3.6), husk(world, arena, 0.0, 5.2)};
		int aside = husk(world, arena, 2.4, 3.0);
		context.waitTicks(2);
		heavy(context, world, 1.0F);
		for (int id : line) {
			check(lost(world, id) > 8.0F, "the heavy thrust runs through the whole line: lost " + lost(world, id));
		}
		check(lost(world, aside) == 0.0F, "but not what stands aside: " + lost(world, aside));
	}

	private static void scytheDrawsThemIn(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.REAP), Skills.EMPTY);
		int left = husk(world, arena, -2.5, 2.0);
		int right = husk(world, arena, 2.5, 2.0);
		int back = husk(world, arena, 0.0, -3.0);
		context.waitTicks(2);
		heavyLanded(context, world, 1.0F);
		check(lost(world, left) > 8.0F && lost(world, right) > 8.0F, "the reap's 200 degrees take both sides");
		check(lost(world, back) == 0.0F, "but not what is behind");
		double[] pulled = {pull(world, left), pull(world, right)};
		check(pulled[0] > 0.4 && pulled[1] > 0.4, "and draws them in, about 0.5 a tick toward the hunter: " + pulled[0] + ", " + pulled[1]);
	}

	private static void daggerCutsTwice(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.FLURRY), Skills.EMPTY);
		int target = husk(world, arena, 0.0, 2.0);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> HeavyService.release(player(server), player(server).getMainHandItem(), 12));
		// the server's clock and the husk's health, every tick: the cuts are two separate drops
		List<long[]> drops = new ArrayList<>();
		float last = 0.0F;
		for (int i = 0; i < 16; i++) {
			float now = lost(world, target);
			long time = world.getServer().computeOnServer(server -> server.overworld().getGameTime());
			if (now > last + 0.5F) {
				drops.add(new long[] {time, Math.round((now - last) * 10.0F)});
				last = now;
			}
			context.waitTicks(1);
		}
		List<String> seen = drops.stream().map(d -> "t" + d[0] + " -" + d[1] / 10.0F).toList();
		check(drops.size() == 2, "the heavy dagger cuts twice: " + seen);
		check(drops.get(0)[1] > 30 && drops.get(1)[1] > 30, "each cut lands about 5: " + seen);
		long gap = drops.get(1)[0] - drops.get(0)[0];
		check(gap >= HeavyService.FLURRY_GAP - 1 && gap <= HeavyService.FLURRY_GAP + 1, "the second " + HeavyService.FLURRY_GAP + " ticks after the first: " + seen);
	}

	private static void greatswordShakesTheGround(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.CLEAVE), Skills.EMPTY);
		int near = husk(world, arena, 0.0, 3.0);
		int beyond = husk(world, arena, 0.0, 5.6);
		context.waitTicks(2);
		heavy(context, world, 1.0F);
		float full = lost(world, near);
		float shaken = lost(world, beyond);
		check(full > 14.0F, "the chop hits what it falls on, about 18: " + full);
		check(shaken > 5.0F && shaken < full * 0.7F, "and the ground it lands on shakes what is just past it, about half: " + shaken);
	}

	private static void hammerKnocksBack(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SMASH), Skills.EMPTY);
		int target = husk(world, arena, 0.0, 2.0);
		context.waitTicks(2);
		heavyLanded(context, world, 1.0F);
		double thrown = -pull(world, target);
		check(thrown > 0.9, "the heavy sweep throws it back hard: " + thrown);
	}

	private static void fullChargeBreaksAWindup(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SLASH), Skills.EMPTY);
		int target = husk(world, arena, 0.0, 2.0);
		world.getServer().runOnServer(server -> mob(server, target).setAttached(WindupService.WINDUP,
			new WindupService.Windup(server.overworld().getGameTime(), 200, player(server).getId(), false)));
		heavy(context, world, 0.6F);
		check(world.getServer().computeOnServer(server -> WindupService.winding((net.minecraft.world.entity.Mob) mob(server, target))),
			"a partial charge does not break the windup");
		world.getServer().runOnServer(server -> player(server).setAttached(MistinnAttachments.STAMINA, StaminaService.max(player(server))));
		heavy(context, world, 1.0F);
		check(!world.getServer().computeOnServer(server -> WindupService.winding((net.minecraft.world.entity.Mob) mob(server, target))),
			"a full charge breaks it off");
	}

	/** With the real keys: hold the use key until full, let go, and the blow lands. */
	private static void realKeysChargeAndStrike(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SLASH), Skills.EMPTY);
		int target = husk(world, arena, 0.9, 2.5);
		context.waitTicks(20);
		float stamina = world.getServer().computeOnServer(server -> StaminaService.get(player(server)));
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(10);
		check(context.computeOnClient(mc -> mc.player.isUsingItem()), "holding the use key charges");
		float half = context.computeOnClient(mc -> {
			HeavyClient.Pose pose = HeavyClient.sample(mc.player, 0.0F);
			return pose == null ? -1.0F : pose.charge();
		});
		check(half > 0.4F && half < 0.9F, "half way through the charge after ten ticks: " + half);
		context.waitTicks(10);
		context.takeScreenshot(TestScreenshotOptions.of("charge-hud").disableCounterPrefix().withDestinationDir(Path.of("combat")));
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitTicks(10);
		check(!context.computeOnClient(mc -> mc.player.isUsingItem()), "letting go ends the charge");
		check(lost(world, target) > 8.0F, "and the heavy blow lands: lost " + lost(world, target));
		float spent = stamina - world.getServer().computeOnServer(server -> StaminaService.get(player(server)));
		check(spent > 21.0F, "a full charge's stamina is spent: " + spent);
	}

	private static void noChargeWithoutStamina(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SLASH), Skills.EMPTY);
		world.getServer().runOnServer(server -> StaminaService.spend(player(server), 1000.0F));
		context.waitTicks(3);
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(6);
		boolean using = context.computeOnClient(mc -> mc.player.isUsingItem());
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitTicks(4);
		check(!using, "with no stamina left there is no charging");
	}

	private static Skills skills(final int strength, final int constitution, final int intellect) {
		Set<String> nodes = new LinkedHashSet<>();
		nodes.add("slide");
		for (int i = 1; i <= strength; i++) {
			nodes.add("strength_" + i);
		}
		for (int i = 1; i <= constitution; i++) {
			nodes.add("constitution_" + i);
		}
		for (int i = 1; i <= intellect; i++) {
			nodes.add("intellect_" + i);
		}
		return new Skills(nodes);
	}

	private static void unfitSpecialWeapon(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, MistinnItems.TIDE_MAUL, Skills.EMPTY);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(SpecialWeapon.unfit(player), "without strength 5 and constitution 3 the maul does not answer");
			check(near(player.getAttributeValue(Attributes.ATTACK_DAMAGE), 13.0 * 0.5), "it hits at half its 13: " + player.getAttributeValue(Attributes.ATTACK_DAMAGE));
			check(HeavyService.refusal(player, player.getMainHandItem()) == HeavyService.Refusal.UNFIT, "and gives no heavy blow");
			check(!HeavyService.release(player, player.getMainHandItem(), 40), "not even at a full charge");
			SkillService.set(player, skills(5, 2, 0));
		});
		context.waitTicks(2);
		check(world.getServer().computeOnServer(server -> SpecialWeapon.unfit(player(server))), "one constitution short is still short");
		world.getServer().runOnServer(server -> SkillService.set(player(server), skills(5, 3, 0)));
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(!SpecialWeapon.unfit(player), "strength 5 and constitution 3: it answers");
			check(near(player.getAttributeValue(Attributes.ATTACK_DAMAGE), 13.0 * 1.15), "at its full 13, strength added: " + player.getAttributeValue(Attributes.ATTACK_DAMAGE));
			check(HeavyService.refusal(player, player.getMainHandItem()) == HeavyService.Refusal.NONE, "and it charges");
		});
	}

	private static void snufferLeavesItsHook(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SLASH), Skills.EMPTY);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			AchievementService.clear(player);
			check(!AchievementService.has(player, AchievementService.SNUFFER), "no achievement yet");
			Snuffer keeper = MistinnEntities.SNUFFER.create(server.overworld(), EntitySpawnReason.COMMAND);
			keeper.setNoAi(true);
			keeper.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 6.5, 180.0F, 0.0F);
			server.overworld().addFreshEntity(keeper);
			keeper.kill(server.overworld());
		});
		context.waitTicks(5);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(AchievementService.has(player, AchievementService.SNUFFER), "seeing the Snuffer fall is remembered");
			List<ItemEntity> hooks = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(arena).inflate(16.0), e -> e.getItem().is(MistinnItems.SNUFFER_HOOK));
			check(hooks.size() == 1, "and it leaves its hook behind: " + hooks.size());
			SkillService.set(player, skills(2, 0, 0));
			check(!SpecialWeapon.SNUFFER_HOOK.fits(player), "the hook asks strength 3 as well");
			SkillService.set(player, skills(3, 0, 0));
			check(SpecialWeapon.SNUFFER_HOOK.fits(player), "with it and the Snuffer down, the hook answers");
		});
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			AchievementService.clear(player);
			SkillService.set(player, skills(3, 0, 0));
			check(!SpecialWeapon.SNUFFER_HOOK.fits(player), "strength alone is not enough for the hook");
			AchievementService.grant(player, AchievementService.SNUFFER);
		});
	}

	private static void hookDragsTheFarthest(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, MistinnItems.SNUFFER_HOOK, skills(3, 0, 0));
		int close = husk(world, arena, 0.0, 2.0);
		int farthest = husk(world, arena, 0.8, 4.4);
		context.waitTicks(2);
		heavyLanded(context, world, 0.5F);
		check(lost(world, close) > 4.0F && lost(world, farthest) > 4.0F, "the hook reaps both");
		double hooked = pull(world, farthest);
		double reaped = pull(world, close);
		check(hooked > 1.1, "and drags the farthest toward the hunter's feet, about 3 blocks' worth: " + hooked);
		check(reaped < hooked, "harder than the reap draws the rest: " + reaped);
	}

	private static void lanternBursts(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, MistinnItems.LANTERN_STAFF, skills(0, 0, 4));
		int ahead = husk(world, arena, 0.0, 3.0);
		int aside = husk(world, arena, 2.6, 2.4);
		int behind = husk(world, arena, 0.0, -3.5);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> check(SpecialWeapon.LANTERN_STAFF.fits(player(server)), "intellect 4 lights the lantern"));
		heavy(context, world, 1.0F);
		check(lost(world, ahead) > 14.0F && lost(world, aside) > 14.0F, "the burst burns all near it with intellect's strength, about 20: " + lost(world, ahead) + ", " + lost(world, aside));
		check(lost(world, behind) == 0.0F, "but not what is behind the hunter");
		check(world.getServer().computeOnServer(server -> mob(server, ahead).getRemainingFireTicks() > 0 || mob(server, ahead).isOnFire()), "and sets them alight");
	}

	private static void maulTollsAllAround(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, MistinnItems.TIDE_MAUL, skills(5, 3, 0));
		int[] ring = {husk(world, arena, 0.0, 2.5), husk(world, arena, 0.0, -2.5), husk(world, arena, -3.0, 0.0), husk(world, arena, 3.0, 0.0)};
		int far = husk(world, arena, 0.0, 7.0);
		context.waitTicks(2);
		boolean[] lifted = {true};
		world.getServer().runOnServer(server -> HeavyService.release(player(server), player(server).getMainHandItem(), 40));
		context.waitTicks(HeavyService.shape(WeaponStyle.SMASH, SpecialWeapon.TIDE_MAUL).landTicks() + 1);
		world.getServer().runOnServer(server -> {
			for (int id : ring) {
				if (mob(server, id).getDeltaMovement().y < 0.2 && mob(server, id).getY() < arena.getY() + 0.2) {
					lifted[0] = false;
				}
			}
		});
		context.waitTicks(10);
		for (int id : ring) {
			check(lost(world, id) > 20.0F, "the toll strikes all around, front, back and sides: " + lost(world, id));
		}
		check(lifted[0], "and throws them up");
		check(lost(world, far) == 0.0F, "but not past its reach");
	}

	/** 鐘舌: a full heavy blow rings out all round and breaks off every windup within six blocks, struck or not; a partial one does not. */
	private static void clapperBreaksEveryWindup(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, MistinnItems.BELL_CLAPPER, skills(4, 0, 0));
		world.getServer().runOnServer(server -> AchievementService.grant(player(server), AchievementService.SUNKEN_BELL));
		int ahead = husk(world, arena, 0.0, 2.0);
		int behind = husk(world, arena, 0.0, -4.0);
		int aside = husk(world, arena, 4.5, 0.0);
		int far = husk(world, arena, 0.0, 9.0);
		int[] all = {ahead, behind, aside, far};
		java.util.function.Consumer<MinecraftServer> windUp = server -> {
			for (int id : all) {
				mob(server, id).setAttached(WindupService.WINDUP, new WindupService.Windup(server.overworld().getGameTime(), 400, player(server).getId(), false));
			}
		};
		world.getServer().runOnServer(server -> {
			check(SpecialWeapon.BELL_CLAPPER.fits(player(server)), "the Sunken Bell down and strength 4: the clapper answers");
			windUp.accept(server);
		});
		heavy(context, world, 0.6F);
		check(world.getServer().computeOnServer(server -> WindupService.winding((net.minecraft.world.entity.Mob) mob(server, behind))),
			"a partial charge rings nothing out");
		world.getServer().runOnServer(server -> {
			player(server).setAttached(MistinnAttachments.STAMINA, StaminaService.max(player(server)));
			windUp.accept(server);
		});
		heavy(context, world, 1.0F);
		world.getServer().runOnServer(server -> {
			check(!WindupService.winding((net.minecraft.world.entity.Mob) mob(server, behind)), "a full one breaks off the windup of one behind, never struck");
			check(!WindupService.winding((net.minecraft.world.entity.Mob) mob(server, aside)), "and of one off to the side");
			check(WindupService.winding((net.minecraft.world.entity.Mob) mob(server, far)), "but not past six blocks");
		});
	}

	// ── Found in review ───────────────────────────────────────────

	/** A charge the weapon refuses gives the use key back, so whatever is in the other hand can act. */
	private static void refusedChargeLeavesTheOtherHand(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, MistinnItems.TIDE_MAUL, Skills.EMPTY);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			net.minecraft.world.InteractionResult result = player.getMainHandItem().getItem().use(server.overworld(), player, InteractionHand.MAIN_HAND);
			check(result == net.minecraft.world.InteractionResult.PASS, "an unfit hunter's charge is refused with a pass, not a fail: " + result);
			check(!player.isUsingItem(), "and no charge starts");
		});
	}

	/** A kill is the weapon's that struck it, even if the hand changed before the blow landed. */
	private static void killGoesToTheBlowsWeapon(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SLASH), Skills.EMPTY);
		int target = husk(world, arena, 0.0, 2.0);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			life.shizune.mistinn.achievement.AchievementService.clear(player);
			mob(server, target).setHealth(2.0F);
			check(HeavyService.release(player, player.getMainHandItem(), 40), "the heavy slash is let go");
			// the hand changes before the blow lands
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(iron(WeaponStyle.REAP)));
		});
		context.waitTicks(10);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(mob(server, target) == null || !mob(server, target).isAlive(), "the slash kills");
			check(life.shizune.mistinn.achievement.AchievementService.has(player, "kill:blade"), "the kill is the blade's");
			check(!life.shizune.mistinn.achievement.AchievementService.has(player, "kill:scythe"), "not the scythe's, which only came to hand after");
		});
	}

	/** Sliding into a creature the slide cannot hurt sweeps nothing, and is no achievement. */
	private static void slideMissPaysNothing(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, null, new Skills(Set.of(life.shizune.mistinn.skill.SkillTree.ROOT)));
		int target = husk(world, arena, 0.0, 1.0);
		world.getServer().runOnServer(server -> {
			life.shizune.mistinn.achievement.AchievementService.clear(player(server));
			mob(server, target).setPermanentlyInvulnerable(true);
			check(life.shizune.mistinn.skill.SlideService.request(player(server), 0.0F), "the slide starts");
		});
		context.waitTicks(12);
		check(!world.getServer().computeOnServer(server -> life.shizune.mistinn.achievement.AchievementService.has(player(server),
			life.shizune.mistinn.achievement.Achievement.SLIDE_KICK)), "a slide that hurts nothing is not swept away");
	}

	/** A blow let go just before stepping into another world does not land in the new one. */
	private static void heavyStaysInItsWorld(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SLASH), Skills.EMPTY);
		int far = world.getServer().computeOnServer(server -> {
			net.minecraft.server.level.ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
			BlockPos at = new BlockPos(arena.getX(), 100, arena.getZ());
			for (BlockPos floor : BlockPos.betweenClosed(at.offset(-3, -1, -3), at.offset(3, -1, 6))) {
				nether.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
			}
			for (BlockPos air : BlockPos.betweenClosed(at.offset(-3, 0, -3), at.offset(3, 3, 6))) {
				nether.setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
			}
			Husk mob = EntityTypes.HUSK.create(nether, EntitySpawnReason.COMMAND);
			mob.setNoAi(true);
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
			mob.setHealth(200.0F);
			mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 180.0F, 0.0F);
			nether.addFreshEntity(mob);
			ServerPlayer player = player(server);
			check(HeavyService.release(player, player.getMainHandItem(), 40), "the heavy slash is let go");
			player.teleportTo(nether, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			return mob.getId();
		});
		context.waitTicks(14);
		world.getServer().runOnServer(server -> {
			net.minecraft.server.level.ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
			LivingEntity mob = (LivingEntity) nether.getEntity(far);
			check(mob.getHealth() == 200.0F, "the blow let go in the overworld does not land in the nether: " + mob.getHealth());
			mob.discard();
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		});
		context.waitTicks(20);
	}

	/**
	 * A roll ends a charge on both sides without letting it go: with the real keys, charge, roll, let go,
	 * and nothing is struck, so the hunter never sees a blow the server did not land.
	 */
	private static void rollBreaksTheCharge(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SLASH), Skills.EMPTY);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getMainHandItem().getItem().use(server.overworld(), player, InteractionHand.MAIN_HAND);
			check(player.isUsingItem(), "the charge starts");
			life.shizune.mistinn.stamina.RollService.request(player, 90.0F);
			check(!player.isUsingItem(), "a roll ends it on the server");
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
		});
		context.waitTicks(30);
		int target = husk(world, arena, 0.9, 2.5);
		context.waitTicks(20);
		context.getInput().holdKey(options -> options.keyUse);
		context.waitTicks(20);
		check(context.computeOnClient(mc -> mc.player.isUsingItem()), "holding the use key charges");
		context.getInput().pressKey(options -> java.util.Arrays.stream(options.keyMappings).filter(k -> k.getName().equals("key.mistinn.roll")).findFirst().orElseThrow());
		context.waitTicks(2);
		check(!context.computeOnClient(mc -> mc.player.isUsingItem()), "the roll ends the charge on the hunter's own client");
		check(!world.getServer().computeOnServer(server -> player(server).isUsingItem()), "and on the server");
		context.getInput().releaseKey(options -> options.keyUse);
		context.waitTicks(12);
		check(lost(world, target) == 0.0F, "letting go after the roll strikes nothing: lost " + lost(world, target));
		context.waitTicks(20);
	}

	/** Both arms destroyed while charging: letting go strikes nothing, on the server as on the hunter's screen. */
	private static void deadArmsLetNothingGo(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SLASH), Skills.EMPTY);
		int target = husk(world, arena, 0.0, 2.0);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			BodyService.set(player, BodyService.get(player).with(life.shizune.mistinn.body.BodyPart.LEFT_ARM, 0.0F).with(life.shizune.mistinn.body.BodyPart.RIGHT_ARM, 0.0F));
			check(!HeavyService.canLetGo(player, player.getMainHandItem()), "two dead arms cannot let a heavy blow go");
			check(!HeavyService.release(player, player.getMainHandItem(), 40), "so the release does nothing");
			BodyService.restoreAll(player);
			check(HeavyService.canLetGo(player, player.getMainHandItem()), "whole arms can");
		});
		context.waitTicks(10);
		check(lost(world, target) == 0.0F, "and nothing is struck: lost " + lost(world, target));
	}

	/** A creature that dies of the lantern's fire after the burst is still the hunter's kill. */
	private static void fireKillIsTheHunters(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, MistinnItems.LANTERN_STAFF, skills(0, 0, 4));
		int target = husk(world, arena, 0.0, 3.0);
		world.getServer().runOnServer(server -> life.shizune.mistinn.achievement.AchievementService.clear(player(server)));
		heavyLanded(context, world, 1.0F);
		// counted from after the burst, whose own achievement pays too
		int salt = world.getServer().computeOnServer(server -> {
			check(mob(server, target).getRemainingFireTicks() > 0 || mob(server, target).isOnFire(), "the burst sets it alight");
			mob(server, target).setHealth(1.0F);
			return life.shizune.mistinn.salt.SaltService.get(player(server));
		});
		for (int i = 0; i < 100 && world.getServer().computeOnServer(server -> mob(server, target) != null && mob(server, target).isAlive()); i++) {
			context.waitTicks(1);
		}
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			int gained = life.shizune.mistinn.salt.SaltService.get(player) - salt;
			check(gained >= 200, "the fire's kill pays the hunter the husk's salt: " + gained);
			check(life.shizune.mistinn.achievement.AchievementService.has(player, "kill:spear"), "and counts for the staff's branch");
		});
	}

	// ── For the eye ───────────────────────────────────────────────

	/** Each branch's heavy blow frozen at its beats, and the sword qi, written to combat/. */
	private static void frames(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		world.getServer().runOnServer(server -> {
			clearArena(server, arena);
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		});
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		for (WeaponStyle style : WeaponStyle.values()) {
			world.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(iron(style))));
			context.waitTicks(20);
			float contact = HeavyService.shape(style, null).contact();
			float[][] beats = {{0, 1.0F}, {1, contact}, {1, (contact + 1.0F) / 2.0F - 0.1F}};
			String[] names = {"charged", "contact", "follow"};
			for (CameraType camera : new CameraType[] {CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK, CameraType.FIRST_PERSON}) {
				// hiding the interface hides one's own hand too: first person keeps it
				context.runOnClient(mc -> {
					mc.options.setCameraType(camera);
					if (mc.gui.hud.isHidden() == (camera == CameraType.FIRST_PERSON)) {
						mc.gui.hud.toggle();
					}
				});
				String view = camera == CameraType.FIRST_PERSON ? "1p" : camera == CameraType.THIRD_PERSON_FRONT ? "front" : "back";
				for (int i = 0; i < beats.length; i++) {
					float[] beat = beats[i];
					HeavyClient.Pose pose = new HeavyClient.Pose(beat[0] == 0 ? HeavyClient.Phase.CHARGE : HeavyClient.Phase.STRIKE, style, beat[1], 1.0F, 0.0F);
					context.runOnClient(mc -> HeavyClient.preview = pose);
					context.waitTicks(2);
					context.takeScreenshot(TestScreenshotOptions.of(style.id + "-" + view + "-" + names[i]).disableCounterPrefix().withDestinationDir(Path.of("combat")));
				}
			}
			context.runOnClient(mc -> HeavyClient.preview = null);
			// the qi: a light swing's and a heavy blow's, at their brightest, hand in view
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.runOnClient(mc -> {
				SwordQi.preview = 0.5F;
				WeaponSwings.preview = WeaponSwings.contact(style) + 0.05F;
				mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
			});
			context.waitTicks(2);
			context.takeScreenshot(TestScreenshotOptions.of("qi-" + style.id + "-light").disableCounterPrefix().withDestinationDir(Path.of("combat")));
			context.runOnClient(mc -> WeaponSwings.preview = -1.0F);
			context.waitTicks(30);
			context.runOnClient(mc -> HeavyClient.preview = new HeavyClient.Pose(HeavyClient.Phase.STRIKE, style, HeavyService.shape(style, null).contact() + 0.08F, 1.0F, 0.0F));
			context.waitTicks(2);
			context.takeScreenshot(TestScreenshotOptions.of("qi-" + style.id + "-heavy").disableCounterPrefix().withDestinationDir(Path.of("combat")));
			context.runOnClient(mc -> {
				HeavyClient.preview = null;
				SwordQi.preview = -1.0F;
			});
		}
		// the new weapons in hand, seen from the front
		context.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		for (Item item : new Item[] {iron(WeaponStyle.REAP), MistinnItems.SNUFFER_HOOK, MistinnItems.LANTERN_STAFF, MistinnItems.TIDE_MAUL}) {
			world.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item)));
			context.waitTicks(20);
			String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
			context.takeScreenshot(TestScreenshotOptions.of("held-" + name).disableCounterPrefix().withDestinationDir(Path.of("combat")));
		}
		context.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		world.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
	}

	private static boolean near(final double a, final double b) {
		return Math.abs(a - b) < 1.0E-3;
	}

	static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
