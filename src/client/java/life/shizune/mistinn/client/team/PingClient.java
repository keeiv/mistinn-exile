package life.shizune.mistinn.client.team;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import life.shizune.mistinn.team.PingService;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 標記: press the key and what is under the crosshair, however far, is marked for the whole team in
 * this hunter's colour: a place, a creature (the mark rides on it) or a thing on the ground. Each
 * mark shows where it is and how far, with who made it, for twelve seconds; one off screen is
 * pointed at from round the crosshair. The minimap shows them too.
 */
public final class PingClient implements HudElement {
	/** How long a mark stays, and how long it takes to fade. */
	public static final int LIFE = 240;
	private static final int FADE = 40;
	/** How many marks one hunter keeps up at once: the oldest goes. */
	private static final int EACH = 3;
	/** Where the pointers to marks off screen sit, round the crosshair. */
	private static final int RING = 64;

	/** A mark: who made it, in what colour, where, what it rides on (-1 for none), what kind, and when. */
	public record Ping(UUID from, String name, int colour, Vec3 at, int entity, int kind, long born) {
	}

	private static final List<Ping> PINGS = new ArrayList<>();
	private static @Nullable KeyMapping key;

	public static void register(final KeyMapping.Category category) {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mistinn.ping", InputConstants.KEY_G, category));
		ClientPlayNetworking.registerGlobalReceiver(PingService.ShowPayload.TYPE, (payload, context) -> received(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PINGS.clear());
	}

	/** The marks up now, oldest first. */
	public static List<Ping> pings() {
		return List.copyOf(PINGS);
	}

	public static void tick(final Minecraft mc) {
		if (key == null) {
			return;
		}
		while (key.consumeClick()) {
			if (mc.gui.screen() == null && mc.player != null && mc.level != null && mc.player.isAlive()) {
				mark(mc);
			}
		}
		long now = mc.level == null ? 0L : mc.level.getGameTime();
		PINGS.removeIf(ping -> now - ping.born() > LIFE);
	}

	/** Marks what is under the crosshair: the first creature or thing on the line, else the block it meets. */
	public static void mark(final Minecraft mc) {
		Vec3 eye = mc.gameRenderer.mainCamera().position();
		Vec3 look = mc.player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(PingService.REACH));
		BlockHitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(mc.player, eye, stop, new AABB(eye, stop).inflate(1.0),
			e -> e != mc.player && !e.isSpectator() && (e.isPickable() || e instanceof ItemEntity), eye.distanceToSqr(stop));
		if (entity != null) {
			ClientPlayNetworking.send(new PingService.PingPayload(entity.getLocation(), entity.getEntity().getId()));
		} else if (block.getType() != HitResult.Type.MISS) {
			ClientPlayNetworking.send(new PingService.PingPayload(block.getLocation(), -1));
		}
	}

	private static void received(final PingService.ShowPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		List<Ping> mine = PINGS.stream().filter(p -> p.from().equals(payload.from())).toList();
		if (mine.size() >= EACH) {
			PINGS.remove(mine.getFirst());
		}
		PINGS.add(new Ping(payload.from(), payload.name(), payload.colour(), payload.at(), payload.entity(), payload.kind(), mc.level.getGameTime()));
		boolean enemy = payload.kind() == PingService.ENEMY;
		mc.getSoundManager().play(SimpleSoundInstance.forUI(enemy ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_PLING.value(),
			enemy ? 0.9F : 1.6F, 0.7F));
	}

	/** Where a mark is now: over the creature it rides on while it is in sight, else where it was made. */
	public static Vec3 where(final Minecraft mc, final Ping ping, final float partial) {
		if (ping.entity() >= 0 && mc.level != null) {
			Entity entity = mc.level.getEntity(ping.entity());
			if (entity != null && entity.isAlive()) {
				return entity.getPosition(partial).add(0.0, entity.getBbHeight() + 0.5, 0.0);
			}
		}
		return ping.at();
	}

	/** How much of a mark still shows, 0 to 1: it fades over its last two seconds. */
	public static float shown(final Ping ping, final long now) {
		return Mth.clamp((LIFE - (now - ping.born())) / (float) FADE, 0.0F, 1.0F);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.getDebugOverlay().showDebugScreen() || PINGS.isEmpty()) {
			return;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		Vec3 eye = mc.gameRenderer.mainCamera().position();
		Vec3 look = mc.player.getViewVector(partial);
		Font font = mc.font;
		long now = mc.level.getGameTime();
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		for (Ping ping : PINGS) {
			float alpha = shown(ping, now);
			int a = Math.max(8, Math.round(alpha * 255.0F)) << 24;
			int colour = a | ping.colour() & 0xFFFFFF;
			Vec3 at = where(mc, ping, partial);
			Vec3 to = at.subtract(eye);
			double distance = to.length();
			Vec3 screen = mc.gameRenderer.projectPointToScreen(at);
			int x = (int) Math.round((screen.x + 1.0) / 2.0 * g.guiWidth());
			int y = (int) Math.round((1.0 - screen.y) / 2.0 * g.guiHeight());
			boolean ahead = to.dot(look) > 0.0;
			if (!ahead || x < 8 || x > g.guiWidth() - 8 || y < 8 || y > g.guiHeight() - 8) {
				// off screen: a pointer round the crosshair, the way to turn
				double yawTo = Math.atan2(to.z, to.x) - Math.atan2(look.z, look.x);
				double px = Math.sin(yawTo);
				double py = -Math.cos(yawTo);
				int ax = cx + (int) Math.round(px * RING);
				int ay = cy + (int) Math.round(py * RING);
				sign(g, ax, ay, ping.kind(), colour);
				continue;
			}
			sign(g, x, y, ping.kind(), colour);
			Component what = Component.translatable(switch (ping.kind()) {
				case PingService.ENEMY -> "hud.mistinn.ping.enemy";
				case PingService.ITEM -> "hud.mistinn.ping.item";
				default -> "hud.mistinn.ping.place";
			});
			Component line = Component.translatable("hud.mistinn.ping.line", what, String.format(Locale.ROOT, "%d", Math.round(distance)));
			// above the sign, clear of the creature it rides on
			g.text(font, line, x - font.width(line) / 2, y - 26, colour, true);
			g.text(font, ping.name(), x - font.width(ping.name()) / 2, y - 16, a | 0xB8B2A8, true);
		}
	}

	/** A mark's sign: a diamond for a place, a ring with a bar for a creature, a small square for a thing. */
	public static void sign(final GuiGraphicsExtractor g, final int x, final int y, final int kind, final int argb) {
		int rim = argb & 0xFF000000 | 0x14100A;
		if (kind == PingService.ENEMY) {
			g.fill(x - 5, y - 5, x + 6, y + 6, rim);
			g.fill(x - 4, y - 4, x + 5, y + 5, argb);
			g.fill(x - 2, y - 2, x + 3, y + 3, rim);
			g.fill(x, y - 4, x + 1, y + 1, rim);
			return;
		}
		if (kind == PingService.ITEM) {
			g.fill(x - 3, y - 3, x + 4, y + 4, rim);
			g.fill(x - 2, y - 2, x + 3, y + 3, argb);
			return;
		}
		MarkerClient.diamond(g, x, y, argb);
	}
}
