package piHAUPA;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Hộp chọn file/thư mục đồng bộ với giao diện Desktop. */
final class ModernPathDialog extends JDialog {
    private static final Color INK = new Color(16, 32, 51);
    private static final Color MUTED = new Color(100, 117, 137);
    private static final Color BORDER = new Color(220, 228, 239);
    private static final Color BLUE = new Color(37, 99, 235);
    private static final Color CANVAS = new Color(246, 248, 252);

    private final boolean directoryMode;
    private final JTextField pathField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JLabel message = new JLabel(" ");
    private final JTable entriesTable = new JTable();
    private final List<Path> entries = new ArrayList<>();
    private Path currentDirectory;
    private Path selectedPath;

    static Path choose(JFrame owner, Path start, boolean directoryMode) {
        ModernPathDialog dialog = new ModernPathDialog(owner, start, directoryMode);
        dialog.setVisible(true);
        return dialog.selectedPath;
    }

    private ModernPathDialog(JFrame owner, Path start, boolean directoryMode) {
        super(owner, directoryMode ? "Chọn thư mục xuất" : "Chọn dữ liệu PIHAUPA", true);
        this.directoryMode = directoryMode;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(660, 480));
        setSize(800, 565);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Color.WHITE);
        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);
        add(browser(), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        Path initial = start == null ? Path.of(".") : start.toAbsolutePath().normalize();
        while (initial != null && !Files.isDirectory(initial)) initial = initial.getParent();
        navigate(initial == null ? Path.of(".") : initial);
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(16, 0));
        panel.setBackground(Color.WHITE);
        /* DESIGN PATTERN: Static Factory của Swing (BorderFactory).
         * Các hàm create... tạo border; createCompoundBorder ghép đường kẻ và đệm.
         * Mục đích: dùng chung cách tạo đường viền, không tự viết logic vẽ.
         * Tối ưu hiện có: tạo khi dựng header thay vì tạo lại lúc điều hướng.
         * Đây là sử dụng factory của thư viện, không tự triển khai Factory Method.
         */
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(20, 24, 20, 24)));
        JLabel symbol = new JLabel(directoryMode ? "↗" : "TXT", SwingConstants.CENTER);
        symbol.setOpaque(true);
        symbol.setBackground(new Color(232, 240, 255));
        symbol.setForeground(BLUE);
        symbol.setFont(new Font("Segoe UI", Font.BOLD, directoryMode ? 22 : 13));
        symbol.setPreferredSize(new Dimension(48, 48));
        panel.add(symbol, BorderLayout.WEST);
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 2));
        heading.setOpaque(false);
        heading.add(label(directoryMode ? "Thư mục lưu báo cáo" : "Mở bộ dữ liệu", 20, Font.BOLD, INK));
        heading.add(label(directoryMode ? "Chọn nơi lưu JSON, CSV và các bản tách batch"
                : "Chọn tệp .txt chứa external utility và các batch", 12, Font.PLAIN, MUTED));
        panel.add(heading, BorderLayout.CENTER);
        return panel;
    }

    private JPanel browser() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 24, 10, 24));

        JPanel navigation = new JPanel(new BorderLayout(10, 0));
        navigation.setOpaque(false);
        JButton parent = button("↑  Lên một cấp", false);
        /* DESIGN PATTERN: Observer thông qua ActionListener.
         * Nút parent phát sự kiện; lambda nhận sự kiện và gọi navigate thư mục cha.
         * Các ô đường dẫn và nút chọn/hủy bên dưới dùng cùng cơ chế đăng ký sự kiện.
         * Mục đích: tách hành động điều hướng/chọn file khỏi thành phần phát sự kiện.
         * Tối ưu hiện có: mỗi listener đăng ký một lần khi tạo hộp thoại.
         * Lưu ý: navigate còn thực hiện truy cập thư mục; ổ mạng có thể làm EDT chậm.
         * Chưa đo độ trễ điều hướng và chưa chuyển việc duyệt thư mục sang luồng nền.
         */
        parent.addActionListener(event -> {
            if (currentDirectory != null && currentDirectory.getParent() != null)
                navigate(currentDirectory.getParent());
        });
        navigation.add(parent, BorderLayout.WEST);
        styleField(pathField);
        pathField.setToolTipText("Nhập đường dẫn thư mục rồi nhấn Enter");
        pathField.addActionListener(event -> navigateTyped());
        navigation.add(pathField, BorderLayout.CENTER);
        JButton go = button("Mở đường dẫn", false);
        go.addActionListener(event -> navigateTyped());
        navigation.add(go, BorderLayout.EAST);
        panel.add(navigation, BorderLayout.NORTH);

        entriesTable.setRowHeight(40);
        entriesTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        entriesTable.setForeground(INK);
        entriesTable.setBackground(Color.WHITE);
        entriesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        entriesTable.setSelectionBackground(new Color(232, 240, 255));
        entriesTable.setSelectionForeground(INK);
        entriesTable.setShowVerticalLines(false);
        entriesTable.setShowHorizontalLines(false);
        entriesTable.setIntercellSpacing(new Dimension(0, 0));
        entriesTable.setGridColor(new Color(239, 243, 248));
        entriesTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        entriesTable.getTableHeader().setBackground(CANVAS);
        entriesTable.getTableHeader().setForeground(MUTED);
        entriesTable.getTableHeader().setReorderingAllowed(false);
        entriesTable.getTableHeader().setPreferredSize(new Dimension(0, 36));
        entriesTable.getTableHeader().setDefaultRenderer((table, value, selected, focused, row, column) -> {
            JLabel heading = label(String.valueOf(value), 11, Font.BOLD, MUTED);
            heading.setOpaque(true);
            heading.setBackground(CANVAS);
            heading.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                    BorderFactory.createEmptyBorder(0, 12, 0, 8)));
            return heading;
        });
        entriesTable.getSelectionModel().addListSelectionListener(event -> {
            int row = entriesTable.getSelectedRow();
            if (!event.getValueIsAdjusting() && row >= 0 && row < entries.size()) {
                nameField.setText(entries.get(row).getFileName().toString());
            }
        });
        entriesTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2) return;
                int row = entriesTable.rowAtPoint(event.getPoint());
                if (row < 0 || row >= entries.size()) return;
                Path path = entries.get(row);
                if (Files.isDirectory(path)) navigate(path);
                else if (!directoryMode) accept(path);
            }
        });
        JScrollPane scroll = new JScrollPane(entriesTable);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        scroll.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel footer() {
        JPanel footer = new JPanel(new BorderLayout(0, 12));
        footer.setBackground(CANVAS);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(14, 24, 16, 24)));
        JPanel selection = new JPanel(new BorderLayout(12, 0));
        selection.setOpaque(false);
        selection.add(label(directoryMode ? "Thư mục:" : "Tên tệp:", 12, Font.BOLD, MUTED), BorderLayout.WEST);
        styleField(nameField);
        nameField.setToolTipText(directoryMode ? "Để trống để chọn thư mục đang mở"
                : "Nhập tên tệp hoặc đường dẫn đầy đủ");
        nameField.addActionListener(event -> acceptTyped());
        selection.add(nameField, BorderLayout.CENTER);
        footer.add(selection, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        message.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        message.setForeground(MUTED);
        bottom.add(message, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancel = button("Huỷ", false);
        cancel.addActionListener(event -> dispose());
        JButton select = button(directoryMode ? "Chọn thư mục" : "Mở dữ liệu", true);
        select.addActionListener(event -> acceptTyped());
        actions.add(cancel);
        actions.add(select);
        bottom.add(actions, BorderLayout.EAST);
        footer.add(bottom, BorderLayout.SOUTH);
        return footer;
    }

    private void navigate(Path destination) {
        try {
            Path path = destination.toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && !directoryMode) {
                accept(path);
                return;
            }
            if (!Files.isDirectory(path)) throw new IOException("Thư mục không tồn tại: " + path);
            List<Path> found;
            try (Stream<Path> stream = Files.list(path)) {
                found = stream.filter(entry -> Files.isDirectory(entry)
                                || (!directoryMode && entry.getFileName().toString()
                                .toLowerCase(Locale.ROOT).endsWith(".txt")))
                        .sorted(Comparator.comparing((Path entry) -> !Files.isDirectory(entry))
                                .thenComparing(entry -> entry.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .toList();
            }
            currentDirectory = path;
            pathField.setText(path.toString());
            nameField.setText("");
            entries.clear();
            entries.addAll(found);
            DefaultTableModel model = new DefaultTableModel(new Object[]{"Tên", "Loại", "Dung lượng"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            for (Path entry : found) {
                boolean folder = Files.isDirectory(entry);
                model.addRow(new Object[]{(folder ? "▸  " : "TXT  ") + entry.getFileName(),
                        folder ? "Thư mục" : "Tệp dữ liệu", folder ? "—" : fileSize(entry)});
            }
            entriesTable.setModel(model);
            entriesTable.getColumnModel().getColumn(0).setPreferredWidth(470);
            entriesTable.getColumnModel().getColumn(1).setPreferredWidth(120);
            entriesTable.getColumnModel().getColumn(2).setPreferredWidth(100);
            DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
                @Override public java.awt.Component getTableCellRendererComponent(
                        JTable table, Object value, boolean selected, boolean focused, int row, int column) {
                    super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                    if (!selected) {
                        setBackground(row % 2 == 0 ? Color.WHITE : CANVAS);
                        setForeground(column == 0 ? INK : MUTED);
                    }
                    setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                    return this;
                }
            };
            for (int i = 0; i < model.getColumnCount(); i++)
                entriesTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
            feedback(found.size() + " mục trong " + path.getFileName(), false);
        } catch (Exception error) {
            feedback(error.getMessage(), true);
        }
    }

    private void navigateTyped() {
        try {
            navigate(Path.of(pathField.getText().trim()));
        } catch (RuntimeException error) {
            feedback("Đường dẫn không hợp lệ: " + error.getMessage(), true);
        }
    }

    private void acceptTyped() {
        try {
            String text = nameField.getText().trim();
            Path target = text.isEmpty() ? currentDirectory
                    : Path.of(text).isAbsolute() ? Path.of(text) : currentDirectory.resolve(text);
            if (target != null) accept(target);
        } catch (RuntimeException error) {
            feedback("Tên hoặc đường dẫn không hợp lệ: " + error.getMessage(), true);
        }
    }

    private void accept(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!directoryMode && Files.isDirectory(normalized)) {
            navigate(normalized);
            return;
        }
        if (directoryMode ? !Files.isDirectory(normalized) : !Files.isRegularFile(normalized)) {
            feedback(directoryMode ? "Hãy chọn một thư mục hiện có." : "Hãy chọn một tệp hiện có.", true);
            return;
        }
        if (!directoryMode && !normalized.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")) {
            feedback("Dữ liệu đầu vào phải là tệp .txt.", true);
            return;
        }
        selectedPath = normalized;
        dispose();
    }

    private void feedback(String text, boolean error) {
        message.setText(text == null ? " " : text);
        message.setForeground(error ? new Color(190, 55, 65) : MUTED);
    }

    private static String fileSize(Path path) {
        try {
            long size = Files.size(path);
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format(Locale.US, "%.1f KB", size / 1024.0);
            return String.format(Locale.US, "%.1f MB", size / 1048576.0);
        } catch (IOException error) {
            return "—";
        }
    }

    private static JLabel label(String text, int size, int weight, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", weight, size));
        label.setForeground(color);
        return label;
    }

    private static void styleField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setForeground(INK);
        field.setBackground(Color.WHITE);
        field.setPreferredSize(new Dimension(100, 38));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
    }

    private static JButton button(String text, boolean primary) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = primary ? BLUE : Color.WHITE;
                if (getModel().isPressed()) fill = primary ? new Color(24, 73, 187) : CANVAS;
                else if (getModel().isRollover()) fill = primary ? new Color(29, 83, 208) : CANVAS;
                g.setColor(fill);
                g.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 11, 11);
                g.setColor(primary ? fill : BORDER);
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 11, 11);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(primary ? Color.WHITE : INK);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setRolloverEnabled(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        button.setPreferredSize(new Dimension(primary ? 130 : 115, 38));
        return button;
    }

    private static final class SlimScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(188, 201, 219);
            trackColor = CANVAS;
        }
        @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }
        private JButton zeroButton() {
            JButton button = new JButton();
            Dimension zero = new Dimension(0, 0);
            button.setPreferredSize(zero);
            button.setMinimumSize(zero);
            button.setMaximumSize(zero);
            return button;
        }
        @Override protected void paintTrack(Graphics graphics, javax.swing.JComponent component, Rectangle bounds) {
            graphics.setColor(CANVAS);
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        @Override protected void paintThumb(Graphics graphics, javax.swing.JComponent component, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(thumbColor);
            g.fillRoundRect(bounds.x + 2, bounds.y + 2, Math.max(4, bounds.width - 4),
                    Math.max(4, bounds.height - 4), 8, 8);
            g.dispose();
        }
    }
}
