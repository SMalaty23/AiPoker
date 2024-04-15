package players;
import game.HandRanks;
import game.Player;
import game.PlayerActions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SamersPlayer extends Player {
    private int foldCount;  // Tracks the number of times players fold
    private Map<String, List<PlayerActions>> opponentsActions;  // Tracks opponents' actions
    private Map<String, Integer> previousPlayerBanks;

    public SamersPlayer(String name) {
        super(name);
        this.foldCount = 0;
        this.opponentsActions = new HashMap<>();
        this.previousPlayerBanks = new HashMap<>(); // Initialize the map
    }

    // Method to observe and record opponents' actions for analysis
    public void observeOpponentsActions() {
        if (getGameState() == null) {
            return; // Ensure the game state is available
        }

        List<Map<String, Integer>> players = getGameState().getListPlayersNameBankMap();
        Map<String, Integer> currentPlayerBanks = extractPlayerBanks(players);

        // Analyze the changes between previous and current bank states
        for (Map.Entry<String, Integer> entry : currentPlayerBanks.entrySet()) {
            String playerName = entry.getKey();
            Integer currentBank = entry.getValue();
            Integer previousBank = previousPlayerBanks.getOrDefault(playerName, currentBank);

            if (!playerName.equals(getName())) { // Don't track self
                if (previousBank > currentBank) {
                    // The player's bank has decreased, likely indicating a call or a raise
                    PlayerActions action = determineNextAction(playerName);
                    trackOpponentActions(playerName, action);
                } else if (previousBank.equals(currentBank)) {
                    // The player's bank has not changed, possible check or fold
                    PlayerActions action = PlayerActions.values()[(int) (Math.random() * PlayerActions.values().length)];
                    trackOpponentActions(playerName, action);
                }
            }
        }

        // Update the previous bank records for the next round
        previousPlayerBanks.clear();
        previousPlayerBanks.putAll(currentPlayerBanks);
    }

    private PlayerActions determineNextAction(String playerName) {
        // Retrieve the opponent's past actions from the opponentsActions map
        List<PlayerActions> opponentActions = opponentsActions.getOrDefault(playerName, new ArrayList<>());

        // Check if the opponent has folded in the past
        boolean hasFolded = opponentActions.contains(PlayerActions.FOLD);

        // Check if the opponent has raised in the past
        boolean hasRaised = opponentActions.contains(PlayerActions.RAISE);

        // Check if the opponent has called in the past
        boolean hasCalled = opponentActions.contains(PlayerActions.CALL);

        // Determine the next action based on past behavior
        if (hasFolded) {
            // If the opponent has folded previously, they might fold again
            return PlayerActions.FOLD;
        } else if (hasRaised) {
            // If the opponent has raised previously, we adapt our strategy
            // Adaptive Calling: Call more often when facing frequent raiser
            if (Math.random() < 0.7) { // Adjust probability as needed
                return PlayerActions.CALL;
            } else {
                // Selective Raising: Raise with strong hands to counter aggressive player
                double handStrength = evaluateHandStrength();
                if (handStrength > 0.6) { // Adjust threshold based on hand strength
                    return PlayerActions.RAISE;
                } else {
                    return PlayerActions.FOLD; // Fold weaker hands against aggressive player
                }
            }
        } else if (hasCalled) {
            // If the opponent has called previously, they might call again, raise, or fold
            double randomNumber = Math.random();
            if (randomNumber < 0.33) {
                return PlayerActions.CALL;
            } else if (randomNumber < 0.66) {
                return PlayerActions.RAISE;
            } else {
                return PlayerActions.FOLD;
            }
        } else {
            // If the opponent hasn't taken any action previously, they might check, bet, or fold
            double randomNumber = Math.random();
            if (randomNumber < 0.33) {
                return PlayerActions.CHECK;
            } else if (randomNumber < 0.66) {
                return PlayerActions.RAISE;
            } else {
                return PlayerActions.FOLD;
            }
        }
    }

    private Map<String, Integer> extractPlayerBanks(List<Map<String, Integer>> players) {
        Map<String, Integer> playerBanks = new HashMap<>();
        for (Map<String, Integer> playerInfo : players) {
            playerBanks.putAll(playerInfo);
        }
        return playerBanks;
    }

    private void trackOpponentActions(String playerName, PlayerActions action) {
        if (!opponentsActions.containsKey(playerName)) {
            opponentsActions.put(playerName, new ArrayList<>());
        }
        opponentsActions.get(playerName).add(action);
    }


    protected boolean defendStrongHand() {
        HandRanks handRank = evaluatePlayerHand();
        int betOnTable = getGameState().getTableBet();
        int myCurrentBank = getBank();

        // Check if the hand rank is strong enough to defend
        if (handRank.getValue() >= HandRanks.TWO_PAIR.getValue()) {
            // If the opponent's bet is significant relative to your bank, consider calling or raising
            if (betOnTable > myCurrentBank * 0.2) {
                // Decide whether to call or raise based on the strength of your hand
                if (handRank.getValue() >= HandRanks.FULL_HOUSE.getValue()) {
                    // If you have a full house or better, consider raising
                    return true;
                } else {
                    // If you have a strong hand but not a full house, consider calling
                    return true;
                }
            }
        }
        return false;
    }

    private boolean areOpponentsPlayingAggressively() {
        // Iterate over opponents' actions
        for (List<PlayerActions> actions : opponentsActions.values()) {
            int raiseCount = 0;
            int totalActions = actions.size();

            // Count the number of raises
            for (PlayerActions action : actions) {
                if (action == PlayerActions.RAISE) {
                    raiseCount++;
                }
            }

            // If more than 50% of actions are raises, opponents are considered consistently raising
            if ((double) raiseCount / totalActions > 0.5) {
                return true;
            }
        }
        return false;
    }


    @Override
    public void takePlayerTurn() {
        observeOpponentsActions();  // Update opponent action tracking at the start of each turn

        if (shouldFold()) {
            fold();
        } else if (shouldCheck()) {
            check();
        } else if (shouldCall()) {
            call();
        } else if (defendStrongHand()) {
            // Defend a strong hand by calling or raising
            if (shouldRaise()) {
                raise(calculateOptimalRaise());
            } else {
                call();
            }
        } else if (shouldRaise()) {
            raise(calculateOptimalRaise());
        } else if (shouldAllIn()) {
            allIn();
        }
    }

    @Override
    protected boolean shouldFold() {
        // Example folding logic based on game state
        int betOnTable = getGameState().getTableBet();
        int myCurrentBank = getBank();

        // Check if opponents are consistently raising
        boolean opponentsConsistentlyRaising = areOpponentsPlayingAggressively();

        // If opponents are consistently raising and the bet on the table is relatively high compared to the player's bank, fold
        if (opponentsConsistentlyRaising && betOnTable > myCurrentBank * 0.1) {
            return true;
        }

        // Otherwise, follow the default logic
        return betOnTable > myCurrentBank * 0.25;
    }

    @Override
    protected boolean shouldCheck() {
        return !isBetActive();
    }

    @Override
    protected boolean shouldCall() {
        int betOnTable = getGameState().getTableBet();
        int myCurrentBank = getBank();
        return isBetActive() && betOnTable < myCurrentBank * 0.1;
    }

    @Override
    protected boolean shouldRaise() {
        double handStrength = evaluateHandStrength();

        // Check if opponents are consistently raising
        boolean opponentsConsistentlyRaising = areOpponentsPlayingAggressively();

        // If opponents are consistently raising and the hand strength is sufficient, raise
        if (opponentsConsistentlyRaising && handStrength > 0.6) {
            return true;
        }

        // Otherwise, follow the default logic based on hand strength
        return handStrength > 0.7;  // Example threshold for raising
    }

    @Override
    protected boolean shouldAllIn() {
        double handStrength = evaluateHandStrength();
        boolean isStrategicBluff = isStrategicBluff();
        return handStrength > 0.85 || isStrategicBluff;
    }

    private double evaluateHandStrength() {
        // Simplified hand strength evaluation
        return evaluatePlayerHand().getValue();
    }

    private boolean isStrategicBluff() {
        // Evaluate bluffing opportunities based on opponents' tendencies
        return opponentsActions.values().stream()
                .flatMap(List::stream)
                .anyMatch(a -> a == PlayerActions.FOLD);
    }

    private int calculateOptimalRaise() {
        // Adjust the raise based on the current game state
        int tableMinBet = getGameState().getTableMinBet();
        int raiseMultiplier = 2; // Default multiplier
        int numRoundStage = getGameState().getNumRoundStage();

        // Adjust raise multiplier based on the current round stage
        switch (numRoundStage) {
            case 1:
                raiseMultiplier = 2;
                break;
            case 2:
                raiseMultiplier = 3;
                break;
            case 3:
                raiseMultiplier = 4;
                break;
            default:
                raiseMultiplier = 2;
                break;
        }

        // Calculate the optimal raise amount
        return (int) (tableMinBet * raiseMultiplier);
    }
}