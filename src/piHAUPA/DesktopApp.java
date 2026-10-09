package piHAUPA;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.LookAndFeel;
import javax.swing.Scrollable;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Light desktop dashboard dùng chung lõi PIHAUPA với Web UI. */
public final class DesktopApp extends JFrame {
    private static final Color CANVAS = new Color(243, 246, 251);
    private static final Color SURFACE = Color.WHITE;
    private static final Color SURFACE_SOFT = new Color(248, 250, 252);
    private static final Color BORDER = new Color(220, 228, 239);
    private static final Color INK = new Color(16, 32, 51);
    private static final Color MUTED = new Color(108, 126, 146);
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Color PRIMARY_SOFT = new Color(232, 240, 255);
    private static final Color TEAL = new Color(7, 155, 131);
    private static final Color TEAL_SOFT = new Color(229, 248, 244);
    private static final Color AMBER = new Color(184, 106, 5);
    private static final Color AMBER_SOFT = new Color(255, 245, 223);

    private final JTextField inputField = field();
    private final JTextField upperField = field("0.23");
    private final JTextField lowerField = field("0.10");
    private final JTextField outputField = field();
    private final JLabel inputNameLabel = label("Tables 2–3 · paper-example.txt", 13, Font.BOLD, INK);
    private final JLabel inputMetaLabel = label("TXT · dữ liệu định lượng theo batch", 11, Font.PLAIN, MUTED);
    private final JLabel reportSourceLabel = label("Tables 2–3 · paper-example.txt", 13, Font.BOLD, INK);
    private final JLabel reportMetaLabel = label("Sẵn sàng phân tích", 11, Font.PLAIN, MUTED);
    private final JLabel finalThresholdLabel = label("", 11, Font.PLAIN, MUTED);
    private final JLabel statusLabel = new JLabel("●  Đã nạp dữ liệu bài báo");
    private final JLabel loadingSourceLabel = label("", 13, Font.PLAIN, MUTED);
    private final JButton runButton = primaryButton("Chạy PIHAUPA   →");
    private final JButton exportButton = secondaryButton("↓  Xuất TXT và CSV kết quả");
    private RunResult lastRun;
    private String selectedArchiveDataset;
    private final CardLayout resultCards = new CardLayout();
    private final JPanel resultHost = new JPanel(resultCards);
    private final JPanel overviewTab = new JPanel(new BorderLayout(12, 12));
    private final JPanel batchTab = new JPanel(new BorderLayout());
    private final JPanel explanationTab = new JPanel(new BorderLayout());
    private final JTable comparisonTable = table();
    private final JTable patternTable = table();
    private final JTable utilityTable = table();
    private final JTable transactionTable = table();
    private final JLabel transactionPreviewLabel = label("", 11, Font.PLAIN, MUTED);
    private final JLabel transactionCountLabel = label("", 10, Font.PLAIN, MUTED);
    private final JButton moreTransactionsButton = secondaryButton("Xem thêm 100 giao dịch");
    private InputData displayedInput;
    private int visibleTransactionCount;
    private JScrollPane reportScroll;

    private DesktopApp() {
        super("PIHAUPA · Algorithm Laboratory");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
        setPreferredSize(new Dimension(1420, 860));
        getContentPane().setBackground(CANVAS);
        setLayout(new BorderLayout());
        add(createHeader(), BorderLayout.NORTH);
        add(createWorkspace(), BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SURFACE);
        /* DESIGN PATTERN: Static Factory có sẵn trong Swing.
         * Vai trò: BorderFactory tạo đối tượng Border; header là thành phần sử dụng.
         * createMatteBorder tạo đường kẻ, createEmptyBorder tạo khoảng đệm;
         * createCompoundBorder ghép hai border để không phải tự vẽ từng phần.
         * Mục đích: cách dựng đường viền thống nhất, dễ đổi màu và khoảng cách.
         * Tối ưu hiện có: dựng border lúc tạo giao diện, không dựng lại mỗi lần nhấn nút.
         * Đây là hàm tạo tĩnh của thư viện, không phải tự cài GoF Factory Method.
         */
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(14, 26, 14, 26)));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brand.setOpaque(false);
        JLabel mark = new JLabel("P", SwingConstants.CENTER);
        mark.setOpaque(true);
        mark.setBackground(PRIMARY);
        mark.setForeground(Color.WHITE);
        mark.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        mark.setPreferredSize(new Dimension(42, 42));
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        JLabel title = label("PIHAUPA", 17, Font.BOLD, INK);
        JLabel subtitle = label("Algorithm Laboratory", 12, Font.PLAIN, MUTED);
        names.add(title);
        names.add(Box.createVerticalStrut(3));
        names.add(subtitle);
        brand.add(mark);
        brand.add(names);
        JLabel paper = label("Kim et al. · Journal of Big Data · 2026", 12, Font.PLAIN, MUTED);
        paper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        header.add(brand, BorderLayout.WEST);
        header.add(paper, BorderLayout.EAST);
        return header;
    }

    private JPanel createWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(22, 0));
        workspace.setBackground(CANVAS);
        workspace.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        JPanel controls = createControls();
        controls.setPreferredSize(new Dimension(355, 790));
        JScrollPane controlsScroll = new JScrollPane(controls,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        controlsScroll.setBorder(null);
        controlsScroll.setPreferredSize(new Dimension(372, 680));
        controlsScroll.getVerticalScrollBar().setUnitIncrement(18);
        workspace.add(controlsScroll, BorderLayout.WEST);
        resultHost.setOpaque(false);
        resultHost.add(createEmptyState(), "empty");
        resultHost.add(createLoadingState(), "loading");
        resultHost.add(createDashboard(), "dashboard");
        workspace.add(resultHost, BorderLayout.CENTER);
        resultCards.show(resultHost, "empty");
        return workspace;
    }

    private JPanel createControls() {
        RoundedPanel card = new RoundedPanel(20, SURFACE);
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, 0, 12, 0);
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        heading.add(label("SETUP", 11, Font.BOLD, PRIMARY), BorderLayout.WEST);
        JPanel headingText = vertical(label("Cấu hình phân tích", 22, Font.BOLD, INK),
                label("Dữ liệu định lượng theo batch", 13, Font.PLAIN, MUTED));
        heading.add(headingText, BorderLayout.CENTER);
        card.add(heading, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 8, 0);
        card.add(stepLabel("1", "NGUỒN DỮ LIỆU"), c);
        c.gridy++;
        c.insets = new Insets(2, 0, 10, 0);
        RoundedPanel upload = new RoundedPanel(16, SURFACE_SOFT);
        upload.setLayout(new BoxLayout(upload, BoxLayout.Y_AXIS));
        upload.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createDashedBorder(new Color(170, 192, 222), 1.2f, 5, 4, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        JLabel arrow = label("↑", 26, Font.PLAIN, PRIMARY);
        arrow.setAlignmentX(Component.CENTER_ALIGNMENT);
        inputNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        inputMetaLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (JLabel text : List.of(inputNameLabel, inputMetaLabel)) {
            text.setHorizontalAlignment(SwingConstants.CENTER);
            text.setPreferredSize(new Dimension(270, 20));
            text.setMinimumSize(new Dimension(0, 20));
            text.setMaximumSize(new Dimension(270, 20));
        }
        upload.add(arrow);
        upload.add(Box.createVerticalStrut(6));
        upload.add(inputNameLabel);
        upload.add(Box.createVerticalStrut(5));
        upload.add(inputMetaLabel);
        MouseAdapter openInput = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { chooseFile(); }
        };
        for (JComponent target : List.of(upload, arrow, inputNameLabel, inputMetaLabel)) {
            target.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            target.addMouseListener(openInput);
        }
        upload.setTransferHandler(new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (files.isEmpty()) return false;
                    selectInput(files.get(0).toPath());
                    return true;
                } catch (Exception error) {
                    showError(error);
                    return false;
                }
            }
        });
        card.add(upload, c);

        c.gridy++;
        c.gridwidth = 1;
        c.weightx = .5;
        JButton chooseInput = secondaryButton("Chọn tệp");
        card.add(chooseInput, c);
        c.gridx = 1;
        c.insets = new Insets(0, 8, 8, 0);
        JButton sampleButton = secondaryButton("Dữ liệu bài báo");
        card.add(sampleButton, c);

        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        c.weightx = 1;
        c.insets = new Insets(8, 0, 5, 0);
        card.add(stepLabel("2", "NGƯỠNG PHÂN LOẠI"), c);
        c.gridy++;
        c.gridwidth = 1;
        c.weightx = .5;
        c.insets = new Insets(10, 0, 6, 6);
        card.add(fieldLabel("Upper threshold · Su"), c);
        c.gridx = 1;
        c.insets = new Insets(10, 6, 6, 0);
        card.add(fieldLabel("Lower threshold · Sl"), c);
        c.gridx = 0;
        c.gridy++;
        c.insets = new Insets(0, 0, 8, 6);
        card.add(upperField, c);
        c.gridx = 1;
        c.insets = new Insets(0, 6, 8, 0);
        card.add(lowerField, c);

        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.gridy++;
        c.insets = new Insets(8, 0, 5, 0);
        card.add(stepLabel("3", "ĐIỀU KIỆN RE-SCAN"), c);
        c.gridy++;
        c.insets = new Insets(8, 0, 10, 0);
        RoundedPanel formula = new RoundedPanel(12, new Color(239, 246, 255));
        formula.setLayout(new BoxLayout(formula, BoxLayout.Y_AXIS));
        formula.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, PRIMARY),
                BorderFactory.createEmptyBorder(12, 12, 12, 10)));
        formula.add(label("Tight re-scan · Eq.(12)", 12, Font.BOLD, new Color(23, 74, 198)));
        formula.add(Box.createVerticalStrut(7));
        formula.add(label("MU(ID) − Su×TU(ID) ≥ (Su−Sl)×TU(OD)", 11, Font.PLAIN, new Color(52, 75, 103)));
        card.add(formula, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 12, 0);
        card.add(runButton, c);
        c.gridy++;
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);
        card.add(statusLabel, c);
        c.gridy++;
        c.insets = new Insets(8, 0, 0, 0);
        JButton chooseOutput = tertiaryButton("＋  Thư mục xuất báo cáo (tuỳ chọn)");
        card.add(chooseOutput, c);
        c.gridy++;
        c.insets = new Insets(8, 0, 0, 0);
        exportButton.setEnabled(false);
        exportButton.setToolTipText("Xuất BAO_CAO.txt, bao-cao.csv và các bảng CSV từ kết quả đang hiển thị");
        card.add(exportButton, c);
        c.gridy++;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 0);
        card.add(Box.createVerticalGlue(), c);

        /* DESIGN PATTERN: Observer thông qua cơ chế ActionListener của Swing.
         * Đối tượng phát sự kiện: các JButton; đối tượng nhận: listener viết bằng lambda.
         * addActionListener đăng ký listener; khi nhấn nút, Swing gọi lambda trên EDT
         * (luồng xử lý sự kiện giao diện), rồi lambda gọi hành động tương ứng bên dưới.
         * Mục đích: JButton không cần biết thuật toán khai phá hay cách xuất báo cáo.
         * Tối ưu hiện có: đăng ký một lần khi dựng giao diện để tránh xử lý sự kiện trùng.
         * Hàm runAnalysis/exportLastRun chuyển phần xử lý nặng sang SwingWorker;
         * listener chỉ khởi động công việc, giúp EDT tiếp tục phản hồi giao diện.
         * Lưu ý: hộp thoại modal vẫn có thể chờ người dùng; Observer không bảo đảm mọi
         * hành động đều nhanh, và tự nó không làm thuật toán khai phá chạy nhanh hơn.
         */
        chooseInput.addActionListener(event -> chooseFile());
        sampleButton.addActionListener(event -> usePaperSample());
        chooseOutput.addActionListener(event -> chooseOutputDirectory());
        exportButton.addActionListener(event -> exportLastRun());
        runButton.addActionListener(event -> runAnalysis());
        return card;
    }

    private JPanel createEmptyState() {
        RoundedPanel empty = new RoundedPanel(20, SURFACE);
        empty.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        JLabel au = new JLabel("AU", SwingConstants.CENTER);
        au.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        au.setForeground(PRIMARY);
        au.setOpaque(true);
        au.setBackground(PRIMARY_SOFT);
        au.setPreferredSize(new Dimension(130, 130));
        au.setBorder(BorderFactory.createLineBorder(new Color(198, 216, 246), 2));
        empty.add(au, c);
        c.gridy++;
        c.insets = new Insets(24, 0, 8, 0);
        empty.add(label("Kết quả sẽ xuất hiện tại đây", 24, Font.BOLD, INK), c);
        c.gridy++;
        c.insets = new Insets(0, 40, 0, 40);
        JLabel message = label("Chọn file hoặc dùng bộ dữ liệu bài báo, sau đó chạy để xem mẫu, re-scan và tài nguyên.",
                14, Font.PLAIN, MUTED);
        empty.add(message, c);
        return empty;
    }

    private JPanel createLoadingState() {
        RoundedPanel loading = new RoundedPanel(20, SURFACE);
        loading.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        JLabel mark = label("…", 34, Font.BOLD, PRIMARY);
        mark.setHorizontalAlignment(SwingConstants.CENTER);
        mark.setOpaque(true);
        mark.setBackground(PRIMARY_SOFT);
        mark.setPreferredSize(new Dimension(100, 100));
        loading.add(mark, c);
        c.gridy++;
        c.insets = new Insets(22, 0, 8, 0);
        loading.add(label("Đang phân tích dữ liệu", 23, Font.BOLD, INK), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 8, 0);
        loading.add(loadingSourceLabel, c);
        c.gridy++;
        loading.add(label("Đang tính Bước 1–5 của PIHAUPA Tight.",
                13, Font.PLAIN, MUTED), c);
        return loading;
    }

    private JPanel createDashboard() {
        overviewTab.setOpaque(false);
        batchTab.setOpaque(false);
        explanationTab.setOpaque(false);
        RoundedPanel dashboard = new RoundedPanel(20, SURFACE);
        dashboard.setLayout(new BorderLayout());
        dashboard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel report = new VerticalReportPanel();
        report.setBackground(SURFACE);
        report.setBorder(BorderFactory.createEmptyBorder(10, 12, 20, 12));
        report.setLayout(new BoxLayout(report, BoxLayout.Y_AXIS));
        report.add(reportHero());
        report.add(Box.createVerticalStrut(18));
        report.add(section("01", "Kết quả chính", "Nhìn nhanh dữ liệu, mẫu và chi phí xử lý", overviewTab));
        report.add(Box.createVerticalStrut(18));
        report.add(section("02", "Dữ liệu đã đọc", "External utility và giao dịch được dùng để tính toán", inputDataView()));
        report.add(Box.createVerticalStrut(18));
        report.add(section("03", "Cách đọc kết quả", "Công thức và ranh giới phân loại trong bài báo", explanationTab));
        report.add(Box.createVerticalStrut(18));
        report.add(section("04", "Diễn tiến theo batch", "Đọc lần lượt từ DB0 đến batch mới nhất", batchTab));
        report.add(Box.createVerticalStrut(18));
        report.add(section("05", "Tập mẫu cuối cùng", "HAUP đầy đủ; PRE-LARGE là các ứng viên đang được quản lý", patternView()));
        reportScroll = scroll(report);
        dashboard.add(reportScroll, BorderLayout.CENTER);
        return dashboard;
    }

    private JPanel reportHero() {
        RoundedPanel hero = new RoundedPanel(18, new Color(246, 249, 255));
        hero.setLayout(new BorderLayout(18, 0));
        hero.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        JPanel title = vertical(label("Báo cáo phân tích PIHAUPA", 24, Font.BOLD, INK),
                label("Tight re-scan · High Average Utility Pattern", 12, Font.PLAIN, MUTED));
        hero.add(title, BorderLayout.WEST);
        JPanel source = vertical(reportSourceLabel, reportMetaLabel);
        source.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(207, 220, 240)),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        hero.add(source, BorderLayout.EAST);
        hero.setAlignmentX(Component.LEFT_ALIGNMENT);
        return hero;
    }

    private JPanel stepLabel(String number, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        JLabel badge = label(number, 10, Font.BOLD, Color.WHITE);
        badge.setOpaque(true);
        badge.setBackground(PRIMARY);
        badge.setHorizontalAlignment(SwingConstants.CENTER);
        badge.setPreferredSize(new Dimension(22, 22));
        row.add(badge);
        row.add(label(text, 10, Font.BOLD, new Color(76, 94, 116)));
        return row;
    }

    private JPanel section(String number, String title, String subtitle, Component content) {
        JPanel section = new JPanel(new BorderLayout(0, 12));
        section.setBackground(SURFACE);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(22, 18, 20, 18)));
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        JLabel index = label(number, 11, Font.BOLD, PRIMARY);
        index.setOpaque(true);
        index.setBackground(PRIMARY_SOFT);
        index.setHorizontalAlignment(SwingConstants.CENTER);
        index.setPreferredSize(new Dimension(38, 38));
        heading.add(index, BorderLayout.WEST);
        heading.add(vertical(label(title, 19, Font.BOLD, INK),
                label(subtitle, 11, Font.PLAIN, MUTED)), BorderLayout.CENTER);
        section.add(heading, BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);
        return section;
    }

    private void populateDashboard(AnalysisReport tight, InputData input) {
        int transactionCount = input.batches().stream().mapToInt(batch -> batch.transactions().size()).sum();
        reportSourceLabel.setText(inputNameLabel.getText());
        reportMetaLabel.setText(input.externalUtilities().size() + " item  ·  " + input.batches().size()
                + " batch  ·  " + transactionCount + " giao dịch");
        populateInputData(input);
        populateExplanation(tight);
        overviewTab.removeAll();
        JPanel metrics = new JPanel(new GridLayout(2, 3, 10, 10));
        metrics.setOpaque(false);
        AnalysisReport.BatchView last = tight.batches().get(tight.batches().size() - 1);
        metrics.add(metric("GIAO DỊCH", String.valueOf(tight.transactionCount()), tight.batchCount() + " batch"));
        metrics.add(metric("HAUP CUỐI", String.valueOf(last.largePatterns().size()),
                last.preLargePatterns().size() + " pre-large"));
        metrics.add(metric("TU CUỐI", f(last.totalTransactionUtility()), "sau " + tight.batchCount() + " batch"));
        metrics.add(metric("RE-SCAN", String.valueOf(tight.stats().rescanCount()),
                tight.stats().patternsVisited() + " mẫu duyệt"));
        metrics.add(metric("THỜI GIAN", f(tight.elapsedNanos() / 1_000_000.0) + " ms", "không gồm đọc tệp"));
        metrics.add(metric("HEAP PEAK", f(tight.peakMemoryBytes() / 1048576.0) + " MB", "JVM heap"));
        overviewTab.add(metrics, BorderLayout.NORTH);
        overviewTab.add(overviewBody(tight, last), BorderLayout.CENTER);

        populateBatches(tight);
        setPatternRows(last);
        finalThresholdLabel.setText("Lower " + f(last.minUtilLower()) + "  ≤  AU  <  Upper "
                + f(last.minUtilUpper()) + " là PRE-LARGE; AU ≥ Upper là LARGE.");
        overviewTab.revalidate();
        overviewTab.repaint();
        resultCards.show(resultHost, "dashboard");
        SwingUtilities.invokeLater(() -> reportScroll.getVerticalScrollBar().setValue(0));
    }

    private void setComparisonRows(AnalysisReport oldReport, AnalysisReport newReport, boolean same) {
        DefaultTableModel model = model("Chỉ số", "Original Eq.(5)", "Tight Eq.(6)", "Thay đổi");
        addRow(model, "Số lần re-scan", oldReport.stats().rescanCount(), newReport.stats().rescanCount(),
                signed(newReport.stats().rescanCount() - oldReport.stats().rescanCount()));
        double oldMs = oldReport.elapsedNanos() / 1_000_000.0;
        double newMs = newReport.elapsedNanos() / 1_000_000.0;
        addRow(model, "Thời gian trung vị", f(oldMs) + " ms", f(newMs) + " ms", percent(oldMs, newMs));
        double oldHeap = oldReport.peakMemoryBytes() / 1048576.0;
        double newHeap = newReport.peakMemoryBytes() / 1048576.0;
        addRow(model, "Heap peak quan sát", f(oldHeap) + " MB", f(newHeap) + " MB", percent(oldHeap, newHeap));
        addRow(model, "Pattern đã duyệt", oldReport.stats().patternsVisited(), newReport.stats().patternsVisited(),
                signed(newReport.stats().patternsVisited() - oldReport.stats().patternsVisited()));
        addRow(model, "Node đã kết hợp", oldReport.stats().combinedNodes(), newReport.stats().combinedNodes(),
                signed(newReport.stats().combinedNodes() - oldReport.stats().combinedNodes()));
        addRow(model, "HAUP cuối", same ? "Trùng khớp" : "Khác", same ? "Trùng khớp ✓" : "Khác ⚠", "—");
        comparisonTable.setModel(model);
        styleTable(comparisonTable, 2);
    }

    private void populateBatches(AnalysisReport report) {
        JPanel batches = new JPanel();
        batches.setBackground(SURFACE);
        batches.setLayout(new BoxLayout(batches, BoxLayout.Y_AXIS));
        for (AnalysisReport.BatchView batch : report.batches()) {
            JPanel heading = new JPanel(new BorderLayout());
            heading.setBackground(SURFACE_SOFT);
            heading.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            heading.add(label(batch.name(), 14, Font.BOLD, INK), BorderLayout.WEST);
            String state = batch.index() == 0 ? "KHỞI TẠO" : batch.rescanTriggered() ? "RE-SCAN" : "CẬP NHẬT CÂY";
            heading.add(label(state, 11, Font.BOLD,
                    batch.index() == 0 ? PRIMARY : batch.rescanTriggered() ? AMBER : TEAL), BorderLayout.EAST);
            batches.add(heading);
            batches.add(batchView(report, batch));
            batches.add(Box.createVerticalStrut(14));
        }
        batchTab.removeAll();
        batchTab.add(batches, BorderLayout.CENTER);
        batchTab.revalidate();
    }

    private void setPatternRows(AnalysisReport.BatchView last) {
        DefaultTableModel model = model("Mẫu", "AU", "Sum utility", "|P|", "Phân loại", "So với ngưỡng");
        for (PatternResult pattern : concat(last.largePatterns(), last.preLargePatterns())) {
            String relation = pattern.type() == PatternType.LARGE
                    ? f(pattern.averageUtility()) + " ≥ " + f(last.minUtilUpper())
                    : f(last.minUtilLower()) + " ≤ " + f(pattern.averageUtility())
                    + " < " + f(last.minUtilUpper());
            model.addRow(new Object[]{pattern.displayName(), f(pattern.averageUtility()),
                    f(pattern.sumUtility()), pattern.length(),
                    pattern.type() == PatternType.LARGE ? "LARGE" : "PRE-LARGE", relation});
        }
        patternTable.setModel(model);
        styleTable(patternTable, 4);
        patternTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        patternTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        patternTable.getColumnModel().getColumn(2).setPreferredWidth(105);
        patternTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        patternTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        patternTable.getColumnModel().getColumn(5).setPreferredWidth(230);
    }

    private JPanel overviewBody(AnalysisReport tight, AnalysisReport.BatchView last) {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        RoundedPanel verdict = new RoundedPanel(14, TEAL_SOFT);
        verdict.setLayout(new BorderLayout(14, 0));
        verdict.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        JLabel icon = label("✓", 24, Font.BOLD, TEAL);
        verdict.add(icon, BorderLayout.WEST);
        String conclusion = last.largePatterns().size() + " HAUP cuối; "
                + tight.stats().rescanCount() + " re-scan; "
                + tight.stats().patternsVisited() + " mẫu đã duyệt.";
        verdict.add(vertical(
                label("Kết quả PIHAUPA Tight", 16, Font.BOLD, INK),
                label(conclusion, 12, Font.PLAIN, MUTED)), BorderLayout.CENTER);
        body.add(verdict);
        body.add(Box.createVerticalStrut(12));

        RoundedPanel route = new RoundedPanel(12, SURFACE_SOFT);
        route.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        route.setLayout(new BorderLayout());
        route.add(label("LUỒNG PHÂN TÍCH", 10, Font.BOLD, PRIMARY), BorderLayout.NORTH);
        route.add(label("A Đầu vào → B Ngưỡng → C Khai phá → D Re-scan → E HAUP",
                13, Font.BOLD, INK), BorderLayout.CENTER);
        body.add(route);
        return body;
    }

    private JPanel inputDataView() {
        JPanel view = new JPanel(new BorderLayout(12, 0));
        view.setBackground(SURFACE);

        RoundedPanel utilities = new RoundedPanel(14, SURFACE_SOFT);
        utilities.setLayout(new BorderLayout(0, 10));
        utilities.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        utilities.add(vertical(label("EXTERNAL UTILITY", 11, Font.BOLD, PRIMARY),
                label("Giá trị lợi ích của từng item", 10, Font.PLAIN, MUTED)), BorderLayout.NORTH);
        utilities.add(tableBody(utilityTable), BorderLayout.CENTER);
        utilities.setPreferredSize(new Dimension(190, 200));

        RoundedPanel transactions = new RoundedPanel(14, SURFACE_SOFT);
        transactions.setLayout(new BorderLayout(0, 10));
        transactions.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        transactions.add(vertical(label("GIAO DỊCH THEO BATCH", 11, Font.BOLD, PRIMARY),
                transactionCountLabel), BorderLayout.NORTH);
        JPanel tableAndPaging = new JPanel(new BorderLayout(0, 10));
        tableAndPaging.setOpaque(false);
        tableAndPaging.add(tableBody(transactionTable), BorderLayout.CENTER);
        JPanel paging = new JPanel(new BorderLayout());
        paging.setOpaque(false);
        paging.add(transactionPreviewLabel, BorderLayout.WEST);
        moreTransactionsButton.setPreferredSize(new Dimension(190, 34));
        moreTransactionsButton.addActionListener(event -> {
            visibleTransactionCount += 100;
            renderTransactions();
        });
        paging.add(moreTransactionsButton, BorderLayout.EAST);
        tableAndPaging.add(paging, BorderLayout.SOUTH);
        transactions.add(tableAndPaging, BorderLayout.CENTER);

        view.add(utilities, BorderLayout.WEST);
        view.add(transactions, BorderLayout.CENTER);
        return view;
    }

    private void populateInputData(InputData input) {
        displayedInput = input;
        visibleTransactionCount = 25;
        DefaultTableModel utilities = model("Item", "EU");
        input.externalUtilities().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> utilities.addRow(new Object[]{entry.getKey(), f(entry.getValue())}));
        utilityTable.setModel(utilities);
        styleTable(utilityTable, 0);

        renderTransactions();
    }

    private void renderTransactions() {
        if (displayedInput == null) return;
        DefaultTableModel transactions = model("Batch", "TID", "Items : internal utility", "TU", "MU");
        int total = displayedInput.batches().stream().mapToInt(batch -> batch.transactions().size()).sum();
        int shown = 0;
        for (Batch batch : displayedInput.batches()) {
            for (Transaction transaction : batch.transactions()) {
                if (shown >= visibleTransactionCount) break;
                StringBuilder items = new StringBuilder();
                transaction.internalUtilities().forEach((item, quantity) -> {
                    if (!items.isEmpty()) items.append("   ·   ");
                    items.append(item).append(':').append(f(quantity));
                });
                transactions.addRow(new Object[]{batch.name(), transaction.tid(), items,
                        f(transaction.transactionUtility(displayedInput.externalUtilities())),
                        f(transaction.maximumUtility(displayedInput.externalUtilities()))});
                shown++;
            }
        }
        transactionTable.setModel(transactions);
        styleTable(transactionTable, 0);
        transactionTable.getColumnModel().getColumn(0).setPreferredWidth(65);
        transactionTable.getColumnModel().getColumn(1).setPreferredWidth(65);
        transactionTable.getColumnModel().getColumn(2).setPreferredWidth(430);
        transactionTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        transactionTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        transactionPreviewLabel.setText("Đang xem " + shown + " / " + total + " giao dịch");
        transactionCountLabel.setText("Hiển thị " + shown + "/" + total
                + " dòng · toàn bộ dữ liệu đã được tính toán");
        moreTransactionsButton.setVisible(shown < total);
    }

    private void populateExplanation(AnalysisReport report) {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel formulas = new JPanel(new GridLayout(1, 2, 12, 0));
        formulas.setOpaque(false);
        formulas.add(explanationCard("01  TÍNH AVERAGE UTILITY",
                "U(P,T) = tổng IU(i,T) × EU(i)",
                "AU(P) = tổng U(P,T) / |P| trên các giao dịch chứa P.", PRIMARY, PRIMARY_SOFT));
        formulas.add(explanationCard("02  PHÂN LOẠI MẪU",
                "Upper = S_u × TU(DB)   ·   Lower = S_l × TU(DB)",
                "AU ≥ Upper: LARGE / HAUP  ·  Lower ≤ AU < Upper: PRE-LARGE  ·  còn lại: SMALL.", TEAL, TEAL_SOFT));
        content.add(formulas);
        content.add(Box.createVerticalStrut(12));

        RoundedPanel rescan = new RoundedPanel(14, SURFACE_SOFT);
        rescan.setBorder(BorderFactory.createEmptyBorder(15, 17, 15, 17));
        rescan.setLayout(new BorderLayout(0, 7));
        rescan.add(label("03  QUYẾT ĐỊNH RE-SCAN KHI CÓ BATCH MỚI", 11, Font.BOLD, AMBER), BorderLayout.NORTH);
        rescan.add(vertical(
                label("Eq. (12): MU(ID) − S_u × TU(ID) ≥ (S_u − S_l) × TU(OD)", 14, Font.BOLD, INK),
                label("OD: original database ở lần re-scan gần nhất. ID: incremental database tích luỹ sau đó.",
                        11, Font.PLAIN, MUTED),
                label("Đúng: re-scan OD ∪ ID, dựng lại GL/PT và PatternExpansion. Sai: scan batch mới, cập nhật PT hiện có.",
                        11, Font.PLAIN, MUTED)), BorderLayout.CENTER);
        content.add(rescan);
        content.add(Box.createVerticalStrut(13));

        DefaultTableModel limits = model("Batch", "TU tích luỹ", "Upper = Su × TU", "Lower = Sl × TU", "LARGE cuối batch");
        for (AnalysisReport.BatchView batch : report.batches()) {
            limits.addRow(new Object[]{batch.name(), f(batch.totalTransactionUtility()),
                    f(batch.minUtilUpper()), f(batch.minUtilLower()), batch.largePatterns().size() + " mẫu"});
        }
        JTable thresholdTable = table();
        thresholdTable.setModel(limits);
        styleTable(thresholdTable, 0);
        content.add(tableBody(thresholdTable));
        explanationTab.removeAll();
        explanationTab.add(content, BorderLayout.CENTER);
        explanationTab.revalidate();
    }

    private JPanel explanationCard(String heading, String formula, String meaning, Color accent, Color fill) {
        RoundedPanel card = new RoundedPanel(14, fill);
        card.setBorder(BorderFactory.createEmptyBorder(15, 17, 15, 17));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(label(heading, 11, Font.BOLD, accent));
        card.add(Box.createVerticalStrut(9));
        card.add(label(formula, 12, Font.BOLD, INK));
        card.add(Box.createVerticalStrut(7));
        card.add(label(meaning, 11, Font.PLAIN, MUTED));
        return card;
    }

    private JPanel algorithmCard(String title, AnalysisReport report, Color accent, Color fill) {
        RoundedPanel card = new RoundedPanel(14, fill);
        card.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        card.setLayout(new BorderLayout(12, 0));
        JLabel name = label(title, 12, Font.BOLD, accent);
        card.add(name, BorderLayout.NORTH);
        JPanel values = new JPanel(new GridLayout(1, 3, 12, 0));
        values.setOpaque(false);
        values.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        values.add(miniMetric("RE-SCAN", String.valueOf(report.stats().rescanCount())));
        values.add(miniMetric("PATTERN", String.valueOf(report.stats().patternsVisited())));
        values.add(miniMetric("THỜI GIAN", f(report.elapsedNanos() / 1_000_000.0) + " ms"));
        card.add(values, BorderLayout.CENTER);
        return card;
    }

    private JPanel miniMetric(String title, String value) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(label(title, 9, Font.BOLD, MUTED));
        panel.add(Box.createVerticalStrut(5));
        panel.add(label(value, 17, Font.BOLD, INK));
        return panel;
    }

    private JPanel patternGroup(String title, List<PatternResult> values, Color accent, Color fill) {
        RoundedPanel card = new RoundedPanel(14, SURFACE_SOFT);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(15, 16, 15, 16));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(label(title, 11, Font.BOLD, accent), BorderLayout.WEST);
        JLabel count = label(values.size() + " mẫu", 11, Font.BOLD, accent);
        count.setOpaque(true);
        count.setBackground(fill);
        count.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        heading.add(count, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);
        JPanel chips = new JPanel(new GridLayout(0, 2, 8, 8));
        chips.setOpaque(false);
        if (values.isEmpty()) chips.add(label("Không có mẫu", 12, Font.PLAIN, MUTED));
        for (PatternResult pattern : values) {
            JLabel chip = label(pattern.displayName() + "  ·  AU " + f(pattern.averageUtility()), 11, Font.BOLD, accent);
            chip.setOpaque(true);
            chip.setBackground(fill);
            chip.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
            chips.add(chip);
        }
        card.add(chips, BorderLayout.CENTER);
        return card;
    }

    private JPanel batchView(AnalysisReport report, AnalysisReport.BatchView batch) {
        JPanel view = new JPanel();
        view.setBackground(SURFACE);
        view.setBorder(BorderFactory.createEmptyBorder(14, 12, 16, 12));
        view.setLayout(new BoxLayout(view, BoxLayout.Y_AXIS));

        RoundedPanel steps = new RoundedPanel(12, PRIMARY_SOFT);
        steps.setLayout(new GridLayout(0, 1, 0, 5));
        steps.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        steps.add(label("BƯỚC 1  ĐẦU VÀO  ·  " + batch.transactionCount() + " giao dịch; tích lũy "
                + batch.cumulativeTransactionCount(), 11, Font.BOLD, INK));
        steps.add(label("BƯỚC 2  NGƯỠNG  ·  Su×TU=" + f(batch.minUtilUpper())
                + "  |  Sl×TU=" + f(batch.minUtilLower()), 11, Font.BOLD, INK));
        var maxBound = report.upperBounds().entrySet().stream()
                .max(Map.Entry.comparingByValue());
        steps.add(label("BƯỚC 3  RE-SCAN  ·  " + batch.action(), 11, Font.BOLD, INK));
        steps.add(label("BƯỚC 4  AUUB TOÀN LƯỢT  ·  " + maxBound.map(e -> e.getKey() + " = " + f(e.getValue()))
                .orElse("không có") + "  |  " + report.stats().patternsVisited() + " mẫu duyệt",
                11, Font.BOLD, INK));
        steps.add(label("BƯỚC 5  KẾT QUẢ  ·  " + batch.largePatterns().size() + " LARGE; "
                + batch.preLargePatterns().size() + " PRE-LARGE", 11, Font.BOLD, INK));
        view.add(steps);
        view.add(Box.createVerticalStrut(12));

        JPanel metrics = new JPanel(new GridLayout(2, 3, 9, 9));
        metrics.setOpaque(false);
        metrics.add(metric("GIAO DỊCH", String.valueOf(batch.transactionCount()), batch.name()));
        metrics.add(metric("TU BATCH", f(batch.batchTransactionUtility()), "transaction utility"));
        metrics.add(metric("MU BATCH", f(batch.batchMaximumUtility()), "maximum utility"));
        metrics.add(metric("TU TÍCH LUỸ", f(batch.totalTransactionUtility()), "toàn bộ dữ liệu"));
        metrics.add(metric("UPPER / LOWER", f(batch.minUtilUpper()) + " / " + f(batch.minUtilLower()), "ngưỡng phân loại"));
        metrics.add(metric("THỜI GIAN", f(batch.elapsedMs()) + " ms", f(batch.memoryAfterMb()) + " MB heap"));
        view.add(metrics);
        view.add(Box.createVerticalStrut(12));

        boolean initial = batch.index() == 0;
        boolean rescan = batch.rescanTriggered();
        Color accent = initial ? PRIMARY : rescan ? AMBER : TEAL;
        Color fill = initial ? PRIMARY_SOFT : rescan ? AMBER_SOFT : TEAL_SOFT;
        RoundedPanel decision = new RoundedPanel(14, fill);
        decision.setLayout(new BorderLayout(12, 0));
        decision.setBorder(BorderFactory.createEmptyBorder(15, 17, 15, 17));
        decision.add(label(initial ? "1" : rescan ? "↻" : "✓", 24, Font.BOLD, accent), BorderLayout.WEST);
        String detail = initial ? "Initial scan: Construct(GL), Restructure(GL), PatternExpansion(GL) để tạo PT."
                : rescan ? "Re-scan OD ∪ ID; dựng lại global PIHAUP-List (GL) và pattern tree (PT)."
                : "Không re-scan: scan batch mới và cập nhật utility của các node hiện có trong PT.";
        JPanel explanation = vertical(label(initial ? "KHỞI TẠO PATTERN TREE"
                        : rescan ? "RE-SCAN TOÀN BỘ" : "CẬP NHẬT PATTERN TREE", 14, Font.BOLD, accent),
                label(detail, 11, Font.PLAIN, new Color(74, 87, 103)));
        if (batch.tightRescanLimit() != null) {
            double oldTu = batch.totalTransactionUtility() - batch.accumulatedTransactionUtility();
            double left = batch.accumulatedMaximumUtility()
                    - report.upperThreshold() * batch.accumulatedTransactionUtility();
            double right = (report.upperThreshold() - report.lowerThreshold()) * oldTu;
            explanation.add(label("Eq.(12): " + f(left) + (rescan ? " ≥ " : " < ") + f(right)
                    + "   ·   MU(ID)=" + f(batch.accumulatedMaximumUtility())
                    + "   TU(ID)=" + f(batch.accumulatedTransactionUtility())
                    + "   TU(OD)=" + f(oldTu), 11, Font.BOLD, accent));
        }
        decision.add(explanation, BorderLayout.CENTER);
        view.add(decision);
        view.add(Box.createVerticalStrut(12));

        JPanel groups = new JPanel(new GridLayout(1, 2, 12, 0));
        groups.setOpaque(false);
        groups.add(patternGroup("LARGE / HAUP", batch.largePatterns(), TEAL, TEAL_SOFT));
        groups.add(patternGroup("PRE-LARGE ĐANG GIỮ", batch.preLargePatterns(), AMBER, AMBER_SOFT));
        view.add(groups);
        return view;
    }

    private JPanel comparisonView() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 12, 12, 12));
        RoundedPanel hint = new RoundedPanel(12, PRIMARY_SOFT);
        hint.setLayout(new BorderLayout());
        hint.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        hint.add(label("Original Eq.(5) và Tight Eq.(6) chạy trên cùng dữ liệu", 12, Font.BOLD, new Color(23, 74, 198)), BorderLayout.WEST);
        hint.add(label("Giá trị âm ở cột Thay đổi là mức giảm", 11, Font.PLAIN, MUTED), BorderLayout.EAST);
        panel.add(hint, BorderLayout.NORTH);
        panel.add(tableBody(comparisonTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel patternView() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 12, 12, 12));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(vertical(finalThresholdLabel,
                label("PRE-LARGE hiển thị là các ứng viên được giữ trong pattern tree, không phải mọi tập theo ngưỡng.",
                        10, Font.PLAIN, MUTED)), BorderLayout.CENTER);
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        legend.setOpaque(false);
        legend.add(label("● LARGE", 11, Font.BOLD, TEAL));
        legend.add(label("● PRE-LARGE", 11, Font.BOLD, AMBER));
        heading.add(legend, BorderLayout.EAST);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(tableBody(patternTable), BorderLayout.CENTER);
        return panel;
    }

    private static JPanel tableBody(JTable table) {
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(SURFACE);
        body.setBorder(BorderFactory.createLineBorder(BORDER));
        JTableHeader header = table.getTableHeader();
        header.setPreferredSize(new Dimension(0, 36));
        body.add(header, BorderLayout.NORTH);
        body.add(table, BorderLayout.CENTER);
        return body;
    }

    private JPanel metric(String name, String value, String note) {
        RoundedPanel panel = new RoundedPanel(14, SURFACE_SOFT);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(label(name, 10, Font.BOLD, MUTED));
        panel.add(Box.createVerticalStrut(9));
        panel.add(label(value, 21, Font.BOLD, INK));
        panel.add(Box.createVerticalStrut(5));
        panel.add(label(note, 11, Font.PLAIN, TEAL));
        return panel;
    }

    private void runAnalysis() {
        final double upper;
        final double lower;
        try {
            upper = Double.parseDouble(upperField.getText().trim());
            lower = Double.parseDouble(lowerField.getText().trim());
            if (!(lower > 0 && lower < upper && upper <= 1)) {
                throw new IllegalArgumentException("Cần 0 < Sl < Su ≤ 1.");
            }
        } catch (RuntimeException error) {
            showError(error);
            return;
        }
        runButton.setEnabled(false);
        exportButton.setEnabled(false);
        lastRun = null;
        loadingSourceLabel.setText(inputField.getText().isBlank()
                ? "Tables 2–3 · dữ liệu bài báo" : inputNameLabel.getText());
        resultCards.show(resultHost, "loading");
        statusLabel.setForeground(AMBER);

        statusLabel.setText("●  Đang chạy hai thuật toán…");
        /* DESIGN PATTERN: Template Method thông qua SwingWorker.
         * SwingWorker quản lý trình tự: execute -> doInBackground -> done.
         * Lớp ẩn danh cung cấp các bước cụ thể bằng cách ghi đè hai phương thức.
         * doInBackground đọc file/chạy thuật toán/xuất file ở luồng nền;
         * done chạy trên EDT để lấy kết quả và cập nhật các thành phần Swing.
         * Mục đích: giữ giao diện phản hồi trong khi công việc dài đang thực hiện.
         * Tối ưu đã áp dụng: chụp input/output một lần trên EDT trước khi chạy nền.
         * Worker chỉ dùng các chuỗi final bên dưới, không đọc trực tiếp JTextField;
         * người dùng sửa ô nhập trong lúc chạy không thay đổi dữ liệu của lượt này.
         * Tên nguồn được tính một lần, dùng chung khi xuất file và tạo RunResult.
         * Đây là cải thiện tính nhất quán/an toàn luồng; chưa đo mức tăng tốc UI.
         */
        final String inputText = inputField.getText().trim();
        final String archiveDataset = selectedArchiveDataset;
        final String outputText = outputField.getText().trim();
        new SwingWorker<RunResult, String>() {
            @Override
            protected RunResult doInBackground() throws Exception {
                InputData parsed;
                if (inputText.isBlank()) parsed = InputParser.parseBuiltInSample();
                else if (inputText.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    parsed = ArchiveDatasetReader.read(Path.of(inputText), archiveDataset,
                            upper, lower, message -> publish(message));
                } else if (Path.of(inputText).getFileName().toString().equalsIgnoreCase("transactions.txt")) {
                    try (var reader = Files.newBufferedReader(Path.of(inputText))) {
                        Path parent = Path.of(inputText).toAbsolutePath().getParent();
                        parsed = ArchiveDatasetReader.readTransactionsText(reader,
                                parent == null ? "transactions" : parent.getFileName().toString(), upper, lower,
                                message -> publish(message));
                    }
                } else parsed = InputParser.parse(Path.of(inputText));
                InputData input = new InputData(upper, lower, parsed.externalUtilities(), parsed.batches());
                AnalysisReport report = AnalysisReport.run(input, RescanPolicy.TIGHT, message -> publish(message));
                Path output = outputText.isBlank() ? null : Path.of(outputText);
                String source = inputText.isBlank() ? "Tables 2–3 · bài báo"
                        : Path.of(inputText).getFileName().toString()
                        + (archiveDataset == null ? "" : " · " + archiveDataset);
                if (output != null) {
                    ExportBundle.writeDirectory(input, report, output, source);
                }
                return new RunResult(report, input, output, source);
            }

            @Override
            protected void process(List<String> messages) {
                if (!messages.isEmpty()) statusLabel.setText("●  " + messages.get(messages.size() - 1));
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                try {
                    RunResult result = get();
                    lastRun = result;
                    exportButton.setEnabled(true);
                    populateDashboard(result.report(), result.input());
                    statusLabel.setForeground(TEAL);
                    statusLabel.setText(result.output() == null ? "●  Hoàn tất · chưa chọn thư mục xuất"
                            : "●  Đã xuất TXT và CSV");
                    statusLabel.setToolTipText(result.output() == null ? null
                            : result.output().toAbsolutePath().toString());
                } catch (Exception error) {
                    resultCards.show(resultHost, "empty");
                    showError(error.getCause() == null ? error : error.getCause());
                }
            }
        }.execute();
    }

    private void chooseFile() {
        Path start = inputField.getText().isBlank() ? Path.of("examples")
                : Path.of(inputField.getText().trim());
        Path directory = start.toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) directory = directory.getParent();
        if (directory == null || !Files.isDirectory(directory)) directory = Path.of(".").toAbsolutePath();

        FileDialog picker = new FileDialog(this, "Mở tệp dữ liệu PIHAUPA", FileDialog.LOAD);
        picker.setDirectory(directory.toString());
        picker.setFile("*");
        picker.setVisible(true);
        if (picker.getFile() == null) return;
        Path selected = Path.of(picker.getDirectory(), picker.getFile()).toAbsolutePath().normalize();
        String selectedName = selected.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!Files.isRegularFile(selected) || !(selectedName.endsWith(".txt") || selectedName.endsWith(".zip"))) {
            showError(new IllegalArgumentException("Hãy chọn một tệp dữ liệu .txt hoặc .zip hiện có."));
            return;
        }
        selectInput(selected);
    }

    private void selectInput(Path selected) {
        String dataset = null;
        if (selected.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try {
                List<String> names = ArchiveDatasetReader.datasetNames(selected);
                if (names.isEmpty()) throw new IllegalArgumentException("ZIP không có bộ dữ liệu hợp lệ.");
                dataset = (String) JOptionPane.showInputDialog(this, "Chọn một bộ dữ liệu để phân tích",
                        "Dữ liệu trong ZIP", JOptionPane.QUESTION_MESSAGE, null,
                        names.toArray(String[]::new), names.get(0));
                if (dataset == null) return;
            } catch (Exception error) {
                showError(error);
                return;
            }
        }
        selectedArchiveDataset = dataset;
        lastRun = null;
        exportButton.setEnabled(false);
        inputField.setText(selected.toString());
        inputNameLabel.setText(ellipsis(selected.getFileName().toString(), 31));
        inputNameLabel.setToolTipText(selected.toString());
        inputMetaLabel.setText(dataset == null ? "TXT · Tệp dữ liệu từ máy"
                : "ZIP · " + dataset + " · giao dịch gốc");
        inputMetaLabel.setToolTipText(selected.toString());
        statusLabel.setText("●  Đã chọn " + selected.getFileName());
    }

    private void usePaperSample() {
        selectedArchiveDataset = null;
        lastRun = null;
        exportButton.setEnabled(false);
        inputField.setText(Path.of("examples", "paper-example.txt").toString());
        inputNameLabel.setText("Tables 2–3 · paper-example.txt");
        inputMetaLabel.setText("TXT · dữ liệu định lượng theo batch");
        upperField.setText("0.23");
        lowerField.setText("0.10");
        statusLabel.setForeground(MUTED);
        statusLabel.setText("●  Đã nạp dữ liệu bài báo");
    }

    private void chooseOutputDirectory() {
        Path start = outputField.getText().isBlank() ? Path.of("results")
                : Path.of(outputField.getText().trim());
        Path directory = start.toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) directory = directory.getParent();
        if (directory == null || !Files.isDirectory(directory)) directory = Path.of(".").toAbsolutePath();
        Path selected = null;
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            JFileChooser picker = new JFileChooser(directory.toFile());
            picker.setDialogTitle("Chọn thư mục lưu TXT và CSV");
            picker.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            picker.setApproveButtonText("Chọn thư mục");
            if (picker.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selected = picker.getSelectedFile().toPath().toAbsolutePath().normalize();
            }
        } catch (Exception error) {
            showError(error);
        } finally {
            try { UIManager.setLookAndFeel(previous); } catch (Exception ignored) { }
        }
        if (selected != null) {
            outputField.setText(selected.toString());
            statusLabel.setText("●  Báo cáo sẽ lưu tại " + selected.getFileName());
        }
    }

    private void exportLastRun() {
        RunResult snapshot = lastRun;
        if (snapshot == null) return;
        try {
            if (Double.parseDouble(upperField.getText().trim()) != snapshot.input().upperThreshold()
                    || Double.parseDouble(lowerField.getText().trim()) != snapshot.input().lowerThreshold()) {
                throw new IllegalArgumentException("Ngưỡng đã thay đổi. Hãy chạy lại trước khi xuất.");
            }
        } catch (NumberFormatException error) {
            showError(new IllegalArgumentException("Ngưỡng không hợp lệ. Hãy chạy lại trước khi xuất."));
            return;
        } catch (IllegalArgumentException error) {
            showError(error);
            return;
        }
        if (outputField.getText().isBlank()) chooseOutputDirectory();
        if (outputField.getText().isBlank()) return;
        Path destination = Path.of(outputField.getText().trim());
        exportButton.setEnabled(false);
        statusLabel.setForeground(AMBER);
        statusLabel.setText("●  Đang xuất báo cáo…");
        /* DESIGN PATTERN: Template Method cho tác vụ xuất báo cáo.
         * SwingWorker quản lý luồng nền và gọi done trên EDT khi hoàn tất.
         * snapshot giữ input/report/source của lượt đã chạy; destination đã được chụp.
         * Mục đích: ghi file không chiếm luồng cập nhật giao diện.
         * Tối ưu hiện có: dùng lại kết quả trong snapshot, không chạy mining lại
         * cho TXT, CSV hoặc từng định dạng. Chưa đo riêng thời gian I/O xuất file.
         */
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                ExportBundle.writeDirectory(snapshot.input(), snapshot.report(), destination, snapshot.source());
                return null;
            }
            @Override protected void done() {
                exportButton.setEnabled(true);
                try {
                    get();
                    statusLabel.setForeground(TEAL);
                    statusLabel.setText("●  Đã xuất TXT và CSV");
                    statusLabel.setToolTipText(destination.toAbsolutePath().toString());
                } catch (Exception error) {
                    showError(error.getCause() == null ? error : error.getCause());
                }
            }
        }.execute();
    }

    private void showError(Throwable error) {
        runButton.setEnabled(true);
        statusLabel.setForeground(new Color(212, 61, 81));
        statusLabel.setText("●  Lỗi: " + error.getMessage());
        JOptionPane.showMessageDialog(this, error.getMessage(), "PIHAUPA", JOptionPane.ERROR_MESSAGE);
    }

    private static String summaryText(ComparisonReport comparison) {
        AnalysisReport old = comparison.original();
        AnalysisReport tight = comparison.tight();
        AnalysisReport.BatchView last = tight.batches().get(tight.batches().size() - 1);
        return "KẾT LUẬN SO SÁNH\n" + "─".repeat(72) + "\n"
                + "Kết quả HAUP cuối : " + (comparison.sameHaups() ? "TRÙNG KHỚP ✓" : "KHÁC NHAU ⚠") + "\n"
                + "Original Eq.(5)   : " + old.stats().rescanCount() + " lần re-scan, "
                + old.stats().patternsVisited() + " pattern đã duyệt\n"
                + "Tight Eq.(6)      : " + tight.stats().rescanCount() + " lần re-scan, "
                + tight.stats().patternsVisited() + " pattern đã duyệt\n"
                + "Giảm              : " + (old.stats().rescanCount() - tight.stats().rescanCount())
                + " re-scan, " + (old.stats().patternsVisited() - tight.stats().patternsVisited()) + " pattern\n\n"
                + "HAUP CUỐI\n" + "─".repeat(72) + "\n"
                + patternsInline(last.largePatterns()) + "\n\n"
                + "PRE-LARGE CUỐI\n" + "─".repeat(72) + "\n"
                + patternsInline(last.preLargePatterns()) + "\n";
    }

    private static String batchText(AnalysisReport report, AnalysisReport.BatchView batch) {
        StringBuilder out = new StringBuilder();
        out.append(batch.name()).append(" · DỮ LIỆU VÀ NGƯỠNG\n").append("─".repeat(72)).append('\n');
        out.append(String.format(Locale.US,
                "Transaction: %d    TU batch: %.3f    MU batch: %.3f    TU tích luỹ: %.3f%n",
                batch.transactionCount(), batch.batchTransactionUtility(), batch.batchMaximumUtility(),
                batch.totalTransactionUtility()));
        out.append(String.format(Locale.US, "minUtil upper: %.3f    minUtil lower: %.3f%n%n",
                batch.minUtilUpper(), batch.minUtilLower()));
        out.append("QUYẾT ĐỊNH RE-SCAN\n").append("─".repeat(72)).append('\n');
        out.append(batch.action()).append('\n');
        if (batch.tightRescanLimit() != null) {
            double oldTu = batch.totalTransactionUtility() - batch.accumulatedTransactionUtility();
            double left = batch.accumulatedMaximumUtility()
                    - report.upperThreshold() * batch.accumulatedTransactionUtility();
            double right = (report.upperThreshold() - report.lowerThreshold()) * oldTu;
            out.append(String.format(Locale.US, "Eq.(12): %.6f %s %.6f%n",
                    left, batch.rescanTriggered() ? ">=" : "<", right));
        }
        out.append(String.format(Locale.US, "Thời gian batch: %.3f ms    Heap: %.3f MB%n%n",
                batch.elapsedMs(), batch.memoryAfterMb()));
        out.append("LARGE / HAUP (" + batch.largePatterns().size() + ")\n")
                .append("─".repeat(72)).append('\n').append(patternsInline(batch.largePatterns())).append("\n\n");
        out.append("PRE-LARGE (" + batch.preLargePatterns().size() + ")\n")
                .append("─".repeat(72)).append('\n').append(patternsInline(batch.preLargePatterns())).append('\n');
        return out.toString();
    }

    private static String patternsInline(List<PatternResult> patterns) {
        if (patterns.isEmpty()) return "(không có)";
        List<String> values = new ArrayList<>();
        for (PatternResult pattern : patterns) {
            values.add(pattern.displayName() + " [AU=" + f(pattern.averageUtility()) + "]");
        }
        return String.join("   ·   ", values);
    }

    private static JPanel wrapTable(JTable table) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 12, 12, 12));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private static JScrollPane scroll(Component component) {
        JScrollPane pane = new JScrollPane(component);
        pane.setBorder(null);
        pane.getViewport().setBackground(SURFACE);
        pane.getVerticalScrollBar().setUnitIncrement(18);
        pane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        pane.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return pane;
    }

    private static JTextField field(String... value) {
        JTextField field = new JTextField(value.length == 0 ? "" : value[0]);
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        field.setForeground(INK);
        field.setBackground(SURFACE);
        field.setCaretColor(PRIMARY);
        field.setPreferredSize(new Dimension(100, 42));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(198, 210, 225)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        return field;
    }

    private static JButton primaryButton(String text) {
        return button(text, PRIMARY, Color.WHITE);
    }

    private static JButton secondaryButton(String text) {
        return button(text, PRIMARY_SOFT, new Color(23, 74, 198));
    }

    private static JButton tertiaryButton(String text) {
        JButton button = button(text, SURFACE, new Color(65, 84, 107));
        button.setBorderPainted(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setPreferredSize(new Dimension(240, 42));
        button.setMinimumSize(new Dimension(180, 42));
        return button;
    }

    private static JButton button(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setForeground(foreground);
        button.setBackground(background);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(120, 43));
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { button.setBackground(background.darker()); }
            @Override public void mouseExited(MouseEvent e) { button.setBackground(background); }
        });
        return button;
    }

    private static JLabel fieldLabel(String text) {
        return label(text, 12, Font.BOLD, new Color(65, 84, 107));
    }

    private static JLabel label(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, style, size));
        label.setForeground(color);
        return label;
    }

    private static JPanel vertical(Component... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (Component component : components) {
            if (component instanceof JComponent swingComponent) {
                swingComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            panel.add(component);
            panel.add(Box.createVerticalStrut(3));
        }
        return panel;
    }

    private static JTable table() {
        JTable table = new JTable();
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        table.setForeground(INK);
        table.setBackground(SURFACE);
        table.setGridColor(new Color(237, 241, 246));
        table.setRowHeight(38);
        table.setShowVerticalLines(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(PRIMARY_SOFT);
        table.setSelectionForeground(INK);
        table.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBackground(SURFACE_SOFT);
        table.setFillsViewportHeight(true);
        return table;
    }

    private static void styleTable(JTable table, int coloredColumn) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                    boolean focused, int row, int column) {
                Component component = super.getTableCellRendererComponent(t, value, selected, focused, row, column);
                if (!selected) {
                    component.setBackground(row % 2 == 0 ? SURFACE : SURFACE_SOFT);
                    Color color = INK;
                    if (column == coloredColumn) {
                        String text = String.valueOf(value);
                        color = text.contains("PRE-LARGE") ? AMBER : TEAL;
                    }
                    if (column == 3 && "Thay đổi".equals(t.getColumnName(column))
                            && String.valueOf(value).startsWith("-")) {
                        color = TEAL;
                    }
                    component.setForeground(color);
                    component.setFont(new Font(Font.SANS_SERIF,
                            column == coloredColumn ? Font.BOLD : Font.PLAIN, 13));
                }
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return component;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setCellRenderer(renderer);
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private static void addRow(DefaultTableModel model, Object... values) { model.addRow(values); }
    private static String f(double value) { return String.format(Locale.US, "%.3f", value); }
    private static String ellipsis(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }
    private static String signed(long value) { return value > 0 ? "+" + value : String.valueOf(value); }
    private static String percent(double oldValue, double newValue) {
        return oldValue == 0 ? "—" : String.format(Locale.US, "%+.1f%%", (newValue - oldValue) / oldValue * 100.0);
    }
    private static List<PatternResult> concat(List<PatternResult> first, List<PatternResult> second) {
        List<PatternResult> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"); }
            catch (Exception ignored) { }
            Font base = new Font("Segoe UI", Font.PLAIN, 13);
            UIManager.put("Label.font", base);
            UIManager.put("Button.font", base);
            UIManager.put("TextField.font", base);
            UIManager.put("TextArea.font", base);
            UIManager.put("Table.font", base);
            UIManager.put("TableHeader.font", base.deriveFont(Font.BOLD, 11f));
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("Panel.background", SURFACE);
            DesktopApp app = new DesktopApp();
            app.setVisible(true);
            app.runAnalysis();
        });
    }

    private record RunResult(AnalysisReport report, InputData input, Path output, String source) { }

    private static final class RoundedPanel extends JPanel {
        private final int radius;
        private final Color fill;

        private RoundedPanel(int radius, Color fill) {
            this.radius = radius;
            this.fill = fill;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class VerticalReportPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(80, visibleRect.height - 60);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static final class ModernScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(190, 200, 214);
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
        @Override protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
            graphics.setColor(CANVAS);
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        @Override protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(thumbColor);
            g.fillRoundRect(bounds.x + 2, bounds.y, Math.max(4, bounds.width - 4), bounds.height, 8, 8);
            g.dispose();
        }
    }
}
