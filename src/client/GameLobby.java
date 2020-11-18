package client;

import client.exceptions.BrokenServerConnection;
import client.networking.ClientConnection;
import common.GameListing;
import common.Info;
import protocol.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

public class GameLobby extends JFrame {

    public static final ConsoleHandler CONSOLE_HANDLER = new ConsoleHandler();
    public static FileHandler FILE_HANDLER = null;
    private Info info = new Info();
    private JPanel outerPanel = new JPanel(new BorderLayout());
    private JPanel innerPanel = new JPanel(new BorderLayout());

    static {
        try {
            FILE_HANDLER = new FileHandler(
                    "%h/daifugo-server.log",
                    true
            );
            System.setProperty(
                    "java.util.logging.SimpleFormatter.format",
                    "[%4$s] (%1$ta %1$tF %1$tT %1$tZ) - %3$s:  %5$s %n"
            );
            FILE_HANDLER.setFormatter(new SimpleFormatter());
            CONSOLE_HANDLER.setFormatter(new SimpleFormatter());

            // Setting default log level to finest, overridden by local LOGGER object
            FILE_HANDLER.setLevel(Level.ALL);
            CONSOLE_HANDLER.setLevel(Level.ALL);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static final Logger LOGGER = Logger.getLogger(
            "Client"
    );


    private final String[] columnNames = {
            "ID",
            "Game name",
            "Owner",
            "Players",
            "Access",
            ""
    };
    private final DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 5; // only last column, needed for button to work
        }
    };
    private final JTable gamesTable = new JTable(tableModel);
    private final int window_height = 1000;
    private final int window_width = 1000;
    private final int MAX_PLAYERS = 8;
    private String playerName;
    private final List<GameListing> gameList = new ArrayList<>();
    private ClientConnection conn = null;
    private int latency = 0;
    private String serverAddress = "localhost"; // Default server address, will be changed through settings etc
    private volatile boolean connectionOK = false;
    private JTextField newServerAddressTextField = new JTextField(serverAddress);
    private ScheduledExecutorService heartbeatExecutor;
    private JLabel latencyLabel = new JLabel();
    private JButton joinGameButton = new JButton();
    private JFrame pwFrame = new JFrame("Join game");
    private String playerToken;

    /* Lobby Panels */
    JPanel newGamePanel = new JPanel();
    JPanel statusBar = new JPanel();
    JPanel settingsPanel = new JPanel();
    JPanel controlPanel = new JPanel();
    JScrollPane sp = new JScrollPane(gamesTable);

    public GameLobby() {

        try {
            conn = new ClientConnection(serverAddress);
            Message response = conn.sendMessage(new Message(MessageType.CONNECT));
            if (response.isError()) {
                LOGGER.warning("Failed to connect with session: " + response.getErrorMessage());
            } else {
                IdentityResponse tmp = (IdentityResponse) response;
                playerToken = tmp.getToken();
                playerName = tmp.getNick();
                connectionOK = true;
            }
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.warning("ERROR: Failed to connect!");
            connectToServerFrame();
        }

        while(!connectionOK){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        LOGGER.info("Connected to server " + serverAddress);



        /* Create window */
        setSize(window_width,window_height);
        setLayout(new BorderLayout());
        setTitle("Daifugo");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        /** File menu */
        JMenuBar topMenuBar = new JMenuBar();
        topMenuBar.setSize(window_width, 100);

        JMenu fileMenu = new JMenu("File");
        JMenuItem menuItemExit = new JMenuItem(new AbstractAction("Exit"){
            public void actionPerformed(ActionEvent e) {
                heartbeatExecutor.shutdown();    // kill latency thread
                synchronized (this) {
                    try {
                        conn.disconnect();
                    } catch (IOException | ClassNotFoundException ioException) {
                        ioException.printStackTrace();
                    }
                }
                System.exit(0);
            }
        });


        JMenu helpMenu = new JMenu("Help");
        JMenuItem menuItemHowToPlay = new JMenuItem(new AbstractAction("Show rules"){
            public void actionPerformed(ActionEvent e) {
                info.showRulesWindow();
            }
        });
        JMenu aboutMenu = new JMenu("About");
        JMenuItem menuItemAbout = new JMenuItem(new AbstractAction("About"){
            public void actionPerformed(ActionEvent e) {
                info.showAboutWindow();
            }
        });

        fileMenu.add(menuItemExit);
        helpMenu.add(menuItemHowToPlay);
        aboutMenu.add(menuItemAbout);

        topMenuBar.add(fileMenu);
        topMenuBar.add(helpMenu);
        topMenuBar.add(aboutMenu);
        topMenuBar.setMaximumSize(new Dimension(window_width, 50));
        add(topMenuBar);

        /**
         *  New game view
         ************************/
        newGamePanel.setSize(window_width,window_height);
        newGamePanel.setVisible(false);
        newGamePanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel newGameNameLabel = new JLabel("Name: ");
        JTextField newGameName = new JTextField("my new game");
        JPasswordField newGamePassword = new JPasswordField("");
        newGamePassword.setEnabled(false);
        JCheckBox newGamePrivateCheckbox = new JCheckBox("Private ");
        JButton newGameConfirmButton = new JButton("Confirm");
        JButton cancelNewGameButton = new JButton("Cancel");

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.ipadx = 40;
        gbc.weightx = 0.0;
        gbc.gridx = 0;
        gbc.gridy = 0;
        newGamePanel.add(newGameNameLabel, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 1;
        newGamePanel.add(newGameName, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 1;
        newGamePanel.add(newGamePrivateCheckbox, gbc);
        gbc.gridx = 1;
        newGamePanel.add(newGamePassword, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = 2;
        newGamePanel.add(newGameConfirmButton, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 3;
        gbc.gridx = 0;
        gbc.gridy = 3;
        newGamePanel.add(cancelNewGameButton, gbc);


        /**
         *  Settings view
         ************************/
        settingsPanel.setSize(window_width,window_height);
        settingsPanel.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();


        JLabel newNickNameLabel = new JLabel("Nickname: ");
        JTextField newNickNameTextField = new JTextField(playerName);
        JLabel newServerAddressLabel = new JLabel("Server address: ");
        JButton cancelSettingsButton = new JButton("Cancel");

        JLabel settingsConnectionFailedMessage = new JLabel("Connection failed");
        settingsConnectionFailedMessage.setForeground(new Color(255, 0, 0));
        settingsConnectionFailedMessage.setBounds(400, 50, 150,20);
        settingsConnectionFailedMessage.setVisible(false);


        JButton settingsConfirmButton = new JButton("Confirm");

        g.fill = GridBagConstraints.HORIZONTAL;
        g.ipadx = 40;
        g.weightx = 0.0;
        g.gridx = 0;
        g.gridy = 0;
        settingsPanel.add(newNickNameLabel, g);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 1;
        settingsPanel.add(newNickNameTextField, g);

        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0;
        g.gridy = 1;
        settingsPanel.add(newServerAddressLabel, g);
        g.gridx = 1;
        settingsPanel.add(newServerAddressTextField, g);

        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridwidth = 3;
        g.gridx = 0;
        g.gridy = 2;
        settingsPanel.add(settingsConfirmButton, g);

        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridwidth = 3;
        g.gridx = 0;
        g.gridy = 3;
        settingsPanel.add(cancelSettingsButton, g);

        /**
         *  Control bar
         ************************/

        controlPanel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        controlPanel.setBackground(Color.lightGray);

        JButton newGameButton = new JButton();
        newGameButton.setText("New Game");


        JButton refreshGamesButton = new JButton();
        refreshGamesButton.setText("Refresh");
        refreshGamesButton.addActionListener(e -> {
            refreshGamesList();
        });

        JLabel nickText = new JLabel();
        nickText.setText(playerName);

        JButton settingsButton = new JButton();
        settingsButton.setText("Settings");

        JTextField searchField = new JTextField();
        JLabel searchLabel = new JLabel("        Search:");



        c.fill = GridBagConstraints.LINE_START;
        c.weightx = 0.0;
        c.gridx = 0;
        c.gridy = 0;
        controlPanel.add(newGameButton, c);

        c.fill = GridBagConstraints.LINE_START;
        c.weightx = 0.0;
        c.gridx = 1;
        c.gridy = 0;
        controlPanel.add(refreshGamesButton, c);

        c.fill = GridBagConstraints.CENTER;
        c.weightx = 0.1;
        c.gridx = 2;
        c.gridy = 0;
        c.ipadx = 100;
        controlPanel.add(nickText, c);

        c.fill = GridBagConstraints.LINE_END;
        c.weightx = 0.0;
        c.gridx = 3;
        c.gridy = 0;
        c.ipadx = 0;
        controlPanel.add(settingsButton, c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.0;
        c.weighty = 0.1;
        c.gridwidth = 3;
        c.gridx = 1;
        c.gridy = 1;
        controlPanel.add(searchField, c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.0;
        c.weighty = 0.1;
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 1;
        controlPanel.add(searchLabel, c);



        /**
         *  Status bar
         ************************/
        statusBar.setBackground(Color.lightGray);
        statusBar.setLayout(new BorderLayout());


        startHeartbeat();
        latencyLabel.setText("Latency: " + latency + "  ");
        statusBar.add(latencyLabel, BorderLayout.LINE_END);
        /***********************/


        /**
         *  Table
         ************************/
        getGamesList();

        gamesTable.setModel(tableModel);
        TableRowColorRenderer colorRenderer = new TableRowColorRenderer();
        gamesTable.setDefaultRenderer(Object.class, colorRenderer);
        gamesTable.setAutoCreateRowSorter(true);
        TableRowSorter<TableModel> rowSorter = new TableRowSorter<>(gamesTable.getModel());
        gamesTable.setRowSorter(rowSorter);
        gamesTable.setRowHeight(30);


        /* add join buttons to table rows */
        gamesTable.getColumn("").setCellRenderer(new ButtonRenderer());
        gamesTable.getColumn("").setCellEditor(new ButtonEditor(joinGameButton, new JCheckBox()));

        /* Resize columns in table */
        gamesTable.getColumnModel().getColumn(0).setMaxWidth(25);
        gamesTable.getColumnModel().getColumn(0).setMinWidth(25);
        gamesTable.getColumnModel().getColumn(1).setPreferredWidth(300);
        gamesTable.getColumnModel().getColumn(1).setMinWidth(100);
        gamesTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        gamesTable.getColumnModel().getColumn(2).setMinWidth(50);
        gamesTable.getColumnModel().getColumn(3).setMaxWidth(100);
        gamesTable.getColumnModel().getColumn(3).setMinWidth(50);
        gamesTable.getColumnModel().getColumn(4).setMaxWidth(100);
        gamesTable.getColumnModel().getColumn(4).setMinWidth(50);
        gamesTable.getColumnModel().getColumn(5).setMinWidth(78);
        gamesTable.getColumnModel().getColumn(5).setMaxWidth(78);


        /**
         * Search bar functionality
         *************************/
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                String text = searchField.getText();

                if (text.trim().length() == 0) {
                    rowSorter.setRowFilter(null);
                } else {
                    rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                String text = searchField.getText();

                if (text.trim().length() == 0) {
                    rowSorter.setRowFilter(null);
                } else {
                    rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                throw new UnsupportedOperationException("Not supported");
            }
        });

        /**
         *  Button action listeners
         ************************/
        newGamePrivateCheckbox.addActionListener(e -> {
            newGamePassword.setEnabled(newGamePrivateCheckbox.isSelected());

        });

        newGameConfirmButton.addActionListener(e -> {
            char[] pw;
            if (newGamePrivateCheckbox.isSelected() && newGamePassword.getPassword().length > 0){
                pw = newGamePassword.getPassword();
            } else {
                pw = null;
            }
            createNewGame(newGameName.getText(), pw);
            sp.setVisible(false);
        });

        cancelNewGameButton.addActionListener(e -> {
            // Reset new game view
            newGameName.setText("my new game");
            newGamePassword.setText("");
            newGamePrivateCheckbox.setSelected(false);

            showLobby(true);    // reset view back to default lobby view
        });

        cancelSettingsButton.addActionListener(e -> {
            // reset settings view
            newNickNameTextField.setText(playerName);
            newServerAddressTextField.setText(serverAddress);

            showLobby(true);    // reset view back to default lobby view
        });

        settingsConfirmButton.addActionListener(e -> {
            boolean allSettingsOK;
            if (!newServerAddressTextField.getText().equals(serverAddress)) {
                serverAddress = newServerAddressTextField.getText();
                connectionOK = false;
                try {
                    conn = new ClientConnection(serverAddress);
                    connectionOK = true;
                } catch (IOException a) {
                    LOGGER.warning("ERROR: Failed to connect!");
                    connectToServerFrame();
                }
            }
            allSettingsOK = connectionOK;

            if (!newNickNameTextField.getText().equals(playerName)){
                try {
                    String newNick = newNickNameTextField.getText();
                    Message response;
                    synchronized (this) {
                        response = conn.sendMessage(
                                new UpdateNickMessage(playerToken, newNick)
                        );
                    }
                    if (response.isError()){
                        JOptionPane.showMessageDialog(settingsConfirmButton, response.getErrorMessage());
                        allSettingsOK = false;
                        return;
                    }
                    IdentityResponse updatedNickResponse = (IdentityResponse) response;
                    playerName = updatedNickResponse.getNick();
                    nickText.setText(playerName);
                } catch (IOException | ClassNotFoundException ioException) {
                    ioException.printStackTrace();
                }
            }

            if (allSettingsOK){ // settings ok, exit settings view
                showLobby(true);
            }

        });

        newGameButton.addActionListener(e -> {
            showLobby(false);
            innerPanel.setVisible(true);
            newGamePanel.setVisible(true);
        });

        settingsButton.addActionListener(e -> {
            showLobby(false);
            innerPanel.setVisible(true);
            settingsPanel.setVisible(true);
        });

        joinGameButton.addActionListener(e -> {
            int gameNumber = Integer.parseInt(gamesTable.getValueAt(gamesTable.getSelectedRow(), 0).toString());
            int playerCount = Character.getNumericValue(gamesTable.getValueAt(gamesTable.getSelectedRow(), 3).toString().charAt(0));

            String gameID;
            gameID = gameList.get(gameNumber-1).getID();

            if (playerCount < 8){
                if (gamesTable.getValueAt(gamesTable.getSelectedRow(), 4).toString().equals("Private")) { // game is private
                    // show window for entering password
                    pwFrame.setLayout(null);
                    pwFrame.setSize(300,150);
                    pwFrame.setVisible(true);
                    pwFrame.setLocationRelativeTo(null);
                    JLabel pwToJoinLabel = new JLabel("Enter password");
                    JPasswordField pwToJoinField = new JPasswordField();
                    JButton pwEnterGameButton = new JButton("Join game");

                    pwToJoinLabel.setBounds(100,10, 200, 20);
                    pwToJoinField.setBounds(50,30, 200, 20);
                    pwEnterGameButton.setBounds(100, 75, 95, 20);

                    pwEnterGameButton.addActionListener(e1 -> {
                        joinGame(gameID, pwToJoinField.getPassword());

                    });

                    pwFrame.add(pwToJoinLabel);
                    pwFrame.add(pwToJoinField);
                    pwFrame.add(pwEnterGameButton);
                } else { // game is not private
                    joinGame(gameID, null);

                }

            } else {
                LOGGER.warning("game full!");
                JOptionPane.showMessageDialog(joinGameButton, "Game is full!");
            }
        });

        /* Handle close window */
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                heartbeatExecutor.shutdown();    // kill latency thread
                try {
                    synchronized (this) {
                        conn.disconnect();
                    }

                } catch (ClassNotFoundException | IOException e) {
                    LOGGER.warning(e.getMessage());
                }
                System.exit(0);
            }
        });


        innerPanel.setBounds(0,500,window_width, window_height);

        outerPanel.add(topMenuBar, BorderLayout.PAGE_START, 0);
        innerPanel.add(newGamePanel, 0);
        innerPanel.add(settingsPanel, 0);
        innerPanel.add(controlPanel, BorderLayout.PAGE_START, 0);
        innerPanel.add(sp, BorderLayout.CENTER, 0);
        innerPanel.add(statusBar, BorderLayout.PAGE_END, 0);
        innerPanel.setVisible(true);
        outerPanel.add(innerPanel);
        add(outerPanel);
        setVisible(true);
        setLocationRelativeTo(null);
        showLobby(true);
    }

    /**
     * Refreshes gamesList.
     * Clears the gamesTable, then fills it with updated games from server via getGamesList()
     */
    public void refreshGamesList(){
        // Remove all rows, to prevent duplicates and old games from displaying
        for (int row = tableModel.getRowCount()-1; row >= 0; row--) {
            tableModel.removeRow(row);
        }

        gameList.removeAll(gameList); // Clear gameList
        // Fill table again
        getGamesList();
    }

    /**
     * Fills the gamesList with the games from the server, adds them to the gamesTable
     */
    public void getGamesList(){
        LOGGER.info("Updating games list ...");
        try {
            Message response;
            synchronized (this) {
                response = conn.sendMessage(new Message(MessageType.GET_GAME_LIST));
                if (response.isError()) {
                    LOGGER.warning("Error getting game list: " + response.getErrorMessage());
                    return;
                }
            }

            GameListResponse listResponse = (GameListResponse) response;
            List<GameListing> gamesFromServer = listResponse.getGameList();
            int i = 1;
            for (GameListing listing : gamesFromServer) {
                GameListing game = new GameListing(listing.getID(), listing.getTitle(), listing.getOwner(), listing.getNumberOfPlayers(), listing.hasPassword(), listing.hasStarted());
                gameList.add(game);

                String p = "Public";
                if (listing.hasPassword()){
                    p = "Private";
                }
                Object[] gameToTable = {i,listing.getTitle(), listing.getOwner(), listing.getNumberOfPlayers() + " / " + MAX_PLAYERS, p, "Join"};
                tableModel.addRow(gameToTable);
                i++;
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates an instance of a new game, adds it to the server. The new game will be shown in the table when
     * refreshed.
     * @param gameName The name of the game that should be created.
     * @param gamePassword The password for the game that should be created. Use null if it shouldn't have any password.
     */
    public void createNewGame(String gameName, char[] gamePassword) {
        try {
            Message response;
            synchronized (this) {
                response = conn.sendMessage(new NewGameMessage(
                        gameName,
                        gamePassword
                ));
            }

            if (response.isError()){
                LOGGER.warning(response.getErrorMessage());
                return;
            }

            setWaitingCursor(true);

            heartbeatExecutor.shutdown();
            GameStateResponse tmp = (GameStateResponse) response;
            GameStateTracker tracker;
            synchronized (this) {
                tracker = new ServerTracker(
                        conn,
                        tmp.getState()
                );
            }

            showLobby(false);

            Table playTable = new Table(window_width, window_height, tracker, this);
            playTable.setBounds(0,0, getWidth(), getHeight());
            playTable.setVisible(true);
            playTable.setBounds(0,0,window_width,window_height);
            outerPanel.add(playTable, 1);
            playTable.repaint();
            playTable.revalidate();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a HeartbeatMessage to the server and calculates the time it took.
     * @return the time between your heartbeat request was sent from you and received by the server.
     */
    public int getLatency() throws BrokenServerConnection {
        synchronized (this) {
            int l = 0;
            long timestampBefore = Instant.now().toEpochMilli();
            try {
                Message response;
                response = conn.sendMessage(
                        new HeartbeatMessage(timestampBefore)
                );
                if (response.isError()){
                    LOGGER.warning( "Failed to get heartbeat response: " + response.getErrorMessage());
                    throw new BrokenServerConnection();
                }
                l = (int) (Instant.now().toEpochMilli() - timestampBefore);
            } catch (IOException | ClassNotFoundException e) {
                LOGGER.warning("Exception on sending heartbeat: " + e.getMessage());
                throw new BrokenServerConnection();
            }
            return l;
        }
    }

    /**
     * A popup frame that informs that the connection has failed, and provides a text field to enter a new address.
     * When clicking the confirm button, a new connection to the server is established. If this fails, the frame re-appears
     * for another try until a connection is successful.
     */
    public void connectToServerFrame() {
        JFrame connectionFrame = new JFrame();
        connectionFrame.setLayout(null);
        connectionFrame.setSize(400,200);

        JLabel connectionFailedMessage = new JLabel("Connection failed");
        connectionFailedMessage.setForeground(new Color(255, 0, 0));
        connectionFailedMessage.setBounds(connectionFrame.getWidth()/2 - 50, 0, 100, 50);

        JLabel enterServerAddressMessage = new JLabel("Please enter a valid server address");
        enterServerAddressMessage.setBounds(connectionFrame.getWidth()/2 - 102, 20, 300, 50);


        JTextField addressTextArea = new JTextField(serverAddress);
        addressTextArea.setEditable(true);
        addressTextArea.setBounds( connectionFrame.getWidth()/2 - 100, connectionFrame.getHeight()/3, 200,20);


        JButton confirmButton = new JButton("Confirm");
        confirmButton.setBounds(connectionFrame.getWidth()/2 - 50, connectionFrame.getHeight()/2, 100, 40);
        confirmButton.addActionListener(a -> {
            serverAddress = addressTextArea.getText();
            try {
                conn = new ClientConnection(serverAddress);
                Message response;
                response = conn.sendMessage(new Message(MessageType.CONNECT));
                if (response.isError()){
                    LOGGER.warning("ERROR: " + response.getErrorMessage());
                } else {
                    IdentityResponse tmp = (IdentityResponse) response;
                    playerToken = tmp.getToken();
                    playerName = tmp.getNick();
                    connectionOK = true;
                    newServerAddressTextField.setText(serverAddress);
                    connectionFrame.dispose(); // destroy frame
                }
            } catch (IOException | ClassNotFoundException e) {
                connectionFrame.dispose(); // destroy frame
                connectToServerFrame();   // make new frame, try again
            }
        });

        /* Handle close button */
        connectionFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                System.exit(0);
            }
        });

        connectionFrame.add(connectionFailedMessage);
        connectionFrame.add(enterServerAddressMessage);
        connectionFrame.add(addressTextArea);
        connectionFrame.add(confirmButton);
        connectionFrame.setLocationRelativeTo(null);
        connectionFrame.setVisible(true);

    }

    /**
     * Resets lobby view to default view if true.
     * Hides all lobby elements if false.
     * @param b reset lobby view if true, hide all lobby elements if false
     */
    public void showLobby(boolean b){
        if (b){
            innerPanel.setVisible(true);
            sp.setVisible(true);
            statusBar.setVisible(true);
            newGamePanel.setVisible(false);
            settingsPanel.setVisible(false);
            controlPanel.setVisible(true);
        } else {
            innerPanel.setVisible(false);
            sp.setVisible(false);
            statusBar.setVisible(false);
            newGamePanel.setVisible(false);
            settingsPanel.setVisible(false);
            controlPanel.setVisible(false);
        }
    }

    private void quitClient() {
        JOptionPane.showMessageDialog(
                this,
                "Lost connection to server, the program will now exit.",
                "Connection lost",
                JOptionPane.ERROR_MESSAGE
        );
        System.exit(1);
    }

    public void startHeartbeat(){
        Runnable latencyRunnable = () -> {
            try {
                latency = getLatency();
            } catch (BrokenServerConnection ignored) {
                quitClient();
            }
            latencyLabel.setText("Latency: " + latency + "  ");
        };
        heartbeatExecutor = Executors.newScheduledThreadPool(1);
        heartbeatExecutor.scheduleAtFixedRate(latencyRunnable, 1, 1, TimeUnit.SECONDS);
    }


    /**
     * Connects to a game on server, hides lobby view and shows game view
     * @param gameID Game UID to connect to
     * @param password Password for the game. Use null if it isn't password protected
     */
    public void joinGame(String gameID, char[] password){
        try {
            GameStateTracker tracker;
            synchronized (this) {
                Message response;
                response = conn.sendMessage(new JoinGameRequest(gameID, password));
                if (response.isError()) {
                    if (response.getMessageType() == MessageType.PASSWORD_ERROR) {
                        JOptionPane.showMessageDialog(joinGameButton, "Wrong password!");
                    }
                    LOGGER.warning("ERROR: " + response.getErrorMessage());
                }


                heartbeatExecutor.shutdown();
                GameStateResponse tmp = (GameStateResponse) response;
                tracker = new ServerTracker(
                        conn,
                        tmp.getState()
                );
            }

            setWaitingCursor(true);

            showLobby(false);
            pwFrame.setVisible(false);

            Table playTable = new Table(window_width, window_height, tracker, this);
            playTable.setBounds(0,0, getWidth(), getHeight());
            playTable.setVisible(true);
            playTable.setBounds(0,0,window_width,window_height);
            outerPanel.add(playTable, 1);
            playTable.repaint();
            playTable.revalidate();

        } catch (IOException | ClassNotFoundException ioException) {
            ioException.printStackTrace();
        }
    }

    public void setWaitingCursor(boolean waiting) {
        if (waiting){
            setCursor(new Cursor(Cursor.WAIT_CURSOR));
        } else {
            setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }

    }
}

