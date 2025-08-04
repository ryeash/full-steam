package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import org.apache.commons.lang3.StringUtils;

public class Player {
    protected final String id;
    protected String playerName;
    protected double x;
    protected double y;
    @JsonIgnore
    protected double velocityX;
    @JsonIgnore
    protected double velocityY;
    @JsonIgnore
    protected double speed;
    @JsonIgnore
    protected double defaultSpeed;
    protected int team;
    protected Weapon weapon;
    protected double currentHealth;
    protected double maxHealth;
    @JsonIgnore
    protected transient long lastShotTime;
    @JsonIgnore
    protected transient long lastInputTime;
    protected boolean isDead;
    protected long respawnTime;
    protected int kills;
    protected int deaths;
    protected double lastBulletAngle; // The angle of the last shot in radians
    protected int currentAmmoInMagazine;
    protected boolean isReloading;
    @JsonIgnore
    protected long reloadCompleteTime;
    @JsonIgnore
    protected long nextShotTime;
    public long speedBoostEndTime;
    public long armorUpEndTime;
    public long damageBoostEndTime;
    public double damageMultiplier;
    @JsonIgnore
    private long lastWeaponChangeTime;
    @JsonIgnore
    private long alternateActionCooldown;

    public Player(String id, double x, double y, int team) {
        this(id, id, x, y, team, WeaponFactory.getDefaultWeapon());
    }

    public Player(String id, String playerName, double x, double y, int team, Weapon weapon) {
        this.id = id;
        this.playerName = playerName;
        this.x = x;
        this.y = y;
        this.team = team;
        this.speed = Config.DEFAULT_PLAYER_SPEED;
        this.defaultSpeed = Config.DEFAULT_PLAYER_SPEED;
        setWeapon(weapon);
        this.currentHealth = Config.DEFAULT_PLAYER_HEALTH;
        this.maxHealth = Config.DEFAULT_PLAYER_HEALTH;
        this.velocityY = 0;
        this.lastShotTime = 0;
        this.lastInputTime = System.currentTimeMillis();
        this.isDead = false;
        this.respawnTime = 0;
        this.kills = 0;
        this.deaths = 0;
        this.lastBulletAngle = 0.0; // Default angle (pointing right)
        this.currentAmmoInMagazine = weapon.getRoundsPerMagazine();
        this.isReloading = false;
        this.reloadCompleteTime = 0;
        this.nextShotTime = 0;
        this.speedBoostEndTime = 0;
        this.armorUpEndTime = 0;
        this.damageBoostEndTime = 0;
        this.damageMultiplier = 1.0;
        this.lastWeaponChangeTime = 0;
        this.alternateActionCooldown = 0;
    }

    public void update() {
        x += velocityX;
        y += velocityY;
    }

    public boolean canShoot() {
        return !isDead()
               && !isReloading
               && currentAmmoInMagazine > 0
               && System.currentTimeMillis() >= nextShotTime;
    }

    /**
     * Fires the weapon, decrements ammo, and sets the cooldown.
     */
    public void shoot(double aimAngle) {
        if (!canShoot()) {
            return;
        }
        this.nextShotTime = System.currentTimeMillis() + weapon.getFireRateCooldown();
        this.lastBulletAngle = aimAngle;
        this.lastShotTime = System.currentTimeMillis();
        this.currentAmmoInMagazine -= weapon.getBulletsPerShot();
    }

    /**
     * Initiates the reload process.
     */
    public void startReload() {
        // Can't reload if already reloading or the magazine is full
        if (isReloading || currentAmmoInMagazine == weapon.getRoundsPerMagazine()) {
            return;
        }
        this.isReloading = true;
        this.reloadCompleteTime = System.currentTimeMillis() + weapon.getReloadTime();
    }

    /**
     * Completes the reload process, filling the magazine.
     */
    public void finishReload() {
        this.isReloading = false;
        this.currentAmmoInMagazine = weapon.getRoundsPerMagazine();
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = StringUtils.abbreviate(playerName, 25);
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public Vector2D getCenter() {
        return new Vector2D(this.x + (Config.PLAYER_SIZE / 2.0), this.y + (Config.PLAYER_SIZE / 2.0));
    }

    public void setVelocity(Vector2D velocity) {
        setVelocityX(velocity.x());
        setVelocityY(velocity.y());
    }

    public double getVelocityX() {
        return velocityX;
    }

    public void setVelocityX(double velocityX) {
        this.velocityX = velocityX;
    }

    public double getVelocityY() {
        return velocityY;
    }

    public void setVelocityY(double velocityY) {
        this.velocityY = velocityY;
    }

    @JsonIgnore
    public Vector2D getVelocity() {
        return new Vector2D(velocityX, velocityY);
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getDefaultSpeed() {
        return defaultSpeed;
    }

    public void setDefaultSpeed(double defaultSpeed) {
        this.defaultSpeed = defaultSpeed;
    }

    public void restoreSpeed() {
        setSpeed(getDefaultSpeed());
    }

    public int getTeam() {
        return team;
    }

    public void setTeam(int team) {
        this.team = team;
    }

    public long getLastInputTime() {
        return lastInputTime;
    }

    public void setLastInputTime(long lastInputTime) {
        this.lastInputTime = lastInputTime;
    }

    public boolean isDead() {
        return isDead;
    }

    public void setDead(boolean dead) {
        isDead = dead;
        if (dead) {
            this.isReloading = false; // Cancel reload on death
        }
    }

    public long getRespawnTime() {
        return respawnTime;
    }

    public void setRespawnTime(long respawnTime) {
        this.respawnTime = respawnTime;
    }

    public int getKills() {
        return kills;
    }

    public void incrementKills() {
        this.kills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void incrementDeaths() {
        this.deaths++;
    }

    public double getLastBulletAngle() {
        return lastBulletAngle;
    }

    public Weapon getWeapon() {
        return weapon;
    }

    public void setWeapon(Weapon weapon) {
        double percentMagRemain = 1;
        if (this.weapon != null) {
            percentMagRemain = (double) currentAmmoInMagazine / this.weapon.getRoundsPerMagazine();
        }
        this.weapon = weapon;
        this.currentAmmoInMagazine = (int) (percentMagRemain * (double) weapon.getRoundsPerMagazine());
        this.isReloading = false;
    }

    public double getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(double currentHealth) {
        this.currentHealth = currentHealth;
    }

    public int getCurrentAmmoInMagazine() {
        return currentAmmoInMagazine;
    }

    public void setCurrentAmmoInMagazine(int currentAmmoInMagazine) {
        this.currentAmmoInMagazine = currentAmmoInMagazine;
    }

    public boolean isReloading() {
        return isReloading;
    }

    public long getReloadCompleteTime() {
        return reloadCompleteTime;
    }

    /**
     * Applies damage to the player. A negative amount will heal the player.
     *
     * @param amount The amount of damage to inflict.
     * @return {@code true} if the player's health dropped to or below zero, {@code false} otherwise.
     */
    public boolean takeDamage(double amount) {
        // If armor is active and the player is taking damage (not being healed)
        if (System.currentTimeMillis() < this.armorUpEndTime && amount > 0) {
            return false; // Invincible, do not take damage
        }
        this.currentHealth -= amount;
        if (this.currentHealth > this.maxHealth) {
            this.currentHealth = this.maxHealth;
        }
        return this.currentHealth <= 0;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    public void resetHealth() {
        this.currentHealth = this.maxHealth;
    }

    /**
     * Resets the player's kills and deaths to zero, typically at the start of a new round.
     */
    public void resetStats() {
        this.kills = 0;
        this.deaths = 0;
        this.armorUpEndTime = 0;
        this.speedBoostEndTime = 0;
    }

    public long getSpeedBoostEndTime() {
        return speedBoostEndTime;
    }

    public void applySpeedBoost(long durationMs) {
        this.speedBoostEndTime = System.currentTimeMillis() + durationMs;
    }

    public void applyArmorUp(long durationMs) {
        this.armorUpEndTime = System.currentTimeMillis() + durationMs;
    }

    public long getArmorUpEndTime() {
        return armorUpEndTime;
    }

    public void applyDamageBoost(long durationMs) {
        this.damageBoostEndTime = System.currentTimeMillis() + durationMs;
        this.damageMultiplier = Config.DAMAGE_BOOST_MULTIPLIER;
    }

    public long getDamageBoostEndTime() {
        return damageBoostEndTime;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    public double getAngle() {
        return lastBulletAngle;
    }

    @JsonIgnore
    public long getLastWeaponChangeTime() {
        return lastWeaponChangeTime;
    }

    public void setLastWeaponChangeTime(long lastWeaponChangeTime) {
        this.lastWeaponChangeTime = lastWeaponChangeTime;
    }

    public long getAlternateActionCooldown() {
        return alternateActionCooldown;
    }

    public void setAlternateActionCooldown(long alternateActionCooldown) {
        this.alternateActionCooldown = alternateActionCooldown;
    }
}
