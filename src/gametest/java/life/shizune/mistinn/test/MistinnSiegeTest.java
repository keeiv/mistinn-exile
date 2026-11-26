package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.terra.CrystalDrake;
import life.shizune.mistinn.terra.CrystalLizard;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.Siege;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * A siege of a homestead: the lattice stirs, then three waves come up round the hub (lizards, and
 * drakes with them after the first), each when the last is down; holding all three gives every
 * hunter near salt and a crystal core. A second siege, left standing at dawn, crumbles away.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnSiegeTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set day");
			context.waitTicks(20);
			Settlements.Settlement[] home = new Settlements.Settlement[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 3, false, false));
				// a highland whose heart is far enough off that its elder sleeps
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(180, 0, 0));
				Domain domain = TerraService.settleAt(level, centre, 240);
				Settlements.Settlement built = Settlements.buildNow(level, domain);
				check(built != null, "a settlement is built");
				check(Settlements.light(level, built.hub(), player), "and its hub woken");
				home[0] = Settlements.of(server, domain.id());
				BlockPos hub = home[0].hub();
				player.teleportTo(level, hub.getX() + 3.5, hub.getY() + 1.0, hub.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			});
			context.waitTicks(40);
			world.getServer().runCommand("time set 14000");
			context.waitTicks(30);
			check(world.getServer().computeOnServer(server -> !Siege.watch(server, home[0].domain()).on()), "no siege comes of itself in a test world");

			// the lattice stirs, and three waves come and are held
			int[] salt = new int[1];
			world.getServer().runOnServer(server -> {
				salt[0] = SaltService.get(player(server));
				Siege.begin(server.overworld(), home[0], 20);
				check(Siege.watch(server, home[0].domain()).on(), "the lattice under the homestead stirs");
			});
			for (int wave = 1; wave <= Siege.WAVES; wave++) {
				int expected = wave;
				boolean came = false;
				for (int i = 0; i < 30 && !came; i++) {
					context.waitTicks(10);
					came = world.getServer().computeOnServer(server -> Siege.watch(server, home[0].domain()).wave() == expected);
				}
				check(came, "wave " + wave + " comes");
				world.getServer().runOnServer(server -> {
					ServerLevel level = server.overworld();
					Siege.Watch watch = Siege.watch(server, home[0].domain());
					int lizards = 0;
					int drakes = 0;
					for (UUID id : watch.foes()) {
						Entity foe = level.getEntity(id);
						if (foe instanceof CrystalLizard) {
							lizards++;
						} else if (foe instanceof CrystalDrake) {
							drakes++;
						}
						check(foe == null || foe.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(home[0].hub())) < 60.0 * 60.0, "it comes up round the hub");
					}
					check(lizards >= Siege.lizards(expected, 1) - 1, "wave " + expected + " brings lizards: " + lizards);
					check(drakes == Siege.drakes(expected), "and drakes after the first: " + drakes);
					check(watch.foes().stream().map(level::getEntity).allMatch(e -> !(e instanceof LivingEntity mob) || mob instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null),
						"each goes for the hunter");
					// the hunters bring them all down
					for (UUID id : watch.foes()) {
						if (level.getEntity(id) instanceof LivingEntity foe) {
							foe.hurtServer(level, level.damageSources().playerAttack(player(server)), 10000.0F);
						}
					}
				});
			}
			boolean held = false;
			for (int i = 0; i < 10 && !held; i++) {
				context.waitTicks(10);
				held = world.getServer().computeOnServer(server -> !Siege.watch(server, home[0].domain()).on());
			}
			check(held, "all three held, the siege is over");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(SaltService.get(player) >= salt[0] + Siege.SALT, "the hunter has salt of the tillers' thanks: " + salt[0] + " -> " + SaltService.get(player));
				List<ItemEntity> cores = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4.0),
					e -> e.getItem().is(MistinnItems.CRYSTAL_CORE));
				boolean carried = player.getInventory().countItem(MistinnItems.CRYSTAL_CORE) > 0;
				check(!cores.isEmpty() || carried, "and a crystal core");
			});

			// another, left standing at dawn: it crumbles away
			List<UUID> left = new java.util.ArrayList<>();
			world.getServer().runOnServer(server -> Siege.begin(server.overworld(), home[0], 1));
			boolean came = false;
			for (int i = 0; i < 10 && !came; i++) {
				context.waitTicks(10);
				came = world.getServer().computeOnServer(server -> Siege.watch(server, home[0].domain()).wave() == 1);
			}
			check(came, "a second siege comes");
			world.getServer().runOnServer(server -> left.addAll(Siege.watch(server, home[0].domain()).foes()));
			world.getServer().runCommand("time set 1000");
			context.waitTicks(30);
			world.getServer().runOnServer(server -> {
				check(!Siege.watch(server, home[0].domain()).on(), "at dawn the siege is over");
				for (UUID id : left) {
					Entity foe = server.overworld().getEntity(id);
					check(foe == null || !foe.isAlive(), "and what was left of it has crumbled");
				}
			});
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
