package org.mtr.core.generated.oba;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.oba.*;

public abstract class AgencySchema implements SerializedDataBase {

	protected final String id;

	protected final String name;

	protected final String url;

	protected final String timezone;

	protected String lang = "";

	protected String phone = "";

	protected String fareUrl = "";

	protected String email = "";

	protected AgencySchema(final String id, final String name, final String url, final String timezone) {
		this.id = id;
		this.name = name;
		this.url = url;
		this.timezone = timezone;
	}

	protected AgencySchema(final ReaderBase readerBase) {
		id = readerBase.getString("id", "");
		name = readerBase.getString("name", "");
		url = readerBase.getString("url", "");
		timezone = readerBase.getString("timezone", "");
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.unpackString("lang", value -> lang = value);
		readerBase.unpackString("phone", value -> phone = value);
		readerBase.unpackString("fareUrl", value -> fareUrl = value);
		readerBase.unpackString("email", value -> email = value);
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeString("id", id);
		writerBase.writeString("name", name);
		writerBase.writeString("url", url);
		writerBase.writeString("timezone", timezone);
		serializeLang(writerBase);
		serializePhone(writerBase);
		serializeFareUrl(writerBase);
		serializeEmail(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "id: " + id + "\n"
			+ "name: " + name + "\n"
			+ "url: " + url + "\n"
			+ "timezone: " + timezone + "\n"
			+ "lang: " + lang + "\n"
			+ "phone: " + phone + "\n"
			+ "fareUrl: " + fareUrl + "\n"
			+ "email: " + email + "\n"
		;
	}

	protected void serializeLang(final WriterBase writerBase) {
		writerBase.writeString("lang", lang);
	}

	protected void serializePhone(final WriterBase writerBase) {
		writerBase.writeString("phone", phone);
	}

	protected void serializeFareUrl(final WriterBase writerBase) {
		writerBase.writeString("fareUrl", fareUrl);
	}

	protected void serializeEmail(final WriterBase writerBase) {
		writerBase.writeString("email", email);
	}
}