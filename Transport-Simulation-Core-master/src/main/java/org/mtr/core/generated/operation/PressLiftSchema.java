package org.mtr.core.generated.operation;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.core.operation.*;

public abstract class PressLiftSchema implements SerializedDataBase {

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<PressLiftInstruction> instructions = new it.unimi.dsi.fastutil.objects.ObjectArrayList<PressLiftInstruction>();

	protected PressLiftSchema() {
	}

	protected PressLiftSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("instructions", instructions::clear, readerBaseChild -> instructions.add(new PressLiftInstruction(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		serializeInstructions(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "instructions: " + instructions + "\n"
		;
	}

	protected void serializeInstructions(final WriterBase writerBase) {
		writerBase.writeDataset(instructions, "instructions");
	}
}