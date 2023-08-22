package me.ramidzkh.qc.server;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

/**
 * An association between a client valid certificate and a guaranteed login with a UUID and username combination,
 * bypassing Mojang authentication servers.
 * <p>
 * An extension is to be allocated within the client certificate, with {@link GameProfileCodec#OID the allocated OID},
 * encapsulating the following ASN.1 data in DER format:
 *
 * <pre>
 *     GameProfile ::= SEQUENCE {
 *         uuid OCTET STRING (SIZE(16)),
 *         name UTF8String,
 *         properties SEQUENCE OF Property
 *     }
 *
 *     Property ::= SEQUENCE {
 *         name UTF8String,
 *         value UTF8String,
 *         signature UTF8String OPTIONAL
 *     }
 * </pre>
 */
public class GameProfileCodec {

    public static final String OID = "1.3.6.1.4.1.9999999.1.1";

    public static GameProfile read(ByteBuffer buffer) {
        // trim octet stream
        if (buffer.get(buffer.position()) == 0x04) {
            buffer.get();
            return read(enter(buffer));
        }

        ensure(buffer, 0x30, "GameProfile");
        var sequence = enter(buffer);

        ensure(sequence, 0x04, "UUID");
        ensure(sequence, 0x10, "UUID"); // Against DER to use long-form if short-form is possible
        var uuid = new UUID(sequence.getLong(), sequence.getLong());
        var username = readUTF8String(sequence);

        var profile = new GameProfile(uuid, username);

        ensure(sequence, 0x30, "properties");
        var propertySequence = enter(sequence);

        while (propertySequence.hasRemaining()) {
            ensure(propertySequence, 0x30, "property");
            var property = enter(propertySequence);

            var name = readUTF8String(property);
            var value = readUTF8String(property);
            var signature = property.hasRemaining() ? readUTF8String(property) : null;

            profile.getProperties().put(name, new Property(name, value, signature));
        }

        return profile;
    }

    private static ByteBuffer enter(ByteBuffer buffer) {
        var length = readLength(buffer);
        var slice = buffer.duplicate().position(buffer.position()).limit(buffer.position() + length);
        buffer.position(buffer.position() + length);
        return slice;
    }

    private static int readLength(ByteBuffer buffer) {
        var firstByte = buffer.get() & 0xFF;

        // Short form
        if ((firstByte & 0x80) == 0) {
            return firstByte;
        }

        // Long form
        var numBytes = firstByte & 0x7F;

        // Limit to 4 bytes (32-bit integer max length)
        if (numBytes == 0 || numBytes > 4) {
            throw new IllegalArgumentException(
                    "Invalid DER length encoding @ " + Integer.toHexString(buffer.position() & 0xff) + ", asking for "
                            + Integer.toHexString(numBytes & 0xff) + " with " + Integer.toHexString(firstByte & 0xff));
        }

        var length = 0;

        for (var i = 0; i < numBytes; i++) {
            length = length << 8 | buffer.get() & 0xFF;
        }

        return length;
    }

    private static String readUTF8String(ByteBuffer buffer) {
        ensure(buffer, 0x0C, "string");
        var stringLength = readLength(buffer);
        var string = StandardCharsets.UTF_8.decode(buffer.duplicate().limit(buffer.position() + stringLength))
                .toString();
        buffer.position(buffer.position() + stringLength);
        return string;
    }

    private static void ensure(ByteBuffer buffer, int want, String name) {
        var got = buffer.get();

        if (got != want) {
            throw new RuntimeException(
                    "Expected " + name + ", got 0x" + Integer.toHexString(got & 0xFF) + " @ " + buffer.position());
        }
    }

    public static byte[] write(GameProfile profile) {
        var uuidBuffer = allocate(0x04, 0x10);
        uuidBuffer.putLong(profile.getId().getMostSignificantBits());
        uuidBuffer.putLong(profile.getId().getLeastSignificantBits());

        var nameBytes = writeUTF8String(profile.getName());

        var propertiesList = new ArrayList<byte[]>();
        var propertiesTotalLength = 0;

        for (var entry : profile.getProperties().entries()) {
            var prop = entry.getValue();
            var propNameBytes = writeUTF8String(prop.getName());
            var valueBytes = writeUTF8String(prop.getValue());

            var propertyLength = propNameBytes.length + valueBytes.length;

            byte[] signatureBytes = null;

            if (prop.getSignature() != null) {
                signatureBytes = writeUTF8String(prop.getSignature());
                propertyLength += signatureBytes.length;
            }

            var propertyBuffer = allocate(0x30, propertyLength);
            propertyBuffer.put(propNameBytes);
            propertyBuffer.put(valueBytes);

            if (signatureBytes != null) {
                propertyBuffer.put(signatureBytes);
            }

            propertiesList.add(propertyBuffer.array());
            propertiesTotalLength += propertyBuffer.array().length;
        }

        var propertiesBuffer = allocate(0x30, propertiesTotalLength);

        for (var propertyBytes : propertiesList) {
            propertiesBuffer.put(propertyBytes);
        }

        var totalLength = uuidBuffer.array().length + nameBytes.length + propertiesBuffer.array().length;
        var result = allocate(0x30, totalLength);
        result.put(uuidBuffer.array());
        result.put(nameBytes);
        result.put(propertiesBuffer.clear());

        return result.array();
    }

    private static byte[] writeUTF8String(String str) {
        var strBytes = str.getBytes(StandardCharsets.UTF_8);
        var buffer = allocate(0x0C, strBytes.length);
        buffer.put(strBytes);
        return buffer.array();
    }

    private static ByteBuffer allocate(int token, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        } else if (length < 0x80) {
            var buffer = ByteBuffer.allocate(2 + length);
            buffer.put((byte) token);
            buffer.put((byte) length);
            return buffer;
        } else if (length <= 0xFF) {
            var buffer = ByteBuffer.allocate(3 + length);
            buffer.put((byte) token);
            buffer.put((byte) 0x81);
            buffer.put((byte) length);
            return buffer;
        } else if (length <= 0xFFFF) {
            var buffer = ByteBuffer.allocate(4 + length);
            buffer.put((byte) token);
            buffer.put((byte) 0x82);
            buffer.put((byte) (length >> 8));
            buffer.put((byte) length);
            return buffer;
        } else if (length <= 0xFFFFFF) {
            var buffer = ByteBuffer.allocate(5 + length);
            buffer.put((byte) token);
            buffer.put((byte) 0x83);
            buffer.put((byte) (length >> 16));
            buffer.put((byte) (length >> 8));
            buffer.put((byte) length);
            return buffer;
        } else {
            var buffer = ByteBuffer.allocate(6 + length);
            buffer.put((byte) token);
            buffer.put((byte) 0x84);
            buffer.put((byte) (length >> 24));
            buffer.put((byte) (length >> 16));
            buffer.put((byte) (length >> 8));
            buffer.put((byte) length);
            return buffer;
        }
    }
}
