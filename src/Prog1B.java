import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class Prog1B {
    public static void main(String args[]) throws IOException {
        if (args == null || args.length < 1) throw new RuntimeException("Usage java Prog1B [file path]");
        String binFilename = args[0];
        RandomAccessFile file = new RandomAccessFile(new File(binFilename), "r");
        BinaryFileDB db = new BinaryFileDB(file);
        System.exit(0);
    }

    public static class BinaryFileDB {
        private RandomAccessFile file;
        private long dataStart;
        private int numFields;
        private boolean fieldIsString[];
        private int maxFieldSize[];
        private int numEntries;

        // read data first for debugging purposes
        private Object data[];


        public BinaryFileDB(RandomAccessFile file) {
            try {
                this.file = file;
                numFields = file.readInt();
                numEntries = file.readInt();

                fieldIsString = new boolean[numFields];
                maxFieldSize = new int[numFields];
                for (int i = 0; i < numFields; i++) {
                    fieldIsString[i] = file.readBoolean();
                    maxFieldSize[i] = file.readInt();
                }

                dataStart = file.getFilePointer();
            } catch (IOException ex) {
                System.out.println("Binary file could not be read or was corrupt");
            }
        }
    }
}
