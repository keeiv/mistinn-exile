package life.shizune.mistinn.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.enemy.ExecutionMove;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.phys.Vec3;

/**
 * Finishers must not pass bodies through each other: every striker is posed against the hunter at every tick
 * of its move and the deepest overlap between their boxes is held under {@link #TOLERANCE}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnClipTest implements FabricClientGameTest {
	/** Bodies may press into each other this far, in blocks (about a pixel and a half): a grip, a bite, a foot on a chest. */
	static final float TOLERANCE = 0.1F;

	/** A creature to test: its vanilla id and, for cube mobs, its size; a negative size is a young one. */
	record Striker(String id, int size) {
		String label() {
			return this.size > 0 ? this.id + "#" + this.size : this.size < 0 ? this.id + "#baby" : this.id;
		}
	}

	private static final List<Striker> STRIKERS = List.of(
		new Striker("zombie", 0), new Striker("zombie_villager", 0), new Striker("husk", 0), new Striker("drowned", 0), new Striker("zombified_piglin", 0),
		new Striker("spider", 0), new Striker("cave_spider", 0), new Striker("wolf", 0), new Striker("silverfish", 0), new Striker("endermite", 0), new Striker("phantom", 0),
		new Striker("slime", 1), new Striker("slime", 2), new Striker("slime", 4), new Striker("magma_cube", 1), new Striker("magma_cube", 2), new Striker("magma_cube", 4),
		new Striker("sulfur_cube", 0),
		new Striker("warden", 0), new Striker("creaking", 0),
		new Striker("ravager", 0), new Striker("hoglin", 0), new Striker("zoglin", 0), new Striker("polar_bear", 0),
		new Striker("vindicator", 0), new Striker("piglin", 0), new Striker("piglin_brute", 0), new Striker("vex", 0),
		new Striker("wither_skeleton", 0), new Striker("skeleton", 0), new Striker("stray", 0), new Striker("bogged", 0), new Striker("parched", 0),
		new Striker("enderman", 0),
		new Striker("zombie", -1), new Striker("husk", -1), new Striker("drowned", -1), new Striker("zombie_villager", -1), new Striker("zombified_piglin", -1),
		new Striker("hoglin", -1), new Striker("zoglin", -1), new Striker("piglin", -1)
	);

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			List<String> failures = context.computeOnClient(MistinnClipTest::probe);
			if (!Boolean.getBoolean("mistinn.clipreport") && !failures.isEmpty()) {
				throw new AssertionError("finishers pass bodies through each other: " + failures);
			}
		}
	}

	private static Mob create(final Minecraft mc, final Striker striker) {
		Mob mob = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(striker.id)).create(mc.level, EntitySpawnReason.COMMAND);
		if (mob instanceof Slime slime && striker.size > 0) {
			slime.setSize(striker.size, true);
		}
		if (striker.size < 0) {
			mob.setBaby(true);
			mob.refreshDimensions();
		}
		// never added to the level, so it has no id of its own yet
		mob.setId(1_000_000 + STRIKERS.indexOf(striker));
		// as it stands when it has just struck
		mob.setAggressive(true);
		return mob;
	}

	/** Measures every striker's move; returns the ones over tolerance. Also writes the report and geometry for authoring. */
	private static List<String> probe(final Minecraft mc) {
		JsonObject report = new JsonObject();
		JsonArray geometry = new JsonArray();
		List<String> failures = new ArrayList<>();
		float[] base = new float[16];
		JsonObject hunterParts = new JsonObject();
		ClipProbe.boxes(mc, mc.player, Vec3.ZERO, 0.0F, state -> { }, false, base, hunterParts);
		JsonObject hunter = new JsonObject();
		hunter.addProperty("id", "player");
		hunter.add("base", ClipProbe.floats(base));
		hunter.add("parts", hunterParts);
		geometry.add(hunter);
		for (Striker striker : STRIKERS) {
			Mob mob = create(mc, striker);
			ExecutionMove move = ExecutionMove.of(mob);
			JsonObject parts = new JsonObject();
			ClipProbe.boxes(mc, mob, Vec3.ZERO, 0.0F, state -> { }, false, base, parts);
			JsonObject described = new JsonObject();
			described.addProperty("id", striker.label());
			described.addProperty("move", move == null ? "" : move.id());
			described.addProperty("spacing", move == null ? 0.0F : move.spacing(mob));
			described.addProperty("width", mob.getBbWidth());
			described.addProperty("height", mob.getBbHeight());
			described.addProperty("size", life.shizune.mistinn.client.enemy.EnemyMotion.drawnSize(mob));
			described.add("base", ClipProbe.floats(base));
			described.add("parts", parts);
			geometry.add(described);
			if (move == null || move.ranged()) {
				continue;
			}
			JsonArray ticks = new JsonArray();
			ClipProbe.Worst worst = new ClipProbe.Worst(0.0F, "", "");
			int at = 0;
			for (int tick = 0; tick <= move.length(); tick++) {
				List<ClipProbe.Box> a = ClipProbe.striker(mc, mob, move, tick);
				List<ClipProbe.Box> b = ClipProbe.victim(mc, mob, move, tick);
				ClipProbe.Worst now = ClipProbe.worst(a, b);
				JsonObject entry = new JsonObject();
				entry.addProperty("tick", tick);
				entry.addProperty("depth", now.depth());
				entry.addProperty("striker", now.striker());
				entry.addProperty("victim", now.victim());
				if (tick == move.kill() / 2) {
					JsonArray boxes = new JsonArray();
					a.forEach(box -> boxes.add(box.json()));
					entry.add("strikerBoxes", boxes);
					JsonArray hunterBoxes = new JsonArray();
					b.forEach(box -> hunterBoxes.add(box.json()));
					entry.add("victimBoxes", hunterBoxes);
				}
				ticks.add(entry);
				if (now.depth() > worst.depth()) {
					worst = now;
					at = tick;
				}
			}
			JsonObject result = new JsonObject();
			result.addProperty("move", move.id());
			result.addProperty("worst", worst.depth());
			result.addProperty("at", at);
			result.add("ticks", ticks);
			report.add(striker.label(), result);
			String line = String.format("%s %s: %.3f at tick %d (%s into %s)", move.id(), striker.label(), worst.depth(), at, worst.striker(), worst.victim());
			System.out.println("CLIP " + line);
			if (worst.depth() > TOLERANCE) {
				failures.add(line);
			}
		}
		try {
			Path out = mc.gameDirectory.toPath().resolve("clip-probe");
			Files.createDirectories(out);
			Files.writeString(out.resolve("report.json"), new GsonBuilder().create().toJson(report), StandardCharsets.UTF_8);
			Files.writeString(out.resolve("geometry.json"), new GsonBuilder().create().toJson(geometry), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return failures;
	}
}
