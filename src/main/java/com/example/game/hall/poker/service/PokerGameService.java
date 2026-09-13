package com.example.game.hall.poker.service;

import com.example.game.hall.poker.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PokerGameService {

    private static final int MAX_PLAYERS = 8;
    private static final int SMALL_BLIND = 10;
    private static final int BIG_BLIND = 20;
    private static final int MIN_PLAYERS = 2;

    private GamePhase phase = GamePhase.WAITING;
    private final List<PokerPlayer> players = new CopyOnWriteArrayList<>();
    private final Deck deck = new Deck();
    private final List<Card> communityCards = new ArrayList<>();
    private int pot = 0;
    private int currentBet = 0;
    private int dealerIndex = 0;
    private int currentPlayerIndex = 0;
    private int minRaise = BIG_BLIND;

    private final Map<String, WebSocketSession> playerSessions = new ConcurrentHashMap<>();
    private final AtomicInteger playerIdCounter = new AtomicInteger(1);

    private final AIPlayerService aiService = new AIPlayerService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final AIStyle[] AI_STYLES = {
        AIStyle.AGGRESSIVE,
        AIStyle.CONSERVATIVE,
        AIStyle.RANDOM,
        AIStyle.CALCULATOR,
        AIStyle.CALLER
    };

    private final ReentrantLock gameLock = new ReentrantLock();

    public PokerPlayer joinGame(String username, String avatar, WebSocketSession session) {
        gameLock.lock();
        try {
            if (players.size() >= MAX_PLAYERS) {
                return null;
            }

            for (PokerPlayer player : players) {
                if (player.getUsername().equals(username)) {
                    playerSessions.put(player.getId(), session);
                    return player;
                }
            }

            String playerId = "player_" + playerIdCounter.incrementAndGet();
            int seatIndex = findEmptySeat();

            PokerPlayer player = new PokerPlayer(playerId, username, avatar != null ? avatar : getDefaultAvatar(),
                                                seatIndex, false);
            players.add(player);
            playerSessions.put(playerId, session);

            broadcastGameState();

            return player;
        } finally {
            gameLock.unlock();
        }
    }

    public void leaveGame(String playerId) {
        gameLock.lock();
        try {
            PokerPlayer playerToRemove = null;
            for (PokerPlayer player : players) {
                if (player.getId().equals(playerId)) {
                    playerToRemove = player;
                    break;
                }
            }

            if (playerToRemove != null) {
                players.remove(playerToRemove);
                playerSessions.remove(playerId);

                if (!phase.equals(GamePhase.WAITING)) {
                    playerToRemove.setFolded(true);
                }

                broadcastGameState();
            }
        } finally {
            gameLock.unlock();
        }
    }

    public boolean startGame() {
        gameLock.lock();
        try {
            if (players.size() < MIN_PLAYERS || phase != GamePhase.WAITING) {
                return false;
            }

            fillAIPlayers();
            resetGame();
            setBlinds();
            dealHoleCards();

            phase = GamePhase.PREFLOP;
            currentPlayerIndex = (dealerIndex + 2) % players.size();

            broadcastGameState();

            return true;
        } finally {
            gameLock.unlock();
        }
    }

    public void playerAction(String playerId, PokerPlayer.Action action, int raiseAmount) {
        gameLock.lock();
        try {
            if (currentPlayerIndex >= players.size()) {
                return;
            }

            PokerPlayer currentPlayer = players.get(currentPlayerIndex);
            if (!currentPlayer.getId().equals(playerId)) {
                return;
            }

            int toCall = currentBet - currentPlayer.getCurrentBet();

            switch (action) {
                case FOLD:
                    currentPlayer.setFolded(true);
                    break;

                case CHECK:
                    if (toCall > 0) {
                        return;
                    }
                    break;

                case CALL:
                    currentPlayer.bet(toCall);
                    break;

                case RAISE:
                    int actualRaise = Math.max(raiseAmount, toCall + minRaise);
                    currentPlayer.bet(actualRaise);
                    currentBet = currentPlayer.getCurrentBet();
                    minRaise = actualRaise - toCall;
                    break;

                case ALL_IN:
                    int allInAmount = currentPlayer.getChips();
                    currentPlayer.bet(allInAmount);
                    if (currentPlayer.getCurrentBet() > currentBet) {
                        currentBet = currentPlayer.getCurrentBet();
                    }
                    break;
            }

            moveToNextPlayer();
        } finally {
            gameLock.unlock();
        }
    }

    public void aiThink(PokerPlayer aiPlayer) {
        new Thread(() -> {
            gameLock.lock();
            try {
                int toCall = currentBet - aiPlayer.getCurrentBet();

                aiService.setPot(pot);
                PokerPlayer.Action action = aiService.think(
                    aiPlayer,
                    currentBet,
                    toCall,
                    aiPlayer.getHandCards(),
                    communityCards,
                    pot
                );

                int raiseAmount = 0;
                if (action == PokerPlayer.Action.RAISE) {
                    raiseAmount = aiService.getRaiseAmount(aiPlayer, currentBet, minRaise);
                }

                playerAction(aiPlayer.getId(), action, raiseAmount);
            } finally {
                gameLock.unlock();
            }
        }).start();
    }

    private void moveToNextPlayer() {
        int nextIndex = (currentPlayerIndex + 1) % players.size();
        int startIndex = nextIndex;

        while (true) {
            PokerPlayer player = players.get(nextIndex);

            if (!player.isFolded() && !player.isAllIn()) {
                currentPlayerIndex = nextIndex;

                if (isRoundComplete()) {
                    nextPhase();
                } else if (player.isAI()) {
                    aiThink(player);
                }
                break;
            }

            nextIndex = (nextIndex + 1) % players.size();

            if (nextIndex == startIndex) {
                nextPhase();
                break;
            }
        }

        broadcastGameState();
    }

    private boolean isRoundComplete() {
        boolean allActed = true;
        int activePlayers = 0;

        for (PokerPlayer player : players) {
            if (!player.isFolded()) {
                activePlayers++;
                if (!player.isAllIn() && player.getCurrentBet() < currentBet) {
                    allActed = false;
                }
            }
        }

        return allActed && activePlayers > 1;
    }

    private void nextPhase() {
        for (PokerPlayer player : players) {
            pot += player.getCurrentBet();
            player.setCurrentBet(0);
        }

        currentBet = 0;
        minRaise = BIG_BLIND;

        phase = phase.next();

        switch (phase) {
            case FLOP:
                communityCards.addAll(deck.dealCards(3));
                currentPlayerIndex = dealerIndex;
                break;

            case TURN:
                communityCards.add(deck.dealCard());
                currentPlayerIndex = dealerIndex;
                break;

            case RIVER:
                communityCards.add(deck.dealCard());
                currentPlayerIndex = dealerIndex;
                break;

            case SHOWDOWN:
                showdown();
                return;

            default:
                endGame();
                return;
        }

        findFirstActivePlayer();
        broadcastGameState();
    }

    private void showdown() {
        List<PokerPlayer> activePlayers = new ArrayList<>();
        for (PokerPlayer player : players) {
            if (!player.isFolded()) {
                activePlayers.add(player);
            }
        }

        if (activePlayers.size() == 1) {
            distributePot(activePlayers.get(0));
        } else {
            PokerHand bestHand = null;
            PokerPlayer winner = null;

            for (PokerPlayer player : activePlayers) {
                PokerHand hand = HandEvaluator.evaluate(player.getHandCards(), communityCards);
                if (bestHand == null || hand.getScore() > bestHand.getScore()) {
                    bestHand = hand;
                    winner = player;
                }
            }

            if (winner != null) {
                distributePot(winner);
            }
        }

        broadcastGameState();
    }

    private void distributePot(PokerPlayer winner) {
        winner.setChips(winner.getChips() + pot);
        pot = 0;

        sendMessage(winner.getId(), "恭喜你获胜！赢得底池：" + winner.getChips());

        endGame();
    }

    private void fillAIPlayers() {
        int aiIndex = 0;

        while (players.size() < MAX_PLAYERS) {
            AIStyle style = AI_STYLES[aiIndex % AI_STYLES.length];

            String aiId = "ai_" + (players.size() + 1);
            String aiName = style.getDisplayName() + "_" + (aiIndex / AI_STYLES.length + 1);
            String aiAvatar = style.getAvatar();
            int seatIndex = findEmptySeat();

            PokerPlayer aiPlayer = new PokerPlayer(aiId, aiName, aiAvatar, seatIndex, true, style);
            players.add(aiPlayer);

            aiIndex++;
        }
    }

    private void endGame() {
        phase = GamePhase.GAME_OVER;

        dealerIndex = (dealerIndex + 1) % players.size();

        for (PokerPlayer player : players) {
            player.resetRound();
        }

        communityCards.clear();
        pot = 0;
        currentBet = 0;

        broadcastGameState();

        phase = GamePhase.WAITING;
    }

    private void resetGame() {
        deck.shuffle();
        communityCards.clear();
        pot = 0;
        currentBet = 0;
        minRaise = BIG_BLIND;

        for (PokerPlayer player : players) {
            player.resetRound();
        }
    }

    private void setBlinds() {
        int sbIndex = (dealerIndex + 1) % players.size();
        int bbIndex = (dealerIndex + 2) % players.size();

        PokerPlayer sb = players.get(sbIndex);
        PokerPlayer bb = players.get(bbIndex);

        sb.setSmallBlind(true);
        bb.setBigBlind(true);

        sb.bet(SMALL_BLIND);
        bb.bet(BIG_BLIND);

        currentBet = BIG_BLIND;
        pot = SMALL_BLIND + BIG_BLIND;
    }

    private void dealHoleCards() {
        for (PokerPlayer player : players) {
            List<Card> hand = deck.dealCards(2);
            player.setHandCards(hand);
        }
    }

    private void findFirstActivePlayer() {
        int startIndex = currentPlayerIndex;
        while (true) {
            PokerPlayer player = players.get(currentPlayerIndex);
            if (!player.isFolded() && !player.isAllIn()) {
                if (player.isAI()) {
                    aiThink(player);
                }
                break;
            }
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            if (currentPlayerIndex == startIndex) {
                break;
            }
        }
    }

    private int findEmptySeat() {
        Set<Integer> occupiedSeats = new HashSet<>();
        for (PokerPlayer player : players) {
            occupiedSeats.add(player.getSeatIndex());
        }

        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!occupiedSeats.contains(i)) {
                return i;
            }
        }

        return players.size();
    }

    private String getDefaultAvatar() {
        String[] avatars = {
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼"
        };
        return avatars[playerIdCounter.get() % avatars.length];
    }

    private void broadcastGameState() {
        try {
            Map<String, Object> gameState = getGameState();
            String message = objectMapper.writeValueAsString(gameState);

            for (WebSocketSession session : playerSessions.values()) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(String playerId, String message) {
        WebSocketSession session = playerSessions.get(playerId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Map<String, Object> getGameState() {
        Map<String, Object> state = new HashMap<>();
        state.put("phase", phase.getDisplayName());
        state.put("pot", pot);
        state.put("currentBet", currentBet);
        state.put("currentPlayerIndex", currentPlayerIndex);
        state.put("dealerIndex", dealerIndex);
        state.put("communityCards", communityCards.stream()
            .map(Card::getDisplayName)
            .toArray(String[]::new));

        List<PokerPlayer.PlayerState> playerStates = new ArrayList<>();
        for (PokerPlayer player : players) {
            playerStates.add(player.toState());
        }
        state.put("players", playerStates);

        return state;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public List<PokerPlayer> getPlayers() {
        return players;
    }

    public int getPot() {
        return pot;
    }

    public List<Card> getCommunityCards() {
        return communityCards;
    }
}
