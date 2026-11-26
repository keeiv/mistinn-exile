package life.shizune.mistinn.client.naval;

import life.shizune.mistinn.naval.NavalFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Draws the sea fight's flashes, smoke and spray from the server's word. */
public final class NavalFxClient {
	private NavalFxClient() {
	}

	public static void received(final NavalFx fx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		Vec3 from = fx.from();
		switch (fx.kind()) {
			case NavalFx.GUN -> gun(level, random, from, fx.to());
			case NavalFx.CANNON -> cannon(level, random, from, fx.to().subtract(from));
			case NavalFx.SPLASH -> splash(level, random, from);
			case NavalFx.BLAST -> blast(level, random, from);
			default -> {
			}
		}
	}

	/** A musket's flash and puff, the thin smoke of the shot's line, and where it struck. */
	private static void gun(final ClientLevel level, final RandomSource random, final Vec3 from, final Vec3 to) {
		for (int i = 0; i < 4; i++) {
			level.addParticle(ParticleTypes.FLAME, from.x, from.y, from.z, gauss(random, 0.02), gauss(random, 0.02), gauss(random, 0.02));
		}
		for (int i = 0; i < 10; i++) {
			level.addParticle(ParticleTypes.SMOKE, from.x + gauss(random, 0.1), from.y + gauss(random, 0.1), from.z + gauss(random, 0.1),
				gauss(random, 0.02), 0.02 + random.nextFloat() * 0.02, gauss(random, 0.02));
		}
		level.addParticle(ParticleTypes.POOF, from.x, from.y, from.z, 0.0, 0.03, 0.0);
		Vec3 line = to.subtract(from);
		int steps = (int) Math.min(60, line.length() / 0.7);
		for (int i = 1; i < steps; i++) {
			Vec3 at = from.add(line.scale(i / (double) steps));
			level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0.0, 0.005, 0.0);
		}
		BlockPos struck = BlockPos.containing(to);
		boolean water = !level.getFluidState(struck).isEmpty();
		for (int i = 0; i < 8; i++) {
			level.addParticle(water ? ParticleTypes.SPLASH : ParticleTypes.CRIT, to.x, to.y, to.z, gauss(random, 0.15), 0.1 + random.nextFloat() * 0.1,
				gauss(random, 0.15));
		}
	}

	/** A gun going off: the flash, and a great cloud rolled out of the gunport. */
	private static void cannon(final ClientLevel level, final RandomSource random, final Vec3 from, final Vec3 out) {
		level.addParticle(ParticleTypes.EXPLOSION, from.x + out.x * 0.6, from.y, from.z + out.z * 0.6, 0.0, 0.0, 0.0);
		for (int i = 0; i < 8; i++) {
			level.addParticle(ParticleTypes.FLAME, from.x, from.y, from.z, out.x * 0.25 + gauss(random, 0.05), gauss(random, 0.05), out.z * 0.25 + gauss(random, 0.05));
		}
		for (int i = 0; i < 18; i++) {
			double speed = 0.05 + random.nextFloat() * 0.25;
			level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, from.x + gauss(random, 0.3), from.y + gauss(random, 0.3),
				from.z + gauss(random, 0.3), out.x * speed + gauss(random, 0.02), 0.01 + random.nextFloat() * 0.02, out.z * speed + gauss(random, 0.02));
		}
		for (int i = 0; i < 10; i++) {
			level.addParticle(ParticleTypes.LARGE_SMOKE, from.x + out.x, from.y, from.z + out.z, out.x * 0.1 + gauss(random, 0.04), 0.02, out.z * 0.1 + gauss(random, 0.04));
		}
	}

	/** A shot into the sea: a column of spray thrown up and falling back, foam, and the roar of it. */
	private static void splash(final ClientLevel level, final RandomSource random, final Vec3 at) {
		for (int i = 0; i < 90; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double out = random.nextDouble() * 0.35;
			level.addAlwaysVisibleParticle(ParticleTypes.SPLASH, true, at.x + gauss(random, 0.6), at.y + 0.2, at.z + gauss(random, 0.6),
				Math.cos(angle) * out, 0.6 + random.nextDouble() * 0.9, Math.sin(angle) * out);
		}
		for (int i = 0; i < 30; i++) {
			level.addParticle(ParticleTypes.BUBBLE, at.x + gauss(random, 0.8), at.y - 0.3, at.z + gauss(random, 0.8), 0.0, 0.1, 0.0);
			level.addParticle(ParticleTypes.CLOUD, at.x + gauss(random, 0.9), at.y + 0.3 + random.nextDouble(), at.z + gauss(random, 0.9),
				gauss(random, 0.03), 0.04, gauss(random, 0.03));
		}
		level.playLocalSound(at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 4.0F, 0.6F + random.nextFloat() * 0.2F, false);
	}

	/** A shot striking something hard: smoke and sparks. */
	private static void blast(final ClientLevel level, final RandomSource random, final Vec3 at) {
		for (int i = 0; i < 16; i++) {
			level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + gauss(random, 0.6), at.y + gauss(random, 0.4), at.z + gauss(random, 0.6), 0.0, 0.05, 0.0);
			level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, gauss(random, 0.4), 0.2 + random.nextDouble() * 0.3, gauss(random, 0.4));
		}
	}

	private static double gauss(final RandomSource random, final double size) {
		return random.nextGaussian() * size;
	}
}
