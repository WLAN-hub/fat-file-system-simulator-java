package fatfs;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {
    private static final Font UI_FONT = new Font("Microsoft YaHei UI", Font.PLAIN, 14);
    private static final Font UI_BOLD = new Font("Microsoft YaHei UI", Font.BOLD, 14);
    private static final Color BACKGROUND = new Color(244, 246, 248);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final FileSystemService service;
    private DirectoryEntry currentDirectory;
    private DirectoryEntry selectedEntry;
    private boolean refreshingTree;

    private final EntryTableModel tableModel = new EntryTableModel();
    private final JTable table = new JTable(tableModel);
    private final JTree directoryTree = new JTree();
    private final JLabel emptyLabel = new JLabel("当前目录为空，可点击“新建目录”或“新建文件”。", JLabel.CENTER);
    private final DiskPanel diskPanel = new DiskPanel();
    private final JTextArea fatInfoArea = new JTextArea();
    private final JTextArea logArea = new JTextArea();
    private final JTextField pathField = new JTextField("/");
    private final JLabel summaryLabel = new JLabel();

    public MainFrame() throws IOException {
        super("模拟文件系统 —— 基于FAT文件分配表");
        setupLookAndFeel();
        service = new FileSystemService(Path.of("virtual-disk.dat"));
        currentDirectory = service.root();
        selectedEntry = currentDirectory;
        buildUi();
        refreshAll();
        appendLog("系统启动，加载模拟磁盘 virtual-disk.dat");
    }

    private void setupLookAndFeel() {
        UIManager.put("Label.font", UI_FONT);
        UIManager.put("Button.font", UI_BOLD);
        UIManager.put("Table.font", UI_FONT);
        UIManager.put("TableHeader.font", UI_BOLD);
        UIManager.put("TextField.font", UI_FONT);
        UIManager.put("TextArea.font", UI_FONT);
        UIManager.put("Tree.font", UI_FONT);
        UIManager.put("TitledBorder.font", UI_BOLD);
    }

    private void buildUi() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1260, 760));
        setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        root.add(createToolbar(), BorderLayout.NORTH);
        root.add(createUnifiedContent(), BorderLayout.CENTER);
        setContentPane(root);

        setupTable();
        setupTree();
    }

    private void setupTable() {
        table.setFont(UI_FONT);
        table.getTableHeader().setFont(UI_BOLD);
        table.setRowHeight(34);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setShowGrid(true);
        table.setGridColor(new Color(210, 217, 224));
        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);

        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            int row = table.getSelectedRow();
            selectedEntry = row >= 0 ? tableModel.getEntry(table.convertRowIndexToModel(row)) : currentDirectory;
            refreshDiskAndFatInfo();
        });
    }

    private void setupTree() {
        directoryTree.setRootVisible(true);
        directoryTree.setShowsRootHandles(true);
        directoryTree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public java.awt.Component getTreeCellRendererComponent(
                    JTree tree, Object value, boolean selected, boolean expanded,
                    boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObject instanceof DirectoryEntry entry) {
                    setText((entry.isDirectory() ? "[目录] " : "[文件] ") + entry.getName());
                    setIcon(null);
                }
                return this;
            }
        });
        directoryTree.addTreeSelectionListener(event -> {
            if (refreshingTree) {
                return;
            }
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) directoryTree.getLastSelectedPathComponent();
            if (node == null || !(node.getUserObject() instanceof DirectoryEntry entry)) {
                return;
            }
            selectedEntry = entry;
            if (entry.isDirectory()) {
                currentDirectory = entry;
                refreshTable();
                appendLog("切换目录: " + currentDirectory.path());
            } else {
                currentDirectory = entry.getParent();
                refreshTable();
                selectTableEntry(entry);
                appendLog("选择文件: " + entry.path());
            }
            refreshDiskAndFatInfo();
        });
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setBorder(new EmptyBorder(4, 0, 4, 0));
        toolbar.setOpaque(false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(button("新建目录", new Color(40, 174, 78), this::createDirectory));
        buttons.add(button("新建文件", new Color(30, 136, 210), this::createFile));
        buttons.add(button("写文件内容", new Color(239, 139, 0), this::writeSelectedFile));
        buttons.add(button("查看文件", new Color(145, 42, 172), this::viewSelectedFile));
        buttons.add(button("进入目录", new Color(58, 91, 184), this::enterSelectedDirectory));
        buttons.add(button("返回上级", new Color(0, 151, 136), this::goParent));
        buttons.add(button("删除", new Color(238, 65, 55), this::deleteSelected));
        buttons.add(button("复制", new Color(113, 78, 64), this::copySelectedFile));
        buttons.add(moreButton());

        JPanel pathPanel = new JPanel(new BorderLayout(6, 0));
        pathPanel.setOpaque(false);
        pathField.setPreferredSize(new Dimension(120, 34));
        pathField.setFont(UI_FONT);
        JLabel pathLabel = new JLabel("路径:");
        pathLabel.setFont(UI_BOLD);
        pathPanel.add(pathLabel, BorderLayout.WEST);
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(button("跳转", new Color(70, 70, 70), this::jumpToPath), BorderLayout.EAST);

        toolbar.add(buttons, BorderLayout.CENTER);
        toolbar.add(pathPanel, BorderLayout.EAST);
        return toolbar;
    }

    private JButton moreButton() {
        JButton button = new JButton("更多操作");
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(66, 99, 130));
        button.setFont(UI_BOLD);
        int width = button.getFontMetrics(UI_BOLD).stringWidth("更多操作") + 34;
        button.setPreferredSize(new Dimension(width, 36));
        button.setMinimumSize(new Dimension(width, 36));

        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("刷新", this::refreshAllWithLog));
        menu.add(menuItem("重置磁盘", this::resetDisk));
        menu.add(menuItem("查看FAT表", this::showFatTable));
        button.addActionListener(event -> menu.show(button, 0, button.getHeight()));
        return button;
    }

    private JMenuItem menuItem(String text, RunnableWithIOException action) {
        JMenuItem item = new JMenuItem(text);
        item.setFont(UI_FONT);
        item.addActionListener(event -> runSafely(action));
        return item;
    }

    private JButton button(String text, Color color, RunnableWithIOException action) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFont(UI_BOLD);
        int width = Math.max(96, button.getFontMetrics(UI_BOLD).stringWidth(text) + 34);
        button.setPreferredSize(new Dimension(width, 36));
        button.setMinimumSize(new Dimension(width, 36));
        button.addActionListener(event -> runSafely(action));
        return button;
    }

    private JSplitPane createUnifiedContent() {
        JSplitPane leftTop = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createTreePanel(), createTablePanel());
        leftTop.setResizeWeight(0.22);
        leftTop.setDividerLocation(0.22);
        leftTop.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane leftColumn = new JSplitPane(JSplitPane.VERTICAL_SPLIT, leftTop, createFatPanel());
        leftColumn.setResizeWeight(0.78);
        leftColumn.setDividerLocation(0.78);
        leftColumn.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane rightColumn = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createDiskPanel(), createLogPanel());
        rightColumn.setResizeWeight(0.78);
        rightColumn.setDividerLocation(0.78);
        rightColumn.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftColumn, rightColumn);
        mainSplit.setResizeWeight(0.68);
        mainSplit.setDividerLocation(0.68);
        mainSplit.setBorder(BorderFactory.createEmptyBorder());
        return mainSplit;
    }

    private JPanel createTreePanel() {
        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.setBackground(Color.WHITE);
        treePanel.setBorder(BorderFactory.createTitledBorder("目录树"));
        treePanel.add(new JScrollPane(directoryTree), BorderLayout.CENTER);
        return treePanel;
    }

    private JPanel createTablePanel() {
        JPanel tablePanel = new JPanel(new BorderLayout(0, 6));
        tablePanel.setBackground(Color.WHITE);
        tablePanel.setBorder(BorderFactory.createTitledBorder("目录和文件"));
        tablePanel.add(new JScrollPane(table), BorderLayout.CENTER);
        emptyLabel.setFont(UI_FONT);
        emptyLabel.setForeground(new Color(116, 126, 138));
        emptyLabel.setBorder(new EmptyBorder(6, 0, 2, 0));
        tablePanel.add(emptyLabel, BorderLayout.SOUTH);
        return tablePanel;
    }

    private JPanel createDiskPanel() {
        JPanel diskPanelWrapper = new JPanel(new BorderLayout(0, 4));
        diskPanelWrapper.setBackground(Color.WHITE);
        diskPanelWrapper.setBorder(BorderFactory.createTitledBorder("磁盘块分配状态"));
        diskPanelWrapper.add(diskPanel, BorderLayout.CENTER);
        diskPanelWrapper.add(createLegendPanel(), BorderLayout.SOUTH);
        return diskPanelWrapper;
    }

    private JPanel createFatPanel() {
        JPanel fatPanel = new JPanel(new BorderLayout());
        fatPanel.setBackground(Color.WHITE);
        fatPanel.setBorder(BorderFactory.createTitledBorder("选中文件/目录的FAT信息"));
        fatInfoArea.setEditable(false);
        fatInfoArea.setFont(UI_FONT);
        fatInfoArea.setLineWrap(true);
        fatInfoArea.setWrapStyleWord(true);
        fatInfoArea.setBorder(new EmptyBorder(4, 6, 4, 6));
        fatPanel.add(new JScrollPane(fatInfoArea), BorderLayout.CENTER);
        return fatPanel;
    }

    private JPanel createLogPanel() {
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBackground(Color.WHITE);
        logPanel.setBorder(BorderFactory.createTitledBorder("操作日志"));
        logArea.setEditable(false);
        logArea.setFont(UI_FONT);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBorder(new EmptyBorder(4, 6, 4, 6));
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return logPanel;
    }

    private JPanel createLegendPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(new EmptyBorder(2, 8, 6, 8));

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        legend.setOpaque(false);
        legend.add(legendItem("空闲", DiskPanel.FREE_COLOR));
        legend.add(legendItem("系统区", DiskPanel.SYSTEM_COLOR));
        legend.add(legendItem("目录块", DiskPanel.DIRECTORY_COLOR));
        legend.add(legendItem("文件数据块", DiskPanel.FILE_COLOR));
        legend.add(legendItem("高亮", DiskPanel.SELECTED_COLOR));

        summaryLabel.setFont(UI_BOLD);
        summaryLabel.setHorizontalAlignment(JLabel.CENTER);
        wrapper.add(legend, BorderLayout.NORTH);
        wrapper.add(summaryLabel, BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel legendItem(String text, Color color) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        item.setOpaque(false);
        JLabel square = new JLabel("  ");
        square.setPreferredSize(new Dimension(16, 16));
        square.setOpaque(true);
        square.setBackground(color);
        square.setBorder(BorderFactory.createLineBorder(new Color(212, 218, 224)));
        JLabel label = new JLabel(text);
        label.setFont(UI_FONT);
        item.add(square);
        item.add(label);
        return item;
    }

    private void createDirectory() throws IOException {
        String name = JOptionPane.showInputDialog(this, "目录名称:", "新建目录", JOptionPane.PLAIN_MESSAGE);
        if (name != null) {
            DirectoryEntry entry = service.mkdir(currentDirectory, name);
            selectedEntry = entry;
            refreshAll();
            appendLog("创建目录: " + entry.path() + "，更新父目录项 " + currentDirectory.path());
        }
    }

    private void createFile() throws IOException {
        String name = JOptionPane.showInputDialog(this, "文件名称:", "新建文件", JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        String content = inputContent("输入文件内容", "");
        if (content != null) {
            DirectoryEntry entry = service.createFile(currentDirectory, name, content);
            selectedEntry = entry;
            List<Integer> blocks = service.getFileBlocks(entry);
            refreshAll();
            appendLog("创建文件: " + entry.path() + "，分配块 " + blocks);
        }
    }

    private void writeSelectedFile() throws IOException {
        DirectoryEntry entry = requireSelectedFile();
        List<Integer> oldBlocks = service.getFileBlocks(entry);
        String content = inputContent("修改文件内容: " + entry.getName(), service.readFileText(entry));
        if (content != null) {
            service.overwriteFile(entry, content);
            List<Integer> newBlocks = service.getFileBlocks(entry);
            refreshAll();
            appendLog("写文件内容: " + entry.path() + "，释放块 " + oldBlocks + "，重新分配块 " + newBlocks);
        }
    }

    private void viewSelectedFile() {
        DirectoryEntry entry = requireSelectedFile();
        JTextArea area = new JTextArea(service.readFileText(entry), 14, 48);
        area.setFont(UI_FONT);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "查看文件: " + entry.getName(), JOptionPane.PLAIN_MESSAGE);
        appendLog("查看文件: " + entry.path());
    }

    private void enterSelectedDirectory() {
        if (selectedEntry == null || !selectedEntry.isDirectory()) {
            throw new IllegalArgumentException("请选择一个目录");
        }
        currentDirectory = selectedEntry;
        selectedEntry = currentDirectory;
        refreshAll();
        appendLog("进入目录: " + currentDirectory.path());
    }

    private void goParent() {
        if (currentDirectory.getParent() != null) {
            currentDirectory = currentDirectory.getParent();
            selectedEntry = currentDirectory;
            refreshAll();
            appendLog("返回上级目录: " + currentDirectory.path());
        }
    }

    private void deleteSelected() throws IOException {
        DirectoryEntry entry = selectedEntry;
        if (entry == null || entry == currentDirectory || entry.getParent() == null) {
            throw new IllegalArgumentException("请选择要删除的文件或目录");
        }
        List<Integer> released = entry.isFile() ? service.getFileBlocks(entry) : List.of();
        String path = entry.path();
        int result = JOptionPane.showConfirmDialog(this, "确认删除 " + entry.getName() + " ?", "删除确认", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            service.delete(entry);
            selectedEntry = currentDirectory;
            refreshAll();
            appendLog((released.isEmpty() ? "删除目录: " : "删除文件: ") + path
                    + (released.isEmpty() ? "，更新目录项" : "，释放块 " + released));
        }
    }

    private void copySelectedFile() throws IOException {
        DirectoryEntry source = requireSelectedFile();
        DirectoryEntry copy = service.copyFile(source, currentDirectory);
        selectedEntry = copy;
        List<Integer> blocks = service.getFileBlocks(copy);
        refreshAll();
        appendLog("复制文件: " + source.path() + " -> " + copy.path() + "，分配块 " + blocks);
    }

    private void jumpToPath() {
        DirectoryEntry target = findByPath(pathField.getText().trim());
        if (target == null || !target.isDirectory()) {
            throw new IllegalArgumentException("目录不存在");
        }
        currentDirectory = target;
        selectedEntry = currentDirectory;
        refreshAll();
        appendLog("跳转目录: " + currentDirectory.path());
    }

    private void refreshAllWithLog() {
        refreshAll();
        appendLog("刷新界面: 目录树、文件列表、磁盘块状态");
    }

    private void resetDisk() throws IOException {
        int result = JOptionPane.showConfirmDialog(
                this,
                "重置磁盘会清空所有目录和文件，是否继续？",
                "重置磁盘",
                JOptionPane.YES_NO_OPTION
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        service.format();
        currentDirectory = service.root();
        selectedEntry = currentDirectory;
        logArea.setText("");
        refreshAll();
        appendLog("重置磁盘: 清空目录项和 FAT 表，恢复系统区与目录区");
    }

    private void refreshAll() {
        refreshTree();
        refreshTable();
        refreshDiskAndFatInfo();
    }

    private void refreshTree() {
        refreshingTree = true;
        DefaultMutableTreeNode rootNode = buildTreeNode(service.root());
        directoryTree.setModel(new DefaultTreeModel(rootNode));
        expandAllTreeRows();
        selectTreeNode(rootNode, currentDirectory);
        refreshingTree = false;
    }

    private DefaultMutableTreeNode buildTreeNode(DirectoryEntry entry) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
        for (DirectoryEntry child : service.listDirectory(entry)) {
            node.add(child.isDirectory() ? buildTreeNode(child) : new DefaultMutableTreeNode(child));
        }
        return node;
    }

    private void expandAllTreeRows() {
        for (int i = 0; i < directoryTree.getRowCount(); i++) {
            directoryTree.expandRow(i);
        }
    }

    private boolean selectTreeNode(DefaultMutableTreeNode node, DirectoryEntry target) {
        if (node.getUserObject() == target) {
            directoryTree.setSelectionPath(new TreePath(node.getPath()));
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (selectTreeNode((DefaultMutableTreeNode) node.getChildAt(i), target)) {
                return true;
            }
        }
        return false;
    }

    private void refreshTable() {
        pathField.setText(currentDirectory.path());
        tableModel.setEntries(service.listDirectory(currentDirectory));
        emptyLabel.setVisible(tableModel.getRowCount() == 0);
    }

    private void selectTableEntry(DirectoryEntry entry) {
        table.clearSelection();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getEntry(i) == entry) {
                int viewRow = table.convertRowIndexToView(i);
                table.setRowSelectionInterval(viewRow, viewRow);
                table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                return;
            }
        }
    }

    private void refreshDiskAndFatInfo() {
        List<Integer> blocks = selectedEntry != null && selectedEntry.isFile() ? service.getFileBlocks(selectedEntry) : List.of();
        diskPanel.updateState(service.fatSnapshot(), blocks);
        int total = service.blockCount();
        int used = service.usedBlocks();
        int free = service.freeBlocks();
        int percent = total == 0 ? 0 : Math.round(used * 100f / total);
        summaryLabel.setText("总块: " + total + " | 已用: " + used + " | 空闲: " + free + " | 使用率: " + percent + "%");
        fatInfoArea.setText(buildFatInfo(blocks));
    }

    private String buildFatInfo(List<Integer> blocks) {
        if (selectedEntry == null || selectedEntry == currentDirectory) {
            return "当前目录: " + currentDirectory.path()
                    + "\n请选择文件查看 FAT 链，或选择目录后点击“进入目录”。";
        }
        if (selectedEntry.isDirectory()) {
            return "目录: " + selectedEntry.path()
                    + "\n目录项保存在目录结构中；本模拟系统将 1 号块标记为目录区。";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("文件: ").append(selectedEntry.path()).append('\n');
        builder.append("大小: ").append(selectedEntry.getSize()).append(" B    ");
        builder.append("起始块: ").append(selectedEntry.getStartBlock()).append("    ");
        builder.append("盘块数: ").append(blocks.size()).append('\n');
        builder.append("块链: ").append(blocks).append('\n');
        builder.append("FAT 表项: ");
        int[] fat = service.fatSnapshot();
        for (int block : blocks) {
            builder.append("FAT[").append(block).append("]=").append(fat[block]).append("  ");
        }
        return builder.toString();
    }

    private void showFatTable() {
        int[] fat = service.fatSnapshot();
        List<Integer> selectedBlocks = selectedEntry != null && selectedEntry.isFile() ? service.getFileBlocks(selectedEntry) : List.of();
        JTable fatTable = new JTable(new FatTableModel(fat, selectedBlocks, diskPanel));
        fatTable.setFont(UI_FONT);
        fatTable.getTableHeader().setFont(UI_BOLD);
        fatTable.setRowHeight(28);
        JScrollPane scrollPane = new JScrollPane(fatTable);
        scrollPane.setPreferredSize(new Dimension(520, 420));
        JOptionPane.showMessageDialog(this, scrollPane, "FAT 文件分配表", JOptionPane.PLAIN_MESSAGE);
        appendLog("查看 FAT 表: 共 " + fat.length + " 个磁盘块");
    }

    private String inputContent(String title, String initialText) {
        JTextArea area = new JTextArea(initialText, 12, 48);
        area.setFont(UI_FONT);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        int result = JOptionPane.showConfirmDialog(this, new JScrollPane(area), title, JOptionPane.OK_CANCEL_OPTION);
        return result == JOptionPane.OK_OPTION ? area.getText() : null;
    }

    private DirectoryEntry requireSelectedFile() {
        if (selectedEntry == null || !selectedEntry.isFile()) {
            throw new IllegalArgumentException("请选择一个文件");
        }
        return selectedEntry;
    }

    private DirectoryEntry findByPath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return service.root();
        }
        String[] parts = path.replace('\\', '/').split("/");
        DirectoryEntry cursor = service.root();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            DirectoryEntry next = cursor.findChild(part);
            if (next == null || !next.isDirectory()) {
                return null;
            }
            cursor = next;
        }
        return cursor;
    }

    private void appendLog(String message) {
        logArea.append("[" + LocalTime.now().format(TIME_FORMAT) + "] " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void runSafely(RunnableWithIOException action) {
        try {
            action.run();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "操作失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    @FunctionalInterface
    private interface RunnableWithIOException {
        void run() throws IOException;
    }

    private static class EntryTableModel extends AbstractTableModel {
        private final String[] columns = {"名称", "类型", "大小", "盘块数", "盘块号"};
        private List<DirectoryEntry> entries = new ArrayList<>();

        public void setEntries(List<DirectoryEntry> entries) {
            this.entries = entries;
            fireTableDataChanged();
        }

        public DirectoryEntry getEntry(int row) {
            return entries.get(row);
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DirectoryEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.getName();
                case 1 -> entry.isDirectory() ? "目录" : "文件";
                case 2 -> entry.isDirectory() ? "-" : entry.getSize();
                case 3 -> entry.isDirectory() ? "-" : Math.max(0, (int) Math.ceil(entry.getSize() / (double) VirtualDisk.DEFAULT_BLOCK_SIZE));
                case 4 -> entry.getStartBlock() < 0 ? "-" : entry.getStartBlock();
                default -> "";
            };
        }
    }

    private static class FatTableModel extends AbstractTableModel {
        private final String[] columns = {"块号", "FAT值", "状态"};
        private final int[] fat;
        private final List<Integer> selectedBlocks;
        private final DiskPanel diskPanel;

        FatTableModel(int[] fat, List<Integer> selectedBlocks, DiskPanel diskPanel) {
            this.fat = fat;
            this.selectedBlocks = selectedBlocks;
            this.diskPanel = diskPanel;
        }

        @Override
        public int getRowCount() {
            return fat.length;
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return switch (columnIndex) {
                case 0 -> rowIndex;
                case 1 -> fat[rowIndex];
                case 2 -> selectedBlocks.contains(rowIndex) ? "当前选中文件块" : diskPanel.statusFor(rowIndex);
                default -> "";
            };
        }
    }

    public static void showOnEventThread() {
        SwingUtilities.invokeLater(() -> {
            try {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, ex.getMessage(), "启动失败", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
