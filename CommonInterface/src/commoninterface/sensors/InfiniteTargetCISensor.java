package commoninterface.sensors;

import java.util.ArrayList;
import java.util.HashMap;

import commoninterface.AquaticDroneCI;
import commoninterface.RobotCI;
import commoninterface.entities.target.Target;
import commoninterface.mathutils.Vector2d;
import commoninterface.utils.CIArguments;
import commoninterface.utils.CoordinateUtilities;
import commoninterface.utils.jcoord.LatLon;
import net.jafama.FastMath;

public class InfiniteTargetCISensor extends WaypointCISensor {
	private static final long serialVersionUID = 490158787475877489L;
	private double expFactor = 30;
	// private double linearFactor = -0.009;
	private boolean excludeOccupied = false;
	private boolean linear = false;
	private boolean stabilize = false;
	private boolean normalize = false;

	// XXX
	private boolean alternative = false;

	private double range = 100;
	private int historySize = 10;
	private Target[] lastSeenTargets;
	private int pointer = 0;

	public InfiniteTargetCISensor(int id, RobotCI robot, CIArguments args) {
		super(id, robot, args);

		expFactor = args.getArgumentAsDoubleOrSetDefault("expFactor", expFactor);
		excludeOccupied = args.getArgumentAsIntOrSetDefault("excludeOccupied", 0) == 1;
		linear = args.getArgumentAsIntOrSetDefault("linear", 0) == 1;
		stabilize = args.getArgumentAsIntOrSetDefault("stabilize", 0) == 1;
		historySize = args.getArgumentAsIntOrSetDefault("historySize", historySize);
		range = args.getArgumentAsDoubleOrSetDefault("range", range);
		normalize = args.getArgumentAsIntOrSetDefault("normalize", 0) == 1;

		// XXX
		alternative = args.getArgumentAsIntOrSetDefault("normalize", 0) == 1;

		lastSeenTargets = new Target[historySize];
		for (int i = 0; i < lastSeenTargets.length; i++) {
			lastSeenTargets[i] = null;
		}
	}

	/**
	 * Sets the sensor reading in the readings array. The first element of the
	 * array corresponds to the difference between the robot orientation and the
	 * azimut to target. The second element corresponds to the distance from
	 * robot to the target
	 * 
	 * Return: readings[0] = difference between robot orientation an the azimuth
	 * to target, readings[1] = distance to target (in meters)
	 */
	@Override
	public void update(double time, Object[] entities) {

		LatLon robotLatLon = ((AquaticDroneCI) robot).getGPSLatLon();
		Target target = getClosestTarget(excludeOccupied, entities);

		LatLon latLon = null;
		double distance = -1;
		if (target != null) {
			latLon = target.getLatLon();
			distance = CoordinateUtilities.distanceInMeters(robotLatLon, latLon);
		}

		if (target != null && distance > -1 && latLon != null && distance <= range) {
			double currentOrientation = ((AquaticDroneCI) robot).getCompassOrientationInDegrees();
			double coordinatesAngle = CoordinateUtilities.angleInDegrees(robotLatLon, latLon);
			double orientationDifference = currentOrientation - coordinatesAngle + 180;

			if (normalize) {
				// if (orientationDifference < 90) {
				// orientationDifference = -(orientationDifference - 90);
				// } else {
				// orientationDifference = 450 - orientationDifference;
				// }
				orientationDifference %= 360;
				orientationDifference /= 360;
			} else {
				orientationDifference %= 360;
				if (orientationDifference > 180) {
					orientationDifference = -((180 - orientationDifference) + 180);
				}
			}

			readings[0] = orientationDifference;

			if (linear) {
				if (normalize) {
					readings[1] = distance / range;
				} else {
					readings[1] = distance;
				}
			} else {
				if (normalize) {
					readings[1] = (FastMath.exp(-distance / expFactor)) / range;
				} else {
					readings[1] = (FastMath.exp(-distance / expFactor));
				}
			}
		} else {
			readings[0] = 0;

			if (normalize) {
				readings[1] = 1;
			} else {
				readings[1] = range;
			}
		}
	}

	private Target getClosestTarget(boolean excludeOccupied, Object[] entities) {
		// Get robot location
		Vector2d robotPosition = null;
		if (robot instanceof AquaticDroneCI) {
			robotPosition = CoordinateUtilities.GPSToCartesian(((AquaticDroneCI) robot).getGPSLatLon());
		} else {
			throw new NullPointerException("Incompatible robot instance!");
		}

		// Get the closest target
		Target closest = null;
		double minDistance = Double.MAX_VALUE;
		for (Object ent : ((AquaticDroneCI) robot).getEntities()) {
			if (ent instanceof Target) {
				Vector2d pos = CoordinateUtilities.GPSToCartesian(((Target) ent).getLatLon());

				// XXX
				if (alternative) {
					if (((Target) ent).isOccupied() && robotPosition.distanceTo(pos) <= ((Target) ent).getRadius()
							&& robotPosition.distanceTo(pos) < minDistance) {
						minDistance = robotPosition.distanceTo(pos);
						closest = (Target) ent;
					} else {
						if (!((Target) ent).isOccupied() && robotPosition.distanceTo(pos) < minDistance) {
							minDistance = robotPosition.distanceTo(pos);
							closest = (Target) ent;
						}
					}
				} else {
					if (!excludeOccupied || (excludeOccupied && !((Target) ent).isOccupied())) {
						if (robotPosition.distanceTo(pos) < minDistance) {
							minDistance = robotPosition.distanceTo(pos);
							closest = (Target) ent;
						}
					}
				}
			}
		}

		// Return either the most seen or the closest
		if (stabilize) {
			int pos = (pointer++) % historySize;
			lastSeenTargets[pos] = closest;

			if (pos < historySize) {
				return getMostCommonTarget(pos);
			} else {
				return getMostCommonTarget();
			}
		} else {
			return closest;
		}
	}

	private Target getMostCommonTarget(int endIndex) {
		// Create a map of target positions in a count table
		HashMap<Target, Integer> positions = new HashMap<Target, Integer>();
		ArrayList<Target> targets = new ArrayList<Target>();

		int counter = 0;
		targets.add(null);
		positions.put(null, counter++);
		for (Object ent : ((AquaticDroneCI) robot).getEntities()) {
			if (ent instanceof Target) {
				positions.put((Target) ent, counter++);
				targets.add((Target) ent);
			}
		}

		// Table to count the occurrences
		int[] count = new int[positions.size()];

		// Count each target occurrence
		for (int i = 0; i < endIndex; i++) {
			int positionInArray = positions.get(lastSeenTargets[i]);
			count[positionInArray]++;
		}

		// Get the most common one
		int maxValue = Integer.MIN_VALUE;
		Target target = null;
		for (int i = 0; i < count.length; i++) {
			if (count[i] > maxValue) {
				target = targets.get(i);
			}
		}

		return target;
	}

	private Target getMostCommonTarget() {
		return getMostCommonTarget(historySize);
	}
}