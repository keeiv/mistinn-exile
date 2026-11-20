package life.shizune.mistinn.test;

import java.nio.file.Path;
import java.util.Set;
import life.shizune.mistinn.bell.BellService;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.residue.ResidueService;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.Structures;
import life.shizune.mistinn.keeper.LampStandBlock;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Photo session for the website and README: ultra settings with shaders (Sodium, Iris and a shader
 * pack join only in photo mode), a real generated world, each structure built in view and framed
 * on purpose. Runs only with -Dmistinn.photo=true; writes 1920x1080 shots to photos/.
 */
@SuppressWarnings("UnstableApiUsage")
public class MistinnPhotoTest implements FabricClientGameTest {
	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (!Boolean.getBoolean("mistinn.photo")) {
			return;
		}
		boolean[] reloaded = {false};
		context.runOnClient(mc -> {
			mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
			mc.options.graphicsPreset().set(GraphicsPreset.FABULOUS);
			mc.options.renderDistance().set(16);
			mc.options.simulationDistance().set(12);
			mc.options.particles().set(ParticleStatus.ALL);
			mc.options.guiScale().set(4);
			mc.getWindow().setWindowed(WIDTH, HEIGHT);
			mc.options.languageCode = "zh_tw";
			mc.getLanguageManager().setSelected("zh_tw");
			mc.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
		});
		context.waitFor(mc -> reloaded[0], 6000);
		// the title screen, with the pointer resting on the first entry
		context.waitTicks(60);
		double[] first = context.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			int width = mc.getWindow().getGuiScaledWidth();
			int height = mc.getWindow().getGuiScaledHeight();
			return new double[] {(Math.max(24, width / 14) + 40) * scale, (height / 2.0 - 6 + 10) * scale};
		});
		context.getInput().setCursorPos(first[0], first[1]);
		context.waitTicks(20);
		context.takeScreenshot(TestScreenshotOptions.of("title").disableCounterPrefix().withDestinationDir(Path.of("photos")));

		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("mistinn-exile"))
			.create()) {
			// with Sodium and Iris the test API's chunk checks never report done, so the photo run
			// waits a fixed time for the world to load and the shaders to compile
			context.waitTicks(600);
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("weather clear");

			BlockPos spawn = world.getServer().computeOnServer(server -> server.overworld().getRespawnData().pos());

			// ── The spawn station, at golden hour ────────────────────
			world.getServer().runCommand("time set 12300");
			frame(context, world, spawn.offset(-10, 5, -11), spawn.offset(0, 1, 2), true);
			shot(context, "spawn-station", true);

			// ── A real drop station on open grassland ────────────────
			BlockPos station = build(world, find(world, spawn, Biomes.PLAINS, Biomes.MEADOW, Biomes.SUNFLOWER_PLAINS, Biomes.SAVANNA),
				b -> Structures.station(b, Structures.Station.INTACT));
			frame(context, world, station.offset(-10, 6, 12), station.offset(0, 2, 0), true);
			shot(context, "drop-station", true);

			// ── The hunter in full gear, standing on the pad ─────────
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MistinnItems.HELMETS.get(6)));
				player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(MistinnItems.ARMORS.get(6)));
				// leg gear stops at V
				player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(MistinnItems.LEGGINGS.get(5)));
				player.setItemSlot(EquipmentSlot.FEET, new ItemStack(MistinnItems.BOOTS.get(5)));
				// the bone face over the skull helm, the bone rig on the chest and its box on the back
				GridService.set(player, GridService.get(player).withMask(new ItemStack(MistinnItems.MASKS.get(6)))
					.withRig(new ItemStack(MistinnItems.RIGS.get(6)))
					.withBackpack(new ItemStack(MistinnItems.BACKPACKS.get(6))));
				player.getInventory().setItem(0, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SMASH).get(WeaponMaterial.NETHERITE)));
				player.getInventory().setSelectedSlot(0);
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
				player.teleportTo(server.overworld(), station.getX() + 0.5, station.getY() + 1, station.getZ() - 3.5, Set.of(), 180.0F, 8.0F, true);
			});
			context.waitTicks(40);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			shot(context, "hunter", true);
			// the hammer carried past the line of the blow, frozen at the overshoot
			context.runOnClient(mc -> {
				mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
				life.shizune.mistinn.client.anim.Clip clip = life.shizune.mistinn.client.anim.WeaponSwings.clip(WeaponStyle.SMASH);
				life.shizune.mistinn.client.anim.WeaponSwings.preview = clip.key(2) + (clip.key(3) - clip.key(2)) * 0.45F;
			});
			context.waitTicks(3);
			shot(context, "swing", true);
			context.runOnClient(mc -> life.shizune.mistinn.client.anim.WeaponSwings.preview = -1.0F);
			context.waitTicks(30);
			// halfway through a roll to the side
			context.runOnClient(mc -> {
				life.shizune.mistinn.client.move.RollClient.received(new life.shizune.mistinn.stamina.RollService.RollAnimPayload(mc.player.getId(), 0, mc.player.getYRot() - 90.0F, 1.0F, 1.0F));
				life.shizune.mistinn.client.move.RollClient.preview = 0.42F;
			});
			context.waitTicks(3);
			shot(context, "roll", true);
			context.runOnClient(mc -> life.shizune.mistinn.client.move.RollClient.preview = -1.0F);
			context.waitTicks(25);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// ── First person with the HUD, a diamond greatsword ──────
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.getInventory().setItem(0, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.CLEAVE).get(WeaponMaterial.DIAMOND)));
				player.getInventory().setItem(1, new ItemStack(MistinnItems.ANCHOR_BLADE));
				player.getInventory().setItem(2, new ItemStack(MistinnItems.HUNTER_GUIDE));
				player.getInventory().setItem(3, new ItemStack(Items.BREAD, 9));
				SaltService.set(player, 1480);
			});
			frame(context, world, station.offset(-3, 1, -8), station.offset(0, 2, 0), false);
			shot(context, "first-person", false);

			// ── Resting at a golden bell as night falls ──────────────
			world.getServer().runCommand("time set 12850");
			context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(5)); // empty hand: the scene, not the blade
			BlockPos bell = station.offset(2, 1, -2);
			world.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(bell, MistinnBlocks.GOLDEN_BELL.defaultBlockState()));
			frame(context, world, bell.offset(-2, 1, -3), bell, false);
			world.getServer().runOnServer(server -> BellService.rest(player(server), bell, false));
			context.waitTicks(8);
			shot(context, "golden-bell", false);

			// ── The residue: a gravestone in the grass, on the minimap ──
			world.getServer().runCommand("time set 12450");
			BlockPos grave = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos at = station.offset(1, 0, -12);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
				ServerPlayer player = player(server);
				player.teleportTo(level, at.getX() + 0.5, y, at.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				SaltService.set(player, 2860);
				ResidueService.onDeath(player);
				ResidueService.placeNow();
				return player.getAttached(life.shizune.mistinn.registry.MistinnAttachments.DEATH_MARK).pos().pos();
			});
			frame(context, world, grave.offset(-2, 1, -3), grave, false);
			context.waitTicks(40);
			shot(context, "gravestone", false);

			// ── The watchpost at the forest's edge, golden light ─────
			world.getServer().runCommand("time set 12000");
			BlockPos post = build(world, find(world, spawn, Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.TAIGA, Biomes.DARK_FOREST),
				Structures::watchpost);
			frame(context, world, post.offset(11, 4, 13), post.offset(0, 6, 0), true);
			shot(context, "watchpost", true);

			// ── The anchor wreck on a beach at sunrise ───────────────
			world.getServer().runCommand("time set 23400");
			BlockPos anchor = build(world, find(world, spawn, Biomes.BEACH), Structures::anchorWreck);
			frame(context, world, anchor.offset(6, 3, 10), anchor.offset(0, 3, 0), true);
			shot(context, "anchor-wreck", true);

			// ── The salt cairn in the badlands, midday ───────────────
			world.getServer().runCommand("time set 6000");
			BlockPos cairn = build(world, find(world, spawn, Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.ERODED_BADLANDS, Biomes.DESERT), Structures::saltCairn);
			frame(context, world, cairn.offset(6, 3, 8), cairn.offset(0, 2, 0), true);
			shot(context, "salt-cairn", true);

			// ── 霧岸燈塔 on the coast at dusk ────────────────────────
			// the keeper is a monster: peaceful would remove it
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("time set 12450");
			BlockPos base = build(world, find(world, spawn, Biomes.BEACH, Biomes.STONY_SHORE), Structures::lighthouse);
			BlockPos room = base.offset(0, Structures.LAMP_ROOM + 1, 0);
			frame(context, world, base.offset(-24, 12, 28), base.offset(0, 13, 0), true);
			shot(context, "lighthouse", true);
			// only now is the keeper's chunk loaded: hold it still at the far side of its room
			world.getServer().runOnServer(server -> {
				Snuffer keeper = keeper(server, room);
				keeper.setNoAi(true);
				keeper.snapTo(room.getX() + 0.5, room.getY(), room.getZ() + 4.5, 160.0F, 0.0F);
				keeper.setYHeadRot(160.0F);
				keeper.setYBodyRot(160.0F);
			});

			// ── The keeper in its lamp room ──────────────────────────
			frame(context, world, room.offset(4, 1, -1), room.offset(0, 1, 4), false);
			shot(context, "snuffer", true);

			// ── The dark half: lamps lit against the night ───────────
			world.getServer().runCommand("time set 15000");
			world.getServer().runOnServer(server -> {
				Snuffer keeper = keeper(server, room);
				keeper.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.SOUL_LANTERN));
				ServerLevel level = server.overworld();
				for (BlockPos lamp : keeper.lamps(level)) {
					level.setBlockAndUpdate(lamp, level.getBlockState(lamp).setValue(LampStandBlock.LIT, true));
				}
			});
			frame(context, world, room.offset(-5, 2, -3), room.offset(0, 1, 4), false);
			shot(context, "snuffer-dark", true);

			// ── Struck down on the drop station's pad: the death scene, then the end ──
			// (the lamp room is too tight for the scene's view)
			world.getServer().runCommand("time set 12300");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.getAbilities().flying = false;
				player.getAbilities().mayfly = false;
				player.onUpdateAbilities();
				// where the hunter photo stood: the view looks back at the hunter with the pod behind
				player.teleportTo(server.overworld(), station.getX() + 0.5, station.getY() + 1, station.getZ() - 3.5, Set.of(), 180.0F, 10.0F, true);
			});
			context.waitTicks(200);
			// struck down by a creature beside the hunter, close enough for the client to know it
			// (the scene hides it); the keeper's room is far off and unloaded by now
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				net.minecraft.world.entity.monster.zombie.Zombie zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(server.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				// beyond the pad's north edge, so the blows push the hunter onto the pad, not off it
				zombie.snapTo(player.getX(), player.getY(), player.getZ() - 1.5, 0.0F, 0.0F);
				zombie.setNoAi(true);
				server.overworld().addFreshEntity(zombie);
			});
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				for (int i = 0; i < 12 && player.isAlive(); i++) {
					player.damageCooldownTime = 0;
					var killer = server.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, player.getBoundingBox().inflate(6.0)).getFirst();
					player.hurtServer(server.overworld(), player.damageSources().mobAttack(killer), 60.0F);
				}
			});
			context.waitTicks(16);
			shot(context, "death", true);
			context.waitTicks(108);
			shot(context, "mission-end", true);
			double[] back = context.computeOnClient(mc -> {
				double[] gui = ((life.shizune.mistinn.client.death.MissionEndScreen) mc.gui.screen()).choiceCentre(0);
				double scale = mc.getWindow().getGuiScale();
				return new double[] {gui[0] * scale, gui[1] * scale};
			});
			context.getInput().setCursorPos(back[0], back[1]);
			context.waitTicks(2);
			context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
			world.getServer().runOnServer(server -> server.overworld().getEntities(net.minecraft.world.entity.EntityTypes.ZOMBIE, z -> true).forEach(net.minecraft.world.entity.Entity::discard));
			// long enough for the respawn message to fade before the next photo
			context.waitTicks(160);
			world.getServer().runCommand("difficulty peaceful");
			world.getServer().runCommand("time set 12450");

			// ── A story card: the prologue's opening picture ─────────
			context.setScreen(() -> new life.shizune.mistinn.client.story.StoryCardScreen(life.shizune.mistinn.story.StoryScript.DROP));
			context.getInput().setCursorPos(4.0, 4.0);
			context.waitTicks(70);
			shot(context, "story-card", false);
			context.setScreen(() -> null);
			context.waitTicks(10);

			// ── The hunter inventory over the world ──────────────────
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				GridService.set(player, GridInventory.EMPTY
					.withBackpack(new ItemStack(MistinnItems.BACKPACKS.get(5)))
					.withRig(new ItemStack(MistinnItems.RIGS.get(4)))
					.withMask(new ItemStack(MistinnItems.MASKS.get(4))));
				player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(MistinnItems.LEGGINGS.get(4)));
				player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(MistinnItems.BOOTS.get(4)));
				GridService.insert(player, new ItemStack(MistinnItems.POTENT_INJECTOR, 2));
				GridService.insert(player, new ItemStack(Items.BREAD, 6));
				// a hunter fresh from the lamp room: an arm hurt, a leg nearly gone
				BodyService.set(player, Body.FULL.with(BodyPart.LEFT_ARM, 41.0F).with(BodyPart.RIGHT_LEG, 9.0F).with(BodyPart.TORSO, 66.0F));
				GridService.insert(player, new ItemStack(Items.DIAMOND, 6));
				GridService.insert(player, new ItemStack(Items.GOLDEN_APPLE, 2));
				GridService.insert(player, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.THRUST).get(WeaponMaterial.IRON)));
				GridService.insert(player, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SMASH).get(WeaponMaterial.STONE)));
				GridService.insert(player, new ItemStack(Items.DIAMOND_ORE, 2));
				GridService.insert(player, new ItemStack(MistinnItems.SALT, 12));
				GridService.insert(player, new ItemStack(MistinnItems.HELMETS.get(4)));
				GridService.insert(player, new ItemStack(MistinnItems.SALT_FANG));
				GridService.set(player, GridService.get(player).with(Area.SAFE, java.util.List.of(new life.shizune.mistinn.grid.Placed(0, 0, 0, false, new ItemStack(MistinnItems.RUST_GOD_SALT)))));
			});
			context.setScreen(() -> new InventoryScreen(context.computeOnClient(mc -> mc.player)));
			// keep the mouse in a corner, clear of every slot, so no tooltip covers the screen
			context.getInput().setCursorPos(4.0, 4.0);
			context.waitTicks(10);
			shot(context, "grid-inventory", false);
			context.setScreen(() -> null);
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Snuffer keeper(final MinecraftServer server, final BlockPos room) {
		return server.overworld().getEntitiesOfClass(Snuffer.class, new net.minecraft.world.phys.AABB(room).inflate(12)).getFirst();
	}

	/** The nearest place of one of the biomes, at least a little way from spawn. */
	@SafeVarargs
	private static BlockPos find(final TestSingleplayerContext world, final BlockPos spawn, final ResourceKey<Biome>... biomes) {
		return world.getServer().computeOnServer(server -> {
			var found = server.overworld().findClosestBiome3d(h -> java.util.Arrays.stream(biomes).anyMatch(h::is), spawn.offset(64, 0, 64), 2400, 32, 64);
			return found == null ? spawn.offset(64, 0, 64) : found.getFirst();
		});
	}

	/** Builds a structure on the ground at (x, z) and returns its floor centre. */
	private static BlockPos build(final TestSingleplayerContext world, final BlockPos near, final java.util.function.Consumer<Builder> structure) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			level.getChunk(near); // make sure the ground is generated
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ()) - 1;
			BlockPos floor = new BlockPos(near.getX(), y, near.getZ());
			Builder builder = new Builder(level, floor, RandomSource.create(floor.asLong()));
			builder.clear(-6, 1, -6, 6, 12, 6);
			structure.accept(builder);
			return floor;
		});
	}

	/** Puts the player's eyes at {@code eye} looking at {@code target}, and waits for the view. */
	private static void frame(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos eye, final BlockPos target, final boolean newPlace) {
		double dx = target.getX() - eye.getX();
		double dy = target.getY() - eye.getY();
		double dz = target.getZ() - eye.getZ();
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getAbilities().flying = true;
			player.getAbilities().mayfly = true;
			player.onUpdateAbilities();
			player.teleportTo(server.overworld(), eye.getX() + 0.5, eye.getY() - 1.62 + 0.5, eye.getZ() + 0.5, Set.of(), yaw, pitch, true);
		});
		// the new view loads, Sodium builds it, the shaders' lighting settles
		context.waitTicks(newPlace ? 400 : 120);
	}

	private static void shot(final ClientGameTestContext context, final String name, final boolean hideHud) {
		if (hideHud) {
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		context.waitTicks(2);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(Path.of("photos")));
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}
}
