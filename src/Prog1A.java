import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TreeSet;

/**
 * Code for part A of program 1.
 * @author Bohan Li
 */
public class Prog1A {
    public static void main(String args[]) throws IOException {
        if (args == null || args.length < 1) throw new RuntimeException("Usage java Prog1A [file path]");
        String inputFilename = args[0];
        String outputFilename = removeExtension(getBaseNameFromPath(inputFilename)) + ".bin";

        Data data = new Data(inputFilename);
        RandomAccessFile output = new RandomAccessFile(new File(outputFilename),"rw");
        data.outputToBin(output);
        output.close();
    }

    /******** File utility Functions **********/
    private static String getBaseNameFromPath(String path) {
        return path.substring(Math.max(0, path.lastIndexOf('/') + 1), path.length());
    }

    private static String removeExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index == -1 ? filename : filename.substring(0, index);
    }

    /**
     * Class for storing all data in the CSV file.
     */
    public static class Data {
        private String fieldNames[];
        private boolean fieldIsString[];
        private Object data[];
        private int maxFieldSize[];
        private int numDataEntries;
        private int numEntries;

        public static DateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");

        /**
         * Constructor based on a CSV file. Assumes each line in the CSV has at least as many
         * elements as the first line, which declares the fields.
         *
         * @param csvFilename name of the CSV file
         */
        public Data(String csvFilename) {
            try {
                BufferedReader input = new BufferedReader(new FileReader(csvFilename));
                String line = input.readLine();
                fieldNames = line.split(",");
                fieldIsString = new boolean[fieldNames.length];
                maxFieldSize = new int[fieldNames.length];
                numDataEntries = 0;
                Date previous = new Date(Long.MIN_VALUE);

                TreeSet<Integer> lineIsOut = new TreeSet<>();
                int lineCounter = -1;
                numEntries = 0;
                while((line = input.readLine()) != null) {
                    lineCounter++;
                    String dataValues[] = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                    try {
                        Date thisDate = DATE_FORMAT.parse(dataValues[4]);
                        if (previous.after(thisDate)) {
                            lineIsOut.add(lineCounter);
                            continue;
                        }
                        previous = thisDate;
                    } catch (ParseException | ArrayIndexOutOfBoundsException ex) {
                        lineIsOut.add(lineCounter);
                        continue;
                    }
                    numDataEntries += fieldNames.length;
                    for (int i = 0; i < fieldNames.length; i++) {
                        try {
                            Integer.parseInt(dataValues[i]);
                        } catch (NumberFormatException ex) {
                            if (dataValues[i].length() != 0) {
                                fieldIsString[i] = true;
                                maxFieldSize[i] = Math.max(maxFieldSize[i], dataValues[i].length());
                            }
                        }
                    }
                    numEntries++;
                }
                input.close();

                PrintWriter output = new PrintWriter(new File("out.csv"));
                data = new Object[numDataEntries];

                input = new BufferedReader(new FileReader(csvFilename));
                input.readLine();
                int entryIndex = 0;
                lineCounter = -1;
                while((line = input.readLine()) != null) {
                    lineCounter++;
                    if (lineIsOut.contains(lineCounter)) continue;
                    output.write(line + "\n");
                    String dataValues[] = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                    for (int i = 0; i < fieldNames.length; i++) {
                        if (fieldIsString[i]) {
                            data[entryIndex] = String.format("%-" + maxFieldSize[i] + "s", dataValues[i]);
                        } else {
                            try {
                                data[entryIndex] = Integer.parseInt(dataValues[i]);
                            } catch (NumberFormatException ex) {
                                data[entryIndex] = -1;
                            }
                        }
                        entryIndex++;
                    }
                }
                output.close();
                input.close();
            } catch (IOException ex) {
                ex.printStackTrace();
                System.out.println("I/O ERROR: Couldn't read from the file, or file was corrupt");
                System.exit(-1);
            }
        }

        /**
         * Writes the data represented by the object into a bin file. Field names are not written.
         * @param output stream of bin file
         */
        public void outputToBin(RandomAccessFile output) {
            try {
                output.writeInt(fieldNames.length);
                output.writeInt(numEntries);

                for (int i = 0; i < fieldNames.length; i++) {
                    output.writeBoolean(fieldIsString[i]);
                    output.writeInt(maxFieldSize[i]);
                }

                for (int j = 0; j < numEntries; j++) {
                    for (int i = 0; i < fieldNames.length; i++) {
                        if (fieldIsString[i]) output.writeBytes((String) data[fieldNames.length*j + i]);
                        else output.writeInt((Integer) data[fieldNames.length*j + i]);
                    }
                }
            } catch (IOException ex) {
                System.out.println("I/O ERROR: Couldn't write to the file;\n\t"
                        + "perhaps the file system is full?");
                System.exit(-1);
            }
        }
    }
}
