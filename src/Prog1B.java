import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

/**
 * Assignment: Program 1B
 * @author Bohan Li
 * Course: CSC 460
 * Instructor: Lester McCann
 * TA's: Prathyusha Butti, Zheng Tang
 *
 * Usage java Prog1B [file path]
 *
 * This program reads a .bin file written using Prog1A, the path of which must be provided as an argument.
 * The .bin file represents a database of court case information. This program prints the first 3,
 * middle 3 (4 if even), and last 3 elements in the DB, then takes in date inputs MM/dd/yyyy from the user,
 * printing any hits in the DB. The date query is implemented using ternary search. The query functionality assumes
 * the database arrives already sorted.
 *
 * System requirements: Java 8
 */
public class Prog1B {
    // indices for desired fields to be printed for the assignment
    public static int CASEID_INDEX = 0, DATEDECISION_INDEX = 4, CASENAME_INDEX = 14;

    /**
     * @name printEntry
     * Prints appropriate information of the given data entry to the screen.
     * @param entry
     */
    private static void printEntry(Object[] entry) {
        System.out.println(entry[CASEID_INDEX] + " " + entry[DATEDECISION_INDEX] + " " + entry[CASENAME_INDEX]);
    }

    public static void main(String args[]) throws IOException {
        if (args == null || args.length < 1) throw new RuntimeException("Usage java Prog1B [file path]");
        String binFilename = args[0]; // filename for the .bin file
        RandomAccessFile file = new RandomAccessFile(new File(binFilename), "r"); // file providing access to the DB
        BinaryFileDB db = new BinaryFileDB(file); // DB object constructed using the file

        /* Output all data for part 1 */
        Object[][] first3 = {db.get(0), db.get(1), db.get(2)}; // array containing first 3 elements in db
        for (int i = 0; i < 3; i++) printEntry(first3[i]);

        if (db.getNumEntries() % 2 == 0) printEntry(db.get(db.getNumEntries()/2 - 2));
        // array containing middle 3 elements of db
        Object[][] middle3 = {db.get(db.getNumEntries()/2 - 1), db.get(db.getNumEntries()/2), db.get(db.getNumEntries()/2 + 1)};
        for (int i = 0; i < 3; i++) printEntry(middle3[i]);

        // array containing last 3 elements of db
        Object[][] last3 = {db.get(db.getNumEntries() - 3), db.get(db.getNumEntries() - 2), db.get(db.getNumEntries() - 1)};
        for (int i = 0; i < 3; i++) printEntry(last3[i]);

        System.out.println(db.getNumEntries());

        /* Code for part 2 */
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNext()) {
            try {
                Date queryDate = Prog1A.Data.DATE_FORMAT.parse(scanner.next()); // date to be queried
                List<Object[]> entries = db.query(queryDate, 0, db.getNumEntries()); // results from DB query
                entries.forEach(entry -> printEntry(entry));
            } catch (ParseException ex) {
                System.out.println("Please enter a date in the form MM/dd/yyyy");
            }
        }

        scanner.close();
        file.close();
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
         * Queries database by date.
         * @name query
         * @param date query date in MM/dd/yyyy format
         * @param startIndex starting index of the query, inclusive
         * @param endIndex ending index of the query, exclusive
         * @return list of all entries
         */
        public List<Object[]> query(Date date, int startIndex, int endIndex) {
            List<Object[]> retval = new LinkedList<>(); // return value
            if (endIndex > startIndex) {
                // indices at 1/3 and 2/3 of the way between startIndex and endIndex
                // order is start, leftMid, rightMid, end
                int leftMidIndex = startIndex + (endIndex - startIndex) / 3;
                int rightMidIndex = startIndex + 2 * (endIndex - startIndex) / 3;

                try {
                    // entries for leftMidIndex, rightMidIndex, respectively
                    Object[] leftEntry = get(leftMidIndex), rightEntry = get(rightMidIndex);

                    // dates for leftMidIndex, rightMidIndex, respectively
                    Date leftDate = Prog1A.Data.DATE_FORMAT.parse((String) leftEntry[DATEDECISION_INDEX]);
                    Date rightDate = Prog1A.Data.DATE_FORMAT.parse((String) rightEntry[DATEDECISION_INDEX]);

                    // add query points to return value if query match is found
                    if (leftDate.equals(date))
                        retval.add(leftEntry);
                    if (rightDate.equals(date) && leftMidIndex != rightMidIndex)
                        retval.add(rightEntry);

                    // recurse lower, if one data point is a match, then check partitions on both sides of it
                    if (date.before(leftDate) || date.equals(leftDate))
                        retval.addAll(query(date, startIndex, leftMidIndex));
                    if (date.after(rightDate) || date.equals(rightDate))
                        retval.addAll(query(date, rightMidIndex + 1, endIndex));
                    if ((date.after(leftDate) && date.before(rightDate)) || date.equals(leftDate) || date.equals(rightDate))
                        retval.addAll(query(date, leftMidIndex + 1, rightMidIndex));
                } catch (ParseException ex) {
                    System.out.println("Corrupt date was found in the bin data. Exiting...");
                    System.exit(1);
                }
            }
            return retval;
        }

        /**
         * @return the number of entries in the database
         */
        public int getNumEntries() {
            return numEntries;
        }
    }
}
