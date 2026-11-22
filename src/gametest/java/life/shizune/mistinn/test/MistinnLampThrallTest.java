package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.client.enemy.EnemyMotion;
import life.shizune.mistinn.client.shore.LampThrallModel;
import life.shizune.mistinn.client.shore.LampThrallRenderer;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.enemy.EnemyRig;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.shore.LampThrall;
import life.shizune.mistinn.stamina.StaminaService;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 燈奴: near enough, its lantern flares and it stands still a breath, the warning; then it rushes
 * and strikes whoever is still in the way, and is spent a moment after. A full heavy blow during
 * the flare breaks it off.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnLampThrallTest implements FabricClientGameTest {
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
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("difficulty normal");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			BlockPos arena = arena(world);
			context.waitTicks(10);
			world.getServer().runOnServer(server -> check(EnemyRig.of(MistinnEntities.LAMP_THRALL) == EnemyRig.UNDEAD, "it fights as the undead do"));
			drawnOnItsOwnFrame(context, world, arena);
			cloakSwingsAsItWalks(context);
			raisesItsLampToFlare(context);
			flaresThenRushes(context, world, arena);
			heavyBlowBreaksTheFlare(context, world, arena);
		}
	}

	/**
	 * It is drawn on its own frame, not the zombie's: bent forward, so the hood hangs out ahead of its feet, no
	 * taller than the space it takes, and nothing of it, cloak or tatters, sinks into the ground, standing or rocked back.
	 */
	private static void drawnOnItsOwnFrame(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		for (float side : new float[] {-1.0F, 0.0F, 1.0F}) {
			float[] shape = context.computeOnClient(mc -> {
				LampThrall entity = MistinnEntities.LAMP_THRALL.create(mc.level, EntitySpawnReason.COMMAND);
				// a model only, never added to the world: it still needs an id to be drawn
				entity.setId(-4242);
				check(((LivingEntityRenderer<?, ?, ?>) mc.getEntityRenderDispatcher().getRenderer(entity)).getModel() instanceof LampThrallModel,
					"it is drawn on its own frame");
				List<ClipProbe.Box> rest = ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> { }, true, null, null);
				List<ClipProbe.Box> rocked = ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> {
					EnemyMotion.Sample sample = state.getData(EnemyMotion.KEY);
					state.setData(EnemyMotion.KEY, sample.rocked(1.0F, side));
				}, true, null, null);
				// facing south: ahead is +z
				return new float[] {ClipProbe.reach(rest, -1.0F), ClipProbe.reach(rocked, -1.0F), ClipProbe.reach(rest, 1.0F), meanZ(rest, "/head"), meanZ(rest, "/right_leg", "/left_leg")};
			});
			check(shape[0] > -0.06F, "standing, nothing of it sinks into the ground: lowest " + shape[0]);
			check(shape[1] > -0.06F && shape[1] > shape[0] - 0.05F, "rocked back (side " + side + "), nothing sinks into the ground: lowest " + shape[1]);
			check(shape[2] < 1.95F, "no taller than the space it takes: top " + shape[2]);
			check(shape[3] - shape[4] > 0.15F, "bent forward, its hood hangs out ahead of its feet: " + (shape[3] - shape[4]));
		}
		// a still from the side, for the eye
		int id = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			player.setGameMode(GameType.CREATIVE);
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() - 2.5, Set.of(), 0.0F, 12.0F, true);
			LampThrall thrall = MistinnEntities.LAMP_THRALL.create(server.overworld(), EntitySpawnReason.COMMAND);
			thrall.setNoAi(true);
			thrall.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, 90.0F, 0.0F);
			thrall.setYHeadRot(90.0F);
			thrall.setYBodyRot(90.0F);
			server.overworld().addFreshEntity(thrall);
			return thrall.getId();
		});
		context.waitTicks(10);
		context.takeScreenshot("thrall-00-frame");
		world.getServer().runOnServer(server -> {
			server.overworld().getEntity(id).discard();
			player(server).setGameMode(GameType.SURVIVAL);
		});
	}

	/** While its lantern flares, it holds the lantern up over its head, where a hunter sees the warning from afar. */
	private static void raisesItsLampToFlare(final ClientGameTestContext context) {
		float[] got = context.computeOnClient(mc -> {
			LampThrall entity = MistinnEntities.LAMP_THRALL.create(mc.level, EntitySpawnReason.COMMAND);
			// a model only, never added to the world: it still needs an id to be drawn
			entity.setId(-4242);
			List<ClipProbe.Box> prowling = ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> { }, true, null, null);
			List<ClipProbe.Box> flaring = ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> {
				if (state instanceof LampThrallRenderer.State thrall) {
					thrall.flaring = true;
				}
			}, true, null, null);
			return new float[] {top(prowling, "/right_arm"), top(prowling, "/head"), top(flaring, "/right_arm"), top(flaring, "/head")};
		});
		check(got[0] < got[1], "prowling, its lantern hangs below its head");
		check(got[2] > got[3], "flaring, it holds the lantern up over its head: hand " + got[2] + ", head " + got[3]);
	}

	/** The highest any box under this part reaches. */
	private static float top(final List<ClipProbe.Box> boxes, final String part) {
		float most = -Float.MAX_VALUE;
		for (ClipProbe.Box box : boxes) {
			if (box.path().startsWith(part)) {
				float half = 0.0F;
				for (int i = 0; i < 3; i++) {
					half += box.half()[i] * Math.abs(box.axes()[i].y);
				}
				most = Math.max(most, box.center().y + half);
			}
		}
		return most;
	}

	/** Walking, the hem of its cloak swings out behind it, and the tatters at the hem swing with its steps. */
	private static void cloakSwingsAsItWalks(final ClientGameTestContext context) {
		float[] got = context.computeOnClient(mc -> {
			LampThrall entity = MistinnEntities.LAMP_THRALL.create(mc.level, EntitySpawnReason.COMMAND);
			// a model only, never added to the world: it still needs an id to be drawn
			entity.setId(-4242);
			List<ClipProbe.Box> still = ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> { }, true, null, null);
			List<ClipProbe.Box> step = ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> {
				state.walkAnimationSpeed = 1.0F;
				state.walkAnimationPos = 0.0F;
			}, true, null, null);
			List<ClipProbe.Box> stride = ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> {
				state.walkAnimationSpeed = 1.0F;
				state.walkAnimationPos = 2.4F;
			}, true, null, null);
			return new float[] {cloakZ(still, HEM), cloakZ(step, HEM), moved(step, stride, TATTER)};
		});
		// facing south: behind it is -z
		check(got[1] < got[0] - 0.02F, "walking, the hem of its cloak swings out behind it: " + got[1] + " against " + got[0]);
		check(got[2] > 0.05F, "and its tatters swing with its steps: they moved " + got[2] + " between two steps");
	}

	/** The bottom band of the cloak's panels, and the tatters at its hem. */
	private static final java.util.regex.Pattern HEM = java.util.regex.Pattern.compile("/body/spine/cloak/cloak_\\d+_3");
	private static final java.util.regex.Pattern TATTER = java.util.regex.Pattern.compile("/body/spine/cloak/cloak_tatter_\\d+");

	/** Where along z, on average, the cloak's boxes this matches lie. */
	private static float cloakZ(final List<ClipProbe.Box> boxes, final java.util.regex.Pattern parts) {
		float sum = 0.0F;
		int count = 0;
		for (ClipProbe.Box box : boxes) {
			if (parts.matcher(box.path()).matches()) {
				sum += box.center().z;
				count++;
			}
		}
		return sum / count;
	}

	/** How far, all told, the boxes this matches moved from one pose to the other. */
	private static float moved(final List<ClipProbe.Box> from, final List<ClipProbe.Box> to, final java.util.regex.Pattern parts) {
		float total = 0.0F;
		for (int i = 0; i < from.size(); i++) {
			if (parts.matcher(from.get(i).path()).matches()) {
				total += from.get(i).center().distance(to.get(i).center());
			}
		}
		return total;
	}

	/** Where along z, on average, the boxes under these parts sit. */
	private static float meanZ(final List<ClipProbe.Box> boxes, final String... parts) {
		float sum = 0.0F;
		int count = 0;
		for (ClipProbe.Box box : boxes) {
			for (String part : parts) {
				if (box.path().startsWith(part)) {
					sum += box.center().z;
					count++;
				}
			}
		}
		return count == 0 ? Float.NaN : sum / count;
	}

	private static void flaresThenRushes(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int id = spawn(world, arena);
		Vec3[] at = new Vec3[1];
		long[] flared = new long[1];
		until(context, world, server -> thrall(server, id).state() == LampThrall.FLARE, 200, "near enough, its lantern flares");
		world.getServer().runOnServer(server -> {
			LampThrall thrall = thrall(server, id);
			check(thrall.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.LANTERN), "the lantern burns bright");
			at[0] = thrall.position();
			flared[0] = server.overworld().getGameTime();
		});
		// a moment for the flare to reach the screen: the lantern held up, burning orange
		context.waitTicks(3);
		context.takeScreenshot("thrall-01-flare");
		until(context, world, server -> thrall(server, id).state() != LampThrall.FLARE, LampThrall.FLARE_TICKS + 10, "the flare ends");
		world.getServer().runOnServer(server -> {
			LampThrall thrall = thrall(server, id);
			long waited = server.overworld().getGameTime() - flared[0];
			check(waited >= LampThrall.FLARE_TICKS - 2, "the warning lasts long enough to answer: " + waited + " ticks");
			check(thrall.state() == LampThrall.RUSHING, "then it rushes");
		});
		context.waitTicks(3);
		context.takeScreenshot("thrall-02-rush");
		until(context, world, server -> thrall(server, id).state() == LampThrall.SPENT, LampThrall.RUSH_TICKS + 10, "the rush ends");
		world.getServer().runOnServer(server -> {
			LampThrall thrall = thrall(server, id);
			check(thrall.position().distanceTo(at[0]) > 1.5, "the rush carried it forward");
			check(thrall.struckLastRush(), "the hunter who stood in the way is struck");
			check(thrall.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.SOUL_LANTERN), "spent, its lantern gutters back to blue");
		});
	}

	private static void heavyBlowBreaksTheFlare(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int id = spawn(world, arena);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON)));
		});
		until(context, world, server -> thrall(server, id).state() == LampThrall.FLARE, 200, "it flares");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			LampThrall thrall = thrall(server, id);
			// face it, close enough to reach, and let a full heavy blow go
			Vec3 to = thrall.position().subtract(player.position());
			player.teleportTo(server.overworld(), thrall.getX() - to.normalize().x * 2.0, thrall.getY(), thrall.getZ() - to.normalize().z * 2.0,
				Set.of(), (float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0F, 10.0F, true);
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
			check(HeavyService.release(player, player.getMainHandItem(), HeavyService.chargeTicks(player, WeaponStyle.SLASH)), "the blow is let go");
		});
		until(context, world, server -> thrall(server, id).state() == LampThrall.SPENT, 30, "the full blow breaks off the flare");
		world.getServer().runOnServer(server -> thrall(server, id).discard());
	}

	private static BlockPos arena(final TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			player.setGameMode(GameType.SURVIVAL);
			BlockPos pos = player.blockPosition().above(12);
			for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -12), pos.offset(8, -1, 12))) {
				player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
			}
			for (BlockPos air : BlockPos.betweenClosed(pos.offset(-8, 0, -12), pos.offset(8, 4, 12))) {
				player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
			}
			return pos;
		});
	}

	/** A thrall eight blocks off, the hunter in its sights, the hunter facing it. */
	private static int spawn(final TestSingleplayerContext world, final BlockPos arena) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			server.overworld().getEntitiesOfClass(LampThrall.class, new AABB(arena).inflate(24.0)).forEach(LampThrall::discard);
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() - 4.5, Set.of(), 0.0F, 10.0F, true);
			player.setHealth(player.getMaxHealth());
			BodyService.restoreAll(player);
			LampThrall thrall = MistinnEntities.LAMP_THRALL.create(server.overworld(), EntitySpawnReason.COMMAND);
			thrall.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 3.5, 180.0F, 0.0F);
			server.overworld().addFreshEntity(thrall);
			thrall.setTarget(player);
			return thrall.getId();
		});
	}

	private static void until(final ClientGameTestContext context, final TestSingleplayerContext world,
		final java.util.function.Predicate<MinecraftServer> condition, final int ticks, final String what) {
		for (int i = 0; i < ticks; i++) {
			if (world.getServer().computeOnServer(condition::test)) {
				return;
			}
			context.waitTicks(1);
		}
		throw new AssertionError("timed out waiting until " + what);
	}

	private static LampThrall thrall(final MinecraftServer server, final int id) {
		return (LampThrall) server.overworld().getEntity(id);
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
