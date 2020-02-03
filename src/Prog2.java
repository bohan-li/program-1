import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

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
        private Bucket[] directory; // structure holding all buckets

        public Index(BinaryFileDB db) {
            this.db = db;
            this.maxDepth = 1;
            this.directory = new Bucket[KEY_CARDINALITY];
            for (int i = 0; i < KEY_CARDINALITY; i++) {
                directory[i] = new Bucket(maxDepth, i);
            }

            // insert pointers to all elements from the db file
            for (int index = 0; index < db.getNumEntries(); index++) {
                try {
                    int issue = (Integer) db.get(index)[KEY_INDEX]; // issue value of the object at the given index
                    if (issue == -1) continue;
                    insert(issue, index);
                } catch (ClassCastException ex) {
                    continue;
                }
            }
        }

        private void insert(int key, int value) {
            BucketEntry thisEntry = new BucketEntry(key, value); // bucket entry to be inserted
            int hash = key / (int) Math.pow(KEY_CARDINALITY, 6 - maxDepth); // hashcode of the issue number

            if (directory[hash].size() == Bucket.BUCKET_MAX_SIZE) { // bucket is full, time to split
                Bucket thisBucket = directory[hash]; // temporarily store bucket to be split

                if (thisBucket.getDepth() == maxDepth) { // bucket is lowest level, allocate new directory
                    if (maxDepth == KEY_DIGITS) { // cannot split any further, and bucket is full
                        throw new RuntimeException("Error: element cannot be inserted, bucket is full at max depth");
                    }

                    Bucket[] newDirectory = new Bucket[directory.length * KEY_CARDINALITY]; // allocate new directory
                    maxDepth++;

                    // redistribute bucket pointers
                    for (int i = 0; i < directory.length; i++) {
                        for (int j = KEY_CARDINALITY * i; j < KEY_CARDINALITY * (i + 1); j++) {
                            if (thisBucket.getParents().contains(i)) { // reset all pointers that point to the split bucket
                                newDirectory[j] = new Bucket(maxDepth, j);
                            } else { // keep existing pointers to unchanged buckets
                                newDirectory[j] = directory[i];
                                directory[i].getParents().add(j);
                                directory[i].getParents().removeFirstOccurrence(i); // remove outdated parent pointers
                            }
                        }
                    }
                    this.directory = newDirectory;

                    // reinsert contents of split bucket
                    thisBucket.forEach(entry -> insert(entry.getKey(), entry.getIndex()));
                } else { // split existing bucket
                    thisBucket.getParents().forEach(parent -> {
                        directory[parent] = new Bucket(thisBucket.depth + 1, parent);
                    });

                    // reinsert elements in the bucket
                    thisBucket.forEach(item -> insert(item.getKey(), item.getIndex()));
                }
            } else {
                directory[hash].add(thisEntry);
            }
        }

        public List<Object[]> query(String prefix) {
            List<Object[]> retval = new LinkedList<>(); // return value

            int matchLength = prefix.length();
            try {
                if (matchLength > KEY_DIGITS) throw new NumberFormatException();
                if (matchLength >= maxDepth) { // only one bucket to search, since prefix length is greater than hashcode digit length
                    int hash = Integer.parseInt(prefix.substring(0, maxDepth)); // hash value of the prefix

                    directory[hash].forEach(entry -> {
                        String keyStr = String.format("%0" + KEY_DIGITS + "d", entry.getKey()); // key integer to 6 char string

                        if (keyStr.startsWith(prefix)) retval.add(db.get(entry.getIndex()));
                    });
                } else {
                    Set<Bucket> reported = new HashSet<>(); // maintain pointers to buckets already reported

                    // search all buckets potentially with the given prefix, indices between values given by leftHash and rightHash
                    String leftHashStr = prefix, rightHashStr = prefix;
                    int leftHash, rightHash; // values represented by leftHashStr and rightHashStr, respectively


                    while (leftHashStr.length() < maxDepth) leftHashStr += "0";
                    leftHash = Integer.parseInt(leftHashStr);

                    rightHash = Integer.parseInt(rightHashStr) + 1;
                    rightHashStr = String.format("%0" + prefix.length() + "d", rightHash); // reattach leading 0's
                    while (rightHashStr.length() < maxDepth) rightHashStr += "0";
                    rightHash = Integer.parseInt(rightHashStr);

                    for (int i = leftHash; i < rightHash; i++) {
                        if (!reported.contains(directory[i])) {
                            reported.add(directory[i]);
                            directory[i].forEach(entry -> {
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

    public static class Bucket extends LinkedList<BucketEntry> {
        public static int BUCKET_MAX_SIZE = 250; // max elements in a bucket

        private final int depth; // depth of bucket
        private final LinkedList<Integer> parents; // pointer to location in directory, as an integer index

        public Bucket(int depth, int parent) {
            super();
            this.depth = depth;
            this.parents = new LinkedList<>();
            this.parents.add(parent);
        }

        public int getDepth() {
            return depth;
        }

        public LinkedList<Integer> getParents() {
            return parents;
        }
    }

    public static class BucketEntry {
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
