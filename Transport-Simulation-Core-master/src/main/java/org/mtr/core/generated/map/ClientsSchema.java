package org.mtr.core.generated.map;

import org.mtr.core.serializer.*;
import org.mtr.core.tool.*;
import javax.annotation.*;
import org.mtr.core.map.*;

public abstract class ClientsSchema implements SerializedDataBase {

	protected final long cachedResponseTime;

	protected final it.unimi.dsi.fastutil.objects.ObjectArrayList<Client> clients = new it.unimi.dsi.fastutil.objects.ObjectArrayList<Client>();

	protected ClientsSchema(final long cachedResponseTime) {
		this.cachedResponseTime = cachedResponseTime;
	}

	protected ClientsSchema(final ReaderBase readerBase) {
		cachedResponseTime = readerBase.getLong("cachedResponseTime", 0);
	}

	public void updateData(final ReaderBase readerBase) {
		readerBase.iterateReaderArray("clients", clients::clear, readerBaseChild -> clients.add(new Client(readerBaseChild)));
	}

	public void serializeData(final WriterBase writerBase) {
		writerBase.writeLong("cachedResponseTime", cachedResponseTime);
		serializeClients(writerBase);
	}

	@Nonnull
	public String toString() {
		return ""
			+ "cachedResponseTime: " + cachedResponseTime + "\n"
			+ "clients: " + clients + "\n"
		;
	}

	protected void serializeClients(final WriterBase writerBase) {
		writerBase.writeDataset(clients, "clients");
	}
}