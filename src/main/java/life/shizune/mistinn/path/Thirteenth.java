package life.shizune.mistinn.path;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.ledger.LedgerService;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.story.StoryScript;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * 十三人斬: the way of a hunter who let all twelve fall, and who in their other worlds sent the salt up and never once
 * burned it. They are not let stop. The twelve are stitched into one thing, the thirteenth, and the hunter walks on as
 * that: in the best of everything, hard to hurt, screaming when it is hurt. Hunter No. 3412, sent up the highland by
 * the inn and the tribe, comes to put it down, and does. Its death is the end: a picture, the card of the hunter going
 * home, and the hidden deed.
 */
public final class Thirteenth {
	/** The ledger's tone bit for a hunter who, in their other worlds, sent the salt up and never burned it. */
	public static final int CRUEL_STORY = 8;
	/** What a blow takes from the thirteenth, of what it would take; the armour it wears under its skin. */
	public static final float TAKEN = 0.12F;
	/** Ticks after the stitching before the hunter is seen coming. */
	public static final int COMING = 160;
	/** Whether this hunter walks as the thirteenth (every client knows, for its skin). */
	public static final AttachmentType<Boolean> STITCHED = AttachmentRegistry.<Boolean>builder()
		.persistent(com.mojang.serialization.Codec.BOOL)
		.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
		.buildAndRegister(life.shizune.mistinn.Mistinn.id("stitched"));

	/** When the hunter comes (game time), or -1 when no one is coming. */
	private static long comesAt = -1L;
	/** The last scream of each thirteenth, so it does not drown itself. */
	private static final Map<UUID, Long> SCREAMED = new HashMap<>();
	/** Hunters whose thirteenth fell, to be told how it ended once they wake (not over its face). */
	private static final java.util.Set<UUID> WAKING = new java.util.HashSet<>();
	/** The thirteenths the hunter's last blow has reached: nothing else lets them fall. */
	private static final java.util.Set<UUID> FINISHED = new java.util.HashSet<>();

	private Thirteenth() {
	}

	public static boolean stitched(final net.minecraft.world.entity.player.Player player) {
		return player.getAttachedOrElse(STITCHED, false);
	}

	/** What a blow takes from this hunter, of what it would otherwise. */
	public static float taken(final net.minecraft.world.entity.player.Player player) {
		return stitched(player) ? TAKEN : 1.0F;
	}

	/** How much of the thirteenth's strength is left, 0 to 1. */
	public static float strength(final ServerPlayer player) {
		return BodyService.get(player).total() / (6.0F * BodyService.max(player));
	}

	/** Whether the thirteenth has had enough: little of its strength left (what would kill it brings the last blow on too). */
	public static boolean spent(final ServerPlayer player) {
		return strength(player) <= RustApostle.SPENT;
	}

	/** The hunter's last blow reaches it: this time it may fall. */
	public static void finishing(final ServerPlayer player) {
		FINISHED.add(player.getUUID());
	}

	/** Whether the twelfth falling goes on as the thirteenth: any hunter here sent the salt up and never burned it. */
	public static boolean due(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((LedgerService.tone(player) & CRUEL_STORY) != 0) {
				return true;
			}
		}
		return false;
	}

	/** The twelve are stitched into one, and every hunter here walks on as it, from {@code at}. */
	public static void begin(final MinecraftServer server, final BlockPos at) {
		ServerLevel level = server.overworld();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.setGameMode(GameType.SURVIVAL);
			player.setAttached(STITCHED, true);
			player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), player.getYRot(), 0.0F, true);
			// the best of everything, on a body that is not anyone's
			player.getInventory().clearContent();
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(MistinnItems.HELMETS.getLast()));
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(MistinnItems.ARMORS.getLast()));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(MistinnItems.LEGGINGS.getLast()));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(MistinnItems.BOOTS.getLast()));
			player.getInventory().setItem(0, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.CLEAVE).get(WeaponMaterial.NETHERITE)));
			player.getInventory().setItem(1, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.NETHERITE)));
			player.getInventory().setSelectedSlot(0);
			BodyService.restoreAll(player);
			player.setHealth(player.getMaxHealth());
			player.getFoodData().setFoodLevel(20);
			ServerPlayNetworking.send(player, new StoryService.CardPayload(StoryScript.PATH_STITCHED.id()));
		}
		comesAt = level.getGameTime() + COMING;
	}

	/** The hunter, sent up the highland, comes at the thirteenth. */
	private static void comes(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer target = null;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (stitched(player) && player.isAlive()) {
				target = player;
			}
		}
		if (target == null) {
			return;
		}
		// out of the fog: some way off, toward the highland's edge or wherever there is ground
		Vec3 from = target.position().add(target.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(-26.0));
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(from));
		RustApostle hunter = MistinnEntities.RUST_APOSTLE.create(level, EntitySpawnReason.EVENT);
		if (hunter == null) {
			return;
		}
		hunter.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 0.0F, 0.0F);
		hunter.hunt(target);
		level.addFreshEntity(hunter);
		hunter.say("apostle_intro");
	}

	/** Test hook: the thirteenth begins here and the hunter comes at once. */
	public static RustApostle beginNow(final ServerPlayer player) {
		begin(player.level().getServer(), player.blockPosition());
		comesAt = -1L;
		ServerLevel level = player.level();
		RustApostle hunter = MistinnEntities.RUST_APOSTLE.create(level, EntitySpawnReason.EVENT);
		Vec3 at = player.position().add(0.0, 0.0, 9.0);
		hunter.snapTo(at.x, at.y, at.z, 180.0F, 0.0F);
		hunter.hunt(player);
		level.addFreshEntity(hunter);
		hunter.say("apostle_intro");
		return hunter;
	}

	/** The thirteenth is down: the end. */
	private static void ends(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		FINISHED.remove(player.getUUID());
		for (ServerPlayer each : server.getPlayerList().getPlayers()) {
			each.removeAttached(STITCHED);
			LedgerService.tell(each, "path.thirteen", true);
			if (each == player) {
				// the one it was wakes first, and is told then
				WAKING.add(each.getUUID());
			} else {
				told(each);
			}
		}
		// the hunter goes home
		for (RustApostle hunter : server.overworld().getEntitiesOfClass(RustApostle.class, player.getBoundingBox().inflate(96.0))) {
			hunter.goHome();
		}
	}

	/** How it ended: the deed, and the card of the hunter going home. */
	private static void told(final ServerPlayer player) {
		AchievementService.grant(player, Achievement.THIRTEEN_SLAIN);
		ServerPlayNetworking.send(player, new StoryService.CardPayload(StoryScript.THIRTEEN_END.id()));
	}

	public static void register() {
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((old, woke, alive) -> {
			if (WAKING.remove(woke.getUUID())) {
				told(woke);
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (comesAt >= 0L && server.overworld().getGameTime() >= comesAt) {
				comesAt = -1L;
				comes(server);
			}
		});
		// it does not fall to anything but the hunter's last blow: what would have killed it brings that blow on
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			// nor does the hunter fall, to anything short of the world itself
			if (entity instanceof RustApostle hunter && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				hunter.setHealth(hunter.getMaxHealth() * RustApostle.FLOOR);
				return false;
			}
			if (!(entity instanceof ServerPlayer player) || !stitched(player) || FINISHED.contains(player.getUUID())) {
				return true;
			}
			java.util.List<RustApostle> hunters = player.level().getEntitiesOfClass(RustApostle.class, player.getBoundingBox().inflate(128.0),
				hunter -> hunter.hunts(player));
			if (hunters.isEmpty()) {
				return true;
			}
			player.setHealth(1.0F);
			hunters.getFirst().finishNow();
			return false;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && stitched(player)) {
				ends(player);
			}
		});
		// it screams when it is hurt: not one voice
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && stitched(player) && taken > 0.0F) {
				long now = player.level().getGameTime();
				Long last = SCREAMED.get(player.getUUID());
				if (last == null || now - last >= 12) {
					SCREAMED.put(player.getUUID(), now);
					ServerLevel level = player.level();
					float pitch = 0.5F + player.getRandom().nextFloat() * 0.15F;
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GHAST_SCREAM, SoundSource.PLAYERS, 1.3F, pitch);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 1.0F, pitch - 0.1F);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.5F, 0.5F);
				}
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			comesAt = -1L;
			SCREAMED.clear();
			FINISHED.clear();
			WAKING.clear();
		});
	}
}
