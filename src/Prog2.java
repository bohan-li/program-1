import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static java.lang.Math.pow;

public class Prog2 {
    // indices for desired fields to be printed for the assignment
    public static int CASEID_INDEX = 0, DATEDECISION_INDEX = 4, CASENAME_INDEX = 14;

    public static void main(String args[]) throws IOException {
        if (args == null || args.length < 1) throw new RuntimeException("Usage java Prog1B [file path]");
        String binFilename = args[0]; // filename for the .bin file
        RandomAccessFile file = new RandomAccessFile(new File(binFilename), "r"); // file providing access to the DB
        Index index = new Index(new BinaryFileDB(file)); // DB object constructed using the file
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNext()) {
            List<Object[]> queryResult = index.query(scanner.next());
            queryResult.forEach(Prog2::printEntry);
            System.out.println(queryResult.size());
        }
    }

    /**
     * @name printEntry
     * Prints appropriate information of the given data entry to the screen.
     * @param entry
     */
    private static void printEntry(Object[] entry) {
        System.out.println(entry[CASEID_INDEX] + " " + entry[DATEDECISION_INDEX] + " " + entry[CASENAME_INDEX] + " " + entry[Index.KEY_INDEX]);
    }

    public static class Index {
        public static int KEY_INDEX = 39; // field that directory is indexed on
        public static int KEY_CARDINALITY = 10; // number of different elements in key char, 10 since numeric
        public static int KEY_DIGITS = 6; // digits in index field

        private BinaryFileDB db; // access to the file
        private int maxDepth; // depth of lowest level bucket
        private Integer[] directory; // structure holding all bucket pointers, Integer so all entries are initialized to null
        private HashBucketFile hashBucketFile; // maintain pointer to hash bucket file
        private int numEntries; // number of entries in the index

        public Index(BinaryFileDB db) {
            this.db = db;
            this.maxDepth = 1;
            this.directory = new Integer[KEY_CARDINALITY];
            this.hashBucketFile = new HashBucketFile();
            this.numEntries = 0;

            // insert pointers to all elements from the db file
            for (int index = 0; index < db.getNumEntries(); index++) {
                try {
                    int issue = (Integer) db.get(index)[KEY_INDEX]; // issue value of the object at the given index
                    if (issue == -1)
                        continue;
                    insert(issue, index);
                    numEntries++;
                } catch (ClassCastException ex) {
                    continue;
                }
            }
        }

        private void insert(int key, int value) {
            BucketEntry thisEntry = new BucketEntry(key, value); // bucket entry to be inserted
            final int hash = key / (int) pow(KEY_CARDINALITY, KEY_DIGITS - maxDepth); // hashcode of the issue number

            if (directory[hash] == null) {
                directory[hash] = hashBucketFile.createBucket(maxDepth); // set pointer to new bucket
                hashBucketFile.addElementToBucket(directory[hash], thisEntry);
            }
            else if (!hashBucketFile.addElementToBucket(directory[hash], thisEntry)) { // bucket is full, time to split
                Bucket thisBucket = hashBucketFile.getBucket(directory[hash]); // temporarily store bucket to be split

                if (thisBucket.getDepth() == maxDepth) { // bucket is lowest level, allocate new directory
                    if (maxDepth == KEY_DIGITS) { // cannot split any further, and bucket is full
                        throw new RuntimeException("Error: element cannot be inserted, bucket is full at max depth");
                    }

                    Integer[] newDirectory = new Integer[directory.length * KEY_CARDINALITY]; // allocate new directory
                    maxDepth++;

                    // keep existing pointers to unchanged buckets
                    for (int i = 0; i < directory.length; i++) {
                        for (int j = KEY_CARDINALITY * i; j < KEY_CARDINALITY * (i + 1); j++) {
                            if (i != hash) newDirectory[j] = directory[i];
                        }
                    }

                    this.directory = newDirectory;

                    // reinsert elements in bucket
                    Arrays.asList(thisBucket.getEntries()).forEach(entry -> insert(entry.getKey(), entry.getIndex()));
                    insert(key, value);
                } else { // split existing bucket
                    // reset existing pointers to bucket
                    final int oldHash = key / (int) pow(KEY_CARDINALITY, KEY_DIGITS - thisBucket.getDepth()); // old hashcode of the issue number, at its depth
                    final int depthDiffFac = (int) pow(KEY_CARDINALITY, maxDepth - thisBucket.getDepth()); // factor of difference between the old hash and this one
                    for (int i = oldHash * depthDiffFac; i < (oldHash + 1) * depthDiffFac; i++) {
                        directory[i] = null;
                    }

                    // reinsert elements in the bucket
                    Arrays.asList(thisBucket.getEntries()).forEach(entry -> insert(entry.getKey(), entry.getIndex()));
                    insert(key, value);
                }
            }
        }

        public List<Object[]> query(String prefix) {
            List<Object[]> retval = new LinkedList<>(); // return value

            int matchLength = prefix.length(); // store length of prefix
            Bucket thisBucket = null;
            try {
                if (matchLength > KEY_DIGITS) throw new NumberFormatException();
                if (matchLength >= maxDepth) { // only one bucket to search, since prefix length is greater than hashcode digit length
                    int hash = Integer.parseInt(prefix.substring(0, maxDepth)); // hash value of the prefix

                    if (directory[hash] == null) return retval;
                    thisBucket = hashBucketFile.getBucket(directory[hash]);

                    Arrays.asList(thisBucket.getEntries()).forEach(entry -> {
                        String keyStr = String.format("%0" + KEY_DIGITS + "d", entry.getKey()); // key integer to 6 char string

                        if (keyStr.startsWith(prefix)) retval.add(db.get(entry.getIndex()));
                    });
                } else {
                    Set<Integer> reported = new HashSet<>(); // maintain pointers to buckets already reported

                    // search all buckets potentially with the given prefix, indices between values given by leftHash and rightHash
                    String leftHashStr = prefix, rightHashStr = prefix;
                    int leftHash, rightHash; // values represented by leftHashStr and rightHashStr, respectively


                    while (leftHashStr.length() < maxDepth) leftHashStr += "0";
                    leftHash = Integer.parseInt(leftHashStr);

                    rightHash = Integer.parseInt(rightHashStr) + 1;
                    rightHashStr = String.format("%0" + prefix.length() + "d", rightHash); // reattach leading 0's
                    while (rightHashStr.length() < maxDepth) rightHashStr += "0";
                    rightHash = Integer.parseInt(rightHashStr);

                    // search over buckets
                    for (int i = leftHash; i < rightHash; i++) {
                        if (directory[i] == null) continue;
                        else if (!reported.contains(directory[i])) { // ignore buckets already reported
                            reported.add(directory[i]);
                            thisBucket = hashBucketFile.getBucket(directory[i]);
                            Arrays.asList(thisBucket.getEntries()).forEach(entry -> {
                                String keyStr = String.format("%0" + KEY_DIGITS + "d", entry.getKey()); // key integer to 6 char string
                                if (keyStr.startsWith(prefix)) retval.add(db.get(entry.getIndex()));
                            });
                        }
                    }
                }
            } catch (NumberFormatException ex) {
                System.out.println("Query must be a digit sequence of up to 6 characters.");
            }
            
            return retval;
        }
    }

    public static class HashBucketFile {
        public static final String FILE_NAME = "hash_bucket_file.bin"; // file name for hash bucket file

        /*
            4 bytes                           ------ integer number of elements in bucket
            4 bytes                           ------ integer depth of bucket
        */
        public static final int BUCKET_METADATA_SIZE = 4 + 4;
        public static final int BUCKET_SIZE = BUCKET_METADATA_SIZE + Bucket.BUCKET_MAX_ENTRIES * BucketEntry.ENTRY_SIZE; // size of bucket in bytes

        private RandomAccessFile randomAccessFile; // file pointer for reading and writing
        private int numBuckets; // number of buckets in the hash bucket file

        public HashBucketFile() {
            try {
                randomAccessFile = new RandomAccessFile(new File(FILE_NAME), "rw");
            }
            catch (IOException ex) {
                System.out.println("Error: Could not create RAF.");
                System.exit(1);
            }

            numBuckets = 0;
        }

        public int createBucket(int depth) {
            int createdIndex = numBuckets++; // index of created bucket
            int startingFileIndex = getStartFileIndex(createdIndex); // index into RAF
            try {
                randomAccessFile.seek(startingFileIndex);
                randomAccessFile.writeInt(0); // write size of bucket
                randomAccessFile.writeInt(depth);
            } catch (IOException ex) {
                System.out.println("Error: write failed. Out of space?");
                System.exit(1);
            }
            return createdIndex;
        }

        public boolean addElementToBucket(int bucketIndex, BucketEntry entry) {
            int startingFileIndex = getStartFileIndex(bucketIndex); // index into RAF
            try {
                randomAccessFile.seek(startingFileIndex);
                int bucketSize = randomAccessFile.readInt(); // read size of bucket
                if (bucketSize == Bucket.BUCKET_MAX_ENTRIES) return false; // no more space to be inserted

                // go to position in RAF for entry to be inserted
                randomAccessFile.seek(startingFileIndex + BUCKET_METADATA_SIZE + bucketSize * BucketEntry.ENTRY_SIZE);

                // write entry
                randomAccessFile.writeInt(entry.getKey());
                randomAccessFile.writeInt(entry.getIndex());

                // increment size
                randomAccessFile.seek(startingFileIndex);
                randomAccessFile.writeInt(bucketSize + 1);
            } catch (IOException ex) {
                System.out.println("Error: file IO failed. Out of space?");
                System.exit(1);
            }
            return true;
        }

        public Bucket getBucket(int bucketIndex) {
            int startingFileIndex = getStartFileIndex(bucketIndex); // index into RAF
            if (bucketIndex < numBuckets && bucketIndex >= 0) {
                try {
                    randomAccessFile.seek(startingFileIndex);
                    int bucketSize = randomAccessFile.readInt();
                    int bucketDepth = randomAccessFile.readInt();
                    Bucket bucket = new Bucket(bucketDepth, new BucketEntry[bucketSize]); // initialize bucket in memory

                    // read entries
                    for (int i = 0; i < bucketSize; i++) {
                        // read entry
                        int key = randomAccessFile.readInt();
                        int index = randomAccessFile.readInt();

                        bucket.getEntries()[i] = new BucketEntry(key, index);
                    }
                    return bucket;
                } catch (IOException ex) {
                    System.out.println("Error: read failed.");
                    System.exit(1);
                }
            }
            return null;
        }

        private int getStartFileIndex(int hashBucketIndex) {
            return hashBucketIndex * BUCKET_SIZE;
        }
    }

    public static class Bucket {
        public static int BUCKET_MAX_ENTRIES = 250; // max elements in a bucket

        private final int depth; // depth of bucket
        private final BucketEntry[] entries; // entries in bucket

        public Bucket(int depth, BucketEntry[] entries) {
            this.depth = depth;
            this.entries = entries;
        }

        public int getDepth() {
            return depth;
        }

        public BucketEntry[] getEntries() {
            return entries;
        }
    }

    public static class BucketEntry {
        public static final int ENTRY_SIZE = 4 + 4; // size, in bytes, of the info in an entry

        private final int key;
        private final int index;

        public BucketEntry(int key, int index) {
            this.key = key;
            this.index = index;
        }

        public int getKey() {
            return key;
        }

        public int getIndex() {
            return index;
        }
    }

    /**
     * Class representing the binary file as a DB, handles queries by index and by date. Depends on file produced from
     * Prog1A.java.
     *
     * @name BinaryFileDB
     * @author Bohan Li
     */
    public static class BinaryFileDB {
        private RandomAccessFile file;          // file for accessing DB data
        private long dataStart;                 // position in file for start point of DB data
        private int numFields;                  // number of fields in the DB
        private boolean fieldIsString[];        // boolean array for whether a field contains string data or not
        private int maxFieldSize[];             // size for each field, in bytes
        private int numEntries;                 // number of rows (entries) in the DB
        private int entrySize;                  // size of each row (entry), in bytes

        public BinaryFileDB(RandomAccessFile file) {
            try {
                this.file = file;
                numFields = file.readInt();
                numEntries = file.readInt();

                fieldIsString = new boolean[numFields];
                maxFieldSize = new int[numFields];
                this.entrySize = 0;
                for (int i = 0; i < numFields; i++) {
                    fieldIsString[i] = file.readBoolean();
                    maxFieldSize[i] = file.readInt();
                    // max field sizes are not set for integers, so set them to 4 bytes
                    entrySize += fieldIsString[i] ? maxFieldSize[i] : 4;
                }

                dataStart = file.getFilePointer();
            } catch (IOException ex) {
                System.out.println("Binary file could not be read or was corrupt");
            }
        }

        /**
         * Queries database by index of the entry. Moves RAF file pointer to the location after the read.
         * @name get
         * @param index
         * @return the entry in the database
         */
        public Object[] get(int index) {
            if (index < 0 || index >= numEntries) throw new IndexOutOfBoundsException();
            try {
                file.seek(dataStart + index*entrySize); // set RAF pointer to beginning of read position
                Object[] retval = new Object[numFields]; // return value
                for (int i = 0; i < numFields; i++) {
                    if (fieldIsString[i]) {
                        byte byteSeq[] = new byte[maxFieldSize[i]]; // read string as sequence of bytes into array
                        file.readFully(byteSeq);
                        retval[i] = new String(byteSeq);
                    } else retval[i] = file.readInt();
                }
                return retval;
            } catch (IOException ex) {
                System.out.println("Could not properly read from the file.");
                System.exit(1);
            }
            return null;
        }

        /**
         * @return the number of entries in the database
         */
        public int getNumEntries() {
            return numEntries;
        }
    }
}
