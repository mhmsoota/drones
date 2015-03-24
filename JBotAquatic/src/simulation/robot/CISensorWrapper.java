package simulation.robot;

import java.util.ArrayList;

import simulation.Simulator;
import simulation.physicalobjects.PhysicalObject;
import simulation.robot.sensors.Sensor;
import simulation.util.Arguments;
import simulation.util.ArgumentsAnnotation;

import commoninterface.CISensor;
import commoninterface.RobotCI;
import commoninterface.utils.CIArguments;

public class CISensorWrapper extends Sensor{

	@ArgumentsAnnotation(name="ci", defaultValue="")
	private CISensor cisensor;
	
	public CISensorWrapper(Simulator simulator, int id, Robot robot, Arguments args) {
		super(simulator, id, robot, args);
		String ciString = args.getArgumentValue("ci");
		
		CIArguments ciargs = new CIArguments(ciString);
		
		cisensor = CISensor.getSensor(((RobotCI)this.robot), ciargs.getArgumentAsString("classname"), ciargs);
		((RobotCI)this.robot).getCISensors().add(cisensor);
	}

	@Override
	public double getSensorReading(int sensorNumber) {
		return cisensor.getSensorReading(sensorNumber);
	}
	
	@Override
	public void update(double time, ArrayList<PhysicalObject> teleported) {
		cisensor.update(time, ((RobotCI)this.robot).getEntities());
	}

}