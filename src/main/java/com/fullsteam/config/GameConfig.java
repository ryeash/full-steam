package com.fullsteam.config;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("game")
public class GameConfig {
    private int width = 1000;
    private int height = 800;
    private int tickRate = 24;
    private int maxGlobalPlayers = 50;
    private int maxSpectatorsPerGame = 5;
    private int obstacleCount = 6;
    private long gameEventDurationMs = 6000;
    private long nextRoundDelayMs = 6000;
    private long respawnDelayMs = 5000;
    private long roundDurationSeconds = 180;
    private long afkTimeoutMs = 190000;
    private boolean allowNameChange = true;
    
    private PlayerConfig player = new PlayerConfig();
    private SpawnConfig spawn = new SpawnConfig();
    private AIConfig ai = new AIConfig();
    private LobbyConfig lobby = new LobbyConfig();
    private GameModeConfigs modes = new GameModeConfigs();

    // Getters and setters
    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
    
    public int getTickRate() { return tickRate; }
    public void setTickRate(int tickRate) { this.tickRate = tickRate; }
    
    public int getMaxGlobalPlayers() { return maxGlobalPlayers; }
    public void setMaxGlobalPlayers(int maxGlobalPlayers) { this.maxGlobalPlayers = maxGlobalPlayers; }
    
    public int getMaxSpectatorsPerGame() { return maxSpectatorsPerGame; }
    public void setMaxSpectatorsPerGame(int maxSpectatorsPerGame) { this.maxSpectatorsPerGame = maxSpectatorsPerGame; }
    
    public int getObstacleCount() { return obstacleCount; }
    public void setObstacleCount(int obstacleCount) { this.obstacleCount = obstacleCount; }
    
    public long getGameEventDurationMs() { return gameEventDurationMs; }
    public void setGameEventDurationMs(long gameEventDurationMs) { this.gameEventDurationMs = gameEventDurationMs; }
    
    public long getNextRoundDelayMs() { return nextRoundDelayMs; }
    public void setNextRoundDelayMs(long nextRoundDelayMs) { this.nextRoundDelayMs = nextRoundDelayMs; }
    
    public long getRespawnDelayMs() { return respawnDelayMs; }
    public void setRespawnDelayMs(long respawnDelayMs) { this.respawnDelayMs = respawnDelayMs; }
    
    public long getRoundDurationSeconds() { return roundDurationSeconds; }
    public void setRoundDurationSeconds(long roundDurationSeconds) { this.roundDurationSeconds = roundDurationSeconds; }
    
    public long getAfkTimeoutMs() { return afkTimeoutMs; }
    public void setAfkTimeoutMs(long afkTimeoutMs) { this.afkTimeoutMs = afkTimeoutMs; }
    
    public boolean isAllowNameChange() { return allowNameChange; }
    public void setAllowNameChange(boolean allowNameChange) { this.allowNameChange = allowNameChange; }
    
    public PlayerConfig getPlayer() { return player; }
    public void setPlayer(PlayerConfig player) { this.player = player; }
    
    public SpawnConfig getSpawn() { return spawn; }
    public void setSpawn(SpawnConfig spawn) { this.spawn = spawn; }
    
    public AIConfig getAi() { return ai; }
    public void setAi(AIConfig ai) { this.ai = ai; }
    
    public LobbyConfig getLobby() { return lobby; }
    public void setLobby(LobbyConfig lobby) { this.lobby = lobby; }
    
    public GameModeConfigs getModes() { return modes; }
    public void setModes(GameModeConfigs modes) { this.modes = modes; }

    @ConfigurationProperties("player")
    public static class PlayerConfig {
        private double size = 20.0;
        private double health = 100.0;
        private double speed = 0.18;
        
        public double getSize() { return size; }
        public void setSize(double size) { this.size = size; }
        
        public double getHealth() { return health; }
        public void setHealth(double health) { this.health = health; }
        
        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }
        
        public double getRadius() { return size / 2; }
        public double getRadiusSquared() { return getRadius() * getRadius(); }
    }

    @ConfigurationProperties("spawn")
    public static class SpawnConfig {
        private double horizontalPadding = 50.0;
        private double verticalPadding = 50.0;
        private double midfieldBuffer = 300.0;
        
        public double getHorizontalPadding() { return horizontalPadding; }
        public void setHorizontalPadding(double horizontalPadding) { this.horizontalPadding = horizontalPadding; }
        
        public double getVerticalPadding() { return verticalPadding; }
        public void setVerticalPadding(double verticalPadding) { this.verticalPadding = verticalPadding; }
        
        public double getMidfieldBuffer() { return midfieldBuffer; }
        public void setMidfieldBuffer(double midfieldBuffer) { this.midfieldBuffer = midfieldBuffer; }
    }

    @ConfigurationProperties("ai")
    public static class AIConfig {
        private long decisionCooldownMs = 800;
        private double visionRange = 450.0;
        private long wanderIntervalMs = 2000;
        private long baseReactionTimeMs = 600;
        private double baseAimInaccuracy = 0.4;
        private long baseStrafeIntervalMs = 1500;
        private double maxForce = 0.5;
        
        public long getDecisionCooldownMs() { return decisionCooldownMs; }
        public void setDecisionCooldownMs(long decisionCooldownMs) { this.decisionCooldownMs = decisionCooldownMs; }
        
        public double getVisionRange() { return visionRange; }
        public void setVisionRange(double visionRange) { this.visionRange = visionRange; }
        
        public long getWanderIntervalMs() { return wanderIntervalMs; }
        public void setWanderIntervalMs(long wanderIntervalMs) { this.wanderIntervalMs = wanderIntervalMs; }
        
        public long getBaseReactionTimeMs() { return baseReactionTimeMs; }
        public void setBaseReactionTimeMs(long baseReactionTimeMs) { this.baseReactionTimeMs = baseReactionTimeMs; }
        
        public double getBaseAimInaccuracy() { return baseAimInaccuracy; }
        public void setBaseAimInaccuracy(double baseAimInaccuracy) { this.baseAimInaccuracy = baseAimInaccuracy; }
        
        public long getBaseStrafeIntervalMs() { return baseStrafeIntervalMs; }
        public void setBaseStrafeIntervalMs(long baseStrafeIntervalMs) { this.baseStrafeIntervalMs = baseStrafeIntervalMs; }
        
        public double getMaxForce() { return maxForce; }
        public void setMaxForce(double maxForce) { this.maxForce = maxForce; }
    }

    @ConfigurationProperties("lobby")
    public static class LobbyConfig {
        private long cleanupIntervalSeconds = 10;
        
        public long getCleanupIntervalSeconds() { return cleanupIntervalSeconds; }
        public void setCleanupIntervalSeconds(long cleanupIntervalSeconds) { this.cleanupIntervalSeconds = cleanupIntervalSeconds; }
    }
}
