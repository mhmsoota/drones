/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package evaluation;

import commoninterface.entities.Waypoint;
import commoninterface.mathutils.Vector2d;
import commoninterface.utils.CoordinateUtilities;
import evolutionaryrobotics.evaluationfunctions.EvaluationFunction;
import simulation.Simulator;
import simulation.robot.AquaticDrone;
import simulation.robot.Robot;
import simulation.util.Arguments;

/**
 *
 * @author jorge
 */
public class WaypointFitness extends EvaluationFunction {

    private boolean configured = false;
    private double startingDistance = 0;
    private double targetDistance = 1.5;
    private int steps = 0;
    private boolean kill = true;
    private double usedEnergy = 0;
    private final double safetyDistance;
    private double minDistanceOthers = Double.POSITIVE_INFINITY;

    public WaypointFitness(Arguments args) {
        super(args);
        targetDistance = args.getArgumentAsDouble("targetdistance");
        kill = args.getFlagIsTrue("kill");
        safetyDistance = args.getArgumentAsDouble("safetydistance");
    }

    @Override
    public void update(Simulator simulator) {
        AquaticDrone drone = (AquaticDrone) simulator.getRobots().get(0);
        Waypoint wp = drone.getActiveWaypoint();
        if (!configured) {
            steps = simulator.getEnvironment().getSteps();
            startingDistance = calculateDistance(wp, drone);
        }
        configured = true;

        // DISTANCE TO WAYPOINT + ENERGY USED TO STAY IN WP
        Vector2d wpPos = CoordinateUtilities.GPSToCartesian(wp.getLatLon());
        double distance = wpPos.distanceTo(new Vector2d(drone.getPosition().x, drone.getPosition().y));
        double energy = drone.getMotorSpeedsInPercentage();

        usedEnergy += (distance <= targetDistance ? energy : 1);
        fitness = (startingDistance - distance) / startingDistance + 1 - (usedEnergy / steps);

        // COLLISIONS
        if (kill && drone.isInvolvedInCollison()) {
            simulator.stopSimulation();
        }
        for (int i = 1; i < simulator.getRobots().size(); i++) {
            Robot r = simulator.getRobots().get(1);
            double d = drone.getPosition().distanceTo(r.getPosition()) - drone.getRadius() - r.getRadius();
            minDistanceOthers = Math.min(d, minDistanceOthers);
        }
        double safetyFactor = Math.min(safetyDistance, minDistanceOthers) / safetyDistance;
        fitness *= safetyFactor;
    }

    @Override
    public double getFitness() {
        return Math.max(0,fitness + 10);
    }

    public static double calculateDistance(Waypoint wp, AquaticDrone drone) {
        Vector2d pos = CoordinateUtilities.GPSToCartesian(wp.getLatLon());
        Vector2d robotPos = new Vector2d(drone.getPosition().getX(), drone.getPosition().getY());
        return pos.distanceTo(robotPos);
    }
}
