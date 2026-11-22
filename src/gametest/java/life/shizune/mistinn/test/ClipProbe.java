package life.shizune.mistinn.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import life.shizune.mistinn.client.enemy.EnemyMotion;
import life.shizune.mistinn.client.enemy.ExecutionView;
import life.shizune.mistinn.enemy.ExecutionMove;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Measures how deep two posed models run into each other. Both models are posed by the game's own code
 * (renderer transforms, vanilla animation, this mod's finisher poses) and every cube becomes an oriented
 * box in world space; the separating axis test then gives the depth of each overlap. Finisher animations
 * are checked with this so that bodies meet without passing through each other.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class ClipProbe {
	private static final Method SETUP_ROTATIONS = method("setupRotations", LivingEntityRenderState.class, PoseStack.class, float.class, float.class);
	private static final Method SCALE = method("scale", LivingEntityRenderState.class, PoseStack.class);
	private static final Field CHILDREN = field("children");
	private static final Field CUBES = field("cubes");

	private ClipProbe() {
	}

	/** One model cube in world space. */
	record Box(String path, Vector3f center, Vector3f[] axes, float[] half) {
		static Box of(final String path, final Matrix4f pose, final ModelPart.Cube cube) {
			Vector3f center = pose.transformPosition(new Vector3f((cube.minX + cube.maxX) / 32.0F, (cube.minY + cube.maxY) / 32.0F, (cube.minZ + cube.maxZ) / 32.0F));
			Vector3f[] axes = {pose.transformDirection(new Vector3f(1, 0, 0)), pose.transformDirection(new Vector3f(0, 1, 0)), pose.transformDirection(new Vector3f(0, 0, 1))};
			float[] half = {(cube.maxX - cube.minX) / 32.0F, (cube.maxY - cube.minY) / 32.0F, (cube.maxZ - cube.minZ) / 32.0F};
			for (int i = 0; i < 3; i++) {
				half[i] *= axes[i].length();
				axes[i].normalize();
			}
			return new Box(path, center, axes, half);
		}

		private float radius(final Vector3f axis) {
			return this.half[0] * Math.abs(this.axes[0].dot(axis)) + this.half[1] * Math.abs(this.axes[1].dot(axis)) + this.half[2] * Math.abs(this.axes[2].dot(axis));
		}

		JsonObject json() {
			JsonObject out = new JsonObject();
			out.addProperty("path", this.path);
			out.add("center", floats(this.center.x, this.center.y, this.center.z));
			JsonArray axes = new JsonArray();
			for (Vector3f axis : this.axes) {
				axes.add(floats(axis.x, axis.y, axis.z));
			}
			out.add("axes", axes);
			out.add("half", floats(this.half));
			return out;
		}
	}

	/** How far two boxes would have to move apart to stop overlapping; 0 when they do not touch. */
	static float depth(final Box a, final Box b) {
		Vector3f between = new Vector3f(b.center).sub(a.center);
		float least = Float.MAX_VALUE;
		List<Vector3f> axes = new ArrayList<>(15);
		for (int i = 0; i < 3; i++) {
			axes.add(a.axes[i]);
			axes.add(b.axes[i]);
		}
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				Vector3f cross = new Vector3f(a.axes[i]).cross(b.axes[j]);
				if (cross.lengthSquared() > 1.0E-6F) {
					axes.add(cross.normalize());
				}
			}
		}
		for (Vector3f axis : axes) {
			float overlap = a.radius(axis) + b.radius(axis) - Math.abs(between.dot(axis));
			if (overlap <= 0.0F) {
				return 0.0F;
			}
			least = Math.min(least, overlap);
		}
		return least;
	}

	/** Parts drawn as a second skin over a limb; they sit a hair outside it and are not bodies of their own. */
	static boolean overlay(final String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		return name.equals("hat") || name.equals("hat_rim") || name.equals("jacket") || name.endsWith("_sleeve") || name.endsWith("_pants")
			|| name.equals("cloak") || name.equals("ear");
	}

	/**
	 * The entity's model as the game would draw it at {@code pos}, facing {@code yaw}: every cube as a world box.
	 * {@code prepare} edits the extracted render state before posing; {@code base}, when given, receives the
	 * transform in front of the model root; {@code parts}, when given, receives the posed part tree.
	 */
	static List<Box> boxes(final Minecraft mc, final LivingEntity entity, final Vec3 pos, final float yaw, final Consumer<LivingEntityRenderState> prepare,
		final boolean enemyMotion, final float[] base, final JsonObject parts) {
		try {
			LivingEntityRenderer renderer = (LivingEntityRenderer) mc.getEntityRenderDispatcher().getRenderer(entity);
			LivingEntityRenderState state = (LivingEntityRenderState) renderer.createRenderState();
			renderer.extractRenderState(entity, state, 0.0F);
			state.bodyRot = yaw;
			state.yRot = 0.0F;
			state.xRot = 0.0F;
			state.walkAnimationSpeed = 0.0F;
			prepare.accept(state);
			PoseStack stack = new PoseStack();
			stack.translate((float) pos.x, (float) pos.y, (float) pos.z);
			stack.scale(state.scale, state.scale, state.scale);
			SETUP_ROTATIONS.invoke(renderer, state, stack, state.bodyRot, state.scale);
			stack.scale(-1.0F, -1.0F, 1.0F);
			SCALE.invoke(renderer, state, stack);
			stack.translate(0.0F, -1.501F, 0.0F);
			if (base != null) {
				stack.last().pose().get(base);
			}
			EntityModel model = renderer.getModel();
			if (state.isBaby && renderer instanceof net.minecraft.client.renderer.entity.AgeableMobRenderer<?, ?, ?>) {
				// the renderer swaps in its young model only while it draws
				java.lang.reflect.Field young = net.minecraft.client.renderer.entity.AgeableMobRenderer.class.getDeclaredField("babyModel");
				young.setAccessible(true);
				model = (EntityModel) young.get(renderer);
			}
			model.setupAnim(state);
			if (enemyMotion) {
				EnemyMotion.apply(model, state);
			}
			if (parts != null) {
				describe(model.root(), "root", parts);
			}
			List<Box> out = new ArrayList<>();
			collect(model.root(), "", stack, out);
			return out;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** As the game draws a model: a hidden part hides everything under it, and overlays are not bodies of their own. */
	private static void collect(final ModelPart part, final String path, final PoseStack stack, final List<Box> out) throws ReflectiveOperationException {
		if (!part.visible) {
			return;
		}
		stack.pushPose();
		part.translateAndRotate(stack);
		if (!part.skipDraw && !overlay(path)) {
			for (ModelPart.Cube cube : (List<ModelPart.Cube>) CUBES.get(part)) {
				out.add(Box.of(path.isEmpty() ? "/" : path, new Matrix4f(stack.last().pose()), cube));
			}
		}
		for (Map.Entry<String, ModelPart> child : ((Map<String, ModelPart>) CHILDREN.get(part)).entrySet()) {
			collect(child.getValue(), path + "/" + child.getKey(), stack, out);
		}
		stack.popPose();
	}

	/** The hunter's boxes at a tick of a finisher by this striker, standing at the origin and facing +z. */
	static List<Box> victim(final Minecraft mc, final LivingEntity striker, final ExecutionMove move, final float tick) {
		return boxes(mc, mc.player, Vec3.ZERO, 0.0F,
			state -> state.setData(ExecutionView.KEY, new ExecutionView.Pose(tick, 0.0F, -1, move, false, ExecutionView.victimClip(move, striker))),
			false, null, null);
	}

	/** The striker's boxes at a tick of its finisher, at the move's spacing in front of the hunter and facing them. */
	static List<Box> striker(final Minecraft mc, final LivingEntity striker, final ExecutionMove move, final float tick) {
		float spacing = striker instanceof net.minecraft.world.entity.Mob mob ? move.spacing(mob) : 1.0F;
		return boxes(mc, striker, new Vec3(0.0, 0.0, spacing), 180.0F, state -> {
			EnemyMotion.Sample sample = state.getData(EnemyMotion.KEY);
			state.setData(EnemyMotion.KEY, new EnemyMotion.Sample(sample.rig(), sample.seed(), 0.0F, 0.0F, 0.0F, true, true, false,
				tick, sample.id(), 1.0F, -1.0F, sample.twitchy(), move, sample.size(), EnemyMotion.finisherClip(move, striker.getType()), 0.0F, 0.0F));
		}, true, null, null);
	}

	/** The deepest overlap between any striker box and any hunter box, with the pair it is between. */
	record Worst(float depth, String striker, String victim) {
	}

	static Worst worst(final List<Box> striker, final List<Box> victim) {
		Worst worst = new Worst(0.0F, "", "");
		for (Box a : striker) {
			for (Box b : victim) {
				float depth = depth(a, b);
				if (depth > worst.depth) {
					worst = new Worst(depth, a.path, b.path);
				}
			}
		}
		return worst;
	}

	// ── Geometry dumps for offline authoring ─────────────────────

	private static void describe(final ModelPart part, final String name, final JsonObject out) throws ReflectiveOperationException {
		out.addProperty("name", name);
		out.add("pos", floats(part.x, part.y, part.z));
		out.add("rot", floats(part.xRot, part.yRot, part.zRot));
		out.add("scale", floats(part.xScale, part.yScale, part.zScale));
		out.addProperty("initialY", part.getInitialPose().y());
		out.addProperty("visible", part.visible);
		out.addProperty("skipDraw", part.skipDraw);
		JsonArray cubes = new JsonArray();
		for (ModelPart.Cube cube : (List<ModelPart.Cube>) CUBES.get(part)) {
			cubes.add(floats(cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ));
		}
		out.add("cubes", cubes);
		JsonArray children = new JsonArray();
		for (Map.Entry<String, ModelPart> child : ((Map<String, ModelPart>) CHILDREN.get(part)).entrySet()) {
			JsonObject described = new JsonObject();
			describe(child.getValue(), child.getKey(), described);
			children.add(described);
		}
		out.add("children", children);
	}

	static JsonArray floats(final float... values) {
		JsonArray out = new JsonArray();
		for (float value : values) {
			out.add(value);
		}
		return out;
	}

	private static Method method(final String name, final Class<?>... types) {
		try {
			Method method = LivingEntityRenderer.class.getDeclaredMethod(name, types);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Field field(final String name) {
		try {
			Field field = ModelPart.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
