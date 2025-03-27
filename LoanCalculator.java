import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.FileWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class LoanCalculator extends JFrame {
    private JFormattedTextField principalField, rateField;
    private JSpinner termField;
    private JTable amortizationTable;
    private DefaultTableModel tableModel;
    private JFreeChart chart;
    private List<PaymentRecord> paymentRecords;
    private final NumberFormat currency = NumberFormat.getCurrencyInstance();
    private final NumberFormat percent = NumberFormat.getPercentInstance();

    public LoanCalculator() {
        super("Loan Calculator");
        setSize(900, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        percent.setMinimumFractionDigits(2);

        initializeComponents();
        createMenuBar();
        setVisible(true);
    }

    private void initializeComponents() {
        // Input Panel
        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        principalField = new JFormattedTextField(currency);
        rateField = new JFormattedTextField(NumberFormat.getNumberInstance());
        termField = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));

        principalField.setValue(100000.0);
        rateField.setValue(5.0);
        termField.setValue(30);

        inputPanel.add(new JLabel("Principal Amount:"));
        inputPanel.add(principalField);
        inputPanel.add(new JLabel("Annual Interest Rate (%):"));
        inputPanel.add(rateField);
        inputPanel.add(new JLabel("Loan Term (years):"));
        inputPanel.add(termField);

        // Buttons
        JButton calculateButton = new JButton("Calculate");
        calculateButton.addActionListener(this::calculateLoan);

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clearFields());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(calculateButton);
        buttonPanel.add(clearButton);

        // Table
        String[] columns = {"Month", "Payment", "Principal", "Interest", "Balance"};
        tableModel = new DefaultTableModel(columns, 0);
        amortizationTable = new JTable(tableModel);
        JScrollPane tableScroll = new JScrollPane(amortizationTable);

        // Chart
        DefaultPieDataset initialDataset = new DefaultPieDataset();
        initialDataset.setValue("Principal", 100);
        initialDataset.setValue("Interest", 0);
        chart = ChartFactory.createPieChart(
                "Payment Breakdown",
                initialDataset,
                true, true, false
        );
        ChartPanel chartPanel = new ChartPanel(chart);

        // Layout
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tableScroll), chartPanel);
        mainSplit.setResizeWeight(0.6);

        add(inputPanel, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem exportCsv = new JMenuItem("Export to CSV");
        exportCsv.addActionListener(e -> exportToCSV());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(exportCsv);
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }

    private void calculateLoan(ActionEvent e) {
        try {
            Object principalValue = principalField.getValue();
            Object rateValue = rateField.getValue();

            if (principalValue == null || rateValue == null) {
                throw new IllegalArgumentException("Please fill in all fields");
            }

            double principal = ((Number) principalValue).doubleValue();
            double annualRate = ((Number) rateValue).doubleValue();
            int termYears = (int) termField.getValue();

            validateInputs(principal, annualRate, termYears);

            double monthlyRate = annualRate / 100 / 12;
            int numberOfPayments = termYears * 12;
            double monthlyPayment = calculateMonthlyPayment(principal, monthlyRate, numberOfPayments);

            paymentRecords = generateAmortizationSchedule(
                    principal,
                    monthlyRate,
                    numberOfPayments,
                    monthlyPayment
            );

            updateTable();
            updateChart(principal, paymentRecords.get(paymentRecords.size()-1).totalInterest());

        } catch (IllegalArgumentException ex) {
            showErrorDialog(ex.getMessage());
        } catch (Exception ex) {
            showErrorDialog("Error: " + ex.getMessage());
        }
    }

    private double calculateMonthlyPayment(double principal, double monthlyRate, int months) {
        return (principal * monthlyRate) /
                (1 - Math.pow(1 + monthlyRate, -months));
    }

    private List<PaymentRecord> generateAmortizationSchedule(
            double principal,
            double monthlyRate,
            int months,
            double monthlyPayment
    ) {
        List<PaymentRecord> records = new ArrayList<>();
        double balance = principal;
        double totalInterest = 0;

        for (int month = 1; month <= months; month++) {
            double interest = Math.round(balance * monthlyRate * 100.0) / 100.0;
            double principalPart = monthlyPayment - interest;
            totalInterest += interest;

            if (month == months) {  // Final payment adjustment
                principalPart = balance;
                balance = 0;
            } else {
                balance -= principalPart;
                balance = Math.round(balance * 100.0) / 100.0;
            }

            records.add(new PaymentRecord(
                    month,
                    monthlyPayment,
                    principalPart,
                    interest,
                    balance,
                    totalInterest
            ));
        }
        return records;
    }

    private void updateTable() {
        tableModel.setRowCount(0);
        for (PaymentRecord record : paymentRecords) {
            tableModel.addRow(new Object[]{
                    record.month(),
                    currency.format(record.payment()),
                    currency.format(record.principal()),
                    currency.format(record.interest()),
                    currency.format(record.balance())
            });
        }
    }

    private void updateChart(double principal, double totalInterest) {
        PiePlot plot = (PiePlot) chart.getPlot();
        DefaultPieDataset dataset = (DefaultPieDataset) plot.getDataset();
        dataset.clear();
        dataset.setValue("Principal (" + currency.format(principal) + ")", principal);
        dataset.setValue("Interest (" + currency.format(totalInterest) + ")", totalInterest);
    }

    private void exportToCSV() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter writer = new FileWriter(fileChooser.getSelectedFile())) {
                writer.write("Month,Payment,Principal,Interest,Balance,Total Interest\n");
                for (PaymentRecord record : paymentRecords) {
                    writer.write(String.format("%d,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                            record.month(),
                            record.payment(),
                            record.principal(),
                            record.interest(),
                            record.balance(),
                            record.totalInterest()));
                }
                JOptionPane.showMessageDialog(this, "Export completed successfully!");
            } catch (Exception ex) {
                showErrorDialog("Export failed: " + ex.getMessage());
            }
        }
    }

    private void clearFields() {
        principalField.setValue(0.0);
        rateField.setValue(0.0);
        termField.setValue(1);
        tableModel.setRowCount(0);
        updateChart(0, 0);
    }

    private void validateInputs(double principal, double rate, int term) {
        if (principal <= 0) throw new IllegalArgumentException("Principal must be positive");
        if (rate <= 0 || rate >= 100) throw new IllegalArgumentException("Rate must be between 0.01% and 99.99%");
        if (term <= 0) throw new IllegalArgumentException("Term must be at least 1 year");
    }

    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Input Error", JOptionPane.ERROR_MESSAGE);
    }

    private record PaymentRecord(
            int month,
            double payment,
            double principal,
            double interest,
            double balance,
            double totalInterest
    ) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoanCalculator());
    }
}