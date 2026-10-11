import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.List; // resolves ambiguity between java.awt.List and java.util.List

/**
 * Library Management System - single-file version.
 *
 * Compile:  javac LibraryManagementSystem.java
 * Run:      java LibraryManagementSystem
 *
 * Default login: admin / admin123
 * Data files (books.txt, issued.txt, users.txt) are created in the working directory.
 */
public class LibraryManagementSystem {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new LoginPage().setVisible(true));
    }
}

// =====================================================================
// Book
// =====================================================================

/** Represents a single book in the library catalog. */
class Book {
    private final int id;
    private String title;
    private String author;
    private String isbn;
    private int totalCopies;
    private int availableCopies;

    public Book(int id, String title, String author, String isbn, int totalCopies, int availableCopies) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }

    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public void setTotalCopies(int totalCopies) { this.totalCopies = totalCopies; }
    public void setAvailableCopies(int availableCopies) { this.availableCopies = availableCopies; }

    /** Serializes this book to a single pipe-delimited line for file storage. */
    public String toFileLine() {
        return id + "|" + title + "|" + author + "|" + isbn + "|" + totalCopies + "|" + availableCopies;
    }

    /** Parses a book back out of a pipe-delimited file line. */
    public static Book fromFileLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 6) return null;
        try {
            return new Book(
                    Integer.parseInt(parts[0]),
                    parts[1],
                    parts[2],
                    parts[3],
                    Integer.parseInt(parts[4]),
                    Integer.parseInt(parts[5])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

// =====================================================================
// IssueRecord
// =====================================================================

/** Represents an active book issue: which book, to whom, and since when. */
class IssueRecord {
    private final int recordId;
    private final int bookId;
    private final String memberName;
    private final LocalDate issueDate;

    public IssueRecord(int recordId, int bookId, String memberName, LocalDate issueDate) {
        this.recordId = recordId;
        this.bookId = bookId;
        this.memberName = memberName;
        this.issueDate = issueDate;
    }

    public int getRecordId() { return recordId; }
    public int getBookId() { return bookId; }
    public String getMemberName() { return memberName; }
    public LocalDate getIssueDate() { return issueDate; }

    /** Serializes this record to a single pipe-delimited line for file storage. */
    public String toFileLine() {
        return recordId + "|" + bookId + "|" + memberName + "|" + issueDate;
    }

    /** Parses a record back out of a pipe-delimited file line. */
    public static IssueRecord fromFileLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 4) return null;
        try {
            return new IssueRecord(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    parts[2],
                    LocalDate.parse(parts[3])
            );
        } catch (Exception e) {
            return null;
        }
    }
}

// =====================================================================
// Library
// =====================================================================

/**
 * Manages the book catalog and issue/return records for the library.
 * Data is persisted to local text files (books.txt, issued.txt).
 */
class Library {

    private static final String BOOKS_FILE = "books.txt";
    private static final String ISSUED_FILE = "issued.txt";

    private final List<Book> books = new ArrayList<>();
    private final List<IssueRecord> issuedRecords = new ArrayList<>();
    private int nextBookId = 1;
    private int nextRecordId = 1;

    public Library() {
        loadBooks();
        loadIssuedRecords();
        seedSampleDataIfEmpty();
    }

    // ---------- Loading ----------

    private void loadBooks() {
        File file = new File(BOOKS_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                Book book = Book.fromFileLine(line);
                if (book != null) {
                    books.add(book);
                    nextBookId = Math.max(nextBookId, book.getId() + 1);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read books file: " + e.getMessage());
        }
    }

    private void loadIssuedRecords() {
        File file = new File(ISSUED_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                IssueRecord record = IssueRecord.fromFileLine(line);
                if (record != null) {
                    issuedRecords.add(record);
                    nextRecordId = Math.max(nextRecordId, record.getRecordId() + 1);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read issued records file: " + e.getMessage());
        }
    }

    private void seedSampleDataIfEmpty() {
        if (books.isEmpty()) {
            addBook("The Pragmatic Programmer", "Andrew Hunt", "9780135957059", 3);
            addBook("Clean Code", "Robert C. Martin", "9780132350884", 2);
            addBook("Effective Java", "Joshua Bloch", "9780134685991", 4);
        }
    }

    // ---------- Persistence ----------

    private void saveBooks() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(BOOKS_FILE))) {
            for (Book book : books) {
                writer.write(book.toFileLine());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Could not save books file: " + e.getMessage());
        }
    }

    private void saveIssuedRecords() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ISSUED_FILE))) {
            for (IssueRecord record : issuedRecords) {
                writer.write(record.toFileLine());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Could not save issued records file: " + e.getMessage());
        }
    }

    // ---------- Book operations ----------

    /** Adds a new book to the catalog. Returns the created Book. */
    public Book addBook(String title, String author, String isbn, int quantity) {
        Book book = new Book(nextBookId++, title, author, isbn, quantity, quantity);
        books.add(book);
        saveBooks();
        return book;
    }

    public List<Book> getAllBooks() {
        return Collections.unmodifiableList(books);
    }

    public Optional<Book> getBookById(int id) {
        return books.stream().filter(b -> b.getId() == id).findFirst();
    }

    /** Case-insensitive search across title, author, and ISBN. */
    public List<Book> searchBooks(String keyword) {
        String k = keyword.trim().toLowerCase();
        List<Book> results = new ArrayList<>();
        for (Book b : books) {
            if (b.getTitle().toLowerCase().contains(k)
                    || b.getAuthor().toLowerCase().contains(k)
                    || b.getIsbn().toLowerCase().contains(k)) {
                results.add(b);
            }
        }
        return results;
    }

    public List<Book> getAvailableBooks() {
        List<Book> results = new ArrayList<>();
        for (Book b : books) {
            if (b.getAvailableCopies() > 0) results.add(b);
        }
        return results;
    }

    // ---------- Issue / return operations ----------

    /** Issues one copy of the given book to a member. Returns the created record, or null if unavailable. */
    public IssueRecord issueBook(int bookId, String memberName) {
        Optional<Book> maybeBook = getBookById(bookId);
        if (maybeBook.isEmpty()) return null;
        Book book = maybeBook.get();
        if (book.getAvailableCopies() <= 0) return null;

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        IssueRecord record = new IssueRecord(nextRecordId++, bookId, memberName, LocalDate.now());
        issuedRecords.add(record);

        saveBooks();
        saveIssuedRecords();
        return record;
    }

    /** Returns a book by its issue record id. Returns true on success. */
    public boolean returnBook(int recordId) {
        IssueRecord toRemove = null;
        for (IssueRecord r : issuedRecords) {
            if (r.getRecordId() == recordId) {
                toRemove = r;
                break;
            }
        }
        if (toRemove == null) return false;

        Optional<Book> maybeBook = getBookById(toRemove.getBookId());
        maybeBook.ifPresent(b -> b.setAvailableCopies(Math.min(b.getTotalCopies(), b.getAvailableCopies() + 1)));

        issuedRecords.remove(toRemove);
        saveBooks();
        saveIssuedRecords();
        return true;
    }

    public List<IssueRecord> getActiveIssuedRecords() {
        return Collections.unmodifiableList(issuedRecords);
    }
}

// =====================================================================
// UserAuth
// =====================================================================

/**
 * Handles user registration/authentication.
 * Credentials are stored in users.txt as "username:sha256hash".
 */
class UserAuth {

    private static final String USER_FILE = "users.txt";
    private final Map<String, String> users = new HashMap<>(); // username -> hashed password

    public UserAuth() {
        loadUsers();
        // Seed a default admin account on first run so the app is usable immediately.
        if (users.isEmpty()) {
            registerUser("admin", "admin123");
        }
    }

    private void loadUsers() {
        File file = new File(USER_FILE);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    users.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read user file: " + e.getMessage());
        }
    }

    private void saveUser(String username, String hashedPassword) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_FILE, true))) {
            writer.write(username + ":" + hashedPassword);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Could not save user: " + e.getMessage());
        }
    }

    /** Registers a new user. Returns false if the username already exists. */
    public boolean registerUser(String username, String plainPassword) {
        if (username == null || username.isBlank() || plainPassword == null || plainPassword.isBlank()) {
            return false;
        }
        if (users.containsKey(username)) {
            return false;
        }
        String hashed = hashPassword(plainPassword);
        users.put(username, hashed);
        saveUser(username, hashed);
        return true;
    }

    /** Validates a login attempt. */
    public boolean validateLogin(String username, String plainPassword) {
        if (username == null || plainPassword == null) return false;
        String storedHash = users.get(username);
        if (storedHash == null) return false;
        return storedHash.equals(hashPassword(plainPassword));
    }

    public boolean userExists(String username) {
        return users.containsKey(username);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }
}

// =====================================================================
// LoginPage
// =====================================================================

/** Login page for the Library Management System (Swing). */
class LoginPage extends JFrame {

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel statusLabel;
    private final UserAuth userAuth;

    public LoginPage() {
        userAuth = new UserAuth();

        setTitle("Library Management System - Login");
        setSize(560, 700);
        setMinimumSize(new Dimension(480, 620));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBackground(new Color(240, 244, 248));

        mainPanel.add(buildHeader(), BorderLayout.NORTH);
        mainPanel.add(buildFormPanel(), BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setBackground(new Color(30, 60, 114));
        header.setPreferredSize(new Dimension(560, 140));
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Library Management System");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subLabel = new JLabel("Please sign in to continue");
        subLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        subLabel.setForeground(new Color(210, 220, 235));
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(Box.createVerticalGlue());
        header.add(titleLabel);
        header.add(Box.createRigidArea(new Dimension(0, 6)));
        header.add(subLabel);
        header.add(Box.createVerticalGlue());

        return header;
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(new Color(240, 244, 248));
        panel.setLayout(new GridBagLayout());
        panel.setBorder(new EmptyBorder(40, 70, 40, 70));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 0, 10, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        JLabel userLabel = new JLabel("Username");
        userLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        gbc.gridy = 0;
        panel.add(userLabel, gbc);

        usernameField = new JTextField();
        styleField(usernameField);
        gbc.gridy = 1;
        panel.add(usernameField, gbc);

        JLabel passLabel = new JLabel("Password");
        passLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        gbc.gridy = 2;
        panel.add(passLabel, gbc);

        passwordField = new JPasswordField();
        styleField(passwordField);
        gbc.gridy = 3;
        panel.add(passwordField, gbc);

        // Pressing Enter in either field triggers login
        ActionListener loginAction = e -> attemptLogin();
        usernameField.addActionListener(loginAction);
        passwordField.addActionListener(loginAction);

        AppButton loginButton = new AppButton("LOGIN", true);
        loginButton.setFont(new Font("SansSerif", Font.BOLD, 18));
        loginButton.setPreferredSize(new Dimension(0, 52));
        loginButton.addActionListener(loginAction);
        getRootPane().setDefaultButton(loginButton);
        gbc.gridy = 4;
        gbc.insets = new Insets(26, 0, 10, 0);
        panel.add(loginButton, gbc);

        AppButton registerButton = new AppButton("Create New Account", false);
        registerButton.setFont(new Font("SansSerif", Font.BOLD, 15));
        registerButton.setPreferredSize(new Dimension(0, 46));
        registerButton.addActionListener(e -> openRegisterDialog());
        gbc.gridy = 5;
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(registerButton, gbc);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        statusLabel.setForeground(new Color(200, 40, 40));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 6;
        gbc.insets = new Insets(10, 0, 0, 0);
        panel.add(statusLabel, gbc);

        JLabel hint = new JLabel("Default account: admin / admin123");
        hint.setFont(new Font("SansSerif", Font.ITALIC, 13));
        hint.setForeground(Color.GRAY);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 7;
        gbc.insets = new Insets(4, 0, 0, 0);
        panel.add(hint, gbc);

        return panel;
    }

    private void styleField(JTextField field) {
        field.setFont(new Font("SansSerif", Font.PLAIN, 17));
        field.setPreferredSize(new Dimension(0, 48));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(180, 190, 200), 1),
                new EmptyBorder(4, 12, 4, 12)
        ));
    }

    private void attemptLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both username and password.");
            return;
        }

        if (userAuth.validateLogin(username, password)) {
            statusLabel.setForeground(new Color(30, 130, 76));
            statusLabel.setText("Login successful!");
            openDashboard(username);
        } else {
            statusLabel.setForeground(new Color(200, 40, 40));
            statusLabel.setText("Invalid username or password.");
            passwordField.setText("");
        }
    }

    private void openDashboard(String username) {
        SwingUtilities.invokeLater(() -> {
            try {
                Dashboard dashboard = new Dashboard(username);
                dashboard.setVisible(true);
                dashboard.toFront();
                dashboard.requestFocus();
                this.dispose();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Login succeeded, but the dashboard could not open:\n" + ex,
                        "Error Opening Dashboard", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void openRegisterDialog() {
        JTextField newUser = new JTextField(25);
        JPasswordField newPass = new JPasswordField(25);
        JPasswordField confirmPass = new JPasswordField(25);

        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.add(new JLabel("Username:"));
        panel.add(newUser);
        panel.add(new JLabel("Password:"));
        panel.add(newPass);
        panel.add(new JLabel("Confirm Password:"));
        panel.add(confirmPass);

        int result = JOptionPane.showConfirmDialog(
                this, panel, "Create New Account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            String username = newUser.getText().trim();
            String pass1 = new String(newPass.getPassword());
            String pass2 = new String(confirmPass.getPassword());

            if (username.isEmpty() || pass1.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username and password cannot be empty.",
                        "Registration Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!pass1.equals(pass2)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.",
                        "Registration Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (userAuth.userExists(username)) {
                JOptionPane.showMessageDialog(this, "Username already exists.",
                        "Registration Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            boolean created = userAuth.registerUser(username, pass1);
            if (created) {
                JOptionPane.showMessageDialog(this, "Account created successfully. You can now log in.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Could not create account. Try again.",
                        "Registration Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

// =====================================================================
// Dashboard
// =====================================================================

/**
 * Main dashboard shown after a successful login.
 * Actions: Add Book, View Books, Issue Book, Return Book, Search Book, Exit.
 */
class Dashboard extends JFrame {

    private static final Color PRIMARY = new Color(30, 60, 114);
    private static final Color BACKGROUND = new Color(240, 244, 248);

    private final Library library;
    private final String username;

    public Dashboard(String username) {
        this.username = username;
        this.library = new Library();

        setTitle("Library Management System - Dashboard");
        setSize(1100, 720);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH); // fill the desktop screen

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmExit();
            }
        });

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BACKGROUND);
        content.add(buildHeader(), BorderLayout.NORTH);
        content.add(buildMenuPanel(), BorderLayout.CENTER);
        content.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(content);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setBackground(PRIMARY);
        header.setPreferredSize(new Dimension(1100, 130));
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("LIBRARY MANAGEMENT SYSTEM");
        title.setFont(new Font("SansSerif", Font.BOLD, 34));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Welcome, " + username);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 18));
        sub.setForeground(new Color(210, 220, 235));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(Box.createVerticalGlue());
        header.add(title);
        header.add(Box.createRigidArea(new Dimension(0, 6)));
        header.add(sub);
        header.add(Box.createVerticalGlue());
        return header;
    }

    private JPanel buildMenuPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 30, 30));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(50, 80, 50, 80));

        panel.add(makeMenuButton("Add Book", e -> showAddBookDialog()));
        panel.add(makeMenuButton("View Books", e -> showViewBooksDialog()));
        panel.add(makeMenuButton("Issue Book", e -> showIssueBookDialog()));
        panel.add(makeMenuButton("Return Book", e -> showReturnBookDialog()));
        panel.add(makeMenuButton("Search Book", e -> showSearchBookDialog()));
        panel.add(makeMenuButton("Exit", e -> confirmExit()));

        return panel;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel();
        footer.setBackground(BACKGROUND);
        footer.setPreferredSize(new Dimension(1100, 40));
        JLabel label = new JLabel(library.getAllBooks().size() + " titles in catalog");
        label.setFont(new Font("SansSerif", Font.PLAIN, 14));
        label.setForeground(Color.GRAY);
        footer.add(label);
        return footer;
    }

    private JButton makeMenuButton(String text, ActionListener action) {
        AppButton button = new AppButton(text, false);
        button.setFont(new Font("SansSerif", Font.BOLD, 22));
        button.addActionListener(action);
        return button;
    }

    // ---------- Add Book ----------

    private void showAddBookDialog() {
        JTextField titleField = new JTextField(30);
        JTextField authorField = new JTextField(30);
        JTextField isbnField = new JTextField(30);
        JTextField qtyField = new JTextField(30);

        JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
        form.add(new JLabel("Title:"));
        form.add(titleField);
        form.add(new JLabel("Author:"));
        form.add(authorField);
        form.add(new JLabel("ISBN:"));
        form.add(isbnField);
        form.add(new JLabel("Quantity:"));
        form.add(qtyField);

        int result = JOptionPane.showConfirmDialog(this, form, "Add Book",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String title = titleField.getText().trim();
        String author = authorField.getText().trim();
        String isbn = isbnField.getText().trim();
        String qtyText = qtyField.getText().trim();

        if (title.isEmpty() || author.isEmpty() || qtyText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title, author, and quantity are required.",
                    "Missing Information", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(qtyText);
            if (quantity < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Quantity must be a positive whole number.",
                    "Invalid Quantity", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Book book = library.addBook(title, author, isbn.isEmpty() ? "N/A" : isbn, quantity);
        JOptionPane.showMessageDialog(this,
                "Book added successfully.\nBook ID: " + book.getId(),
                "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- View Books ----------

    private void showViewBooksDialog() {
        JDialog dialog = new JDialog(this, "View Books", true);
        dialog.setSize(1000, 600);
        dialog.setLocationRelativeTo(this);

        JTable table = new JTable(buildBookTableModel(library.getAllBooks()));
        table.setFont(new Font("SansSerif", Font.PLAIN, 16));
        table.setRowHeight(32);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 16));

        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private DefaultTableModel buildBookTableModel(List<Book> books) {
        String[] columns = {"ID", "Title", "Author", "ISBN", "Total", "Available"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (Book b : books) {
            model.addRow(new Object[]{
                    b.getId(), b.getTitle(), b.getAuthor(), b.getIsbn(),
                    b.getTotalCopies(), b.getAvailableCopies()
            });
        }
        return model;
    }

    // ---------- Issue Book ----------

    private void showIssueBookDialog() {
        List<Book> available = library.getAvailableBooks();
        if (available.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No books are currently available to issue.",
                    "Nothing Available", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JComboBox<String> bookCombo = new JComboBox<>();
        for (Book b : available) {
            bookCombo.addItem(b.getId() + " - " + b.getTitle() + " (" + b.getAvailableCopies() + " available)");
        }
        JTextField memberField = new JTextField(30);

        JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
        form.add(new JLabel("Select Book:"));
        form.add(bookCombo);
        form.add(new JLabel("Member Name:"));
        form.add(memberField);

        int result = JOptionPane.showConfirmDialog(this, form, "Issue Book",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        String memberName = memberField.getText().trim();
        if (memberName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Member name is required.",
                    "Missing Information", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int selectedIndex = bookCombo.getSelectedIndex();
        Book selectedBook = available.get(selectedIndex);

        IssueRecord record = library.issueBook(selectedBook.getId(), memberName);
        if (record != null) {
            JOptionPane.showMessageDialog(this,
                    "Book issued successfully.\nIssue Record ID: " + record.getRecordId()
                            + "\n(Keep this ID to process the return later.)",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Unable to issue this book. It may no longer be available.",
                    "Issue Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------- Return Book ----------

    private void showReturnBookDialog() {
        List<IssueRecord> active = library.getActiveIssuedRecords();
        if (active.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no books currently issued.",
                    "Nothing to Return", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JComboBox<String> recordCombo = new JComboBox<>();
        for (IssueRecord r : active) {
            String bookTitle = library.getBookById(r.getBookId())
                    .map(Book::getTitle).orElse("Unknown Book");
            recordCombo.addItem("Record #" + r.getRecordId() + " - " + bookTitle
                    + " (" + r.getMemberName() + ", issued " + r.getIssueDate() + ")");
        }

        JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
        form.add(new JLabel("Select Issued Book to Return:"));
        form.add(recordCombo);

        int result = JOptionPane.showConfirmDialog(this, form, "Return Book",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return;

        IssueRecord selected = active.get(recordCombo.getSelectedIndex());
        boolean success = library.returnBook(selected.getRecordId());

        if (success) {
            JOptionPane.showMessageDialog(this, "Book returned successfully.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Unable to process the return.",
                    "Return Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------- Search Book ----------

    private void showSearchBookDialog() {
        JDialog dialog = new JDialog(this, "Search Book", true);
        dialog.setSize(1000, 620);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.setBorder(new EmptyBorder(10, 10, 10, 10));
        JTextField searchField = new JTextField();
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 16));
        AppButton searchButton = new AppButton("Search", true);
        searchButton.setFont(new Font("SansSerif", Font.BOLD, 15));
        searchButton.setPreferredSize(new Dimension(120, 38));
        JLabel searchLabel = new JLabel("Title / Author / ISBN:");
        searchLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        searchBar.add(searchLabel, BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
        searchBar.add(searchButton, BorderLayout.EAST);

        JTable resultsTable = new JTable(buildBookTableModel(library.getAllBooks()));
        resultsTable.setFont(new Font("SansSerif", Font.PLAIN, 16));
        resultsTable.setRowHeight(32);
        resultsTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 16));

        Runnable doSearch = () -> {
            String keyword = searchField.getText().trim();
            List<Book> results = keyword.isEmpty()
                    ? library.getAllBooks()
                    : library.searchBooks(keyword);
            resultsTable.setModel(buildBookTableModel(results));
        };

        searchButton.addActionListener(e -> doSearch.run());
        searchField.addActionListener(e -> doSearch.run());

        dialog.add(searchBar, BorderLayout.NORTH);
        dialog.add(new JScrollPane(resultsTable), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    // ---------- Exit ----------

    private void confirmExit() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to exit?",
                "Confirm Exit", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }
}

// =====================================================================
// AppButton
// =====================================================================

/**
 * Custom-painted button. Draws its own background and text so it looks the same
 * on every OS look-and-feel (fixes the faint/invisible "Login" text on Windows).
 *   primary = true  -> solid blue button with white text
 *   primary = false -> white button with blue border and blue text
 */
class AppButton extends JButton {

    private static final Color BLUE = new Color(30, 60, 114);
    private static final Color BLUE_HOVER = new Color(45, 85, 160);
    private static final Color BLUE_PRESSED = new Color(20, 42, 84);
    private static final Color LIGHT_HOVER = new Color(225, 232, 242);

    private final boolean primary;

    public AppButton(String text, boolean primary) {
        super(text);
        this.primary = primary;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setForeground(primary ? Color.WHITE : BLUE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        ButtonModel m = getModel();
        Color fill;
        if (primary) {
            fill = m.isPressed() ? BLUE_PRESSED : (m.isRollover() ? BLUE_HOVER : BLUE);
        } else {
            fill = (m.isPressed() || m.isRollover()) ? LIGHT_HOVER : Color.WHITE;
        }

        int w = getWidth(), h = getHeight();
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
        if (!primary) {
            g2.setColor(BLUE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(0, 0, w - 2, h - 2, 12, 12);
        }

        // Centered text, always drawn in the correct contrast color
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(getText())) / 2;
        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(primary ? Color.WHITE : BLUE);
        g2.drawString(getText(), tx, ty);
        g2.dispose();
    }
}
