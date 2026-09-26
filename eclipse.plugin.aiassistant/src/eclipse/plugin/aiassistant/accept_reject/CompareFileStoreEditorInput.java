package eclipse.plugin.aiassistant.accept_reject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.ICompareInput;
import org.eclipse.compare.structuremergeviewer.ICompareInputChangeListener;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PartInitException;

/**
 * A {@link CompareEditorInput} that compares two file versions represented by
 * {@link IFileStore}s.
 */
public class CompareFileStoreEditorInput extends CompareEditorInput {

	private final IFileStore leftStore;
	private final IFileStore rightStore;
	private final String leftLabel;
	private final String rightLabel;
	private final String unifiedPatch;

	private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

	public CompareFileStoreEditorInput(IFileStore leftStore, IFileStore rightStore, String leftLabel,
			String rightLabel, String unifiedPatch) {
		super(new CompareConfiguration());
		this.leftStore = leftStore;
		this.rightStore = rightStore;
		this.leftLabel = leftLabel;
		this.rightLabel = rightLabel;
		this.unifiedPatch = unifiedPatch;

		CompareConfiguration cc = getCompareConfiguration();
		cc.setLeftEditable(false);
		cc.setRightEditable(false);
		cc.setLeftLabel(leftLabel);
		cc.setRightLabel(rightLabel);

		setTitle("Compare: " + safeName(leftStore) + " \u2194 "
				+ (rightLabel != null ? rightLabel : safeName(rightStore)));
	}

	@Override
	protected Object prepareInput(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
		try {
			ITypedElement leftElement = leftStore != null ? new FileStoreElement(leftStore, leftLabel) : null;
			ITypedElement rightElement;
			if (leftStore != null && unifiedPatch != null && !unifiedPatch.isBlank()) {
				rightElement = new PatchedFileStoreElement(leftStore, leftLabel, unifiedPatch);
			} else {
				rightElement = rightStore != null ? new FileStoreElement(rightStore, rightLabel) : null;
			}
			return new SimpleCompareInput(leftElement, rightElement);
		} catch (CoreException e) {
			throw new InvocationTargetException(e, "Unable to open file for comparison: " + e);
		}
	}

	public static void open(IFileStore left, IFileStore right, String leftLabel, String rightLabel,
			String unifiedPatch) throws PartInitException {
		CompareUI.openCompareEditor(new CompareFileStoreEditorInput(left, right, leftLabel, rightLabel, unifiedPatch),
				true);
	}

	private static String safeName(IFileStore store) {
		return store != null ? store.getName() : "none";
	}

	private static String readAll(IFileStore store) throws CoreException {
		try (InputStream in = store.openInputStream(EFS.NONE, null)) {
			try (java.util.Scanner scanner = new java.util.Scanner(in, StandardCharsets.UTF_8.name())) {
				scanner.useDelimiter("\\A");
				return scanner.hasNext() ? scanner.next() : "";
			}
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, "eclipse.plugin.aiassistant",
					"Unable to read file for comparison: " + store, e));
		}
	}

	private static String applyUnifiedDiff(String original, String diffText, String fileName) {
		UnifiedDiffParser.ParsedDiff parsed = UnifiedDiffParser.parse(diffText);
		List<String> lines = new ArrayList<>(Arrays.asList(original.split("\\R", -1)));
		boolean applied = false;
		for (UnifiedDiffParser.FileOperation operation : parsed.operations()) {
			if (operationMatches(operation, fileName)) {
				for (String hunk : operation.hunks()) {
					lines = applyHunk(lines, hunk);
				}
				applied = true;
			}
		}
		if (!applied && parsed.operations().size() == 1) {
			for (String hunk : parsed.operations().get(0).hunks()) {
				lines = applyHunk(lines, hunk);
			}
		}
		return String.join("\n", lines);
	}

	private static boolean operationMatches(UnifiedDiffParser.FileOperation operation, String fileName) {
		String oldPath = stripPrefix(operation.oldPath());
		String newPath = stripPrefix(operation.newPath());
		return oldPath.equals(fileName) || newPath.equals(fileName)
				|| oldPath.endsWith("/" + fileName) || newPath.endsWith("/" + fileName);
	}

	private static String stripPrefix(String path) {
		String p = path.trim();
		if (p.startsWith("a/") || p.startsWith("b/")) {
			return p.substring(2);
		}
		return p;
	}

	private static List<String> applyHunk(List<String> originalLines, String hunkText) {
		List<String> result = new ArrayList<>(originalLines);
		String[] lines = hunkText.split("\\R", -1);
		Matcher matcher = HUNK_HEADER.matcher(lines[0].trim());
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Invalid hunk header: " + lines[0]);
		}
		int start = Integer.parseInt(matcher.group(1)) - 1;
		int oldCount = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
		if (start < 0 || start > result.size()) {
			throw new IllegalArgumentException("Hunk start out of range: " + start);
		}
		int pos = start;
		int consumed = 0;
		for (int i = 1; i < lines.length; i++) {
			String line = lines[i];
			if (line.isEmpty()) {
				continue;
			}
			char marker = line.charAt(0);
			String content = line.substring(1);
			switch (marker) {
			case ' ':
				if (pos >= result.size() || !result.get(pos).equals(content)) {
					throw new IllegalStateException("Context line mismatch at " + (pos + 1));
				}
				pos++;
				consumed++;
				break;
			case '-':
				if (pos >= result.size() || !result.get(pos).equals(content)) {
					throw new IllegalStateException("Removed line mismatch at " + (pos + 1));
				}
				result.remove(pos);
				consumed++;
				break;
			case '+':
				result.add(pos, content);
				pos++;
				break;
			case '\\':
				// " No newline at end of file" marker
				break;
			default:
				throw new IllegalArgumentException("Unexpected hunk line: " + line);
			}
		}
		if (consumed != oldCount) {
			throw new IllegalStateException("Hunk consumed " + consumed + " lines, expected " + oldCount);
		}
		return result;
	}

	// -- patched element wrapper for IFileStore ---------------------------

	private static final class PatchedFileStoreElement extends PlatformObject
			implements ITypedElement, IStreamContentAccessor {

		private final IFileStore store;
		private final String label;
		private final String unifiedPatch;

		PatchedFileStoreElement(IFileStore store, String label, String unifiedPatch) {
			this.store = store;
			this.label = label;
			this.unifiedPatch = unifiedPatch;
		}

		@Override
		public String getName() {
			return label;
		}

		@Override
		public String getType() {
			return ITypedElement.TEXT_TYPE;
		}

		@Override
		public Image getImage() {
			return null;
		}

		@Override
		public InputStream getContents() throws CoreException {
			String original = readAll(store);
			String patched = applyUnifiedDiff(original, unifiedPatch, store.getName());
			return new ByteArrayInputStream(patched.getBytes(StandardCharsets.UTF_8));
		}
	}

	// -- element wrapper for IFileStore -----------------------------------

	private static final class FileStoreElement extends PlatformObject
			implements ITypedElement, IStreamContentAccessor {

		private final IFileStore store;
		private final String label;

		FileStoreElement(IFileStore store, String label) throws CoreException {
			this.store = store;
			this.label = label;
		}

		@Override
		public String getName() {
			return label;
		}

		@Override
		public String getType() {
			return ITypedElement.TEXT_TYPE;
		}

		@Override
		public Image getImage() {
			return null;
		}

		@Override
		public InputStream getContents() throws CoreException {
			return store.openInputStream(EFS.NONE, null);
		}
	}

	// -- simple ICompareInput implementation ------------------------------

	private static final class SimpleCompareInput extends PlatformObject implements ICompareInput {
		private final java.util.List<ICompareInputChangeListener> changeListeners = new java.util.ArrayList<>();
		private final ITypedElement left;
		private final ITypedElement right;

		SimpleCompareInput(ITypedElement left, ITypedElement right) {
			this.left = left;
			this.right = right;
		}

		@Override
		public void addCompareInputChangeListener(ICompareInputChangeListener listener) {
			if (listener != null && !changeListeners.contains(listener)) {
				changeListeners.add(listener);
			}
		}

		@Override
		public void removeCompareInputChangeListener(ICompareInputChangeListener listener) {
			if (listener != null) {
				changeListeners.remove(listener);
			}
		}

		@Override
		public Image getImage() {
			return null;
		}

		@Override
		public int getKind() {
			return org.eclipse.compare.structuremergeviewer.Differencer.CHANGE;
		}

		@Override
		public ITypedElement getAncestor() {
			return null;
		}

		@Override
		public ITypedElement getLeft() {
			return left;
		}

		@Override
		public ITypedElement getRight() {
			return right;
		}

		@Override
		public String getName() {
			ITypedElement left = getLeft();
			if (left != null) {
				return left.getName();
			}
			ITypedElement right = getRight();
			if (right != null) {
				return right.getName();
			}
			return ""; // NON-NLS-1
		}

		@Override
		public void copy(boolean leftToRight) {
			// read-only comparison
		}
	}
}