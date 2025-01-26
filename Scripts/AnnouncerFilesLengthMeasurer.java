import java.io.File;
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

  public static void main(String[] args) {
    validate();

    scan(scanRoot);

    writeLengthMap();

    printFileLengths();
  }

  private static void printFileLengths() {
    Map<String, List<File>> fileNameGroupByMap = lengthMap.keySet().stream().collect(Collectors.groupingBy(File::getName));
    for (String fileName : fileNameGroupByMap.keySet()) {
      System.out.printf("%s:%n", fileName);
      for (File file : fileNameGroupByMap.get(fileName)) {
        String parentName = file.getParentFile().getName().toUpperCase();
        long length = lengthMap.get(file);
        System.out.printf("  .fromMillies(%s, %d)%n", parentName, length);
      }
    }
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

  /**
   * <p>Scans the given directory for ogg files and calculates their duration.</p>
   * @see <a href="https://stackoverflow.com/questions/20794204/how-to-determine-length-of-ogg-file">Source</a>
   * @param oggFile
   * @return duration in milliseconds
   * @throws IOException
   */
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

    double duration = (double) (length*1000) / (double) rate;
    return duration;
  }
}