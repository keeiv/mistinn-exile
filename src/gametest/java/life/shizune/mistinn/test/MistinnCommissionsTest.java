package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.terra.Commissions;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.Siege;
import life.shizune.mistinn.terra.TerraService;
import life.shizune.mistinn.terra.Tiller;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * 泰拉的委託: only the keeper of a woken homestead gives commissions (its elder), and only in story and free worlds; its
 * board holds three, the same all day; a hunter takes one at a time, and the server refuses what is not so (no keeper,
 * one already held, a commission not on the board). A hunt is done by bringing the creatures down, a delivery by handing
 * in what was asked (part, then the rest), a guard by holding the tide it brings that very night. Done, it pays and the
 * tillers trust the hunters more; at enough trust the keeper of trade brings out better wares. Let go, or held too long,
 * a commission is gone.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnCommissionsTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 1000");
			context.waitTicks(20);
			Settlements.Settlement[] home = new Settlements.Settlement[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(180, 0, 0));
				Domain domain = TerraService.settleAt(level, centre, 240);
				Settlements.Settlement built = Settlements.buildNow(level, domain);
				Tiller elder = elder(level, built);
				check(Commissions.keeps(elder) == null, "a settlement not yet woken gives no commissions");
				check(Settlements.light(level, built.hub(), player), "a homestead is woken");
				home[0] = Settlements.of(server, domain.id());
				BlockPos hub = home[0].hub();
				player.teleportTo(level, hub.getX() + 2.5, hub.getY() + 1.0, hub.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			});
			context.waitTicks(20);

			// the keeper, and its board
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Tiller elder = elder(level, home[0]);
				check(Commissions.keeper(level, home[0]) == elder && Commissions.keeps(elder) != null, "the elder of a woken homestead keeps its board");
				Tiller other = level.getEntitiesOfClass(Tiller.class, new AABB(home[0].hub()).inflate(24.0), t -> t.role() != Tiller.Role.ELDER).getFirst();
				check(Commissions.keeps(other) == null, "the others do not, while the elder lives");
				Commissions.Board board = Commissions.board(server, home[0].domain());
				check(board.offers().size() == Commissions.BOARD, "three commissions on it: " + board.offers());
				check(Commissions.board(server, home[0].domain()).offers().equals(board.offers()), "and the same three all day");
				// the board, as the test will have it
				Commissions.post(server, home[0].domain(), List.of(
					new Commissions.Commission("t1", Commissions.Kind.HUNT, "mistinn:crystal_lizard", 2, 80, 0),
					new Commissions.Commission("t2", Commissions.Kind.DELIVER, "mistinn:crystal_dust", 10, 100, 0),
					new Commissions.Commission("t3", Commissions.Kind.GUARD, "", 1, 400, 1)));
				walkTo(player, elder);
				check(Commissions.take(player, other, "t1") == Commissions.Result.NO_KEEPER, "a tiller who does not keep the board cannot give one");
				check(Commissions.take(player, elder, "nope") == Commissions.Result.GONE, "nor can one not on the board be taken");
				player.teleportTo(level, elder.getX() + 20.0, elder.getY(), elder.getZ(), Set.of(), 0.0F, 0.0F, true);
				check(Commissions.take(player, elder, "t1") == Commissions.Result.NO_KEEPER, "from afar, nothing");
				walkTo(player, elder);
				check(Commissions.take(player, elder, "t1") == Commissions.Result.TAKEN, "near the elder, a hunt is taken");
				check(Commissions.take(player, elder, "t2") == Commissions.Result.BUSY, "one at a time");
				check(Commissions.board(server, home[0].domain()).offers().size() == 2, "and it is off the board");
			});

			// a hunt: two lizards brought down
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				int salt = SaltService.get(player);
				for (int i = 0; i < 2; i++) {
					Mob lizard = MistinnEntities.CRYSTAL_LIZARD.create(level, EntitySpawnReason.COMMAND);
					lizard.snapTo(player.getX() + 2.0 + i, player.getY(), player.getZ(), 0.0F, 0.0F);
					level.addFreshEntity(lizard);
					lizard.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
					if (i == 0) {
						check(player.getAttached(Commissions.TAKEN).done() == 1, "one brought down is one of two");
					}
				}
				check(!player.hasAttached(Commissions.TAKEN), "two, and the hunt is done");
				check(SaltService.get(player) - salt >= 80, "and paid: " + (SaltService.get(player) - salt));
				check(Commissions.trust(server) == 1, "the tillers trust the hunters a little more");
				check(life.shizune.mistinn.achievement.AchievementService.has(player, life.shizune.mistinn.achievement.Achievement.COMMISSION_DONE),
					"the first commission done is remembered: entrusted");
			});

			// a delivery: part, then the rest, from the hand
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Tiller elder = elder(level, home[0]);
				walkTo(player, elder);
				check(Commissions.take(player, elder, "t2") == Commissions.Result.TAKEN, "a delivery taken");
				check(Commissions.handIn(player, elder) == Commissions.Result.NOTHING_TO_HAND, "empty-handed there is nothing to give");
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.CRYSTAL_DUST, 6));
				check(Commissions.handIn(player, elder) == Commissions.Result.HANDED && player.getAttached(Commissions.TAKEN).done() == 6, "six of ten");
				check(player.getMainHandItem().isEmpty(), "taken from the hand");
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.CRYSTAL_DUST, 16));
				check(Commissions.handIn(player, elder) == Commissions.Result.DONE, "the rest, and it is done");
				check(player.getMainHandItem().getCount() == 12, "only what was still wanted is taken: " + player.getMainHandItem().getCount());
				check(Commissions.trust(server) == 2, "trust two");
				// at that trust the keeper of trade brings out more
				List<MerchantOffer> offers = new ArrayList<>(elder.getOffers());
				check(offers.stream().anyMatch(o -> o.getResult().is(MistinnItems.CRYSTAL_CORE)), "the elder now sells crystal cores for grit");
			});

			// let go, and held too long
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Tiller elder = Commissions.keeper(level, home[0]);
				check(elder != null, "someone keeps the board");
				Commissions.post(server, home[0].domain(), List.of(new Commissions.Commission("t4", Commissions.Kind.HUNT, "mistinn:crystal_drake", 2, 240, 1),
					new Commissions.Commission("t5", Commissions.Kind.HUNT, "mistinn:rift_hound", 4, 200, 0)));
				walkTo(player, elder);
				check(Commissions.take(player, elder, "t4") == Commissions.Result.TAKEN, "taken");
				check(Commissions.abandon(player) == Commissions.Result.ABANDONED && !player.hasAttached(Commissions.TAKEN), "and let go, at no cost");
				check(Commissions.take(player, elder, "t5") == Commissions.Result.TAKEN, "another taken");
				Commissions.Taken taken = player.getAttached(Commissions.TAKEN);
				player.setAttached(Commissions.TAKEN, new Commissions.Taken(taken.commission(), taken.domain(), 0, level.getGameTime() - 1));
			});
			context.waitTicks(25);
			check(world.getServer().computeOnServer(server -> !player(server).hasAttached(Commissions.TAKEN)), "held past its time, it runs out");

			// not on the tillers' way: there the elder is the walker's own
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				level.setAttached(WorldMode.ATTACHMENT, WorldMode.PATH);
				Tiller keeper = Commissions.keeper(level, home[0]);
				check(Commissions.keeps(keeper) == null, "on the tillers' way no commissions are given");
				level.setAttached(WorldMode.ATTACHMENT, WorldMode.FREE);
				// with the keeper gone, another keeps the board
				keeper.discard();
				Tiller next = Commissions.keeper(level, home[0]);
				check(next != null && next != keeper && Commissions.keeps(next) != null, "with the keeper gone, another keeps the board: " + next);
			});

			// a guard: the tide comes that night, and holding it is the guard done
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Tiller keeper = Commissions.keeper(level, home[0]);
				Commissions.post(server, home[0].domain(), List.of(new Commissions.Commission("t3", Commissions.Kind.GUARD, "", 1, 400, 1)));
				walkTo(player, keeper);
				check(Commissions.take(player, keeper, "t3") == Commissions.Result.TAKEN, "a guard taken, from whoever keeps the board now");
				// woken long enough ago for a tide to be allowed
				Settlements.Ledger ledger = Settlements.ledger(server);
				List<Settlements.Settlement> built = new ArrayList<>(ledger.built());
				built.replaceAll(s -> s.domain() == home[0].domain() ? s.lit(level.getGameTime() - Siege.SETTLING - 20) : s);
				level.setAttached(Settlements.LEDGER, new Settlements.Ledger(List.copyOf(built), ledger.skipped()));
			});
			world.getServer().runCommand("time set 13500");
			context.waitTicks(60);
			check(world.getServer().computeOnServer(server -> Siege.watch(server, home[0].domain()).on()), "the night falls, and the tide its guard called comes");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				int salt = SaltService.get(player);
				Commissions.held(player, home[0].domain());
				check(!player.hasAttached(Commissions.TAKEN) && SaltService.get(player) - salt == 400, "held, the guard is done and paid");
				check(player.getInventory().countItem(MistinnItems.CRYSTAL_CORE) >= 1, "with a core");
			});
		}
	}

	private static Tiller elder(final ServerLevel level, final Settlements.Settlement home) {
		return level.getEntitiesOfClass(Tiller.class, new AABB(home.hub()).inflate(24.0), t -> t.role() == Tiller.Role.ELDER).getFirst();
	}

	private static void walkTo(final ServerPlayer player, final Tiller tiller) {
		player.teleportTo((ServerLevel) tiller.level(), tiller.getX() + 1.5, tiller.getY(), tiller.getZ(), Set.of(), 0.0F, 0.0F, true);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn commissions] ok: " + what);
	}
}
