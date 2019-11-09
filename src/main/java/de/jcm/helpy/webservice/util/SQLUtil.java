package de.jcm.helpy.webservice.util;

import java.nio.ByteBuffer;
import java.util.UUID;

public class SQLUtil
{
	public static class UUIDHelper
	{
		public static UUID fromBytes(byte[] bytes)
		{
			ByteBuffer buffer = ByteBuffer.wrap(bytes);

			long most = buffer.getLong();
			long least = buffer.getLong();

			return new UUID(most, least);
		}

		public static byte[] toBytes(UUID uuid)
		{
			byte[] array = new byte[16];
			ByteBuffer buffer = ByteBuffer.wrap(array);

			buffer.putLong(uuid.getMostSignificantBits());
			buffer.putLong(uuid.getLeastSignificantBits());

			return array;
		}
	}
}
