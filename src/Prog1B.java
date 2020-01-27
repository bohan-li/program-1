import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

/**
 * Class for part B of program 1 in CSC 460. Usage java Prog1B [file path]. Reads from the bin file specified from the
 * given path that is generated from part A. Prints the first 3, middle 3 (4 if even), and last 3 elements in the DB,
 * then takes in date inputs MM/dd/yyyy from the user, printing any hits in the DB.
 */
public class Prog1B {
    public static int CASEID_INDEX = 0, DATEDECISION_INDEX = 4, CASENAME_INDEX = 14;

    /**
     * Prints appropriate information of the given data entry to the screen.
     * @param entry
     */
    private static void printEntry(Object[] entry) {
        System.out.println(entry[CASEID_INDEX] + " " + entry[DATEDECISION_INDEX] + " " + entry[CASENAME_INDEX]);
    }

    public static void main(String args[]) throws IOException {
        if (args == null || args.length < 1) throw new RuntimeException("Usage java Prog1B [file path]");
        String binFilename = args[0];
        RandomAccessFile file = new RandomAccessFile(new File(binFilename), "r");
        BinaryFileDB db = new BinaryFileDB(file);

        // output all data for part 1
        Object[][] first3 = {db.get(0), db.get(1), db.get(2)};
        for (int i = 0; i < 3; i++) printEntry(first3[i]);

        if (db.getNumEntries() % 2 == 0) printEntry(db.get(db.getNumEntries()/2 - 2));
        Object[][] middle3 = {db.get(db.getNumEntries()/2 - 1), db.get(db.getNumEntries()/2), db.get(db.getNumEntries()/2 + 1)};
        for (int i = 0; i < 3; i++) printEntry(middle3[i]);

        Object[][] last3 = {db.get(db.getNumEntries() - 3), db.get(db.getNumEntries() - 2), db.get(db.getNumEntries() - 1)};
        for (int i = 0; i < 3; i++) printEntry(last3[i]);

        System.out.println(db.getNumEntries());

        // part 2
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNext()) {
            try {
                Date queryDate = Prog1A.Data.DATE_FORMAT.parse(scanner.next());
                List<Object[]> entries = db.query(queryDate, 0, db.getNumEntries());
                entries.forEach(entry -> printEntry(entry));
            } catch (ParseException ex) {
                System.out.println("Please enter a date in the form MM/dd/yyyy");
            }
        }

        scanner.close();
        file.close();
    }

    /**
     * Class representing the binary file as a DB, handles queries by index and by date.
     */
    public static class BinaryFileDB {
        private RandomAccessFile file;
        private long dataStart;
        private int numFields;
        private boolean fieldIsString[];
        private int maxFieldSize[];
        private int numEntries;
        private int entrySize;

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
                    entrySize += fieldIsString[i] ? maxFieldSize[i] : 4;
                }

                dataStart = file.getFilePointer();
            } catch (IOException ex) {
                System.out.println("Binary file could not be read or was corrupt");
            }
        }

        /**
         * Queries database by index of the entry.
         * @param index
         * @return the entry in the database
         */
        public Object[] get(int index) {
            if (index < 0 || index >= numEntries) throw new IndexOutOfBoundsException();
            try {
                file.seek(dataStart + index*entrySize);
                Object[] retval = new Object[numFields];
                for (int i = 0; i < numFields; i++) {
                    if (fieldIsString[i]) {
                        byte byteSeq[] = new byte[maxFieldSize[i]];
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
         * @param date query date in MM/dd/yyyy format
         * @param startIndex starting index of the query, inclusive
         * @param endIndex ending index of the query, exclusive
         * @return list of all entries
         */
        public List<Object[]> query(Date date, int startIndex, int endIndex) {
            List<Object[]> retval = new LinkedList<>();
            if (endIndex > startIndex) {
                int index1 = startIndex + (endIndex - startIndex) / 3;
                int index2 = startIndex + 2 * (endIndex - startIndex) / 3;
                try {
                    Object[] entry1 = get(index1), entry2 = get(index2);
                    Date date1 = Prog1A.Data.DATE_FORMAT.parse((String) entry1[DATEDECISION_INDEX]);
                    Date date2 = Prog1A.Data.DATE_FORMAT.parse((String) entry2[DATEDECISION_INDEX]);

                    if (date1.equals(date)) retval.add(entry1);
                    if (date2.equals(date) && index1 != index2) retval.add(entry2);

                    if (date.before(date1) || date.equals(date1)) retval.addAll(query(date, startIndex, index1));
                    if (date.after(date2) || date.equals(date2)) retval.addAll(query(date, index2 + 1, endIndex));
                    if ((date.after(date1) && date.before(date2)) || date.equals(date1) || date.equals(date2))
                        retval.addAll(query(date, index1 + 1, index2));
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
