package io.input;

import java.io.IOException;

import utils.Math_Utils;
import network.messages.CompassMessage;
import network.messages.InformationRequest;
import network.messages.Message;
import network.messages.MessageProvider;
import network.messages.SystemStatusMessage;

import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;

public class I2CCompassModuleInput extends Thread implements ControllerInput,
		MessageProvider {
	/*
	 * I2C Device variables and settings
	 */
	private final static int ADDR = 0x0E; // 7-bit address for the MAG3110

	private final static int CTRL_REG1_DR = 0x00;
	private final static int CTRL_REG1_OS = 0x18;
	private final static int CTRL_REG1 = (CTRL_REG1_DR | CTRL_REG1_OS);

	private final static int CTRL_REG2_AUTO_MRST_EN = 0x80;
	private final static int CTRL_REG2_RAW = 0x00;//0x20;
	private final static int CTRL_REG2_MAG_RST = 0x00;

	/*
	 * Other variables
	 */
	private final static int I2C_DEVICE_UPDATE_DELAY = 15;

	private I2CBus i2cBus;
	private I2CDevice mag3110;
	private boolean available = false;
	private boolean deviceActiveMode = true;
	private int heading = 0;
	
	private long startTime = System.currentTimeMillis();

	private short[] min = {Short.MAX_VALUE, Short.MAX_VALUE, Short.MAX_VALUE};
	private short[] max = {-Short.MAX_VALUE, -Short.MAX_VALUE, -Short.MAX_VALUE};
	
	
	public I2CCompassModuleInput(I2CBus i2cBus) {
		this.i2cBus = i2cBus;

		try {
			// Get device instance
			mag3110 = i2cBus.getDevice(ADDR);

			// Get device instance
			mag3110 = i2cBus.getDevice(ADDR);
			//System.out.println("Connection to magnetic sensor established!");

			// Write bits in CTRL_REG2 (set reset and data types)
			mag3110.write((byte) 0x11, (byte) (CTRL_REG2_AUTO_MRST_EN
					| CTRL_REG2_MAG_RST | CTRL_REG2_RAW));

			Thread.sleep(15);

			// Write bits in CTRL_REG1 (set output rate and over sample ratio)
			mag3110.write((byte) 0x10, (byte) (0x01 /*| CTRL_REG1*/));

			available = true;
		} catch (IOException e) {
			System.out
					.println("[I2CCompassModule] Error on device initialization");
			System.out.println(e.getMessage());
		} catch (InterruptedException e) {
			System.out
					.println("[I2CCompassModule] Error on device initialization (interruped thread)");
			System.out.println(e.getMessage());
		}
	}

	@Override
	public boolean isAvailable() {
		return available;
	}

	@Override
	public Message getMessage(Message request) {
		if(request instanceof InformationRequest && ((InformationRequest)request).getMessageTypeQuery().equals(InformationRequest.MessageType.COMPASS)){
			if (!available) {
				return new SystemStatusMessage(
						"[CompassModule] Unable to send Compass data");
			}
	
			return new CompassMessage((int[]) getReadings());
		}
		
		return null;
	}

	@Override
	public Object getReadings() {
		// int[] readings=new
		return null;
	}

	/*
	 * Device readers and modifiers
	 */
	private short readX() throws IOException, InterruptedException {
		if (deviceActiveMode) {
			int xl, xh; // define the MSB and LSB
			
			//sometimes when an exception happens at PI4J the next value comes out as shit
			mag3110.read((byte) 0x01);
			Thread.sleep(2);
			mag3110.read((byte) 0x02);
			Thread.sleep(2);
			mag3110.read((byte) 0x01);
			Thread.sleep(2);
			mag3110.read((byte) 0x02);
			Thread.sleep(2);

			xh = mag3110.read((byte) 0x01); // x MSB reg
			Thread.sleep(2); // needs at least 1.3us free time between start &
			
			xl = mag3110.read((byte) 0x02); // x LSB reg
			Thread.sleep(2); // needs at least 1.3us free time between start &
			
			short xout = (short)((xl | (xh << 8)) & 0xFFFF) ; // concatenate the MSB and LSB
			
			if(System.currentTimeMillis() - startTime < 10*1000) {
				if(xout > max[0])
					max[0] = xout;
				if(xout < min[0])
					min[0] = xout;
				
				System.out.print("callibrating...");
			}
			
			return xout;
		} else {
			return -1;
		}
	}

	private short readY() throws IOException, InterruptedException {
		if (deviceActiveMode) {
			int yl, yh; // define the MSB and LSB
			
			//sometimes when an exception happens at PI4J the next value comes out as shit
			mag3110.read((byte) 0x03);
			Thread.sleep(2);
			mag3110.read((byte) 0x04);
			Thread.sleep(2);
			mag3110.read((byte) 0x03);
			Thread.sleep(2);
			mag3110.read((byte) 0x04);
			Thread.sleep(2);

			yh = mag3110.read((byte) 0x03); // y MSB reg
			Thread.sleep(2); // needs at least 1.3us free time between start &
								// stop

			yl = mag3110.read((byte) 0x04); // y LSB reg
			Thread.sleep(2); // needs at least 1.3us free time between start &
								// stop

			short yout = (short)((yl | (yh << 8)) & 0xFFFF); // concatenate the MSB and LSB
			
			if(System.currentTimeMillis() - startTime < 10*1000) {
				if(yout > max[1])
					max[1] = yout;
				if(yout < min[1])
					min[1] = yout;
			}
			
			return yout;
		} else {
			return -1;
		}
	}

	private short readZ() throws IOException, InterruptedException {
		if (deviceActiveMode) {
			int zl, zh; // define the MSB and LSB
			
			//sometimes when an exception happens at PI4J the next value comes out as shit
			mag3110.read((byte) 0x05);
			Thread.sleep(2);
			mag3110.read((byte) 0x06);
			Thread.sleep(2);
			mag3110.read((byte) 0x05);
			Thread.sleep(2);
			mag3110.read((byte) 0x06);
			Thread.sleep(2);

			zh = mag3110.read((byte) 0x05); // z MSB reg
			Thread.sleep(2); // needs at least 1.3us free time between start &
								// stop

			zl = mag3110.read((byte) 0x06); // z LSB reg
			Thread.sleep(2); // needs at least 1.3us free time between start &
								// stop

			short zout = (short)((zl | (zh << 8)) & 0xFFFF); // concatenate the MSB and LSB
			
			if(System.currentTimeMillis() - startTime < 10*1000) {
				if(zout > max[2])
					max[2] = zout;
				if(zout < min[2])
					min[2] = zout;
			}
			
			return zout;
		} else {
			return -1;
		}
	}

	public void standBy() {
		if (deviceActiveMode) {
			try {
				mag3110.write((byte) 0x10, (byte) CTRL_REG1);
				Thread.sleep(2); // 1.3us free time between start & stop
				deviceActiveMode = false;
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void wakeUp() {
		if (!deviceActiveMode) {
			try {
				mag3110.write((byte) 0x10, (byte) (CTRL_REG1 | 0x01));
				Thread.sleep(2); // 1.3us free time between start & stop
				deviceActiveMode = true;
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				short[] rawAxisReadings = new short[3];

				rawAxisReadings[0] = readX();
				rawAxisReadings[1] = readY();
				rawAxisReadings[2] = readZ();
				
//				processRawAxisReadings(rawAxisReadings);
				
				short middleX = (short)((max[0] + min[0])/2);
				short middleY = (short)((max[1] + min[1])/2);
				
				double scaleX = 1.0/(max[0]-min[0]);
				double scaleY = 1.0/(max[1]-min[1]);
				
				double heading = Math.atan2(-(rawAxisReadings[1] - middleY)*scaleY, (rawAxisReadings[0] - middleX)*scaleX);
				
				//Value for Lisbon is -2º (0.034906585 rad). Find more here: http://www.magnetic-declination.com
				double declinationAngle = 0.034906585;
				heading += declinationAngle;

				if(heading < 0) {
					heading += 2*Math.PI;  // correct for when the heading is negative
				}
				
				  // Convert radians to degrees for readability.
				this.heading = (int)(heading * 180/Math.PI);
				
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}

			try {
				Thread.sleep(I2C_DEVICE_UPDATE_DELAY);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
