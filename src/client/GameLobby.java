package client;

import javax.swing.*;
import java.awt.*;


public class GameLobby extends JFrame {
    public GameLobby() {
        int window_height = 1000;
        int window_width = 1000;


        /* Create window */
        setSize(window_width,window_height);
        setLayout(new BorderLayout());
        setTitle("Daifugo - Lobby");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);


        /* Control bar */
        JPanel controlPanel = new JPanel();
        controlPanel.setBounds(0, 0, window_width, window_height/20);
        controlPanel.setBackground(Color.lightGray);
        // TODO: buttons for new game etc
        JButton testButton = new JButton();
        testButton.setBounds(50, 50, 150, 40);
        testButton.setText("Test Button");
        controlPanel.add(testButton);





        /* Lobby table */
        // TODO: get games from server
        String[] columnNames = {
                "Lobby name",
                "Owner",
                "Players"
        };

        // TODO: Sample games, get from server instead
        String[][] games = {
                {"Game name 1", "Dr. Mundo", "2"},
                {"Super Game For Cool Guyz", "Teemo", "6" },
                {"Kosekroken", "Caitlyn", "4"}
        };

        JTable gamesTable = new JTable(games, columnNames);
        JScrollPane sp = new JScrollPane(gamesTable);


        add(controlPanel, BorderLayout.PAGE_START);
        add(sp, BorderLayout.CENTER);
        setVisible(true);
    }
}
