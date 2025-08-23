package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import io.micronaut.core.annotation.Introspected;
import org.apache.commons.lang3.StringUtils;

@Introspected
public class Player implements HasId, HasLife, Targetable {
    protected final long id;
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
    protected double hp;
    protected double maxHp;
    protected double mouseX;
    protected double mouseY;
    @JsonIgnore
    protected transient long lastInputTime;
    protected boolean isDead;
    protected long respawnTime;
    protected int kills;
    protected int deaths;
    protected int currentAmmoInMagazine;
    protected boolean isReloading;
    @JsonIgnore
    protected long reloadCompleteTime;
    @JsonIgnore
    protected long nextShotTime;
    public long speedBoostEndTime;
    public long armorUpEndTime;
    public long damageBoostEndTime;
    public long invisibilityEndTime;
    @JsonIgnore
    public double damageMultiplier;
    @JsonIgnore
    public boolean visionObscured;
    public Long vehicleId;
    public boolean shootDisabled;

    public Player(long id, double x, double y, int team) {
        this(id, RandomNames.randomName(), x, y, team, WeaponFactory.getDefaultWeapon());
    }

    public Player(long id, String playerName, double x, double y, int team, Weapon weapon) {
        this.id = id;
        this.playerName = playerName;
        this.x = x;
        this.y = y;
        this.team = team;
        this.speed = Config.DEFAULT_PLAYER_SPEED;
        this.defaultSpeed = Config.DEFAULT_PLAYER_SPEED;
        setWeapon(weapon);
        this.hp = Config.DEFAULT_PLAYER_HEALTH;
        this.maxHp = Config.DEFAULT_PLAYER_HEALTH;
        this.velocityY = 0;
        this.lastInputTime = System.currentTimeMillis();
        this.isDead = false;
        this.respawnTime = 0;
        this.kills = 0;
        this.deaths = 0;
        this.mouseX = x;
        this.mouseY = y;
        this.currentAmmoInMagazine = weapon.getRoundsPerMagazine();
        this.isReloading = false;
        this.reloadCompleteTime = 0;
        this.nextShotTime = 0;
        this.speedBoostEndTime = 0;
        this.armorUpEndTime = 0;
        this.damageBoostEndTime = 0;
        this.damageMultiplier = 1.0;
        this.invisibilityEndTime = 0;
        this.shootDisabled = false;
    }

    public void update(long delta) {
        x += delta * velocityX;
        y += delta * velocityY;
    }

    public boolean canShoot() {
        return !isDead()
               && !shootDisabled
               && !isReloading
               && currentAmmoInMagazine > 0
               && System.currentTimeMillis() >= nextShotTime;
    }

    /**
     * Fires the weapon, decrements ammo, and sets the cooldown.
     */
    public void shoot() {
        if (!canShoot()) {
            return;
        }
        this.nextShotTime = System.currentTimeMillis() + weapon.getFireRateCooldown();
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

    @Override
    public long id() {
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

    public Vector2D position() {
        return new Vector2D(x, y);
    }

    public double getMouseX() {
        return mouseX;
    }

    public void setMouseX(double mouseX) {
        this.mouseX = mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }

    public void setMouseY(double mouseY) {
        this.mouseY = mouseY;
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
        } else {
            this.respawnTime = 0;
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

    public double getHp() {
        return hp;
    }

    public void setHp(double hp) {
        this.hp = hp;
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
        this.hp -= amount;
        if (this.hp > this.maxHp) {
            this.hp = this.maxHp;
        }
        return this.hp <= 0;
    }

    public double getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(double maxHp) {
        this.maxHp = maxHp;
    }

    public void resetHp() {
        this.hp = this.maxHp;
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
        this.damageMultiplier = Config.POWER_UP_DAMAGE_BOOST_MULTIPLIER;
    }

    public long getDamageBoostEndTime() {
        return damageBoostEndTime;
    }

    public void setDamageBoostEndTime(long damageBoostEndTime) {
        this.damageBoostEndTime = damageBoostEndTime;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    public long getInvisibilityEndTime() {
        return invisibilityEndTime;
    }

    public void setInvisibilityEndTime(long endTime) {
        this.invisibilityEndTime = endTime;
    }

    public boolean isVisionObscured() {
        return visionObscured;
    }

    public void setVisionObscured(boolean visionObscured) {
        this.visionObscured = visionObscured;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public void shootDisabled(boolean shootDisabled) {
        this.shootDisabled = shootDisabled;
    }
}

