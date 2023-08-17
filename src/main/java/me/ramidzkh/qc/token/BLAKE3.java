package me.ramidzkh.qc.token;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

public class BLAKE3 {

    private static final int OUT_LEN = 32;
    private static final int KEY_LEN = 32;
    private static final int BLOCK_LEN = 64;
    private static final int CHUNK_LEN = 1024;

    private static final int CHUNK_START = 1 << 0;
    private static final int CHUNK_END = 1 << 1;
    private static final int PARENT = 1 << 2;
    private static final int ROOT = 1 << 3;
    private static final int KEYED_HASH = 1 << 4;
    private static final int DERIVE_KEY_CONTEXT = 1 << 5;
    private static final int DERIVE_KEY_MATERIAL = 1 << 6;

    private static final int[] IV = { 0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A, 0x510E527F, 0x9B05688C,
            0x1F83D9AB, 0x5BE0CD19 };

    private static final int[] MSG_PERMUTATION = { 2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8 };

    // The mixing function, G, which mixes either a column or a diagonal.
    private static void g(int[] state, int a, int b, int c, int d, int mx, int my) {
        state[a] = state[a] + state[b] + mx;
        state[d] = Integer.rotateRight(state[d] ^ state[a], 16);
        state[c] = state[c] + state[d];
        state[b] = Integer.rotateRight(state[b] ^ state[c], 12);
        state[a] = state[a] + state[b] + my;
        state[d] = Integer.rotateRight(state[d] ^ state[a], 8);
        state[c] = state[c] + state[d];
        state[b] = Integer.rotateRight(state[b] ^ state[c], 7);
    }

    private static void round(int[] state, int[] m) {
        // Mix the columns.
        g(state, 0, 4, 8, 12, m[0], m[1]);
        g(state, 1, 5, 9, 13, m[2], m[3]);
        g(state, 2, 6, 10, 14, m[4], m[5]);
        g(state, 3, 7, 11, 15, m[6], m[7]);

        // Mix the diagonals.
        g(state, 0, 5, 10, 15, m[8], m[9]);
        g(state, 1, 6, 11, 12, m[10], m[11]);
        g(state, 2, 7, 8, 13, m[12], m[13]);
        g(state, 3, 4, 9, 14, m[14], m[15]);
    }

    private static int[] permute(int[] m) {
        int[] permuted = new int[16];

        for (int i = 0; i < 16; i++) {
            permuted[i] = m[MSG_PERMUTATION[i]];
        }

        return permuted;
    }

    private static int[] compress(int[] chainingValue, int[] blockWords, long counter, int blockLen, int flags) {
        int[] state = new int[16];
        System.arraycopy(chainingValue, 0, state, 0, 8);
        System.arraycopy(IV, 0, state, 8, 4);
        state[12] = (int) (counter & 0xffffffffL);
        state[13] = (int) (counter >> 32 & 0xffffffffL);
        state[14] = blockLen;
        state[15] = flags;

        round(state, blockWords); // Round 1
        blockWords = permute(blockWords);
        round(state, blockWords); // Round 2
        blockWords = permute(blockWords);
        round(state, blockWords); // Round 3
        blockWords = permute(blockWords);
        round(state, blockWords); // Round 4
        blockWords = permute(blockWords);
        round(state, blockWords); // Round 5
        blockWords = permute(blockWords);
        round(state, blockWords); // Round 6
        blockWords = permute(blockWords);
        round(state, blockWords); // Round 7

        for (int i = 0; i < 8; i++) {
            state[i] ^= state[i + 8];
            state[i + 8] ^= chainingValue[i];
        }

        return state;
    }

    private static int[] wordsFromLEBytes(byte[] bytes) {
        int[] words = new int[bytes.length / 4];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(words);
        return words;
    }

    private static Node parentNode(int[] leftChildCV, int[] rightChildCV, int[] key, int flags) {
        int[] blockWords = new int[16];
        System.arraycopy(leftChildCV, 0, blockWords, 0, 8);
        System.arraycopy(rightChildCV, 0, blockWords, 8, 8);
        return new Node(key, blockWords, 0, BLOCK_LEN, PARENT | flags);
    }

    private static int[] parentCV(int[] leftChildCV, int[] rightChildCV, int[] key, int flags) {
        return parentNode(leftChildCV, rightChildCV, key, flags).chainingValue();
    }

    private record Node(int[] inputChainingValue, int[] blockWords, long counter, int blockLen, int flags) {
        private int[] chainingValue() {
            return compress(inputChainingValue, blockWords, counter, blockLen, flags);
        }

        private byte[] rootOutputBytes(int outLen) {
            int outputCounter = 0;
            int outputsNeeded = Math.floorDiv(outLen, 2 * OUT_LEN) + 1;

            ByteBuffer hash = ByteBuffer.allocate(64 * ((outLen + 3) / 4)); // 64 * ceil(outLin / 4)
            IntBuffer words = hash.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

            while (outputCounter < outputsNeeded) {
                words.put(compress(inputChainingValue, blockWords, outputCounter++, blockLen, flags | ROOT));

                if (outputCounter * 64 >= outLen) {
                    var output = new byte[outLen];
                    hash.get(output);
                    return output;
                }
            }

            throw new IllegalStateException(
                    "Uh oh something has gone horribly wrong. Please create an issue on https://github.com/rctcwyvrn/blake3");
        }
    }

    private static class ChunkState {
        private int[] chainingValue;
        private final long chunkCounter;
        private byte[] block = new byte[BLOCK_LEN];
        private byte blockLen = 0;
        private byte blocksCompressed = 0;
        private final int flags;

        public ChunkState(int[] chainingValue, long chunkCounter, byte[] block, byte blockLen, byte blocksCompressed,
                int flags) {
            this.chainingValue = chainingValue;
            this.chunkCounter = chunkCounter;
            this.block = block;
            this.blockLen = blockLen;
            this.blocksCompressed = blocksCompressed;
            this.flags = flags;
        }

        public ChunkState(int[] key, long chunkCounter, int flags) {
            this.chainingValue = key;
            this.chunkCounter = chunkCounter;
            this.flags = flags;
        }

        public int len() {
            return BLOCK_LEN * blocksCompressed + blockLen;
        }

        private int startFlag() {
            return blocksCompressed == 0 ? CHUNK_START : 0;
        }

        private void update(byte[] input, int offset, int length) {
            int currPos = 0;

            while (currPos < length) {
                // Chain the next 64 byte block into this chunk/node
                if (blockLen == BLOCK_LEN) {
                    int[] blockWords = wordsFromLEBytes(block);
                    this.chainingValue = compress(this.chainingValue, blockWords, this.chunkCounter, BLOCK_LEN,
                            this.flags | this.startFlag());
                    blocksCompressed++;
                    this.block = new byte[BLOCK_LEN];
                    this.blockLen = 0;
                }

                // Take bytes out of the input and update
                int want = BLOCK_LEN - this.blockLen; // How many bytes we need to fill up the current block
                int canTake = Math.min(want, length - currPos);
                System.arraycopy(input, currPos + offset, block, blockLen, canTake);

                blockLen += canTake;
                currPos += canTake;
            }
        }

        private Node createNode() {
            return new Node(chainingValue, wordsFromLEBytes(block), chunkCounter, blockLen,
                    flags | startFlag() | CHUNK_END);
        }

        public ChunkState fork() {
            return new ChunkState(chainingValue.clone(), chunkCounter, block.clone(), blockLen, blocksCompressed,
                    flags);
        }
    }

    private ChunkState chunkState;
    private int[] key;
    private final int[][] cvStack = new int[54][];
    private byte cvStackLen = 0;
    private int flags;

    private BLAKE3(ChunkState chunkState, int[] key, byte cvStackLen, int flags) {
        this.chunkState = chunkState;
        this.key = key;
        this.cvStackLen = cvStackLen;
        this.flags = flags;
    }

    private BLAKE3() {
        initialize(IV, 0);
    }

    private BLAKE3(byte[] key) {
        initialize(wordsFromLEBytes(key), KEYED_HASH);
    }

    private BLAKE3(String context) {
        BLAKE3 contextHasher = new BLAKE3();
        contextHasher.initialize(IV, DERIVE_KEY_CONTEXT);
        contextHasher.update(context.getBytes(StandardCharsets.UTF_8));
        int[] contextKey = wordsFromLEBytes(contextHasher.digest());
        initialize(contextKey, DERIVE_KEY_MATERIAL);
    }

    private void initialize(int[] key, int flags) {
        this.chunkState = new ChunkState(key, 0, flags);
        this.key = key;
        this.flags = flags;
    }

    public void update(byte[] input) {
        update(input, 0, input.length);
    }

    public void update(byte[] input, int offset, int length) {
        int currPos = 0;

        while (currPos < length) {
            // If this chunk has chained in 16 64 bytes of input, add its CV to the stack
            if (chunkState.len() == CHUNK_LEN) {
                int[] chunkCV = chunkState.createNode().chainingValue();
                long totalChunks = chunkState.chunkCounter + 1;
                addChunkChainingValue(chunkCV, totalChunks);
                chunkState = new ChunkState(key, totalChunks, flags);
            }

            int want = CHUNK_LEN - chunkState.len();
            int take = Math.min(want, length - currPos);
            chunkState.update(input, offset + currPos, take);
            currPos += take;
        }
    }

    public byte[] digest(int hashLen) {
        Node node = chunkState.createNode();
        int parentNodesRemaining = cvStackLen;

        while (parentNodesRemaining > 0) {
            node = parentNode(cvStack[--parentNodesRemaining], node.chainingValue(), key, flags);
        }

        return node.rootOutputBytes(hashLen);
    }

    public byte[] digest() {
        return digest(32);
    }

    private void pushStack(int[] cv) {
        cvStack[cvStackLen++] = cv;
    }

    private int[] popStack() {
        return cvStack[--cvStackLen];
    }

    private void addChunkChainingValue(int[] newCV, long totalChunks) {
        while ((totalChunks & 1) == 0) {
            newCV = parentCV(popStack(), newCV, key, flags);
            totalChunks >>= 1;
        }

        pushStack(newCV);
    }

    public BLAKE3 fork() {
        BLAKE3 fork = new BLAKE3(chunkState.fork(), key, cvStackLen, flags);

        for (int i = 0; i < cvStackLen; i++) {
            fork.cvStack[i] = cvStack[i].clone();
        }

        return fork;
    }

    public static BLAKE3 newInstance() {
        return new BLAKE3();
    }

    public static BLAKE3 newKeyedHasher(byte[] key) {
        if (key.length != KEY_LEN) {
            throw new IllegalStateException("Invalid key length");
        }

        return new BLAKE3(key);
    }

    public static BLAKE3 newKeyDerivationHasher(String context) {
        return new BLAKE3(context);
    }
}
