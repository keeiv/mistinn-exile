package life.shizune.mistinn.test;

import java.nio.file.Path;
import life.shizune.mistinn.client.medical.TreatmentClient;
import life.shizune.mistinn.medical.Treatment;
import life.shizune.mistinn.medical.TreatmentService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * 處置膠卷: films every medical and repair use as a run of frames, on a mannequin from the
 * side and the front quarter, then in first person with the HUD showing its progress box.
 * Runs only with -Dmistinn.medreel=true; writes to reel-medical/.
 */
@SuppressWarnings("UnstableApiUsage")
public class MistinnMedicalReelTest implements FabricClientGameTest {
	private static final int FRAMES = 16;
	private static final int BEAT_FRAMES = 8;
	private static final Path OUT = Path.of("reel-medical");
	private static final String[][] USES = {
		{"armor_repair", "armor_repair_kit_4", "100"},
		{"helmet_repair", "helmet_repair_kit_6", "100"},
		{"inject", "potent_injector", "60"},
		{"surgery", "surgery_kit", "160"},
		{"pills", "painkillers", "40"},
	};

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (!Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		context.runOnClient(mc -> {
			mc.options.languageCode = "zh_tw";
			mc.getLanguageManager().setSelected("zh_tw");
		});
		boolean[] reloaded = {false};
		context.runOnClient(mc -> mc.reloadResourcePacks().thenRun(() -> reloaded[0] = true));
		context.waitFor(mc -> reloaded[0], 1200);
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
			world.getServer().runCommand("fill " + (x - 8) + " " + (y - 1) + " " + (z - 8) + " " + (x + 8) + " " + (y + 6) + " " + (z + 8) + " air");
			world.getServer().runCommand("fill " + (x - 8) + " " + (y - 1) + " " + (z - 8) + " " + (x + 8) + " " + (y - 1) + " " + (z + 8) + " smooth_stone");
			world.getServer().runCommand("summon minecraft:mannequin " + (x + 0.5) + " " + y + " " + (z + 0.5) + " {Rotation:[0.0f,0.0f],Tags:[\"reel\"]}");
			world.getServer().runCommand("item replace entity @e[tag=reel] armor.chest with mistinn:armor_4");
			world.getServer().runCommand("item replace entity @e[tag=reel] armor.head with mistinn:helmet_3");
			context.waitTicks(10);
			int mannequin = world.getServer().computeOnServer(server -> server.overworld().getEntities(net.minecraft.world.entity.EntityTypes.MANNEQUIN,
				e -> e.entityTags().contains("reel")).getFirst().getId());
			context.runOnClient(mc -> {
				mc.player.getAbilities().flying = true;
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			for (String[] use : USES) {
				Treatment treatment = java.util.Arrays.stream(Treatment.values()).filter(t -> t.id.equals(use[0])).findFirst().orElseThrow();
				Item item = BuiltInRegistries.ITEM.getValue(life.shizune.mistinn.Mistinn.id(use[1]));
				int ticks = Integer.parseInt(use[2]);
				world.getServer().runCommand("item replace entity @e[tag=reel] weapon.mainhand with mistinn:" + use[1]);
				world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
				context.waitTicks(20);
				this.view(context, world, x + 0.5 - 2.6, y - 0.62, z + 0.5, -90.0F, 0.0F);
				this.frames(context, mannequin, treatment, item, ticks, use[0] + "-side", true);
				this.view(context, world, x + 0.5 - 1.75, y - 0.45, z + 0.5 + 1.9, -137.0F, 6.0F);
				this.frames(context, mannequin, treatment, item, ticks, use[0] + "-front", true);
				world.getServer().runCommand("item replace entity @a weapon.mainhand with mistinn:" + use[1]);
				context.waitTicks(20);
				this.view(context, world, x + 0.5, y, z + 0.5 - 4.0, 0.0F, 0.0F);
				this.frames(context, -1, treatment, item, ticks, use[0] + "-1p", false);
				this.beat(context, treatment, item, ticks, use[0] + "-1pwork");
			}
		}
	}

	private void frames(final ClientGameTestContext context, final int entity, final Treatment treatment, final Item item, final int ticks, final String name, final boolean hideGui) {
		context.runOnClient(mc -> TreatmentClient.received(new TreatmentService.TreatmentPayload(entity < 0 ? mc.player.getId() : entity, treatment.ordinal(), ticks, Item.getId(item),
			TreatmentService.RESULT_RUNNING)));
		for (int i = 0; i < FRAMES; i++) {
			float p = (i + 0.5F) / FRAMES;
			context.runOnClient(mc -> TreatmentClient.preview = p);
			context.waitTicks(1);
			this.shot(context, name + "-" + (i < 10 ? "0" + i : Integer.toString(i)), hideGui);
		}
		context.runOnClient(mc -> {
			TreatmentClient.preview = -1.0F;
			TreatmentClient.received(new TreatmentService.TreatmentPayload(entity < 0 ? mc.player.getId() : entity, -1, 0, 0, TreatmentService.RESULT_DONE));
		});
		context.waitTicks(25);
	}

	/** One first-person work beat (for uses that have one), filmed through a single cycle a second in. */
	private void beat(final ClientGameTestContext context, final Treatment treatment, final Item item, final int ticks, final String name) {
		float loop = TreatmentClient.clip(treatment).number("first_person_loop", 0.0F);
		if (loop <= 0.0F) {
			return;
		}
		float duration = ticks / 20.0F;
		context.runOnClient(mc -> TreatmentClient.received(new TreatmentService.TreatmentPayload(mc.player.getId(), treatment.ordinal(), ticks, Item.getId(item), TreatmentService.RESULT_RUNNING)));
		for (int i = 0; i < BEAT_FRAMES; i++) {
			float p = (1.0F + loop * i / BEAT_FRAMES) / duration;
			context.runOnClient(mc -> TreatmentClient.preview = p);
			context.waitTicks(1);
			this.shot(context, name + "-0" + i, false);
		}
		context.runOnClient(mc -> {
			TreatmentClient.preview = -1.0F;
			TreatmentClient.received(new TreatmentService.TreatmentPayload(mc.player.getId(), -1, 0, 0, TreatmentService.RESULT_DONE));
		});
		context.waitTicks(25);
	}

	private void view(final ClientGameTestContext context, final TestSingleplayerContext world, final double x, final double y, final double z, final float yaw, final float pitch) {
		world.getServer().runCommand("tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch);
		context.waitTicks(4);
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
}
