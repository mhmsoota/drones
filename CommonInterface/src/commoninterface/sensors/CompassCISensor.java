package commoninterface.sensors;

import java.util.ArrayList;

import objects.Entity;
import commoninterface.AquaticDroneCI;
import commoninterface.CISensor;
import commoninterface.RobotCI;
import commoninterface.utils.CIArguments;

public class CompassCISensor extends CISensor{
	
	private AquaticDroneCI drone;
	private double reading = 0;

	public CompassCISensor(int id, RobotCI robot, CIArguments args) {
		super(id, robot, args);
		drone = (AquaticDroneCI) robot;
	}

	@Override
	public double getSensorReading(int sensorNumber) {
		return reading;
	}

	@Override
	public void update(double time, ArrayList<Entity> entities) {
		reading = drone.getCompassOrientationInDegrees();	
	}
	
	@Override
	public int getNumberOfSensors() {
		return 1;
	}

}
