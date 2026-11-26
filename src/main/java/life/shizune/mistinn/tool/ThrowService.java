package life.shizune.mistinn.tool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import life.shizune.mistinn.enemy.NoiseService;
import life.shizune.mistinn.enemy.StaggerService;
import life.shizune.mistinn.enemy.WindupService;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 投擲道具 as they land: a salt bomb bursts and rocks every creature near it back on its heels; a
 * flare burns for half a minute, lighting the ground and showing every creature within reach
 * through the fog; a bell lure rings where it fell and draws the creatures with nothing to chase.
 */
public final class ThrowService {
	/** How far a salt bomb's burst reaches, and what it takes from each. */
	public static final double BURST = 4.0;
	public static final float BURST_DAMAGE = 2.0F;
	/** A bomb is loud: creatures this near come to look. */
	public static final double BURST_HEARD = 14.0;
	/** How far a flare shows creatures, and how often it shows them again. */
	public static final double REVEAL = 16.0;
	public static final int REVEAL_EVERY = 40;
	/** How far a lure is heard, every {@link #LURE_EVERY} ticks for {@link #LURE_TICKS}. */
	public static final double LURE = 20.0;
	public static final int LURE_EVERY = 20;
	public static final int LURE_TICKS = 200;
	/** How long a flare burns; for tests, shorter. */
	public static int flareTicks = 600;

	/** What was thrown. */
	public enum Kind {
		SALT_BOMB, FLARE, BELL_LURE;

		public static Kind of(final ItemStack stack) {
			if (stack.is(MistinnItems.FLARE)) {
				return FLARE;
			}
			return stack.is(MistinnItems.BELL_LURE) ? BELL_LURE : SALT_BOMB;
		}
	}

	/** A flare burning or a lure ringing where it fell. */
	private record Lingering(ResourceKey<Level> dimension, BlockPos at, boolean lit, long until, boolean lure) {
	}

	private static final List<Lingering> LINGERING = new ArrayList<>();
	private static final DustParticleOptions SALT = new DustParticleOptions(0xF2EFE6, 1.5F);
	private static final DustParticleOptions EMBER = new DustParticleOptions(0xE8502A, 1.2F);

	private ThrowService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ThrowService::tick);
		// a flare's light goes out with the world
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Lingering lingering : LINGERING) {
				ServerLevel level = server.getLevel(lingering.dimension());
				if (level != null) {
					douse(level, lingering);
				}
			}
			LINGERING.clear();
		});
	}

	/** Something thrown came down at {@code at}; {@code air} is the open block it fell into (for a flare's light). */
	public static void land(final ServerLevel level, final Kind kind, final Vec3 at, final BlockPos air, final @Nullable Entity thrown,
		final @Nullable Entity thrower) {
		switch (kind) {
			case SALT_BOMB -> burst(level, at, thrown, thrower);
			case FLARE -> flare(level, air);
			case BELL_LURE -> {
				LINGERING.add(new Lingering(level.dimension(), air, false, level.getGameTime() + LURE_TICKS, true));
				ring(level, air);
			}
		}
	}

	/** 鹽彈: a burst of salt; every creature in reach is rocked back, its blow broken off, and a little hurt. */
	private static void burst(final ServerLevel level, final Vec3 at, final @Nullable Entity thrown, final @Nullable Entity thrower) {
		level.sendParticles(SALT, at.x, at.y + 0.3, at.z, 50, 1.4, 0.6, 1.4, 0.0);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.3, at.z, 16, 0.8, 0.4, 0.8, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.5F, 0.8F);
		DamageSource source = thrown != null ? level.damageSources().thrown(thrown, thrower) : level.damageSources().generic();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(BURST), m -> m.isAlive() && m instanceof Enemy && m.position().distanceTo(at) <= BURST)) {
			WindupService.interrupt(mob);
			Vec3 away = mob.position().subtract(at).multiply(1.0, 0.0, 1.0);
			StaggerService.stagger(mob, StaggerService.TICKS, away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize());
			mob.hurtServer(level, source, BURST_DAMAGE);
		}
		NoiseService.hear(level, at, BURST_HEARD);
	}

	/** 信號焰: it burns where it fell, lighting the ground; every creature in reach shows through the fog. */
	private static void flare(final ServerLevel level, final BlockPos air) {
		boolean lit = level.getBlockState(air).isAir();
		if (lit) {
			level.setBlock(air, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
		}
		LINGERING.add(new Lingering(level.dimension(), air, lit, level.getGameTime() + flareTicks, false));
		level.playSound(null, air, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.5F, 1.4F);
		reveal(level, air);
	}

	private static void reveal(final ServerLevel level, final BlockPos at) {
		Vec3 centre = Vec3.atCenterOf(at);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(REVEAL), m -> m.isAlive() && m instanceof Enemy && m.position().distanceTo(centre) <= REVEAL)) {
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, REVEAL_EVERY + 20, 0, false, false));
		}
	}

	/** 響鈴誘餌: a ring, and every creature in earshot with nothing to chase comes to look. */
	private static void ring(final ServerLevel level, final BlockPos at) {
		level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0F, 1.5F);
		NoiseService.hear(level, Vec3.atCenterOf(at), LURE);
	}

	private static void tick(final MinecraftServer server) {
		if (LINGERING.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		Iterator<Lingering> it = LINGERING.iterator();
		while (it.hasNext()) {
			Lingering lingering = it.next();
			ServerLevel level = server.getLevel(lingering.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			if (level.getGameTime() >= lingering.until()) {
				douse(level, lingering);
				it.remove();
				continue;
			}
			Vec3 centre = Vec3.atBottomCenterOf(lingering.at());
			if (lingering.lure()) {
				if (now % LURE_EVERY == 0) {
					ring(level, lingering.at());
				}
				continue;
			}
			if (now % 5 == 0) {
				level.sendParticles(ParticleTypes.FLAME, centre.x, centre.y + 0.2, centre.z, 2, 0.08, 0.05, 0.08, 0.01);
				level.sendParticles(EMBER, centre.x, centre.y + 0.4, centre.z, 2, 0.1, 0.2, 0.1, 0.0);
				level.sendParticles(ParticleTypes.SMOKE, centre.x, centre.y + 0.5, centre.z, 1, 0.05, 0.1, 0.05, 0.01);
			}
			if (now % REVEAL_EVERY == 0) {
				reveal(level, lingering.at());
				level.playSound(null, lingering.at(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.2F);
			}
		}
	}

	private static void douse(final ServerLevel level, final Lingering lingering) {
		if (lingering.lit() && level.getBlockState(lingering.at()).is(Blocks.LIGHT)) {
			level.setBlock(lingering.at(), Blocks.AIR.defaultBlockState(), 3);
		}
	}

	/** How many flares are burning and lures ringing, for tests. */
	public static int lingering() {
		return LINGERING.size();
	}
}
