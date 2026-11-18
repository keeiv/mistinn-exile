package life.shizune.mistinn.block;

import life.shizune.mistinn.registry.MistinnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Who fell here, how much salt they lost, and which of their deaths this stone belongs to. */
public class GravestoneBlockEntity extends BlockEntity {
	private String owner = "";
	private String ownerName = "";
	private int salt;
	private int serial;

	public GravestoneBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
		super(MistinnBlockEntities.GRAVESTONE, worldPosition, blockState);
	}

	public void fill(final String owner, final String ownerName, final int salt, final int serial) {
		this.owner = owner;
		this.ownerName = ownerName;
		this.salt = salt;
		this.serial = serial;
		this.setChanged();
	}

	public String owner() {
		return this.owner;
	}

	public String ownerName() {
		return this.ownerName;
	}

	public int salt() {
		return this.salt;
	}

	public int serial() {
		return this.serial;
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.owner = input.getStringOr("owner", "");
		this.ownerName = input.getStringOr("owner_name", "");
		this.salt = input.getIntOr("salt", 0);
		this.serial = input.getIntOr("serial", 0);
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		output.putString("owner", this.owner);
		output.putString("owner_name", this.ownerName);
		output.putInt("salt", this.salt);
		output.putInt("serial", this.serial);
	}
}
