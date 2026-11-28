package life.shizune.mistinn.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.gear.Detector;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The detector in the hunter's view. An empty right hand, used on nothing that answers to it (the open air, or a block
 * that does nothing when used), touches it: the server is asked. What it finds is outlined, each ore in its kind's colour,
 * through stone, until the time is up or the ore is dug. And the hand that touched it comes up to the head, seen from
 * inside and out.
 */
public final class DetectorClient implements HudElement {
	/** The hand's reach, as the renderer poses it: 0 down, 1 at the head. */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "mistinn:detector_reach");
	/** Ticks the hand takes up, holds there, and takes down. */
	private static final float UP = 5.0F;
	private static final float HOLD = 8.0F;
	private static final float DOWN = 6.0F;

	private record Shown(BlockPos pos, Detector.Ore ore) {
	}

	private static final List<Shown> SHOWN = new ArrayList<>();
	private static long until;
	private static final Map<Integer, Long> REACHES = new HashMap<>();
	private static long asked = Long.MIN_VALUE / 2;

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Detector.OresPayload.TYPE, (payload, context) -> found(payload));
		ClientPlayNetworking.registerGlobalReceiver(Detector.ReachPayload.TYPE,
			(payload, context) -> REACHES.put(payload.entity(), System.nanoTime()));
	}

	/** Right click with this hand: when it is empty and used on nothing that answers, the hunter touches their detector. */
	public static void used(final Minecraft mc) {
		if (mc.player == null || mc.level == null || !mc.player.getMainHandItem().isEmpty()) {
			return;
		}
		if (!Detector.is(life.shizune.mistinn.client.grid.GridPanel.inventory(mc.player).mask())) {
			return;
		}
		HitResult hit = mc.hitResult;
		if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
			return;
		}
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && answers(mc.level.getBlockState(block.getBlockPos()))) {
			return;
		}
		if (System.nanoTime() - asked < 1_000_000_000L) {
			return;
		}
		asked = System.nanoTime();
		ClientPlayNetworking.send(new Detector.TouchPayload());
	}

	/** Whether a block does something when used with an empty hand (a door, a chest, a lever...). */
	private static boolean answers(final BlockState state) {
		return state.getBlock() instanceof BaseEntityBlock || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
			|| state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof LeverBlock
			|| state.getBlock() instanceof BedBlock || state.getBlock() instanceof BellBlock || state.getBlock() instanceof CraftingTableBlock
			|| state.getBlock() instanceof NoteBlock || state.getBlock() instanceof DiodeBlock || state.getBlock() instanceof CakeBlock
			|| state.getMenuProvider(Minecraft.getInstance().level, BlockPos.ZERO) != null;
	}

	private static void found(final Detector.OresPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		SHOWN.clear();
		Detector.Ore[] ores = Detector.Ore.values();
		for (int i = 0; i < payload.positions().size(); i++) {
			int kind = payload.kinds().get(i);
			if (kind >= 0 && kind < ores.length) {
				SHOWN.add(new Shown(BlockPos.of(payload.positions().get(i)), ores[kind]));
			}
		}
		until = mc.level.getGameTime() + payload.ticks();
	}

	/** How many ores are outlined now (for tests). */
	public static int shown() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null || mc.level.getGameTime() > until ? 0 : SHOWN.size();
	}

	public static void forgetAll() {
		SHOWN.clear();
		REACHES.clear();
		until = 0L;
	}

	// ── The outlines ──────────────────────────────────────────────

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || SHOWN.isEmpty() || mc.gui.screen() != null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		long now = mc.level.getGameTime();
		if (now > until) {
			SHOWN.clear();
			return;
		}
		// the last half minute it fades
		float left = Math.min(1.0F, (until - now) / 600.0F);
		int alpha = Math.max(40, Math.round(230 * left));
		Vec3 eye = mc.gameRenderer.mainCamera().position();
		Vec3 look = mc.player.getViewVector(deltaTracker.getGameTimeDeltaPartialTick(true));
		SHOWN.removeIf(s -> Detector.oreOf(mc.level.getBlockState(s.pos())) != s.ore());
		for (Shown s : SHOWN) {
			float[][] corners = corners(mc, g, s.pos(), eye, look);
			if (corners == null) {
				continue;
			}
			int colour = alpha << 24 | s.ore().colour;
			// the block's twelve edges: each corner to the ones that differ from it along one axis
			for (int i = 0; i < 8; i++) {
				for (int axis = 1; axis <= 4; axis <<= 1) {
					if ((i & axis) == 0) {
						edge(g, corners[i], corners[i | axis], colour);
					}
				}
			}
		}
	}

	/** Where a block's eight corners fall on the screen, or null if any is behind the eye or the block is off screen. */
	private static float @Nullable [][] corners(final Minecraft mc, final GuiGraphicsExtractor g, final BlockPos pos, final Vec3 eye, final Vec3 look) {
		float[][] out = new float[8][];
		float x0 = Float.MAX_VALUE;
		float y0 = Float.MAX_VALUE;
		float x1 = -Float.MAX_VALUE;
		float y1 = -Float.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			Vec3 corner = new Vec3(pos.getX() + (i & 1), pos.getY() + (i >> 1 & 1), pos.getZ() + (i >> 2 & 1));
			if (corner.subtract(eye).dot(look) <= 0.1) {
				return null;
			}
			Vec3 screen = mc.gameRenderer.projectPointToScreen(corner);
			float x = (float) ((screen.x + 1.0) / 2.0 * g.guiWidth());
			float y = (float) ((1.0 - screen.y) / 2.0 * g.guiHeight());
			out[i] = new float[] {x, y};
			x0 = Math.min(x0, x);
			y0 = Math.min(y0, y);
			x1 = Math.max(x1, x);
			y1 = Math.max(y1, y);
		}
		if (x1 < 0 || y1 < 0 || x0 > g.guiWidth() || y0 > g.guiHeight() || x1 - x0 > g.guiWidth()) {
			return null;
		}
		return out;
	}

	/** One edge, a line a pixel wide from one corner to the other. */
	private static void edge(final GuiGraphicsExtractor g, final float[] from, final float[] to, final int argb) {
		float dx = to[0] - from[0];
		float dy = to[1] - from[1];
		int length = Math.round((float) Math.sqrt(dx * dx + dy * dy));
		if (length < 1) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(from[0], from[1]);
		g.pose().rotate((float) Math.atan2(dy, dx));
		g.fill(0, 0, length, 1, argb);
		g.pose().popMatrix();
	}

	// ── The hand to the head ──────────────────────────────────────

	/** How far up one's hand is, reaching to touch it: 0 down, 1 at the head; null when it is not. */
	public static @Nullable Float reach(final int entityId) {
		Long start = REACHES.get(entityId);
		if (start == null) {
			return null;
		}
		float ticks = (System.nanoTime() - start) / 1.0E9F * 20.0F;
		if (ticks >= UP + HOLD + DOWN) {
			REACHES.remove(entityId);
			return null;
		}
		float w = ticks < UP ? ticks / UP : ticks < UP + HOLD ? 1.0F : 1.0F - (ticks - UP - HOLD) / DOWN;
		w = Mth.clamp(w, 0.0F, 1.0F);
		return w * w * (3.0F - 2.0F * w);
	}

	/** Seen from outside: the right arm comes up, bent, the hand to the side of the head; the head tips a little to meet it. */
	public static void thirdPerson(final float w, final ModelPart arm, final ModelPart head) {
		arm.xRot = Mth.lerp(w, arm.xRot, -2.6F);
		arm.yRot = Mth.lerp(w, arm.yRot, -0.35F);
		arm.zRot = Mth.lerp(w, arm.zRot, 0.55F);
		head.zRot = Mth.lerp(w, head.zRot, -0.12F);
	}

	/** In one's own view: the empty right hand rises out of sight toward the temple, as if to touch something worn there. */
	public static void firstPerson(final float w, final PoseStack stack) {
		// up past the right edge of the view, the fingers last to leave it
		stack.translate(0.18F * w, 0.42F * w, 0.12F * w);
		stack.rotateDegrees(Axis.XP, 18.0F * w);
		stack.rotateDegrees(Axis.ZP, -22.0F * w);
	}
}
