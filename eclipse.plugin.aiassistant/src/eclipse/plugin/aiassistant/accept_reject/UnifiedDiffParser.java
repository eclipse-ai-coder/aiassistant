package eclipse.plugin.aiassistant.accept_reject;

import java.util.*;
import java.util.regex.*;

public class UnifiedDiffParser {
    record FileOperation(String oldPath, String newPath, List<String> hunks) {}
    record ParsedDiff(List<FileOperation> operations) {}

    private static final Pattern HEADER = Pattern.compile(
        "^--- (.+)$\\n^\\+\\+\\+ (.+)$", Pattern.MULTILINE);

    private static final Pattern CODE_BLOCK_DIFF = Pattern.compile(
        "```(?:diff|patch)\\n([\\s\\S]*?)```", Pattern.MULTILINE);

    public static ParsedDiff parse(String diffText) {
        List<FileOperation> ops = new ArrayList<>();
        Matcher m = HEADER.matcher(diffText);
        while (m.find()) {
            String oldPath = m.group(1).trim();
            String newPath = m.group(2).trim();
            int start = m.end();
            int end = diffText.length();
            Matcher next = HEADER.matcher(diffText);
            if (next.find(start)) {
                end = next.start();
            }
            String body = diffText.substring(start, end);
            ops.add(new FileOperation(oldPath, newPath, splitHunks(body)));
        }
        return new ParsedDiff(ops);
    }

    private static List<String> splitHunks(String body) {
        List<String> hunks = new ArrayList<>();
        String[] parts = body.split("(?m)(?=^@@ )");
        for (String part : parts) {
            if (!part.isBlank()) hunks.add(part);
        }
        return hunks;
    }

    public static List<String> extractDiffPatches(String markdown) {
        List<String> patches = new ArrayList<>();
        Matcher m = CODE_BLOCK_DIFF.matcher(markdown);
        while (m.find()) {
            String patch = m.group(1).trim();
            if (!patch.isEmpty()) {
                patches.add(patch);
            }
        }
        return patches;
    }

    public static String extractFirstDiffPatch(String markdown) {
        List<String> patches = extractDiffPatches(markdown);
        return patches.isEmpty() ? null : patches.get(0);
    }

    public static String extractFilePathFromPatch(String patch) {
        Matcher m = HEADER.matcher(patch);
        if (m.find()) {
            String oldPath = m.group(1).trim();
            String newPath = m.group(2).trim();
            return stripPrefix(oldPath.isEmpty() ? newPath : oldPath);
        }
        return null;
    }

    private static String stripPrefix(String path) {
        String p = path.trim();
        if (p.startsWith("a/") || p.startsWith("b/")) {
            return p.substring(2);
        }
        return p;
    }
}

