package simulation.robot.actuator;

import java.util.Random;
import mathutils.Vector2d;
import simulation.Simulator;
import simulation.robot.Robot;
import simulation.robot.actuators.Actuator;
import simulation.util.Arguments;

public class RudderActuator extends Actuator {

	public static final float NOISESTDEV = 0.05f;

	protected double heading = 0;
	protected double speed = 0;
	protected Random random;
	private double timeDelta = 0.1;
	private double prevSpeed = 0;
	
	public RudderActuator(Simulator simulator, int id, Arguments arguments) {
		super(simulator, id, arguments);
		this.random = simulator.getRandom();
		timeDelta = simulator.getTimeDelta();
	}

	public void setHeading(double value) {
		this.heading = value;
	}

	public void setSpeed(double percentage) {
		this.speed = percentage;
	}
	
	@Override
	public void apply(Robot drone) {
		
		double angleInDegrees = getTurningAngleFromHeading(heading)*-1;
		double motorDifference = 0;
		double forwardComponent = 0;
		double turningComponent = 0;
		double turningSpeed = 0;
		double forwardSpeed = 0;
		
		double maxIncrementUp = 1.0 / (1.0 * 10.0); //1 second to accel to full speed
		double maxIncrementDown = 1.0 / (5.0 * 10.0); //5 seconds to stop
		
		double desiredSpeed = speed;
		
		if(speed > prevSpeed + maxIncrementUp)
			speed = prevSpeed + maxIncrementUp;
		else if(speed < prevSpeed - maxIncrementDown)
			speed  = prevSpeed - maxIncrementDown;
		
		if(Math.abs(heading) >= 0.9 || Math.abs(heading) < 0.1) {
			
			if(Math.abs(heading) >= 0.9)
				heading = 1.0*Math.signum(heading);
			
			if(Math.abs(heading) <= 0.1)
				heading = 0;
			
			angleInDegrees = getTurningAngleFromHeading(heading)*-1;
			
			motorDifference = getMotorDifferenceFromTurningAngle(Math.abs(angleInDegrees));
			
			forwardComponent = 1.0 - motorDifference;
			turningComponent = 1.0 - forwardComponent;
			
			turningComponent*=speed;
			forwardComponent*=speed;
			
			turningSpeed = getTurningSpeedFromDifferenceOneStoppedMotor(turningComponent);
			forwardSpeed = getForwardSpeedInMs(forwardComponent);
			angleInDegrees = getTurningAngleFromTurningSpeed(turningSpeed)*Math.signum(angleInDegrees);
			
		} else {
			motorDifference = getMotorDifferenceFromAngleOneFullMotor(Math.abs(angleInDegrees));
			turningSpeed = getTurningSpeedFromMotorDifferenceOneMotorFull(motorDifference) * translateSpeedReduction(speed);
		}
		
		double x = drone.getPosition().getX();
		double y = drone.getPosition().getY();
		double o = drone.getOrientation();
		
		turningSpeed = turningSpeed* (1 + random.nextGaussian() * NOISESTDEV);
		forwardSpeed = forwardSpeed* (1 + random.nextGaussian() * NOISESTDEV);
		
		x = x + (turningSpeed + forwardSpeed) * timeDelta * Math.cos(o);
		y = y + (turningSpeed + forwardSpeed) * timeDelta * Math.sin(o);
		
		if(desiredSpeed > 0) {
			double newOrientation = drone.getOrientation() + Math.toRadians(angleInDegrees);
			drone.setOrientation(newOrientation);
		}
		
		drone.setPosition(new Vector2d(x,y));
		
		prevSpeed = speed;
	}

	@Override
	public String toString() {
		return "RudderActuator [heading=" + heading + ", speed="
				+ speed + "]";
	}
	
	private double translateSpeedReduction(double speed) {
		return 0.7*speed + 0.3;
	}
	
	private double getForwardSpeedInMs(double percentage) {
		return 153.99*Math.pow(percentage,0.3663) / 100.0;
	}
	
	private double getTurningSpeedFromDifferenceOneStoppedMotor(double speedDifference) {
		
		if(speedDifference == 0)
			return 0;
		
		return (-35.322*Math.pow(speedDifference,2) + 133.59*speedDifference + 18.964) / 100.0;
	}
	
	private double getMotorDifferenceFromAngleOneFullMotor(double angle) {
		return -0.0068 * Math.pow(angle,2) + 0.1614*angle+ 0.0903;
	}
	
	public double getTurningAngleFromDifferenceOneMotorFull(double speedDifference) {
		return 10.564 * speedDifference - 2.0412;
	}
	
	public double getTurningSpeedFromMotorDifferenceOneMotorFull(double difference) {
		return (-28.958*difference + 139.88) / 100.0;
	}
	
	private double getMotorDifferenceFromTurningAngle(double angle) {
		return Math.min(0.0098*Math.pow(angle,2) + 0.0244*angle,1);
	}
	
	private double getTurningAngleFromTurningSpeed(double turningSpeed) {
		return 7.6401 * turningSpeed;
	}
	
	private double getTurningAngleFromHeading(double heading) {
		return 9*heading;
	}
	
}