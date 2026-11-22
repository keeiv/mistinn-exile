package life.shizune.mistinn.client.death;

import life.shizune.mistinn.client.enemy.ExecutionView;
import life.shizune.mistinn.enemy.ExecutionService;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The death scene. Every other creature vanishes, the view swings out to watch the hunter from
 * outside, and the body goes down: killed by a creature, it drops to one knee with a hand on the
 * ground, then topples onto its side; any other death (hunger, a fall, the water) is a stagger
 * and a fall onto the face. Then the body turns to smoke where the residue will lie, and the
 * mission-end screen comes up.
 */
public final class DeathScene {
	/** Seconds: when the body starts to fall, turns to smoke, is gone, and the screen comes. */
	public static final float FALL = 1.6F;
	public static final float SMOKE = 2.6F;
	public static final float GONE = 3.0F;
	public static final float SCREEN = 4.2F;

	/** Why the hunter fell, which decides the fall and the words said over it. */
	public enum Cause {
		CREATURE,
		EXECUTION,
		KEEPER,
		FALL,
		HUNGER,
		DROWNING,
		FIRE,
		OTHER;

		public boolean kneels() {
			return this == CREATURE || this == KEEPER;
		}
	}

	/** The body's pose for the renderer: seconds into the scene, and how it falls. */
	public record Pose(float seconds, boolean kneel, int side) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "mistinn:death");

	/** Client ticks since the scene began; the game clock, so it keeps pace with the world. */
	private static int ticks;
	private static boolean active;
	private static Cause cause = Cause.OTHER;
	private static int side = 1;
	private static @Nullable CameraType camera;
	private static float yaw;
	private static boolean hudWasHidden;

	private DeathScene() {
	}

	public static boolean active() {
		return active;
	}

	public static Cause cause() {
		return cause;
	}

	public static float seconds() {
		if (!active) {
			return 0.0F;
		}
		return (ticks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20.0F;
	}

	/** Starts the scene for the local player, who has just died. */
	public static void begin(final LocalPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		cause = causeOf(player.getLastDamageSource());
		side = player.getRandom().nextBoolean() ? 1 : -1;
		ticks = 0;
		active = true;
		CameraType entryCamera = ExecutionView.takeCamera();
		camera = entryCamera != null ? entryCamera : mc.options.getCameraType();
		mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		yaw = player.yBodyRot;
		hudWasHidden = mc.gui.hud.isHidden();
		if (!hudWasHidden) {
			mc.gui.hud.toggle();
		}
	}

	/** Ends the scene: view and HUD as they were. */
	public static void end() {
		if (!active) {
			return;
		}
		active = false;
		Minecraft mc = Minecraft.getInstance();
		if (camera != null) {
			mc.options.setCameraType(camera);
		}
		if (!hudWasHidden && mc.gui.hud.isHidden()) {
			mc.gui.hud.toggle();
		}
	}

	private static Cause causeOf(final @Nullable DamageSource source) {
		if (source == null) {
			return Cause.OTHER;
		}
		if (ExecutionService.wasExecuted(source)) {
			return Cause.EXECUTION;
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof life.shizune.mistinn.keeper.Keeper) {
			return Cause.KEEPER;
		}
		if (attacker instanceof LivingEntity && attacker != Minecraft.getInstance().player) {
			return Cause.CREATURE;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			return Cause.FALL;
		}
		if (source.is(DamageTypes.STARVE)) {
			return Cause.HUNGER;
		}
		if (source.is(DamageTypeTags.IS_DROWNING)) {
			return Cause.DROWNING;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return Cause.FIRE;
		}
		return Cause.OTHER;
	}

	public static @Nullable Pose pose() {
		return active ? new Pose(seconds(), cause.kneels(), side) : null;
	}

	/** Every client tick: the slow orbit of the view, and the smoke the body becomes. */
	public static void tick(final Minecraft mc) {
		LocalPlayer player = mc.player;
		if (!active || player == null) {
			return;
		}
		ticks++;
		float s = ticks / 20.0F;
		// the body keeps the way it faced; the view drifts round it from the front, looking down
		player.yBodyRot = yaw;
		player.yBodyRotO = yaw;
		player.setYRot(yaw + 30.0F + 40.0F * Mth.clamp(s / SCREEN, 0.0F, 1.0F));
		player.setYHeadRot(yaw);
		player.yHeadRotO = yaw;
		// seen from the front, the view sits where the hunter looks: up, so it looks down on them
		player.setXRot(-28.0F);
		if (s >= SMOKE - 0.1F && s < GONE + 0.6F && mc.level != null) {
			// smoke off the body where it lies, rising into the residue
			double x = player.getX() + side * (s < GONE ? 0.6 : 0.3);
			double y = player.getY() + 0.25;
			double z = player.getZ();
			for (int i = 0; i < 4; i++) {
				double dx = (player.getRandom().nextDouble() - 0.5) * 1.4;
				double dz = (player.getRandom().nextDouble() - 0.5) * 0.6;
				mc.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + dx, y, z + dz, 0.0, 0.02 + player.getRandom().nextDouble() * 0.03, 0.0);
				mc.level.addParticle(ParticleTypes.WHITE_ASH, x + dx, y + 0.3, z + dz, 0.0, 0.05, 0.0);
			}
			if (player.getRandom().nextInt(3) == 0) {
				mc.level.addParticle(ParticleTypes.POOF, x, y + 0.2, z, 0.0, 0.03, 0.0);
			}
		}
	}

	/** Hides every creature but the dying hunter. */
	public static boolean hides(final Entity entity) {
		LocalPlayer hunter = Minecraft.getInstance().player;
		if (!active || !(entity instanceof LivingEntity) || entity == hunter) {
			return false;
		}
		if (cause == Cause.EXECUTION && hunter != null && seconds() < 0.67F) {
			ExecutionView.Pose execution = ExecutionView.sample(hunter, 0.0F);
			return execution == null || entity.getId() != execution.striker();
		}
		return true;
	}
}
