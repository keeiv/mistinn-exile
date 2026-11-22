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
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.SkillTree;
import life.shizune.mistinn.skill.SlideService;
import life.shizune.mistinn.skill.Stat;
import life.shizune.mistinn.stamina.RollService;
import life.shizune.mistinn.story.RadioBlock;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.story.StoryStep;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.station.StationHand;
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

	private static final BlockPos ENTRY = TrainingGround.ENTRY;
	private static final BlockPos SEAT = TrainingLesson.DEPLOY.target;
	private static final String STRIKE_TARGET = "mistinn.training_target";
	private static final String KEEPER_TARGET = "mistinn.training_keeper";
	private static final String POD_SEAT = "mistinn.training_seat";
	private static final String STAFF = "mistinn.training_staff";
	private static final String DUMMY = "mistinn.training_dummy";
	private static final String SPARRING = "mistinn.training_sparring";
	private static final String CAGED = "mistinn.training_caged";
	private static final int PRACTICE_SKILL_SALT = 300;
	/** Whether each sparring husk was winding up last tick. */
	private static final java.util.Map<Integer, Boolean> WINDING = new java.util.HashMap<>();
	/** Hunters the cook has given their soup. */
	private static final java.util.Set<java.util.UUID> FED = new java.util.HashSet<>();
	/** Where the forge's dummies stand, from the heavy blow lesson's spot: in an arc a full slash reaches. */
	private static final double[][] DUMMIES = {{-1.2, 0.0}, {0.0, -0.5}, {1.2, 0.0}};
	/** The listeners' cages in the pen. */
	private static final BlockPos[] CAGES = {new BlockPos(1, 77, -73), new BlockPos(9, 77, -73), new BlockPos(17, 77, -73)};
	private static final int PRACTICE_SALT = 20;
	private static final int COURSE_REACH = 8;

	private TrainingService() {
	}

	public static void init() {
		TrainingGround.init();
		StationCeremony.init();
		StationArrival.init();
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
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof StationHand hand && level.dimension().equals(TrainingGround.KEY)) {
				dedupe(hand, level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			life.shizune.mistinn.station.StationBarks.forgetAll();
			life.shizune.mistinn.station.StationBroadcast.forgetAll();
			StationCeremony.forgetAll();
			WINDING.clear();
			FED.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(TrainingService::fallen);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.dimension().equals(TrainingGround.KEY) && player instanceof ServerPlayer hunter
				&& (hit.getBlockPos().equals(TrainingLesson.SNEAK.target) || hit.getBlockPos().equals(TrainingLesson.SNEAK.target.above()))) {
				if (at(hunter, TrainingLesson.SNEAK)) {
					hunter.level().playSound(null, hit.getBlockPos(), SoundEvents.LANTERN_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F);
					announce(hunter, TrainingLesson.TOURNIQUET);
				}
				return InteractionResult.SUCCESS;
			}
			if (!level.dimension().equals(TrainingGround.KEY) || !hit.getBlockPos().equals(TrainingLesson.BOARD_BUTTON)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer hunter && at(hunter, TrainingLesson.BOARD)) {
				if (StationCeremony.seenOff(hunter)) {
					board(hunter);
				} else {
					hunter.sendSystemMessage(Component.translatable("training.mistinn.board.wait"), true);
				}
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
		retire(player.level(), next);
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
		if (server.getTickCount() % 20 == 0) {
			staff(range);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && server.isSingleplayerOwner(player.nameAndId())) {
				train(player, range);
				TrainingLesson now = player.getAttached(LESSON);
				if (player.level() == range && now != null) {
					StationCeremony.tick(player, range);
					// the drop has its own calls; the day's chatter stops for it
					boolean drop = now == TrainingLesson.BOARD || now == TrainingLesson.DEPLOY;
					if (!drop) {
						life.shizune.mistinn.station.StationBroadcast.tick(player);
						if (server.getTickCount() % 5 == 0) {
							life.shizune.mistinn.station.StationBarks.tick(range, player);
						}
					}
				}
			}
		}
	}

	/** Puts back any member of the staff missing from a loaded post. */
	private static void staff(final ServerLevel level) {
		for (TrainingGround.Post post : TrainingGround.POSTS) {
			// anywhere in the station counts: some walk rounds, all gather for the drop; those who left stay gone
			if (StationCeremony.departed(post.role()) || !level.isPositionEntityTicking(post.pos())
				|| !level.getEntitiesOfClass(StationHand.class, new AABB(BlockPos.ZERO).inflate(256.0), hand -> hand.role() == post.role()).isEmpty()) {
				continue;
			}
			StationHand hand = MistinnEntities.STATION_HAND.create(level, EntitySpawnReason.STRUCTURE);
			if (hand != null) {
				hand.setRole(post.role());
				hand.addTag(STAFF);
				hand.setCustomName(Component.translatable(post.role().key()));
				hand.snapTo(post.pos().getX() + 0.5, post.pos().getY(), post.pos().getZ() + 0.5, post.yaw(), 0.0F);
				hand.setYHeadRot(post.yaw());
				hand.setYBodyRot(post.yaw());
				if (post.role() == StaffRole.DOCK_WORKER_4) {
					hand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
				}
				level.addFreshEntity(hand);
			}
		}
	}

	/**
	 * A save built on the old quays, rebuilt as the station: the course starts over from the
	 * barracks, without the old bell, death mark or creatures; the staff come back to their posts.
	 */
	private static void startOver(final ServerPlayer player, final ServerLevel range) {
		TrainingGround.clearUpgraded(range);
		player.setAttached(LESSON, TrainingLesson.ARRIVAL);
		player.removeAttached(MistinnAttachments.BELL);
		player.removeAttached(MistinnAttachments.ONE_TIME_BELL);
		player.removeAttached(MistinnAttachments.DEATH_MARK);
		for (Mob mob : range.getEntitiesOfClass(Mob.class, new AABB(BlockPos.ZERO).inflate(256.0),
			mob -> mob instanceof StationHand || mob.entityTags().stream().anyMatch(tag -> tag.startsWith("mistinn.training")))) {
			mob.discard();
		}
		move(player, range, ENTRY);
	}

	/** The staff member speaks to the hunter: the cook hands over the soup the greeting offers, once. */
	public static void greeted(final ServerPlayer player, final StaffRole role) {
		if (role == StaffRole.COOK && player.level().dimension().equals(TrainingGround.KEY) && FED.add(player.getUUID())) {
			player.getInventory().add(new ItemStack(Items.MUSHROOM_STEW));
		}
	}

	/** One of each member: a second copy loading from a part of the station left behind is let go. */
	private static void dedupe(final StationHand hand, final ServerLevel level) {
		if (StationCeremony.departed(hand.role())) {
			hand.discard();
			return;
		}
		for (StationHand other : level.getEntitiesOfClass(StationHand.class, new AABB(BlockPos.ZERO).inflate(256.0),
			h -> h != hand && h.role() == hand.role() && !h.isRemoved())) {
			if (other.entityTags().contains(StationHand.GATHERED)) {
				hand.discard();
				return;
			}
			other.discard();
		}
	}

	/** What a member of the staff opens with: the topic of the lesson they lead, if it is the hunter's now. */
	public static @Nullable String opening(final ServerPlayer player, final StaffRole role) {
		TrainingLesson lesson = player.getAttached(LESSON);
		return lesson != null && lesson.staff == role && owner(player) ? lesson.talk : null;
	}

	/** The hunter heard a topic out; the lesson it completes moves on. */
	public static void heard(final ServerPlayer player, final StaffRole role, final String topic) {
		TrainingLesson lesson = player.getAttached(LESSON);
		if (lesson != null && lesson.staff == role && topic.equals(lesson.talk) && at(player, lesson)) {
			announce(player, TrainingLesson.values()[lesson.ordinal() + 1]);
		}
	}

	private static void train(final ServerPlayer player, final ServerLevel range) {
		if (StationCeremony.dropping(player)) {
			// the drop has the hunter now: falling into the sea is not falling off a deck
			return;
		}
		if (TrainingGround.upgraded(range)) {
			startOver(player, range);
		}
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
		} else if (player.isInWater() && lesson != TrainingLesson.DEPLOY) {
			// fallen off a deck: fished out onto the nearest one
			move(player, range, TrainingGround.landing(player.position()));
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
					announce(player, TrainingLesson.QUARTERMASTER);
				}
			}
			case QUARTERMASTER -> {
				// completed by hearing the quartermaster out
			}
			case GEAR, PACK -> equipment(player, level, lesson);
			case HEAVY -> {
				supply(player, MistinnItems.ANCHOR_BLADE, 1);
				dummies(level, lesson.target);
				if (struck(level, lesson.target) >= DUMMIES.length) {
					announce(player, TrainingLesson.SPRINT);
				}
			}
			case SLIDE -> {
				// the slide is lent for the lesson and taken back at the drop
				if (!SkillService.get(player).has(SkillTree.ROOT)) {
					SkillService.set(player, SkillService.get(player).with(SkillTree.ROOT));
				}
				if (SlideService.sliding(player)) {
					announce(player, TrainingLesson.STRIKE);
				}
			}
			case WINDUP -> {
				sparring(level, lesson.target);
				// learned when the husk lets its blow go while the hunter is mid-roll, in reach or not
				for (Mob husk : tagged(level, lesson.target, SPARRING)) {
					boolean now = life.shizune.mistinn.enemy.WindupService.winding(husk);
					Boolean was = WINDING.put(husk.getId(), now);
					if (Boolean.TRUE.equals(was) && !now && RollService.dodging(player)) {
						announce(player, TrainingLesson.SNEAK);
						break;
					}
				}
			}
			case SNEAK -> listen(player, level);
			case SKILLS -> {
				supply(player, MistinnItems.HUNTER_GUIDE, 1);
				if (SkillService.get(player).minorCount() > 0) {
					announce(player, TrainingLesson.BELL);
				} else if (SaltService.get(player) < SkillService.price(player, Stat.STRENGTH.id + "_1")) {
					SaltService.set(player, Math.max(PRACTICE_SKILL_SALT, SaltService.get(player)));
				}
			}
			case SPRINT -> {
				if (player.isSprinting()) {
					announce(player, TrainingLesson.ROLL);
				}
			}
			case ROLL -> {
				if (RollService.dodging(player)) {
					announce(player, TrainingLesson.SLIDE);
				}
			}
			case STRIKE -> {
				supply(player, MistinnItems.ANCHOR_BLADE, 1);
				target(level, lesson.target, STRIKE_TARGET, false);
			}
			case SALT -> {
				if (SaltService.get(player) >= PRACTICE_SALT) {
					announce(player, TrainingLesson.WINDUP);
				} else {
					supply(player, MistinnItems.SALT, 1);
				}
			}
			case TOURNIQUET, SPLINT, PAINKILLER, SURGERY, INJECTOR, REPAIR -> infirmary(player, lesson);
			case CRAFT -> {
				if (has(player, MistinnItems.ORE_BELL, 1)) {
					announce(player, TrainingLesson.SKILLS);
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
		if (level.getBlockEntity(TrainingLesson.KIT) instanceof Container chest) {
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
			announce(player, TrainingLesson.HEAVY);
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
		// a full hotbar: into the rest of the inventory
		inventory.add(new ItemStack(item, count));
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
				if (body.total() >= Body.whole(BodyService.max(player)).total()) {
					BodyService.set(player, body.with(BodyPart.LEFT_ARM, BodyService.max(player) / 2.0F));
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
		for (BlockPos pos : TrainingLesson.LAMP_POSITIONS) {
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

	/** A practice husk of the station's: no wits, a lot of health, tagged for its lesson. */
	private static @Nullable Mob practice(final ServerLevel level, final double x, final double y, final double z, final String tag,
		final String name, final boolean thinks) {
		Mob mob = EntityTypes.HUSK.create(level, EntitySpawnReason.STRUCTURE);
		if (mob == null) {
			return null;
		}
		mob.addTag(tag);
		mob.setCustomName(Component.translatable(name));
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		mob.setHealth(mob.getMaxHealth());
		mob.setPersistenceRequired();
		mob.setNoAi(!thinks);
		mob.snapTo(x, y, z, 0.0F, 0.0F);
		level.addFreshEntity(mob);
		return mob;
	}

	private static java.util.List<Mob> tagged(final ServerLevel level, final BlockPos near, final String tag) {
		return level.getEntitiesOfClass(Mob.class, new AABB(near).inflate(24.0), mob -> mob.entityTags().contains(tag));
	}

	/** The forge's three dummies, set up in an arc in front of the heavy blow lesson's spot. */
	private static void dummies(final ServerLevel level, final BlockPos spot) {
		if (!tagged(level, spot, DUMMY).isEmpty()) {
			return;
		}
		for (double[] at : DUMMIES) {
			Mob dummy = practice(level, spot.getX() + 0.5 + at[0], spot.getY(), spot.getZ() + 0.5 + at[1], DUMMY, "training.mistinn.dummy", false);
			if (dummy != null) {
				dummy.setYRot(180.0F);
				dummy.setYHeadRot(180.0F);
				dummy.setYBodyRot(180.0F);
			}
		}
	}

	/** How many of the dummies one blow has just hurt. */
	private static int struck(final ServerLevel level, final BlockPos spot) {
		int hurt = 0;
		for (Mob dummy : tagged(level, spot, DUMMY)) {
			if (dummy.hurtTime >= 9) {
				hurt++;
			}
			dummy.setHealth(dummy.getMaxHealth());
		}
		return hurt;
	}

	/** The sparring husk: it hunts the hunter, winds up its blows, and is hard to kill. */
	private static void sparring(final ServerLevel level, final BlockPos spot) {
		if (tagged(level, spot, SPARRING).isEmpty()) {
			practice(level, spot.getX() + 0.5, spot.getY(), spot.getZ() - 3.5, SPARRING, "training.mistinn.sparring", true);
		}
	}

	/**
	 * The listening pen: a husk in each cage that cannot see, only hear. If one hears the hunter, the
	 * hunter goes back to the pen's door and they all forget.
	 */
	private static void listen(final ServerPlayer player, final ServerLevel level) {
		java.util.List<Mob> caged = tagged(level, TrainingLesson.SNEAK.target, CAGED);
		if (caged.isEmpty()) {
			for (BlockPos cage : CAGES) {
				practice(level, cage.getX() + 0.5, cage.getY(), cage.getZ() + 0.5, CAGED, "training.mistinn.caged", true);
			}
			return;
		}
		boolean heard = false;
		for (Mob mob : caged) {
			// blind: no hunting by sight, only the noise service's investigating
			((life.shizune.mistinn.mixin.MobTargetsAccessor) mob).mistinn$targetSelector().removeAllGoals(goal -> true);
			mob.setTarget(null);
			heard |= life.shizune.mistinn.enemy.NoiseService.heard(mob) != null;
		}
		if (heard) {
			for (Mob mob : caged) {
				life.shizune.mistinn.enemy.NoiseService.forget(mob);
				mob.getNavigation().stop();
			}
			move(player, level, TrainingGround.SNEAK_START);
			player.sendSystemMessage(Component.translatable("training.mistinn.sneak.heard"), true);
			level.playSound(null, TrainingGround.SNEAK_START, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.8F, 0.6F);
		}
	}

	/** Practice creatures go once their lesson is behind the hunter. */
	private static void retire(final ServerLevel level, final TrainingLesson next) {
		if (!level.dimension().equals(TrainingGround.KEY)) {
			return;
		}
		if (next.ordinal() > TrainingLesson.HEAVY.ordinal()) {
			tagged(level, TrainingLesson.HEAVY.target, DUMMY).forEach(Mob::discard);
		}
		if (next.ordinal() > TrainingLesson.WINDUP.ordinal()) {
			tagged(level, TrainingLesson.WINDUP.target, SPARRING).forEach(Mob::discard);
		}
		if (next.ordinal() > TrainingLesson.SNEAK.ordinal()) {
			tagged(level, TrainingLesson.SNEAK.target, CAGED).forEach(Mob::discard);
		}
	}

	/** A blow passed through the hunter's roll: the windup lesson is learned. */
	public static void dodged(final ServerPlayer hunter) {
		if (at(hunter, TrainingLesson.WINDUP)) {
			announce(hunter, TrainingLesson.SNEAK);
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

	/** The hunter chose where to go from the seat: the drop begins, three bells before they go. */
	private static void finish(final ServerPlayer player, final WorldMode next) {
		if (at(player, TrainingLesson.DEPLOY) && inSeat(player)) {
			StationCeremony.drop(player, next);
		}
	}

	/**
	 * Under the sea, the drop sends the hunter on to the world they chose: they come out of the dark
	 * falling over it ({@link StationArrival}), or, with no room for a fall, are set down on its pad.
	 */
	static void complete(final ServerPlayer player, final WorldMode next) {
		// by now the seat is down in the sea, far from the pod: only the lesson and the station are asked after
		if (player.getAttached(LESSON) != TrainingLesson.DEPLOY || !player.level().dimension().equals(TrainingGround.KEY)) {
			Mistinn.LOGGER.warn("The drop could not send {} on: not seated for it", player.getName().getString());
			return;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = server.overworld();
		Entity seat = player.getVehicle();
		player.stopRiding();
		boolean falling = StationArrival.begin(player, overworld, next == WorldMode.STORY);
		if (!falling && !move(player, overworld, overworld.getRespawnData().pos())) {
			return;
		}
		if (seat != null && seat.entityTags().contains(POD_SEAT)) {
			seat.discard();
		}
		player.removeAttached(LESSON);
		player.getInventory().clearContent();
		player.containerMenu.setCarried(ItemStack.EMPTY);
		player.getInventory().setItem(0, new ItemStack(MistinnItems.HUNTER_GUIDE));
		GridService.set(player, GridInventory.EMPTY);
		player.removeAttached(MistinnAttachments.BELL);
		player.removeAttached(MistinnAttachments.ONE_TIME_BELL);
		player.removeAttached(MistinnAttachments.DEATH_MARK);
		player.removeAttached(MistinnAttachments.PAINKILLER);
		SaltService.set(player, 0);
		// the lent slide, practice stat points and achievements earned in training do not go down with the hunter
		SkillService.set(player, life.shizune.mistinn.skill.Skills.EMPTY);
		life.shizune.mistinn.achievement.AchievementService.clear(player);
		TreatmentService.reset(player);
		WoundService.clearAll(player);
		BodyService.restoreAll(player);
		player.removeEffect(MobEffects.DARKNESS);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0F);
		player.setGameMode(server.getWorldData().getGameType());
		overworld.setAttached(WorldMode.ATTACHMENT, next);
		if (!falling && next == WorldMode.STORY) {
			// the story waits for the landing when there is a fall
			StoryService.beginAfterTraining(server);
		}
	}
}
