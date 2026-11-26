package life.shizune.mistinn.test;

import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.CrystalDrake;
import life.shizune.mistinn.terra.CrystalLizard;
import life.shizune.mistinn.terra.Tiller;
import life.shizune.mistinn.terra.TillerTools;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The crystal tillers' tools: the whetstone leaves an edge of grit that keeps a spear's thrust from
 * glancing off a drake's facets and bites deeper, for a while; the fork makes the lizards lying
 * still and the drakes near glow, and is worn and rests a while after; the tillers sell both.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTillerToolsTest implements FabricClientGameTest {
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

			// the whetstone: a spear's thrust no longer glances off, and bites deeper, for a while
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.MIST_SPEAR));
				float bare = CrystalDrake.facets(level.damageSources().playerAttack(player));
				check(bare == CrystalDrake.GLANCE, "a bare spear's thrust glances off: " + bare);
				ItemStack stones = new ItemStack(MistinnItems.CRYSTAL_WHETSTONE, 2);
				player.setItemInHand(InteractionHand.OFF_HAND, stones);
				ItemStack left = stones.getItem().finishUsingItem(stones, level, player);
				check(left.getCount() == 1, "working the whetstone uses one: " + left.getCount());
				check(TillerTools.edged(player), "the blade has its edge of grit");
				float honed = CrystalDrake.facets(level.damageSources().playerAttack(player));
				check(Math.abs(honed - TillerTools.BITE) < 1.0E-4F, "the honed thrust is not turned, and bites deeper: " + honed);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.BELL_GREATSWORD));
				float heavy = CrystalDrake.facets(level.damageSources().playerAttack(player));
				check(Math.abs(heavy - CrystalDrake.CRACK * TillerTools.BITE) < 1.0E-4F, "a honed greatsword cracks deeper still: " + heavy);
				// and it wears off
				player.setAttached(TillerTools.EDGED, level.getGameTime() - 1L);
				check(CrystalDrake.facets(level.damageSources().playerAttack(player)) == CrystalDrake.CRACK, "until the edge wears off");
				player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			});

			// the fork: the lizards lying still, and the drakes, answer it and glow
			CrystalLizard[] lizard = new CrystalLizard[1];
			CrystalDrake[] drake = new CrystalDrake[1];
			CrystalLizard[] far = new CrystalLizard[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				lizard[0] = MistinnEntities.CRYSTAL_LIZARD.spawn(level, player.blockPosition().offset(14, 0, 0), EntitySpawnReason.COMMAND);
				lizard[0].setPersistenceRequired();
				lizard[0].lieStill(1000);
				drake[0] = MistinnEntities.CRYSTAL_DRAKE.spawn(level, player.blockPosition().offset(-10, 6, 6), EntitySpawnReason.COMMAND);
				drake[0].setPersistenceRequired();
				drake[0].setNoAi(true);
				far[0] = MistinnEntities.CRYSTAL_LIZARD.spawn(level, player.blockPosition().offset(0, 0, 60), EntitySpawnReason.COMMAND);
				far[0].setPersistenceRequired();
				far[0].setNoAi(true);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.RESONANCE_FORK));
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				ItemStack fork = player.getMainHandItem();
				fork.getItem().use(level, player, InteractionHand.MAIN_HAND);
				check(lizard[0].hasEffect(MobEffects.GLOWING), "the lizard lying still answers and glows");
				check(lizard[0].isStill(), "and still lies still");
				check(drake[0].hasEffect(MobEffects.GLOWING), "a drake near answers too");
				check(!far[0].hasEffect(MobEffects.GLOWING), "one beyond hearing does not");
				check(fork.getDamageValue() == 1, "the fork wears a little: " + fork.getDamageValue());
				check(player.getCooldowns().isOnCooldown(fork), "and must rest before it rings again");
			});

			// the tillers sell both
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				Tiller tiller = MistinnEntities.TILLER.spawn(level, player(server).blockPosition().offset(3, 0, 0), EntitySpawnReason.COMMAND);
				boolean stone = false;
				boolean fork = false;
				for (MerchantOffer offer : tiller.getOffers()) {
					stone |= offer.getResult().is(MistinnItems.CRYSTAL_WHETSTONE);
					fork |= offer.getResult().is(MistinnItems.RESONANCE_FORK);
				}
				check(stone && fork, "the tillers sell the whetstone and the fork");
				// what the others say: their own lines, and what a squall calls for
				tiller.settle(Tiller.Role.WARDEN, tiller.blockPosition());
				java.util.Set<String> said = new java.util.HashSet<>();
				for (int i = 0; i < 200; i++) {
					said.add(tiller.line(player(server)));
				}
				check(said.size() == Tiller.LINES && said.stream().allMatch(k -> k.startsWith("message.mistinn.tiller.warden.")), "a warden has all its lines: " + said);
				player(server).setAttached(life.shizune.mistinn.terra.Squall.SKY, life.shizune.mistinn.terra.Squall.Stage.RAGING.ordinal());
				check(tiller.line(player(server)).equals("message.mistinn.tiller.squall"), "and in a squall, calls the hunter in");
				player(server).removeAttached(life.shizune.mistinn.terra.Squall.SKY);
				tiller.discard();
			});
			check(context.computeOnClient(mc -> {
				for (String who : new String[] {"tiller", "warden"}) {
					for (int i = 0; i < Tiller.LINES; i++) {
						if (!net.minecraft.locale.Language.getInstance().has("message.mistinn.tiller." + who + "." + i)) {
							return false;
						}
					}
				}
				return true;
			}), "every line they may say is written");
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
