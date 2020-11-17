package client;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Table extends JPanel {

    private BufferedImage image;
    private final PlayersInformation playersInformation;
    private CardsOnTable cardsOnTable;
    private GameStateTracker stateTracker;

    private Void updateGUI() {
        playersInformation.indicateTurn();
        cardsOnTable.updateCardsOnTable();
        return null;
    }

    public Table(int f_width, int f_height, GameStateTracker sT, GameLobby gL) {
        this.stateTracker = sT; // TODO: Bytt ut med ordentlig tracker
        try {
            image = ImageIO.read(ClientMain.class.getResourceAsStream("/green_fabric.jpg"));       // Read the image
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        setLayout(null);

        // TODO: REMOVE LATER
        Player[] players = new Player[1];
        players[0] = new Player(
                "Mohammed Lee",
                "0",
                stateTracker.getHand("temp"),
                f_width /2,
                stateTracker
        );

        players[0].setBounds(
                (f_width /2) - ((f_width /2)/2) - 25,
                (f_height /2) + 100,
                f_width /2,
                (f_height /8) + 100
        );
        add(players[0]);

        playersInformation = new PlayersInformation(players, stateTracker);
        int pInfoWidth = 200;
        int pInfoHeight = 100;
        playersInformation.setBounds(50,50, pInfoWidth, pInfoHeight);
        add(playersInformation);

        int cardsOnTableWidth = 300, cardsOnTableHeight = 200;
        cardsOnTable = new CardsOnTable(cardsOnTableWidth, cardsOnTableHeight, stateTracker);
        cardsOnTable.setBounds(
                (f_width /2) - (cardsOnTableWidth/2),
                (f_height /3) - (cardsOnTableHeight/2),
                   cardsOnTableWidth, cardsOnTableHeight
        );
        add(cardsOnTable);


        stateTracker.registerCallback(this::updateGUI);

        JButton startBtn = new JButton("Start");
        startBtn.setBounds(50,200, 100,50);
        add(startBtn);
        startBtn.addActionListener(e -> {

        });

        JButton exitButton = new JButton("Exit");
        exitButton.setBounds(50, 150, 100, 50);
        add(exitButton);
        exitButton.addActionListener(l -> {
            // TODO: Disconnect from game
            this.setVisible(false);
            gL.showLobby(true);
            gL.startHeartbeat();
        });

    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, this); // Draws the image of onto the JPanel
    }
}
