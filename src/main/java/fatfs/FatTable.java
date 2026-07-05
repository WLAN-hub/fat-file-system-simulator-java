package fatfs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FatTable {
    public static final int FREE = -1;
    public static final int EOF = -2;
    public static final int RESERVED_BLOCKS = 2;

    private final int[] table;

    public FatTable(int[] table) {
        this.table = table;
    }

    public int[] raw() {
        return table;
    }

    public List<Integer> allocate(int blockCount) {
        if (blockCount <= 0) {
            return List.of();
        }
        if (freeCount() < blockCount) {
            throw new IllegalStateException("磁盘空间不足");
        }

        List<Integer> allocated = new ArrayList<>();
        for (int i = 0; i < table.length && allocated.size() < blockCount; i++) {
            if (table[i] == FREE) {
                allocated.add(i);
            }
        }

        for (int i = 0; i < allocated.size(); i++) {
            int current = allocated.get(i);
            table[current] = i == allocated.size() - 1 ? EOF : allocated.get(i + 1);
        }
        return allocated;
    }

    public List<Integer> chain(int startBlock) {
        List<Integer> result = new ArrayList<>();
        if (startBlock < 0) {
            return result;
        }

        boolean[] visited = new boolean[table.length];
        int current = startBlock;
        while (current >= 0 && current < table.length && !visited[current]) {
            result.add(current);
            visited[current] = true;
            int next = table[current];
            if (next == EOF) {
                break;
            }
            current = next;
        }
        return result;
    }

    public void release(int startBlock) {
        for (int block : chain(startBlock)) {
            table[block] = FREE;
        }
    }

    public int freeCount() {
        int count = 0;
        for (int value : table) {
            if (value == FREE) {
                count++;
            }
        }
        return count;
    }

    public int usedCount() {
        return table.length - freeCount();
    }

    public void clear() {
        Arrays.fill(table, FREE);
        for (int i = 0; i < Math.min(RESERVED_BLOCKS, table.length); i++) {
            table[i] = EOF;
        }
    }
}
