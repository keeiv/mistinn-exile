package life.shizune.mistinn.training;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** The instructor's route down the training pier. The ids are shared by saves and translations. */
public enum TrainingLesson implements StringRepresentable {
	ARRIVAL("arrival", 4, true),
	GEAR("gear", -4, true),
	PACK("pack", -4),
	SPRINT("sprint", -18, true),
	ROLL("roll", -18),
	STRIKE("strike", -32, true),
	SALT("salt", -32),
	TOURNIQUET("tourniquet", -48, true),
	SPLINT("splint", -48),
	PAINKILLER("painkiller", -48),
	SURGERY("surgery", -48),
	INJECTOR("injector", -48),
	REPAIR("repair", -48),
	CRAFT("craft", -50),
	BELL("bell", -64, true),
	FALL("fall", -77, true),
	RECOVER("recover", -77, true),
	RADIO("radio", -92, true),
	LAMPS("lamps", -108, true),
	KEEPER("keeper", -108),
	REPORT("report", -121, true),
	BOARD("board", -128, true),
	DEPLOY("deploy", -129);

	public static final Codec<TrainingLesson> CODEC = StringRepresentable.fromEnum(TrainingLesson::values);
	public static final StreamCodec<ByteBuf, TrainingLesson> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
	public static final BlockPos KIT = new BlockPos(-3, 65, -4);
	public static final BlockPos CRAFTING_TABLE = new BlockPos(4, 65, -50);
	public static final BlockPos BOARD_BUTTON = new BlockPos(0, 66, -131);
	public static final List<BlockPos> LAMP_POSITIONS = List.of(
		new BlockPos(-4, 65, -103), new BlockPos(4, 65, -103),
		new BlockPos(-4, 65, -113), new BlockPos(4, 65, -113));

	public final String id;
	public final BlockPos target;
	public final boolean radio;

	TrainingLesson(final String id, final int z) {
		this(id, z, false);
	}

	TrainingLesson(final String id, final int z, final boolean radio) {
		this.id = id;
		this.target = new BlockPos(0, 65, z);
		this.radio = radio;
	}

	public String key() {
		return "training.mistinn.lesson." + this.id;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}
}
