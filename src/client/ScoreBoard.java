package client;

import common.PlayerData;
import common.Role;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static client.GameLobby.LOGGER;


// Class keeps track of the players scores
public class ScoreBoard extends JPanel {
    private final GameStateTracker stateTracker;
    private final GameLobby gameLobby;
    private List<PlayerData> players;     // Array of the players in the game
    private JLabel[] playerScores;        // Array of the labels representing the players
    private final JLabel scoreLabel;        // Array of the labels representing the players
    private final int labelWidth = 100;
    private final int labelHeight = 50;

    public ScoreBoard(GameStateTracker sT, GameLobby gl) {

        stateTracker = sT;
        gameLobby = gl;

        this.players = stateTracker.getPlayerList();
        setLayout(null);


        scoreLabel = new JLabel("Scoreboard", SwingConstants.CENTER);
        scoreLabel.setFont(gameLobby.normalFont.deriveFont(Font.BOLD, 15));
        scoreLabel.setBounds(0, 0, labelWidth, labelHeight);
        scoreLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
        add(scoreLabel);

        playerScores = new JLabel[players.size()];

        LOGGER.info("Inside scoreboard class");
        for (int i = 0; i < players.size(); i++) {

            PlayerData player = this.players.get(i);
            int score = 0;

            playerScores[i] = new JLabel("", SwingConstants.CENTER);
            playerScores[i].setBounds(0, labelHeight+(labelHeight*i), labelWidth, labelHeight);
            playerScores[i].setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));  // Create border


            if(!player.getPreviousRoles().isEmpty()) {
                for(Role r : player.getPreviousRoles()) {
                    LOGGER.info("Previous role is  " + r);
                    switch(r){
                        case PRESIDENT -> score += 2;
                        case VICE_PRESIDENT -> score += 1;
                        case VICE_BUM -> score -= 1;
                        case BUM -> score -= 2;
                    }
                }
            } else
                LOGGER.info("PREVIOUS ROLES ARE EMPTY");

            String playerLabel = player.getNick() + ": " + score;
            playerScores[i].setText(playerLabel);
            add(playerScores[i]);
        }
    }
}
