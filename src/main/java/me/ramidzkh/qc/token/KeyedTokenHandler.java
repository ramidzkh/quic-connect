package me.ramidzkh.qc.token;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.incubator.codec.quic.QuicTokenHandler;

import java.net.InetSocketAddress;
import java.security.MessageDigest;

/**
 * Secure {@link QuicTokenHandler} implementation based on keyed {@link BLAKE3}
 */
public class KeyedTokenHandler implements QuicTokenHandler {

    private static final int HASH_LENGTH = 32;
    private static final int MAX_CONNECTION_ID_LENGTH = 20;

    /**
     * Timestamp will be modulo'd by this window size, and included in the MAC. In verification, we check the last two
     * windows, so a given token expires in roughly this time frame.
     */
    private static final long TIMESTAMP_WINDOW_SIZE = 5 * 60 * 1000;

    private final BLAKE3 hash;

    public KeyedTokenHandler(byte[] key) {
        this.hash = BLAKE3.newKeyedHasher(key);
    }

    @Override
    public boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address) {
        out.writeBytes(hash(address, dcid, System.currentTimeMillis() / TIMESTAMP_WINDOW_SIZE));
        out.writeBytes(dcid, dcid.readerIndex(), dcid.readableBytes());
        return true;
    }

    @Override
    public int validateToken(ByteBuf token, InetSocketAddress address) {
        if (token.readableBytes() < HASH_LENGTH) {
            return -1;
        }

        var actual = new byte[HASH_LENGTH];
        token.getBytes(token.readerIndex(), actual);
        var dcid = token.slice(token.readerIndex() + HASH_LENGTH, token.readableBytes() - HASH_LENGTH);

        var windowId = System.currentTimeMillis() / TIMESTAMP_WINDOW_SIZE;
        var expectedHashNow = hash(address, dcid, windowId);
        var expectedHashPrev = hash(address, dcid, windowId - 1);

        // constant-time comparison
        var equalNow = MessageDigest.isEqual(expectedHashNow, actual);
        var equalPrev = MessageDigest.isEqual(expectedHashPrev, actual);

        if (equalNow | equalPrev) {
            return HASH_LENGTH;
        } else {
            return -1;
        }
    }

    private byte[] hash(InetSocketAddress address, ByteBuf dcid, long windowId) {
        if (dcid.readableBytes() > MAX_CONNECTION_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Connection ID may not exceed " + MAX_CONNECTION_ID_LENGTH + " bytes length");
        }

        var cleartext = Unpooled.buffer();
        cleartext.writeInt(address.getPort());
        cleartext.writeLong(windowId);
        cleartext.writeBytes(address.getAddress().getAddress());
        cleartext.writeBytes(dcid, dcid.readerIndex(), dcid.readableBytes());

        var hash = this.hash.fork();
        hash.update(cleartext.array(), cleartext.arrayOffset() + cleartext.readerIndex(), cleartext.readableBytes());
        cleartext.release();

        return hash.digest(HASH_LENGTH);
    }

    @Override
    public int maxTokenLength() {
        return HASH_LENGTH + MAX_CONNECTION_ID_LENGTH;
    }
}
