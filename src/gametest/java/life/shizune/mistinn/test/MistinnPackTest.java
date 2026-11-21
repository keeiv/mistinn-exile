package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.enemy.NoiseService;
import life.shizune.mistinn.enemy.PackService;
import life.shizune.mistinn.enemy.WindupService;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.stamina.StaminaService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** 圍獵: a pack takes turns, two at most in at once, the rest round the hunter, the next turn to whoever is behind. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnPackTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		rolesAsDescribed();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty easy");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			context.waitTicks(10);
			BlockPos arena = world.getServer().computeOnServer(MistinnPackTest::arena);
			twoAreLeftAlone(context, world, arena);
			fiveTakeTurns(context, world, arena);
			aDownedHunterIsNotHeldBack(context, world, arena);
		}
	}

	// ── The roles, as numbers ─────────────────────────────────────

	private static PackService.Member member(final int id, final double degrees, final double distance, final boolean winding, final boolean striking, final boolean spent) {
		return new PackService.Member(id, Math.toRadians(degrees), distance, winding, striking, spent);
	}

	private static Map<Integer, PackService.Order> byId(final List<PackService.Order> orders) {
		Map<Integer, PackService.Order> out = new HashMap<>();
		for (PackService.Order order : orders) {
			out.put(order.id(), order);
		}
		return out;
	}

	private static void rolesAsDescribed() {
		// two creatures: both strike, nothing is held back
		Map<Integer, PackService.Order> two = byId(PackService.assign(List.of(member(1, 0, 3, false, false, false), member(2, 90, 3, false, false, false)), 0.0));
		check(two.get(1).strike() && two.get(2).strike(), "one or two creatures are left to themselves");
		// five, the hunter facing angle 0: the two most behind (near 180 degrees) get the turns
		List<PackService.Member> five = List.of(member(1, 0, 3, false, false, false), member(2, 40, 3, false, false, false),
			member(3, 170, 6, false, false, false), member(4, -160, 7, false, false, false), member(5, 90, 5, false, false, false));
		Map<Integer, PackService.Order> orders = byId(PackService.assign(five, 0.0));
		check(orders.get(3).strike() && orders.get(4).strike(), "the turns go to the two behind the hunter");
		check(orders.values().stream().filter(PackService.Order::strike).count() == 2, "two strike, no more");
		// the three waiting take places spread round the ring
		List<Double> ring = new ArrayList<>();
		orders.values().stream().filter(o -> !o.strike()).forEach(o -> ring.add(o.slot()));
		for (int i = 0; i < ring.size(); i++) {
			for (int j = i + 1; j < ring.size(); j++) {
				check(Math.abs(Math.toDegrees(PackService.behind(ring.get(i), ring.get(j)) * Math.PI)) >= 60.0, "the waiting ones keep apart: " + ring);
			}
		}
		// whoever is winding up keeps striking, even if a better place frees up
		Map<Integer, PackService.Order> winding = byId(PackService.assign(List.of(member(1, 0, 2, true, true, false), member(2, 10, 2, false, true, false),
			member(3, 180, 6, false, false, false), member(4, 90, 6, false, false, false)), 0.0));
		check(winding.get(1).strike(), "a creature mid-windup is never held back");
		// a spent turn passes on: the spent striker waits, the one behind goes
		Map<Integer, PackService.Order> spent = byId(PackService.assign(List.of(member(1, 0, 2, false, true, true), member(2, 10, 2, false, true, false),
			member(3, 180, 6, false, false, false), member(4, 90, 6, false, false, false)), 0.0));
		check(!spent.get(1).strike() && spent.get(3).strike() && spent.get(2).strike(), "a spent turn passes to the one behind");
		check(PackService.behind(Math.toRadians(180), 0.0) > 0.99 && PackService.behind(0.0, 0.0) < 0.01, "behind is measured from the hunter's facing");
		check(Math.abs(PackService.facing(0.0F) - Math.toRadians(90)) < 1.0E-6, "yaw 0 faces +z, which is the angle of a point straight ahead");
	}

	// ── In the world ──────────────────────────────────────────────

	private static BlockPos arena(final MinecraftServer server) {
		BlockPos pos = player(server).blockPosition().above(40).immutable();
		for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-16, -1, -16), pos.offset(16, -1, 16))) {
			server.overworld().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		}
		for (BlockPos air : BlockPos.betweenClosed(pos.offset(-16, 0, -16), pos.offset(16, 5, 16))) {
			server.overworld().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
		}
		return pos;
	}

	/**
	 * The hunter at the arena's centre facing +z, with {@code count} husks after them. (Not made
	 * invulnerable: vanilla creatures do not take an invulnerable player for an enemy. The sampling
	 * keeps the hunter whole instead.)
	 */
	private static List<Integer> hunted(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena, final int count) {
		List<Integer> ids = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			for (var entity : server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new AABB(arena).inflate(30.0), e -> !(e instanceof ServerPlayer))) {
				entity.discard();
			}
			PackService.forgetAll();
			NoiseService.forgetAll();
			BodyService.restoreAll(player);
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			List<Integer> out = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				double a = Math.PI / 2 + (i - (count - 1) / 2.0) * 0.5;
				Husk mob = EntityTypes.HUSK.create(server.overworld(), EntitySpawnReason.COMMAND);
				mob.setPersistenceRequired();
				mob.snapTo(arena.getX() + 0.5 + Math.cos(a) * 9.0, arena.getY(), arena.getZ() + 0.5 + Math.sin(a) * 9.0, 180.0F, 0.0F);
				server.overworld().addFreshEntity(mob);
				mob.setTarget(player);
				out.add(mob.getId());
			}
			return out;
		});
		context.waitTicks(2);
		return ids;
	}

	private static void twoAreLeftAlone(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		List<Integer> ids = hunted(context, world, arena, 2);
		context.waitTicks(40);
		world.getServer().runOnServer(server -> {
			for (int id : ids) {
				check(PackService.role((Mob) server.overworld().getEntity(id)) == null || PackService.mayStrike((Mob) server.overworld().getEntity(id)), "two husks are both free to strike");
			}
		});
	}

	/**
	 * Five husks on one hunter, sampled every tick for fifteen seconds: never more than two winding
	 * up at once, a blow always on its way (no long gap between windups), and some of them from behind.
	 */
	private static void fiveTakeTurns(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		List<Integer> ids = hunted(context, world, arena, 5);
		context.waitTicks(40);
		int mostAtOnce = 0;
		long lastStart = -1;
		long longestGap = 0;
		int fromBehind = 0;
		int starts = 0;
		Map<Integer, Boolean> was = new HashMap<>();
		double spreadSeen = 0.0;
		for (int tick = 0; tick < 300; tick++) {
			Object[] sample = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				keepWhole(player);
				int winding = 0;
				Map<Integer, Boolean> now = new HashMap<>();
				List<Double> ring = new ArrayList<>();
				int behind = 0;
				for (int id : ids) {
					Mob mob = (Mob) server.overworld().getEntity(id);
					if (mob == null) {
						continue;
					}
					mob.setTarget(player);
					boolean w = WindupService.winding(mob);
					now.put(id, w);
					winding += w ? 1 : 0;
					if (w && PackService.behind(PackService.angle(player.position(), mob.position()), PackService.facing(player.getYRot())) > 0.6) {
						behind |= 1 << ids.indexOf(id);
					}
					PackService.Role role = PackService.role(mob);
					if (role != null && !role.strike()) {
						ring.add(PackService.angle(player.position(), mob.position()));
					}
				}
				double spread = Double.MAX_VALUE;
				for (int i = 0; i < ring.size(); i++) {
					for (int j = i + 1; j < ring.size(); j++) {
						spread = Math.min(spread, PackService.behind(ring.get(i), ring.get(j)) * 180.0);
					}
				}
				return new Object[] {winding, now, behind, ring.size() >= 2 ? spread : 0.0, server.overworld().getGameTime()};
			});
			int winding = (Integer) sample[0];
			@SuppressWarnings("unchecked")
			Map<Integer, Boolean> now = (Map<Integer, Boolean>) sample[1];
			long time = (Long) sample[4];
			mostAtOnce = Math.max(mostAtOnce, winding);
			for (Map.Entry<Integer, Boolean> e : now.entrySet()) {
				if (e.getValue() && !was.getOrDefault(e.getKey(), false)) {
					starts++;
					if (((Integer) sample[2] & (1 << ids.indexOf(e.getKey()))) != 0) {
						fromBehind++;
					}
					if (lastStart >= 0) {
						longestGap = Math.max(longestGap, time - lastStart);
					}
					lastStart = time;
				}
			}
			was = now;
			spreadSeen = Math.max(spreadSeen, (Double) sample[3]);
			context.waitTicks(1);
		}
		System.out.println("PACK starts " + starts + " most " + mostAtOnce + " gap " + longestGap + " behind " + fromBehind + " spread " + spreadSeen);
		check(mostAtOnce <= PackService.STRIKERS, "never more than two winding up at once: " + mostAtOnce);
		check(starts >= 5, "and blows keep coming: " + starts + " in fifteen seconds");
		check(longestGap <= 80, "no long lull: the longest gap between windups is " + longestGap + " ticks");
		check(fromBehind >= 1, "some come from behind the hunter: " + fromBehind);
		check(spreadSeen >= 40.0, "the waiting ones spread round the hunter: " + spreadSeen + " degrees apart at best");
	}

	/** With both legs gone the pack lets go, so anyone in reach can finish the hunter. */
	private static void aDownedHunterIsNotHeldBack(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		List<Integer> ids = hunted(context, world, arena, 4);
		context.waitTicks(40);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			BodyService.set(player, BodyService.get(player).with(BodyPart.LEFT_LEG, 0.0F).with(BodyPart.RIGHT_LEG, 0.0F));
		});
		context.waitTicks(PackService.EVERY + 2);
		world.getServer().runOnServer(server -> {
			for (int id : ids) {
				Mob mob = (Mob) server.overworld().getEntity(id);
				if (mob != null) {
					check(PackService.mayStrike(mob), "every husk may go for a downed hunter");
				}
			}
			ServerPlayer player = player(server);
			BodyService.restoreAll(player);
		});
	}

	/** Undoes whatever the husks did, so the hunter stays standing through the sample. */
	private static void keepWhole(final ServerPlayer player) {
		BodyService.restoreAll(player);
		player.removeAttached(MistinnAttachments.WOUNDS);
		player.setHealth(player.getMaxHealth());
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
