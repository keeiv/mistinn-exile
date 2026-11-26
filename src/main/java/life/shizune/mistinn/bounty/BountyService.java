package life.shizune.mistinn.bounty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.elite.Affix;
import life.shizune.mistinn.elite.EliteService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * 霧中懸賞: now and then at dawn the anchor station posts a bounty: an elite of three affixes and
 * twice the life, somewhere out in the fog a long walk from a hunter. Every hunter's minimap shows
 * the place; the creature is there when a hunter comes near. Whoever brings it down is paid, those
 * with them a share, and it leaves a piece of good gear. Unclaimed, the bounty is withdrawn after
 * three days.
 */
public final class BountyService {
	/** The chance, each dawn with no bounty standing, that one is posted. */
	public static final float CHANCE = 0.35F;
	/** No bounty before the world's second day. */
	public static final long FIRST_DAY = 2;
	/** How far from a hunter the creature is put, at the least and the most. */
	public static final int NEAR = 150;
	public static final int FAR = 300;
	/** A hunter this near the place brings the creature out. */
	public static final double WAKE = 48.0;
	/** How long a bounty stands. */
	public static final long LASTS = 72000L;
	/** What it pays its killer, and every other hunter this near. */
	public static final int REWARD = 800;
	public static final int SHARE = 400;
	public static final double SHARE_RANGE = 32.0;
	/** The kinds a bounty may be: fighters that walk the land. */
	private static final List<EntityType<? extends Mob>> KINDS = List.of(net.minecraft.world.entity.EntityTypes.ZOMBIE, net.minecraft.world.entity.EntityTypes.HUSK,
		net.minecraft.world.entity.EntityTypes.SKELETON, net.minecraft.world.entity.EntityTypes.STRAY, net.minecraft.world.entity.EntityTypes.SPIDER);
	private static final Identifier LIFE = Mistinn.id("bounty_life");

	/** A bounty standing: when posted, where, what (its kind and affixes), and the creature once it is out. */
	public record Bounty(long posted, BlockPos at, String kind, List<String> affixes, Optional<UUID> creature) {
		public static final Codec<Bounty> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("posted").forGetter(Bounty::posted),
			BlockPos.CODEC.fieldOf("at").forGetter(Bounty::at),
			Codec.STRING.fieldOf("kind").forGetter(Bounty::kind),
			Codec.STRING.listOf().fieldOf("affixes").forGetter(Bounty::affixes),
			UUIDUtil.CODEC.optionalFieldOf("creature").forGetter(Bounty::creature)
		).apply(i, Bounty::new));
	}

	/** The world's bounty, if one stands, and the last day weighed for one. */
	public record Board(long day, Optional<Bounty> bounty) {
		public static final Board NONE = new Board(-1L, Optional.empty());
		public static final Codec<Board> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Board::day),
			Bounty.CODEC.optionalFieldOf("bounty").forGetter(Board::bounty)
		).apply(i, Board::new));
	}

	public static final AttachmentType<Board> BOARD = AttachmentRegistry.<Board>builder()
		.persistent(Board.CODEC)
		.buildAndRegister(Mistinn.id("bounty_board"));
	/** Where the bounty is, on every hunter in the overworld while it stands: their minimap shows it. */
	public static final AttachmentType<BlockPos> MARK = AttachmentRegistry.<BlockPos>builder()
		.syncWith(BlockPos.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("bounty_mark"));
	/** On the creature itself. */
	public static final AttachmentType<Boolean> WANTED = AttachmentRegistry.<Boolean>builder()
		.persistent(Codec.BOOL)
		.buildAndRegister(Mistinn.id("bounty_wanted"));

	/** For tests: one is posted as soon as it is light, whatever the odds, the day and the world's age. */
	public static boolean certain;

	private BountyService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BountyService::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(BountyService::died);
	}

	public static Board board(final MinecraftServer server) {
		return server.overworld().getAttachedOrElse(BOARD, Board.NONE);
	}

	private static void set(final MinecraftServer server, final Board board) {
		server.overworld().setAttached(BOARD, board);
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		Board board = board(server);
		long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		if (!level.isDarkOutside() && (board.day() != day || certain)) {
			// each morning is weighed once
			board = new Board(day, board.bounty());
			set(server, board);
			if (board.bounty().isEmpty() && (certain || allowed(level, day) && level.getRandom().nextFloat() < CHANCE)) {
				certain = false;
				ServerPlayer near = anyone(level);
				if (near != null) {
					post(level, spot(level, near));
				}
			}
		}
		board = board(server);
		Bounty bounty = board.bounty().orElse(null);
		if (bounty != null) {
			if (level.getGameTime() - bounty.posted() > LASTS) {
				withdraw(level, bounty);
				bounty = null;
			} else if (bounty.creature().isEmpty() && someoneNear(level, bounty.at())) {
				bounty = rouse(level, bounty);
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			BlockPos mark = bounty != null && player.level() == level ? bounty.at() : null;
			if (mark == null) {
				player.removeAttached(MARK);
			} else if (!mark.equals(player.getAttached(MARK))) {
				player.setAttached(MARK, mark);
			}
		}
	}

	private static boolean allowed(final ServerLevel level, final long day) {
		return day >= FIRST_DAY && level.getDifficulty() != Difficulty.PEACEFUL && WorldMode.of(level.getServer()) != WorldMode.TRAINING;
	}

	private static @Nullable ServerPlayer anyone(final ServerLevel level) {
		List<ServerPlayer> hunters = new ArrayList<>(level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList());
		return hunters.isEmpty() ? null : hunters.get(level.getRandom().nextInt(hunters.size()));
	}

	/** Somewhere a long walk from the hunter. */
	private static BlockPos spot(final ServerLevel level, final ServerPlayer hunter) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		int reach = NEAR + random.nextInt(FAR - NEAR + 1);
		return BlockPos.containing(hunter.getX() + Math.cos(angle) * reach, hunter.getY(), hunter.getZ() + Math.sin(angle) * reach);
	}

	/** Posts a bounty at {@code at}: what it is, told to every hunter with the way to it. */
	public static Bounty post(final ServerLevel level, final BlockPos at) {
		RandomSource random = level.getRandom();
		EntityType<? extends Mob> kind = KINDS.get(random.nextInt(KINDS.size()));
		List<Affix> open = new ArrayList<>(List.of(Affix.values()));
		List<String> affixes = new ArrayList<>();
		while (affixes.size() < 3) {
			affixes.add(open.remove(random.nextInt(open.size())).id);
		}
		affixes.sort(java.util.Comparator.comparingInt(id -> Affix.byId(id).ordinal()));
		Bounty bounty = new Bounty(level.getGameTime(), at, BuiltInRegistries.ENTITY_TYPE.getKey(kind).toString(), List.copyOf(affixes), Optional.empty());
		set(level.getServer(), new Board(board(level.getServer()).day(), Optional.of(bounty)));
		Component name = name(bounty);
		for (ServerPlayer player : level.players()) {
			int distance = (int) Math.round(Math.hypot(at.getX() - player.getX(), at.getZ() - player.getZ()));
			player.sendSystemMessage(Component.translatable("message.mistinn.bounty.posted", name, direction(player, at), distance, REWARD)
				.withStyle(ChatFormatting.GOLD));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.0F, 0.7F);
		}
		return bounty;
	}

	/** 「迅捷・嗜血・霧行 屍殼」 under the bounty's own heading. */
	public static Component name(final Bounty bounty) {
		List<Affix> affixes = bounty.affixes().stream().map(Affix::byId).filter(a -> a != null).toList();
		EntityType<?> kind = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(bounty.kind()));
		return Component.translatable("bounty.mistinn.name", EliteService.name(kind, affixes)).withStyle(ChatFormatting.GOLD);
	}

	/** Which way the place lies from the hunter, as a compass point. */
	public static Component direction(final ServerPlayer player, final BlockPos at) {
		double angle = Math.toDegrees(Math.atan2(at.getX() - player.getX(), -(at.getZ() - player.getZ())));
		int point = Math.floorMod(Math.round((float) angle / 45.0F), 8);
		return Component.translatable("direction.mistinn." + new String[] {"n", "ne", "e", "se", "s", "sw", "w", "nw"}[point]);
	}

	private static boolean someoneNear(final ServerLevel level, final BlockPos at) {
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator() && Math.hypot(player.getX() - at.getX() - 0.5, player.getZ() - at.getZ() - 0.5) <= WAKE
				&& level.hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
				return true;
			}
		}
		return false;
	}

	/** A hunter has come near: the creature is there, at the place, on the ground. */
	private static Bounty rouse(final ServerLevel level, final Bounty bounty) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(bounty.kind()));
		if (!(type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob)) {
			return bounty;
		}
		BlockPos at = bounty.at();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		mob.snapTo(at.getX() + 0.5, y, at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		EliteService.make(mob, bounty.affixes().stream().map(Affix::byId).filter(a -> a != null).toList());
		AttributeInstance life = mob.getAttribute(Attributes.MAX_HEALTH);
		if (life != null && !life.hasModifier(LIFE)) {
			life.addPermanentModifier(new AttributeModifier(LIFE, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		mob.setHealth(mob.getMaxHealth());
		mob.setCustomName(name(bounty));
		mob.setPersistenceRequired();
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
		mob.setAttached(WANTED, true);
		level.addFreshEntity(mob);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(mob) < WAKE * WAKE * 4) {
				player.sendSystemMessage(Component.translatable("message.mistinn.bounty.found", name(bounty)).withStyle(ChatFormatting.GOLD));
			}
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 0.8F);
		Bounty out = new Bounty(bounty.posted(), bounty.at(), bounty.kind(), bounty.affixes(), Optional.of(mob.getUUID()));
		set(level.getServer(), new Board(board(level.getServer()).day(), Optional.of(out)));
		return out;
	}

	private static void withdraw(final ServerLevel level, final Bounty bounty) {
		bounty.creature().map(level::getEntity).ifPresent(Entity::discard);
		set(level.getServer(), new Board(board(level.getServer()).day(), Optional.empty()));
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(Component.translatable("message.mistinn.bounty.withdrawn", name(bounty)));
		}
	}

	private static void died(final LivingEntity entity, final DamageSource source) {
		if (!entity.getAttachedOrElse(WANTED, false) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Board board = board(level.getServer());
		Bounty bounty = board.bounty().orElse(null);
		set(level.getServer(), new Board(board.day(), Optional.empty()));
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer player ? player
			: entity.getLastHurtByPlayer() instanceof ServerPlayer striker ? striker : null;
		Component name = bounty != null ? name(bounty) : entity.getDisplayName();
		if (killer == null) {
			for (ServerPlayer player : level.players()) {
				player.sendSystemMessage(Component.translatable("message.mistinn.bounty.lost", name));
			}
			return;
		}
		SaltService.add(killer, REWARD);
		for (ServerPlayer player : level.players()) {
			if (player != killer && player.isAlive() && player.distanceTo(entity) <= SHARE_RANGE) {
				SaltService.add(player, SHARE);
			}
			player.sendSystemMessage(Component.translatable("message.mistinn.bounty.claimed", killer.getDisplayName(), name, REWARD).withStyle(ChatFormatting.GOLD));
		}
		AchievementService.grant(killer, Achievement.BOUNTY);
		// and good gear: tier III or IV, any line's
		List<net.minecraft.world.item.Item> pieces = MistinnItems.gearOfTier(3 + level.getRandom().nextInt(2));
		entity.spawnAtLocation(level, new ItemStack(pieces.get(level.getRandom().nextInt(pieces.size()))));
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.5F, 1.0F);
	}

	/** Moves a standing bounty's posting time, for tests. */
	public static void age(final MinecraftServer server, final long by) {
		Board board = board(server);
		board.bounty().ifPresent(b -> set(server, new Board(board.day(), Optional.of(new Bounty(b.posted() - by, b.at(), b.kind(), b.affixes(), b.creature())))));
	}
}
