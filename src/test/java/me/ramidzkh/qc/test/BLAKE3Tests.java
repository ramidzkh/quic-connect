package me.ramidzkh.qc.test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.ramidzkh.qc.token.BLAKE3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

public class BLAKE3Tests {

    private static final byte[] INCREMENTING = new byte[251];

    static {
        for (var i = 0; i < INCREMENTING.length; i++) {
            INCREMENTING[i] = (byte) i;
        }
    }

    @Test
    public void basicHash() {
        var hasher = BLAKE3.newInstance();
        hasher.update("This is a string".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(bytes("718b749f12a61257438b2ea6643555fd995001c9d9ff84764f93f82610a780f2"), hasher.digest(32));
    }

    @Test
    public void testLongerHash() {
        var hasher = BLAKE3.newInstance();
        hasher.update("This is a string".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(bytes(
                "718b749f12a61257438b2ea6643555fd995001c9d9ff84764f93f82610a780f243a9903464658159cf8b216e79006e12ef3568851423fa7c97002cbb9ca4dc44b4185bb3c6d18cdd1a991c2416f5e929810290b24bf24ba6262012684b6a0c4e096f55e8b0b4353c7b04a1141d25afd71fffae1304a5abf0c44150df8b8d4017"),
                hasher.digest(128));
    }

    @Test
    public void testShorterHash() {
        var hasher = BLAKE3.newInstance();
        hasher.update("This is a string".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(bytes("718b749f12a61257438b2ea6643555fd"), hasher.digest(16));
    }

    @Test
    public void testRawByteHash() {
        var hasher = BLAKE3.newInstance();
        hasher.update("This is a string".getBytes(StandardCharsets.UTF_8));
        var digest = hasher.digest();
        assertArrayEquals(digest, new byte[] {
                113, -117, 116, -97, 18, -90, 18, 87, 67, -117, 46, -90, 100, 53, 85, -3, -103, 80, 1, -55, -39, -1,
                -124, 118, 79, -109, -8, 38, 16, -89, -128, -14
        });
    }

    @Test
    public void testKDFHash() {
        var hasher = BLAKE3.newKeyDerivationHasher("meowmeowverysecuremeowmeow");
        hasher.update("This is a string".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(bytes("348de7e5f8f804216998120d1d05c6d233d250bdf40220dbf02395c1f89a73f7"), hasher.digest(32));
    }

    @RepeatedTest(1000)
    public void forks() {
        var random = ThreadLocalRandom.current();
        var bytes = new byte[32];
        random.nextBytes(bytes);

        var root = BLAKE3.newKeyedHasher(bytes);

        random.nextBytes(bytes);
        root.update(bytes);

        var a = root.fork();
        var b = root.fork();

        for (var i = 0; i < 1000; i++) {
            random.nextBytes(bytes);
            a.update(bytes);
            b.update(bytes);

            // Segmented
            root.update(bytes, 0, 32 - 4);
            root.update(bytes, 32 - 4, 3);
            root.update(bytes, 32 - 1, 1);
        }

        var aDigest = a.digest();
        var bDigest = b.digest();
        var rootDigest = root.digest();

        Assertions.assertArrayEquals(aDigest, bDigest);
        Assertions.assertArrayEquals(aDigest, rootDigest);
    }

    public byte[] getTestVectorInput(int inputLen) {
        var remainder = Arrays.copyOfRange(INCREMENTING, 0, inputLen % 251);
        var input = new byte[inputLen];

        var x = 0;

        while (x + 251 < inputLen) {
            System.arraycopy(INCREMENTING, 0, input, x, 251);
            x += 251;
        }

        System.arraycopy(remainder, 0, input, inputLen - remainder.length, remainder.length);
        return input;
    }

    @Test
    public void officialTestVectors() throws IOException {
        try (var file = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/test_vectors.json"), "Test vectors not found"),
                StandardCharsets.UTF_8)) {
            var reader = new Gson().fromJson(file, JsonObject.class);
            var key = reader.getAsJsonPrimitive("key").getAsString();
            var contextString = reader.getAsJsonPrimitive("context_string").getAsString();

            for (var c : reader.getAsJsonArray("cases")) {
                var testCase = c.getAsJsonObject();
                var inputLen = testCase.get("input_len").getAsInt();
                var inputData = getTestVectorInput(inputLen);
                var blake3 = BLAKE3.newInstance();
                var keyed = BLAKE3.newKeyedHasher(key.getBytes(StandardCharsets.US_ASCII));
                var kdf = BLAKE3.newKeyDerivationHasher(contextString);

                blake3.update(inputData);
                keyed.update(inputData);
                kdf.update(inputData);

                assertArrayEquals(bytes(testCase.getAsJsonPrimitive("hash").getAsString()), blake3.digest(131));
                assertArrayEquals(bytes(testCase.getAsJsonPrimitive("keyed_hash").getAsString()), keyed.digest(131));
                assertArrayEquals(bytes(testCase.getAsJsonPrimitive("derive_key").getAsString()), kdf.digest(131));

                assertArrayEquals(bytes(testCase.getAsJsonPrimitive("hash").getAsString().substring(0, 64)),
                        blake3.digest(32));
                assertArrayEquals(bytes(testCase.getAsJsonPrimitive("keyed_hash").getAsString().substring(0, 64)),
                        keyed.digest(32));
                assertArrayEquals(bytes(testCase.getAsJsonPrimitive("derive_key").getAsString().substring(0, 64)),
                        kdf.digest(32));
            }
        }
    }

    private static byte[] bytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }

        return data;
    }
}
