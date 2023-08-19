package me.ramidzkh.qc.token;

import io.netty.incubator.codec.quic.QuicConnectionIdGenerator;
import io.netty.util.internal.ObjectUtil;

import java.nio.ByteBuffer;

/**
 * Secure {@link QuicConnectionIdGenerator} implementation based on keyed {@link BLAKE3}
 */
public class KeyedConnectionIdGenerator implements QuicConnectionIdGenerator {

    private static final int MAX_CONNECTION_ID_LENGTH = 20;

    private final BLAKE3 hash;

    public KeyedConnectionIdGenerator(byte[] key) {
        this.hash = BLAKE3.newKeyedHasher(key);
    }

    @Override
    public ByteBuffer newId(int length) {
        throw new UnsupportedOperationException(
                "KeyedConnectionIdGenerator should always have an input");
    }

    @Override
    public ByteBuffer newId(ByteBuffer input, int length) {
        ObjectUtil.checkNotNull(input, "input");
        ObjectUtil.checkPositive(input.remaining(), "input");
        ObjectUtil.checkInRange(length, 0, maxConnectionIdLength(), "length");

        var hash = this.hash.fork();

        if (input.hasArray()) {
            hash.update(input.array(), input.arrayOffset() + input.position(), input.remaining());
        } else {
            var buffer = new byte[input.remaining()];
            input.get(buffer);
            hash.update(buffer);
        }

        return ByteBuffer.wrap(hash.digest(length));
    }

    @Override
    public int maxConnectionIdLength() {
        return MAX_CONNECTION_ID_LENGTH;
    }

    @Override
    public boolean isIdempotent() {
        return true;
    }
}
