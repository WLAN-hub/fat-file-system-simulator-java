package fatfs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FileSystemService {
    private final VirtualDisk disk;
    private FatTable fatTable;

    public FileSystemService(Path diskPath) throws IOException {
        disk = new VirtualDisk(diskPath);
        fatTable = new FatTable(disk.getImage().getFat());
    }

    public DirectoryEntry root() {
        return disk.getImage().getRoot();
    }

    public int blockSize() {
        return disk.getImage().getBlockSize();
    }

    public int blockCount() {
        return disk.getImage().getBlockCount();
    }

    public int[] fatSnapshot() {
        return fatTable.raw().clone();
    }

    public int freeBlocks() {
        return fatTable.freeCount();
    }

    public int usedBlocks() {
        return fatTable.usedCount();
    }

    public List<DirectoryEntry> listDirectory(DirectoryEntry directory) {
        ensureDirectory(directory);
        List<DirectoryEntry> entries = new ArrayList<>(directory.getChildren());
        entries.sort(Comparator.comparing(DirectoryEntry::isFile).thenComparing(DirectoryEntry::getName, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    public DirectoryEntry mkdir(DirectoryEntry parent, String name) throws IOException {
        ensureDirectory(parent);
        validateName(parent, name);
        DirectoryEntry entry = new DirectoryEntry(name.trim(), true, 0, -1, parent);
        parent.addChild(entry);
        disk.save();
        return entry;
    }

    public DirectoryEntry createFile(DirectoryEntry parent, String name, String content) throws IOException {
        ensureDirectory(parent);
        validateName(parent, name);
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        DirectoryEntry entry = new DirectoryEntry(name.trim(), false, data.length, -1, parent);
        writeDataToEntry(entry, data);
        parent.addChild(entry);
        disk.save();
        return entry;
    }

    public void overwriteFile(DirectoryEntry entry, String content) throws IOException {
        if (entry == null || !entry.isFile()) {
            throw new IllegalArgumentException("请选择文件");
        }
        releaseFileBlocks(entry);
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        entry.setSize(data.length);
        writeDataToEntry(entry, data);
        disk.save();
    }

    public DirectoryEntry copyFile(DirectoryEntry source, DirectoryEntry targetDirectory) throws IOException {
        if (source == null || !source.isFile()) {
            throw new IllegalArgumentException("只能复制文件");
        }
        ensureDirectory(targetDirectory);

        String copyName = nextCopyName(targetDirectory, source.getName());
        byte[] data = readFile(source);
        DirectoryEntry copy = new DirectoryEntry(copyName, false, data.length, -1, targetDirectory);
        writeDataToEntry(copy, data);
        targetDirectory.addChild(copy);
        disk.save();
        return copy;
    }

    public void delete(DirectoryEntry entry) throws IOException {
        if (entry == null || entry.getParent() == null) {
            throw new IllegalArgumentException("不能删除根目录");
        }
        if (entry.isDirectory() && !entry.getChildren().isEmpty()) {
            throw new IllegalStateException("目录非空，不能删除");
        }
        if (entry.isFile()) {
            releaseFileBlocks(entry);
        }
        entry.getParent().removeChild(entry);
        disk.save();
    }

    public byte[] readFile(DirectoryEntry entry) {
        if (entry == null || !entry.isFile()) {
            return new byte[0];
        }
        byte[] result = new byte[entry.getSize()];
        int offset = 0;
        for (int block : fatTable.chain(entry.getStartBlock())) {
            byte[] blockData = disk.readBlock(block);
            int length = Math.min(blockData.length, result.length - offset);
            if (length <= 0) {
                break;
            }
            System.arraycopy(blockData, 0, result, offset, length);
            offset += length;
        }
        return result;
    }

    public String readFileText(DirectoryEntry entry) {
        return new String(readFile(entry), StandardCharsets.UTF_8);
    }

    public List<Integer> getFileBlocks(DirectoryEntry entry) {
        if (entry == null || !entry.isFile()) {
            return List.of();
        }
        return fatTable.chain(entry.getStartBlock());
    }

    public void format() throws IOException {
        disk.format();
        fatTable = new FatTable(disk.getImage().getFat());
    }

    private void writeDataToEntry(DirectoryEntry entry, byte[] data) {
        int requiredBlocks = (int) Math.ceil(data.length / (double) blockSize());
        if (requiredBlocks == 0) {
            entry.setStartBlock(-1);
            return;
        }

        List<Integer> blocks = fatTable.allocate(requiredBlocks);
        int offset = 0;
        for (int block : blocks) {
            int length = Math.min(blockSize(), data.length - offset);
            byte[] part = new byte[length];
            System.arraycopy(data, offset, part, 0, length);
            disk.writeBlock(block, part);
            offset += length;
        }
        entry.setStartBlock(blocks.get(0));
    }

    private void releaseFileBlocks(DirectoryEntry entry) {
        for (int block : fatTable.chain(entry.getStartBlock())) {
            disk.clearBlock(block);
        }
        fatTable.release(entry.getStartBlock());
        entry.setStartBlock(-1);
    }

    private void validateName(DirectoryEntry parent, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        String cleaned = name.trim();
        if (cleaned.contains("/") || cleaned.contains("\\") || cleaned.length() > 40) {
            throw new IllegalArgumentException("名称不能包含路径分隔符，且长度不能超过 40");
        }
        if (parent.findChild(cleaned) != null) {
            throw new IllegalArgumentException("同名文件或目录已存在");
        }
    }

    private String nextCopyName(DirectoryEntry directory, String originalName) {
        String base = originalName;
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) {
            base = originalName.substring(0, dot);
            ext = originalName.substring(dot);
        }

        for (int i = 1; ; i++) {
            String candidate = base + "_copy" + (i == 1 ? "" : i) + ext;
            if (directory.findChild(candidate) == null) {
                return candidate;
            }
        }
    }

    private void ensureDirectory(DirectoryEntry entry) {
        if (entry == null || !entry.isDirectory()) {
            throw new IllegalArgumentException("请选择目录");
        }
    }
}
