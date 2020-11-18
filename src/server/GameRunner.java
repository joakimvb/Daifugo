package server;

import protocol.*;
import server.exceptions.*;

import java.io.*;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.logging.*;

import static protocol.MessageType.*;

/**
 * Module for handling all server requests while the client is participating
 * in a game.
 */
public class GameRunner {
    private final ObjectInputStream in;
    private final ObjectOutputStream out;
    private final Game game;
    private final UserSession userSession;
    private final Logger LOGGER;
    private boolean running;

    public String logPrefix() {
        return userSession.getNick() + ": ";
    }

    public GameRunner(
            UserSession userSession,
            ObjectInputStream in,
            ObjectOutputStream out
    ) {
        this.userSession = userSession;
        this.in = in;
        this.out = out;
        this.game = userSession.getGame();
        LOGGER = Logger.getLogger(GameRunner.class.getName() + "-" + userSession.getToken());
        LOGGER.addHandler(Server.CONSOLE_HANDLER);
        //LOGGER.addHandler(Server.FILE_HANDLER);
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.INFO);

        LOGGER.info(
                logPrefix() + "joined game: " + game.getTitle()
                        + " (" + game.getID() + ")"
        );
    }

    private void sendHeartbeatResponse(HeartbeatMessage request) throws IOException {

        LOGGER.fine(logPrefix() + "got heartbeat");

        // if state hasn't been updated, simply heartbeat back
        PlayerObject po = game.getPlayers().get(userSession.getID());
        if (!po.isStateUpdated()) {
            long receiveTime = Instant.now().toEpochMilli();
            out.writeObject(
                    new HeartbeatMessage(receiveTime)
            );

            po.getGameData().setLatency(receiveTime - request.getTime());
            return;
        }

        // leave game if cancelled, and inform client
        if (game.isCancelled()) {
            game.leaveGame(userSession.getID());
            out.writeObject(new CancelledGameError());
            running = false;
            return;
        }



        // returning game state response
        try {
            out.writeObject(new GameStateResponse(game, userSession));
            po.stateHasBeenSent();
        } catch (UserSessionError userSessionError) {
            out.writeObject(new ErrorMessage(userSessionError.getMessage()));
        }


    }

    private boolean userNotOwner() throws IOException {
        try {
            if (!game.getOwnerNick().equals(userSession.getNick())) {
                out.writeObject(new ErrorMessage("Not allowed"));
                return true;
            }
        } catch (UserSessionError userSessionError) {
            out.writeObject(new ErrorMessage(userSessionError.getMessage()));
            return true;
        }

        return false;
    }

    private void validateCancellation() throws IOException, LeftGame {

        if (userNotOwner()) {
            out.writeObject(new ErrorMessage("Non-owners cannot cancel a game"));
            return;
        }

        game.cancelGame();
        out.writeObject(new Message(OK));
        throw new LeftGame();
    }

    private void validateGameStart() throws IOException {
        if (userNotOwner()) {
            out.writeObject(new ErrorMessage("You are not the owner of this game"));
            return;
        }

        try {
            LOGGER.info("Starting game " + game.getTitle() + "...");
            game.start();
            out.writeObject(new Message(OK));
        } catch (GameException e) {
            out.writeObject(new ErrorMessage(e.getMessage()));
        }

    }


    private void validateGameStop() throws IOException {
        if (userNotOwner()) {
            out.writeObject(new ErrorMessage("You are not the owner of this game"));
            return;
        }

        game.stop();
        out.writeObject(new Message(OK));

    }


    private void playerDisconnect() throws GameDisconnect {
        userSession.leaveCurrentGame();
        throw new GameDisconnect();
    }

    private void handlePlayCards(PlayCardsRequest request) throws IOException {
        try {
            game.playCards(userSession.getID(), request.getCards());
            out.writeObject(new GameStateResponse(game, userSession));
        } catch (UserSessionError | GameException e) {
            out.writeObject(new ErrorMessage(e.getMessage()));
        }


    }

    private void handlePass() throws IOException {
        try {
            game.pass(userSession.getID());
            out.writeObject(new GameStateResponse(game, userSession));
        } catch (RoundOver ignore) {
            // a round cannot end by passing alone
        } catch (UserSessionError userSessionError) {
            out.writeObject(new ErrorMessage(userSessionError.getMessage()));
        }
    }

    void leaveGameHandler() throws IOException, LeftGame {
        userSession.leaveCurrentGame();
        out.writeObject(new Message(OK));
        throw new LeftGame();
    }

    public void run() throws GameDisconnect, IOException {

        LOGGER.info("Entered game mode");
        try {
            out.writeObject(new GameStateResponse(
                    game, userSession
            ));
        } catch (UserSessionError | IOException userSessionError) {
            out.writeObject(new ErrorMessage(userSessionError.toString()));
        }
        running = true;
        while (running) {

            LOGGER.fine("Awaiting request from: " + userSession.getToken() + "|" + userSession.getNick());
            try {

                Message request;
                try {
                    request = (Message) in.readObject();
                } catch (SocketTimeoutException e) {
                    LOGGER.info("socket exception, breaking runloop");
                    break;
                } catch (EOFException e) {
                    LOGGER.warning(
                            "Got EOF while reading game request. Maybe you forgot to call disconnect()?"
                    );
                    throw new GameDisconnect();
                }


                switch (request.getMessageType()) {
/*                            case RECONNECT -> { //TODO: timeout problem
                            if (!handleReconnection(request))
                                break runLoop;
                        }*/
                    case PLAY_CARDS -> handlePlayCards((PlayCardsRequest) request);
                    case PASS_TURN -> handlePass();
                    case START_GAME -> validateGameStart();
                    case STOP_GAME -> validateGameStop();
                    case LEAVE_GAME -> leaveGameHandler();
                    case CANCEL_GAME -> validateCancellation();
                    case HEARTBEAT -> sendHeartbeatResponse((HeartbeatMessage) request);
                    case DISCONNECT -> playerDisconnect();
                    default -> out.writeObject(new ErrorMessage("Invalid game request"));
                }


            } catch (IOException | ClassNotFoundException e) {
                LOGGER.warning(logPrefix() + "got exception: " + e.getMessage());
                break;
            } catch (GameDisconnect ignored) {
                LOGGER.info(logPrefix() + "disconnected during game");
                out.writeObject(new Message(OK));
                throw new GameDisconnect();
            } catch (LeftGame ignored) {
                LOGGER.info(logPrefix() + "left game: " + game.getID().toString());
                break;
            }
        }
    }
}
