import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class SoundsJsonGenerator {
  public static final String SOUNDS_JSON = "../Pack/assets/jelly/sounds.json";
  public static final String SCAN_ROOT = "../Pack/assets/minecraft/sounds";
  private static final File soundsFile = new File(SOUNDS_JSON);
  private static final File scanRoot = new File(SCAN_ROOT);
  private static final List<File> oggList = new ArrayList<>();


  public static void main(String[] args) throws IOException {
    validate();

    scan(scanRoot);

    //noinspection ResultOfMethodCallIgnored
    soundsFile.delete();

    oggList.sort(Comparator.comparing(File::getName));

    writeJson();
  }

  private static void writeJson() throws IOException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(soundsFile));

    writer.write("{\n");

    for (int i = 0; i < oggList.size(); i++) {
      String[] relativePath = getRelativePath(oggList.get(i));
      relativePath[relativePath.length - 1] = relativePath[relativePath.length - 1].replace(".ogg", "");

      writer.write("  \"" + String.join(".", relativePath) + "\": {\n");
      writer.write("    \"sounds\": [\n");
      writer.write("      \"" + String.join("/", relativePath) + "\"\n");
      writer.write("    ],\n");
      writer.write("    \"subtitle\": \"" + relativePath[relativePath.length - 1] + "\"\n");
      if (i == oggList.size() - 1) {
        writer.write("  }\n");
      } else {
        writer.write("  },\n");
      }
    }


    writer.write("}\n");

    writer.flush();
    writer.close();
  }

  private static void scan(File scanRoot) {
    for (File file : Objects.requireNonNull(scanRoot.listFiles())) {
      if (file.isDirectory()) {
        scan(file);
      } else {
        if (file.getName().endsWith(".ogg")) {
          oggList.add(file);
        }
      }
    }
  }

  private static void validate() {
    if (!soundsFile.exists()) {
      System.out.println("Sounds file not found at " + soundsFile.getAbsolutePath());
      System.exit(1);
    }

    if (!scanRoot.exists() || !scanRoot.isDirectory()) {
      System.out.println("Sounds root not found at " + scanRoot.getAbsolutePath());
      System.exit(1);
    }

  }

  private static String[] getRelativePath(File file) {
    return file.getAbsolutePath().replace(scanRoot.getAbsolutePath() + File.separatorChar, "")
        .split(Pattern.quote(File.separator));
  }
}