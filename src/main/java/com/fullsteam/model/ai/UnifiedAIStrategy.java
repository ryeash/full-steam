package com.fullsteam.model.ai;

import com.fullsteam.Config;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Objective;
import com.fullsteam.model.Player;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * A unified AI strategy that analyzes the game state dynamically and makes intelligent decisions
 * without requiring game mode-specific logic. The AI evaluates objectives, threats, and opportunities
 * to determine the best course of action.
 */
public class UnifiedAIStrategy implements IAIStrategy {

    @Override
    public void updateAIState(AIPlayer self, GameState gameState) {
        // Analyze the current game state to identify objectives, threats, and opportunities
        GameStateAnalysis analysis = analyzeGameState(self, gameState);
        
        // Make a decision based on the analysis and AI archetype
        AIDecision decision = makeDecision(self, analysis);
        
        // Apply the decision to the AI
        applyDecision(self, decision);
    }

    /**
     * Analyzes the current game state to identify all relevant factors for decision making.
     */
    private GameStateAnalysis analyzeGameState(AIPlayer self, GameState gameState) {
        GameStateAnalysis analysis = new GameStateAnalysis();
        analysis.gameState = gameState;
        
        // Analyze threats (enemies, hazards)
        analysis.threats = analyzeThreatLevel(self, gameState);
        analysis.immediateThreats = findImmediateThreats(self, gameState);
        
        // Analyze objectives (capture points, bases, etc.)
        analysis.objectives = identifyObjectives(self, gameState);
        
        // Analyze opportunities (power-ups, vulnerable enemies, vehicles)
        analysis.opportunities = identifyOpportunities(self, gameState);
        
        // Analyze team situation
        analysis.teamSituation = analyzeTeamSituation(self, gameState);
        
        // Analyze self condition
        analysis.selfCondition = analyzeSelfCondition(self);
        
        return analysis;
    }

    /**
     * Makes a decision based on the game state analysis and AI archetype.
     */
    private AIDecision makeDecision(AIPlayer self, GameStateAnalysis analysis) {
        AIDecision decision = new AIDecision();
        
        // Priority 1: Immediate survival (flee from overwhelming threats)
        if (shouldFlee(self, analysis)) {
            decision.state = AIPlayer.AIState.FLEEING;
            decision.target = analysis.immediateThreats.isEmpty() ? null : analysis.immediateThreats.get(0).position();
            decision.priority = 10;
            decision.reasoning = "Fleeing from immediate threat";
            return decision;
        }
        
        // Priority 1.5: Special CTF logic - Return flag to base if carrying enemy flag
        String[] flagReturnInfo = checkForFlagReturnWithDetails(self, analysis);
        if (flagReturnInfo != null) {
            decision.state = AIPlayer.AIState.CAPTURING_OBJECTIVE;
            decision.target = new Vector2D(Double.parseDouble(flagReturnInfo[0]), Double.parseDouble(flagReturnInfo[1]));
            decision.priority = 10;
            decision.reasoning = flagReturnInfo[2];
            
            // Modify behavior based on archetype when carrying flag
            if (flagReturnInfo[2].contains("SCORE NOW!")) {
                // Can score immediately - all archetypes prioritize this
                decision.priority = 10;
            } else {
                // Can't score yet - archetype affects behavior
                decision.priority = switch (self.archetype()) {
                    case WARRIOR -> 10; // Warriors charge to base regardless
                    case OBJECTIVE_HOUND -> 10; // Objective hounds always prioritize the mission
                    case GUARDIAN -> 8; // Guardians are more cautious when carrying flag
                    case BALANCED -> 9; // Balanced approach
                };
                decision.reasoning += " (archetype: " + self.archetype() + ")";
            }
            return decision;
        }
        
        // Priority 2: Immediate objectives (within interaction range)
        Objective immediateObjective = findImmediateObjective(self, analysis);
        if (immediateObjective != null) {
            // Special handling for different objective types
            if (immediateObjective instanceof com.fullsteam.model.Base base) {
                // When near a base, we should ATTACK it (shoot at it), not just stand there
                if (base.getTeam() != self.getTeam()) {
                    decision.state = AIPlayer.AIState.ATTACKING;
                    decision.target = applyAntiClustering(self, base.position(), analysis, 60.0);
                    decision.priority = 10;
                    decision.reasoning = "Attacking enemy base: " + base.getObjectiveType();
                } else {
                    // Friendly base - defend it by looking for enemies nearby
                    Player nearbyEnemy = findEnemyNearObjective(self, analysis, base, base.getInteractionRadius() * 2);
                    if (nearbyEnemy != null) {
                        decision.state = AIPlayer.AIState.ATTACKING;
                        decision.target = nearbyEnemy.position();
                        decision.priority = 9;
                        decision.reasoning = "Defending friendly base from nearby enemy";
                    } else {
                        decision.state = AIPlayer.AIState.WANDERING;
                        decision.target = applyAntiClustering(self, base.position(), analysis, 80.0);
                        decision.priority = 6;
                        decision.reasoning = "Patrolling friendly base area";
                    }
                }
            } else {
                // Default objective behavior for non-base objectives
                decision.state = AIPlayer.AIState.CAPTURING_OBJECTIVE;
                decision.target = applyAntiClustering(self, immediateObjective.position(), analysis, 50.0);
                decision.priority = 10;
                decision.reasoning = "Immediate objective within interaction range: " + immediateObjective.getObjectiveType();
            }
            return decision;
        }
        
        // Priority 3: Critical objectives (based on archetype and game state)
        ObjectiveInfo criticalObjective = findCriticalObjective(self, analysis);
        if (criticalObjective != null && criticalObjective.priority >= 8) {
            decision.state = AIPlayer.AIState.CAPTURING_OBJECTIVE;
            decision.target = applyAntiClustering(self, criticalObjective.position, analysis, 50.0);
            decision.priority = criticalObjective.priority;
            decision.reasoning = "Pursuing critical objective: " + criticalObjective.type;
            return decision;
        }
        
        // Priority 4: High-value opportunities
        OpportunityInfo bestOpportunity = findBestOpportunity(self, analysis);
        if (bestOpportunity != null && bestOpportunity.priority >= 7) {
            decision.state = getStateForOpportunity(bestOpportunity);
            decision.target = applyAntiClustering(self, bestOpportunity.position, analysis, 40.0);
            decision.priority = bestOpportunity.priority;
            decision.reasoning = "Pursuing opportunity: " + bestOpportunity.type;
            return decision;
        }
        
        // Priority 5: Combat engagement
        Player bestTarget = findBestCombatTarget(self, analysis);
        if (bestTarget != null) {
            double distance = self.position().distance(bestTarget.position());
            double weaponRange = self.getWeapon().getBulletRange();
            double optimalRange = getOptimalEngagementRange(self, weaponRange);
            
            // Determine optimal combat behavior based on distance to target
            if (distance > weaponRange) {
                // Target is out of range - move closer
                decision.state = AIPlayer.AIState.CAPTURING_OBJECTIVE;
                decision.target = bestTarget.position();
                decision.combatTarget = bestTarget;
                decision.priority = 7;
                decision.reasoning = "Closing distance to enemy: " + bestTarget.getName() + " (out of range)";
            } else if (distance < optimalRange * 0.5) {
                // Too close - back away while shooting
                decision.state = AIPlayer.AIState.FLEEING;
                decision.target = bestTarget.position(); // Flee FROM this position
                decision.combatTarget = bestTarget;
                decision.priority = 8;
                decision.reasoning = "Backing away from enemy: " + bestTarget.getName() + " (too close)";
            } else if (Math.abs(distance - optimalRange) > optimalRange * 0.3) {
                // Not at optimal range - reposition
                decision.state = AIPlayer.AIState.CAPTURING_OBJECTIVE;
                Vector2D optimalPos = calculateOptimalPosition(self, bestTarget, optimalRange);
                decision.target = applyAntiClustering(self, optimalPos, analysis, 30.0);
                decision.combatTarget = bestTarget;
                decision.priority = 7;
                decision.reasoning = "Repositioning for optimal range against: " + bestTarget.getName();
            } else {
                // At good range - engage directly
                decision.state = AIPlayer.AIState.ATTACKING;
                decision.target = bestTarget.position();
                decision.combatTarget = bestTarget;
                decision.priority = 8;
                decision.reasoning = "Engaging enemy at optimal range: " + bestTarget.getName();
            }
            return decision;
        }
        
        // Priority 6: Secondary objectives
        if (criticalObjective != null) {
            decision.state = AIPlayer.AIState.CAPTURING_OBJECTIVE;
            decision.target = applyAntiClustering(self, criticalObjective.position, analysis, 50.0);
            decision.priority = criticalObjective.priority;
            decision.reasoning = "Pursuing secondary objective: " + criticalObjective.type;
            return decision;
        }
        
        // Default: Intelligent wandering based on team and map control
        decision.state = AIPlayer.AIState.WANDERING;
        decision.target = generateStrategicWanderTarget(self, analysis);
        decision.combatTarget = null;
        decision.priority = 1;
        decision.reasoning = "Strategic wandering";
        return decision;
    }

    /**
     * Applies the decision to the AI player.
     */
    private void applyDecision(AIPlayer self, AIDecision decision) {
        self.setCurrentState(decision.state);
        self.setObjectiveTargetPoint(decision.target);
        if (decision.combatTarget != null) {
            self.setCurrentTarget(decision.combatTarget);
        } else {
            self.setCurrentTarget(null);
        }
    }

    // === Analysis Methods ===

    private ThreatLevel analyzeThreatLevel(AIPlayer self, GameState gameState) {
        int nearbyEnemies = 0;
        int poweredEnemies = 0;
        int nearbyEnemyVehicles = 0;
        double closestEnemyDistance = Double.MAX_VALUE;
        
        long currentTime = System.currentTimeMillis();
        
        // Analyze enemy players
        for (Player player : gameState.players()) {
            if (player.getTeam() != self.getTeam() && !player.isDead()) {
                double distance = self.position().distance(player.position());
                if (distance < Config.VISION_RANGE) {
                    nearbyEnemies++;
                    closestEnemyDistance = Math.min(closestEnemyDistance, distance);
                    
                    if (player.getDamageBoostEndTime() > currentTime || 
                        player.getArmorUpEndTime() > currentTime || 
                        player.getSpeedBoostEndTime() > currentTime) {
                        poweredEnemies++;
                    }
                }
            }
        }
        
        // Analyze enemy vehicles (they're significant threats)
        for (Vehicle vehicle : gameState.vehicles()) {
            if (vehicle.getTeam() != self.getTeam() && vehicle.getTeam() >= 0 && !vehicle.isDestroyed()) {
                double distance = self.position().distance(vehicle.position());
                if (distance < Config.VISION_RANGE) {
                    nearbyEnemyVehicles++;
                    closestEnemyDistance = Math.min(closestEnemyDistance, distance);
                    
                    // Vehicles with mounted weapons are especially dangerous
                    if (!vehicle.getMountedWeapons().isEmpty()) {
                        nearbyEnemies++; // Count as additional enemy threat
                    }
                }
            }
        }
        
        // Determine threat level (vehicles significantly increase threat)
        int totalThreats = nearbyEnemies + (nearbyEnemyVehicles * 2); // Vehicles count as 2x threat
        
        if (totalThreats == 0) return ThreatLevel.NONE;
        if (totalThreats >= 4 || poweredEnemies >= 2 || nearbyEnemyVehicles >= 2) return ThreatLevel.OVERWHELMING;
        if (totalThreats >= 3 || poweredEnemies >= 1 || nearbyEnemyVehicles >= 1) return ThreatLevel.HIGH;
        if (closestEnemyDistance < 100) return ThreatLevel.MODERATE;
        return ThreatLevel.LOW;
    }

    private List<Player> findImmediateThreats(AIPlayer self, GameState gameState) {
        List<Player> threats = new ArrayList<>();
        
        // Find threatening enemy players
        for (Player player : gameState.players()) {
            if (player.getTeam() != self.getTeam() && !player.isDead()) {
                double distance = self.position().distance(player.position());
                
                // Consider immediate threats based on distance and player state
                if (distance < 150 && !player.isReloading()) {
                    threats.add(player);
                }
            }
        }
        
        // Also consider players in nearby enemy vehicles as threats
        for (Vehicle vehicle : gameState.vehicles()) {
            if (vehicle.getTeam() != self.getTeam() && vehicle.getTeam() >= 0 && !vehicle.isDestroyed()) {
                double distance = self.position().distance(vehicle.position());
                
                // Vehicles with weapons are immediate threats at longer range
                if (distance < 200 && !vehicle.getMountedWeapons().isEmpty()) {
                    // Add the driver as a threat (representing the vehicle threat)
                    Long driverId = vehicle.getDriverId();
                    if (driverId != null) {
                        for (Player player : gameState.players()) {
                            if (player.getId() == driverId && !threats.contains(player)) {
                                threats.add(player);
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        // Sort by distance (closest first)
        threats.sort(Comparator.comparing(p -> self.position().distance(p.position())));
        return threats;
    }

    private List<ObjectiveInfo> identifyObjectives(AIPlayer self, GameState gameState) {
        List<ObjectiveInfo> objectives = new ArrayList<>();
        
        // Use reflection to find all Objective implementations in the game info
        GameInfo gameInfo = gameState.info();
        if (gameInfo != null) {
            objectives.addAll(extractObjectivesFromGameInfo(self, gameInfo, gameState));
        }
        
        return objectives;
    }
    
    /**
     * Extracts all Objective implementations from the game info using reflection.
     * This automatically discovers objectives without game mode-specific code.
     */
    private List<ObjectiveInfo> extractObjectivesFromGameInfo(AIPlayer self, GameInfo gameInfo, GameState gameState) {
        List<ObjectiveInfo> objectives = new ArrayList<>();
        
        try {
            // Get all methods that might return objectives
            var methods = gameInfo.getClass().getMethods();
            
            for (var method : methods) {
                // Skip basic Object methods and getters that don't return objectives
                if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
                    try {
                        Object result = method.invoke(gameInfo);
                        
                        // Handle single objectives
                        if (result instanceof Objective objective) {
                            objectives.add(analyzeObjective(self, objective, gameState));
                        }
                        // Handle lists of objectives
                        else if (result instanceof List<?> list) {
                            for (Object item : list) {
                                if (item instanceof Objective objective) {
                                    objectives.add(analyzeObjective(self, objective, gameState));
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Skip methods that fail to invoke
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: if reflection fails, we'll have empty objectives list
        }
        
        return objectives;
    }
    
    /**
     * Analyzes any Objective implementation using the interface methods.
     * This replaces all the specific objective analysis methods.
     */
    private ObjectiveInfo analyzeObjective(AIPlayer self, Objective objective, GameState gameState) {
        double distance = self.position().distance(objective.position());
        
        // Use the objective's own priority calculation
        int priority = objective.getPriorityForTeam(self.getTeam());
        
        // Special handling for specific objective types with enhanced context
        if (objective instanceof com.fullsteam.model.Oddball oddball) {
            priority = analyzeOddballWithContext(self, oddball, gameState);
        } else if (objective instanceof com.fullsteam.model.Payload payload) {
            priority = analyzePayloadWithContext(self, payload, gameState);
        }
        
        // Apply distance penalty based on interaction radius
        double effectiveRadius = getEffectiveInteractionDistance(self, objective);
        
        if (distance > effectiveRadius * 3) priority -= 2; // Far from objective
        else if (distance > effectiveRadius * 2) priority -= 1; // Moderately far
        // No penalty if within 2x effective interaction distance
        
        // Apply archetype modifiers
        priority = applyArchetypeModifiers(self, objective, priority);
        
        return new ObjectiveInfo(
            objective.getObjectiveType(),
            objective.position(),
            Math.max(1, priority), // Ensure minimum priority of 1
            distance
        );
    }
    
    /**
     * Enhanced analysis for Oddball objectives using game state context.
     */
    private int analyzeOddballWithContext(AIPlayer self, com.fullsteam.model.Oddball oddball, GameState gameState) {
        // If the oddball is carried, try to determine the carrier's team
        if (oddball.state() == com.fullsteam.model.Oddball.OddballState.CARRIED && oddball.carrierId() != null) {
            // Look up the carrier in the game state
            for (Player player : gameState.players()) {
                if (player.getId() == oddball.carrierId()) {
                    // Found the carrier - use enhanced priority calculation
                    return oddball.getPriorityForTeamWithCarrier(self.getTeam(), player.getTeam());
                }
            }
        }
        
        // Fallback to standard priority calculation
        return oddball.getPriorityForTeam(self.getTeam());
    }
    
    /**
     * Enhanced analysis for Payload objectives using game state context.
     */
    private int analyzePayloadWithContext(AIPlayer self, com.fullsteam.model.Payload payload, GameState gameState) {
        // Determine current control status by checking nearby players
        Vector2D payloadPos = payload.position();
        double captureRadiusSq = payload.getCaptureRadius() * payload.getCaptureRadius();
        
        int nearbyTeam1Players = 0;
        int nearbyTeam2Players = 0;
        
        for (Player player : gameState.players()) {
            if (!player.isDead() && player.position().distanceSquared(payloadPos) < captureRadiusSq) {
                if (player.getTeam() == 1) {
                    nearbyTeam1Players++;
                } else if (player.getTeam() == 2) {
                    nearbyTeam2Players++;
                }
            }
        }
        
        // Determine control status
        int controllingTeam = 0;
        boolean isContested = false;
        
        if (nearbyTeam1Players > 0 && nearbyTeam2Players == 0) {
            controllingTeam = 1;
        } else if (nearbyTeam2Players > 0 && nearbyTeam1Players == 0) {
            controllingTeam = 2;
        } else if (nearbyTeam1Players > 0 && nearbyTeam2Players > 0) {
            isContested = true;
            // Keep the controlling team as the one with more players, or 0 if tied
            if (nearbyTeam1Players > nearbyTeam2Players) {
                controllingTeam = 1;
            } else if (nearbyTeam2Players > nearbyTeam1Players) {
                controllingTeam = 2;
            }
        }
        
        // Use enhanced priority calculation with control context
        return payload.getPriorityForTeamWithControl(self.getTeam(), controllingTeam, isContested);
    }
    
    /**
     * Applies AI archetype-specific modifiers to objective priorities.
     */
    private int applyArchetypeModifiers(AIPlayer self, Objective objective, int basePriority) {
        // Special handling for specific objective types
        if (objective instanceof com.fullsteam.model.Oddball oddball) {
            return applyOddballArchetypeModifiers(self, oddball, basePriority);
        } else if (objective instanceof com.fullsteam.model.Payload payload) {
            return applyPayloadArchetypeModifiers(self, payload, basePriority);
        }
        
        return switch (self.archetype()) {
            case GUARDIAN -> {
                // Guardians prioritize defensive objectives
                if (objective.getOwningTeam() == self.getTeam()) {
                    yield basePriority + 2; // Boost defensive objectives
                } else {
                    yield Math.max(1, basePriority - 1); // Slightly reduce offensive objectives
                }
            }
            case WARRIOR -> {
                // Warriors prioritize offensive objectives
                if (objective.getOwningTeam() != self.getTeam() && objective.getOwningTeam() > 0) {
                    yield basePriority + 2; // Boost offensive objectives
                } else {
                    yield basePriority; // No change for neutral/defensive
                }
            }
            case OBJECTIVE_HOUND -> {
                // Objective hounds prioritize high-value objectives
                if (objective.getStrategicValue() >= 7) {
                    yield basePriority + 1; // Boost high-value objectives
                } else {
                    yield basePriority;
                }
            }
            case BALANCED -> basePriority; // No modifiers for balanced
        };
    }
    
    /**
     * Applies archetype-specific modifiers for Oddball objectives.
     */
    private int applyOddballArchetypeModifiers(AIPlayer self, com.fullsteam.model.Oddball oddball, int basePriority) {
        return switch (self.archetype()) {
            case GUARDIAN -> {
                // Guardians are more cautious about picking up the oddball (makes them a target)
                // But they strongly prioritize protecting friendly carriers
                if (oddball.state() == com.fullsteam.model.Oddball.OddballState.CARRIED) {
                    yield basePriority + 1; // Boost priority to protect/eliminate carrier
                } else {
                    yield Math.max(1, basePriority - 1); // Slightly reduce pickup priority
                }
            }
            case WARRIOR -> {
                // Warriors love the action - high priority for all oddball interactions
                yield basePriority + 2;
            }
            case OBJECTIVE_HOUND -> {
                // Objective hounds prioritize the oddball highly (it's the main objective)
                yield basePriority + 2;
            }
            case BALANCED -> basePriority; // No modifiers for balanced
        };
    }
    
    /**
     * Applies archetype-specific modifiers for Payload objectives.
     */
    private int applyPayloadArchetypeModifiers(AIPlayer self, com.fullsteam.model.Payload payload, int basePriority) {
        return switch (self.archetype()) {
            case GUARDIAN -> {
                // Guardians prioritize based on payload position and their team's progress
                double progress = payload.getProgressToTeam1Goal();
                if (self.getTeam() == 1) {
                    // Team 1 guardian - more defensive when payload is close to enemy goal
                    if (progress < 0.3) {
                        yield basePriority + 2; // High priority to push payload away from enemy goal
                    } else {
                        yield basePriority; // Standard priority when safe
                    }
                } else if (self.getTeam() == 2) {
                    // Team 2 guardian - more defensive when payload is close to enemy goal
                    if (progress > 0.7) {
                        yield basePriority + 2; // High priority to push payload away from enemy goal
                    } else {
                        yield basePriority; // Standard priority when safe
                    }
                } else {
                    yield basePriority;
                }
            }
            case WARRIOR -> {
                // Warriors always prioritize payload highly - they love the fight
                yield basePriority + 2;
            }
            case OBJECTIVE_HOUND -> {
                // Objective hounds prioritize payload extremely highly (main objective)
                yield basePriority + 3;
            }
            case BALANCED -> basePriority; // No modifiers for balanced
        };
    }

    private List<OpportunityInfo> identifyOpportunities(AIPlayer self, GameState gameState) {
        List<OpportunityInfo> opportunities = new ArrayList<>();
        
        // Power-up opportunities
        if (gameState.powerUps() != null) {
            for (PowerUp powerUp : gameState.powerUps()) {
                opportunities.add(analyzePowerUpOpportunity(self, powerUp));
            }
        }
        
        // Vulnerable enemy opportunities
        for (Player enemy : gameState.players()) {
            if (enemy.getTeam() != self.getTeam() && !enemy.isDead()) {
                OpportunityInfo vulnOpportunity = analyzeVulnerableEnemy(self, enemy);
                if (vulnOpportunity != null) {
                    opportunities.add(vulnOpportunity);
                }
            }
        }
        
        // Vehicle opportunities
        for (Vehicle vehicle : gameState.vehicles()) {
            OpportunityInfo vehicleOpportunity = analyzeVehicleOpportunity(self, vehicle, gameState);
            if (vehicleOpportunity != null) {
                opportunities.add(vehicleOpportunity);
            }
        }
        
        return opportunities;
    }

    private TeamSituation analyzeTeamSituation(AIPlayer self, GameState gameState) {
        int friendlyCount = 0;
        int enemyCount = 0;
        double avgFriendlyHealth = 0;
        double avgEnemyHealth = 0;
        
        for (Player player : gameState.players()) {
            if (!player.isDead()) {
                if (player.getTeam() == self.getTeam()) {
                    friendlyCount++;
                    avgFriendlyHealth += player.getHp() / player.getMaxHp();
                } else {
                    enemyCount++;
                    avgEnemyHealth += player.getHp() / player.getMaxHp();
                }
            }
        }
        
        avgFriendlyHealth = friendlyCount > 0 ? avgFriendlyHealth / friendlyCount : 0;
        avgEnemyHealth = enemyCount > 0 ? avgEnemyHealth / enemyCount : 0;
        
        return new TeamSituation(friendlyCount, enemyCount, avgFriendlyHealth, avgEnemyHealth);
    }

    private SelfCondition analyzeSelfCondition(AIPlayer self) {
        double healthRatio = self.getHp() / self.getMaxHp();
        boolean hasAmmo = self.getAmmoInMag() > 0;
        boolean isReloading = self.isReloading();
        
        long currentTime = System.currentTimeMillis();
        boolean hasPowerUp = self.getDamageBoostEndTime() > currentTime || 
                           self.getArmorUpEndTime() > currentTime || 
                           self.getSpeedBoostEndTime() > currentTime;
        
        return new SelfCondition(healthRatio, hasAmmo, isReloading, hasPowerUp);
    }

    // === Decision Logic Methods ===

    private boolean shouldFlee(AIPlayer self, GameStateAnalysis analysis) {
        // Archetype-based flee thresholds
        double fleeThreshold = switch (self.archetype()) {
            case WARRIOR -> 0.15; // Very brave
            case GUARDIAN -> 0.4;  // Cautious
            case OBJECTIVE_HOUND -> 0.25; // Mission-focused
            case BALANCED -> 0.3;  // Moderate
        };
        
        // Adjust flee threshold based on team situation
        if (analysis.teamSituation.friendlyCount > analysis.teamSituation.enemyCount) {
            fleeThreshold *= 0.8; // More willing to fight when we have numbers
        } else if (analysis.teamSituation.friendlyCount < analysis.teamSituation.enemyCount) {
            fleeThreshold *= 1.2; // More cautious when outnumbered
        }
        
        // Consider team health situation
        if (analysis.teamSituation.avgFriendlyHealth > analysis.teamSituation.avgEnemyHealth) {
            fleeThreshold *= 0.9; // More willing to fight when team is healthier
        } else if (analysis.teamSituation.avgFriendlyHealth < analysis.teamSituation.avgEnemyHealth) {
            fleeThreshold *= 1.1; // More cautious when team is weaker
        }
        
        // Consider power-ups - less likely to flee if we have advantages
        if (analysis.selfCondition.hasPowerUp) {
            fleeThreshold *= 0.7;
        }
        
        // Consider ammo situation
        if (!analysis.selfCondition.hasAmmo && analysis.selfCondition.isReloading) {
            fleeThreshold *= 1.5; // Much more likely to flee when reloading
        }
        
        return analysis.selfCondition.healthRatio < fleeThreshold && 
               (analysis.threats == ThreatLevel.HIGH || analysis.threats == ThreatLevel.OVERWHELMING);
    }

    /**
     * Special CTF logic: Checks if the AI is carrying an enemy flag and should return to base.
     * Returns [x, y, reasoning] if they should return, null otherwise.
     */
    private String[] checkForFlagReturnWithDetails(AIPlayer self, GameStateAnalysis analysis) {
        GameInfo gameInfo = analysis.gameState.info();
        
        // Only apply to Capture the Flag game mode
        if (gameInfo == null || !"Capture the Flag".equals(gameInfo.getType())) {
            return null;
        }
        
        try {
            // Get both flags from CTF game info
            var getTeam1FlagMethod = gameInfo.getClass().getMethod("getTeam1Flag");
            var getTeam2FlagMethod = gameInfo.getClass().getMethod("getTeam2Flag");
            com.fullsteam.model.Flag team1Flag = (com.fullsteam.model.Flag) getTeam1FlagMethod.invoke(gameInfo);
            com.fullsteam.model.Flag team2Flag = (com.fullsteam.model.Flag) getTeam2FlagMethod.invoke(gameInfo);
            
            com.fullsteam.model.Flag enemyFlag = null;
            com.fullsteam.model.Flag friendlyFlag = null;
            
            if (self.getTeam() == 1) {
                enemyFlag = team2Flag;
                friendlyFlag = team1Flag;
            } else if (self.getTeam() == 2) {
                enemyFlag = team1Flag;
                friendlyFlag = team2Flag;
            }
            
            // Check if this AI is carrying the enemy flag
            if (enemyFlag != null && enemyFlag.state() == com.fullsteam.model.Flag.FlagState.CARRIED && 
                enemyFlag.carrierId() != null && enemyFlag.carrierId().equals(self.getId())) {
                
                // AI is carrying the enemy flag! Check if they can score
                if (friendlyFlag != null && friendlyFlag.state() == com.fullsteam.model.Flag.FlagState.AT_BASE) {
                    // Our flag is at base - we can score! Head to our base
                    Vector2D basePos = friendlyFlag.basePosition();
                    return new String[] {
                        String.valueOf(basePos.x()), 
                        String.valueOf(basePos.y()), 
                        "SCORE NOW! Returning enemy flag to base (our flag is home)"
                    };
                } else {
                    // Our flag is not at base - we can't score yet, but still should head towards base
                    // and position defensively near it for when our flag returns
                    if (friendlyFlag != null) {
                        Vector2D basePos = friendlyFlag.basePosition();
                        String flagStatus = friendlyFlag.state() == com.fullsteam.model.Flag.FlagState.CARRIED ? 
                            "carried by enemy" : "dropped";
                        return new String[] {
                            String.valueOf(basePos.x()), 
                            String.valueOf(basePos.y()), 
                            "Carrying enemy flag to base (waiting for our flag to return - currently " + flagStatus + ")"
                        };
                    }
                }
            }
        } catch (Exception e) {
            // Reflection failed - not a CTF game or different structure
        }
        
        return null;
    }
    
    /**
     * Finds objectives that are within immediate interaction range and should be prioritized.
     */
    private Objective findImmediateObjective(AIPlayer self, GameStateAnalysis analysis) {
        // Look through all discovered objectives to find any that are immediate
        for (ObjectiveInfo objInfo : analysis.objectives) {
            // We need to get the actual Objective instance to check interaction radius
            // This requires looking it up from the game state
            Objective objective = findObjectiveByPosition(self, analysis.gameState, objInfo.position);
            if (objective != null && isObjectiveImmediate(self, objective)) {
                // Found an immediate objective - prioritize it
                return objective;
            }
        }
        return null;
    }
    
    /**
     * Finds the actual Objective instance by position from the game state.
     */
    private Objective findObjectiveByPosition(AIPlayer self, GameState gameState, Vector2D position) {
        // Search through game info for objectives at this position
        GameInfo gameInfo = gameState.info();
        if (gameInfo != null) {
            try {
                var methods = gameInfo.getClass().getMethods();
                for (var method : methods) {
                    if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
                        try {
                            Object result = method.invoke(gameInfo);
                            
                            // Handle single objectives
                            if (result instanceof Objective objective) {
                                if (objective.position().distance(position) < 1.0) { // Small tolerance for floating point
                                    return objective;
                                }
                            }
                            // Handle lists of objectives
                            else if (result instanceof List<?> list) {
                                for (Object item : list) {
                                    if (item instanceof Objective objective) {
                                        if (objective.position().distance(position) < 1.0) {
                                            return objective;
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Skip methods that fail to invoke
                        }
                    }
                }
            } catch (Exception e) {
                // Fallback if reflection fails
            }
        }
        return null;
    }

    private ObjectiveInfo findCriticalObjective(AIPlayer self, GameStateAnalysis analysis) {
        return analysis.objectives.stream()
            .filter(obj -> isObjectiveAccessible(self, obj)) // Only consider accessible objectives
            .max(Comparator.comparing((ObjectiveInfo obj) -> obj.priority)
                .thenComparing(obj -> -obj.distance)) // Prefer closer objectives when priority is equal
            .orElse(null);
    }

    private OpportunityInfo findBestOpportunity(AIPlayer self, GameStateAnalysis analysis) {
        return analysis.opportunities.stream()
            .filter(opp -> opp.priority >= 5) // Only consider high-priority opportunities
            .max(Comparator.comparing((OpportunityInfo opp) -> opp.priority)
                .thenComparing(opp -> -opp.distance)) // Prefer closer opportunities when priority is equal
            .orElse(null);
    }

    private Player findBestCombatTarget(AIPlayer self, GameStateAnalysis analysis) {
        double weaponRange = self.getWeapon().getBulletRange();
        
        // Find enemies within weapon range, prioritizing those at optimal distance
        return analysis.gameState.players().stream()
            .filter(player -> player.getTeam() != self.getTeam())
            .filter(player -> !player.isDead())
            .filter(player -> {
                double distance = self.position().distance(player.position());
                return distance <= weaponRange; // Only consider enemies within weapon range
            })
            .min(Comparator.comparing(player -> {
                double distance = self.position().distance(player.position());
                double optimalRange = getOptimalEngagementRange(self, weaponRange);
                
                // Score based on how close to optimal range (lower score = better)
                double rangeScore = Math.abs(distance - optimalRange);
                
                // Bonus for low health enemies (easier kills)
                double healthPenalty = player.getHp() / player.getMaxHp() * 50;
                
                return rangeScore + healthPenalty;
            }))
            .orElse(null);
    }
    
    /**
     * Calculates the optimal engagement range based on AI archetype and current health.
     */
    private double getOptimalEngagementRange(AIPlayer self, double weaponRange) {
        double healthRatio = self.getHp() / self.getMaxHp();
        
        // Base range multiplier by archetype
        double baseMultiplier = switch (self.archetype()) {
            case WARRIOR -> 0.6; // Aggressive - get closer
            case GUARDIAN -> 0.8; // Cautious - stay farther
            case OBJECTIVE_HOUND -> 0.7; // Balanced but slightly aggressive
            case BALANCED -> 0.7; // Standard range
        };
        
        // Adjust based on health (wounded AI should stay farther)
        double healthMultiplier = healthRatio < 0.3 ? 1.2 : // Stay far when critically wounded
                                 healthRatio < 0.6 ? 1.1 : // Stay slightly farther when damaged
                                 1.0; // Normal range when healthy
        
        return weaponRange * baseMultiplier * healthMultiplier;
    }
    
    /**
     * Calculates an optimal position to engage a target from the desired range.
     */
    private Vector2D calculateOptimalPosition(AIPlayer self, Player target, double optimalRange) {
        Vector2D selfPos = self.position();
        Vector2D targetPos = target.position();
        
        // Calculate direction from target to self
        Vector2D direction = selfPos.subtract(targetPos);
        double currentDistance = direction.magnitude();
        
        if (currentDistance == 0) {
            // If we're exactly on top of the target, pick a random direction
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            direction = new Vector2D(Math.cos(angle), Math.sin(angle));
        } else {
            // Normalize the direction
            direction = direction.normalize();
        }
        
        // Calculate the optimal position at the desired range
        Vector2D optimalPos = targetPos.add(direction.multiply(optimalRange));
        
        // Ensure the position is within game bounds
        double clampedX = Math.max(50, Math.min(Config.GAME_WIDTH - 50, optimalPos.x()));
        double clampedY = Math.max(50, Math.min(Config.GAME_HEIGHT - 50, optimalPos.y()));
        
        return new Vector2D(clampedX, clampedY);
    }
    
    /**
     * Finds an enemy player near a specific objective (for base defense, etc.)
     */
    private Player findEnemyNearObjective(AIPlayer self, GameStateAnalysis analysis, Objective objective, double searchRadius) {
        Vector2D objectivePos = objective.position();
        
        return analysis.gameState.players().stream()
            .filter(player -> player.getTeam() != self.getTeam())
            .filter(player -> !player.isDead())
            .filter(player -> player.position().distance(objectivePos) <= searchRadius)
            .min(Comparator.comparing(player -> self.position().distance(player.position())))
            .orElse(null);
    }

    private boolean shouldEngageDirectly(AIPlayer self, Player target, GameStateAnalysis analysis) {
        double distance = self.position().distance(target.position());
        double weaponRange = self.getWeapon().getBulletRange();
        
        // Archetype-based engagement preferences
        return switch (self.archetype()) {
            case WARRIOR -> distance < weaponRange * 0.8; // Aggressive
            case GUARDIAN -> distance < weaponRange * 0.6 && analysis.selfCondition.healthRatio > 0.5; // Cautious
            case OBJECTIVE_HOUND -> distance < weaponRange * 0.5; // Quick engagement
            case BALANCED -> distance < weaponRange * 0.7; // Moderate
        };
    }

    // === Helper Methods ===
    
    /**
     * Determines if an objective is accessible based on distance and interaction requirements.
     */
    private boolean isObjectiveAccessible(AIPlayer self, ObjectiveInfo objectiveInfo) {
        // Always consider objectives accessible unless we have specific exclusion criteria
        // This could be enhanced to check for obstacles blocking the path, etc.
        return true;
    }
    
    /**
     * Checks if the AI is within interaction range of an objective.
     */
    private boolean isWithinInteractionRange(AIPlayer self, Objective objective) {
        double distance = self.position().distance(objective.position());
        return distance <= objective.getInteractionRadius();
    }
    
    /**
     * Calculates effective interaction distance considering AI archetype and objective type.
     */
    private double getEffectiveInteractionDistance(AIPlayer self, Objective objective) {
        double baseRadius = objective.getInteractionRadius();
        
        // Archetype modifiers for interaction distance
        return switch (self.archetype()) {
            case GUARDIAN -> baseRadius * 1.2; // Guardians stay closer to objectives
            case WARRIOR -> baseRadius * 0.8; // Warriors get closer before engaging
            case OBJECTIVE_HOUND -> baseRadius * 1.5; // Objective hounds start focusing from farther away
            case BALANCED -> baseRadius; // Standard interaction distance
        };
    }
    
    /**
     * Determines if the AI should consider an objective as "immediate priority" based on proximity.
     */
    private boolean isObjectiveImmediate(AIPlayer self, Objective objective) {
        double distance = self.position().distance(objective.position());
        double effectiveRadius = getEffectiveInteractionDistance(self, objective);
        
        // Consider it immediate if we're within 1.5x the effective interaction distance
        return distance <= (effectiveRadius * 1.5);
    }

    private OpportunityInfo analyzePowerUpOpportunity(AIPlayer self, PowerUp powerUp) {
        double distance = self.position().distance(powerUp.getPosition());
        int priority = 3; // Base priority
        
        // Adjust priority based on power-up type and self condition
        String powerUpTypeName = powerUp.getType().name();
        priority += switch (powerUpTypeName) {
            case "HEALTH_PACK" -> (int) ((1.0 - self.getHp() / self.getMaxHp()) * 5);
            case "DAMAGE_BOOST" -> 4;
            case "ARMOR_UP" -> 3;
            case "SPEED_BOOST" -> 2;
            default -> 1;
        };
        
        // Adjust for distance
        if (distance > 200) priority -= 2;
        
        return new OpportunityInfo("Power-up: " + powerUp.getType(), powerUp.getPosition(), priority, distance);
    }

    private OpportunityInfo analyzeVulnerableEnemy(AIPlayer self, Player enemy) {
        double distance = self.position().distance(enemy.position());
        if (distance > Config.VISION_RANGE) return null;
        
        int priority = 5; // Base combat priority
        
        // Increase priority for vulnerable enemies
        double healthRatio = enemy.getHp() / enemy.getMaxHp();
        if (healthRatio < 0.3) priority += 3;
        if (enemy.isReloading()) priority += 2;
        
        // Decrease priority for powered enemies
        long currentTime = System.currentTimeMillis();
        if (enemy.getDamageBoostEndTime() > currentTime) priority -= 2;
        if (enemy.getArmorUpEndTime() > currentTime) priority -= 1;
        
        return new OpportunityInfo("Vulnerable Enemy", enemy.position(), priority, distance);
    }

    private OpportunityInfo analyzeVehicleOpportunity(AIPlayer self, Vehicle vehicle, GameState gameState) {
        // Only consider vehicles we can enter
        if (vehicle.getTeam() >= 0 && vehicle.getTeam() != self.getTeam()) return null;
        if (vehicle.isDestroyed()) return null;
        
        double distance = self.position().distance(vehicle.position());
        if (distance > 300) return null; // Too far
        
        int priority = 2; // Base priority
        String opportunityType = "Vehicle";
        
        // Analyze vehicle type and current situation
        Vehicle.VehicleType vehicleType = vehicle.getVehicleType();
        
        // Check if vehicle has available seats
        boolean hasAvailableSeats = vehicle.getPassengerIds().size() < vehicle.getMaxPassengers();
        if (!hasAvailableSeats) return null; // Can't enter if full
        
        // Analyze current occupants
        Long driverId = vehicle.getDriverId();
        boolean hasHumanDriver = false;
        boolean hasTeammateDriver = false;
        
        if (driverId != null) {
            for (Player player : gameState.players()) {
                if (player.getId() == driverId) {
                    if (player.getTeam() == self.getTeam()) {
                        hasTeammateDriver = true;
                        if (!(player instanceof AIPlayer)) {
                            hasHumanDriver = true;
                        }
                    }
                    break;
                }
            }
        }
        
        // Priority adjustments based on vehicle type and situation
        if (hasHumanDriver) {
            priority += 5; // High priority for supporting human teammates
            opportunityType = "Support Human Driver";
        } else if (hasTeammateDriver) {
            priority += 3; // Medium priority for supporting AI teammates
            opportunityType = "Support Teammate";
        } else if (driverId == null) {
            // Empty vehicle - priority based on type and archetype
            priority += switch (vehicleType) {
                case TANK -> 4; // High value target
                case MECH -> 3; // Good combat vehicle
                case DAVINCI -> 2; // Utility vehicle
                case JEEP -> 1; // Basic transport
                case FIXED_CANNON -> self.archetype() == AIArchetype.GUARDIAN ? 4 : 2; // Guardians love defensive positions
            };
            opportunityType = "Empty " + vehicleType.name();
        }
        
        // Adjust priority based on vehicle health
        double healthRatio = vehicle.getHp() / vehicle.getMaxHp();
        if (healthRatio < 0.3) {
            priority -= 2; // Avoid nearly destroyed vehicles
        } else if (healthRatio > 0.8) {
            priority += 1; // Prefer healthy vehicles
        }
        
        // Adjust based on self condition
        if (self.getHp() / self.getMaxHp() < 0.4) {
            priority += 2; // Vehicles provide protection when wounded
        }
        
        // Distance penalty
        if (distance > 150) priority -= 1;
        if (distance > 250) priority -= 1;
        
        return new OpportunityInfo(opportunityType, vehicle.position(), priority, distance);
    }

    private AIPlayer.AIState getStateForOpportunity(OpportunityInfo opportunity) {
        if (opportunity.type.contains("Vehicle")) {
            return AIPlayer.AIState.SEEKING_VEHICLE;
        }
        return AIPlayer.AIState.CAPTURING_OBJECTIVE;
    }

    private Vector2D generateStrategicWanderTarget(AIPlayer self, GameStateAnalysis analysis) {
        // Generate wander targets based on team position and map control
        double teamBias = self.getTeam() == 1 ? 0.3 : 0.7;
        double variance = 0.4;
        
        double xBias = teamBias + (ThreadLocalRandom.current().nextGaussian() * variance);
        xBias = Math.max(0.1, Math.min(0.9, xBias));
        
        double x = Config.GAME_WIDTH * xBias;
        double y = ThreadLocalRandom.current().nextDouble(50, Config.GAME_HEIGHT - 50);
        
        Vector2D baseTarget = new Vector2D(x, y);
        
        // Apply anti-clustering to spread out AI players
        return applyAntiClustering(self, baseTarget, analysis, 80.0);
    }

    /**
     * Applies anti-clustering logic to prevent AI players from all converging on the same point.
     * Adjusts the target position to maintain spacing between friendly AI players.
     */
    private Vector2D applyAntiClustering(AIPlayer self, Vector2D originalTarget, GameStateAnalysis analysis, double minSpacing) {
        List<AIPlayer> nearbyFriendlyAI = findNearbyFriendlyAI(self, analysis, originalTarget, minSpacing * 2);
        
        if (nearbyFriendlyAI.isEmpty()) {
            return originalTarget; // No clustering issue
        }
        
        // Calculate repulsion forces from nearby friendly AI
        Vector2D repulsionForce = Vector2D.ZERO;
        int repulsionCount = 0;
        
        for (AIPlayer friendlyAI : nearbyFriendlyAI) {
            Vector2D direction = originalTarget.subtract(friendlyAI.position());
            double distance = direction.magnitude();
            
            if (distance > 0 && distance < minSpacing * 2) {
                // Stronger repulsion when closer
                double repulsionStrength = (minSpacing * 2 - distance) / (minSpacing * 2);
                repulsionForce = repulsionForce.add(direction.normalize().multiply(repulsionStrength * minSpacing));
                repulsionCount++;
            }
        }
        
        // If too many AI are clustered, use a more aggressive spreading approach
        if (repulsionCount >= 3) {
            // Create a radial distribution pattern
            double angleOffset = (self.getId() % 8) * (Math.PI / 4); // 8 directions
            double spreadDistance = minSpacing * (1 + repulsionCount * 0.3);
            
            Vector2D radialOffset = new Vector2D(
                Math.cos(angleOffset) * spreadDistance,
                Math.sin(angleOffset) * spreadDistance
            );
            
            Vector2D adjustedTarget = originalTarget.add(radialOffset);
            
            // Ensure the adjusted target is within game bounds
            double clampedX = Math.max(50, Math.min(Config.GAME_WIDTH - 50, adjustedTarget.x()));
            double clampedY = Math.max(50, Math.min(Config.GAME_HEIGHT - 50, adjustedTarget.y()));
            
            return new Vector2D(clampedX, clampedY);
        }
        
        // Apply normal repulsion force to spread out the target
        Vector2D adjustedTarget = originalTarget.add(repulsionForce);
        
        // Ensure the adjusted target is within game bounds
        double clampedX = Math.max(50, Math.min(Config.GAME_WIDTH - 50, adjustedTarget.x()));
        double clampedY = Math.max(50, Math.min(Config.GAME_HEIGHT - 50, adjustedTarget.y()));
        
        return new Vector2D(clampedX, clampedY);
    }
    
    /**
     * Finds nearby friendly AI players that might cause clustering.
     */
    private List<AIPlayer> findNearbyFriendlyAI(AIPlayer self, GameStateAnalysis analysis, Vector2D targetPosition, double searchRadius) {
        return analysis.gameState.players().stream()
            .filter(player -> player instanceof AIPlayer)
            .map(player -> (AIPlayer) player)
            .filter(ai -> ai.getId() != self.getId()) // Exclude self
            .filter(ai -> ai.getTeam() == self.getTeam()) // Only friendly AI
            .filter(ai -> !ai.isDead()) // Only alive AI
            .filter(ai -> {
                // Check if AI is near the target position
                double distanceToTarget = ai.position().distance(targetPosition);
                return distanceToTarget < searchRadius;
            })
            .collect(Collectors.toList());
    }

    // === Data Classes ===

    private static class GameStateAnalysis {
        ThreatLevel threats;
        List<Player> immediateThreats;
        List<ObjectiveInfo> objectives;
        List<OpportunityInfo> opportunities;
        TeamSituation teamSituation;
        SelfCondition selfCondition;
        GameState gameState;
    }

    private static class AIDecision {
        AIPlayer.AIState state;
        Vector2D target;
        Player combatTarget;
        int priority;
        String reasoning;
        
        AIDecision() {
            // Default constructor
        }
        
        AIDecision(AIPlayer.AIState state, Vector2D target, Player combatTarget, int priority, String reasoning) {
            this.state = state;
            this.target = target;
            this.combatTarget = combatTarget;
            this.priority = priority;
            this.reasoning = reasoning;
        }
    }

    private static class ObjectiveInfo {
        String type;
        Vector2D position;
        int priority;
        double distance;
        
        ObjectiveInfo(String type, Vector2D position, int priority, double distance) {
            this.type = type;
            this.position = position;
            this.priority = priority;
            this.distance = distance;
        }
    }

    private static class OpportunityInfo {
        String type;
        Vector2D position;
        int priority;
        double distance;
        
        OpportunityInfo(String type, Vector2D position, int priority, double distance) {
            this.type = type;
            this.position = position;
            this.priority = priority;
            this.distance = distance;
        }
    }

    private static class TeamSituation {
        int friendlyCount;
        int enemyCount;
        double avgFriendlyHealth;
        double avgEnemyHealth;
        
        TeamSituation(int friendlyCount, int enemyCount, double avgFriendlyHealth, double avgEnemyHealth) {
            this.friendlyCount = friendlyCount;
            this.enemyCount = enemyCount;
            this.avgFriendlyHealth = avgFriendlyHealth;
            this.avgEnemyHealth = avgEnemyHealth;
        }
    }

    private static class SelfCondition {
        double healthRatio;
        boolean hasAmmo;
        boolean isReloading;
        boolean hasPowerUp;
        
        SelfCondition(double healthRatio, boolean hasAmmo, boolean isReloading, boolean hasPowerUp) {
            this.healthRatio = healthRatio;
            this.hasAmmo = hasAmmo;
            this.isReloading = isReloading;
            this.hasPowerUp = hasPowerUp;
        }
    }

    private enum ThreatLevel {
        NONE, LOW, MODERATE, HIGH, OVERWHELMING
    }
}
