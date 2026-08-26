package org.mtr.legacy.generated.data;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.data.*;
import org.mtr.legacy.data.*;

public abstract class RailNodeConnectionSchema implements SerializedDataBase {

	protected long node_pos;

	protected double h_1;

	protected double k_1;

	protected double h_2;

	protected double k_2;

	protected double r_1;

	protected double r_2;

	protected double t_start_1;

	protected double t_end_1;

	protected double t_start_2;

	protected double t_end_2;

	protected long y_start;

	protected long y_end;

	protected boolean reverse_t_1;

	protected boolean is_straight_1;

	protected boolean reverse_t_2;

	protected boolean is_straight_2;

	protected String rail_type = "";

	protected TransportMode transportMode = TransportMode.values()[0];

	protected String model_key = "";

	protected boolean is_secondary_dir;

	protected double vertical_curve_radius;

	protected RailNodeConnectionSchema() {
	}

	protected RailNodeConnectionSchema(final ReaderBase readerBase) {
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackLong("node_pos", value -> node_pos = value);
		readerBase.unpackDouble("h_1", value -> h_1 = value);
		readerBase.unpackDouble("k_1", value -> k_1 = value);
		readerBase.unpackDouble("h_2", value -> h_2 = value);
		readerBase.unpackDouble("k_2", value -> k_2 = value);
		readerBase.unpackDouble("r_1", value -> r_1 = value);
		readerBase.unpackDouble("r_2", value -> r_2 = value);
		readerBase.unpackDouble("t_start_1", value -> t_start_1 = value);
		readerBase.unpackDouble("t_end_1", value -> t_end_1 = value);
		readerBase.unpackDouble("t_start_2", value -> t_start_2 = value);
		readerBase.unpackDouble("t_end_2", value -> t_end_2 = value);
		readerBase.unpackLong("y_start", value -> y_start = value);
		readerBase.unpackLong("y_end", value -> y_end = value);
		readerBase.unpackBoolean("reverse_t_1", value -> reverse_t_1 = value);
		readerBase.unpackBoolean("is_straight_1", value -> is_straight_1 = value);
		readerBase.unpackBoolean("reverse_t_2", value -> reverse_t_2 = value);
		readerBase.unpackBoolean("is_straight_2", value -> is_straight_2 = value);
		readerBase.unpackString("rail_type", value -> rail_type = value);
		readerBase.unpackString("transportMode", value -> transportMode = EnumHelper.valueOf(TransportMode.values()[0], value));
		readerBase.unpackString("model_key", value -> model_key = value);
		readerBase.unpackBoolean("is_secondary_dir", value -> is_secondary_dir = value);
		readerBase.unpackDouble("vertical_curve_radius", value -> vertical_curve_radius = value);
	}

	public void serializeData(final WriterBase writerBase) {
		serializeNode_pos(writerBase);
		serializeH_1(writerBase);
		serializeK_1(writerBase);
		serializeH_2(writerBase);
		serializeK_2(writerBase);
		serializeR_1(writerBase);
		serializeR_2(writerBase);
		serializeT_start_1(writerBase);
		serializeT_end_1(writerBase);
		serializeT_start_2(writerBase);
		serializeT_end_2(writerBase);
		serializeY_start(writerBase);
		serializeY_end(writerBase);
		serializeReverse_t_1(writerBase);
		serializeIs_straight_1(writerBase);
		serializeReverse_t_2(writerBase);
		serializeIs_straight_2(writerBase);
		serializeRail_type(writerBase);
		serializeTransportMode(writerBase);
		serializeModel_key(writerBase);
		serializeIs_secondary_dir(writerBase);
		serializeVertical_curve_radius(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "node_pos: " + node_pos + "\n"
			+ "h_1: " + h_1 + "\n"
			+ "k_1: " + k_1 + "\n"
			+ "h_2: " + h_2 + "\n"
			+ "k_2: " + k_2 + "\n"
			+ "r_1: " + r_1 + "\n"
			+ "r_2: " + r_2 + "\n"
			+ "t_start_1: " + t_start_1 + "\n"
			+ "t_end_1: " + t_end_1 + "\n"
			+ "t_start_2: " + t_start_2 + "\n"
			+ "t_end_2: " + t_end_2 + "\n"
			+ "y_start: " + y_start + "\n"
			+ "y_end: " + y_end + "\n"
			+ "reverse_t_1: " + reverse_t_1 + "\n"
			+ "is_straight_1: " + is_straight_1 + "\n"
			+ "reverse_t_2: " + reverse_t_2 + "\n"
			+ "is_straight_2: " + is_straight_2 + "\n"
			+ "rail_type: " + rail_type + "\n"
			+ "transportMode: " + transportMode + "\n"
			+ "model_key: " + model_key + "\n"
			+ "is_secondary_dir: " + is_secondary_dir + "\n"
			+ "vertical_curve_radius: " + vertical_curve_radius + "\n"
		;
	}

	protected void serializeNode_pos(final WriterBase writerBase) {
		writerBase.writeLong("node_pos", node_pos);
	}

	protected void serializeH_1(final WriterBase writerBase) {
		writerBase.writeDouble("h_1", h_1);
	}

	protected void serializeK_1(final WriterBase writerBase) {
		writerBase.writeDouble("k_1", k_1);
	}

	protected void serializeH_2(final WriterBase writerBase) {
		writerBase.writeDouble("h_2", h_2);
	}

	protected void serializeK_2(final WriterBase writerBase) {
		writerBase.writeDouble("k_2", k_2);
	}

	protected void serializeR_1(final WriterBase writerBase) {
		writerBase.writeDouble("r_1", r_1);
	}

	protected void serializeR_2(final WriterBase writerBase) {
		writerBase.writeDouble("r_2", r_2);
	}

	protected void serializeT_start_1(final WriterBase writerBase) {
		writerBase.writeDouble("t_start_1", t_start_1);
	}

	protected void serializeT_end_1(final WriterBase writerBase) {
		writerBase.writeDouble("t_end_1", t_end_1);
	}

	protected void serializeT_start_2(final WriterBase writerBase) {
		writerBase.writeDouble("t_start_2", t_start_2);
	}

	protected void serializeT_end_2(final WriterBase writerBase) {
		writerBase.writeDouble("t_end_2", t_end_2);
	}

	protected void serializeY_start(final WriterBase writerBase) {
		writerBase.writeLong("y_start", y_start);
	}

	protected void serializeY_end(final WriterBase writerBase) {
		writerBase.writeLong("y_end", y_end);
	}

	protected void serializeReverse_t_1(final WriterBase writerBase) {
		writerBase.writeBoolean("reverse_t_1", reverse_t_1);
	}

	protected void serializeIs_straight_1(final WriterBase writerBase) {
		writerBase.writeBoolean("is_straight_1", is_straight_1);
	}

	protected void serializeReverse_t_2(final WriterBase writerBase) {
		writerBase.writeBoolean("reverse_t_2", reverse_t_2);
	}

	protected void serializeIs_straight_2(final WriterBase writerBase) {
		writerBase.writeBoolean("is_straight_2", is_straight_2);
	}

	protected void serializeRail_type(final WriterBase writerBase) {
		writerBase.writeString("rail_type", rail_type);
	}

	protected void serializeTransportMode(final WriterBase writerBase) {
		writerBase.writeString("transportMode", transportMode.toString());
	}

	protected void serializeModel_key(final WriterBase writerBase) {
		writerBase.writeString("model_key", model_key);
	}

	protected void serializeIs_secondary_dir(final WriterBase writerBase) {
		writerBase.writeBoolean("is_secondary_dir", is_secondary_dir);
	}

	protected void serializeVertical_curve_radius(final WriterBase writerBase) {
		writerBase.writeDouble("vertical_curve_radius", vertical_curve_radius);
	}
}