package com.sandship.stability.loading_optimizer;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import com.sandship.stability.config.CargoParamsConfig;
import com.sandship.stability.config.ShipParamsConfig;
import com.sandship.stability.config.StabilityConfig;
import com.sandship.stability.dto.LoadingOptimizationRequest;
import com.sandship.stability.dto.LoadingOptimizationResultDTO;
import com.sandship.stability.entity.*;
import com.sandship.stability.events.LoadingOptimizedEvent;
import com.sandship.stability.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.FastMath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class LoadingOptimizerService {

    static {
        try {
            Loader.loadNativeLibraries();
        } catch (Exception e) {
            log.warn("[Loading Optimizer] OR-Tools加载失败，将使用纯启发式模式: {}", e.getMessage());
        }
    }

    private static final int HEURISTIC_THRESHOLD = 20;
    private static final long MIP_TIME_LIMIT = 30000;
    private static final double GRAIN_UNIT_WEIGHT = 0.5;
    private static final double SALT_UNIT_WEIGHT = 0.8;
    private static final double GRAIN_UNIT_VOLUME = 0.8;
    private static final double SALT_UNIT_VOLUME = 0.4;

    @Autowired
    private LoadingOptimizationRepository optimizationRepository;

    @Autowired
    private CargoLoadingRepository cargoLoadingRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private CargoHoldRepository cargoHoldRepository;

    @Autowired
    private CargoTypeRepository cargoTypeRepository;

    @Autowired
    private StabilityConfig stabilityConfig;

    @Autowired
    private ShipParamsConfig shipParamsConfig;

    @Autowired
    private CargoParamsConfig cargoParamsConfig;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Transactional
    public LoadingOptimizationResultDTO optimizeLoading(LoadingOptimizationRequest request) {
        UUID shipId = request.getShipId();
        long startTime = System.currentTimeMillis();

        Optional<Ship> shipOpt = shipRepository.findById(shipId);
        if (shipOpt.isEmpty()) {
            return buildErrorResult(shipId, "船舶不存在: " + shipId);
        }

        Ship ship = shipOpt.get();
        List<CargoHold> cargoHolds = cargoHoldRepository.findByShipIdOrderByHoldNumber(shipId);
        if (cargoHolds.isEmpty()) {
            return buildErrorResult(shipId, "未找到货舱配置");
        }

        cargoHolds.removeIf(h -> Boolean.TRUE.equals(h.getIsTank()));

        Optional<CargoType> grainOpt = cargoTypeRepository.findByCargoCode("GRAIN");
        Optional<CargoType> saltOpt = cargoTypeRepository.findByCargoCode("SALT");
        CargoType grain = grainOpt.orElse(null);
        CargoType salt = saltOpt.orElse(null);

        if (grain == null || salt == null) {
            return buildErrorResult(shipId, "货物类型未初始化");
        }

        double minGm = request.getMinGm() != null
                ? request.getMinGm().doubleValue()
                : cargoParamsConfig.getOptimization().getMinGmRequired().doubleValue();

        OptimizationResult result;
        int problemSize = cargoHolds.size() * 2;

        if (problemSize > HEURISTIC_THRESHOLD || Boolean.TRUE.equals(request.getUseHeuristic())) {
            result = solveWithHeuristic(ship, cargoHolds, grain, salt,
                    request.getGrainWeight() != null ? request.getGrainWeight().doubleValue() : ship.getDeadweightTons().doubleValue() * 0.6,
                    request.getSaltWeight() != null ? request.getSaltWeight().doubleValue() : ship.getDeadweightTons().doubleValue() * 0.4,
                    minGm,
                    request.getMaxTrim() != null ? request.getMaxTrim().doubleValue() : 0.5);

            long elapsed = System.currentTimeMillis() - startTime;
            long remainingTime = MIP_TIME_LIMIT - elapsed;

            if (result.isFeasible() && Boolean.TRUE.equals(request.getRefineWithMip()) && remainingTime > 5000) {
                OptimizationResult mipResult = solveIntegerProgramming(ship, cargoHolds, grain, salt,
                        request.getGrainWeight() != null ? request.getGrainWeight().doubleValue() : ship.getDeadweightTons().doubleValue() * 0.6,
                        request.getSaltWeight() != null ? request.getSaltWeight().doubleValue() : ship.getDeadweightTons().doubleValue() * 0.4,
                        minGm,
                        request.getMaxTrim() != null ? request.getMaxTrim().doubleValue() : 0.5,
                        result, remainingTime);
                if (mipResult.isFeasible() && mipResult.objectiveValue > result.objectiveValue) {
                    result = mipResult;
                    result.algorithmUsed = "MIP_REFINED";
                }
            }
        } else {
            result = solveIntegerProgramming(ship, cargoHolds, grain, salt,
                    request.getGrainWeight() != null ? request.getGrainWeight().doubleValue() : ship.getDeadweightTons().doubleValue() * 0.6,
                    request.getSaltWeight() != null ? request.getSaltWeight().doubleValue() : ship.getDeadweightTons().doubleValue() * 0.4,
                    minGm,
                    request.getMaxTrim() != null ? request.getMaxTrim().doubleValue() : 0.5,
                    null, MIP_TIME_LIMIT);
        }

        long solveTime = System.currentTimeMillis() - startTime;
        result.solveTimeMs = solveTime;

        LoadingOptimization entity = saveOptimizationResult(ship, result, grain, salt, request);

        if (result.isFeasible()) {
            saveLoadingPlan(shipId, cargoHolds, grain, salt, result);
        }

        LoadingOptimizationResultDTO dto = convertToDTO(entity, cargoHolds, result, grain, salt);
        eventPublisher.publishEvent(new LoadingOptimizedEvent(this, shipId, dto));

        log.info("[Loading Optimizer] 优化完成 - 船舶: {}, 算法: {}, 目标值: {}, 耗时: {}ms",
                shipId, result.algorithmUsed, result.objectiveValue, solveTime);

        return dto;
    }

    private OptimizationResult solveIntegerProgramming(Ship ship, List<CargoHold> holds,
                                                        CargoType grain, CargoType salt,
                                                        double maxGrain, double maxSalt,
                                                        double minGm, double maxTrim,
                                                        OptimizationResult initialGuess, long timeLimit) {
        String solverId = "CBC_MIXED_INTEGER_PROGRAMMING";
        MPSolver solver = MPSolver.createSolver(solverId);
        if (solver == null) {
            return fallbackToHeuristic(ship, holds, grain, salt, maxGrain, maxSalt, minGm, maxTrim);
        }

        solver.setNumThreads(4);
        solver.setTimeLimit(timeLimit);

        int n = holds.size();
        MPVariable[][] x = new MPVariable[n][2];
        double[][] holdWeights = new double[n][2];
        double[][] holdVolumes = new double[n][2];
        double grainPriority = 1.0;
        double saltPriority = 1.0;

        for (int i = 0; i < n; i++) {
            CargoHold hold = holds.get(i);
            double maxUnitsByWeight = hold.getMaxWeight().doubleValue() / GRAIN_UNIT_WEIGHT;
            double maxUnitsByVolume = hold.getCapacityCubic().doubleValue() / GRAIN_UNIT_VOLUME;
            int maxGrainUnits = (int) Math.min(maxUnitsByWeight, maxUnitsByVolume);

            maxUnitsByWeight = hold.getMaxWeight().doubleValue() / SALT_UNIT_WEIGHT;
            maxUnitsByVolume = hold.getCapacityCubic().doubleValue() / SALT_UNIT_VOLUME;
            int maxSaltUnits = (int) Math.min(maxUnitsByWeight, maxUnitsByVolume);

            x[i][0] = solver.makeIntVar(0, maxGrainUnits, "x" + i + "_grain");
            x[i][1] = solver.makeIntVar(0, maxSaltUnits, "x" + i + "_salt");

            holdWeights[i][0] = GRAIN_UNIT_WEIGHT;
            holdWeights[i][1] = SALT_UNIT_WEIGHT;
            holdVolumes[i][0] = GRAIN_UNIT_VOLUME;
            holdVolumes[i][1] = SALT_UNIT_VOLUME;
        }

        double totalGrainWeight = 0, totalSaltWeight = 0;
        for (int i = 0; i < n; i++) {
            totalGrainWeight += x[i][0].safeBounds().upperBound() * holdWeights[i][0];
            totalSaltWeight += x[i][1].safeBounds().upperBound() * holdWeights[i][1];
        }

        MPConstraint grainConstraint = solver.makeConstraint(0, maxGrain, "total_grain");
        MPConstraint saltConstraint = solver.makeConstraint(0, maxSalt, "total_salt");
        MPConstraint[] weightConstraints = new MPConstraint[n];
        MPConstraint[] volumeConstraints = new MPConstraint[n];
        MPConstraint lcgConstraint = solver.makeConstraint(-maxTrim, maxTrim, "lcg_balance");

        for (int i = 0; i < n; i++) {
            grainConstraint.setCoefficient(x[i][0], holdWeights[i][0]);
            saltConstraint.setCoefficient(x[i][1], holdWeights[i][1]);

            weightConstraints[i] = solver.makeConstraint(0, holds.get(i).getMaxWeight().doubleValue(), "weight_" + i);
            weightConstraints[i].setCoefficient(x[i][0], holdWeights[i][0]);
            weightConstraints[i].setCoefficient(x[i][1], holdWeights[i][1]);

            volumeConstraints[i] = solver.makeConstraint(0, holds.get(i).getCapacityCubic().doubleValue(), "volume_" + i);
            volumeConstraints[i].setCoefficient(x[i][0], holdVolumes[i][0]);
            volumeConstraints[i].setCoefficient(x[i][1], holdVolumes[i][1]);

            double lcgCoef = holds.get(i).getCenterGravityX().doubleValue() / ship.getLengthOverall().doubleValue();
            lcgConstraint.setCoefficient(x[i][0], lcgCoef * holdWeights[i][0] / Math.max(maxGrain + maxSalt, 1));
            lcgConstraint.setCoefficient(x[i][1], lcgCoef * holdWeights[i][1] / Math.max(maxGrain + maxSalt, 1));
        }

        MPObjective objective = solver.objective();
        for (int i = 0; i < n; i++) {
            objective.setCoefficient(x[i][0], holdWeights[i][0] * grainPriority);
            objective.setCoefficient(x[i][1], holdWeights[i][1] * saltPriority);
        }
        objective.setMaximization();

        if (initialGuess != null && initialGuess.solution != null) {
            MPVariable[] hintVars = new MPVariable[n * 2];
            double[] hintValues = new double[n * 2];
            for (int i = 0; i < n; i++) {
                hintVars[i * 2] = x[i][0];
                hintVars[i * 2 + 1] = x[i][1];
                hintValues[i * 2] = initialGuess.solution[i][0];
                hintValues[i * 2 + 1] = initialGuess.solution[i][1];
            }
            solver.setHint(hintVars, hintValues);
        }

        MPSolver.ResultStatus resultStatus = solver.solve();

        OptimizationResult result = new OptimizationResult(n);
        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {
            result.feasible = true;
            result.objectiveValue = objective.value();
            for (int i = 0; i < n; i++) {
                result.solution[i][0] = (int) Math.round(x[i][0].solutionValue());
                result.solution[i][1] = (int) Math.round(x[i][1].solutionValue());
            }
            result.cgZ = calculateCGZ(holds, result.solution);
            result.gm = calculateGM(ship, result.cgZ, holds, result.solution);
            result.algorithmUsed = "MIP_EXACT";

            if (result.gm < minGm) {
                result = adjustForGmConstraint(ship, holds, result, grain, salt, maxGrain, maxSalt, minGm);
            }
        } else {
            result = fallbackToHeuristic(ship, holds, grain, salt, maxGrain, maxSalt, minGm, maxTrim);
        }

        return result;
    }

    private OptimizationResult solveWithHeuristic(Ship ship, List<CargoHold> holds,
                                                   CargoType grain, CargoType salt,
                                                   double maxGrain, double maxSalt,
                                                   double minGm, double maxTrim) {
        int n = holds.size();
        OptimizationResult result = buildSolution(n);

        List<Integer> sortedIndices = new ArrayList<>();
        for (int i = 0; i < n; i++) sortedIndices.add(i);
        sortedIndices.sort((a, b) -> {
            double scoreA = -holds.get(a).getCenterGravityZ().doubleValue() * 2
                    + holds.get(a).getCapacityCubic().doubleValue() * 0.01;
            double scoreB = -holds.get(b).getCenterGravityZ().doubleValue() * 2
                    + holds.get(b).getCapacityCubic().doubleValue() * 0.01;
            return Double.compare(scoreB, scoreA);
        });

        double remainingGrain = maxGrain;
        double remainingSalt = maxSalt;

        for (int idx : sortedIndices) {
            CargoHold hold = holds.get(idx);
            double remainingCapacityWeight = hold.getMaxWeight().doubleValue();
            double remainingCapacityVolume = hold.getCapacityCubic().doubleValue();

            if (remainingGrain > 0) {
                double maxByWeight = remainingCapacityWeight / GRAIN_UNIT_WEIGHT;
                double maxByVolume = remainingCapacityVolume / GRAIN_UNIT_VOLUME;
                double maxByCargo = remainingGrain / GRAIN_UNIT_WEIGHT;
                int units = (int) Math.floor(Math.min(Math.min(maxByWeight, maxByVolume), maxByCargo));

                if (units > 0) {
                    result.solution[idx][0] = units;
                    double loadedWeight = units * GRAIN_UNIT_WEIGHT;
                    double loadedVolume = units * GRAIN_UNIT_VOLUME;
                    remainingGrain -= loadedWeight;
                    remainingCapacityWeight -= loadedWeight;
                    remainingCapacityVolume -= loadedVolume;
                    result.objectiveValue += loadedWeight;
                }
            }

            if (remainingSalt > 0 && remainingCapacityWeight > 0) {
                double maxByWeight = remainingCapacityWeight / SALT_UNIT_WEIGHT;
                double maxByVolume = remainingCapacityVolume / SALT_UNIT_VOLUME;
                double maxByCargo = remainingSalt / SALT_UNIT_WEIGHT;
                int units = (int) Math.floor(Math.min(Math.min(maxByWeight, maxByVolume), maxByCargo));

                if (units > 0) {
                    result.solution[idx][1] = units;
                    double loadedWeight = units * SALT_UNIT_WEIGHT;
                    remainingSalt -= loadedWeight;
                    result.objectiveValue += loadedWeight;
                }
            }
        }

        result.feasible = true;
        result.cgZ = calculateCGZ(holds, result.solution);
        result.gm = calculateGM(ship, result.cgZ, holds, result.solution);
        result.algorithmUsed = "HEURISTIC_GREEDY";

        result = localSearchOptimization(ship, holds, grain, salt, maxGrain, maxSalt, result);

        if (result.gm < minGm) {
            result = adjustForGmConstraintHeuristic(ship, holds, result, grain, salt, maxGrain, maxSalt, minGm);
        }

        return result;
    }

    private OptimizationResult localSearchOptimization(Ship ship, List<CargoHold> holds,
                                                       CargoType grain, CargoType salt,
                                                       double maxGrain, double maxSalt,
                                                       OptimizationResult current) {
        int n = holds.size();
        boolean improved = true;
        int iterations = 0;
        int maxIterations = 50;

        while (improved && iterations < maxIterations) {
            improved = false;
            iterations++;

            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    for (int ci = 0; ci < 2; ci++) {
                        for (int cj = 0; cj < 2; cj++) {
                            if (current.solution[i][ci] > 0) {
                                double weightCI = ci == 0 ? GRAIN_UNIT_WEIGHT : SALT_UNIT_WEIGHT;
                                double weightCJ = cj == 0 ? GRAIN_UNIT_WEIGHT : SALT_UNIT_WEIGHT;
                                double volumeCI = ci == 0 ? GRAIN_UNIT_VOLUME : SALT_UNIT_VOLUME;
                                double volumeCJ = cj == 0 ? GRAIN_UNIT_VOLUME : SALT_UNIT_VOLUME;

                                int maxMove = Math.min(current.solution[i][ci],
                                        (int) ((holds.get(j).getMaxWeight().doubleValue() -
                                                getHoldWeight(holds.get(j), current.solution[j])) / weightCJ));
                                maxMove = Math.min(maxMove,
                                        (int) ((holds.get(j).getCapacityCubic().doubleValue() -
                                                getHoldVolume(holds.get(j), current.solution[j])) / volumeCJ));

                                if (maxMove > 0) {
                                    current.solution[i][ci]--;
                                    current.solution[j][cj]++;

                                    double newCGZ = calculateCGZ(holds, current.solution);
                                    double newGM = calculateGM(ship, newCGZ, holds, current.solution);

                                    if (newGM > current.gm && newGM >= 0.3) {
                                        current.cgZ = newCGZ;
                                        current.gm = newGM;
                                        improved = true;
                                    } else {
                                        current.solution[i][ci]++;
                                        current.solution[j][cj]--;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return current;
    }

    private OptimizationResult adjustForGmConstraint(Ship ship, List<CargoHold> holds,
                                                      OptimizationResult result, CargoType grain, CargoType salt,
                                                      double maxGrain, double maxSalt, double minGm) {
        int n = holds.size();
        int maxAdjustments = 100;
        int adjustments = 0;

        while (result.gm < minGm && adjustments < maxAdjustments) {
            int highestIdx = -1;
            double highestZ = -1;

            for (int i = 0; i < n; i++) {
                if ((result.solution[i][0] > 0 || result.solution[i][1] > 0)
                        && holds.get(i).getCenterGravityZ().doubleValue() > highestZ) {
                    highestIdx = i;
                    highestZ = holds.get(i).getCenterGravityZ().doubleValue();
                }
            }

            if (highestIdx < 0) break;

            if (result.solution[highestIdx][0] > 0) {
                result.solution[highestIdx][0]--;
                result.objectiveValue -= GRAIN_UNIT_WEIGHT;
            } else if (result.solution[highestIdx][1] > 0) {
                result.solution[highestIdx][1]--;
                result.objectiveValue -= SALT_UNIT_WEIGHT;
            } else {
                break;
            }

            result.cgZ = calculateCGZ(holds, result.solution);
            result.gm = calculateGM(ship, result.cgZ, holds, result.solution);
            adjustments++;
        }
        return result;
    }

    private OptimizationResult adjustForGmConstraintHeuristic(Ship ship, List<CargoHold> holds,
                                                               OptimizationResult result, CargoType grain, CargoType salt,
                                                               double maxGrain, double maxSalt, double minGm) {
        OptimizationResult adjusted = adjustForGmConstraint(ship, holds, result, grain, salt, maxGrain, maxSalt, minGm);
        adjusted.algorithmUsed = "HEURISTIC_GM_ADJUSTED";
        return adjusted;
    }

    private double getHoldWeight(CargoHold hold, int[] solution) {
        return solution[0] * GRAIN_UNIT_WEIGHT + solution[1] * SALT_UNIT_WEIGHT;
    }

    private double getHoldVolume(CargoHold hold, int[] solution) {
        return solution[0] * GRAIN_UNIT_VOLUME + solution[1] * SALT_UNIT_VOLUME;
    }

    private double calculateCGZ(List<CargoHold> holds, int[][] solution) {
        double totalMoment = 0;
        double totalWeight = 0;
        for (int i = 0; i < holds.size(); i++) {
            double holdWeight = solution[i][0] * GRAIN_UNIT_WEIGHT + solution[i][1] * SALT_UNIT_WEIGHT;
            totalMoment += holdWeight * holds.get(i).getCenterGravityZ().doubleValue();
            totalWeight += holdWeight;
        }
        return totalWeight > 0 ? totalMoment / totalWeight : 0.8;
    }

    private double calculateGM(Ship ship, double cgZ, List<CargoHold> holds, int[][] solution) {
        double breadth = ship.getBreadthMolded().doubleValue();
        double length = ship.getLengthOverall().doubleValue();
        double displacement = ship.getDisplacement().doubleValue();
        double seawaterDensity = shipParamsConfig.getHull().getSeawaterDensity().doubleValue();
        double kbCoefficient = shipParamsConfig.getHull().getBuoyancyCenterCoefficient().doubleValue();

        double draft = ship.getDesignDraft().doubleValue();
        double it = (length * FastMath.pow(breadth, 3)) / 12.0;
        double kb = draft * kbCoefficient;
        double bmt = it / displacement * seawaterDensity;
        double km = kb + bmt;
        return km - cgZ;
    }

    private OptimizationResult fallbackToHeuristic(Ship ship, List<CargoHold> holds,
                                                    CargoType grain, CargoType salt,
                                                    double maxGrain, double maxSalt, double minGm, double maxTrim) {
        return solveWithHeuristic(ship, holds, grain, salt, maxGrain, maxSalt, minGm, maxTrim);
    }

    private OptimizationResult buildSolution(int n) {
        return new OptimizationResult(n);
    }

    private LoadingOptimization saveOptimizationResult(Ship ship, OptimizationResult result,
                                                       CargoType grain, CargoType salt,
                                                       LoadingOptimizationRequest request) {
        LoadingOptimization entity = new LoadingOptimization();
        entity.setShipId(ship.getId());
        entity.setOptimizationTime(LocalDateTime.now());
        entity.setTotalCargoWeight(BigDecimal.valueOf(result.objectiveValue).setScale(2, RoundingMode.HALF_UP));
        entity.setEffectivePayload(BigDecimal.valueOf(result.objectiveValue).setScale(2, RoundingMode.HALF_UP));
        entity.setMinGmRequired(request.getMinGm() != null ? request.getMinGm() : BigDecimal.valueOf(0.3));
        entity.setResultingGm(BigDecimal.valueOf(result.gm).setScale(4, RoundingMode.HALF_UP));

        double totalGrainWeight = 0, totalSaltWeight = 0;
        for (int[] ints : result.solution) {
            totalGrainWeight += ints[0] * GRAIN_UNIT_WEIGHT;
            totalSaltWeight += ints[1] * SALT_UNIT_WEIGHT;
        }
        entity.setGrainWeight(BigDecimal.valueOf(totalGrainWeight).setScale(2, RoundingMode.HALF_UP));
        entity.setSaltWeight(BigDecimal.valueOf(totalSaltWeight).setScale(2, RoundingMode.HALF_UP));

        entity.setStatus(result.isFeasible() ? "OPTIMAL" : "INFEASIBLE");
        entity.setObjectiveValue(BigDecimal.valueOf(result.objectiveValue).setScale(2, RoundingMode.HALF_UP));
        entity.setSolveTimeMs(BigDecimal.valueOf(result.solveTimeMs).setScale(2, RoundingMode.HALF_UP));
        entity.setAlgorithmUsed(result.algorithmUsed);

        List<Map<String, Object>> solutionData = new ArrayList<>();
        for (int i = 0; i < result.solution.length; i++) {
            Map<String, Object> holdSolution = new LinkedHashMap<>();
            holdSolution.put("holdIndex", i);
            holdSolution.put("grainUnits", result.solution[i][0]);
            holdSolution.put("saltUnits", result.solution[i][1]);
            solutionData.add(holdSolution);
        }
        entity.setSolution(solutionData);

        return optimizationRepository.save(entity);
    }

    private void saveLoadingPlan(UUID shipId, List<CargoHold> holds, CargoType grain, CargoType salt,
                                  OptimizationResult result) {
        cargoLoadingRepository.deleteByShipId(shipId);

        for (int i = 0; i < holds.size(); i++) {
            if (result.solution[i][0] > 0) {
                CargoLoading loading = new CargoLoading();
                loading.setShipId(shipId);
                loading.setHoldId(holds.get(i).getId());
                loading.setCargoTypeId(grain.getId());
                loading.setWeight(BigDecimal.valueOf(result.solution[i][0] * GRAIN_UNIT_WEIGHT)
                        .setScale(2, RoundingMode.HALF_UP));
                loading.setVolume(BigDecimal.valueOf(result.solution[i][0] * GRAIN_UNIT_VOLUME)
                        .setScale(2, RoundingMode.HALF_UP));
                loading.setLoadingTime(LocalDateTime.now());
                cargoLoadingRepository.save(loading);
            }
            if (result.solution[i][1] > 0) {
                CargoLoading loading = new CargoLoading();
                loading.setShipId(shipId);
                loading.setHoldId(holds.get(i).getId());
                loading.setCargoTypeId(salt.getId());
                loading.setWeight(BigDecimal.valueOf(result.solution[i][1] * SALT_UNIT_WEIGHT)
                        .setScale(2, RoundingMode.HALF_UP));
                loading.setVolume(BigDecimal.valueOf(result.solution[i][1] * SALT_UNIT_VOLUME)
                        .setScale(2, RoundingMode.HALF_UP));
                loading.setLoadingTime(LocalDateTime.now());
                cargoLoadingRepository.save(loading);
            }
        }
    }

    private LoadingOptimizationResultDTO buildErrorResult(UUID shipId, String message) {
        LoadingOptimizationResultDTO dto = new LoadingOptimizationResultDTO();
        dto.setShipId(shipId);
        dto.setFeasible(false);
        dto.setStatus("ERROR");
        dto.setMessage(message);
        return dto;
    }

    public LoadingOptimizationResultDTO convertToDTO(LoadingOptimization entity, List<CargoHold> holds,
                                                     OptimizationResult result, CargoType grain, CargoType salt) {
        LoadingOptimizationResultDTO dto = new LoadingOptimizationResultDTO();
        dto.setId(entity.getId());
        dto.setShipId(entity.getShipId());
        dto.setOptimizationTime(entity.getOptimizationTime());
        dto.setTotalCargoWeight(entity.getTotalCargoWeight());
        dto.setTotalCargoVolume(entity.getTotalCargoVolume());
        dto.setEffectivePayload(entity.getEffectivePayload());
        dto.setMinGmRequired(entity.getMinGmRequired());
        dto.setResultingGm(entity.getResultingGm());
        dto.setGrainWeight(entity.getGrainWeight());
        dto.setSaltWeight(entity.getSaltWeight());
        dto.setFeasible(result.feasible);
        dto.setStatus(entity.getStatus());
        dto.setObjectiveValue(entity.getObjectiveValue());
        dto.setSolveTimeMs(entity.getSolveTimeMs());
        dto.setAlgorithmUsed(entity.getAlgorithmUsed());
        dto.setSolution(entity.getSolution());
        dto.setMessage(result.feasible ? "优化成功" : "无法找到满足所有约束的可行解");

        shipRepository.findById(entity.getShipId()).ifPresent(s -> dto.setShipName(s.getName()));

        return dto;
    }

    private static class OptimizationResult {
        boolean feasible;
        double objectiveValue;
        int[][] solution;
        double cgZ;
        double gm;
        String algorithmUsed;
        long solveTimeMs;

        OptimizationResult(int n) {
            feasible = false;
            objectiveValue = 0;
            solution = new int[n][2];
            cgZ = 0.8;
            gm = 0;
            algorithmUsed = "UNKNOWN";
            solveTimeMs = 0;
        }

        boolean isFeasible() {
            return feasible;
        }
    }
}
