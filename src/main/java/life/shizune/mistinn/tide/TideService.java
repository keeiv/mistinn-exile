package life.shizune.mistinn.tide;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.terra.CrystalLizard;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 霧潮: some nights the fog does not lift. A bell tolls at dusk and the fog rolls in on every side
 * (each hunter's client draws it close); creatures walk out of it around every hunter, elites come
 * twice as often and pirates land on the shore more readily. At dawn it ebbs, and a hunter who was
 * there when it rose and never fell in it has come through the tide.
 */
public final class TideService {
	/** The chance, on a normal world, that a night is a tide night; easy worlds see fewer, Rust Anchor more. */
	public static final float CHANCE = 0.15F;
	/** No tide before the world's third night. */
	public static final long FIRST_NIGHT = 3;
	/** Every this many ticks, creatures walk out of the fog round each hunter. */
	public static final int EVERY = 400;
	/** How far off they come out of it. */
	public static final int NEAR = 20;
	public static final int FAR = 36;
	/** No more come while this many hostile creatures are already this near a hunter. */
	public static final int CROWD = 16;
	public static final double CROWD_RANGE = 48.0;

	/** The world's tide: the night last weighed, whether the fog is up, and who was there when it rose and has not fallen since. */
	public record Tide(long night, boolean up, List<UUID> witnesses) {
		public static final Tide NONE = new Tide(-1L, false, List.of());
		public static final Codec<Tide> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("night").forGetter(Tide::night),
			Codec.BOOL.fieldOf("up").forGetter(Tide::up),
			UUIDUtil.CODEC.listOf().optionalFieldOf("witnesses", List.of()).forGetter(Tide::witnesses)
		).apply(i, Tide::new));
	}

	public static final AttachmentType<Tide> TIDE = AttachmentRegistry.<Tide>builder()
		.persistent(Tide.CODEC)
		.buildAndRegister(Mistinn.id("fog_tide"));
	/** On each hunter in the overworld while the tide is up: their client draws the fog in. */
	public static final AttachmentType<Boolean> FOG = AttachmentRegistry.<Boolean>builder()
		.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("tide_fog"));

	/** For tests: the next night weighed is a tide night, whatever the odds and however young the world. */
	public static boolean certain;

	private TideService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TideService::tick);
		// falling in the fog is not coming through it
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && player.level().getServer() != null) {
				Tide tide = tide(player.level().getServer());
				if (tide.up() && tide.witnesses().contains(player.getUUID())) {
					List<UUID> left = new ArrayList<>(tide.witnesses());
					left.remove(player.getUUID());
					set(player.level().getServer(), new Tide(tide.night(), true, List.copyOf(left)));
				}
			}
		});
	}

	public static Tide tide(final MinecraftServer server) {
		return server.overworld().getAttachedOrElse(TIDE, Tide.NONE);
	}

	private static void set(final MinecraftServer server, final Tide tide) {
		server.overworld().setAttached(TIDE, tide);
	}

	/** Whether the fog tide is up over the overworld now. */
	public static boolean up(final MinecraftServer server) {
		return tide(server).up();
	}

	/** The chance that a night in this world is a tide night. */
	public static float chance(final MinecraftServer server) {
		return switch (Hardship.of(server)) {
			case EASY -> CHANCE * 2.0F / 3.0F;
			case NORMAL -> CHANCE;
			case RUST_ANCHOR -> CHANCE * 5.0F / 3.0F;
			case HUNTER -> CHANCE;
		};
	}

	private static void tick(final MinecraftServer server) {
		int now = server.getTickCount();
		if (now % 20 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		Tide tide = tide(server);
		long night = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		if (tide.up()) {
			if (!level.isDarkOutside()) {
				lower(level);
			} else if (now % EVERY == 0) {
				waves(level);
			}
		} else if (level.isDarkOutside() && tide.night() != night) {
			// each night is weighed once, as it falls
			set(server, new Tide(night, false, List.of()));
			if (certain || allowed(level, night) && level.getRandom().nextFloat() < chance(server)) {
				certain = false;
				raise(level);
			}
		}
		boolean upNow = tide(server).up();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean fog = upNow && player.level() == level;
			if (fog != player.getAttachedOrElse(FOG, false)) {
				if (fog) {
					player.setAttached(FOG, true);
				} else {
					player.removeAttached(FOG);
				}
			}
		}
	}

	private static boolean allowed(final ServerLevel level, final long night) {
		return night >= FIRST_NIGHT && level.getDifficulty() != Difficulty.PEACEFUL && WorldMode.of(level.getServer()) != WorldMode.TRAINING;
	}

	/** The fog comes in: a bell, a title, and everyone in the overworld now is a witness. */
	public static void raise(final ServerLevel level) {
		long night = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		List<UUID> there = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator()) {
				there.add(player.getUUID());
			}
			player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 70, 40));
			player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.mistinn.tide")));
			player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.mistinn.tide.sub")));
			player.sendSystemMessage(Component.translatable("message.mistinn.tide.rise"));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 2.0F, 0.5F);
		}
		set(level.getServer(), new Tide(night, true, List.copyOf(there)));
	}

	/** Dawn: the fog ebbs, and those who were there when it rose and never fell in it come through. */
	public static void lower(final ServerLevel level) {
		Tide tide = tide(level.getServer());
		set(level.getServer(), new Tide(tide.night(), false, List.of()));
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(Component.translatable("message.mistinn.tide.fall"));
			if (player.isAlive() && tide.witnesses().contains(player.getUUID())) {
				AchievementService.grant(player, Achievement.TIDE);
			}
		}
	}

	/** Creatures walk out of the fog round every hunter in the overworld, unless the hunter is already beset; somewhere far off, a bell. */
	public static void waves(final ServerLevel level) {
		if (!level.getGameRules().get(GameRules.SPAWN_MOBS) || !level.getGameRules().get(GameRules.SPAWN_MONSTERS)) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
				continue;
			}
			if (level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(CROWD_RANGE),
				mob -> mob instanceof Enemy && !CrystalLizard.companion(mob)).size() >= CROWD) {
				continue;
			}
			int count = 1 + random.nextInt(3);
			for (int i = 0; i < count; i++) {
				emerge(level, player, random);
			}
			if (random.nextBoolean()) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				level.playSound(null, player.getX() + Math.cos(angle) * 40.0, player.getY(), player.getZ() + Math.sin(angle) * 40.0,
					SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 3.0F, 0.45F);
			}
		}
	}

	/** One creature of the place's own kinds, out of the fog somewhere dark a little way off (none when nowhere would do). */
	private static void emerge(final ServerLevel level, final ServerPlayer player, final RandomSource random) {
		for (int attempt = 0; attempt < 10; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int reach = NEAR + random.nextInt(FAR - NEAR + 1);
			int x = Mth.floor(player.getX() + Math.cos(angle) * reach);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * reach);
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (Math.abs(at.getY() - player.getY()) > 12 || !level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()
				|| level.getBrightness(LightLayer.BLOCK, at) > 7) {
				continue;
			}
			// what would come of itself here: the biome's creatures, or a structure's (a fortress, a witch's hut)
			Optional<MobSpawnSettings.SpawnerData> kind = level.getChunkSource().getGenerator()
				.getMobsAt(level, level.structureManager(), MobCategory.MONSTER, at).getRandom(random);
			if (kind.isEmpty()) {
				continue;
			}
			EntityType<?> type = kind.get().type();
			if (!SpawnPlacements.isSpawnPositionOk(type, level, at) || !SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, at, random)) {
				continue;
			}
			// natural, so it may be an elite (twice as likely in the tide)
			Entity creature = type.spawn(level, at, EntitySpawnReason.NATURAL);
			if (creature != null) {
				level.sendParticles(ParticleTypes.CLOUD, creature.getX(), creature.getY() + 1.0, creature.getZ(), 14, 0.5, 0.6, 0.5, 0.01);
				return;
			}
		}
	}
}
