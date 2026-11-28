package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import life.shizune.mistinn.client.terra.CommissionScreen;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.expedition.DamGround;
import life.shizune.mistinn.expedition.Expedition;
import life.shizune.mistinn.expedition.Grounds;
import life.shizune.mistinn.expedition.Layout;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.terra.Commissions;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.TerraService;
import life.shizune.mistinn.terra.Tiller;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * The way into an expedition: a keeper's board offers none until the tillers trust the hunters (one commission done
 * opens the dam), then always one for the dam, which stays on the board when taken. Holding it, the hunter sets out from
 * the hub; getting out alive is the commission done, paid as any other, and the tillers trust the hunters more.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnExpeditionCommissionTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		Expedition.musterTicks = 40;
		Expedition.exitTicks = 40;
		Expedition.layout(Grounds.DAM, new Ground());
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 1000");
			context.waitTicks(20);
			Settlements.Settlement[] home = new Settlements.Settlement[1];
			int[] salt = new int[1];
			int[] expedition = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				server.setDifficulty(Difficulty.NORMAL, true);
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(180, 0, 0));
				Domain domain = TerraService.settleAt(level, centre, 240);
				Settlements.Settlement built = Settlements.buildNow(level, domain);
				check(Settlements.light(level, built.hub(), player), "a homestead is woken");
				home[0] = Settlements.of(server, domain.id());
				fly(player);
				Tiller elder = elder(level, home[0]);
				player.teleportTo(level, elder.getX() + 1.5, elder.getY(), elder.getZ(), Set.of(), 0.0F, 0.0F, true);
				check(Commissions.offers(server, home[0].domain()).stream().noneMatch(c -> c.kind() == Commissions.Kind.EXPEDITION),
					"untrusted, the board offers no expedition");
				Commissions.trust(server, 1);
				List<Commissions.Commission> offers = Commissions.offers(server, home[0].domain());
				Commissions.Commission dam = offers.stream().filter(c -> c.kind() == Commissions.Kind.EXPEDITION).findFirst().orElse(null);
				check(dam != null && dam.what().equals(Grounds.DAM.identifier().toString()), "one commission done, the dam is offered: " + offers);
				check(dam.describe().getString().contains(Expedition.name(Grounds.DAM).getString()), "named as the dam: " + dam.describe().getString());
				expedition[0] = offers.indexOf(dam);
			});
			// (waking the hub puts its tillers back afresh: the elder is looked for again once it has)
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Tiller elder = elder(level, home[0]);
				// the elder by the hub, as by day; the hunter beside both, at the board
				BlockPos hub = home[0].hub();
				elder.setNoAi(true);
				elder.teleportTo(hub.getX() + 3.5, hub.getY() + 1.0, hub.getZ() + 0.5);
				player.teleportTo(level, hub.getX() + 2.5, hub.getY() + 1.0, hub.getZ() + 2.5, Set.of(), 0.0F, 0.0F, true);
				salt[0] = SaltService.get(player);
				Commissions.open(player, elder);
			});
			context.waitTicks(10);
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof CommissionScreen), "the keeper's board is open");
			click(context, "gui.mistinn.commission.take", expedition[0]);
			context.waitFor(mc -> mc.player.hasAttached(Commissions.TAKEN), 100);
			world.getServer().runOnServer(server -> {
				Commissions.Taken taken = player(server).getAttached(Commissions.TAKEN);
				check(taken != null && taken.commission().kind() == Commissions.Kind.EXPEDITION, "on the board, the hunter takes the expedition");
				check(Commissions.offers(server, home[0].domain()).stream().anyMatch(c -> c.kind() == Commissions.Kind.EXPEDITION), "and it stays on the board");
			});
			click(context, "gui.mistinn.commission.depart", 0);
			check(world.getServer().computeOnServer(server -> Expedition.mustering()), "\"set out\" on the board, by the hub, and the party gathers");
			check(context.computeOnClient(mc -> !(mc.gui.screen() instanceof CommissionScreen)), "the board closes as they go");
			check(context.computeOnClient(mc -> mc.getSituationalMusic().sound().value() == life.shizune.mistinn.registry.MistinnSounds.MUSIC_MUSTER),
				"holding an expedition at a homestead, the setting out's music plays");
			context.waitTicks(Expedition.musterTicks + 15);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.level().dimension() == Grounds.DAM, "and go to the dam");
				player.teleportTo(server.getLevel(Grounds.DAM), 0.5, 65.0, 6.5, Set.of(), 0.0F, 0.0F, true);
			});
			context.waitTicks(Expedition.exitTicks + 30);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.level() == server.overworld(), "out alive, back at the hub");
				check(!player.hasAttached(Commissions.TAKEN), "the expedition is done");
				check(SaltService.get(player) - salt[0] >= Commissions.EXPEDITION_SALT, "and paid (the first commission's achievement too): " + (SaltService.get(player) - salt[0]));
				check(Commissions.trust(server) == 2, "and the tillers trust the hunters more");
			});
		} finally {
			Expedition.layout(Grounds.DAM, new DamGround());
			Expedition.musterTicks = 200;
			Expedition.exitTicks = 600;
		}
	}

	/** Clicks a button of the open board, by its words and which of them it is. */
	private static void click(final ClientGameTestContext context, final String key, final int index) {
		double[] at = context.computeOnClient(mc -> {
			double[] gui = ((CommissionScreen) mc.gui.screen()).buttonCentre(key, index);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(6);
	}

	private static void fly(final ServerPlayer player) {
		player.getAbilities().mayfly = true;
		player.getAbilities().flying = true;
		player.onUpdateAbilities();
	}

	private static Tiller elder(final ServerLevel level, final Settlements.Settlement home) {
		return level.getEntitiesOfClass(Tiller.class, new AABB(home.hub()).inflate(24.0), t -> t.role() == Tiller.Role.ELDER).getFirst();
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn expedition commission] ok: " + what);
	}

	/** A floor at 64 round the origin, one way out just south of where the party comes in. */
	private static final class Ground implements Layout {
		@Override
		public BoundingBox bounds() {
			return new BoundingBox(-16, 60, -16, 16, 72, 16);
		}

		@Override
		public List<Consumer<ServerLevel>> plan() {
			return List.of(level -> {
				for (int x = -12; x <= 12; x++) {
					for (int z = -12; z <= 12; z++) {
						level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 2);
					}
				}
			});
		}

		@Override
		public List<BlockPos> starts() {
			return List.of(new BlockPos(0, 65, -6));
		}

		@Override
		public List<Exit> exits() {
			return List.of(new Exit(new BlockPos(0, 65, 6), 2.0));
		}

		@Override
		public void stock(final ServerLevel level, final RandomSource random) {
		}

		@Override
		public void wave(final ServerLevel level, final Exit exit, final int wave, final RandomSource random) {
		}
	}
}
