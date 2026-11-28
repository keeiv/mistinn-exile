package life.shizune.mistinn.terra;

import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

/**
 * 泰拉: a world holds six Terra highlands, no more. Where they lie is settled once, from the
 * world's seed, the first time the world is opened (old worlds too): far out from spawn, each
 * well apart from the others, never centred on open water. The land itself turns when its chunks
 * load (see {@link TerraShaper}).
 */
public final class TerraService {
	/** How many a world holds, and how far each reaches. */
	public static final int COUNT = 6;
	public static final int RADIUS = 260;
	/** How far out from spawn they lie, and how far apart (centre to centre) at the least. */
	public static final int NEAREST = 1200;
	public static final int FARTHEST = 3200;
	public static final int APART = 1300;
	/** How far short of another domain's edge an older highland's widening stops. */
	public static final int GAP = 16;

	private TerraService() {
	}

	public static void register() {
		DomainService.shaper(Domain.Kind.TERRA, new TerraShaper());
		ServerLifecycleEvents.SERVER_STARTED.register(TerraService::widen);
		ServerLifecycleEvents.SERVER_STARTED.register(TerraService::plan);
		TerraLake.register();
		ResearchShip.register();
	}

	/**
	 * An older world's highlands grow to the highlands' present width: each spreads on out from where it reached, and the
	 * new ground is turned as any is (only what nature put there, never near anyone's things).
	 */
	public static void widen(final MinecraftServer server) {
		for (Domain domain : DomainService.of(server, Domain.Kind.TERRA)) {
			if (domain.radius() >= RADIUS) {
				continue;
			}
			// (never so far that it reaches another's ground, a rift's that came near it)
			int wide = RADIUS;
			for (Domain other : DomainService.all(server)) {
				if (other.id() != domain.id()) {
					wide = Math.min(wide, (int) Math.floor(domain.distance(other.centre().getX() + 0.5, other.centre().getZ() + 0.5)) - other.radius() - GAP);
				}
			}
			if (wide > domain.radius()) {
				DomainService.update(server, new Domain(domain.id(), domain.kind(), domain.centre(), wide, domain.phase(), domain.since(), domain.spread()));
			}
		}
	}

	/** Settles where the world's Terra highlands lie, if it has not been done. */
	public static void plan(final MinecraftServer server) {
		if (WorldMode.of(server) == WorldMode.TRAINING) {
			return;
		}
		ServerLevel level = server.overworld();
		// Udasru's one highland is drawn: the crystal highland, round the crown peak where its elder nests
		if (WorldMode.of(server) == WorldMode.EPIC) {
			if (DomainService.of(server, Domain.Kind.TERRA).isEmpty()) {
				life.shizune.mistinn.udasru.UdasruPlaces.GreatBell crown = life.shizune.mistinn.udasru.UdasruPlaces.GreatBell.CRYSTAL;
				DomainService.add(server, Domain.Kind.TERRA, new BlockPos(crown.x, level.getSeaLevel(), crown.z), RADIUS, Domain.Phase.SETTLED,
					RADIUS + DomainService.EDGE);
			}
			return;
		}
		RandomSource random = RandomSource.create(level.getSeed() ^ 0x7E44A5EEDL);
		BlockPos spawn = level.getRespawnData().pos();
		int placed = DomainService.of(server, Domain.Kind.TERRA).size();
		for (int tries = 0; placed < COUNT && tries < 400; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = NEAREST + random.nextDouble() * (FARTHEST - NEAREST);
			BlockPos centre = new BlockPos((int) (spawn.getX() + Math.cos(angle) * distance), level.getSeaLevel(), (int) (spawn.getZ() + Math.sin(angle) * distance));
			if (DomainService.crowded(server, centre, RADIUS, APART - 2 * RADIUS) || water(level, centre)) {
				continue;
			}
			DomainService.add(server, Domain.Kind.TERRA, centre, RADIUS, Domain.Phase.SETTLED, RADIUS + DomainService.EDGE);
			placed++;
		}
	}

	/** Whether the middle, or most of the ring round it, is sea or river (read from the world's plan, without making the chunks). */
	private static boolean water(final ServerLevel level, final BlockPos centre) {
		int wet = 0;
		for (int i = 0; i < 9; i++) {
			BlockPos at = i == 0 ? centre : centre.offset((int) (Math.cos(i * Math.PI / 4) * RADIUS * 0.6), 0, (int) (Math.sin(i * Math.PI / 4) * RADIUS * 0.6));
			Holder<Biome> biome = level.getBiome(at);
			if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
				if (i == 0) {
					return true;
				}
				wet++;
			}
		}
		return wet > 3;
	}

	/** Lays a Terra highland just here, already whole (for tests). */
	public static Domain settleAt(final ServerLevel level, final BlockPos centre, final int radius) {
		return DomainService.add(level.getServer(), Domain.Kind.TERRA, centre, radius, Domain.Phase.SETTLED, radius + DomainService.EDGE);
	}
}
