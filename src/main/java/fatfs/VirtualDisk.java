package fatfs;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class VirtualDisk {
    public static final int DEFAULT_BLOCK_SIZE = 512;
    public static final int DEFAULT_BLOCK_COUNT = 256;

    private final Path diskPath;
    private DiskImage image;

    public VirtualDisk(Path diskPath) throws IOException {
        this.diskPath = diskPath;
        loadOrCreate();
    }

    public DiskImage getImage() {
        return image;
    }

    public void format() throws IOException {
        image = new DiskImage(DEFAULT_BLOCK_SIZE, DEFAULT_BLOCK_COUNT);
        save();
    }

    public void save() throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(diskPath))) {
            out.writeObject(image);
        }
    }

    public void writeBlock(int index, byte[] data) {
        checkIndex(index);
        Arrays.fill(image.getBlocks()[index], (byte) 0);
        System.arraycopy(data, 0, image.getBlocks()[index], 0, Math.min(data.length, image.getBlockSize()));
    }

    public byte[] readBlock(int index) {
        checkIndex(index);
        return Arrays.copyOf(image.getBlocks()[index], image.getBlockSize());
    }

    public void clearBlock(int index) {
        checkIndex(index);
        Arrays.fill(image.getBlocks()[index], (byte) 0);
    }

    private void loadOrCreate() throws IOException {
        if (!Files.exists(diskPath)) {
            format();
            return;
        }

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(diskPath))) {
            Object value = in.readObject();
            if (!(value instanceof DiskImage)) {
                throw new IOException("磁盘文件格式不正确");
            }
            image = (DiskImage) value;
            if (image.getBlockSize() != DEFAULT_BLOCK_SIZE || image.getBlockCount() != DEFAULT_BLOCK_COUNT) {
                format();
            }
        } catch (ClassNotFoundException | RuntimeException ex) {
            throw new IOException("读取模拟磁盘失败，请格式化后重试", ex);
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= image.getBlockCount()) {
            throw new IllegalArgumentException("磁盘块编号越界: " + index);
        }
    }
}
