package codegen.translators.bloom;

public class HashFactory {
    private int numHashes;
    private final HashFunction[] hashFunctions;

    public static int calcNumHashes() {
        return 8;//new HashFactory().numHashes;
    }

    public HashFactory() {
        // Two are 32-bit, one is 64-bit: each hash is 16 bits so 8 in all
        hashFunctions = new HashFunction[]{
            new Murmur(), new Jenkins(), new FNV()
        };
        numHashes = 0;
        for(HashFunction hashFunction : hashFunctions){
            numHashes += hashFunction.numHashes();
        }
    }

    public int getNumHashes() {
        return new HashFactory().numHashes;
    }

    public int[] hashAll(String text){
        int[] hashes = new int[numHashes];
        int j = 0;
        for(HashFunction hashFunction : hashFunctions){
            j = hashFunction.get(text, j, hashes);
        }
        return hashes;
    }

    private interface HashFunction{
        int numHashes();
        int get(String text, int index, int[] hashes);
    }

    /**@author Viliam Holub, adapted for this implementation by Dave Swanson */
    private static class Murmur implements HashFunction{
        public static final int NUM_HASHES = 2;
        static final int seed = 0x9747b28c;
        // 'm' and 'r' are mixing constants generated offline.
        // They're not really 'magic', they just happen to work well.
        static final int m = 0x5bd1e995;
        static final int r = 24;

        @Override
        public int numHashes(){
            return NUM_HASHES;
        }

        @Override
        public int get(String text, int index, int[] hashes){
            int hash = this.get(text);
            hashes[index++] = hash & 0xFFFF;
            hashes[index++] = (hash >> 16) & 0xFFFF;
            return index;
        }

        private int get(String text) {
            final byte[] data = text.getBytes();
            final int len = data.length;

            // Initialize the hash to a random value
            int h = seed^len;
            int length4 = len/4;

            for (int i=0; i<length4; i++) {
                final int i4 = i*4;
                int k = (data[i4] & 0xff) +((data[i4 + 1] & 0xff) << 8)
                        +((data[i4+2] & 0xff) << 16) +((data[i4+3] & 0xff) << 24);
                k *= m;
                k ^= k >>> r;
                k *= m;
                h *= m;
                h ^= k;
            }

            // Handle the last few bytes of the input array
            switch (len%4) {
                case 3: h ^= (data[(len &~3) +2] & 0xff) << 16;
                case 2: h ^= (data[(len &~3) +1] & 0xff) << 8;
                case 1: h ^= (data[len &~3] & 0xff);
                    h *= m;
            }

            h ^= h >>> 13;
            h *= m;
            h ^= h >>> 15;

            return h;
        }
    }

    /**@author $Author: vijaykandy, adapted for this implementation by Dave Swanson */
    private static class Jenkins implements HashFunction {
        public static final int NUM_HASHES = 4;
        @Override
        public int numHashes(){
            return NUM_HASHES;
        }

        @Override
        public int get(String text, int index, int[] hashes){
            long hash = this.get(text);
            for(int i = 0; i < NUM_HASHES; i++){
                hashes[index] = ((int)hash & 0xFFFF);
                hash >>= 16;
                index ++;
            }
            return index;
        }

        private long get(String text) {
            final byte[] data = text.getBytes();
            int len = data.length;
            int a, b, c;

            a = b = c = 0xdeadbeef + len;

            int offset = 0;
            while (len > 12) {
                a += data[offset];
                a += data[offset + 1] << 8;
                a += data[offset + 2] << 16;
                a += data[offset + 3] << 24;
                b += data[offset + 4];
                b += data[offset + 5] << 8;
                b += data[offset + 6] << 16;
                b += data[offset + 7] << 24;
                c += data[offset + 8];
                c += data[offset + 9] << 8;
                c += data[offset + 10] << 16;
                c += data[offset + 11] << 24;

                // mix(a, b, c);
                a -= c;
                a ^= rot(c, 4);
                c += b;
                b -= a;
                b ^= rot(a, 6);
                a += c;
                c -= b;
                c ^= rot(b, 8);
                b += a;
                a -= c;
                a ^= rot(c, 16);
                c += b;
                b -= a;
                b ^= rot(a, 19);
                a += c;
                c -= b;
                c ^= rot(b, 4);
                b += a;

                len -= 12;
                offset += 12;
            }

            switch (len) {
                case 12:
                    c += data[offset + 11] << 24;
                case 11:
                    c += data[offset + 10] << 16;
                case 10:
                    c += data[offset + 9] << 8;
                case 9:
                    c += data[offset + 8];
                case 8:
                    b += data[offset + 7] << 24;
                case 7:
                    b += data[offset + 6] << 16;
                case 6:
                    b += data[offset + 5] << 8;
                case 5:
                    b += data[offset + 4];
                case 4:
                    a += data[offset + 3] << 24;
                case 3:
                    a += data[offset + 2] << 16;
                case 2:
                    a += data[offset + 1] << 8;
                case 1:
                    a += data[offset];
                    break;
                case 0:
                    return c;
            }

            // Final mixing of three 32-bit values in to c
            c ^= b;
            c -= rot(b, 14);
            a ^= c;
            a -= rot(c, 11);
            b ^= a;
            b -= rot(a, 25);
            c ^= b;
            c -= rot(b, 16);
            a ^= c;
            a -= rot(c, 4);
            b ^= a;
            b -= rot(a, 14);
            c ^= b;
            c -= rot(b, 24);

            return ((((long) c) << 32)) | ((long) b &0xFFFFFFFFL);
        }

        long rot(int x, int distance) {
            return (x << distance) | (x >>> (32 - distance));
        }
    }

    /** @author Makoto YUI (yuin405+xbird@gmail.com), adapted for this implementation by Dave Swanson */
    private static class FNV implements HashFunction {
        public static final int NUM_HASHES = 2;
        private final int FNV_32_INIT = 0x811c9dc5;
        private final int FNV_32_PRIME = 0x01000193;

        @Override
        public int numHashes(){
            return NUM_HASHES;
        }

        @Override
        public int get(String text, int index, int[] hashes){
            int hash = this.get(text);
            hashes[index++] = hash & 0xFFFF;
            hashes[index++] = (hash >> 16) & 0xFFFF;
            return index;
        }

        private int get(String text) {
            int rv = FNV_32_INIT;
            final int len = text.length();
            for(int i = 0; i < len; i++) {
                rv ^= text.charAt(i);
                rv *= FNV_32_PRIME;
            }
            return rv;
        }


    }
}
