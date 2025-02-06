import java.io.File;
import java.io.PrintStream;
import java.util.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.util.stream.Collectors;

public class AnnouncerFilesLengthMeasurer {
  public static final String SCAN_ROOT = "../Pack/assets/minecraft/sounds/arena/announce";
  private static final File scanRoot = new File(SCAN_ROOT);
  private static final List<File> oggFiles = new ArrayList<>();
  private static final SortedMap<File, Long> lengthMap = new TreeMap<>(Comparator.comparing(f -> f.getName() + f.getParentFile().getName()));

  private static PrintStream stream;
  private static String header;
  private static String fileHeader;
  private static String fileLine;
  private static String lastLine;
  private static String fileFooter;
  private static String lastFooter;
  private static String footer;
  private static boolean capitalizeFileName;

  public static void main(String[] args) {
    setup();

    validate();

    scan(scanRoot);

    writeLengthMap();

    printFileLengths();
  }

  private static void setup() {
    String outputFile = System.getProperty("outputFile");
    if (outputFile != null) {
      try {
        stream = new PrintStream(outputFile);
      } catch (IOException e) {
        System.err.println("Error opening output file " + outputFile);
        e.printStackTrace();
        System.exit(1);
      }
    } else {
      stream = System.out;
    }

    String outputFormat = System.getProperty("format", "hardcoded");
    switch (outputFormat) {
      case "hardcoded":
        header     = "Map.ofEntries(\n";
        fileHeader = "Map.entry(\n  %s,\n    ConstantAnnouncementSkip.builder()\n";
        fileLine   = "      .fromMillies(%s, %d)\n";
        lastLine   = fileLine;
        fileFooter = "      .addGraceTimeToAll()\n      .build()),\n";
        lastFooter = "      .addGraceTimeToAll()\n      .build()\n";
        footer     = ");\n";
        capitalizeFileName = true;
        break;
      case "yaml":
        header     = "skips:\n";
        fileHeader = "  %s:\n";
        fileLine   = "    %s: %d\n";
        lastLine   = fileLine;
        fileFooter = "";
        lastFooter = fileFooter;
        footer     = "";
        capitalizeFileName = false;
        break;
      case "json":
        header     = "{\n \"skips\": {\n";
        fileHeader = "    \"%s\": {\n";
        fileLine   = "      \"%s\": %d,\n";
        lastLine   = "      \"%s\": %d\n";
        fileFooter = "    },\n";
        lastFooter = "    }\n";
        footer     = "  }\n}\n";
        break;
      default:
        throw new IllegalArgumentException("Unsupported output format: " + outputFormat);
    }
  }

  private static void printFileLengths() {
    Map<String, List<File>> fileNameGroupByMap = lengthMap.keySet().stream().collect(Collectors.groupingBy(File::getName));
    stream.printf(header);
    int mapLen = fileNameGroupByMap.size();
    for (String fileName : fileNameGroupByMap.keySet()) {
      stream.printf(fileHeader, formatFileName(fileName));
      int varLen = fileNameGroupByMap.get(fileName).size();
      for (File file : fileNameGroupByMap.get(fileName)) {
        String parentName = file.getParentFile().getName().toUpperCase();
        long length = lengthMap.get(file);
        stream.printf(--varLen == 0 ? lastLine : fileLine, parentName, length);
      }
      stream.printf(--mapLen == 0 ? lastFooter : fileFooter);
    }
    stream.printf(footer);
  }

  private static void validate() {
    if (!scanRoot.exists() || !scanRoot.isDirectory()) {
      System.out.println("Sounds root not found at " + scanRoot.getAbsolutePath());
      System.exit(1);
    }
  }

  private static void scan(File scanRoot) {
    for (File file : Objects.requireNonNull(scanRoot.listFiles())) {
      if (file.isDirectory()) {
        scan(file);
      } else {
        if (file.getName().endsWith(".ogg")) {
          oggFiles.add(file);
        }
      }
    }
  }

  private static void writeLengthMap() {
    for (File oggFile : oggFiles) {
      try {
        lengthMap.put(oggFile, Math.round(calculateDuration(oggFile)));
      } catch (IOException e) {
        System.err.println("Error calculating duration for " + oggFile.getAbsolutePath() + ". Non-vorbis or corrupted file.");
        if (!System.getProperty("ignoreErrors", "false").equals("true")) {
          e.printStackTrace();
          System.exit(1);
        }
      }
    }
  }

  private static String formatFileName(String fileName) {
    String formattedFileName = fileName.replace(".ogg", "");
    if (capitalizeFileName) {
      formattedFileName = formattedFileName.toUpperCase();
    }
    return formattedFileName;
  }

  /**
   * <p>Scans the given directory for ogg files and calculates their duration.</p>
   * @see <a href="https://stackoverflow.com/questions/20794204/how-to-determine-length-of-ogg-file">Source</a>
   * @param oggFile the file in question
   * @return duration in milliseconds
   * @throws IOException on read errors
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  static double calculateDuration(final File oggFile) throws IOException {
    int rate = -1;
    int length = -1;

    int size = (int) oggFile.length();
    byte[] t = new byte[size];

    FileInputStream stream = new FileInputStream(oggFile);
    stream.read(t);

    for (int i = size-1-8-2-4; i>=0 && length<0; i--) { //4 bytes for "OggS", 2 unused bytes, 8 bytes for length
      // Looking for length (value after last "OggS")
      if (
          t[i]==(byte)'O'
              && t[i+1]==(byte)'g'
              && t[i+2]==(byte)'g'
              && t[i+3]==(byte)'S'
      ) {
        byte[] byteArray = new byte[]{t[i+6],t[i+7],t[i+8],t[i+9],t[i+10],t[i+11],t[i+12],t[i+13]};
        ByteBuffer bb = ByteBuffer.wrap(byteArray);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        length = bb.getInt(0);
      }
    }
    for (int i = 0; i<size-8-2-4 && rate<0; i++) {
      // Looking for rate (first value after "vorbis")
      if (
          t[i]==(byte)'v'
              && t[i+1]==(byte)'o'
              && t[i+2]==(byte)'r'
              && t[i+3]==(byte)'b'
              && t[i+4]==(byte)'i'
              && t[i+5]==(byte)'s'
      ) {
        byte[] byteArray = new byte[]{t[i+11],t[i+12],t[i+13],t[i+14]};
        ByteBuffer bb = ByteBuffer.wrap(byteArray);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        rate = bb.getInt(0);
      }

    }
    stream.close();

    return (double) (length*1000) / (double) rate;
  }
}