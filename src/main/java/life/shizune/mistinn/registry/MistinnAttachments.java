package life.shizune.mistinn.registry;

import life.shizune.mistinn.body.Body;
import com.mojang.serialization.Codec;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.residue.DeathMark;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;

/** Data kept on each player: saved with the world, sent only to that player. */
public final class MistinnAttachments {
	/** How much salt the hunter carries. */
	public static final AttachmentType<Integer> SALT = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("salt"));

	/** Where the hunter last fell, and which gravestone holds their residue. */
	public static final AttachmentType<DeathMark> DEATH_MARK = AttachmentRegistry.<DeathMark>builder()
		.persistent(DeathMark.CODEC)
		.syncWith(DeathMark.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("death_mark"));

	/** Counts deaths so that only the latest gravestone can be opened. */
	public static final AttachmentType<Integer> RESIDUE_SERIAL = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("residue_serial"));

	/** The golden bell the hunter last rested at: where they come back. */
	public static final AttachmentType<GlobalPos> BELL = AttachmentRegistry.<GlobalPos>builder()
		.persistent(GlobalPos.CODEC)
		.syncWith(GlobalPos.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("bell"));

	/** Pockets, safe box and worn backpack. Copied on death; the death handler empties all but the safe box. */
	public static final AttachmentType<GridInventory> GRID = AttachmentRegistry.<GridInventory>builder()
		.persistent(GridInventory.CODEC)
		.syncWith(GridInventory.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("grid"));

	/** Health of each body part. Not copied on death: a respawned hunter is whole. */
	public static final AttachmentType<Body> BODY = AttachmentRegistry.<Body>builder()
		.persistent(Body.CODEC)
		.syncWith(Body.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("body"));

	/** Bleeding, broken bones, tourniquets and splints. Not copied on death: a respawned hunter is unhurt. */
	public static final AttachmentType<life.shizune.mistinn.body.Wounds> WOUNDS = AttachmentRegistry.<life.shizune.mistinn.body.Wounds>builder()
		.persistent(life.shizune.mistinn.body.Wounds.CODEC)
		.syncWith(life.shizune.mistinn.body.Wounds.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("wounds"));

	/** Tier index of the worn mask (-1 for none), sent to everyone so it can be drawn on the face. */
	public static final AttachmentType<Integer> WORN_MASK = AttachmentRegistry.<Integer>builder()
		.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("worn_mask"));

	/** Tier index of the worn backpack (-1 for none), sent to everyone so it can be drawn on the back. */
	public static final AttachmentType<Integer> WORN_BACKPACK = AttachmentRegistry.<Integer>builder()
		.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("worn_backpack"));

	/** Tier index of the worn chest rig (-1 for none), sent to everyone so it can be drawn on the chest. */
	public static final AttachmentType<Integer> WORN_RIG = AttachmentRegistry.<Integer>builder()
		.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("worn_rig"));

	/** Stamina, 0 to 100. Not saved: a hunter rejoins rested. */
	public static final AttachmentType<Float> STAMINA = AttachmentRegistry.<Float>builder()
		.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("stamina"));

	/** 止痛: the game time until which painkillers keep destroyed parts from being felt. */
	public static final AttachmentType<Long> PAINKILLER = AttachmentRegistry.<Long>builder()
		.persistent(Codec.LONG)
		.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("painkiller"));

	/** Whether this hunter already received the guide; kept through death so it comes once. */
	public static final AttachmentType<Boolean> GUIDE_GIVEN = AttachmentRegistry.<Boolean>builder()
		.persistent(Codec.BOOL)
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("guide_given"));

	private MistinnAttachments() {
	}

	public static void init() {
		// Loading the class registers the fields above.
	}
}
