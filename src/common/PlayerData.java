package common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static client.GameLobby.LOGGER;

public class PlayerData implements Serializable {
    private String nick;
    private long latency;
    private int numberOfCards;
    private boolean connectionLost;
    private Role role;
    private boolean passed;
    private boolean outOfRound;
    private int outCount;
    private List<Role> previousRoles;
    private boolean mustTrade;

    public PlayerData(
            String nick,
            int numberOfCards,
            boolean passed,
            Role role,
            int latency,
            List<Role> previousRoles
    ) {
       this.nick = nick;
       this.passed = passed;
       this.numberOfCards = numberOfCards;
       this.role = role;
       this.latency = latency;

       mustTrade = false;
       outCount = 0;
       outOfRound = false;
       this.previousRoles = previousRoles;

       // negative latency value is lost connection
       connectionLost = latency < 0;
    }

    public void setOutOfRound(boolean outOfRound) {
        this.outOfRound = outOfRound;
    }

    public int getOutCount() {
        return outCount;
    }

    public void reset() {
        role = Role.NEUTRAL;
        previousRoles = new ArrayList<>();
        passed = false;
        outOfRound = false;
        outCount = 0;
    }

    public String getNick() {
        return nick;
    }

    public long getLatency() {
        return latency;
    }

    public int getNumberOfCards() {
        return this.numberOfCards;
    }

    public Role getRole() {
        return role;
    }

    public void setConnectionLost(boolean connectionLost) {
        this.connectionLost = connectionLost;
    }

    public void setLatency(long latency) {
        this.latency = latency;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public void setNumberOfCards(int noOfCards) {
        this.numberOfCards = noOfCards;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public boolean hasPassed() {
        return this.passed;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setOutCount(int outCount) {
        this.outCount = outCount;
        outOfRound = true;
    }

    public boolean isOutOfRound() {
        return outOfRound;
    }

    public boolean assignRoleFewPlayers() {
        mustTrade = true;
        if (outCount == 1) {
            role = Role.PRESIDENT;
        } else if (outCount == 3) {
            role = Role.BUM;
        } else {
            role = Role.NEUTRAL;
            mustTrade = false;
        }
        previousRoles.add(role);

        return mustTrade;
    }


    public boolean assignRoleManyPlayers(int playerAmount) {
        mustTrade = true;
        if (outCount == 1) {
            role = Role.PRESIDENT;
        } else if (outCount == 2) {
            role = Role.VICE_PRESIDENT;
        } else if (outCount == playerAmount - 1) {
            role = Role.VICE_BUM;
        } else if (outCount == playerAmount) {
            role = Role.BUM;
        } else {
            role = Role.NEUTRAL;
            mustTrade = false;
        }
        previousRoles.add(role);
        return mustTrade;
    }

    public boolean hasToTrade() {
        return mustTrade;
    }

    public void doneTrading() {
        mustTrade = false;
    }

    public List<Role> getPreviousRoles(){
        return new ArrayList<>(previousRoles);
    }
}
