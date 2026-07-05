package fatfs;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DirectoryEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String name;
    private final boolean directory;
    private int size;
    private int startBlock;
    private DirectoryEntry parent;
    private final LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private final List<DirectoryEntry> children;

    public DirectoryEntry(String name, boolean directory, int size, int startBlock, DirectoryEntry parent) {
        this.name = name;
        this.directory = directory;
        this.size = size;
        this.startBlock = startBlock;
        this.parent = parent;
        this.createdAt = LocalDateTime.now();
        this.modifiedAt = this.createdAt;
        this.children = directory ? new ArrayList<>() : Collections.emptyList();
    }

    public static DirectoryEntry root() {
        return new DirectoryEntry("/", true, 0, -1, null);
    }

    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return directory;
    }

    public boolean isFile() {
        return !directory;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
        touch();
    }

    public int getStartBlock() {
        return startBlock;
    }

    public void setStartBlock(int startBlock) {
        this.startBlock = startBlock;
        touch();
    }

    public DirectoryEntry getParent() {
        return parent;
    }

    public void setParent(DirectoryEntry parent) {
        this.parent = parent;
    }

    public List<DirectoryEntry> getChildren() {
        return children;
    }

    public String getCreatedText() {
        return createdAt.format(FORMATTER);
    }

    public String getModifiedText() {
        return modifiedAt.format(FORMATTER);
    }

    public void addChild(DirectoryEntry child) {
        ensureDirectory();
        children.add(child);
        child.setParent(this);
        touch();
    }

    public void removeChild(DirectoryEntry child) {
        ensureDirectory();
        children.remove(child);
        touch();
    }

    public DirectoryEntry findChild(String childName) {
        ensureDirectory();
        for (DirectoryEntry child : children) {
            if (child.getName().equalsIgnoreCase(childName)) {
                return child;
            }
        }
        return null;
    }

    public String path() {
        if (parent == null) {
            return "/";
        }
        String parentPath = parent.path();
        return "/".equals(parentPath) ? parentPath + name : parentPath + "/" + name;
    }

    public void touch() {
        modifiedAt = LocalDateTime.now();
    }

    private void ensureDirectory() {
        if (!directory) {
            throw new IllegalStateException("文件不能包含子项");
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
