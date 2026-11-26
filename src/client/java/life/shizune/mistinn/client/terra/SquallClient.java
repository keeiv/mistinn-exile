package life.shizune.mistinn.client.terra;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.terra.Squall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A crystal squall as the hunter sees and hears it: as it gathers a pale haze comes over the
 * highland and the crystal begins to chime; when it breaks the haze thickens until the world ends
 * some fifty blocks off, grit of the lattice streams down slantwise wherever the sky is open, and
 * the wind gusts. As slowly, it clears.
 */
public final class SquallClient {
	/** Under the full squall, the world ends this near. */
	private static final float END = 48.0F;
	/** The squall's pale crystal haze. */
	private static final float[] GRIT = {0.70F, 0.86F, 0.92F};
	/** How far the haze comes while the squall only gathers, and how far it moves each tick. */
	private static final float GATHERING = 0.3F;
	private static final float STEP = 0.008F;

	private static float haze;
	private static int gust;

	private SquallClient() {
	}

	public static void tick(final Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (level == null || player == null) {
			haze = 0.0F;
			return;
		}
		Squall.Stage stage = Squall.Stage.values()[player.getAttachedOrElse(Squall.SKY, 0)];
		float want = stage == Squall.Stage.RAGING ? 1.0F : stage == Squall.Stage.GATHERING ? GATHERING : 0.0F;
		haze += Mth.clamp(want - haze, -STEP, STEP);
		if (haze <= 0.01F || mc.isPaused()) {
			return;
		}
		RandomSource random = level.getRandom();
		// grit streams down slantwise wherever the sky is open
		BlockParticleOption grit = new BlockParticleOption(ParticleTypes.FALLING_DUST, MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState());
		int count = Math.round(haze * haze * 18.0F);
		for (int i = 0; i < count; i++) {
			double x = player.getX() + (random.nextDouble() - 0.5) * 32.0;
			double z = player.getZ() + (random.nextDouble() - 0.5) * 32.0;
			double y = player.getY() + 4.0 + random.nextDouble() * 10.0;
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) > y) {
				continue;
			}
			if (random.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.25 + random.nextDouble() * 0.15, -0.18, 0.08);
			} else {
				level.addParticle(grit, x, y, z, 0.0, 0.0, 0.0);
			}
		}
		// the crystal chimes all about, and the wind gusts
		if (random.nextFloat() < haze * 0.35F) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			level.playLocalSound(player.getX() + Math.cos(angle) * 6.0, player.getY() + 2.0, player.getZ() + Math.sin(angle) * 6.0,
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.WEATHER, 0.3F + 0.5F * haze, 0.7F + random.nextFloat() * 0.9F, false);
		}
		if (stage == Squall.Stage.RAGING && --gust <= 0) {
			gust = 60 + random.nextInt(50);
			level.playLocalSound(player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER,
				0.18F * haze, 0.55F + random.nextFloat() * 0.15F, false);
		}
	}

	/** How thick the haze is: 0 none, 1 the full squall. */
	public static float haze() {
		return haze;
	}

	/** Draws the squall's haze in over whatever the world was going to be drawn with. */
	public static void fog(final FogData fog) {
		if (haze <= 0.001F) {
			return;
		}
		float environment = Mth.lerp(haze, fog.environmentalEnd, END);
		float distance = Mth.lerp(haze, fog.renderDistanceEnd, END);
		fog.environmentalEnd = Math.min(fog.environmentalEnd, environment);
		fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, distance);
		fog.environmentalStart = Math.min(fog.environmentalStart, environment * 0.15F);
		fog.renderDistanceStart = Math.min(fog.renderDistanceStart, distance * 0.15F);
		fog.skyEnd = Math.min(fog.skyEnd, distance);
		fog.cloudEnd = Math.min(fog.cloudEnd, distance);
		float light = Mth.clamp((0.299F * fog.color.x + 0.587F * fog.color.y + 0.114F * fog.color.z) / 0.6F, 0.3F, 1.0F);
		float mix = 0.7F * haze;
		fog.color.set(Mth.lerp(mix, fog.color.x, GRIT[0] * light), Mth.lerp(mix, fog.color.y, GRIT[1] * light),
			Mth.lerp(mix, fog.color.z, GRIT[2] * light), fog.color.w);
	}
}
