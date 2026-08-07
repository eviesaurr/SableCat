package eviesaurr.sablecat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class ConsoleAnsiArtist {
    private static final Map<Character, String[]> ansiArtLibrary = new HashMap<>();

    static {
        ansiArtLibrary.put('A', new String[]{" █████╗ ", "██╔══██╗", "███████║", "██╔══██║", "██║  ██║", "╚═╝  ╚═╝"});
        ansiArtLibrary.put('B', new String[]{"██████╗ ", "██╔══██╗", "██████╔╝", "██╔══██╗", "██████╔╝", "╚═════╝ "});
        ansiArtLibrary.put('C', new String[]{" ██████╗", "██╔════╝", "██║     ", "██║     ", "╚██████╗", " ╚═════╝"});
        ansiArtLibrary.put('D', new String[]{"██████╗ ", "██╔══██╗", "██║  ██║", "██║  ██║", "██████╔╝", "╚═════╝ "});
        ansiArtLibrary.put('E', new String[]{"███████╗", "██╔════╝", "█████╗  ", "██╔══╝  ", "███████╗", "╚══════╝"});
        ansiArtLibrary.put('F', new String[]{"███████╗", "██╔════╝", "█████╗  ", "██╔══╝  ", "██║     ", "╚═╝     "});
        ansiArtLibrary.put('G', new String[]{" ██████╗ ", "██╔════╝ ", "██║  ███╗", "██║   ██║", "╚██████╔╝", " ╚═════╝ "});
        ansiArtLibrary.put('H', new String[]{"██╗  ██╗", "██║  ██║", "███████║", "██╔══██║", "██║  ██║", "╚═╝  ╚═╝"});
        ansiArtLibrary.put('I', new String[]{"██╗", "██║", "██║", "██║", "██║", "╚═╝"});
        ansiArtLibrary.put('J', new String[]{"     ██╗", "     ██║", "     ██║", "██   ██║", "╚█████╔╝", " ╚════╝ "});
        ansiArtLibrary.put('K', new String[]{"██╗  ██╗", "██║ ██╔╝", "█████╔╝ ", "██╔═██╗ ", "██║  ██╗", "╚═╝  ╚═╝"});
        ansiArtLibrary.put('L', new String[]{"██╗     ", "██║     ", "██║     ", "██║     ", "███████╗", "╚══════╝"});
        ansiArtLibrary.put('M', new String[]{"███╗   ███╗", "████╗ ████║", "██╔████╔██║", "██║╚██╔╝██║", "██║ ╚═╝ ██║", "╚═╝     ╚═╝"});
        ansiArtLibrary.put('N', new String[]{"███╗   ██╗", "████╗  ██║", "██╔██╗ ██║", "██║╚██╗██║", "██║ ╚████║", "╚═╝  ╚═══╝"});
        ansiArtLibrary.put('O', new String[]{" ██████╗ ", "██╔═══██╗", "██║   ██║", "██║   ██║", "╚██████╔╝", " ╚═════╝ "});
        ansiArtLibrary.put('P', new String[]{"██████╗ ", "██╔══██╗", "██████╔╝", "██╔═══╝ ", "██║     ", "╚═╝     "});
        ansiArtLibrary.put('Q', new String[]{" ██████╗ ", "██╔═══██╗", "██║   ██║", "██║▄▄ ██║", "╚██████╔╝", " ╚══▀▀═╝ "});
        ansiArtLibrary.put('R', new String[]{"██████╗ ", "██╔══██╗", "██████╔╝", "██╔══██╗", "██║  ██║", "╚═╝  ╚═╝"});
        ansiArtLibrary.put('S', new String[]{" ███████╗", "██╔═════╝", "███████╗ ", "╚════██║ ", "███████║ ", "╚══════╝ "});
        ansiArtLibrary.put('T', new String[]{"████████╗", "╚══██╔══╝", "   ██║   ", "   ██║   ", "   ██║   ", "   ╚═╝   "});
        ansiArtLibrary.put('U', new String[]{"██╗   ██╗", "██║   ██║", "██║   ██║", "██║   ██║", "╚██████╔╝", " ╚═════╝ "});
        ansiArtLibrary.put('V', new String[]{"██╗   ██╗", "██║   ██║", "██║   ██║", "╚██╗ ██╔╝", " ╚████╔╝ ", "  ╚═══╝  "});
        ansiArtLibrary.put('W', new String[]{"██╗    ██╗", "██║    ██║", "██║ █╗ ██║", "██║███╗██║", "╚███╔███╔╝", " ╚══╝╚══╝ "});
        ansiArtLibrary.put('X', new String[]{"██╗  ██╗", "╚██╗██╔╝", " ╚███╔╝ ", " ██╔██╗ ", "██╔╝ ██╗", "╚═╝  ╚═╝"});
        ansiArtLibrary.put('Y', new String[]{"██╗   ██╗", "╚██╗ ██╔╝", " ╚████╔╝ ", "  ╚██╔╝  ", "   ██║   ", "   ╚═╝   "});
        ansiArtLibrary.put('Z', new String[]{"███████╗", "╚══███╔╝", "  ███╔╝ ", " ███╔╝  ", "███████╗", "╚══════╝"});
        ansiArtLibrary.put(',', new String[]{"   ", "   ", "   ", "██╗", "██║", "╚═╝"});
        ansiArtLibrary.put('?', new String[]{" ██████╗ ", "██╔═══██╗", "     ██╔╝", "   ██╔╝  ", "   ╚═╝   ", "   ██╗   "});
        ansiArtLibrary.put('\'', new String[]{"██╗", "██║", "╚═╝", "   ", "   ", "   "});
        ansiArtLibrary.put('"', new String[]{"██╗   ██╗", "██║   ██║", "╚═╝   ╚═╝", "        ", "        ", "        "});
        ansiArtLibrary.put('-', new String[]{"   ", "   ", "█████╗", "   ", "   ", "   "});
        ansiArtLibrary.put('!', new String[]{" ██╗ ", " ██║ ", " ██║ ", " ██║ ", " ╚═╝ ", " ██╗ "});
        ansiArtLibrary.put(' ', new String[]{"     ", "     ", "     ", "     ", "     ", "     "});
    }

    private static class CharArtData {
        @SerializedName("Char")
        private String character;
        
        @SerializedName("Lines")
        private String[] lines;

        public String getCharacter() { return character; }
        public String[] getLines() { return lines; }
    }

    public static void importFromJson(String json, boolean overwrite) {
        Gson gson = new Gson();
        CharArtData[] items = gson.fromJson(json, CharArtData[].class);
        
        for (CharArtData item : items) {
            char c = item.getCharacter().charAt(0);
            if (overwrite || !ansiArtLibrary.containsKey(c)) {
                ansiArtLibrary.put(c, item.getLines());
            }
        }
    }

    public static void importFromJsonFile(String filePath, boolean overwrite) throws IOException {
        String json = new String(Files.readAllBytes(Paths.get(filePath)), java.nio.charset.StandardCharsets.UTF_8);
        importFromJson(json, overwrite);
    }

    private static boolean isValidRgbColor(String rgbString) {
        String[] parts = rgbString.split(",");
        if (parts.length != 3) return false;
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value < 0 || value > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static String getAnsiColorCode(String rgbString) {
        String[] parts = rgbString.split(",");
        int r = Integer.parseInt(parts[0].trim());
        int g = Integer.parseInt(parts[1].trim());
        int b = Integer.parseInt(parts[2].trim());
        return String.format("\u001B[38;2;%d;%d;%dm", r, g, b);
    }

    private static String getAnsiBackgroundColorCode(String rgbString) {
        String[] parts = rgbString.split(",");
        int r = Integer.parseInt(parts[0].trim());
        int g = Integer.parseInt(parts[1].trim());
        int b = Integer.parseInt(parts[2].trim());
        return String.format("\u001B[48;2;%d;%d;%dm", r, g, b);
    }

    private static String getAnsiResetCode() {
        return "\u001B[0m";
    }

    public static void printAnsiText(String text) {
        printAnsiText(text, "", "");
    }

    public static void printAnsiText(String text, String foregroundColor, String backgroundColor) {
        if (text == null || text.isEmpty()) return;

        boolean hasValidForeground = foregroundColor != null && !foregroundColor.isEmpty() && isValidRgbColor(foregroundColor);
        boolean hasValidBackground = backgroundColor != null && !backgroundColor.isEmpty() && isValidRgbColor(backgroundColor);

        StringBuilder colorPrefix = new StringBuilder();
        if (hasValidForeground) colorPrefix.append(getAnsiColorCode(foregroundColor));
        if (hasValidBackground) colorPrefix.append(getAnsiBackgroundColorCode(backgroundColor));
        String colorSuffix = colorPrefix.length() == 0 ? "" : getAnsiResetCode();

        boolean canPrintAsAnsi = true;
        for (char c : text.toCharArray()) {
            if (!ansiArtLibrary.containsKey(getCharKey(c)) && c != ' ' && !isChineseCharacter(c)) {
                canPrintAsAnsi = false;
                break;
            }
        }

        if (!canPrintAsAnsi) {
            if (colorPrefix.length() > 0) {
                System.out.print(colorPrefix.toString());
                System.out.print(text);
                System.out.println(colorSuffix);
            } else {
                System.out.println(text);
            }
            return;
        }

        int maxHeight = 0;
        for (char c : text.toCharArray()) {
            char key = getCharKey(c);
            if (ansiArtLibrary.containsKey(key)) {
                maxHeight = Math.max(maxHeight, ansiArtLibrary.get(key).length);
            }
        }

        if (maxHeight == 0) {
            if (colorPrefix.length() > 0) {
                System.out.print(colorPrefix.toString());
                System.out.print(text);
                System.out.println(colorSuffix);
            } else {
                System.out.println(text);
            }
            return;
        }

        for (int line = 0; line < maxHeight; line++) {
            StringBuilder lineBuilder = new StringBuilder();
            if (colorPrefix.length() > 0) lineBuilder.append(colorPrefix);

            for (char c : text.toCharArray()) {
                char key = getCharKey(c);
                if (c == ' ') {
                    lineBuilder.append("   ");
                } else if (isChineseCharacter(c) && !ansiArtLibrary.containsKey(c)) {
                    if (line == 0) {
                        lineBuilder.append(c).append(" ");
                    } else {
                        lineBuilder.append("  ");
                    }
                } else if (ansiArtLibrary.containsKey(key)) {
                    String[] artLines = ansiArtLibrary.get(key);
                    if (line < artLines.length) {
                        lineBuilder.append(artLines[line]);
                    } else if (artLines.length > 0) {
                        lineBuilder.append(" ".repeat(artLines[0].length()));
                    } else {
                        lineBuilder.append("   ");
                    }
                    lineBuilder.append(' ');
                }
            }

            if (!colorSuffix.isEmpty()) lineBuilder.append(colorSuffix);
            System.out.println(lineBuilder.toString());
        }
    }

    private static char getCharKey(char c) {
        if (isSpecialSymbol(c)) return c;
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return Character.toUpperCase(c);
        return c;
    }

    private static boolean isSpecialSymbol(char c) {
        return c == ',' || c == '?' || c == '\'' || c == '"' || c == '-' || c == '!';
    }

    private static boolean isChineseCharacter(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF);
    }

    public static void addCustomCharacter(char character, String[] artLines) {
        ansiArtLibrary.put(character, artLines);
    }
}