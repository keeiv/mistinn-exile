package life.shizune.mistinn.client.training;

import life.shizune.mistinn.training.StationArrival;
import life.shizune.mistinn.world.MistinnWorld;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the hunter sees and hears of the drop. At the station: the deck going dark over the three
 * bells, black as the clamps let go, then the sea rushing up out of the fog, the splash, and the
 * water closing over and going dark. Then, in the new world: wind in the dark, and the sky
 * opening above a hunter falling on their back, the clouds going away from them; they turn over,
 * and the station below comes up at them until the ground does. While it lasts the view is the
 * drop's ({@code SceneCameraMixin}) and the HUD is put away ({@link DropScreen}).
 */
public final class DropCinematic {
	/**
	 * At the station; the new world loading; wind in the dark until the ground under the hunter
	 * has arrived; the fall; the landing.
	 */
	private enum Phase { NONE, STATION, WAITING, GATHERING, FALLING, LANDED }

	/** Height of each black bar, as a share of the screen. */
	private static final float BAR = 0.1F;
	/** At the station: the deck nearly dark by the third bell. */
	private static final float DUSK = 0.9F;
	/** Over the new world: the dark while the ground arrives, at least and at most. */
	private static final int GATHER_LEAST = 15;
	private static final int GATHER_MOST = 200;
	/** Then black a little longer, the sky opens; the turn from the sky to the ground, by how far down. */
	private static final int BLIND = 10;
	private static final int OPEN = 30;
	private static final float TURN_FROM = 0.42F;
	private static final float TURN_LENGTH = 0.18F;
	/** The share of the drawing distance within which the ground is close enough to turn to. */
	private static final double TURN_SIGHT = 0.85;
	/** After the landing: black, then the hunter comes to. */
	private static final int OUT = 14;
	private static final int COME_TO = 30;

	private static Phase phase = Phase.NONE;
	private static int ticks;
	/** At the station: when the seat is let go, and when the sea closed over (-1 before then). */
	private static int release = 112;
	private static int splash = -1;
	private static float yawAtRelease;
	private static float pitchAtRelease;
	/** Over the new world: the pad below, where the fall began, how far it is, and whether the seat has been seen. */
	private static BlockPos stand = BlockPos.ZERO;
	private static double top;
	private static double height = 1.0;
	private static boolean seated;
	private static @Nullable Wind wind;
	/** How much of the world showed through in the last frame drawn, 0 (black) to 1. */
	private static float shown = 1.0F;

	private DropCinematic() {
	}

	/** The drop has begun at the station: the seat is let go {@code release} ticks from now. */
	public static void station(final int release) {
		phase = Phase.STATION;
		ticks = 0;
		splash = -1;
		DropCinematic.release = release;
		open(Minecraft.getInstance());
	}

	/** The fall over the new world has begun: the screen opens on it once the world is loaded. */
	public static void arrive(final BlockPos stand, final float top) {
		stopWind();
		phase = Phase.WAITING;
		ticks = 0;
		seated = false;
		DropCinematic.stand = stand;
		DropCinematic.top = top;
		DropCinematic.height = Math.max(1.0, top - stand.getY());
	}

	/** Whether any part of the drop is on screen. */
	public static boolean active() {
		return phase != Phase.NONE;
	}

	/** How much of the world showed through in the last frame, 0 (black) to 1 (clear). */
	public static float shown() {
		return shown;
	}

	public static void tick(final Minecraft mc) {
		if (phase == Phase.NONE) {
			return;
		}
		if (mc.level == null || mc.player == null) {
			if (phase != Phase.WAITING) {
				// the world was left mid-drop
				end(mc);
			}
			return;
		}
		ticks++;
		switch (phase) {
			case STATION -> {
				if (ticks == release) {
					yawAtRelease = mc.player.getYRot();
					pitchAtRelease = mc.player.getXRot();
					startWind(0.55F);
				}
				if (ticks > release && splash < 0) {
					if (wind != null) {
						wind.target = Math.min(1.0F, 0.55F + (ticks - release) * 0.03F);
					}
					if (mc.player.isUnderWater() || ticks > release + 80) {
						splash = ticks;
						stopWind();
					}
				}
				if (splash >= 0 && ticks > splash + 600) {
					// sent on long ago, and nothing came of it
					end(mc);
					return;
				}
				open(mc);
			}
			case WAITING -> {
				// once the new world is loaded: its loading screen gone (or none came up)
				if (mc.gui.screen() == null || mc.gui.screen() instanceof DropScreen && ticks > 5) {
					phase = Phase.GATHERING;
					ticks = 0;
					open(mc);
					startWind(0.75F);
				}
			}
			case GATHERING -> {
				// wind in the dark while the ground far below is built; then the fall may start
				open(mc);
				boolean ready = ticks >= GATHER_LEAST && groundReady(mc);
				if (ready || ticks >= GATHER_MOST) {
					life.shizune.mistinn.Mistinn.LOGGER.info(ready ? "The ground under the drop was ready after {} ticks"
						: "The ground under the drop was not ready after {} ticks; falling all the same", ticks);
					phase = Phase.FALLING;
					ticks = 0;
					ClientPlayNetworking.send(new StationArrival.ReadyPayload());
				}
			}
			case FALLING -> {
				seated |= mc.player.isPassenger();
				if (wind != null) {
					wind.target = 0.75F + 0.35F * progress(mc, 1.0F);
				}
				if (seated && !mc.player.isPassenger() || ticks > StationArrival.TICKS + 400) {
					// the ground
					phase = Phase.LANDED;
					ticks = 0;
					stopWind();
				}
				open(mc);
			}
			case LANDED -> {
				if (ticks >= OUT + COME_TO) {
					end(mc);
				}
			}
			default -> {
			}
		}
	}

	/** The ground the hunter must see is checked at the pad and this far out from it every way. */
	private static final int[][] GROUND = {{0, 0}, {24, 0}, {-24, 0}, {0, 24}, {0, -24}};

	/**
	 * Whether the ground the hunter will see has arrived from the server, at the pad and around it.
	 * It is too far below the top of the fall to be drawn yet: it is built as the hunter comes
	 * within sight of it, before they turn over to look.
	 */
	private static boolean groundReady(final Minecraft mc) {
		for (int[] at : GROUND) {
			if (!mc.level.getChunkSource().hasChunk(stand.getX() + at[0] >> 4, stand.getZ() + at[1] >> 4)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * How far the hunter has turned over from the sky to the ground, 0 to 1. They turn partway down,
	 * and never before the ground is well within how far this screen draws, so it is there to see.
	 */
	private static float turn(final Minecraft mc, final float partial) {
		double above = mc.player.getEyePosition(partial).y - mc.player.getEyeHeight() - stand.getY();
		double sight = mc.options.getEffectiveRenderDistance() * 16.0 * TURN_SIGHT;
		double from = Math.min(height * (1.0 - TURN_FROM), sight);
		return smooth((float) ((from - above) / (height * TURN_LENGTH)));
	}

	/** How white the view is in the cloud layer, as the hunter falls through it. */
	private static float cloud(final Minecraft mc, final float partial) {
		Vec3 eye = mc.player.getEyePosition(partial);
		float clouds = mc.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_HEIGHT, eye);
		float into = (float) Math.abs(eye.y - (clouds + 2.0));
		return 0.6F * Mth.clamp(1.0F - into / 7.0F, 0.0F, 1.0F);
	}

	private static void open(final Minecraft mc) {
		if (mc.gui.screen() == null && mc.level != null) {
			mc.gui.setScreen(new DropScreen());
		}
	}

	private static void end(final Minecraft mc) {
		phase = Phase.NONE;
		stopWind();
		shown = 1.0F;
		if (mc.gui.screen() instanceof DropScreen) {
			mc.gui.setScreen(null);
		}
	}

	/** How far down the fall over the new world the hunter is, 0 at the top to 1 at the pad. */
	private static float progress(final Minecraft mc, final float partial) {
		double feet = mc.player.getEyePosition(partial).y - mc.player.getEyeHeight();
		return (float) Mth.clamp((top - feet) / height, 0.0, 1.0);
	}

	/**
	 * The drop's view, as {yaw, pitch}, or null where the hunter's own view stands. Falling from the
	 * crane they look down at the sea; under it, up at the light going away. Over the new world they
	 * fall on their back, turning slowly, looking up; partway down they turn over and look at the
	 * pod by the pad, all the way down.
	 */
	public static float @Nullable [] view(final Minecraft mc, final float partial) {
		if (mc.player == null) {
			return null;
		}
		float t = ticks + partial;
		if (phase == Phase.STATION && ticks >= release) {
			float since = t - release;
			float yaw = yawAtRelease + since * 0.5F;
			float pitch = splash < 0
				? Mth.lerp(smooth(since / 8.0F), pitchAtRelease, 72.0F)
				: Mth.lerp(smooth((t - splash) / 20.0F), 72.0F, -60.0F);
			return new float[] {yaw, pitch};
		}
		if (phase == Phase.GATHERING) {
			// unseen in the dark: the fall opens looking up
			return new float[] {0.0F, -80.0F};
		}
		if (phase == Phase.FALLING) {
			float p = progress(mc, partial);
			float spin = 140.0F * (1.0F - (1.0F - p) * (1.0F - p));
			float lookingUp = -78.0F + 3.0F * Mth.sin(t * 0.07F);
			Vec3 eye = mc.player.getEyePosition(partial);
			Vec3 look = MistinnWorld.pod(stand).subtract(eye);
			float aimYaw = (float) Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0F;
			float aimPitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
			float turn = turn(mc, partial);
			float yaw = spin + Mth.wrapDegrees(aimYaw - spin) * turn;
			return new float[] {yaw, Mth.lerp(turn, lookingUp, aimPitch)};
		}
		return null;
	}

	/** The drop's overlay: the dark, the wind streaks, the splash, and the bars top and bottom. */
	public static void draw(final GuiGraphicsExtractor g, final float partial) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		float t = ticks + partial;
		float dark = 0.0F;
		float bars = 1.0F;
		float streaks = 0.0F;
		float flash = 0.0F;
		switch (phase) {
			case STATION -> {
				bars = smooth(t / 20.0F);
				if (t < 100.0F) {
					dark = DUSK * Mth.clamp((t - 10.0F) / 90.0F, 0.0F, 1.0F);
				} else if (t < release + 10.0F) {
					// black as the clamps let go, until the seat is out of the pod
					dark = Math.min(1.0F, DUSK + (t - 100.0F) / 6.0F * (1.0F - DUSK));
				} else if (splash < 0) {
					// a glimpse of the sea coming up
					dark = Math.max(0.5F, 1.0F - (t - release - 10.0F) / 4.0F * 0.5F);
					streaks = 1.0F;
				} else {
					float under = t - splash;
					flash = Math.max(0.0F, 1.0F - under / 10.0F) * 0.85F;
					dark = 0.3F + 0.7F * Mth.clamp((under - 8.0F) / 32.0F, 0.0F, 1.0F);
				}
			}
			case WAITING, GATHERING -> {
				dark = 1.0F;
				streaks = 0.6F;
			}
			case FALLING -> {
				Minecraft mc = Minecraft.getInstance();
				dark = 1.0F - Mth.clamp((t - BLIND) / OPEN, 0.0F, 1.0F);
				streaks = 0.5F + 0.5F * progress(mc, partial);
				flash = mc.player == null || mc.level == null ? 0.0F : cloud(mc, partial);
			}
			case LANDED -> {
				dark = 1.0F - Mth.clamp((t - OUT) / COME_TO, 0.0F, 1.0F);
				bars = 1.0F - smooth((t - OUT) / COME_TO);
			}
			default -> {
				return;
			}
		}
		shown = 1.0F - dark;
		if (dark > 0.0F) {
			g.fill(0, 0, w, h, (int) (dark * 255.0F) << 24);
		}
		if (streaks > 0.0F) {
			streaks(g, w, h, t, streaks);
		}
		if (flash > 0.0F) {
			g.fill(0, 0, w, h, (int) (flash * 255.0F) << 24 | 0xF2F6F8);
		}
		int bar = Math.round(h * BAR * bars);
		if (bar > 0) {
			g.fill(0, 0, w, bar, 0xFF000000);
			g.fill(0, h - bar, w, h, 0xFF000000);
		}
	}

	/** Thin lines of air rushing up past the hunter. */
	private static void streaks(final GuiGraphicsExtractor g, final int w, final int h, final float t, final float strength) {
		for (int i = 0; i < 28; i++) {
			int x = (int) (hash(i) * w);
			int length = 8 + (int) (hash(i + 101) * 34.0F);
			float speed = 14.0F + hash(i + 202) * 22.0F;
			float span = h + length;
			float y = h - (t * speed + hash(i + 303) * span) % span;
			int alpha = (int) (strength * (36.0F + hash(i + 404) * 64.0F));
			g.fill(x, (int) y, x + 1, (int) y + length, alpha << 24 | 0xFFFFFF);
		}
	}

	private static float hash(final int i) {
		return Mth.frac(Mth.sin(i * 12.9898F) * 43758.547F);
	}

	private static float smooth(final float x) {
		return Mth.smoothstep(Mth.clamp(x, 0.0F, 1.0F));
	}

	private static void startWind(final float volume) {
		stopWind();
		wind = new Wind(volume);
		Minecraft.getInstance().getSoundManager().play(wind);
	}

	private static void stopWind() {
		if (wind != null) {
			wind.end();
			wind = null;
		}
	}

	/** The rush of air of a fall, heard by the hunter alone, louder the faster they go. */
	private static final class Wind extends AbstractTickableSoundInstance {
		private float target;

		private Wind(final float target) {
			super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.target = target;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.05F;
			this.pitch = 0.9F;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		@Override
		public void tick() {
			this.volume += (this.target - this.volume) * 0.2F;
			this.pitch = 0.8F + this.volume * 0.3F;
		}

		private void end() {
			this.stop();
		}
	}
}
