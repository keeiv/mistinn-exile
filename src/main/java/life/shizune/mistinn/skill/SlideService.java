package life.shizune.mistinn.skill;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.WoundService;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.stamina.RollService;
import life.shizune.mistinn.stamina.StaminaService;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * 滑鏟, the first major skill: running on the ground, crouch to drop feet first and slide about
 * five blocks, slowing as it goes. It costs stamina, breaks off a treatment, and sweeps the legs of
 * every hostile creature it runs into: a little damage and a lift off the ground, once each.
 * Unlike a roll it gives no dodge. The client moves the hunter (movement is the client's); the
 * server checks, charges, kicks and shows the slide to everyone watching.
 */
public final class SlideService {
	/** Blocks the slide carries the hunter. */
	public static final float DISTANCE = 5.0F;
	/** Ticks the slide travels. */
	public static final int TICKS = 10;
	/** Ticks until the hunter is back on their feet (the clip's length). */
	public static final int ANIM_TICKS = 14;
	public static final float COST = 18.0F;
	public static final int COOLDOWN = 24;
	/** Damage dealt to each creature swept. */
	public static final float KICK = 3.0F;
	/** Upward push given to each creature swept. */
	public static final double LIFT = 0.38;

	private record Slide(int left, float yaw, Set<Integer> struck) {
	}

	private static final Map<UUID, Slide> SLIDES = new HashMap<>();
	private static final Map<UUID, Integer> LAST = new HashMap<>();

	private SlideService() {
	}

	public static boolean sliding(final Player player) {
		return SLIDES.containsKey(player.getUUID());
	}

	/** Whether this hunter may slide at all: the skill learned and at least one leg alive. */
	public static boolean able(final Player player) {
		if (!StaminaService.applies(player) || !SkillService.get(player).has(SkillTree.ROOT)) {
			return false;
		}
		Body body = BodyService.get(player);
		return !(body.destroyed(BodyPart.LEFT_LEG) && body.destroyed(BodyPart.RIGHT_LEG));
	}

	/** The client asked to slide towards {@code yaw}; returns whether the slide happens. */
	public static boolean handle(final ServerPlayer player, final float yaw) {
		if (player.isDeadOrDying() || player.isPassenger() || !able(player) || !Float.isFinite(yaw)) {
			return false;
		}
		int now = player.level().getServer().getTickCount();
		if (now - LAST.getOrDefault(player.getUUID(), -COOLDOWN) < COOLDOWN || !StaminaService.has(player, COST)) {
			return false;
		}
		StaminaService.spend(player, COST);
		LAST.put(player.getUUID(), now);
		TreatmentService.interrupt(player);
		SLIDES.put(player.getUUID(), new Slide(TICKS, Mth.wrapDegrees(yaw), new HashSet<>()));
		player.level().playSound(null, player.blockPosition(), MistinnSounds.SLIDE, SoundSource.PLAYERS, 0.9F, 0.95F + player.getRandom().nextFloat() * 0.1F);
		Body body = BodyService.get(player);
		RollService.RollAnimPayload anim = new RollService.RollAnimPayload(player.getId(), RollService.Kind.SLIDE.ordinal(), yaw,
			WoundService.broken(player, BodyPart.LEFT_ARM) ? 0.0F : Math.min(1.0F, body.get(BodyPart.LEFT_ARM) / BodyService.max(player)),
			WoundService.broken(player, BodyPart.RIGHT_ARM) ? 0.0F : Math.min(1.0F, body.get(BodyPart.RIGHT_ARM) / BodyService.max(player)));
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(watcher, anim);
		}
		ServerPlayNetworking.send(player, anim);
		return true;
	}

	public static void tick(final MinecraftServer server) {
		for (Map.Entry<UUID, Slide> entry : Map.copyOf(SLIDES).entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Slide slide = entry.getValue();
			if (player == null || player.isDeadOrDying()) {
				SLIDES.remove(entry.getKey());
				continue;
			}
			sweep(player, slide);
			if (slide.left() <= 1) {
				SLIDES.remove(entry.getKey());
			} else {
				SLIDES.put(entry.getKey(), new Slide(slide.left() - 1, slide.yaw(), slide.struck()));
			}
		}
	}

	/** Every hostile creature at the hunter's feet, or just ahead of them, is swept once. */
	private static void sweep(final ServerPlayer player, final Slide slide) {
		ServerLevel level = player.level();
		float rad = slide.yaw() * Mth.DEG_TO_RAD;
		double ax = -Mth.sin(rad) * 0.6;
		double az = Mth.cos(rad) * 0.6;
		AABB feet = player.getBoundingBox();
		AABB reach = new AABB(feet.minX, feet.minY, feet.minZ, feet.maxX, feet.minY + 1.0, feet.maxZ).expandTowards(ax, 0.0, az).inflate(0.25, 0.0, 0.25);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, reach, e -> e instanceof Enemy && e.isAlive() && e != player)) {
			if (!slide.struck().add(target.getId())) {
				continue;
			}
			target.hurtServer(level, player.damageSources().playerAttack(player), KICK);
			life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.Achievement.SLIDE_KICK);
			// a lighthouse keeper is too heavy to be lifted off its feet
			if (!(target instanceof life.shizune.mistinn.keeper.Snuffer)) {
				target.push(0.0, LIFT, 0.0);
				target.syncVelocity = true;
			}
		}
	}

	/** Test hook: performs a slide request as if the client had sent it. */
	public static boolean request(final ServerPlayer player, final float yaw) {
		LAST.remove(player.getUUID());
		return handle(player, yaw);
	}

	public static void register() {
		ServerPlayerEvents.JOIN.register(SlideService::forget);
		ServerPlayerEvents.LEAVE.register(SlideService::forget);
		PayloadTypeRegistry.serverboundPlay().register(SlidePayload.TYPE, SlidePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SlidePayload.TYPE, (payload, context) -> handle(context.player(), payload.yaw()));
	}

	private static void forget(final ServerPlayer player) {
		LAST.remove(player.getUUID());
		SLIDES.remove(player.getUUID());
	}

	/** The client slid towards {@code yaw}. */
	public record SlidePayload(float yaw) implements CustomPacketPayload {
		public static final Type<SlidePayload> TYPE = new Type<>(Mistinn.id("slide"));
		public static final StreamCodec<ByteBuf, SlidePayload> CODEC = ByteBufCodecs.FLOAT.map(SlidePayload::new, SlidePayload::yaw);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
