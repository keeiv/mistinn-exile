package life.shizune.mistinn.elite;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.WoundService;
import life.shizune.mistinn.enemy.ExecutionService;
import life.shizune.mistinn.enemy.StaggerService;
import life.shizune.mistinn.enemy.WindupService;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 精英怪物: now and then a creature that came into the world of itself has stayed too long in the
 * fog. Its name shows gold when a hunter looks at it, gold dust drifts about it, and it carries one
 * to three {@link Affix affixes} and half again its kind's life. They are commoner by night and the
 * further out from the world's spawn. A hunter it is after (or who struck it) sees its life on a bar
 * at the top of the screen. It pays more salt, and brought down by a hunter it may leave supplies.
 */
public final class EliteService {
	/** The chance that a creature coming into the world of itself is an elite, before night and distance. */
	public static final float CHANCE = 0.03F;
	/** More by night, in the overworld. */
	public static final float NIGHT = 0.02F;
	/** And more the further out: this for every 500 blocks from the world's spawn, up to {@link #MOST_DISTANT}. */
	public static final float PER_500 = 0.01F;
	public static final float MOST_DISTANT = 0.04F;
	/** Further than this from the world's spawn, an elite may carry a third affix. */
	public static final double FAR = 1500.0;
	/** The chance of a second affix, and (far out) of a third. */
	public static final float SECOND = 0.25F;
	public static final float THIRD = 0.2F;
	/** Every elite has this much again of its kind's life. */
	public static final double LIFE = 0.5;
	/** A volatile elite bursts this long after it dies, this strong. */
	public static final int FUSE = 30;
	public static final float BURST = 1.4F;
	/** A warchief drives on the creatures this near it, every {@link #RALLY_EVERY} ticks. */
	public static final double RALLY = 12.0;
	public static final int RALLY_EVERY = 40;
	/** A mistwalker, struck, goes into the fog this often; once at most in {@link #BLINK_REST} ticks. */
	public static final float BLINK = 0.35F;
	public static final int BLINK_REST = 60;
	/** Hunters this near an elite that is fighting see its bar. */
	public static final double BAR = 24.0;
	/** For each affix, the chance a hunter's kill leaves something behind. */
	public static final float SPOILS = 0.6F;
	/** The chance a hunter's kill also leaves a piece of gear. */
	public static final float GEAR = 0.25F;
	public static final ResourceKey<DamageType> SALT_BURST = ResourceKey.create(Registries.DAMAGE_TYPE, Mistinn.id("salt_burst"));

	/** An elite's affixes, by id; absent on an ordinary creature. Saved with it, and known to every client. */
	public static final AttachmentType<List<String>> ELITE = AttachmentRegistry.<List<String>>builder()
		.persistent(Codec.STRING.listOf())
		.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("elite"));

	/** The kinds that may be elites: the common fighters of the overworld and the fortress (none of the mod's own). */
	private static final Set<EntityType<?>> KINDS = Set.of(EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.DROWNED, EntityTypes.ZOMBIE_VILLAGER,
		EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.PARCHED, EntityTypes.SPIDER, EntityTypes.ENDERMAN,
		EntityTypes.WITHER_SKELETON);
	/** Gold dust about an elite. */
	private static final DustParticleOptions DUST = new DustParticleOptions(0xE0B040, 1.1F);
	/** The white of salt about to burst. */
	private static final DustParticleOptions SALT = new DustParticleOptions(0xF4F1E8, 1.6F);
	/** Blood, as a bloodthirsty elite drinks. */
	private static final DustParticleOptions BLOOD = new DustParticleOptions(0x8A1010, 1.2F);
	/** A burst hurts the living, not what lies on the ground (the elite's own spoils among it). */
	private static final ExplosionDamageCalculator SPARES_LOOT = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(final Explosion explosion, final Entity entity) {
			return !(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb);
		}
	};
	/** Automated runs meet no elites by chance: a test that wants one makes it, or sets {@link #certain}. */
	private static final boolean AUTOMATED = System.getProperty("fabric.client.gametest") != null;

	/** A volatile elite's last breath: where and when it bursts. */
	private record Burst(ResourceKey<Level> dimension, Vec3 at, long when) {
	}

	/** Creatures that came of themselves this tick, weighed at its end in their final form. */
	private static final List<Mob> SPAWNED = new ArrayList<>();
	private static final Set<Mob> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	private static final Map<Mob, Long> BLINKED = new WeakHashMap<>();
	/** The quarry each warchief last roared at. */
	private static final Map<Mob, UUID> ROARED = new WeakHashMap<>();
	private static final List<Burst> BURSTS = new ArrayList<>();
	/** For tests: every chance comes up (a creature that came of itself is an elite, a struck mistwalker goes). */
	public static boolean certain;

	private EliteService() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.hasAttached(ELITE)) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				LOADED.remove(mob);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(EliteService::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof Mob mob && damageTaken > 0.0F && source.getEntity() instanceof ServerPlayer hunter && has(mob, Affix.MISTWALKER)) {
				blink(mob, hunter);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(EliteService::died);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SPAWNED.clear();
			LOADED.clear();
			BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			BARS.clear();
			BLINKED.clear();
			ROARED.clear();
			BURSTS.clear();
		});
	}

	// ── Who is an elite ─────────────────────────────────────────────

	/** A creature finished spawning ({@code Mob.finalizeSpawn}): one that came of itself is weighed at the tick's end. */
	public static void spawned(final Mob mob, final EntitySpawnReason reason) {
		if (reason == EntitySpawnReason.NATURAL && !mob.level().isClientSide()) {
			SPAWNED.add(mob);
		}
	}

	/** Whether the creature could be an elite: one of the common fighters, grown, alone, in a world that is not the training ground. */
	public static boolean eligible(final ServerLevel level, final Mob mob) {
		return KINDS.contains(mob.getType()) && !mob.isBaby() && !mob.hasAttached(ELITE) && !mob.isPassenger() && !mob.isVehicle()
			&& level.getDifficulty() != Difficulty.PEACEFUL && WorldMode.of(level.getServer()) != WorldMode.TRAINING;
	}

	/** The chance that a creature coming into the world here, now, is an elite. */
	public static float chance(final ServerLevel level, final BlockPos at) {
		float chance = CHANCE;
		if (level.dimension().equals(Level.OVERWORLD) && level.isDarkOutside()) {
			chance += NIGHT;
		}
		chance += Math.min(MOST_DISTANT, (float) (distance(level, at) / 500.0) * PER_500);
		// twice as many under the fog tide
		if (level.dimension().equals(Level.OVERWORLD) && life.shizune.mistinn.tide.TideService.up(level.getServer())) {
			chance *= 2.0F;
		}
		float hardship = switch (Hardship.of(level.getServer())) {
			case EASY -> 0.5F;
			case NORMAL -> 1.0F;
			case RUST_ANCHOR -> 1.5F;
			// the epic's hunters meet the marked more often: more to hunt, more to carry home
			case HUNTER -> 1.6F;
		};
		return chance * hardship;
	}

	/** How far out from the world's spawn, in overworld blocks (the nether counts eight to one). */
	private static double distance(final ServerLevel level, final BlockPos at) {
		BlockPos spawn = level.getServer().overworld().getRespawnData().pos();
		double scale = level.dimensionType().coordinateScale();
		return Math.hypot(at.getX() * scale - spawn.getX(), at.getZ() * scale - spawn.getZ());
	}

	private static void roll(final ServerLevel level, final Mob mob) {
		if (!eligible(level, mob)) {
			return;
		}
		RandomSource random = mob.getRandom();
		if (!certain && (AUTOMATED || random.nextFloat() >= chance(level, mob.blockPosition()))) {
			return;
		}
		int count = 1;
		if (random.nextFloat() < SECOND) {
			count++;
			if (distance(level, mob.blockPosition()) > FAR && random.nextFloat() < THIRD) {
				count++;
			}
		}
		make(mob, pick(mob, random, count));
	}

	/** {@code count} different affixes that suit the creature, at random, in a steady order. */
	public static List<Affix> pick(final Mob mob, final RandomSource random, final int count) {
		List<Affix> open = new ArrayList<>();
		for (Affix affix : Affix.values()) {
			if (affix.fits(mob)) {
				open.add(affix);
			}
		}
		List<Affix> picked = new ArrayList<>();
		while (picked.size() < count && !open.isEmpty()) {
			picked.add(open.remove(random.nextInt(open.size())));
		}
		picked.sort(Comparator.naturalOrder());
		return picked;
	}

	/** Makes the creature an elite with these affixes: its body, its name, its life full. */
	public static void make(final Mob mob, final List<Affix> affixes) {
		mob.setAttached(ELITE, affixes.stream().map(affix -> affix.id).toList());
		modify(mob, Attributes.MAX_HEALTH, Mistinn.id("elite_life"), LIFE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		for (Affix affix : affixes) {
			affix.apply(mob);
		}
		mob.setHealth(mob.getMaxHealth());
		mob.setCustomName(name(mob.getType(), affixes));
		LOADED.add(mob);
	}

	/** Adds a lasting modifier unless the creature already carries it (or lacks the attribute). */
	static void modify(final Mob mob, final Holder<Attribute> attribute, final Identifier id, final double amount, final AttributeModifier.Operation operation) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && !instance.hasModifier(id)) {
			instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** 「迅捷・鐵皮 殭屍」, in gold, in each hunter's own language. */
	public static Component name(final EntityType<?> type, final List<Affix> affixes) {
		MutableComponent titles = Component.empty();
		for (int i = 0; i < affixes.size(); i++) {
			if (i > 0) {
				titles.append(Component.translatable("elite.mistinn.separator"));
			}
			titles.append(affixes.get(i).title());
		}
		return Component.translatable("elite.mistinn.name", titles, type.getDescription()).withStyle(ChatFormatting.GOLD);
	}

	public static boolean isElite(final Entity entity) {
		return entity.hasAttached(ELITE);
	}

	public static List<Affix> affixes(final Entity entity) {
		List<String> ids = entity.getAttached(ELITE);
		if (ids == null) {
			return List.of();
		}
		List<Affix> affixes = new ArrayList<>();
		for (String id : ids) {
			Affix affix = Affix.byId(id);
			if (affix != null) {
				affixes.add(affix);
			}
		}
		return affixes;
	}

	public static boolean has(final Entity entity, final Affix affix) {
		List<String> ids = entity.getAttached(ELITE);
		return ids != null && ids.contains(affix.id);
	}

	/** How many times its kind's salt a creature pays: an elite one more for each affix. */
	public static int saltFactor(final LivingEntity victim) {
		List<String> ids = victim.getAttached(ELITE);
		return ids == null ? 1 : 1 + ids.size();
	}

	// ── What elites do ─────────────────────────────────────────────

	private static void tick(final MinecraftServer server) {
		if (!SPAWNED.isEmpty()) {
			List<Mob> spawned = List.copyOf(SPAWNED);
			SPAWNED.clear();
			for (Mob mob : spawned) {
				if (!mob.isRemoved() && mob.level() instanceof ServerLevel level && level.getEntity(mob.getUUID()) == mob) {
					roll(level, mob);
				}
			}
		}
		bursts(server);
		int now = server.getTickCount();
		if (now % 10 != 0) {
			return;
		}
		Set<UUID> fighting = new HashSet<>();
		for (Mob mob : List.copyOf(LOADED)) {
			if (!mob.isAlive() || mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				continue;
			}
			aura(level, mob);
			bar(level, mob);
			fighting.add(mob.getUUID());
			if (now % RALLY_EVERY == 0 && has(mob, Affix.WARCHIEF)) {
				rally(level, mob);
			}
		}
		// the bars of elites that are gone (dead, despawned, unloaded) come down
		BARS.entrySet().removeIf(entry -> {
			if (fighting.contains(entry.getKey())) {
				return false;
			}
			entry.getValue().removeAllPlayers();
			return true;
		});
	}

	private static void aura(final ServerLevel level, final Mob mob) {
		double width = mob.getBbWidth();
		double height = mob.getBbHeight();
		level.sendParticles(DUST, mob.getX(), mob.getY() + height * 0.55, mob.getZ(), 2, width * 0.45, height * 0.3, width * 0.45, 0.0);
	}

	/** The elite's life on a bar, for the hunters near it while it fights (it is after a hunter, or one struck it). */
	private static void bar(final ServerLevel level, final Mob mob) {
		boolean fights = mob.getTarget() instanceof Player || mob.getLastHurtByPlayer() != null;
		ServerBossEvent bar = BARS.get(mob.getUUID());
		if (bar == null) {
			if (!fights) {
				return;
			}
			bar = new ServerBossEvent(UUID.randomUUID(), mob.getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
			BARS.put(mob.getUUID(), bar);
		}
		bar.setProgress(Mth.clamp(mob.getHealth() / mob.getMaxHealth(), 0.0F, 1.0F));
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (player.level() != level) {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : level.players()) {
			boolean sees = fights && player.isAlive() && !player.isSpectator() && player.distanceToSqr(mob) < BAR * BAR;
			if (sees && !bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			} else if (!sees && bar.getPlayers().contains(player)) {
				bar.removePlayer(player);
			}
		}
	}

	/** A warchief after a hunter drives the fighters round it on: faster, harder, and after the same hunter. */
	private static void rally(final ServerLevel level, final Mob chief) {
		if (!(chief.getTarget() instanceof Player quarry) || !quarry.isAlive()) {
			return;
		}
		for (Mob mob : level.getEntitiesOfClass(Mob.class, chief.getBoundingBox().inflate(RALLY), mob -> mob != chief && mob.isAlive() && KINDS.contains(mob.getType()))) {
			mob.addEffect(new MobEffectInstance(MobEffects.SPEED, RALLY_EVERY + 30, 0, false, true));
			mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RALLY_EVERY + 30, 0, false, true));
			if (mob.getTarget() == null) {
				mob.setTarget(quarry);
			}
		}
		if (!quarry.getUUID().equals(ROARED.get(chief))) {
			ROARED.put(chief, quarry.getUUID());
			level.playSound(null, chief.getX(), chief.getY(), chief.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.9F, 1.5F);
		}
	}

	/** A struck mistwalker may go into the fog and step out beside or behind the hunter; never mid-blow, staggered or in a finisher. */
	private static void blink(final Mob mob, final ServerPlayer hunter) {
		if (!mob.isAlive() || !(mob.level() instanceof ServerLevel level) || WindupService.winding(mob) || StaggerService.staggered(mob)
			|| ExecutionService.holdsMob(mob)) {
			return;
		}
		long now = level.getGameTime();
		Long last = BLINKED.get(mob);
		if (last != null && now - last < BLINK_REST || !certain && mob.getRandom().nextFloat() >= BLINK) {
			return;
		}
		Vec3 spot = beside(level, mob, hunter);
		if (spot == null) {
			return;
		}
		BLINKED.put(mob, now);
		puff(level, mob.position(), mob);
		mob.teleportTo(spot.x, spot.y, spot.z);
		mob.getNavigation().stop();
		mob.setTarget(hunter);
		puff(level, spot, mob);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	/** Somewhere to step out of the fog: a few blocks from the hunter, behind or to the side, on firm ground with room to stand. */
	private static @Nullable Vec3 beside(final ServerLevel level, final Mob mob, final ServerPlayer hunter) {
		RandomSource random = mob.getRandom();
		for (int attempt = 0; attempt < 12; attempt++) {
			float yaw = hunter.getYRot() + 180.0F + (random.nextFloat() - 0.5F) * 180.0F;
			double reach = 2.5 + random.nextDouble() * 1.5;
			Vec3 way = Vec3.directionFromRotation(0.0F, yaw);
			BlockPos column = BlockPos.containing(hunter.getX() + way.x * reach, hunter.getY(), hunter.getZ() + way.z * reach);
			for (int dy = 2; dy >= -3; dy--) {
				BlockPos feet = column.above(dy);
				Vec3 at = Vec3.atBottomCenterOf(feet);
				if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
					&& level.noCollision(mob, mob.getDimensions(mob.getPose()).makeBoundingBox(at)) && !level.containsAnyLiquid(mob.getBoundingBox().move(at.subtract(mob.position())))) {
					return at;
				}
			}
		}
		return null;
	}

	private static void puff(final ServerLevel level, final Vec3 at, final Mob mob) {
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + mob.getBbHeight() * 0.5, at.z, 18, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.35,
			mob.getBbWidth() * 0.4, 0.02);
	}

	/**
	 * A hunter took a blow ({@code damage} after armour, in a creature's measure): a bloodthirsty
	 * elite's opens a bleeding wound where it landed, and what it took heals the elite.
	 */
	public static void struck(final ServerPlayer player, final BodyService.Hit hit, final DamageSource source, final float damage) {
		if (!(source.getEntity() instanceof Mob mob) || !mob.isAlive() || !has(mob, Affix.BLOODTHIRSTY)) {
			return;
		}
		BodyPart part = hit.part() != null ? hit.part()
			: hit.legs() ? (player.getRandom().nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG) : BodyPart.TORSO;
		WoundService.inflict(player, part, false, true);
		mob.heal(damage);
		if (mob.level() instanceof ServerLevel level) {
			level.sendParticles(BLOOD, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
		}
	}

	private static void died(final LivingEntity entity, final DamageSource source) {
		if (!(entity instanceof Mob mob) || !isElite(mob) || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		ServerBossEvent bar = BARS.remove(mob.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
		List<Affix> affixes = affixes(mob);
		if (affixes.contains(Affix.VOLATILE)) {
			BURSTS.add(new Burst(level.dimension(), mob.position().add(0.0, mob.getBbHeight() * 0.4, 0.0), level.getGameTime() + FUSE));
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.2F, 0.6F);
		}
		ServerPlayer hunter = source.getEntity() instanceof ServerPlayer killer ? killer
			: mob.getLastHurtByPlayer() instanceof ServerPlayer striker ? striker : null;
		if (hunter == null) {
			return;
		}
		for (int i = 0; i < affixes.size(); i++) {
			if (mob.getRandom().nextFloat() < SPOILS) {
				mob.spawnAtLocation(level, spoil(mob.getRandom()));
			}
		}
		// and now and then a piece of gear, of the tier its affixes make it (I to III), any line's
		if (mob.getRandom().nextFloat() < GEAR) {
			List<net.minecraft.world.item.Item> pieces = MistinnItems.gearOfTier(Math.min(3, affixes.size()));
			mob.spawnAtLocation(level, new ItemStack(pieces.get(mob.getRandom().nextInt(pieces.size()))));
		}
		AchievementService.grant(hunter, AchievementService.ELITE_SLAIN);
		if (affixes.size() >= 3) {
			AchievementService.grant(hunter, AchievementService.ELITE_TRIPLE);
		}
	}

	/** Something an elite leaves: salt, or a hunter's medical supplies. */
	private static ItemStack spoil(final RandomSource random) {
		int roll = random.nextInt(12);
		if (roll < 4) {
			return new ItemStack(MistinnItems.SALT, 2 + random.nextInt(3));
		}
		if (roll < 6) {
			return new ItemStack(MistinnItems.TOURNIQUET);
		}
		if (roll < 8) {
			return new ItemStack(MistinnItems.SPLINT);
		}
		if (roll < 10) {
			return new ItemStack(MistinnItems.PAINKILLERS);
		}
		return new ItemStack(roll < 11 ? MistinnItems.SURGERY_KIT : MistinnItems.POTENT_INJECTOR);
	}

	/** Volatile elites' last breaths: salt gathering where they fell, then the burst. */
	private static void bursts(final MinecraftServer server) {
		Iterator<Burst> it = BURSTS.iterator();
		while (it.hasNext()) {
			Burst burst = it.next();
			ServerLevel level = server.getLevel(burst.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			Vec3 at = burst.at();
			long left = burst.when() - level.getGameTime();
			if (left > 0) {
				if (left % 5 == 0) {
					level.sendParticles(SALT, at.x, at.y, at.z, 6 + (int) (FUSE - left) / 3, 0.3, 0.3, 0.3, 0.0);
				}
				continue;
			}
			it.remove();
			DamageSource cause = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(SALT_BURST));
			level.explode(null, cause, SPARES_LOOT, at.x, at.y, at.z, BURST, false, Level.ExplosionInteraction.NONE);
			level.sendParticles(SALT, at.x, at.y, at.z, 40, 1.2, 0.8, 1.2, 0.0);
		}
	}

	/** The bar an elite's life shows on, if it has had one, for tests. */
	public static @Nullable ServerBossEvent bar(final Mob mob) {
		return BARS.get(mob.getUUID());
	}

	/** How many volatile elites are still to burst, for tests. */
	public static int bursting() {
		return BURSTS.size();
	}
}
