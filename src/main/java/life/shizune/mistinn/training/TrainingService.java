package life.shizune.mistinn.training;

import io.netty.buffer.ByteBuf;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.WoundService;
import life.shizune.mistinn.body.Wounds;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.keeper.LampStandBlock;
import life.shizune.mistinn.medical.Treatment;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.stamina.RollService;
import life.shizune.mistinn.story.RadioBlock;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.story.StoryStep;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** The integrated-server owner's course, from the first radio call to the launch seat. */
public final class TrainingService {
	public static final AttachmentType<TrainingLesson> LESSON = AttachmentRegistry.<TrainingLesson>builder()
		.persistent(TrainingLesson.CODEC)
		.syncWith(TrainingLesson.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("training_lesson"));

	private static final BlockPos ENTRY = new BlockPos(0, 65, 10);
	private static final BlockPos KIT = new BlockPos(-3, 65, -4);
	private static final BlockPos SEAT = TrainingLesson.DEPLOY.target;
	private static final BlockPos BOARD_BUTTON = new BlockPos(0, 66, -131);
	private static final BlockPos[] LAMPS = {
		new BlockPos(-4, 65, -103), new BlockPos(4, 65, -103),
		new BlockPos(-4, 65, -113), new BlockPos(4, 65, -113)
	};
	private static final String STRIKE_TARGET = "mistinn.training_target";
	private static final String KEEPER_TARGET = "mistinn.training_keeper";
	private static final String POD_SEAT = "mistinn.training_seat";
	private static final int PRACTICE_SALT = 20;
	private static final int COURSE_REACH = 8;

	private TrainingService() {
	}

	public static void init() {
		TrainingGround.init();
		PayloadTypeRegistry.serverboundPlay().register(DeployPayload.TYPE, DeployPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DeployPayload.TYPE, (payload, context) ->
			finish(context.player(), payload.story() ? WorldMode.STORY : WorldMode.FREE));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel range = server.getLevel(TrainingGround.KEY);
			if (range != null && WorldMode.of(server) == WorldMode.TRAINING) {
				TrainingGround.ensureBuilt(range);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(TrainingService::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(TrainingService::fallen);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.dimension().equals(TrainingGround.KEY) || !hit.getBlockPos().equals(BOARD_BUTTON)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer hunter && at(hunter, TrainingLesson.BOARD)) {
				board(hunter);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** The seat is the server's second check behind a client choosing its destination. */
	public record DeployPayload(boolean story) implements CustomPacketPayload {
		public static final Type<DeployPayload> TYPE = new Type<>(Mistinn.id("training_deploy"));
		public static final StreamCodec<ByteBuf, DeployPayload> CODEC = ByteBufCodecs.BOOL.map(DeployPayload::new, DeployPayload::story);

		@Override
		public Type<DeployPayload> type() {
			return TYPE;
		}
	}

	private static boolean owner(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return server.isSingleplayerOwner(player.nameAndId()) && WorldMode.of(server) == WorldMode.TRAINING;
	}

	private static boolean at(final ServerPlayer player, final TrainingLesson lesson) {
		return player.getAttached(LESSON) == lesson && player.isAlive() && owner(player)
			&& player.level().dimension().equals(TrainingGround.KEY)
			&& near(player, lesson.target, COURSE_REACH);
	}

	private static boolean near(final ServerPlayer player, final BlockPos pos, final double radius) {
		return player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= radius * radius;
	}

	private static void announce(final ServerPlayer player, final TrainingLesson next) {
		player.setAttached(LESSON, next);
		if (next == TrainingLesson.CRAFT) {
			player.awardRecipesByKey(java.util.List.of(ResourceKey.create(Registries.RECIPE, Mistinn.id("ore_bell"))));
		}
	}

	private static void tick(final MinecraftServer server) {
		if (WorldMode.of(server) != WorldMode.TRAINING) {
			return;
		}
		ServerLevel range = server.getLevel(TrainingGround.KEY);
		if (range == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && server.isSingleplayerOwner(player.nameAndId())) {
				train(player, range);
			}
		}
	}

	private static void train(final ServerPlayer player, final ServerLevel range) {
		TrainingLesson lesson = player.getAttached(LESSON);
		if (player.gameMode() != GameType.SURVIVAL) {
			// Training saves made before the interaction change still have Adventure on the player.
			player.setGameMode(GameType.SURVIVAL);
		}
		if (player.level() != range || player.getY() < 61.0) {
			GlobalPos bell = player.getAttached(MistinnAttachments.BELL);
			BlockPos checkpoint = bell == null ? ENTRY : bell.pos().south(2);
			if (!move(player, range, lesson == TrainingLesson.DEPLOY ? SEAT : checkpoint)) {
				return;
			}
		}
		if (lesson == null) {
			// Send the first objective after the client enters the range, where its new player receives it.
			lesson = TrainingLesson.ARRIVAL;
			announce(player, lesson);
		}
		if (lesson == TrainingLesson.DEPLOY) {
			if (!inSeat(player)) {
				board(player);
			}
			return;
		}
		if (near(player, lesson.target, COURSE_REACH)) {
			runLesson(player, range, lesson);
		}
	}

	private static void runLesson(final ServerPlayer player, final ServerLevel level, final TrainingLesson lesson) {
		switch (lesson) {
			case ARRIVAL -> {
				if (near(player, lesson.target, 2.5)) {
					announce(player, TrainingLesson.GEAR);
				}
			}
			case GEAR, PACK -> equipment(player, level, lesson);
			case SPRINT -> {
				if (player.isSprinting()) {
					announce(player, TrainingLesson.ROLL);
				}
			}
			case ROLL -> {
				if (RollService.dodging(player)) {
					announce(player, TrainingLesson.STRIKE);
				}
			}
			case STRIKE -> {
				supply(player, MistinnItems.ANCHOR_BLADE, 1);
				target(level, lesson.target, STRIKE_TARGET, false);
			}
			case SALT -> {
				if (SaltService.get(player) >= PRACTICE_SALT) {
					announce(player, TrainingLesson.TOURNIQUET);
				} else {
					supply(player, MistinnItems.SALT, 1);
				}
			}
			case TOURNIQUET, SPLINT, PAINKILLER, SURGERY, INJECTOR, REPAIR -> infirmary(player, lesson);
			case CRAFT -> {
				if (has(player, MistinnItems.ORE_BELL, 1)) {
					announce(player, TrainingLesson.BELL);
				} else if (player.containerMenu == player.inventoryMenu) {
					supply(player, Items.RAW_GOLD, 5);
					supply(player, Items.IRON_CHAIN, 1);
				}
			}
			case BELL -> {
				GlobalPos bell = player.getAttached(MistinnAttachments.BELL);
				if (bell != null && bell.pos().equals(lesson.target)) {
					announce(player, TrainingLesson.FALL);
				}
			}
			case FALL -> {
				if (near(player, lesson.target, 0.8)) {
					SaltService.set(player, Math.max(PRACTICE_SALT, SaltService.get(player)));
					player.kill(level);
				}
			}
			case RECOVER -> {
				if (!player.hasAttached(MistinnAttachments.DEATH_MARK)) {
					announce(player, TrainingLesson.RADIO);
				}
			}
			case RADIO -> {
				var radio = level.getBlockState(lesson.target);
				if (radio.is(MistinnBlocks.RADIO) && radio.getValue(RadioBlock.LIT)) {
					announce(player, TrainingLesson.LAMPS);
				} else {
					supply(player, Items.REDSTONE, StoryStep.RADIO_PARTS);
				}
			}
			case LAMPS -> {
				if (allLampsLit(level)) {
					announce(player, TrainingLesson.KEEPER);
				}
			}
			case KEEPER -> {
				supply(player, MistinnItems.ANCHOR_BLADE, 1);
				target(level, lesson.target, KEEPER_TARGET, true);
			}
			case REPORT -> supply(player, MistinnItems.RUST_GOD_SALT, 1);
			case BOARD, DEPLOY -> {
			}
		}
	}

	private static void equipment(final ServerPlayer player, final ServerLevel level, final TrainingLesson lesson) {
		GridInventory grid = GridService.get(player);
		Item armor = MistinnItems.ARMORS.get(3);
		Item backpack = MistinnItems.BACKPACKS.get(2);
		Item rig = MistinnItems.RIGS.getFirst();
		Item mask = MistinnItems.MASKS.getFirst();
		if (level.getBlockEntity(KIT) instanceof Container chest) {
			stock(player, chest, 0, armor);
			stock(player, chest, 2, backpack);
			stock(player, chest, 4, rig);
			stock(player, chest, 6, mask);
			stock(player, chest, 8, MistinnItems.SPLINT);
		}
		if (grid.backpack().isEmpty() || grid.rig().isEmpty() || grid.mask().isEmpty()
			|| player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
			return;
		}
		if (lesson == TrainingLesson.GEAR) {
			announce(player, TrainingLesson.PACK);
		} else if (grid.items(Area.BACKPACK).stream().anyMatch(placed -> placed.rotated() && placed.stack().is(MistinnItems.SPLINT))) {
			announce(player, TrainingLesson.SPRINT);
		}
	}

	private static void stock(final ServerPlayer player, final Container chest, final int slot, final Item item) {
		if (chest.getItem(slot).isEmpty() && !chest.hasAnyOf(Set.of(item)) && !has(player, item, 1)) {
			chest.setItem(slot, new ItemStack(item));
		}
	}

	private static boolean has(final ServerPlayer player, final Item item, final int count) {
		Inventory inventory = player.getInventory();
		ItemStack carried = player.containerMenu.getCarried();
		int found = carried.is(item) ? carried.getCount() : 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item)) {
				found += stack.getCount();
			}
		}
		GridInventory grid = GridService.get(player);
		if (grid.backpack().is(item) || grid.rig().is(item) || grid.mask().is(item)) {
			return true;
		}
		for (Area area : Area.values()) {
			for (var placed : grid.items(area)) {
				if (placed.stack().is(item)) {
					found += placed.stack().getCount();
				}
			}
		}
		return found >= count;
	}

	private static void supply(final ServerPlayer player, final Item item, final int count) {
		if (has(player, item, count)) {
			return;
		}
		Inventory inventory = player.getInventory();
		for (int slot = 1; slot < Inventory.getSelectionSize(); slot++) {
			if (inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, new ItemStack(item, count));
				return;
			}
		}
	}

	private static void infirmary(final ServerPlayer player, final TrainingLesson lesson) {
		Wounds wounds = WoundService.get(player);
		var arm = wounds.get(BodyPart.LEFT_ARM);
		var leg = wounds.get(BodyPart.LEFT_LEG);
		switch (lesson) {
			case TOURNIQUET -> {
				if (!arm.bleeding() && !arm.tied()) {
					WoundService.set(player, wounds.with(BodyPart.LEFT_ARM, arm.bleeding(true)));
				}
				supply(player, MistinnItems.TOURNIQUET, 1);
			}
			case SPLINT, SURGERY -> {
				if (!leg.fractured()) {
					WoundService.set(player, wounds.with(BodyPart.LEFT_LEG, leg.fractured(true)));
				}
				supply(player, lesson == TrainingLesson.SPLINT ? MistinnItems.SPLINT : MistinnItems.SURGERY_KIT, 1);
			}
			case PAINKILLER -> supply(player, MistinnItems.PAINKILLERS, 1);
			case INJECTOR -> {
				Body body = BodyService.get(player);
				if (body.total() == Body.FULL.total()) {
					BodyService.set(player, body.with(BodyPart.LEFT_ARM, BodyPart.MAX / 2.0F));
				}
				supply(player, MistinnItems.POTENT_INJECTOR, 1);
			}
			case REPAIR -> {
				ItemStack armor = player.getItemBySlot(EquipmentSlot.CHEST);
				if (armor.isEmpty()) {
					armor = new ItemStack(MistinnItems.ARMORS.get(3));
					player.setItemSlot(EquipmentSlot.CHEST, armor);
				}
				if (!armor.isDamaged()) {
					armor.setDamageValue(armor.getMaxDamage() / 3);
				}
				supply(player, MistinnItems.ARMOR_REPAIR_KITS.getFirst(), 1);
			}
			default -> {
			}
		}
	}

	/** Credits a completed timed treatment, not a click that started or interrupted one. */
	public static void treated(final ServerPlayer player, final Treatment treatment) {
		TrainingLesson current = switch (treatment) {
			case TOURNIQUET -> TrainingLesson.TOURNIQUET;
			case SPLINT -> TrainingLesson.SPLINT;
			case PILLS -> TrainingLesson.PAINKILLER;
			case SURGERY -> TrainingLesson.SURGERY;
			case INJECT -> TrainingLesson.INJECTOR;
			case ARMOR_REPAIR -> TrainingLesson.REPAIR;
			case HELMET_REPAIR -> null;
		};
		if (current != null && at(player, current)) {
			announce(player, TrainingLesson.values()[current.ordinal() + 1]);
		}
	}

	private static boolean allLampsLit(final ServerLevel level) {
		for (BlockPos pos : LAMPS) {
			var lamp = level.getBlockState(pos);
			if (!lamp.is(MistinnBlocks.LAMP_STAND) || !lamp.getValue(LampStandBlock.LIT)) {
				return false;
			}
		}
		return true;
	}

	private static void target(final ServerLevel level, final BlockPos pos, final String tag, final boolean keeper) {
		AABB room = new AABB(pos).inflate(20.0);
		if (!level.getEntitiesOfClass(Mob.class, room, mob -> mob.entityTags().contains(tag)).isEmpty()) {
			return;
		}
		Mob target = keeper ? MistinnEntities.SNUFFER.create(level, EntitySpawnReason.STRUCTURE)
			: EntityTypes.HUSK.create(level, EntitySpawnReason.STRUCTURE);
		if (target != null) {
			target.addTag(tag);
			target.setCustomName(Component.translatable(keeper ? "training.mistinn.keeper" : "training.mistinn.target"));
			target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(keeper ? 42.0 : 12.0);
			target.setHealth(target.getMaxHealth());
			target.setPersistenceRequired();
			target.setNoAi(!keeper);
			target.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
			level.addFreshEntity(target);
		}
	}

	private static void fallen(final LivingEntity entity, final net.minecraft.world.damagesource.DamageSource cause) {
		if (!entity.level().dimension().equals(TrainingGround.KEY)) {
			return;
		}
		if (entity instanceof ServerPlayer hunter && hunter.getAttached(LESSON) == TrainingLesson.FALL && owner(hunter)) {
			announce(hunter, TrainingLesson.RECOVER);
		} else if (cause.getEntity() instanceof ServerPlayer hunter) {
			if (entity.entityTags().contains(STRIKE_TARGET) && at(hunter, TrainingLesson.STRIKE)) {
				announce(hunter, TrainingLesson.SALT);
			} else if (entity.entityTags().contains(KEEPER_TARGET) && at(hunter, TrainingLesson.KEEPER)) {
				announce(hunter, TrainingLesson.REPORT);
			}
		}
	}

	/** The report console accepts the exercise trophy; the repair console still uses its own logic. */
	public static boolean deliver(final ServerPlayer player, final BlockPos radio) {
		if (at(player, TrainingLesson.REPORT) && radio.equals(TrainingLesson.REPORT.target)) {
			announce(player, TrainingLesson.BOARD);
			return true;
		}
		return false;
	}

	private static boolean inSeat(final ServerPlayer player) {
		Entity vehicle = player.getVehicle();
		return vehicle != null && vehicle.entityTags().contains(POD_SEAT);
	}

	private static void board(final ServerPlayer player) {
		ServerLevel level = player.level();
		ArmorStand seat = level.getEntitiesOfClass(ArmorStand.class, new AABB(SEAT).inflate(1.0),
			stand -> stand.entityTags().contains(POD_SEAT)).stream().findFirst().orElseGet(() -> createSeat(level));
		if (seat == null || !move(player, level, SEAT) || !player.startRiding(seat, true, true)) {
			return;
		}
		if (player.getAttached(LESSON) != TrainingLesson.DEPLOY) {
			level.playSound(null, SEAT, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.7F);
			announce(player, TrainingLesson.DEPLOY);
		}
	}

	private static @Nullable ArmorStand createSeat(final ServerLevel level) {
		ArmorStand seat = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.STRUCTURE);
		if (seat != null) {
			seat.addTag(POD_SEAT);
			seat.setInvisible(true);
			seat.setNoGravity(true);
			seat.noPhysics = true;
			seat.snapTo(SEAT.getX() + 0.5, SEAT.getY() - 0.2, SEAT.getZ() + 0.5, 180.0F, 0.0F);
			level.addFreshEntity(seat);
		}
		return seat;
	}

	private static boolean move(final ServerPlayer player, final ServerLevel destination, final BlockPos pos) {
		return player.teleportTo(destination, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), 180.0F, 0.0F, true);
	}

	private static void finish(final ServerPlayer player, final WorldMode next) {
		if (!at(player, TrainingLesson.DEPLOY) || !inSeat(player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = server.overworld();
		Entity seat = player.getVehicle();
		player.stopRiding();
		if (!move(player, overworld, overworld.getRespawnData().pos())) {
			return;
		}
		seat.discard();
		player.removeAttached(LESSON);
		player.getInventory().clearContent();
		player.containerMenu.setCarried(ItemStack.EMPTY);
		player.getInventory().setItem(0, new ItemStack(MistinnItems.HUNTER_GUIDE));
		GridService.set(player, GridInventory.EMPTY);
		player.removeAttached(MistinnAttachments.BELL);
		player.removeAttached(MistinnAttachments.DEATH_MARK);
		player.removeAttached(MistinnAttachments.PAINKILLER);
		SaltService.set(player, 0);
		TreatmentService.reset(player);
		WoundService.clearAll(player);
		BodyService.restoreAll(player);
		player.removeEffect(MobEffects.DARKNESS);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0F);
		player.setGameMode(server.getWorldData().getGameType());
		overworld.setAttached(WorldMode.ATTACHMENT, next);
		if (next == WorldMode.STORY) {
			StoryService.beginAfterTraining(server);
		}
	}
}
