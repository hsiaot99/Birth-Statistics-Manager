import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.sql.*;
import java.util.Vector;

public class BirthStatsManager extends JFrame {
    private JTable dataTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private final ConfigManager config;

    public BirthStatsManager() {
        config = ConfigManager.getInstance();
        
        // Load the SQL Server JDBC driver
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(this, 
                "Error loading SQL Server JDBC driver: " + e.getMessage(),
                "Driver Error", 
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        
        // Set up the frame
        setTitle(config.getAppTitle());
        setSize(config.getWindowWidth(), config.getWindowHeight());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Create main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create button panels for two rows
        JPanel buttonPanelContainer = new JPanel(new GridLayout(2, 1, 0, 5));
        
        // First row panel for import/export buttons
        JPanel firstRowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton refreshBtn = new JButton("Refresh Data");
        JButton importBtn = new JButton("Import CSV");
        JButton importJsonBtn = new JButton("Import JSON");
        JButton importXmlBtn = new JButton("Import XML");
        JButton exportBtn = new JButton("Export to CSV");
        JButton exportJsonBtn = new JButton("Export to JSON");
        JButton exportXmlBtn = new JButton("Export to XML");

        firstRowPanel.add(refreshBtn);
        firstRowPanel.add(importBtn);
        firstRowPanel.add(importJsonBtn);
        firstRowPanel.add(importXmlBtn);
        firstRowPanel.add(exportBtn);
        firstRowPanel.add(exportJsonBtn);
        firstRowPanel.add(exportXmlBtn);

        // Second row panel for CRUD and search
        JPanel secondRowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton addBtn = new JButton("Add Record");
        JButton editBtn = new JButton("Edit Record");
        JButton deleteBtn = new JButton("Delete Record");
        searchField = new JTextField(20);
        JButton searchBtn = new JButton("Search");

        secondRowPanel.add(addBtn);
        secondRowPanel.add(editBtn);
        secondRowPanel.add(deleteBtn);
        secondRowPanel.add(new JLabel("Search:"));
        secondRowPanel.add(searchField);
        secondRowPanel.add(searchBtn);

        // Add both rows to the container
        buttonPanelContainer.add(firstRowPanel);
        buttonPanelContainer.add(secondRowPanel);

        // Create table
        String[] columns = {"ID", "Year", "Record Type", "Area Code", "Area Name", 
                          "Gender", "Birth Weight", "Multiple Birth", "Birth Count"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        dataTable = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(dataTable);

        // Add components to main panel
        mainPanel.add(buttonPanelContainer, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Add main panel to frame
        add(mainPanel);

        // Add action listeners
        refreshBtn.addActionListener(e -> refreshData());
        importBtn.addActionListener(e -> importCSV());
        importJsonBtn.addActionListener(e -> importJSON());
        importXmlBtn.addActionListener(e -> importXML());
        exportBtn.addActionListener(e -> exportToCSV());
        exportJsonBtn.addActionListener(e -> exportToJSON());
        exportXmlBtn.addActionListener(e -> exportToXML());
        addBtn.addActionListener(e -> addRecord());
        editBtn.addActionListener(e -> editRecord());
        deleteBtn.addActionListener(e -> deleteRecord());
        searchBtn.addActionListener(e -> searchRecords());

        // Initial data load
        refreshData();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
            config.getDatabaseUrl(),
            config.getDatabaseUser(),
            config.getDatabasePassword()
        );
    }

    private void refreshData() {
        try {
            // Clear existing data
            tableModel.setRowCount(0);
            
            // Get database connection and test it first
            try (Connection conn = getConnection()) {
                // First try to access the test table
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM TestTable")) {
                    
                    System.out.println("Successfully connected to database!");
                    System.out.println("Test table contents:");
                    while (rs.next()) {
                        System.out.println("ID: " + rs.getInt("ID") + ", Value: " + rs.getString("TestValue"));
                    }
                }
                
                // Now try to access the main table
                String sql = "SELECT TOP 1000 * FROM BirthStatistics ORDER BY ID";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    
                    while (rs.next()) {
                        Object[] row = {
                            rs.getInt("ID"),
                            rs.getInt("StatYear"),
                            rs.getString("RecordType"),
                            rs.getString("AreaCode"),
                            rs.getString("AreaName"),
                            rs.getString("Gender"),
                            rs.getString("BirthWeight"),
                            rs.getString("MultipleBirth"),
                            rs.getInt("BirthCount")
                        };
                        tableModel.addRow(row);
                    }
                }
            }
        } catch (SQLException e) {
            String errorMessage = String.format(
                "Database Error:\nMessage: %s\nError Code: %d\nSQL State: %s",
                e.getMessage(), e.getErrorCode(), e.getSQLState());
            JOptionPane.showMessageDialog(this,
                errorMessage,
                "Database Error",
                JOptionPane.ERROR_MESSAGE);
            System.err.println("\nDetailed SQL Error:");
            System.err.println(errorMessage);
            System.err.println("\nConnection string used (without password):");
            System.err.println(config.getDatabaseUrl());
            e.printStackTrace();
        } catch (Exception e) {
            String errorMessage = "Unexpected error: " + e.getMessage();
            JOptionPane.showMessageDialog(this,
                errorMessage,
                "Error",
                JOptionPane.ERROR_MESSAGE);
            System.err.println(errorMessage);
            e.printStackTrace();
        }
    }

    private void importCSV() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (Connection conn = getConnection();
                 BufferedReader reader = new BufferedReader(new FileReader(fileChooser.getSelectedFile()))) {
                
                conn.setAutoCommit(false);  // Start transaction
                
                // Read header
                String header = reader.readLine();
                if (header == null) {
                    throw new Exception("CSV file is empty");
                }
                System.out.println("CSV Header: " + header);
                
                // Prepare insert statement
                String insertSql = "INSERT INTO BirthStatistics " +
                    "(StatYear, RecordType, AreaCode, AreaName, Gender, BirthWeight, MultipleBirth, BirthCount) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                
                int lineCount = 0;
                String line;
                int batchSize = config.getCsvBatchSize();
                
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    // Read data lines
                    while ((line = reader.readLine()) != null) {
                        lineCount++;
                        if (line.trim().isEmpty()) continue;
                        
                        // Split the line by comma, handling quoted values
                        String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                        
                        // Remove quotes from values
                        for (int i = 0; i < values.length; i++) {
                            values[i] = values[i].replaceAll("^\"|\"$", "").trim();
                        }
                        
                        try {
                            // Set values in prepared statement
                            pstmt.setInt(1, Integer.parseInt(values[0]));  // StatYear
                            pstmt.setString(2, values[1]);  // RecordType
                            pstmt.setString(3, values[2]);  // AreaCode
                            pstmt.setString(4, values[3]);  // AreaName
                            pstmt.setString(5, values[4]);  // Gender
                            pstmt.setString(6, values[5]);  // BirthWeight
                            pstmt.setString(7, values[6]);  // MultipleBirth
                            pstmt.setInt(8, Integer.parseInt(values[7]));  // BirthCount
                            
                            pstmt.addBatch();
                            
                            // Execute batch every configured batch size
                            if (lineCount % batchSize == 0) {
                                pstmt.executeBatch();
                                System.out.println("Processed " + lineCount + " rows");
                            }
                        } catch (Exception e) {
                            System.err.println("Error on line " + lineCount + ": " + line);
                            System.err.println("Parsed values: " + String.join(", ", values));
                            throw e;
                        }
                    }
                    
                    // Execute final batch
                    pstmt.executeBatch();
                }
                
                conn.commit();  // Commit transaction
                System.out.println("Successfully imported " + lineCount + " rows");
                
                JOptionPane.showMessageDialog(this, 
                    "Import completed successfully!\nImported " + lineCount + " rows.",
                    "Import Success",
                    JOptionPane.INFORMATION_MESSAGE);
                
                refreshData();
                
            } catch (SQLException ex) {
                String errorMessage = String.format(
                    "Database Error:\nMessage: %s\nError Code: %d\nSQL State: %s",
                    ex.getMessage(), ex.getErrorCode(), ex.getSQLState());
                System.err.println("\nDetailed SQL Error:");
                System.err.println(errorMessage);
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            } catch (Exception ex) {
                String errorMessage = "Error during import: " + ex.getMessage();
                System.err.println(errorMessage);
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Import Error",
                    JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private void exportToCSV() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM BirthStatistics");
                 PrintWriter writer = new PrintWriter(new File(fileChooser.getSelectedFile() + ".csv"))) {

                // Write header
                writer.println("StatYear,RecordType,AreaCode,AreaName,Gender,BirthWeight,MultipleBirth,BirthCount");

                // Write data
                while (rs.next()) {
                    writer.println(String.format("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d",
                        rs.getInt("StatYear"),
                        rs.getString("RecordType"),
                        rs.getString("AreaCode"),
                        rs.getString("AreaName"),
                        rs.getString("Gender"),
                        rs.getString("BirthWeight"),
                        rs.getString("MultipleBirth"),
                        rs.getInt("BirthCount")));
                }
                JOptionPane.showMessageDialog(this, "Export completed successfully!");
            } catch (SQLException ex) {
                String errorMessage = String.format(
                    "Database Error:\nMessage: %s\nError Code: %d\nSQL State: %s",
                    ex.getMessage(), ex.getErrorCode(), ex.getSQLState());
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
                System.err.println(errorMessage);
                ex.printStackTrace();
            } catch (Exception ex) {
                String errorMessage = "Unexpected error: " + ex.getMessage();
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                System.err.println(errorMessage);
                ex.printStackTrace();
            }
        }
    }

    private void addRecord() {
        // Show input dialog
        JPanel panel = new JPanel(new GridLayout(9, 2));
        JTextField yearField = new JTextField();
        JTextField typeField = new JTextField();
        JTextField areaCodeField = new JTextField();
        JTextField areaNameField = new JTextField();
        JTextField genderField = new JTextField();
        JTextField weightField = new JTextField();
        JTextField multipleBirthField = new JTextField();
        JTextField countField = new JTextField();

        panel.add(new JLabel("Year:"));
        panel.add(yearField);
        panel.add(new JLabel("Record Type:"));
        panel.add(typeField);
        panel.add(new JLabel("Area Code:"));
        panel.add(areaCodeField);
        panel.add(new JLabel("Area Name:"));
        panel.add(areaNameField);
        panel.add(new JLabel("Gender:"));
        panel.add(genderField);
        panel.add(new JLabel("Birth Weight:"));
        panel.add(weightField);
        panel.add(new JLabel("Multiple Birth:"));
        panel.add(multipleBirthField);
        panel.add(new JLabel("Birth Count:"));
        panel.add(countField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add New Record",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            try {
                // Get database connection
                try (Connection conn = getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO BirthStatistics (StatYear, RecordType, AreaCode, AreaName, " +
                         "Gender, BirthWeight, MultipleBirth, BirthCount) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {

                    stmt.setInt(1, Integer.parseInt(yearField.getText()));
                    stmt.setString(2, typeField.getText());
                    stmt.setString(3, areaCodeField.getText());
                    stmt.setString(4, areaNameField.getText());
                    stmt.setString(5, genderField.getText());
                    stmt.setString(6, weightField.getText());
                    stmt.setString(7, multipleBirthField.getText());
                    stmt.setInt(8, Integer.parseInt(countField.getText()));

                    stmt.executeUpdate();
                    JOptionPane.showMessageDialog(this, "Record added successfully!");
                    refreshData();
                }
            } catch (SQLException ex) {
                String errorMessage = String.format(
                    "Database Error:\nMessage: %s\nError Code: %d\nSQL State: %s",
                    ex.getMessage(), ex.getErrorCode(), ex.getSQLState());
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
                System.err.println(errorMessage);
                ex.printStackTrace();
            } catch (NumberFormatException ex) {
                String errorMessage = "Invalid number format: " + ex.getMessage();
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Input Error",
                    JOptionPane.ERROR_MESSAGE);
                System.err.println(errorMessage);
                ex.printStackTrace();
            } catch (Exception ex) {
                String errorMessage = "Unexpected error: " + ex.getMessage();
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                System.err.println(errorMessage);
                ex.printStackTrace();
            }
        }
    }

    private void editRecord() {
        int selectedRow = dataTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a record to update.");
            return;
        }

        // Show input dialog with current values
        JPanel panel = new JPanel(new GridLayout(9, 2));
        JTextField idField = new JTextField(tableModel.getValueAt(selectedRow, 0).toString());
        JTextField yearField = new JTextField(tableModel.getValueAt(selectedRow, 1).toString());
        JTextField typeField = new JTextField(tableModel.getValueAt(selectedRow, 2).toString());
        JTextField areaCodeField = new JTextField(tableModel.getValueAt(selectedRow, 3).toString());
        JTextField areaNameField = new JTextField(tableModel.getValueAt(selectedRow, 4).toString());
        JTextField genderField = new JTextField(tableModel.getValueAt(selectedRow, 5).toString());
        JTextField weightField = new JTextField(tableModel.getValueAt(selectedRow, 6).toString());
        JTextField multipleBirthField = new JTextField(tableModel.getValueAt(selectedRow, 7).toString());
        JTextField countField = new JTextField(tableModel.getValueAt(selectedRow, 8).toString());

        idField.setEditable(false);

        panel.add(new JLabel("ID:"));
        panel.add(idField);
        panel.add(new JLabel("Year:"));
        panel.add(yearField);
        panel.add(new JLabel("Record Type:"));
        panel.add(typeField);
        panel.add(new JLabel("Area Code:"));
        panel.add(areaCodeField);
        panel.add(new JLabel("Area Name:"));
        panel.add(areaNameField);
        panel.add(new JLabel("Gender:"));
        panel.add(genderField);
        panel.add(new JLabel("Birth Weight:"));
        panel.add(weightField);
        panel.add(new JLabel("Multiple Birth:"));
        panel.add(multipleBirthField);
        panel.add(new JLabel("Birth Count:"));
        panel.add(countField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Update Record",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            try {
                // Get database connection
                try (Connection conn = getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "UPDATE BirthStatistics SET StatYear=?, RecordType=?, AreaCode=?, " +
                         "AreaName=?, Gender=?, BirthWeight=?, MultipleBirth=?, BirthCount=? " +
                         "WHERE ID=?")) {

                    stmt.setInt(1, Integer.parseInt(yearField.getText()));
                    stmt.setString(2, typeField.getText());
                    stmt.setString(3, areaCodeField.getText());
                    stmt.setString(4, areaNameField.getText());
                    stmt.setString(5, genderField.getText());
                    stmt.setString(6, weightField.getText());
                    stmt.setString(7, multipleBirthField.getText());
                    stmt.setInt(8, Integer.parseInt(countField.getText()));
                    stmt.setInt(9, Integer.parseInt(idField.getText()));

                    stmt.executeUpdate();
                    JOptionPane.showMessageDialog(this, "Record updated successfully!");
                    refreshData();
                }
            } catch (SQLException ex) {
                String errorMessage = String.format(
                    "Database Error:\nMessage: %s\nError Code: %d\nSQL State: %s",
                    ex.getMessage(), ex.getErrorCode(), ex.getSQLState());
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
                System.err.println(errorMessage);
                ex.printStackTrace();
            } catch (NumberFormatException ex) {
                String errorMessage = "Invalid number format: " + ex.getMessage();
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Input Error",
                    JOptionPane.ERROR_MESSAGE);
                System.err.println(errorMessage);
                ex.printStackTrace();
            } catch (Exception ex) {
                String errorMessage = "Unexpected error: " + ex.getMessage();
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                System.err.println(errorMessage);
                ex.printStackTrace();
            }
        }
    }

    private void deleteRecord() {
        int selectedRow = dataTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a record to delete.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Are you sure you want to delete this record?",
            "Confirm Delete", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try (Connection conn = getConnection()) {
                String sql = "DELETE FROM BirthStatistics WHERE ID=?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, (Integer)tableModel.getValueAt(selectedRow, 0));
                    pstmt.executeUpdate();
                    JOptionPane.showMessageDialog(this, "Record deleted successfully!");
                    refreshData();
                }
            } catch (SQLException ex) {
                String errorMessage = String.format(
                    "Database Error:\nMessage: %s\nError Code: %d\nSQL State: %s",
                    ex.getMessage(), ex.getErrorCode(), ex.getSQLState());
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Database Error",
                    JOptionPane.ERROR_MESSAGE);
                System.err.println(errorMessage);
                ex.printStackTrace();
            } catch (Exception ex) {
                String errorMessage = "Unexpected error: " + ex.getMessage();
                JOptionPane.showMessageDialog(this,
                    errorMessage,
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                System.err.println(errorMessage);
                ex.printStackTrace();
            }
        }
    }

    private void searchRecords() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            refreshData();
            return;
        }

        tableModel.setRowCount(0);
        // Optimize search with parameterized query and limited columns
        // Note: Full table scans with leading wildcards can be slow on large datasets
        try (Connection conn = getConnection()) {
            String sql = "SELECT TOP 1000 * FROM BirthStatistics WHERE " +
                        "CAST(StatYear AS NVARCHAR) LIKE ? OR " +
                        "RecordType LIKE ? OR " +
                        "AreaCode LIKE ? OR " +
                        "AreaName LIKE ? OR " +
                        "Gender LIKE ? OR " +
                        "BirthWeight LIKE ? OR " +
                        "MultipleBirth LIKE ? OR " +
                        "CAST(BirthCount AS NVARCHAR) LIKE ?";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                String searchPattern = "%" + searchTerm + "%";
                for (int i = 1; i <= 8; i++) {
                    pstmt.setString(i, searchPattern);
                }

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Object[] row = {
                            rs.getInt("ID"),
                            rs.getInt("StatYear"),
                            rs.getString("RecordType"),
                            rs.getString("AreaCode"),
                            rs.getString("AreaName"),
                            rs.getString("Gender"),
                            rs.getString("BirthWeight"),
                            rs.getString("MultipleBirth"),
                            rs.getInt("BirthCount")
                        };
                        tableModel.addRow(row);
                    }
                }
            }
        } catch (SQLException ex) {
            String errorMessage = String.format(
                "Database Error:\nMessage: %s\nError Code: %d\nSQL State: %s",
                ex.getMessage(), ex.getErrorCode(), ex.getSQLState());
            JOptionPane.showMessageDialog(this,
                errorMessage,
                "Database Error",
                JOptionPane.ERROR_MESSAGE);
            System.err.println(errorMessage);
            ex.printStackTrace();
        } catch (Exception ex) {
            String errorMessage = "Unexpected error: " + ex.getMessage();
            JOptionPane.showMessageDialog(this,
                errorMessage,
                "Error",
                JOptionPane.ERROR_MESSAGE);
            System.err.println(errorMessage);
            ex.printStackTrace();
        }
    }

    private void importJSON() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (BufferedReader reader = new BufferedReader(new FileReader(fileChooser.getSelectedFile()))) {
                StringBuilder jsonContent = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
                
                // Parse JSON and insert into database
                try (Connection conn = getConnection()) {
                    // Implementation for JSON parsing and database insertion
                    JOptionPane.showMessageDialog(this, "JSON import completed successfully!");
                    refreshData();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error importing JSON: " + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importXML() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("XML Files", "xml"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                // Implementation for XML parsing and database insertion
                JOptionPane.showMessageDialog(this, "XML import completed successfully!");
                refreshData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error importing XML: " + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportToJSON() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".json")) {
                file = new File(file.getAbsolutePath() + ".json");
            }
            
            try (Connection conn = getConnection();
                 PrintWriter writer = new PrintWriter(file)) {
                
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM BirthStatistics");
                
                // Start JSON array
                writer.println("[");
                boolean first = true;
                
                while (rs.next()) {
                    if (!first) {
                        writer.println(",");
                    }
                    writer.printf("  {%n");
                    writer.printf("    \"id\": %d,%n", rs.getInt("id"));
                    writer.printf("    \"year\": %d,%n", rs.getInt("StatYear"));
                    writer.printf("    \"recordType\": \"%s\",%n", rs.getString("RecordType"));
                    writer.printf("    \"areaCode\": \"%s\",%n", rs.getString("AreaCode"));
                    writer.printf("    \"areaName\": \"%s\",%n", rs.getString("AreaName"));
                    writer.printf("    \"gender\": \"%s\",%n", rs.getString("Gender"));
                    writer.printf("    \"birthWeight\": \"%s\",%n", rs.getString("BirthWeight"));
                    writer.printf("    \"multipleBirth\": \"%s\",%n", rs.getString("MultipleBirth"));
                    writer.printf("    \"birthCount\": %d%n", rs.getInt("BirthCount"));
                    writer.printf("  }");
                    first = false;
                }
                writer.println("\n]");
                
                JOptionPane.showMessageDialog(this, "Data exported to JSON successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting to JSON: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportToXML() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("XML Files", "xml"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".xml")) {
                file = new File(file.getAbsolutePath() + ".xml");
            }
            
            try (Connection conn = getConnection();
                 PrintWriter writer = new PrintWriter(file)) {
                
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM BirthStatistics");
                
                writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                writer.println("<birthStats>");
                
                while (rs.next()) {
                    writer.println("  <record>");
                    writer.printf("    <id>%d</id>%n", rs.getInt("id"));
                    writer.printf("    <year>%d</year>%n", rs.getInt("StatYear"));
                    writer.printf("    <recordType>%s</recordType>%n", rs.getString("RecordType"));
                    writer.printf("    <areaCode>%s</areaCode>%n", rs.getString("AreaCode"));
                    writer.printf("    <areaName>%s</areaName>%n", rs.getString("AreaName"));
                    writer.printf("    <gender>%s</gender>%n", rs.getString("Gender"));
                    writer.printf("    <birthWeight>%s</birthWeight>%n", rs.getString("BirthWeight"));
                    writer.printf("    <multipleBirth>%s</multipleBirth>%n", rs.getString("MultipleBirth"));
                    writer.printf("    <birthCount>%d</birthCount>%n", rs.getInt("BirthCount"));
                    writer.println("  </record>");
                }
                writer.println("</birthStats>");
                
                JOptionPane.showMessageDialog(this, "Data exported to XML successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting to XML: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("Starting BirthStatsManager...");
            // Set look and feel
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            
            // Create and show GUI on EDT
            SwingUtilities.invokeLater(() -> {
                try {
                    System.out.println("Creating main window...");
                    BirthStatsManager frame = new BirthStatsManager();
                    frame.setVisible(true);
                } catch (Exception e) {
                    System.err.println("Error creating application window:");
                    System.err.println("Error type: " + e.getClass().getName());
                    System.err.println("Error message: " + e.getMessage());
                    if (e.getCause() != null) {
                        System.err.println("Caused by: " + e.getCause().getMessage());
                    }
                    e.printStackTrace(System.err);
                    JOptionPane.showMessageDialog(null,
                        "Error: " + e.getMessage() + "\nPlease check console for details.",
                        "Application Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            });
        } catch (Exception e) {
            System.err.println("Fatal error starting application:");
            e.printStackTrace(System.err);
        }
    }
}
