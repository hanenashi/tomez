package com.hanenashi.tomez;

import java.util.ArrayList;
import java.util.List;

final class DiffPatch {
    private DiffPatch() { }

    static String create(String name, String before, String after) {
        if (before.equals(after)) return "";
        List<String> oldLines = lines(before);
        List<String> newLines = lines(after);
        int prefix = 0;
        while (prefix < oldLines.size() && prefix < newLines.size()
                && oldLines.get(prefix).equals(newLines.get(prefix))) prefix++;
        int suffix = 0;
        while (suffix < oldLines.size() - prefix && suffix < newLines.size() - prefix
                && oldLines.get(oldLines.size() - suffix - 1)
                .equals(newLines.get(newLines.size() - suffix - 1))) suffix++;

        // One valid hunk keeps export small for nearby edits without a diff dependency.
        int beforeContext = Math.min(3, prefix);
        int afterContext = Math.min(3, suffix);
        int from = prefix - beforeContext;
        int oldEnd = oldLines.size() - suffix;
        int newEnd = newLines.size() - suffix;
        int oldCount = beforeContext + oldEnd - prefix + afterContext;
        int newCount = beforeContext + newEnd - prefix + afterContext;
        String file = name == null ? "Untitled.txt"
                : name.replace('/', '_').replace('\\', '_')
                        .replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        if (file.isEmpty() || file.equals(".") || file.equals("..")) file = "document.txt";
        StringBuilder patch = new StringBuilder();
        patch.append("--- ").append(name == null ? "/dev/null" : "a/" + file).append('\n');
        patch.append("+++ b/").append(file).append('\n');
        patch.append("@@ -").append(oldCount == 0 ? from : from + 1).append(',').append(oldCount)
                .append(" +").append(newCount == 0 ? from : from + 1).append(',')
                .append(newCount).append(" @@\n");
        for (int i = from; i < prefix; i++) appendLine(patch, ' ', oldLines.get(i));
        for (int i = prefix; i < oldEnd; i++) appendLine(patch, '-', oldLines.get(i));
        for (int i = prefix; i < newEnd; i++) appendLine(patch, '+', newLines.get(i));
        for (int i = oldEnd; i < oldEnd + afterContext; i++)
            appendLine(patch, ' ', oldLines.get(i));
        return patch.toString();
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        for (int from = 0; from < text.length(); ) {
            int end = text.indexOf('\n', from);
            if (end < 0) {
                lines.add(text.substring(from));
                break;
            }
            lines.add(text.substring(from, end + 1));
            from = end + 1;
        }
        return lines;
    }

    private static void appendLine(StringBuilder patch, char prefix, String line) {
        patch.append(prefix).append(line);
        if (!line.endsWith("\n"))
            patch.append("\n\\ No newline at end of file\n");
    }
}
