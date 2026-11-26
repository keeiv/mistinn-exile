package life.shizune.mistinn.test;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.naval.CannonShot;
import life.shizune.mistinn.naval.FlintlockItem;
import life.shizune.mistinn.naval.GunShot;
import life.shizune.mistinn.naval.NavalService;
import life.shizune.mistinn.naval.Pirate;
import life.shizune.mistinn.naval.PirateRole;
import life.shizune.mistinn.naval.ShipFrame;
import life.shizune.mistinn.naval.ShipPlan;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pirates at sea: the ship's plan stands as drawn and turns true, a hunter out of sight of land is
 * found by a ship that heaves to broadside on, crewed by its roles; its guns fire while a gunner
 * lives and fall silent when none does; a shot is decided on its line; the flintlock spends a
 * cartridge; the captain's fall takes the ship, sends the crew over the side and hardens the next.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnNavalTest implements FabricClientGameTest {
	private static BlockPos sea;
	private static int shipId;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setSeed("20261126")).create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule spawn_mobs false");
			context.waitTicks(10);
			world.getServer().runOnServer(MistinnNavalTest::thePlanStandsAsDrawn);
			world.getServer().runOnServer(MistinnNavalTest::framesTurnTrue);
			world.getServer().runOnServer(MistinnNavalTest::theFlintlockSpendsACartridge);
			world.getServer().runOnServer(MistinnNavalTest::findTheSea);
			context.waitTicks(100);
			world.getConnection().waitForChunksRender();
			world.getServer().runOnServer(MistinnNavalTest::intoABoat);
			context.waitTicks(40);
			aShipComesForAHunterAtSea(context, world);
			theGunsFireWhileAGunnerLives(context, world);
			world.getServer().runOnServer(MistinnNavalTest::aShotIsDecidedOnItsLine);
			theCaptainsFallTakesTheShip(context, world);
		} finally {
			NavalService.certain = false;
			NavalService.seaTime = NavalService.SEA_TIME;
		}
	}

	private static void thePlanStandsAsDrawn(final MinecraftServer server) {
		Map<BlockPos, BlockState> plan = ShipPlan.blocks();
		check(plan.size() > 600, "a ship of some size: " + plan.size() + " blocks");
		check(ShipPlan.guns().size() == 6, "six guns");
		for (ShipPlan.Gun gun : ShipPlan.guns()) {
			check(plan.get(gun.muzzle()).is(Blocks.DISPENSER), "a muzzle in the bulwark at " + gun.muzzle());
			standsOn(plan, gun.crew(), "a gunner's place");
		}
		standsOn(plan, ShipPlan.CAPTAIN, "the captain's place");
		ShipPlan.BOARDER_SPOTS.forEach(spot -> standsOn(plan, spot, "a boarder's place"));
		ShipPlan.MARKSMAN_SPOTS.forEach(spot -> standsOn(plan, spot, "a marksman's place"));
		check(plan.get(ShipPlan.CHEST).is(Blocks.CHEST), "the captain's chest");
		for (BlockPos boarding : ShipPlan.boardings()) {
			standsOn(plan, boarding, "the top of a ladder");
			BlockPos ladder = new BlockPos(boarding.getX(), ShipPlan.DECK, boarding.getZ() + Integer.signum(boarding.getZ()) * 2);
			check(plan.get(ladder) != null && plan.get(ladder).is(Blocks.LADDER), "a ladder comes up the hull below " + boarding + ": " + plan.get(ladder));
			check(plan.get(ladder.below(2)) != null && plan.get(ladder.below(2)).is(Blocks.LADDER), "and reaches down into the sea");
		}
		long visible = ShipPlan.visible().size();
		check(visible < plan.size(), "what is closed in is not drawn: " + visible + " of " + plan.size());
	}

	private static void standsOn(final Map<BlockPos, BlockState> plan, final BlockPos spot, final String what) {
		BlockState floor = plan.get(spot.below());
		BlockState at = plan.get(spot);
		BlockState head = plan.get(spot.above());
		check(floor != null && floor.isSolidRender(), what + " has a floor at " + spot + ": " + floor);
		check((at == null || at.isAir()) && (head == null || head.isAir()), what + " has room to stand at " + spot + ": " + at + " / " + head);
	}

	private static void framesTurnTrue(final MinecraftServer server) {
		for (Rotation turn : Rotation.values()) {
			ShipFrame frame = new ShipFrame(new BlockPos(100, 62, -40), turn);
			for (BlockPos local : List.of(new BlockPos(0, 0, 0), new BlockPos(5, 2, -3), new BlockPos(-12, 5, 3), ShipPlan.CHEST)) {
				Vec3 centre = frame.centre(local);
				check(BlockPos.containing(centre.add(0.0, 0.01, 0.0)).equals(frame.world(local)), turn + ": the middle of " + local + " is in its block");
				Vec3 back = frame.local(centre);
				check(Math.abs(back.x - (local.getX() + 0.5)) < 1.0E-6 && Math.abs(back.z - (local.getZ() + 0.5)) < 1.0E-6, turn + ": back to " + local + ": " + back);
			}
			check(frame.aboard(frame.centre(new BlockPos(0, ShipPlan.DECK + 1, 0))), turn + ": the middle of the deck is aboard");
			check(!frame.aboard(frame.centre(new BlockPos(0, ShipPlan.DECK + 1, ShipPlan.BEAM + 2))), turn + ": off the side is not");
			check(!frame.aboard(frame.centre(new BlockPos(0, 0, 0))), turn + ": in the sea is not");
		}
	}

	/** On land, before going to sea: a flintlock fires only with a cartridge, spends it, and hurts what it is pointed at. */
	private static void theFlintlockSpendsACartridge(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		player.getInventory().clearContent();
		ItemStack gun = new ItemStack(MistinnItems.FLINTLOCK);
		player.setItemInHand(InteractionHand.MAIN_HAND, gun);
		Zombie target = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		// high in the open air, where nothing of the ground stands in the way
		Vec3 at = new Vec3(player.getX(), 200.0, player.getZ());
		player.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), 0.0F, 0.0F, true);
		target.snapTo(at.x, at.y, at.z + 6.0, 180.0F, 0.0F);
		target.setNoAi(true);
		level.addFreshEntity(target);
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
		player.startUsingItem(InteractionHand.MAIN_HAND);
		check(!gun.getItem().releaseUsing(gun, level, player, 72000 - 30), "with no cartridge it does not fire");
		player.getInventory().setItem(3, new ItemStack(MistinnItems.PAPER_CARTRIDGE, 2));
		float before = target.getHealth();
		player.startUsingItem(InteractionHand.MAIN_HAND);
		check(gun.getItem().releaseUsing(gun, level, player, 72000 - 30), "with one it fires");
		check(player.getInventory().getItem(3).getCount() == 1, "and spends one");
		check(target.getHealth() < before, "the zombie in its sights is hit: " + target.getHealth());
		check(player.getCooldowns().isOnCooldown(gun), "then it must be reloaded");
		target.discard();
		player.getInventory().clearContent();
		check(FlintlockItem.class.isInstance(MistinnItems.FLINTLOCK_PISTOL), "the pistol is a flintlock too");
	}

	private static void findTheSea(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(biome -> biome.is(BiomeTags.IS_DEEP_OCEAN), spawn, 6400, 32, 64);
		check(found != null, "a deep ocean within reach of spawn");
		sea = new BlockPos(found.getFirst().getX(), level.getSeaLevel(), found.getFirst().getZ());
		ServerPlayer player = player(server);
		player.teleportTo(level, sea.getX() + 0.5, sea.getY() + 1.0, sea.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
	}

	private static void intoABoat(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		AbstractBoat boat = (AbstractBoat) net.minecraft.world.entity.EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.COMMAND);
		boat.snapTo(sea.getX() + 0.5, level.getSeaLevel() - 0.3, sea.getZ() + 0.5, 0.0F, 0.0F);
		level.addFreshEntity(boat);
		check(player.startRiding(boat), "the hunter gets into the boat");
		// the guns hurt: this hunter is here to watch, not to die
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100000, 0, false, false));
	}

	/** Out of sight of land, long enough: a ship heaves to broadside on, crewed by its roles. */
	private static void aShipComesForAHunterAtSea(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(NavalService.atSea(server.overworld(), player), "a hunter in a boat on the deep ocean is at sea");
			NavalService.reset(server);
			player.removeAttached(NavalService.NEXT);
			NavalService.seaTime = 40;
			NavalService.certain = true;
		});
		context.waitTicks(80);
		world.getServer().runOnServer(server -> {
			NavalService.certain = false;
			ServerLevel level = server.overworld();
			List<NavalService.Ship> ships = NavalService.sea(server).ships();
			check(ships.size() == 1, "one ship came: " + ships.size());
			NavalService.Ship ship = ships.getFirst();
			shipId = ship.id();
			ShipFrame frame = ship.frame();
			ServerPlayer player = player(server);
			double distance = frame.middle().distanceTo(player.position());
			check(distance > 25.0 && distance < 60.0, "a stone's throw off: " + distance);
			check(level.getBlockEntity(frame.world(ShipPlan.CHEST)) instanceof ChestBlockEntity, "the captain's chest is in the cabin");
			check(level.getBlockState(frame.world(ShipPlan.guns().getFirst().muzzle())).is(Blocks.DISPENSER), "the guns are in place");
			// broadside on: the hunter is off one side, not ahead or astern
			Vec3 local = frame.local(player.position());
			check(Math.abs(local.z) > Math.abs(local.x) * 0.5, "the ship lies broadside on to the hunter: " + local);
			List<Pirate> crew = crew(level, frame);
			check(crew.stream().filter(p -> p.role() == PirateRole.CAPTAIN).count() == 1, "one captain");
			check(crew.stream().filter(p -> p.role() == PirateRole.GUNNER).count() == 2, "two gunners");
			check(crew.stream().filter(p -> p.role() == PirateRole.MARKSMAN).count() == 2, "two marksmen");
			check(crew.stream().filter(p -> p.role() == PirateRole.BOARDER).count() == 2, "two boarders");
			check(player.getAttachedOrElse(NavalService.NEXT, 0L) > level.getGameTime(), "and the next will not come soon");
		});
		float yaw = world.getServer().computeOnServer(server -> {
			Vec3 middle = NavalService.ship(server, shipId).frame().middle();
			ServerPlayer player = player(server);
			return (float) Math.toDegrees(Math.atan2(middle.z - player.getZ(), middle.x - player.getX())) - 90.0F;
		});
		context.runOnClient(mc -> {
			mc.player.setYRot(yaw);
			mc.player.setXRot(-6.0F);
		});
		context.waitTicks(60);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot("mistinn-pirate-ship");
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			NavalService.Ship ship = NavalService.ship(server, shipId);
			for (Pirate pirate : crew(level, ship.frame())) {
				check(ship.frame().aboard(pirate.position()), "the crew keep to the ship: " + pirate.role() + " at " + pirate.position());
			}
		});
	}

	/** The guns fire at a hunter off their side while a gunner lives, and fall silent when none does. */
	private static void theGunsFireWhileAGunnerLives(final ClientGameTestContext context, final TestSingleplayerContext world) {
		boolean fired = false;
		for (int i = 0; i < 60 && !fired; i++) {
			context.waitTicks(5);
			fired = world.getServer().computeOnServer(server -> !server.overworld().getEntitiesOfClass(CannonShot.class,
				NavalService.ship(server, shipId).frame().box().inflate(64.0)).isEmpty());
		}
		check(fired, "the guns fired at the hunter in the boat");
		context.takeScreenshot("mistinn-pirate-broadside");
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			for (Pirate pirate : crew(level, NavalService.ship(server, shipId).frame())) {
				if (pirate.role() == PirateRole.GUNNER) {
					pirate.kill(level);
				}
			}
		});
		context.waitTicks(60);
		boolean again = false;
		for (int i = 0; i < 40 && !again; i++) {
			context.waitTicks(5);
			again = world.getServer().computeOnServer(server -> !server.overworld().getEntitiesOfClass(CannonShot.class,
				NavalService.ship(server, shipId).frame().box().inflate(64.0)).isEmpty());
		}
		check(!again, "with every gunner dead the guns are silent");
	}

	/** A shot goes along its line to the first body on it, through a pirate in the way: pirates do not shoot one another. */
	private static void aShotIsDecidedOnItsLine(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		Vec3 high = NavalService.ship(server, shipId).frame().middle().add(0.0, 30.0, 0.0);
		Pirate shooter = (Pirate) life.shizune.mistinn.registry.MistinnEntities.PIRATE.create(level, EntitySpawnReason.COMMAND);
		Pirate between = (Pirate) life.shizune.mistinn.registry.MistinnEntities.PIRATE.create(level, EntitySpawnReason.COMMAND);
		Zombie target = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		shooter.snapTo(high.x, high.y, high.z, -90.0F, 0.0F);
		between.snapTo(high.x + 3.0, high.y, high.z, 0.0F, 0.0F);
		target.snapTo(high.x + 6.0, high.y, high.z, 0.0F, 0.0F);
		for (var mob : List.of(shooter, between, target)) {
			mob.setNoAi(true);
			level.addFreshEntity(mob);
		}
		float before = target.getHealth();
		var hit = GunShot.fire(level, shooter, shooter.getEyePosition(), target.getBoundingBox().getCenter(), 7.0F, 0.0, 40.0);
		check(hit == target && target.getHealth() <= before - 6.0F, "the shot passes the pirate in the way and strikes the body on its line: " + hit
			+ ", " + target.getHealth());
		check(between.getHealth() == between.getMaxHealth(), "the pirate in the way is not hit");
		List.of(shooter, between, target).forEach(net.minecraft.world.entity.Entity::discard);
	}

	/** The captain falls: the ship is taken, the crew go over the side, and the next crews are harder. */
	private static void theCaptainsFallTakesTheShip(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			NavalService.Ship ship = NavalService.ship(server, shipId);
			Pirate captain = crew(level, ship.frame()).stream().filter(p -> p.role() == PirateRole.CAPTAIN).findFirst().orElseThrow();
			check(captain.getMaxHealth() >= 140.0F, "the captain is hard to kill: " + captain.getMaxHealth());
			captain.kill(level);
			check(NavalService.ship(server, shipId).state() == NavalService.TAKEN, "the ship is taken");
			check(NavalService.sea(server).taken() == 1, "and the fog sea remembers");
			check(life.shizune.mistinn.achievement.AchievementService.has(player(server), life.shizune.mistinn.achievement.AchievementService.PIRATE_SHIP),
				"the hunter near earns the prize of the fog sea");
		});
		context.waitTicks(260);
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			NavalService.Ship ship = NavalService.ship(server, shipId);
			check(crew(level, ship.frame()).isEmpty(), "the crew have gone over the side");
			check(level.getBlockState(ship.frame().world(ShipPlan.CHEST)).is(Blocks.CHEST), "the ship stays, its chest with it");
			// the sea's record survives a save
			var saved = NavalService.Sea.CODEC.encodeStart(JsonOps.INSTANCE, NavalService.sea(server)).getOrThrow();
			NavalService.Sea back = NavalService.Sea.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
			check(back.equals(NavalService.sea(server)), "the fog sea's ships are saved as they are");
		});
	}

	private static List<Pirate> crew(final ServerLevel level, final ShipFrame frame) {
		AABB box = frame.box().inflate(24.0);
		return level.getEntitiesOfClass(Pirate.class, box, p -> p.isAlive() && p.ship() == shipId);
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
