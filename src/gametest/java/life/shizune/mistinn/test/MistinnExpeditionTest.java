package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.block.GravestoneBlockEntity;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.expedition.Expedition;
import life.shizune.mistinn.expedition.Grounds;
import life.shizune.mistinn.expedition.Layout;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.residue.DeathMark;
import life.shizune.mistinn.team.DownService;
import life.shizune.mistinn.terra.HomesteadTravel;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Terra's expeditions, on a small ground of the test's own: only from a woken homestead's hub, not on Peaceful, not to a
 * ground not ready; the party goes when the count is done and comes in at the start, counted in, with its own time. In
 * the ground no block is broken or placed and no explosion takes one, though a chest still opens. Holding the way out
 * for the count brings a hunter back to the hub (stepping off it stops the count, and the ground sends a wave at once).
 * A ground left empty unloads; the next party finds it swept and stocked again, nothing left lying from before. A hunter
 * whose time runs out there dies of it even with a teammate standing; their stone stays until the ground is swept, and
 * then their mark is gone too.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnExpeditionTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		Arena arena = new Arena();
		Expedition.musterTicks = 100;
		Expedition.exitTicks = 60;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 1000");
			context.waitTicks(20);
			BlockPos[] hub = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				server.setDifficulty(Difficulty.NORMAL, true);
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(180, 0, 0));
				Domain domain = TerraService.settleAt(level, centre, 240);
				Settlements.Settlement built = Settlements.buildNow(level, domain);
				check(Settlements.light(level, built.hub(), player), "a homestead is woken");
				hub[0] = built.hub();
				toHub(level, player, hub[0]);
				Expedition.layout(Grounds.DAM, null);
				check(Expedition.muster(player, Grounds.DAM) == Expedition.Result.NOT_READY, "a ground not laid out is not sent to");
				Expedition.layout(Grounds.DAM, arena);
				server.setDifficulty(Difficulty.PEACEFUL, true);
				check(Expedition.muster(player, Grounds.DAM) == Expedition.Result.PEACEFUL, "on Peaceful there is nothing to go for");
				server.setDifficulty(Difficulty.NORMAL, true);
				BlockPos aside = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hub[0].offset(30, 0, 0));
				player.teleportTo(level, aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				check(Expedition.muster(player, Grounds.DAM) == Expedition.Result.NOT_HOME, "away from the hub, no one sets out");
				toHub(level, player, hub[0]);
				check(Expedition.muster(player, Grounds.DAM) == Expedition.Result.MUSTERING, "by the hub, the count begins");
				check(Expedition.muster(player, Grounds.DAM) == Expedition.Result.BUSY, "and one party at a time sets out from a hub");
				check(Expedition.held().contains(Grounds.DAM), "the ground's chunks load while the party gathers");
			});
			context.waitTicks(Expedition.musterTicks + 30);

			// in the ground
			world.getServer().runOnServer(server -> {
				ServerLevel dam = server.getLevel(Grounds.DAM);
				ServerPlayer player = player(server);
				check(player.level() == dam, "the party goes when the count is done");
				check(player.blockPosition().equals(Arena.START), "and comes in at the start: " + player.blockPosition());
				Expedition.Away away = player.getAttached(Expedition.AWAY);
				check(away != null && away.ends() - dam.getGameTime() > Expedition.LIMIT - 200L, "with its own twenty minutes");
				Expedition.Ground ground = Expedition.ground(server, Grounds.DAM);
				check(ground.built() && ground.serial() == 1 && ground.inside().containsKey(player.getUUID()), "counted in: " + ground);
				check(arena.stocked == 1 && stands(dam) == 1, "the ground was stocked for it");
				check(HomesteadTravel.homeHere(server, player) == null, "the homestead is not here");
				check(DomainService.at(dam, hub[0].getX(), hub[0].getZ()) == null, "nor its highland");

				BlockPos floor = new BlockPos(2, 64, 0);
				check(!player.gameMode.destroyBlock(floor) && dam.getBlockState(floor).is(Blocks.STONE), "no block is broken");
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
				player.gameMode.useItemOn(player, dam, player.getMainHandItem(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0), Direction.UP, floor, false));
				check(dam.getBlockState(floor.above()).isAir(), "nor placed");
				check(!player.mayUseItemAt(floor.above(), Direction.UP, new ItemStack(Items.WATER_BUCKET)), "nor poured");
				player.gameMode.useItemOn(player, dam, player.getMainHandItem(), InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(Arena.CHEST), Direction.UP, Arena.CHEST, false));
				check(player.containerMenu != player.inventoryMenu, "but a chest still opens");
				player.closeContainer();
				dam.explode(null, -11.5, 65.0, 11.5, 4.0F, Level.ExplosionInteraction.TNT);
				boolean whole = true;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						whole &= dam.getBlockState(new BlockPos(-12 + dx, 64, 12 + dz)).is(Blocks.STONE);
					}
				}
				check(whole, "no explosion takes a block");
				if (dam.getBlockEntity(Arena.CHEST) instanceof ChestBlockEntity chest) {
					check(chest.getItem(0).is(Items.DIAMOND), "what is to be found is there");
					chest.setItem(0, ItemStack.EMPTY);
				}
				dam.addFreshEntity(new ItemEntity(dam, -4.5, 65.5, -4.5, new ItemStack(Items.EMERALD)));
			});
			context.waitTicks(10);
			check(context.computeOnClient(mc -> mc.level.dimension() == Grounds.DAM && mc.level.getAttached(DomainService.VIEW) == null),
				"the hunter's own world is the ground, with no highland drawn over it");
			check(context.computeOnClient(mc -> mc.player.getAttached(Expedition.AWAY) != null && !mc.player.getAbilities().mayBuild),
				"their client knows their time, and does not build there");

			// a way out: stepping off stops the count; holding it to the end brings the hunter home
			world.getServer().runOnServer(server -> toExit(server.getLevel(Grounds.DAM), player(server)));
			context.waitTicks(20);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.getAttached(Expedition.AWAY).leaving() != 0L, "standing in the way out begins the count");
				check(arena.waves >= 1, "and the ground sends a wave at once");
				player.teleportTo(server.getLevel(Grounds.DAM), Arena.START.getX() + 0.5, Arena.START.getY(), Arena.START.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			});
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.getAttached(Expedition.AWAY).leaving() == 0L, "stepping off stops it");
				toExit(server.getLevel(Grounds.DAM), player);
			});
			context.waitTicks(Expedition.exitTicks + 30);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.level() == server.overworld() && player.distanceToSqr(Vec3.atCenterOf(hub[0])) < 16.0, "held to the end, the hunter is back at the hub");
				check(!player.hasAttached(Expedition.AWAY) && player.getAbilities().mayBuild, "no longer away, and free to build again");
				check(Expedition.ground(server, Grounds.DAM).inside().isEmpty(), "the ground is empty");
			});

			// the empty ground unloads; the next party finds it swept and stocked
			boolean unloaded = false;
			for (int i = 0; i < 90 && !unloaded; i++) {
				context.waitTicks(20);
				unloaded = world.getServer().computeOnServer(server -> !server.getLevel(Grounds.DAM).getChunkSource().hasChunk(0, 0));
			}
			check(unloaded, "the empty ground unloads");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				toHub(server.overworld(), player, hub[0]);
				check(Expedition.muster(player, Grounds.DAM) == Expedition.Result.MUSTERING, "the next party sets out");
			});
			context.waitTicks(Expedition.musterTicks + 30);
			world.getServer().runOnServer(server -> {
				ServerLevel dam = server.getLevel(Grounds.DAM);
				ServerPlayer player = player(server);
				check(player.level() == dam && Expedition.ground(server, Grounds.DAM).serial() == 2, "into the ground, stocked a second time");
				check(dam.getEntitiesOfClass(ItemEntity.class, AABB.of(arena.bounds())).isEmpty(), "nothing left lying from before");
				check(stands(dam) == 1, "one keeper, not two");
				check(dam.getBlockEntity(Arena.CHEST) instanceof ChestBlockEntity chest && chest.getItem(0).is(Items.DIAMOND), "the chest filled again");
				// a teammate standing would have lifted a hunter who fell to a blow: not one whose time is up
				DownService.standing = p -> 1;
				Expedition.hasten(player, 10L);
			});
			context.waitTicks(30);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				DownService.standing = DownService.STANDING;
				check(player.isDeadOrDying() && !DownService.isDown(player), "time up, the hunter dies where they stand, not down");
				Expedition.Ground ground = Expedition.ground(server, Grounds.DAM);
				check(!ground.inside().containsKey(player.getUUID()) && ground.graved().contains(player.getUUID()), "out of the count, a stone left there");
			});
			context.waitTicks(100);
			world.getServer().runOnServer(server -> check(stones(server.getLevel(Grounds.DAM)) == 1, "the stone stands in the ground"));
			context.runOnClient(mc -> mc.player.respawn());
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.level() == server.overworld() && !player.hasAttached(Expedition.AWAY), "back in the world, no longer away");
				DeathMark mark = player.getAttached(MistinnAttachments.DEATH_MARK);
				check(mark != null && mark.pos().dimension() == Grounds.DAM, "the map marks the stone in the ground");
				toHub(server.overworld(), player, hub[0]);
				check(Expedition.muster(player, Grounds.DAM) == Expedition.Result.MUSTERING, "and they set out again");
			});
			context.waitTicks(Expedition.musterTicks + 30);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(Expedition.ground(server, Grounds.DAM).serial() == 3, "the ground swept a third time");
				check(stones(server.getLevel(Grounds.DAM)) == 0, "the stone is gone");
				check(!player.hasAttached(MistinnAttachments.DEATH_MARK), "and so is the mark of it");
				player.removeAttached(Expedition.AWAY);
			});
		} finally {
			Expedition.layout(Grounds.DAM, null);
			Expedition.musterTicks = 200;
			Expedition.exitTicks = 600;
			DownService.standing = DownService.STANDING;
		}
	}

	/** To the hub, held in the air there: in a flat test world the homestead's deck lies under the world's floor. */
	private static void toHub(final ServerLevel level, final ServerPlayer player, final BlockPos hub) {
		player.getAbilities().mayfly = true;
		player.getAbilities().flying = true;
		player.onUpdateAbilities();
		player.teleportTo(level, hub.getX() + 2.5, hub.getY() + 1.0, hub.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
	}

	private static void toExit(final ServerLevel dam, final ServerPlayer player) {
		player.teleportTo(dam, Arena.EXIT.getX() + 0.5, Arena.EXIT.getY(), Arena.EXIT.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
	}

	private static int stands(final ServerLevel dam) {
		return dam.getEntitiesOfClass(ArmorStand.class, AABB.of(new Arena().bounds())).size();
	}

	private static int stones(final ServerLevel dam) {
		int stones = 0;
		for (int cx = -2; cx <= 1; cx++) {
			for (int cz = -2; cz <= 1; cz++) {
				for (BlockEntity entity : dam.getChunk(cx, cz).getBlockEntities().values()) {
					if (entity instanceof GravestoneBlockEntity) {
						stones++;
					}
				}
			}
		}
		return stones;
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn expedition] ok: " + what);
	}

	/** The test's own small ground: a stone floor round the origin, a chest, one keeper (a stand), one way out. */
	private static final class Arena implements Layout {
		static final BlockPos START = new BlockPos(0, 65, 0);
		static final BlockPos CHEST = new BlockPos(4, 65, 4);
		static final BlockPos EXIT = new BlockPos(12, 65, -12);
		int stocked;
		int waves;

		@Override
		public BoundingBox bounds() {
			return new BoundingBox(-24, 60, -24, 24, 80, 24);
		}

		@Override
		public void build(final ServerLevel level) {
			for (int x = -16; x <= 16; x++) {
				for (int z = -16; z <= 16; z++) {
					level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 2);
				}
			}
			level.setBlock(CHEST, Blocks.CHEST.defaultBlockState(), 3);
		}

		@Override
		public List<BlockPos> starts() {
			return List.of(START);
		}

		@Override
		public List<Exit> exits() {
			return List.of(new Exit(EXIT, 2.5));
		}

		@Override
		public void stock(final ServerLevel level, final RandomSource random) {
			if (level.getBlockEntity(CHEST) instanceof ChestBlockEntity chest) {
				chest.clearContent();
				chest.setItem(0, new ItemStack(Items.DIAMOND));
			}
			ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
			if (stand != null) {
				stand.setPos(-8.5, 65.0, -8.5);
				level.addFreshEntity(stand);
			}
			this.stocked++;
		}

		@Override
		public void wave(final ServerLevel level, final Exit exit, final int wave, final RandomSource random) {
			this.waves++;
		}
	}
}
