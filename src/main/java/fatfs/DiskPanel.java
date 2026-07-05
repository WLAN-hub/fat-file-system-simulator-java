package fatfs;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiskPanel extends JPanel {
    public static final Color FREE_COLOR = new Color(232, 247, 236);
    public static final Color SYSTEM_COLOR = new Color(255, 203, 213);
    public static final Color DIRECTORY_COLOR = new Color(211, 198, 235);
    public static final Color FILE_COLOR = new Color(180, 217, 247);
    public static final Color SELECTED_COLOR = new Color(255, 221, 72);

    private static final int COLUMNS = 16;
    private static final int GAP = 5;
    private static final Color BORDER_COLOR = new Color(77, 89, 87);
    private static final Color TEXT_COLOR = new Color(44, 54, 58);

    private int[] fat = new int[0];
    private Set<Integer> highlighted = Set.of();
    private Rectangle[] blockBounds = new Rectangle[0];

    public DiskPanel() {
        setPreferredSize(new Dimension(360, 360));
        setMinimumSize(new Dimension(300, 320));
        setBackground(Color.WHITE);
        setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 10));
        setToolTipText("");
    }

    public void updateState(int[] fat, List<Integer> highlightedBlocks) {
        this.fat = fat == null ? new int[0] : fat.clone();
        this.highlighted = new HashSet<>(highlightedBlocks == null ? List.of() : highlightedBlocks);
        this.blockBounds = new Rectangle[this.fat.length];
        repaint();
    }

    public String describeBlock(int block) {
        if (block < 0 || block >= fat.length) {
            return "";
        }
        return "块号: " + block + " | 状态: " + statusFor(block)
                + " | FAT值: " + fat[block]
                + (highlighted.contains(block) ? " | 当前选中文件占用" : "");
    }

    public String statusFor(int block) {
        if (block == 0) {
            return "系统区";
        }
        if (block == 1) {
            return "目录块";
        }
        if (highlighted.contains(block)) {
            return "选中文件块";
        }
        if (block >= 0 && block < fat.length && fat[block] != FatTable.FREE) {
            return "文件数据块";
        }
        return "空闲";
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int block = blockAt(event.getPoint());
        return block >= 0 ? describeBlock(block) : null;
    }

    private int blockAt(Point point) {
        for (int i = 0; i < blockBounds.length; i++) {
            Rectangle bounds = blockBounds[i];
            if (bounds != null && bounds.contains(point)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int rows = Math.max(1, (int) Math.ceil(fat.length / (double) COLUMNS));
        int availableWidth = getWidth() - 18;
        int availableHeight = getHeight() - 18;
        int cellByWidth = (availableWidth - GAP * (COLUMNS - 1)) / COLUMNS;
        int cellByHeight = (availableHeight - GAP * (rows - 1)) / rows;
        int cell = Math.max(12, Math.min(24, Math.min(cellByWidth, cellByHeight)));

        int gridWidth = COLUMNS * cell + (COLUMNS - 1) * GAP;
        int startX = Math.max(9, (getWidth() - gridWidth) / 2);
        int startY = 8;

        FontMetrics metrics = g2.getFontMetrics();
        for (int i = 0; i < fat.length; i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int x = startX + col * (cell + GAP);
            int y = startY + row * (cell + GAP);
            blockBounds[i] = new Rectangle(x, y, cell, cell);

            g2.setColor(colorFor(i));
            g2.fillRoundRect(x, y, cell, cell, 4, 4);
            g2.setColor(BORDER_COLOR);
            g2.drawRoundRect(x, y, cell, cell, 4, 4);

            if (cell >= 18 && (highlighted.contains(i) || i < FatTable.RESERVED_BLOCKS)) {
                String text = String.valueOf(i);
                int textX = x + (cell - metrics.stringWidth(text)) / 2;
                int textY = y + (cell + metrics.getAscent() - metrics.getDescent()) / 2;
                g2.setColor(TEXT_COLOR);
                g2.drawString(text, textX, textY);
            }
        }

        g2.dispose();
    }

    private Color colorFor(int block) {
        if (highlighted.contains(block)) {
            return SELECTED_COLOR;
        }
        if (block == 0) {
            return SYSTEM_COLOR;
        }
        if (block == 1) {
            return DIRECTORY_COLOR;
        }
        if (block < fat.length && fat[block] != FatTable.FREE) {
            return FILE_COLOR;
        }
        return FREE_COLOR;
    }
}
