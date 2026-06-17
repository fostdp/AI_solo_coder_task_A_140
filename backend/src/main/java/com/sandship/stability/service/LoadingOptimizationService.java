package com.sandship.stability.service;

import com.sandship.stability.config.LoadingConfig;
import com.sandship.stability.config.StabilityConfig;
import com.sandship.stability.dto.LoadingOptimizationRequest;
import com.sandship.stability.dto.LoadingOptimizationResultDTO;
import com.sandship.stability.entity.*;
import com.sandship.stability.repository.*;
import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class LoadingOptimizationService {

    static {
        Loader.loadNativeLibraries();
    }

    @Autowired
    private LoadingOptimizationRepository optimizationRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private CargoHoldRepository cargoHoldRepository;

    @Autowired
    private CargoTypeRepository cargoTypeRepository;

    @Autowired
    private CargoLoadingRepository cargoLoadingRepository;

    @Autowired
    private StabilityCalculationService stabilityCalculationService;

    @Autowired
    private StabilityConfig stabilityConfig;

    @Autowired
    private LoadingConfig loadingConfig;

    private static final int HEURISTIC_THRESHOLD = 20;
    private static final long MIP_TIME_LIMIT = 30000;

    @Transactional
    public LoadingOptimizationResultDTO optimizeLoading(LoadingOptimizationRequest request) {
        UUID shipId = request.getShipId();
        Optional<Ship> shipOpt = shipRepository.findById(shipId);
        if (shipOpt.isEmpty()) {
            throw new IllegalArgumentException("船舶不存在: " + shipId);
        }

        Ship ship = shipOpt.get();
        List<CargoHold> cargoHolds = cargoHoldRepository.findByShipIdOrderByHoldNumber(shipId);

        Optional<CargoType> grainOpt = cargoTypeRepository.findByCargoCode("GRAIN");
        Optional<CargoType> saltOpt = cargoTypeRepository.findByCargoCode("SALT");

        if (grainOpt.isEmpty() || saltOpt.isEmpty()) {
            throw new IllegalStateException("货物类型未初始化");
        }

        CargoType grain = grainOpt.get();
        CargoType salt = saltOpt.get();

        BigDecimal minGmRequired = request.getMinGmRequired() != null
                ? request.getMinGmRequired() : loadingConfig.getDefaultMinGm();

        LoadingOptimization optimization = new LoadingOptimization();
        optimization.setShipId(shipId);
        optimization.setOptimizationTime(LocalDateTime.now());
        optimization.setGrainWeight(request.getGrainWeight());
        optimization.setSaltWeight(request.getSaltWeight());
        optimization.setMinGmRequired(minGmRequired);
        optimization.setStatus("PROCESSING");
        optimization = optimizationRepository.save(optimization);

        try {
            long startTime = System.currentTimeMillis();
            OptimizationResult result;

            int problemSize = cargoHolds.size() * 2;
            if (problemSize > HEURISTIC_THRESHOLD || Boolean.TRUE.equals(request.getUseHeuristic())) {
                log.info("问题规模较大({}变量)，先使用启发式算法求解", problemSize);
                result = solveWithHeuristic(
                        ship, cargoHolds, grain, salt,
                        request.getGrainWeight(), request.getSaltWeight(),
                        minGmRequired, request.getPrioritizeGrain()
                );

                if (result.isFeasible()) {
                    long heuristicTime = System.currentTimeMillis() - startTime;
                    log.info("启发式算法完成，耗时: {}ms，总载重: {}t", heuristicTime, result.totalWeight);

                    long remainingTime = MIP_TIME_LIMIT - heuristicTime;
                    if (remainingTime > 5000 && Boolean.TRUE.equals(request.getRefineWithMip())) {
                        log.info("使用MIP进行精化，剩余时间: {}ms", remainingTime);
                        OptimizationResult mipResult = solveIntegerProgramming(
                                ship, cargoHolds, grain, salt,
                                request.getGrainWeight(), request.getSaltWeight(),
                                minGmRequired, request.getPrioritizeGrain(),
                                result, remainingTime
                        );
                        if (mipResult.isFeasible() && mipResult.objectiveValue.compareTo(result.objectiveValue) > 0) {
                            result = mipResult;
                            log.info("MIP精化完成，目标值提升: {} -> {}", result.objectiveValue, mipResult.objectiveValue);
                        }
                    }
                }
            } else {
                result = solveIntegerProgramming(
                        ship, cargoHolds, grain, salt,
                        request.getGrainWeight(), request.getSaltWeight(),
                        minGmRequired, request.getPrioritizeGrain(),
                        null, MIP_TIME_LIMIT
                );
            }

            optimization.setStatus(result.isFeasible() ? "SUCCESS" : "INFEASIBLE");
            optimization.setTotalCargoWeight(result.totalWeight);
            optimization.setTotalCargoVolume(result.totalVolume);
            optimization.setEffectivePayload(result.effectivePayload);
            optimization.setResultingGm(result.resultingGm);
            optimization.setObjectiveValue(result.objectiveValue);
            optimization.setSolution(result.solution);
            optimization.setSolveTimeMs(BigDecimal.valueOf(System.currentTimeMillis() - startTime));
            optimization.setAlgorithmUsed(result.algorithmUsed);

            if (result.isFeasible()) {
                saveLoadingPlan(shipId, cargoHolds, grain, salt, result);
            }

            optimization = optimizationRepository.save(optimization);

            return convertToDTO(optimization, ship, cargoHolds, grain, salt, result);

        } catch (Exception e) {
            log.error("装载优化失败 - 船舶: {}", shipId, e);
            optimization.setStatus("FAILED");
            optimizationRepository.save(optimization);
            throw new RuntimeException("装载优化失败", e);
        }
    }

    private OptimizationResult solveIntegerProgramming(
            Ship ship, List<CargoHold> holds, CargoType grain, CargoType salt,
            BigDecimal targetGrainWeight, BigDecimal targetSaltWeight,
            BigDecimal minGmRequired, Boolean prioritizeGrain,
            OptimizationResult initialSolution, long timeLimitMs) {

        int numHolds = holds.size();
        MPSolver solver = MPSolver.createSolver("CBC");

        solver.setTimeLimit(timeLimitMs);
        if (initialSolution != null) {
            solver.setNumThreads(4);
        }

        MPVariable[][] x = new MPVariable[numHolds][2];
        for (int i = 0; i < numHolds; i++) {
            x[i][0] = solver.makeIntVar(0, 1000, "grain_" + i);
            x[i][1] = solver.makeIntVar(0, 1000, "salt_" + i);
        }

        MPObjective objective = solver.objective();
        double grainWeight = grain.getUnitWeight().doubleValue();
        double saltWeight = salt.getUnitWeight().doubleValue();
        double grainDensity = grain.getDensity().doubleValue();
        double saltDensity = salt.getDensity().doubleValue();

        double grainPriority = prioritizeGrain != null && prioritizeGrain ? 1.5 : 1.0;
        double saltPriority = 1.0;

        for (int i = 0; i < numHolds; i++) {
            objective.setCoefficient(x[i][0], grainWeight * grainPriority);
            objective.setCoefficient(x[i][1], saltWeight * saltPriority);
        }
        objective.setMaximization();

        BigDecimal totalTargetGrain = targetGrainWeight != null ? targetGrainWeight : ship.getDeadweightTons();
        BigDecimal totalTargetSalt = targetSaltWeight != null ? targetSaltWeight : ship.getDeadweightTons();

        MPConstraint totalGrainConstraint = solver.makeConstraint(0, totalTargetGrain.doubleValue(), "total_grain");
        MPConstraint totalSaltConstraint = solver.makeConstraint(0, totalTargetSalt.doubleValue(), "total_salt");

        for (int i = 0; i < numHolds; i++) {
            totalGrainConstraint.setCoefficient(x[i][0], grainWeight);
            totalSaltConstraint.setCoefficient(x[i][1], saltWeight);
        }

        MPConstraint totalWeightConstraint = solver.makeConstraint(0,
                ship.getDeadweightTons().doubleValue(), "total_weight");
        for (int i = 0; i < numHolds; i++) {
            totalWeightConstraint.setCoefficient(x[i][0], grainWeight);
            totalWeightConstraint.setCoefficient(x[i][1], saltWeight);
        }

        for (int i = 0; i < numHolds; i++) {
            CargoHold hold = holds.get(i);
            MPConstraint weightConstraint = solver.makeConstraint(0,
                    hold.getMaxWeight().doubleValue(), "hold_weight_" + i);
            weightConstraint.setCoefficient(x[i][0], grainWeight);
            weightConstraint.setCoefficient(x[i][1], saltWeight);

            MPConstraint volumeConstraint = solver.makeConstraint(0,
                    hold.getCapacityCubic().doubleValue(), "hold_volume_" + i);
            volumeConstraint.setCoefficient(x[i][0], 1.0 / grainDensity);
            volumeConstraint.setCoefficient(x[i][1], 1.0 / saltDensity);
        }

        MPConstraint trimConstraint = solver.makeConstraint(
                -ship.getLengthOverall().doubleValue() * 0.01,
                ship.getLengthOverall().doubleValue() * 0.01,
                "trim_constraint");

        for (int i = 0; i < numHolds; i++) {
            CargoHold hold = holds.get(i);
            double lcg = hold.getCenterGravityX().doubleValue();
            trimConstraint.setCoefficient(x[i][0], grainWeight * lcg);
            trimConstraint.setCoefficient(x[i][1], saltWeight * lcg);
        }

        MPSolver.ResultStatus status = solver.solve();

        OptimizationResult result = new OptimizationResult();
        result.isFeasible = status == MPSolver.ResultStatus.OPTIMAL
                || status == MPSolver.ResultStatus.FEASIBLE;

        if (!result.isFeasible) {
            log.warn("装载优化无解 - 船舶: {}, 状态: {}", ship.getId(), status);
            return result;
        }

        result.solution = new ArrayList<>();
        double totalGrainLoaded = 0;
        double totalSaltLoaded = 0;
        double totalVolume = 0;

        double[][] weights = new double[numHolds][2];
        for (int i = 0; i < numHolds; i++) {
            weights[i][0] = x[i][0].solutionValue() * grainWeight;
            weights[i][1] = x[i][1].solutionValue() * saltWeight;

            totalGrainLoaded += weights[i][0];
            totalSaltLoaded += weights[i][1];

            double volumeGrain = x[i][0].solutionValue() / grainDensity;
            double volumeSalt = x[i][1].solutionValue() / saltDensity;
            totalVolume += volumeGrain + volumeSalt;

            CargoHold hold = holds.get(i);
            if (weights[i][0] > 0.01) {
                Map<String, Object> grainAlloc = new LinkedHashMap<>();
                grainAlloc.put("holdId", hold.getId().toString());
                grainAlloc.put("holdNumber", hold.getHoldNumber());
                grainAlloc.put("holdName", hold.getHoldName());
                grainAlloc.put("cargoCode", grain.getCargoCode());
                grainAlloc.put("cargoName", grain.getCargoName());
                grainAlloc.put("weight", round(weights[i][0], 2));
                grainAlloc.put("volume", round(volumeGrain, 2));
                grainAlloc.put("units", (int) x[i][0].solutionValue());
                grainAlloc.put("color", grain.getColorHex());
                result.solution.add(grainAlloc);
            }
            if (weights[i][1] > 0.01) {
                Map<String, Object> saltAlloc = new LinkedHashMap<>();
                saltAlloc.put("holdId", hold.getId().toString());
                saltAlloc.put("holdNumber", hold.getHoldNumber());
                saltAlloc.put("holdName", hold.getHoldName());
                saltAlloc.put("cargoCode", salt.getCargoCode());
                saltAlloc.put("cargoName", salt.getCargoName());
                saltAlloc.put("weight", round(weights[i][1], 2));
                saltAlloc.put("volume", round(volumeSalt, 2));
                saltAlloc.put("units", (int) x[i][1].solutionValue());
                saltAlloc.put("color", salt.getColorHex());
                result.solution.add(saltAlloc);
            }
        }

        result.totalWeight = BigDecimal.valueOf(totalGrainLoaded + totalSaltLoaded)
                .setScale(2, RoundingMode.HALF_UP);
        result.totalVolume = BigDecimal.valueOf(totalVolume)
                .setScale(2, RoundingMode.HALF_UP);
        result.effectivePayload = result.totalWeight;
        result.objectiveValue = BigDecimal.valueOf(solver.objective().value())
                .setScale(2, RoundingMode.HALF_UP);
        result.grainWeight = BigDecimal.valueOf(totalGrainLoaded)
                .setScale(2, RoundingMode.HALF_UP);
        result.saltWeight = BigDecimal.valueOf(totalSaltLoaded)
                .setScale(2, RoundingMode.HALF_UP);

        double lightshipKg = ship.getLightshipWeight().multiply(new BigDecimal("0.8"))
                .doubleValue();
        double totalWeight = ship.getLightshipWeight().doubleValue() + totalGrainLoaded + totalSaltLoaded;
        double numerator = lightshipKg;

        for (int i = 0; i < numHolds; i++) {
            CargoHold hold = holds.get(i);
            double kg = hold.getCenterGravityZ().doubleValue();
            numerator += weights[i][0] * kg + weights[i][1] * kg;
        }

        double cgZ = numerator / totalWeight;
        double cbZ = ship.getDesignDraft().multiply(new BigDecimal("0.54")).doubleValue();
        double breadth = ship.getBreadthMolded().doubleValue();
        double length = ship.getLengthOverall().doubleValue();
        double it = (length * Math.pow(breadth, 3)) / 12.0;
        double displacement = ship.getDisplacement().doubleValue();
        double bmt = it / displacement * 1.025;
        double km = cbZ + bmt;
        double gm = km - cgZ;

        result.resultingGm = BigDecimal.valueOf(gm).setScale(4, RoundingMode.HALF_UP);
        result.algorithmUsed = initialSolution != null ? "MIP_REFINED" : "MIP_EXACT";

        if (gm < minGmRequired.doubleValue()) {
            log.warn("优化结果GM值(%.3f)低于要求(%.3f)，尝试调整", gm, minGmRequired);
            result = adjustForGmConstraint(ship, holds, grain, salt, x, weights,
                    minGmRequired.doubleValue(), result, grainWeight, saltWeight);
        }

        return result;
    }

    private OptimizationResult solveWithHeuristic(
            Ship ship, List<CargoHold> holds, CargoType grain, CargoType salt,
            BigDecimal targetGrainWeight, BigDecimal targetSaltWeight,
            BigDecimal minGmRequired, Boolean prioritizeGrain) {

        int numHolds = holds.size();
        double grainWeight = grain.getUnitWeight().doubleValue();
        double saltWeight = salt.getUnitWeight().doubleValue();
        double grainDensity = grain.getDensity().doubleValue();
        double saltDensity = salt.getDensity().doubleValue();

        double grainPriority = prioritizeGrain != null && prioritizeGrain ? 1.5 : 1.0;
        double saltPriority = 1.0;

        double totalGrainTarget = targetGrainWeight != null ? targetGrainWeight.doubleValue() : Double.MAX_VALUE;
        double totalSaltTarget = targetSaltWeight != null ? targetSaltWeight.doubleValue() : Double.MAX_VALUE;
        double maxDeadweight = ship.getDeadweightTons().doubleValue();

        double[][] weights = new double[numHolds][2];
        double totalGrainLoaded = 0;
        double totalSaltLoaded = 0;
        double totalWeight = 0;

        List<HoldScore> holdScores = new ArrayList<>();
        for (int i = 0; i < numHolds; i++) {
            CargoHold hold = holds.get(i);
            double kg = hold.getCenterGravityZ().doubleValue();
            double lowerKgPenalty = 1.0 + (3.0 - kg) * 0.2;
            holdScores.add(new HoldScore(i, lowerKgPenalty));
        }
        holdScores.sort((a, b) -> Double.compare(b.score, a.score));

        for (HoldScore hs : holdScores) {
            int i = hs.holdIndex;
            CargoHold hold = holds.get(i);
            double remainingWeightCap = hold.getMaxWeight().doubleValue();
            double remainingVolumeCap = hold.getCapacityCubic().doubleValue();

            while (true) {
                boolean loaded = false;

                if (totalGrainLoaded < totalGrainTarget && totalWeight < maxDeadweight) {
                    double grainUnits = Math.min(
                            remainingWeightCap / grainWeight,
                            remainingVolumeCap * grainDensity
                    );
                    grainUnits = Math.min(grainUnits, (totalGrainTarget - totalGrainLoaded) / grainWeight);
                    grainUnits = Math.min(grainUnits, (maxDeadweight - totalWeight) / grainWeight);
                    grainUnits = Math.floor(grainUnits);

                    if (grainUnits >= 1) {
                        double w = grainUnits * grainWeight;
                        weights[i][0] += w;
                        totalGrainLoaded += w;
                        totalWeight += w;
                        remainingWeightCap -= w;
                        remainingVolumeCap -= w / grainDensity;
                        loaded = true;
                    }
                }

                if (totalSaltLoaded < totalSaltTarget && totalWeight < maxDeadweight && remainingWeightCap > saltWeight) {
                    double saltUnits = Math.min(
                            remainingWeightCap / saltWeight,
                            remainingVolumeCap * saltDensity
                    );
                    saltUnits = Math.min(saltUnits, (totalSaltTarget - totalSaltLoaded) / saltWeight);
                    saltUnits = Math.min(saltUnits, (maxDeadweight - totalWeight) / saltWeight);
                    saltUnits = Math.floor(saltUnits);

                    if (saltUnits >= 1) {
                        double w = saltUnits * saltWeight;
                        weights[i][1] += w;
                        totalSaltLoaded += w;
                        totalWeight += w;
                        remainingWeightCap -= w;
                        remainingVolumeCap -= w / saltDensity;
                        loaded = true;
                    }
                }

                if (!loaded) break;
            }
        }

        double totalVolume = 0;
        for (int i = 0; i < numHolds; i++) {
            totalVolume += weights[i][0] / grainDensity + weights[i][1] / saltDensity;
        }

        OptimizationResult result = new OptimizationResult();
        result.isFeasible = totalWeight > 0;
        result.algorithmUsed = "HEURISTIC_GREEDY";

        if (!result.isFeasible) {
            return result;
        }

        result = localSearchOptimization(ship, holds, grain, salt, weights,
                minGmRequired.doubleValue(), grainPriority, saltPriority,
                totalGrainTarget, totalSaltTarget, maxDeadweight);

        result.totalWeight = BigDecimal.valueOf(totalGrainLoaded + totalSaltLoaded)
                .setScale(2, RoundingMode.HALF_UP);
        result.totalVolume = BigDecimal.valueOf(totalVolume)
                .setScale(2, RoundingMode.HALF_UP);
        result.effectivePayload = result.totalWeight;
        result.objectiveValue = BigDecimal.valueOf(
                totalGrainLoaded * grainPriority + totalSaltLoaded * saltPriority
        ).setScale(2, RoundingMode.HALF_UP);
        result.grainWeight = BigDecimal.valueOf(totalGrainLoaded)
                .setScale(2, RoundingMode.HALF_UP);
        result.saltWeight = BigDecimal.valueOf(totalSaltLoaded)
                .setScale(2, RoundingMode.HALF_UP);

        result.solution = buildSolution(holds, grain, salt, weights,
                grainWeight, saltWeight, grainDensity, saltDensity);

        double cgZ = calculateCGZ(ship, holds, weights);
        double km = calculateKM(ship);
        double gm = km - cgZ;
        result.resultingGm = BigDecimal.valueOf(gm).setScale(4, RoundingMode.HALF_UP);

        if (gm < minGmRequired.doubleValue()) {
            result = adjustForGmConstraintHeuristic(ship, holds, grain, salt,
                    weights, minGmRequired.doubleValue(), result,
                    grainWeight, saltWeight, grainDensity, saltDensity);
        }

        return result;
    }

    private OptimizationResult localSearchOptimization(
            Ship ship, List<CargoHold> holds, CargoType grain, CargoType salt,
            double[][] weights, double minGm, double grainPriority, double saltPriority,
            double totalGrainTarget, double totalSaltTarget, double maxDeadweight) {

        int numHolds = holds.size();
        double grainWeight = grain.getUnitWeight().doubleValue();
        double saltWeight = salt.getUnitWeight().doubleValue();
        double grainDensity = grain.getDensity().doubleValue();
        double saltDensity = salt.getDensity().doubleValue();

        boolean improved = true;
        int iterations = 0;
        int maxIterations = 50;

        while (improved && iterations < maxIterations) {
            improved = false;
            iterations++;

            for (int i = 0; i < numHolds; i++) {
                for (int j = 0; j < numHolds; j++) {
                    if (i == j) continue;

                    CargoHold holdI = holds.get(i);
                    CargoHold holdJ = holds.get(j);

                    if (weights[i][0] >= grainWeight && weights[j][1] >= saltWeight) {
                        double oldObj = weights[i][0] * grainPriority + weights[j][1] * saltPriority;

                        weights[i][0] -= grainWeight;
                        weights[i][1] += saltWeight;
                        weights[j][1] -= saltWeight;
                        weights[j][0] += grainWeight;

                        double newObj = weights[i][0] * grainPriority + weights[j][1] * saltPriority;

                        boolean weightValidI = weights[i][0] + weights[i][1] <= holdI.getMaxWeight().doubleValue();
                        boolean weightValidJ = weights[j][0] + weights[j][1] <= holdJ.getMaxWeight().doubleValue();
                        boolean volumeValidI = weights[i][0]/grainDensity + weights[i][1]/saltDensity <= holdI.getCapacityCubic().doubleValue();
                        boolean volumeValidJ = weights[j][0]/grainDensity + weights[j][1]/saltDensity <= holdJ.getCapacityCubic().doubleValue();

                        if (newObj > oldObj && weightValidI && weightValidJ && volumeValidI && volumeValidJ) {
                            improved = true;
                        } else {
                            weights[i][0] += grainWeight;
                            weights[i][1] -= saltWeight;
                            weights[j][1] += saltWeight;
                            weights[j][0] -= grainWeight;
                        }
                    }
                }
            }
        }

        OptimizationResult result = new OptimizationResult();
        result.isFeasible = true;
        return result;
    }

    private List<Map<String, Object>> buildSolution(
            List<CargoHold> holds, CargoType grain, CargoType salt,
            double[][] weights, double grainWeight, double saltWeight,
            double grainDensity, double saltDensity) {

        List<Map<String, Object>> solution = new ArrayList<>();
        int numHolds = holds.size();

        for (int i = 0; i < numHolds; i++) {
            CargoHold hold = holds.get(i);

            if (weights[i][0] > 0.01) {
                Map<String, Object> alloc = new LinkedHashMap<>();
                alloc.put("holdId", hold.getId().toString());
                alloc.put("holdNumber", hold.getHoldNumber());
                alloc.put("holdName", hold.getHoldName());
                alloc.put("cargoCode", grain.getCargoCode());
                alloc.put("cargoName", grain.getCargoName());
                alloc.put("weight", round(weights[i][0], 2));
                alloc.put("volume", round(weights[i][0] / grainDensity, 2));
                alloc.put("units", (int) (weights[i][0] / grainWeight));
                alloc.put("color", grain.getColorHex());
                solution.add(alloc);
            }

            if (weights[i][1] > 0.01) {
                Map<String, Object> alloc = new LinkedHashMap<>();
                alloc.put("holdId", hold.getId().toString());
                alloc.put("holdNumber", hold.getHoldNumber());
                alloc.put("holdName", hold.getHoldName());
                alloc.put("cargoCode", salt.getCargoCode());
                alloc.put("cargoName", salt.getCargoName());
                alloc.put("weight", round(weights[i][1], 2));
                alloc.put("volume", round(weights[i][1] / saltDensity, 2));
                alloc.put("units", (int) (weights[i][1] / saltWeight));
                alloc.put("color", salt.getColorHex());
                solution.add(alloc);
            }
        }

        return solution;
    }

    private double calculateCGZ(Ship ship, List<CargoHold> holds, double[][] weights) {
        double lightshipKg = ship.getLightshipWeight().multiply(new BigDecimal("0.8")).doubleValue();
        double totalWeight = ship.getLightshipWeight().doubleValue();
        double numerator = lightshipKg * ship.getLightshipWeight().doubleValue();

        for (int i = 0; i < holds.size(); i++) {
            double kg = holds.get(i).getCenterGravityZ().doubleValue();
            totalWeight += weights[i][0] + weights[i][1];
            numerator += weights[i][0] * kg + weights[i][1] * kg;
        }

        return numerator / totalWeight;
    }

    private double calculateKM(Ship ship) {
        double cbZ = ship.getDesignDraft().multiply(new BigDecimal("0.54")).doubleValue();
        double breadth = ship.getBreadthMolded().doubleValue();
        double length = ship.getLengthOverall().doubleValue();
        double it = (length * Math.pow(breadth, 3)) / 12.0;
        double displacement = ship.getDisplacement().doubleValue();
        double bmt = it / displacement * 1.025;
        return cbZ + bmt;
    }

    private OptimizationResult adjustForGmConstraintHeuristic(
            Ship ship, List<CargoHold> holds, CargoType grain, CargoType salt,
            double[][] weights, double minGm, OptimizationResult currentResult,
            double grainWeight, double saltWeight, double grainDensity, double saltDensity) {

        int numHolds = holds.size();
        double targetGm = minGm + 0.1;
        double totalWeight = ship.getLightshipWeight().doubleValue();

        for (int i = 0; i < numHolds; i++) {
            totalWeight += weights[i][0] + weights[i][1];
        }

        double km = calculateKM(ship);
        double targetCgZ = km - targetGm;

        List<Integer> sortedHolds = new ArrayList<>();
        for (int i = 0; i < numHolds; i++) {
            sortedHolds.add(i);
        }
        sortedHolds.sort((a, b) -> Double.compare(
                holds.get(b).getCenterGravityZ().doubleValue(),
                holds.get(a).getCenterGravityZ().doubleValue()));

        double cgZ = calculateCGZ(ship, holds, weights);
        double excessMoment = (cgZ - targetCgZ) * totalWeight;

        for (int holdIdx : sortedHolds) {
            if (excessMoment <= 0) break;

            CargoHold hold = holds.get(holdIdx);
            double kg = hold.getCenterGravityZ().doubleValue();
            if (kg <= targetCgZ) continue;

            if (weights[holdIdx][1] > 1) {
                double unitsToRemove = Math.min(
                        weights[holdIdx][1] / saltWeight,
                        excessMoment / (saltWeight * (kg - targetCgZ)));
                unitsToRemove = Math.floor(unitsToRemove);

                if (unitsToRemove > 0) {
                    weights[holdIdx][1] -= unitsToRemove * saltWeight;
                    excessMoment -= unitsToRemove * saltWeight * (kg - targetCgZ);
                }
            }

            if (weights[holdIdx][0] > 1 && excessMoment > 0) {
                double unitsToRemove = Math.min(
                        weights[holdIdx][0] / grainWeight,
                        excessMoment / (grainWeight * (kg - targetCgZ)));
                unitsToRemove = Math.floor(unitsToRemove);

                if (unitsToRemove > 0) {
                    weights[holdIdx][0] -= unitsToRemove * grainWeight;
                    excessMoment -= unitsToRemove * grainWeight * (kg - targetCgZ);
                }
            }
        }

        double totalGrain = 0, totalSalt = 0, totalVolume = 0;
        for (int i = 0; i < numHolds; i++) {
            totalGrain += weights[i][0];
            totalSalt += weights[i][1];
            totalVolume += weights[i][0] / grainDensity + weights[i][1] / saltDensity;
        }

        currentResult.solution = buildSolution(holds, grain, salt, weights,
                grainWeight, saltWeight, grainDensity, saltDensity);
        currentResult.totalWeight = BigDecimal.valueOf(totalGrain + totalSalt)
                .setScale(2, RoundingMode.HALF_UP);
        currentResult.totalVolume = BigDecimal.valueOf(totalVolume)
                .setScale(2, RoundingMode.HALF_UP);
        currentResult.effectivePayload = currentResult.totalWeight;
        currentResult.grainWeight = BigDecimal.valueOf(totalGrain)
                .setScale(2, RoundingMode.HALF_UP);
        currentResult.saltWeight = BigDecimal.valueOf(totalSalt)
                .setScale(2, RoundingMode.HALF_UP);

        double newCgZ = calculateCGZ(ship, holds, weights);
        double newGm = km - newCgZ;
        currentResult.resultingGm = BigDecimal.valueOf(newGm)
                .setScale(4, RoundingMode.HALF_UP);
        currentResult.algorithmUsed = "HEURISTIC_GM_ADJUSTED";

        return currentResult;
    }

    private static class HoldScore {
        int holdIndex;
        double score;

        HoldScore(int holdIndex, double score) {
            this.holdIndex = holdIndex;
            this.score = score;
        }
    }

    private OptimizationResult adjustForGmConstraint(
            Ship ship, List<CargoHold> holds, CargoType grain, CargoType salt,
            MPVariable[][] x, double[][] weights, double minGm,
            OptimizationResult currentResult, double grainWeight, double saltWeight) {

        int numHolds = holds.size();
        double targetGm = minGm + 0.1;

        double totalWeight = ship.getLightshipWeight().doubleValue();
        for (int i = 0; i < numHolds; i++) {
            totalWeight += weights[i][0] + weights[i][1];
        }

        double lightshipKg = ship.getLightshipWeight().multiply(new BigDecimal("0.8"))
                .doubleValue();
        double currentMoment = lightshipKg * ship.getLightshipWeight().doubleValue();
        for (int i = 0; i < numHolds; i++) {
            CargoHold hold = holds.get(i);
            double kg = hold.getCenterGravityZ().doubleValue();
            currentMoment += weights[i][0] * kg + weights[i][1] * kg;
        }

        double cbZ = ship.getDesignDraft().multiply(new BigDecimal("0.54")).doubleValue();
        double breadth = ship.getBreadthMolded().doubleValue();
        double length = ship.getLengthOverall().doubleValue();
        double it = (length * Math.pow(breadth, 3)) / 12.0;
        double displacement = ship.getDisplacement().doubleValue();
        double bmt = it / displacement * 1.025;
        double km = cbZ + bmt;
        double targetCgZ = km - targetGm;
        double maxMoment = targetCgZ * totalWeight;

        if (currentMoment <= maxMoment) {
            return currentResult;
        }

        double excessMoment = currentMoment - maxMoment;
        List<Integer> sortedHolds = new ArrayList<>();
        for (int i = 0; i < numHolds; i++) {
            sortedHolds.add(i);
        }
        sortedHolds.sort((a, b) -> Double.compare(
                holds.get(b).getCenterGravityZ().doubleValue(),
                holds.get(a).getCenterGravityZ().doubleValue()));

        List<Map<String, Object>> adjustedSolution = new ArrayList<>();
        double[][] adjustedWeights = new double[numHolds][2];
        double totalGrain = 0, totalSalt = 0;

        for (int i = 0; i < numHolds; i++) {
            System.arraycopy(weights[i], 0, adjustedWeights[i], 0, 2);
        }

        for (int holdIdx : sortedHolds) {
            if (excessMoment <= 0) break;

            CargoHold hold = holds.get(holdIdx);
            double kg = hold.getCenterGravityZ().doubleValue();

            if (adjustedWeights[holdIdx][1] > 1) {
                double unitsToRemove = Math.min(
                        adjustedWeights[holdIdx][1] / saltWeight,
                        excessMoment / (saltWeight * (kg - targetCgZ)));
                unitsToRemove = Math.floor(unitsToRemove);

                if (unitsToRemove > 0) {
                    adjustedWeights[holdIdx][1] -= unitsToRemove * saltWeight;
                    excessMoment -= unitsToRemove * saltWeight * (kg - targetCgZ);
                }
            }

            if (adjustedWeights[holdIdx][0] > 1 && excessMoment > 0) {
                double unitsToRemove = Math.min(
                        adjustedWeights[holdIdx][0] / grainWeight,
                        excessMoment / (grainWeight * (kg - targetCgZ)));
                unitsToRemove = Math.floor(unitsToRemove);

                if (unitsToRemove > 0) {
                    adjustedWeights[holdIdx][0] -= unitsToRemove * grainWeight;
                    excessMoment -= unitsToRemove * grainWeight * (kg - targetCgZ);
                }
            }
        }

        double grainDensity = grain.getDensity().doubleValue();
        double saltDensity = salt.getDensity().doubleValue();
        double totalVolume = 0;

        for (int i = 0; i < numHolds; i++) {
            totalGrain += adjustedWeights[i][0];
            totalSalt += adjustedWeights[i][1];

            double volumeGrain = adjustedWeights[i][0] / grainWeight / grainDensity;
            double volumeSalt = adjustedWeights[i][1] / saltWeight / saltDensity;
            totalVolume += volumeGrain + volumeSalt;

            CargoHold hold = holds.get(i);
            if (adjustedWeights[i][0] > 0.01) {
                Map<String, Object> alloc = new LinkedHashMap<>();
                alloc.put("holdId", hold.getId().toString());
                alloc.put("holdNumber", hold.getHoldNumber());
                alloc.put("holdName", hold.getHoldName());
                alloc.put("cargoCode", grain.getCargoCode());
                alloc.put("cargoName", grain.getCargoName());
                alloc.put("weight", round(adjustedWeights[i][0], 2));
                alloc.put("volume", round(volumeGrain, 2));
                alloc.put("units", (int) (adjustedWeights[i][0] / grainWeight));
                alloc.put("color", grain.getColorHex());
                adjustedSolution.add(alloc);
            }
            if (adjustedWeights[i][1] > 0.01) {
                Map<String, Object> alloc = new LinkedHashMap<>();
                alloc.put("holdId", hold.getId().toString());
                alloc.put("holdNumber", hold.getHoldNumber());
                alloc.put("holdName", hold.getHoldName());
                alloc.put("cargoCode", salt.getCargoCode());
                alloc.put("cargoName", salt.getCargoName());
                alloc.put("weight", round(adjustedWeights[i][1], 2));
                alloc.put("volume", round(volumeSalt, 2));
                alloc.put("units", (int) (adjustedWeights[i][1] / saltWeight));
                alloc.put("color", salt.getColorHex());
                adjustedSolution.add(alloc);
            }
        }

        currentResult.solution = adjustedSolution;
        currentResult.totalWeight = BigDecimal.valueOf(totalGrain + totalSalt)
                .setScale(2, RoundingMode.HALF_UP);
        currentResult.totalVolume = BigDecimal.valueOf(totalVolume)
                .setScale(2, RoundingMode.HALF_UP);
        currentResult.effectivePayload = currentResult.totalWeight;
        currentResult.grainWeight = BigDecimal.valueOf(totalGrain)
                .setScale(2, RoundingMode.HALF_UP);
        currentResult.saltWeight = BigDecimal.valueOf(totalSalt)
                .setScale(2, RoundingMode.HALF_UP);

        double newCgZ = (lightshipKg * ship.getLightshipWeight().doubleValue()
                + calculateWeightMoment(holds, adjustedWeights)) / totalWeight;
        double newGm = km - newCgZ;
        currentResult.resultingGm = BigDecimal.valueOf(newGm)
                .setScale(4, RoundingMode.HALF_UP);

        return currentResult;
    }

    private double calculateWeightMoment(List<CargoHold> holds, double[][] weights) {
        double moment = 0;
        for (int i = 0; i < holds.size(); i++) {
            double kg = holds.get(i).getCenterGravityZ().doubleValue();
            moment += weights[i][0] * kg + weights[i][1] * kg;
        }
        return moment;
    }

    @Transactional
    public void saveLoadingPlan(UUID shipId, List<CargoHold> holds,
                                CargoType grain, CargoType salt, OptimizationResult result) {
        cargoLoadingRepository.findByShipIdAndOptimized(shipId, true)
                .forEach(cargoLoadingRepository::delete);

        int order = 1;
        for (Map<String, Object> alloc : result.solution) {
            String cargoCode = (String) alloc.get("cargoCode");
            UUID holdId = UUID.fromString((String) alloc.get("holdId"));
            BigDecimal weight = BigDecimal.valueOf(((Number) alloc.get("weight")).doubleValue());
            BigDecimal volume = BigDecimal.valueOf(((Number) alloc.get("volume")).doubleValue());

            CargoType cargoType = "GRAIN".equals(cargoCode) ? grain : salt;

            CargoLoading loading = new CargoLoading();
            loading.setShipId(shipId);
            loading.setHoldId(holdId);
            loading.setCargoTypeId(cargoType.getId());
            loading.setWeight(weight);
            loading.setVolume(volume);
            loading.setLoadingTime(LocalDateTime.now());
            loading.setLoadingOrder(order++);
            loading.setIsOptimized(true);

            cargoLoadingRepository.save(loading);
        }
    }

    private double round(double value, int places) {
        BigDecimal bd = BigDecimal.valueOf(value);
        return bd.setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    public LoadingOptimizationResultDTO convertToDTO(LoadingOptimization optimization,
                                                     Ship ship, List<CargoHold> holds,
                                                     CargoType grain, CargoType salt,
                                                     OptimizationResult result) {
        LoadingOptimizationResultDTO dto = new LoadingOptimizationResultDTO();
        dto.setId(optimization.getId());
        dto.setShipId(optimization.getShipId());
        dto.setShipName(ship.getName());
        dto.setOptimizationTime(optimization.getOptimizationTime());
        dto.setTotalCargoWeight(optimization.getTotalCargoWeight());
        dto.setTotalCargoVolume(optimization.getTotalCargoVolume());
        dto.setEffectivePayload(optimization.getEffectivePayload());
        dto.setMinGmRequired(optimization.getMinGmRequired());
        dto.setResultingGm(optimization.getResultingGm());
        dto.setGrainWeight(optimization.getGrainWeight());
        dto.setSaltWeight(optimization.getSaltWeight());
        dto.setStatus(optimization.getStatus());
        dto.setSolution(optimization.getSolution());
        dto.setObjectiveValue(optimization.getObjectiveValue());
        dto.setSolveTimeMs(optimization.getSolveTimeMs());
        dto.setAlgorithmUsed(optimization.getAlgorithmUsed());
        dto.setCreatedAt(optimization.getCreatedAt());

        List<Map<String, Object>> holdAllocations = new ArrayList<>();
        for (CargoHold hold : holds) {
            Map<String, Object> holdInfo = new LinkedHashMap<>();
            holdInfo.put("holdId", hold.getId().toString());
            holdInfo.put("holdNumber", hold.getHoldNumber());
            holdInfo.put("holdName", hold.getHoldName());
            holdInfo.put("capacity", hold.getCapacityCubic());
            holdInfo.put("maxWeight", hold.getMaxWeight());
            holdInfo.put("cargoX", hold.getCenterGravityX());
            holdInfo.put("cargoY", hold.getCenterGravityY());
            holdInfo.put("cargoZ", hold.getCenterGravityZ());

            List<Map<String, Object>> cargos = new ArrayList<>();
            if (result.solution != null) {
                for (Map<String, Object> alloc : result.solution) {
                    if (alloc.get("holdId").equals(hold.getId().toString())) {
                        cargos.add(alloc);
                    }
                }
            }
            holdInfo.put("cargos", cargos);
            holdAllocations.add(holdInfo);
        }
        dto.setHoldAllocations(holdAllocations);

        return dto;
    }

    public Optional<LoadingOptimization> getLatestOptimization(UUID shipId) {
        return optimizationRepository.findTopByShipIdOrderByOptimizationTimeDesc(shipId);
    }

    private static class OptimizationResult {
        boolean isFeasible;
        BigDecimal totalWeight;
        BigDecimal totalVolume;
        BigDecimal effectivePayload;
        BigDecimal grainWeight;
        BigDecimal saltWeight;
        BigDecimal resultingGm;
        BigDecimal objectiveValue;
        List<Map<String, Object>> solution;
        String algorithmUsed;

        boolean isFeasible() {
            return isFeasible;
        }
    }
}
