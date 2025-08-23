package com.fullsteam.config;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("game.modes")
public class GameModeConfigs {
    private CTFConfig ctf = new CTFConfig();
    private KOTHConfig koth = new KOTHConfig();
    private EliminationConfig elimination = new EliminationConfig();
    private JuggernautConfig juggernaut = new JuggernautConfig();
    private OddballConfig oddball = new OddballConfig();

    public CTFConfig getCtf() { return ctf; }
    public void setCtf(CTFConfig ctf) { this.ctf = ctf; }
    
    public KOTHConfig getKoth() { return koth; }
    public void setKoth(KOTHConfig koth) { this.koth = koth; }
    
    public EliminationConfig getElimination() { return elimination; }
    public void setElimination(EliminationConfig elimination) { this.elimination = elimination; }
    
    public JuggernautConfig getJuggernaut() { return juggernaut; }
    public void setJuggernaut(JuggernautConfig juggernaut) { this.juggernaut = juggernaut; }
    
    public OddballConfig getOddball() { return oddball; }
    public void setOddball(OddballConfig oddball) { this.oddball = oddball; }

    @ConfigurationProperties("ctf")
    public static class CTFConfig {
        private int scoreToWin = 3;
        private double flagPickupRadius = 30.0;
        private long flagReturnTimeoutMs = 15000;
        private double baseAreaPadding = 100.0;
        
        public int getScoreToWin() { return scoreToWin; }
        public void setScoreToWin(int scoreToWin) { this.scoreToWin = scoreToWin; }
        
        public double getFlagPickupRadius() { return flagPickupRadius; }
        public void setFlagPickupRadius(double flagPickupRadius) { this.flagPickupRadius = flagPickupRadius; }
        
        public long getFlagReturnTimeoutMs() { return flagReturnTimeoutMs; }
        public void setFlagReturnTimeoutMs(long flagReturnTimeoutMs) { this.flagReturnTimeoutMs = flagReturnTimeoutMs; }
        
        public double getBaseAreaPadding() { return baseAreaPadding; }
        public void setBaseAreaPadding(double baseAreaPadding) { this.baseAreaPadding = baseAreaPadding; }
    }

    @ConfigurationProperties("koth")
    public static class KOTHConfig {
        private double scoreToWin = 100.0;
        private double pointsPerSecond = 1.0;
        private double hillRadius = 75.0;
        private double hillKeepOutRadius = 125.0;
        
        public double getScoreToWin() { return scoreToWin; }
        public void setScoreToWin(double scoreToWin) { this.scoreToWin = scoreToWin; }
        
        public double getPointsPerSecond() { return pointsPerSecond; }
        public void setPointsPerSecond(double pointsPerSecond) { this.pointsPerSecond = pointsPerSecond; }
        
        public double getHillRadius() { return hillRadius; }
        public void setHillRadius(double hillRadius) { this.hillRadius = hillRadius; }
        
        public double getHillKeepOutRadius() { return hillKeepOutRadius; }
        public void setHillKeepOutRadius(double hillKeepOutRadius) { this.hillKeepOutRadius = hillKeepOutRadius; }
    }

    @ConfigurationProperties("elimination")
    public static class EliminationConfig {
        private int scoreToWin = 5;
        
        public int getScoreToWin() { return scoreToWin; }
        public void setScoreToWin(int scoreToWin) { this.scoreToWin = scoreToWin; }
    }

    @ConfigurationProperties("juggernaut")
    public static class JuggernautConfig {
        private int scoreToWin = 3;
        private int health = 1000;
        private long selectionDelayMs = 7000;
        
        public int getScoreToWin() { return scoreToWin; }
        public void setScoreToWin(int scoreToWin) { this.scoreToWin = scoreToWin; }
        
        public int getHealth() { return health; }
        public void setHealth(int health) { this.health = health; }
        
        public long getSelectionDelayMs() { return selectionDelayMs; }
        public void setSelectionDelayMs(long selectionDelayMs) { this.selectionDelayMs = selectionDelayMs; }
    }

    @ConfigurationProperties("oddball")
    public static class OddballConfig {
        private double scoreToWin = 90.0;
        private double pointsPerSecond = 1.0;
        private double ballPickupRadius = 30.0;
        private long ballResetTimeoutMs = 15000;
        private double keepOutRadius = 150.0;
        
        public double getScoreToWin() { return scoreToWin; }
        public void setScoreToWin(double scoreToWin) { this.scoreToWin = scoreToWin; }
        
        public double getPointsPerSecond() { return pointsPerSecond; }
        public void setPointsPerSecond(double pointsPerSecond) { this.pointsPerSecond = pointsPerSecond; }
        
        public double getBallPickupRadius() { return ballPickupRadius; }
        public void setBallPickupRadius(double ballPickupRadius) { this.ballPickupRadius = ballPickupRadius; }
        
        public long getBallResetTimeoutMs() { return ballResetTimeoutMs; }
        public void setBallResetTimeoutMs(long ballResetTimeoutMs) { this.ballResetTimeoutMs = ballResetTimeoutMs; }
        
        public double getKeepOutRadius() { return keepOutRadius; }
        public void setKeepOutRadius(double keepOutRadius) { this.keepOutRadius = keepOutRadius; }
    }
}
