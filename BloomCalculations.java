package codegen.translators.bloom;

import datasource.TextSourceFile;

import java.util.Random;

public class BloomCalculations {
    private final CoreCalculations coreCalculations;
    private final BloomMeasurements bloomMeasurements;

    public static final double EPSILON = .0001;

    public BloomCalculations() {
        coreCalculations = new CoreCalculations();
        bloomMeasurements = new BloomMeasurements();
    }

    public int bitsNeeded(int n, double p){
        return coreCalculations.bitsNeeded(n, p);
    }

    public int hashesNeeded(int n, int m){
        return coreCalculations.hashesNeeded(n, m);
    }

    public double probabilityFalse(int n, int m, int k){
        return coreCalculations.probabilityFalse(n, m, k);
    }

    public int calcSize(int n, double p){
        return coreCalculations.optimizeSize(n, p);
    }

    public int measureSize(int n, double p){
        if(n % 2 != 0){
            n++;
        }
        String[] arr = new BloomCalculations.RandStrings().get(n);
        return bloomMeasurements.measureSize(arr, p, coreCalculations);
    }

    public int measureSize(String fileName, double p){
        String[] arr = new TextSourceFile(fileName).toArray();
        return bloomMeasurements.measureSize(arr, p, coreCalculations);
    }

    public int getBestSize() {
        return bloomMeasurements.getBestSize();
    }

    public double getBestProbability() {
        return bloomMeasurements.getBestProbability();
    }

    public static class BloomMeasurements {
        private int inputSize, prevSize, currSize, numHashes;
        private double prevProbability, shrinkFactor;

        public BloomMeasurements(){
            numHashes = HashFactory.calcNumHashes();
        }
        public int getBestSize() {
            return prevSize;
        }

        public double getBestProbability() {
            return prevProbability;
        }

        public int measureSize(String[] arr, double p, CoreCalculations coreCalculations){
            shrinkFactor = 0.9;
            inputSize = arr.length;

            double currTest = p;
            currSize = coreCalculations.optimizeSize(inputSize, numHashes, p);
            prevSize = 0;
            //System.out.printf("\ninputSize = %d, calc size = %d \n", inputSize, currSize );

            for(int i = 0; i < 10; i ++){
                prevProbability = currTest;
                if((currTest = measureOne(arr)) > p){
                    //System.out.printf("took %d iterations\n", i + 1);
                    return (i == 0)? currSize : prevSize;
                }
                //System.out.printf("%d: bestSize = %d, currTest = %f \n", i, bestSize, currTest);
                if(currSize == prevSize){
                    shrinkFactor *= 0.9;
                }
                else{
                    prevSize = currSize;
                }
                //System.out.println("shrink to " + shrinkFactor);
                currSize = coreCalculations.makeMultiple((int)(currSize * shrinkFactor));
            }
            //System.out.printf("took > 10 iterations\n");
            return currSize;
        }

        private double measureOne(String[] test){
            BloomStore bloomStore = new BloomStore(currSize);

            for(int i = 0; i < inputSize; i++){
                bloomStore.set(test[i]);
            }
            for(int i = 0; i < inputSize; i++){
                if(!bloomStore.get(test[i])){
                    throw new IllegalStateException("Bloom false negative on '" + test[i] + "'");
                }
            }

            String[] control = new RandStrings().get(test);
            int badHits = 0;
            for(int i = 0; i < inputSize; i++){
                if(bloomStore.get(control[i])){
                    badHits++;
                }
            }
            return badHits/(double)currSize;
        }

    }

    public static class CoreCalculations{
        public static final double EPSILON = .00001;

        /**@param n Expected number of string elements to add
         * @param p Desired probability of false positive
         * @return number of bits needed */
        public int bitsNeeded(int n, double p){
            double nLnP = n * Math.log(p);
            double ln2Squared = -0.4084530139182015;
            return (int)Math.ceil( nLnP / ln2Squared );
        }

        /**@param n Expected number of string elements to add
         * @param m Number of bits available in store
         * @return optimum number of hashes */
        public int hashesNeeded(int n, int m){
            double ln2 = 0.6931471805599453094172;
            //System.out.println(ln2 + " vs\n" + Math.log(2));
            return (int)Math.round((m/(double)n) * ln2);
        }

        /**@param n Expected number of string elements to add
         * @param m Number of bits available in store
         * @param k Number of hashes to be used
         * @return probability of false positive */
        public double probabilityFalse(int n, int m, int k){
            int kn = k * n;
            double inner = 1 - Math.pow(1 - 1/m, kn);
            return Math.pow(
                    (1 - Math.exp( -k * (double)n / (double)m )),
                    k
            );
        }

        public int optimizeSize(int n, double p){
            int k = HashFactory.calcNumHashes();
            return optimizeSize(n, k, p);
        }
        public int optimizeSize(int n, int k, double p){
            int m = this.bitsNeeded(n, p);
            int nudge, hurry = 300;
            int lastNudge = 0;

            for(int i = 0; i < 50; i ++){
                nudge = compareDouble(
                        probabilityFalse(n, m, k), p
                );
                if(nudge == 0){
                    break;
                }
                if(i > 0 && !sameSign(nudge, lastNudge)){// never happens with EPSILON = .00001;
                    hurry /= 2;
                }
                m += nudge * m * EPSILON * hurry;;
                lastNudge = nudge;
            }

            return this.makeMultiple(m);
        }

        private int compareDouble(double test, double target){
            double comp = test - target;
            if(Math.abs(comp) < EPSILON){
                return 0;
            }
            return (comp > 0)? 1 : -1;
        }

        private boolean sameSign(int a, int b){
            return a - b == 0;
        }

        private int makeMultiple(int m){
            int mod = m % Long.SIZE;
            return (mod == 0)? m : m + Long.SIZE - mod;
        }
    }

    public static class RandStrings{
        private final Random random;
        private final int minLen, maxLen;

        public RandStrings() {
            random = new Random();
            minLen = 3;
            maxLen = 13;
        }

        /**Single-String version, upper- and lower-case alpha only
         * Min length 3 gives 26^3 = 17,576 uniques (at min)
         * @return a nonsense word of randomized length and content */
        public String get(){
            int length = random.nextInt(maxLen - minLen) + minLen;
            char[] out = new char[length];
            for(int i = 0; i < length; i++){
                //int upperLower = (Math.random() > 0.5)? 65 : 97;
                int ascii = random.nextInt(26) + 97;//upperLower;
                out[i] = (char)ascii;
            }
            return new String(out);
        }

        /**Multi-String version; uniqueness within output list
         * @param length how many words
         * @return array of unique nonsense words */
        public String[] get(int length){
            String[] out = new String[length];
            String test;
            for(int i = 0; i < length; i++){
                do{
                    test = this.get();
                }
                while(findInArray(test, out));
                out[i] = test;
            }
            return out;
        }

        /**Generate a matching list, unique from 'other' and itself
         * @param other the test list
         * @return array of unique nonsense words */
        public String[] get(String[] other){
            int length = other.length;
            String[] out = new String[length];
            String test;
            for(int i = 0; i < length; i++){
                do{
                    test = this.get();
                }
                while(findInArray(test, out) || findInArray(test, other));
                out[i] = test;
            }
            return out;
        }

        private boolean findInArray(String target, String[] array){
            for(int i = 0; i < array.length; i++){
                if(array[i] == null){
                    break;
                }
                if(target.equals(array[i])){
                    return true;
                }
            }
            return false;
        }


    }
}
