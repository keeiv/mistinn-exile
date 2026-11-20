package life.shizune.mistinn.block;

import life.shizune.mistinn.registry.MistinnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Who fell here, how much salt and what things they lost, and which of their deaths this stone belongs to. */
public class GravestoneBlockEntity extends BlockEntity {
	private String owner = "";
	private String ownerName = "";
	private int salt;
	private int serial;
	/** Everything the hunter carried, but their salt and safe box. */
	private java.util.List<life.shizune.mistinn.residue.Kept> items = java.util.List.of();

	public GravestoneBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
		super(MistinnBlockEntities.GRAVESTONE, worldPosition, blockState);
	}

	public void fill(final String owner, final String ownerName, final int salt, final int serial, final java.util.List<life.shizune.mistinn.residue.Kept> items) {
		this.items = java.util.List.copyOf(items);
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

	public java.util.List<life.shizune.mistinn.residue.Kept> items() {
		return this.items;
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.owner = input.getStringOr("owner", "");
		this.ownerName = input.getStringOr("owner_name", "");
		this.salt = input.getIntOr("salt", 0);
		this.serial = input.getIntOr("serial", 0);
		this.items = input.read("items", life.shizune.mistinn.residue.Kept.CODEC.listOf()).orElse(java.util.List.of());
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		output.putString("owner", this.owner);
		output.putString("owner_name", this.ownerName);
		output.putInt("salt", this.salt);
		output.putInt("serial", this.serial);
		if (!this.items.isEmpty()) {
			output.store("items", life.shizune.mistinn.residue.Kept.CODEC.listOf(), this.items);
		}
	}
}
