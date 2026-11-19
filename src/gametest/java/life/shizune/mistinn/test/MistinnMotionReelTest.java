package life.shizune.mistinn.test;

import java.nio.file.Path;
import life.shizune.mistinn.client.anim.WeaponSwings;
import life.shizune.mistinn.client.move.RollClient;
import life.shizune.mistinn.stamina.RollService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 動作膠卷: films every body animation the mod draws as a dense run of frames, so each can be
 * judged as motion (strips and GIFs) rather than as four stills. Weapons and dodges are played
 * on a mannequin seen from the side and from the front quarter, and in first person; then the
 * keeper is filmed coming for the hunter, and the death scene is filmed through. Runs only with
 * -Dmistinn.reel=true; writes to reel/.
 */
@SuppressWarnings("UnstableApiUsage")
public class MistinnMotionReelTest implements FabricClientGameTest {
	private static final String[] WEAPONS = {"iron_blade", "iron_greatsword", "iron_spear", "iron_dagger", "iron_hammer"};
	/** Frames per swing or dodge, spread evenly over its progress. */
	private static final int FRAMES = 16;
	private static final Path OUT = Path.of("reel");

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (!Boolean.getBoolean("mistinn.reel")) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamemode creative @a");
			Vec3 at = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().position()).add(30.0, 0.0, 0.0);
			int x = (int) Math.floor(at.x);
			int y = (int) Math.floor(at.y);
			int z = (int) Math.floor(at.z);
			// a clean stage: a stone floor, open sky
			world.getServer().runCommand("fill " + (x - 8) + " " + (y - 1) + " " + (z - 8) + " " + (x + 8) + " " + (y + 6) + " " + (z + 8) + " air");
			world.getServer().runCommand("fill " + (x - 8) + " " + (y - 1) + " " + (z - 8) + " " + (x + 8) + " " + (y - 1) + " " + (z + 8) + " smooth_stone");
			world.getServer().runCommand("summon minecraft:mannequin " + (x + 0.5) + " " + y + " " + (z + 0.5) + " {Rotation:[0.0f,0.0f],Tags:[\"reel\"]}");
			context.waitTicks(10);
			int mannequin = world.getServer().computeOnServer(server -> server.overworld().getEntities(net.minecraft.world.entity.EntityTypes.MANNEQUIN, e -> e.entityTags().contains("reel")).getFirst().getId());
			context.runOnClient(mc -> {
				mc.player.getAbilities().flying = true;
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});

			for (String weapon : WEAPONS) {
				world.getServer().runCommand("item replace entity @e[tag=reel] weapon.mainhand with mistinn:" + weapon);
				// the camera's own hands stay empty, or its view would sway with the frozen swing
				world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
				context.waitTicks(25);
				// from the right side, level with the chest
				this.view(context, world, x + 0.5 - 2.6, y - 0.62, z + 0.5, -90.0F, 0.0F);
				this.swingFrames(context, mannequin, weapon + "-side", true);
				// from the front quarter, a little above
				this.view(context, world, x + 0.5 - 1.75, y - 0.45, z + 0.5 + 1.9, -137.0F, 6.0F);
				this.swingFrames(context, mannequin, weapon + "-front", true);
				// through the hunter's own eyes
				world.getServer().runCommand("item replace entity @a weapon.mainhand with mistinn:" + weapon);
				context.waitTicks(25);
				this.view(context, world, x + 0.5, y, z + 0.5 - 4.0, 0.0F, 0.0F);
				this.swingFrames(context, -1, weapon + "-1p", false);
			}

			// dodges, from the side, empty-handed
			world.getServer().runCommand("item replace entity @e[tag=reel] weapon.mainhand with minecraft:air");
			world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
			this.view(context, world, x + 0.5 - 3.6, y - 0.5, z + 0.5 + 1.2, -90.0F, 4.0F);
			String[] dodges = {"roll", "dive"};
			for (int kind = 0; kind < 2; kind++) {
				int k = kind;
				context.runOnClient(mc -> RollClient.received(new RollService.RollAnimPayload(mannequin, k, 0.0F, 1.0F, 1.0F)));
				for (int i = 0; i < FRAMES; i++) {
					float p = (i + 0.5F) / FRAMES;
					context.runOnClient(mc -> RollClient.preview = p);
					context.waitTicks(1);
					this.shot(context, dodges[kind] + "-" + pad(i), true);
				}
				context.runOnClient(mc -> RollClient.preview = -1.0F);
				context.waitTicks(30);
			}

			// the keeper's three moves, each from its tell, seen from the side by a hunter it ignores
			world.getServer().runCommand("kill @e[tag=reel]");
			world.getServer().runCommand("summon mistinn:snuffer " + (x + 0.5) + " " + y + " " + (z + 0.5) + " {Rotation:[0.0f,0.0f],Tags:[\"stage\"]}");
			this.view(context, world, x + 0.5 - 4.6, y - 0.1, z + 0.5 + 1.0, -90.0F, -4.0F);
			context.waitTicks(20);
			for (life.shizune.mistinn.keeper.Snuffer.Move move : life.shizune.mistinn.keeper.Snuffer.Move.values()) {
				world.getServer().runOnServer(server -> {
					var keeper = server.overworld().getEntities(life.shizune.mistinn.registry.MistinnEntities.SNUFFER, e -> e.entityTags().contains("stage")).getFirst();
					keeper.teleportTo(x + 0.5, y, z + 0.5);
					keeper.setYRot(0.0F);
					keeper.setYBodyRot(0.0F);
					keeper.setYHeadRot(0.0F);
					keeper.start(move);
				});
				String name = "keeper-" + move.name().toLowerCase(java.util.Locale.ROOT);
				for (int i = 0; i < 24; i++) {
					context.waitTicks(2);
					this.shot(context, name + "-" + pad(i), true);
				}
				context.waitTicks(30);
			}
			world.getServer().runCommand("kill @e[tag=stage]");

			// the keeper comes for the hunter, who is made too tough to fall
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("effect give @a minecraft:resistance infinite 4 true");
			world.getServer().runCommand("effect give @a minecraft:regeneration infinite 4 true");
			world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			world.getServer().runCommand("tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5 - 5.0) + " 0 -8");
			world.getServer().runCommand("summon mistinn:snuffer " + (x + 0.5) + " " + y + " " + (z + 0.5 + 5.0) + " {Rotation:[180.0f,0.0f],Tags:[\"keeper\"]}");
			for (int i = 0; i < 90; i++) {
				context.waitTicks(3);
				this.shot(context, "keeper-" + pad(i), true);
			}
			world.getServer().runCommand("kill @e[tag=keeper]");
			world.getServer().runCommand("effect clear @a");
			context.waitTicks(10);

			// the death scene: struck down by a creature
			world.getServer().runCommand("summon minecraft:zombie " + (x + 0.5) + " " + y + " " + (z + 4.5) + " {NoAI:1b,Tags:[\"killer\"],Silent:1b}");
			context.waitTicks(5);
			world.getServer().runCommand("damage @p 1000 minecraft:mob_attack by @e[tag=killer,limit=1]");
			for (int i = 0; i < 44; i++) {
				context.waitTicks(2);
				this.shot(context, "death-" + pad(i), false);
			}
		}
	}

	private void view(final ClientGameTestContext context, final TestSingleplayerContext world, final double x, final double y, final double z, final float yaw, final float pitch) {
		world.getServer().runCommand("tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch);
		context.waitTicks(4);
	}

	/** Frames of one swing; {@code entity} -1 swings the hunter's own weapon (first person). */
	private void swingFrames(final ClientGameTestContext context, final int entity, final String name, final boolean hideHand) {
		for (int i = 0; i < FRAMES; i++) {
			float p = (i + 0.5F) / FRAMES;
			context.runOnClient(mc -> {
				Entity target = entity < 0 ? mc.player : mc.level.getEntity(entity);
				if (target instanceof LivingEntity living) {
					living.swing(InteractionHand.MAIN_HAND, living.getMainHandItem().getAttackAnimation(), false);
				}
				WeaponSwings.preview = p;
			});
			context.waitTicks(1);
			this.shot(context, name + "-" + pad(i), hideHand);
			context.runOnClient(mc -> WeaponSwings.preview = -1.0F);
		}
		context.waitTicks(30);
	}

	private void shot(final ClientGameTestContext context, final String name, final boolean hideGui) {
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() != hideGui) {
				mc.gui.hud.toggle();
			}
		});
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(OUT));
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static String pad(final int i) {
		return i < 10 ? "0" + i : Integer.toString(i);
	}
}
