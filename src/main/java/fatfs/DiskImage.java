package fatfs;

import java.io.Serializable;

public class DiskImage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int blockSize;
    private final int blockCount;
    private final int[] fat;
    private final byte[][] blocks;
    private final DirectoryEntry root;

    public DiskImage(int blockSize, int blockCount) {
        this.blockSize = blockSize;
        this.blockCount = blockCount;
        this.fat = new int[blockCount];
        this.blocks = new byte[blockCount][blockSize];
        this.root = DirectoryEntry.root();
        for (int i = 0; i < fat.length; i++) {
            fat[i] = FatTable.FREE;
        }
        for (int i = 0; i < Math.min(FatTable.RESERVED_BLOCKS, fat.length); i++) {
            fat[i] = FatTable.EOF;
        }
    }

    public int getBlockSize() {
        return blockSize;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public int[] getFat() {
        return fat;
    }

    public byte[][] getBlocks() {
        return blocks;
    }

    public DirectoryEntry getRoot() {
        return root;
    }
}
