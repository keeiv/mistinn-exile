package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.block.HubCoreBlock;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.TerraService;
import life.shizune.mistinn.terra.Tiller;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The crystal tillers: a highland's settlement is built once its ground has loaded, with its hub,
 * deck and people; its facilities cannot be broken or blown up; the keeper of trade deals in
 * crystal; harming one of them turns the warden on the hunter and the keeper away; three crystal
 * cores wake the hub and make the settlement the hunters' homestead.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTillersTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set day");
			context.waitTicks(20);

			int[] id = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(30, 0, 0));
				id[0] = TerraService.settleAt(level, centre, 60).id();
			});
			Settlements.Settlement[] built = new Settlements.Settlement[1];
			for (int i = 0; i < 60 && built[0] == null; i++) {
				context.waitTicks(20);
				built[0] = world.getServer().computeOnServer(server -> Settlements.of(server, id[0]));
			}
			check(built[0] != null, "the settlement is built once its ground has loaded");
			BlockPos hub = built[0].hub();

			// the settlement: hub, deck, people
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				check(level.getBlockState(hub).is(MistinnBlocks.HUB_CORE) && !level.getBlockState(hub).getValue(HubCoreBlock.LIT), "a sleeping hub at its middle");
				check(level.getBlockState(built[0].centre().offset(6, 0, 0)).is(MistinnBlocks.FACILITY_DECK), "on a deck");
				List<Tiller> people = level.getEntitiesOfClass(Tiller.class, new AABB(hub).inflate(16.0));
				for (Tiller.Role role : java.util.Arrays.stream(Tiller.Role.values()).filter(r -> !r.ship).toList()) {
					check(people.stream().anyMatch(t -> t.role() == role), "the settlement has its " + role.id);
				}
				// what they build cannot be broken or blown away
				check(level.getBlockState(hub).getDestroySpeed(level, hub) < 0.0F, "the hub cannot be broken");
				// a blast at the top of a pylon, well above the people: lamp and truss stand
				BlockPos lamp = null;
				for (BlockPos at : BlockPos.betweenClosed(hub.offset(-9, 6, -9), hub.offset(9, 8, 9))) {
					if (level.getBlockState(at).is(MistinnBlocks.RELAY_PYLON)
						&& level.getBlockState(at).getValue(life.shizune.mistinn.block.RelayPylonBlock.PART) == life.shizune.mistinn.block.RelayPylonBlock.Part.HEAD) {
						lamp = at.immutable();
						break;
					}
				}
				check(lamp != null, "a pylon's head to blow at");
				level.explode(null, lamp.getX() + 0.5, lamp.getY() + 0.5, lamp.getZ() + 0.5, 4.0F, Level.ExplosionInteraction.TNT);
				check(level.getBlockState(lamp).is(MistinnBlocks.RELAY_PYLON) && level.getBlockState(lamp.below()).is(MistinnBlocks.RELAY_PYLON),
					"nor blown up");
				// the keeper of trade
				Tiller elder = people.stream().filter(t -> t.role() == Tiller.Role.ELDER).findFirst().orElseThrow();
				check(elder.getOffers().getFirst().getResult().is(MistinnItems.LATTICE_CHIP), "the keeper trades crystal grit for homeward chips");
			});
			look(context, world, built[0].centre().offset(-14, 9, -14), built[0].centre(), "settlement");

			// a hunter harms one of them: the warden turns on them, the keeper turns away
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
				player.teleportTo(level, hub.getX() + 4.5, hub.getY(), hub.getZ() + 0.5, Set.of(), 90.0F, 0.0F, true);
				Tiller tiller = level.getEntitiesOfClass(Tiller.class, new AABB(hub).inflate(16.0), t -> t.role() == Tiller.Role.TILLER_1).getFirst();
				tiller.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
				Tiller elder = level.getEntitiesOfClass(Tiller.class, new AABB(hub).inflate(16.0), t -> t.role() == Tiller.Role.ELDER).getFirst();
				check(elder.holdsAgainst(player), "the whole settlement holds it against them");
			});
			boolean guarded = false;
			for (int i = 0; i < 40 && !guarded; i++) {
				context.waitTicks(5);
				guarded = world.getServer().computeOnServer(server -> server.overworld().getEntitiesOfClass(Tiller.class, new AABB(hub).inflate(20.0),
					t -> t.role() == Tiller.Role.WARDEN && t.getTarget() == player(server)).size() == 1);
			}
			check(guarded, "the warden turns on them");

			// three crystal cores wake the hub: the homestead
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				ItemStack cores = new ItemStack(MistinnItems.CRYSTAL_CORE, Settlements.CORES);
				player.setItemInHand(InteractionHand.MAIN_HAND, cores);
				level.getBlockState(hub).useItemOn(cores, level, player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(hub), Direction.UP, hub, false));
				check(level.getBlockState(hub).getValue(HubCoreBlock.LIT), "the hub wakes");
				check(player.getMainHandItem().isEmpty(), "taking the three cores");
				check(Settlements.homesteads(server).size() == 1, "the settlement is a homestead");
				check(AchievementService.has(player, Achievement.HOMESTEAD), "and it is remembered");
				player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
			});
			context.waitTicks(5);
			check(context.computeOnClient(mc -> {
				List<Settlements.Settlement> view = mc.level.getAttached(Settlements.VIEW);
				return view != null && view.stream().anyMatch(Settlements.Settlement::lit);
			}), "every client knows of the homestead");
			look(context, world, hub.offset(-5, 2, -4), hub, "hub-lit");
		}
	}

	private static void look(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos from, final BlockPos at, final String name) {
		world.getServer().runOnServer(server -> {
			double dx = at.getX() - from.getX();
			double dz = at.getZ() - from.getZ();
			double dy = at.getY() + 0.5 - (from.getY() + 1.62);
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
			ServerPlayer player = player(server);
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.teleportTo(server.overworld(), from.getX() + 0.5, from.getY(), from.getZ() + 0.5, Set.of(), yaw, pitch, true);
		});
		context.waitTicks(40);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("tillers")));
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
