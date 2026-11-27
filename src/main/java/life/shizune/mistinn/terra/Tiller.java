package life.shizune.mistinn.terra;

import java.util.Optional;
import java.util.UUID;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * 晶耕族: the people of the Terra valleys, who farm the living crystal and build with high
 * technology. Each keeps to their settlement and its day ({@link TillerRoutine}): the tillers tend
 * the field and run when struck,
 * the warden stands guard with a spear against monsters and the crystal drakes, and the keeper of
 * trade deals in crystal. Whoever harms one of them is turned away by all of them for a while.
 */
public class Tiller extends PathfinderMob implements Merchant {
	/** Who one is, and how they look. */
	public enum Role {
		TILLER_1("tiller_1", true), TILLER_2("tiller_2", false), WARDEN("warden", false), ELDER("elder", true);

		public final String id;
		public final boolean slim;

		Role(final String id, final boolean slim) {
			this.id = id;
			this.slim = slim;
		}

		public static Role byIndex(final int index) {
			Role[] all = values();
			return all[Math.floorMod(index, all.length)];
		}
	}

	private static final EntityDataAccessor<Byte> DATA_ROLE = SynchedEntityData.defineId(Tiller.class, EntityDataSerializers.BYTE);
	/** How long a wrong is held against a hunter. */
	public static final long GRUDGE = 6000L;
	/** How far a wrong is heard of in the settlement. */
	private static final double HEARD = 32.0;

	private @Nullable Player trading;
	private @Nullable MerchantOffers offers;
	private @Nullable UUID wronged;
	private long wrongedUntil;
	private @Nullable BlockPos home;

	public Tiller(final EntityType<? extends Tiller> type, final Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 34.0)
			.add(Attributes.ATTACK_DAMAGE, 7.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.ARMOR, 4.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ROLE, (byte) Role.TILLER_1.ordinal());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, true) {
			@Override
			public boolean canUse() {
				return Tiller.this.role() == Role.WARDEN && super.canUse();
			}
		});
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.4) {
			@Override
			public boolean canUse() {
				return Tiller.this.role() != Role.WARDEN && super.canUse();
			}
		});
		// the settlement's day: field, hub, workshop (see TillerRoutine)
		this.goalSelector.addGoal(3, new TillerRoutine(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.7));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return Tiller.this.role() == Role.WARDEN && super.canUse();
			}
		});
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
			(target, level) -> this.role() == Role.WARDEN && target instanceof Enemy && !CrystalLizard.companion(target) && target.distanceTo(this) < 16.0F));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
			(target, level) -> this.role() == Role.WARDEN && this.holdsAgainst(target)));
	}

	/** Takes up a place in a settlement: a role, and a home to keep near. */
	public void settle(final Role role, final BlockPos home) {
		this.entityData.set(DATA_ROLE, (byte) role.ordinal());
		this.home = home;
		this.setHomeTo(home, Settlements.HOME);
		if (role == Role.WARDEN) {
			this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MistinnItems.MIST_SPEAR));
			this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50.0);
			this.setHealth(50.0F);
		}
	}

	public Role role() {
		return Role.byIndex(this.entityData.get(DATA_ROLE));
	}

	/** The hub this one keeps near, or null for one who keeps no settlement. */
	public @Nullable BlockPos home() {
		return this.home;
	}

	/** Whether a hunter is trading with this one now. */
	public boolean isTrading() {
		return this.trading != null;
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.mistinn.tiller." + this.role().id);
	}

	// ── Wrongs ────────────────────────────────────────────────────

	/** Whether this settlement holds a wrong against the hunter. */
	public boolean holdsAgainst(final LivingEntity hunter) {
		return this.wronged != null && hunter.getUUID().equals(this.wronged) && this.level().getGameTime() < this.wrongedUntil;
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
		if (source.getEntity() instanceof Player hunter && !hunter.isCreative()) {
			if (hunter instanceof net.minecraft.server.level.ServerPlayer wronging) {
				life.shizune.mistinn.ledger.LedgerService.tell(wronging, "tiller.wronged", false);
			}
			// word goes round the settlement
			long until = level.getGameTime() + GRUDGE;
			for (Tiller other : level.getEntitiesOfClass(Tiller.class, new AABB(this.blockPosition()).inflate(HEARD))) {
				other.wronged = hunter.getUUID();
				other.wrongedUntil = until;
				// the wardens know who did it, wherever they stand
				if (other.role() == Role.WARDEN && !hunter.isCreative()) {
					other.setTarget(hunter);
				}
			}
		}
		return super.hurtServer(level, source, amount);
	}

	// ── Talk and trade ────────────────────────────────────────────

	@Override
	protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		if (!this.isAlive() || this.trading != null) {
			return super.mobInteract(player, hand);
		}
		if (!this.level().isClientSide()) {
			if (this.holdsAgainst(player)) {
				player.sendSystemMessage(Component.translatable("message.mistinn.tiller.wronged").withStyle(ChatFormatting.GRAY));
			} else if (player instanceof net.minecraft.server.level.ServerPlayer hunter && Commissions.keeps(this) != null) {
				// the keeper of a woken homestead's board: its commissions first, its wares a button away
				Commissions.open(hunter, this);
			} else if (this.role() == Role.ELDER) {
				this.trade(player);
			} else {
				String line = this.line(player);
				boolean pressing = line.endsWith(".squall") || line.endsWith(".siege");
				// on the path of longing they are the walker's own people, and mostly speak of the way
				Component kin = player instanceof net.minecraft.server.level.ServerPlayer hunter && !pressing && this.random.nextInt(3) != 0
					? life.shizune.mistinn.path.PathService.kin(hunter, this.random) : null;
				player.sendSystemMessage((kin != null ? kin.copy() : Component.translatable(line)).withStyle(ChatFormatting.GRAY));
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Opens this one's wares to a hunter. */
	public void trade(final Player player) {
		if (this.trading == null && this.isAlive()) {
			this.trading = player;
			this.openTradingScreen(player, this.getDisplayName(), 1);
		}
	}

	/** How many things each of them has to say, besides what the moment calls for. */
	public static final int LINES = 6;
	/** How many things any of them says at dusk, and at night. */
	public static final int EVENING_LINES = 3;

	/** What one of them says to a hunter: what the moment calls for (a squall over them, a siege at their door), or one of their own. */
	public String line(final Player player) {
		String who = this.role().id.replaceAll("_\\d", "");
		if (player instanceof net.minecraft.server.level.ServerPlayer hunter && Squall.sky(hunter) == Squall.Stage.RAGING) {
			return "message.mistinn.tiller.squall";
		}
		if (this.level().getServer() != null) {
			life.shizune.mistinn.domain.Domain domain = life.shizune.mistinn.domain.DomainService.at(this.level().getServer(), this.getX(), this.getZ());
			if (domain != null && Siege.watch(this.level().getServer(), domain.id()).on()) {
				return "message.mistinn.tiller.siege";
			}
		}
		// one who walked the tillers' way is known to them, once in a hunter's world
		if (player instanceof net.minecraft.server.level.ServerPlayer known && this.level().getServer() != null
			&& life.shizune.mistinn.story.WorldMode.of(this.level().getServer()) != life.shizune.mistinn.story.WorldMode.PATH) {
			int tone = life.shizune.mistinn.ledger.LedgerService.tone(known);
			if ((tone & 6) != 0 && !life.shizune.mistinn.ledger.LedgerService.knownOnce(known)) {
				life.shizune.mistinn.achievement.AchievementService.grant(known, life.shizune.mistinn.achievement.Achievement.TILLERS_KNOWN);
				return "message.mistinn.tiller.known." + ((tone & 2) != 0 ? "kind" : "cruel");
			}
		}
		// at dusk and at night they speak of the day's end, half the time
		Settlements.Settlement home = TillerRoutine.home(this);
		if (home != null && this.random.nextBoolean()) {
			TillerRoutine.Phase phase = TillerRoutine.phase(this.level(), home);
			if (phase == TillerRoutine.Phase.GATHER || phase == TillerRoutine.Phase.REST) {
				return "message.mistinn.tiller." + (phase == TillerRoutine.Phase.GATHER ? "dusk" : "night") + "." + this.random.nextInt(EVENING_LINES);
			}
		}
		return "message.mistinn.tiller." + who + "." + this.random.nextInt(LINES);
	}

	@Override
	public void setTradingPlayer(final @Nullable Player player) {
		this.trading = player;
	}

	@Override
	public @Nullable Player getTradingPlayer() {
		return this.trading;
	}

	/** Whether the wares this one deals in were drawn up with the tillers' trust high enough for the better ones. */
	private boolean trusted;

	@Override
	public MerchantOffers getOffers() {
		boolean trusted = this.level() instanceof net.minecraft.server.level.ServerLevel level && Commissions.trust(level.getServer()) >= Commissions.WARES;
		if (this.offers == null || trusted && !this.trusted) {
			this.offers = offers();
			if (trusted) {
				// the hunters have done what was asked: the keeper of trade brings out what it keeps back
				this.offers.addAll(trustedOffers());
			}
			this.trusted = trusted;
		}
		return this.offers;
	}

	/** What the keeper of trade brings out once the tillers trust the hunters. */
	static MerchantOffers trustedOffers() {
		MerchantOffers list = new MerchantOffers();
		list.add(deal(new ItemCost(MistinnItems.CRYSTAL_DUST, 20), new ItemStack(MistinnItems.CRYSTAL_CORE)));
		list.add(deal(new ItemCost(MistinnItems.CRYSTAL_CORE, 4), new ItemStack(MistinnItems.gearOfTier(5).getFirst())));
		list.add(deal(new ItemCost(MistinnItems.CRYSTAL_DUST, 6), new ItemStack(MistinnItems.FLARE, 2)));
		return list;
	}

	/** What the keeper of trade deals in: crystal for their wares, rift crystal bought for their own. */
	static MerchantOffers offers() {
		MerchantOffers list = new MerchantOffers();
		list.add(deal(new ItemCost(MistinnItems.CRYSTAL_DUST, 6), new ItemStack(MistinnItems.LATTICE_CHIP)));
		list.add(deal(new ItemCost(MistinnItems.CRYSTAL_DUST, 4), new ItemStack(MistinnItems.CRYSTAL_WHETSTONE, 2)));
		list.add(deal(new ItemCost(MistinnItems.CRYSTAL_CORE, 1), new ItemStack(MistinnItems.RESONANCE_FORK)));
		list.add(deal(new ItemCost(MistinnItems.CRYSTAL_DUST, 3), new ItemStack(Items.BREAD, 4)));
		list.add(deal(new ItemCost(MistinnItems.CRYSTAL_DUST, 5), new ItemStack(MistinnItems.TOURNIQUET, 2)));
		list.add(deal(new ItemCost(MistinnItems.CRYSTAL_DUST, 8), new ItemStack(MistinnItems.PAINKILLERS)));
		list.add(deal(new ItemCost(MistinnItems.CRYSTAL_CORE, 1), new ItemStack(MistinnItems.POTENT_INJECTOR)));
		list.add(deal(new ItemCost(MistinnItems.CRYSTAL_CORE, 2), new ItemStack(MistinnItems.gearOfTier(4).getFirst())));
		list.add(deal(new ItemCost(MistinnItems.RIFT_SHARD, 1), new ItemStack(MistinnItems.CRYSTAL_DUST, 2)));
		return list;
	}

	private static MerchantOffer deal(final ItemCost cost, final ItemStack result) {
		return new MerchantOffer(cost, Optional.empty(), result, 0, Integer.MAX_VALUE, 0, 0.0F);
	}

	@Override
	public void overrideOffers(final MerchantOffers offers) {
		this.offers = offers;
	}

	@Override
	public void notifyTrade(final MerchantOffer offer) {
		offer.increaseUses();
		this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
	}

	@Override
	public void notifyTradeUpdated(final ItemStack stack) {
	}

	@Override
	public int getVillagerXp() {
		return 0;
	}

	@Override
	public void overrideXp(final int xp) {
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.AMETHYST_BLOCK_CHIME;
	}

	@Override
	public boolean isClientSide() {
		return this.level().isClientSide();
	}

	@Override
	public boolean stillValid(final Player player) {
		return this.trading == player && this.isAlive() && player.distanceToSqr(this) < 64.0;
	}

	@Override
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}

	// ── Saving ────────────────────────────────────────────────────

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("role", this.role().id);
		if (this.home != null) {
			output.putLong("home", this.home.asLong());
		}
		if (this.wronged != null) {
			output.store("wronged", UUIDUtil.CODEC, this.wronged);
			output.putLong("wronged_until", this.wrongedUntil);
		}
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		String id = input.getStringOr("role", Role.TILLER_1.id);
		for (Role role : Role.values()) {
			if (role.id.equals(id)) {
				this.entityData.set(DATA_ROLE, (byte) role.ordinal());
			}
		}
		this.wronged = input.read("wronged", UUIDUtil.CODEC).orElse(null);
		this.wrongedUntil = input.getLongOr("wronged_until", 0L);
		long home = input.getLongOr("home", Long.MIN_VALUE);
		if (home != Long.MIN_VALUE) {
			this.home = BlockPos.of(home);
			this.setHomeTo(this.home, Settlements.HOME);
		}
	}
}
