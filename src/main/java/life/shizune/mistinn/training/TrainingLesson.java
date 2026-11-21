package life.shizune.mistinn.training;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import life.shizune.mistinn.station.StaffRole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/** The course through Rust Anchor Station, lesson by lesson. The ids are shared by saves and translations. */
public enum TrainingLesson implements StringRepresentable {
	ARRIVAL("arrival", new BlockPos(-6, 65, 16), true),
	QUARTERMASTER("quartermaster", new BlockPos(5, 65, 4), StaffRole.QUARTERMASTER, "kit"),
	GEAR("gear", new BlockPos(5, 65, 9), false),
	PACK("pack", new BlockPos(5, 65, 9), false),
	HEAVY("heavy", new BlockPos(-42, 77, -48), false),
	SPRINT("sprint", new BlockPos(-22, 77, -42), true),
	ROLL("roll", new BlockPos(-22, 77, -42), false),
	SLIDE("slide", new BlockPos(-32, 77, -50), false),
	STRIKE("strike", new BlockPos(-21, 77, -56), true),
	SALT("salt", new BlockPos(-21, 77, -56), false),
	WINDUP("windup", new BlockPos(-21, 77, -56), false),
	SNEAK("sneak", new BlockPos(10, 77, -66), false),
	TOURNIQUET("tourniquet", new BlockPos(38, 77, -41), true),
	SPLINT("splint", new BlockPos(38, 77, -41), false),
	PAINKILLER("painkiller", new BlockPos(38, 77, -41), false),
	SURGERY("surgery", new BlockPos(38, 77, -41), false),
	INJECTOR("injector", new BlockPos(38, 77, -41), false),
	REPAIR("repair", new BlockPos(38, 77, -41), false),
	CRAFT("craft", new BlockPos(45, 77, -13), false),
	SKILLS("skills", new BlockPos(43, 77, -71), false),
	BELL("bell", new BlockPos(12, 77, -88), true),
	FALL("fall", new BlockPos(12, 77, -98), true),
	RECOVER("recover", new BlockPos(12, 77, -98), true),
	RADIO("radio", new BlockPos(-17, 77, -86), true),
	LAMPS("lamps", new BlockPos(-48, 77, -81), true),
	KEEPER("keeper", new BlockPos(-48, 77, -81), false),
	REPORT("report", new BlockPos(-33, 91, -110), true),
	BOARD("board", new BlockPos(-27, 91, -128), true),
	DEPLOY("deploy", new BlockPos(-27, 91, -129), false);

	public static final Codec<TrainingLesson> CODEC = StringRepresentable.fromEnum(TrainingLesson::values);
	public static final StreamCodec<ByteBuf, TrainingLesson> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
	public static final BlockPos KIT = new BlockPos(7, 65, 6);
	public static final BlockPos CRAFTING_TABLE = new BlockPos(48, 77, -13);
	public static final BlockPos BOARD_BUTTON = new BlockPos(-27, 92, -131);
	public static final List<BlockPos> LAMP_POSITIONS = List.of(
		new BlockPos(-55, 77, -88), new BlockPos(-41, 77, -88),
		new BlockPos(-55, 77, -74), new BlockPos(-41, 77, -74));

	public final String id;
	public final BlockPos target;
	public final boolean radio;
	/** Who leads this lesson in person, and the topic of theirs that, heard out, completes it. */
	public final @Nullable StaffRole staff;
	public final @Nullable String talk;

	TrainingLesson(final String id, final BlockPos target, final boolean radio) {
		this(id, target, radio, null, null);
	}

	TrainingLesson(final String id, final BlockPos target, final StaffRole staff, final String talk) {
		this(id, target, false, staff, talk);
	}

	TrainingLesson(final String id, final BlockPos target, final boolean radio, final @Nullable StaffRole staff, final @Nullable String talk) {
		this.id = id;
		this.target = target;
		this.radio = radio;
		this.staff = staff;
		this.talk = talk;
	}

	public String key() {
		return "training.mistinn.lesson." + this.id;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}
}
