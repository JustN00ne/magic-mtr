package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class RouteSchema implements SerializedDataBase {

	protected final String id;

	protected final String agencyId;

	protected final String shortName;

	protected final String longName;

	protected final String description;

	protected final long type;

	protected final String url;

	protected final String color;

	protected final String textColor;

	protected RouteSchema(final String id, final String agencyId, final String shortName, final String longName, final String description, final long type, final String url, final String color, final String textColor) {
		this.id = id;
		this.agencyId = agencyId;
		this.shortName = shortName;
		this.longName = longName;
		this.description = description;
		this.type = type;
		this.url = url;
		this.color = color;
		this.textColor = textColor;
	}

	protected RouteSchema(final ReaderBase readerBase) {
		id = readerBase.getString("id", "");
		agencyId = readerBase.getString("agencyId", "");
		shortName = readerBase.getString("shortName", "");
		longName = readerBase.getString("longName", "");
		description = readerBase.getString("description", "");
		type = readerBase.getLong("type", 0);
		url = readerBase.getString("url", "");
		color = readerBase.getString("color", "");
		textColor = readerBase.getString("textColor", "");
	}

	public void updateData(final ReaderBase readerBase) {
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("id", id);
		writerBase.writeString("agencyId", agencyId);
		writerBase.writeString("shortName", shortName);
		writerBase.writeString("longName", longName);
		writerBase.writeString("description", description);
		writerBase.writeLong("type", type);
		writerBase.writeString("url", url);
		writerBase.writeString("color", color);
		writerBase.writeString("textColor", textColor);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "agencyId: " + agencyId + "\n"
			+ "shortName: " + shortName + "\n"
			+ "longName: " + longName + "\n"
			+ "description: " + description + "\n"
			+ "type: " + type + "\n"
			+ "url: " + url + "\n"
			+ "color: " + color + "\n"
			+ "textColor: " + textColor + "\n"
		;
	}
}