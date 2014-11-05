package io.input;

import java.io.FileNotFoundException;
import java.io.NotActiveException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import network.messages.GPSMessage;
import network.messages.InformationRequest;
import network.messages.Message;
import network.messages.MessageProvider;
import network.messages.SystemStatusMessage;

import org.joda.time.LocalDateTime;

import utils.NMEA_Utils;

import com.pi4j.io.serial.Serial;
import com.pi4j.io.serial.SerialDataEvent;
import com.pi4j.io.serial.SerialDataListener;
import com.pi4j.io.serial.SerialFactory;

import dataObjects.GPSData;

public class GPSModuleInput implements ControllerInput, MessageProvider,
		Serializable {
	private static final long serialVersionUID = -5443358826645386873L;

	private final static String FILE_NAME = "/home/pi/RaspberryController/logs/GPSLog_";
	private boolean localLog = false;
	private PrintWriter localLogPrintWriterOut;

	private final static boolean DEBUG_MODE = false;

	private final static String NMEA_REGEX = "GP[A-Z]{3},[a-zA-Z0-9,._]*[*][0-9a-fA-F]{2}?";
	private final static String PMTK_REGEX = "PMTK[a-zA-Z0-9,._]*[*][0-9a-fA-F]{2}?";
	private final static int DEFAULT_BAUD_RATE = 9600;
	private final static int TARGET_BAUD_RATE = 57600; // Possible:
														// 4800,9600,14400,19200,38400,57600,115200
	private final static int UPDATE_DELAY = 100; // In miliseconds on
													// [100,10000] interval
	private final static String COM_PORT = Serial.DEFAULT_COM_PORT;

	private NMEA_Utils nmeaUtils = new NMEA_Utils();
	private Serial serial; // Serial connection
	private String receivedDataBuffer = "";
	private GPSData gpsData = new GPSData(); // Contains the obtained info
	private List<String> ackResponses = Collections
			.synchronizedList(new ArrayList<String>());

	private boolean available = false;

	public GPSModuleInput() {

		try {

			serial = SerialFactory.createInstance();

			serial.addListener(new SerialDataListener() {
				@Override
				public void dataReceived(SerialDataEvent event) {
					receivedDataBuffer += event.getData();
					if (event.getData().endsWith("\r\n")) {
						String data = receivedDataBuffer;
						receivedDataBuffer = "";
						processReceivedData(data);
					}
				}
			});

			print("Initializing GPS!", false);

			serial.open(COM_PORT, DEFAULT_BAUD_RATE);

			setupGPSReceiver();

			available = true;

		} catch (NotActiveException | IllegalArgumentException
				| InterruptedException e) {
			System.err.println("Error initializing GPSModule! ("
					+ e.getMessage() + ")");
			serial.close();
		} catch (Error | Exception e) {
			System.err.println("Error initializing GPSModule! ("
					+ e.getMessage() + ")");
		}
	}

	public void closeSerial() {
		serial.close();
	}

	@Override
	public GPSData getReadings() {
		return gpsData;
	}

	@SuppressWarnings("unused")
	private void setupGPSReceiver() throws NotActiveException,
			InterruptedException, IllegalArgumentException {
		if (UPDATE_DELAY < 100 || UPDATE_DELAY > 10000) {
			throw new IllegalArgumentException(
					"Frequency must be in [100,10000] interval");
		}

		// Change Baud Rate
		String command = "$PMTK251," + TARGET_BAUD_RATE + "*";
		int checkSum = nmeaUtils.calculateNMEAChecksum(command);
		command += Integer.toHexString(checkSum).toUpperCase() + "\r\n";
		serial.write(command);
		print("[COMMAND] " + command, false);

		// Closing old velocity serial
		serial.close();
		serial.flush();
		receivedDataBuffer = "";
		ackResponses.clear();
		Thread.sleep(1000);

		serial.addListener(new SerialDataListener() {
			@Override
			public void dataReceived(SerialDataEvent event) {
				receivedDataBuffer += event.getData();
				if (event.getData().endsWith("\r\n")) {
					String data = receivedDataBuffer;
					receivedDataBuffer = "";
					processReceivedData(data);
				}
			}
		});
		serial.open(COM_PORT, TARGET_BAUD_RATE);
		print("Started new baud rate!", false);
		Thread.sleep(1000);

		// Change Update Frequency
		command = "$PMTK220," + UPDATE_DELAY + "*";
		checkSum = nmeaUtils.calculateNMEAChecksum(command);
		command += Integer.toHexString(checkSum).toUpperCase() + "\r\n";
		serial.write(command);
		print("[COMMAND] " + command, false);

		Thread.sleep(1000);
		int index = ackResponses.lastIndexOf("$PMTK001,220,3*30");
		if (index == -1) {
			throw new NotActiveException(
					"The update frequency was not succefully changed");
		} else {
			ackResponses.remove(index);
		}
		ackResponses.clear();

		// Set navigation speed threshold to 0
		command = "$PMTK397,0*23\r\n";
		serial.write(command);

		Thread.sleep(1000);
		index = ackResponses.lastIndexOf("$PMTK001,397,3*3D");
		if (index == -1) {
			throw new NotActiveException(
					"The navigation speed threshold was not succefully changed");
		} else {
			ackResponses.remove(index);
		}
		ackResponses.clear();
	}

	/**
	 * Processes the received data and splits it by commands and messages
	 * sentences and send them to the correct parser
	 * 
	 * @param data
	 *            : data to be processed
	 */
	private void processReceivedData(String data) {

		data = data.replace("\n", "").replace("\r", "");
		if (data.contains("$GP") || data.contains("$PMTK")) {
			String[] strs = data.split("\\$");

			for (int i = 0; i < strs.length; i++) {
				if (localLog) {
					localLogPrintWriterOut.println("$" + strs[i]);
				}

				if (strs[i].matches(NMEA_REGEX)) {
					parseNMEAData("$" + strs[i]);
				} else {
					if (strs[i].matches(PMTK_REGEX)) {
						parsePMTKData("$" + strs[i]);
					}
				}
			}
		}
	}

	/**
	 * Print the given string on the defined system console
	 * 
	 * @param str
	 *            : string to be printed
	 * @param mode
	 *            : true if an error message, false if is a normal print
	 */
	private void print(String str, boolean mode) {
		if (DEBUG_MODE)
			if (!mode)
				System.out.println(str);
			else
				System.err.println(str);
	}

	public Message getMessage(Message request) {
		if (request instanceof InformationRequest
				&& ((InformationRequest) request).getMessageTypeQuery().equals(
						InformationRequest.MessageType.GPS)) {

			if (!available)
				return new SystemStatusMessage(
						"[GPSModule] Unable to send GPS data");

			return new GPSMessage(getReadings());
		}

		return null;
	}

	@Override
	public boolean isAvailable() {
		return available;
	}

	/*
	 * GPS Parse Functions
	 */
	/**
	 * Parses the NMEA messages send by the GPS receiver, containing the
	 * navigation information
	 * 
	 * @param data
	 *            : NMEA sentence to be processed
	 */
	private void parseNMEAData(String data) {
		data = data.replace("\n", "").replace("\r", "");
		// System.out.println(data);
		if (nmeaUtils.checkNMEAChecksum(data)) {

			String[] params = data.split(",");

			switch (params[0]) {
			case "$GPGGA":
				parseGPGGASentence(params);
				break;

			case "$GPGSA":
				parseGPGSASentence(params);
				break;

			case "$GPGSV":
				parseGPGSVSentence(params);
				break;

			case "$GPRMC":
				parseGPRMCSentence(params);
				break;

			case "$GPVTG":
				parseGPVTGSentence(params);
				break;

			default:
				print("No parser for " + params[0] + " sentence", false);
				break;
			}
		}
	}

	/**
	 * Parses the PMTK acknowledges messages send by the GPS receiver
	 * 
	 * @param data
	 *            : PMTK sentence to be processed
	 */
	private void parsePMTKData(String data) {
		data = data.replace("\n", "").replace("\r", "");
		if (nmeaUtils.checkNMEAChecksum(data)) {
			print("[Parsing PMTK]", false);
			ackResponses.add(data);
			print("[ACK] " + data, false);
		}
	}

	/**
	 * Process Global Positioning System Fix Data
	 * (http://aprs.gids.nl/nmea/#gga)
	 * 
	 * @param params
	 *            : Parameters extracted from GPGGA sentence
	 */
	private void parseGPGGASentence(String[] params) {
		if (params.length == 15) {
			print("[Parsing GPGGA]", false);
			if (params[2].length() != 0) {
				
				double lat = Double.parseDouble(params[2]);
				char latPos = params[3].charAt(0);
				
				double lon = Double.parseDouble(params[4]);
				char lonPos = params[5].charAt(0);
				
				gpsData.setLatitude(""+Nmea0183ToDecimalConverter.convertLatitudeToDecimal(lat, latPos));
				gpsData.setLongitude(""+Nmea0183ToDecimalConverter.convertLongitudeToDecimal(lon, lonPos));
				gpsData.setGPSSourceType(Integer.parseInt(params[6]));
				gpsData.setNumberOfSatellitesInUse(Integer.parseInt(params[7]));
				gpsData.setHDOP(Double.parseDouble(params[8]));

				if (params[9].length() != 0)
					gpsData.setAltitude(Double.parseDouble(params[9]));

				// Missing the geoidal separation (not parsed in this case)
				gpsData.setFix(true);
			} else {
				gpsData.setFix(false);
			}
		}
	}

	/**
	 * GPS DOP and active satellites Data (http://aprs.gids.nl/nmea/#gsa)
	 * 
	 * @param params
	 *            : Parameters extracted from GPGSA sentence
	 */
	private void parseGPGSASentence(String[] params) {
		if (params.length == 18) {
			print("[Parsing GPGSA]", false);
			int fixType = Integer.parseInt(params[2]);
			gpsData.setFixType(fixType);

			if (fixType == 1)
				gpsData.setFix(false);
			else
				gpsData.setFix(true);

			if (params[15].length() != 0 && params[16].length() != 0
					&& params[17].length() != 0) {
				gpsData.setPDOP(Double.parseDouble(params[15]));
				gpsData.setHDOP(Double.parseDouble(params[16]));

				if (params[17].length() > 3)
					gpsData.setVDOP(Double.parseDouble(params[17].substring(0,
							params[17].length() - 3)));
			}

			// Missing list of satellites in view, used to fix (not parsed in
			// this case)
		}
	}

	/**
	 * GPS Satellites in View Data (http://aprs.gids.nl/nmea/#gsv)
	 * 
	 * @param params
	 *            : Parameters extracted from GPGSV sentence
	 */
	private void parseGPGSVSentence(String[] params) {
		if (params.length == 4 && params[3].length() > 3) {
			print("[Parsing GPGSV]", false);
			gpsData.setNumberOfSatellitesInView(Integer.parseInt(params[3]
					.substring(0, params[3].length() - 3)));
		} else {
			if (params.length > 4) {
				print("[Parsing GPGSV]", false);
				gpsData.setNumberOfSatellitesInView(Integer.parseInt(params[3]));
			}
		}
	}

	/**
	 * Recommended minimum specific GPS/Transit data
	 * (http://aprs.gids.nl/nmea/#rmc)
	 * 
	 * @param params
	 *            : Parameters extracted from GPRMC sentence
	 */
	private void parseGPRMCSentence(String[] params) {
		if (params.length == 13) {
			print("[Parsing GPRMC]", false);
			String[] d = params[9].split("(?<=\\G.{2})");

			params[1] = params[1].replace(".", "");
			String[] t = params[1].split("(?<=\\G.{2})");

			LocalDateTime date = new LocalDateTime(
					Integer.parseInt(d[2]) + 100, Integer.parseInt(d[1]),
					Integer.parseInt(d[0]), Integer.parseInt(t[0]),
					Integer.parseInt(t[1]), Integer.parseInt(t[2]),
					Integer.parseInt(t[3] + t[4]));
			gpsData.setDate(date);

			if (params[2].equals("V")) {
				gpsData.setFix(false);
			} else {
				if (params[2].equals("A"))
					gpsData.setFix(true);
			}

			if (params[3].length() != 0 && params[5].length() != 0) {
				
				double lat = Double.parseDouble(params[3]);
				char latPos = params[4].charAt(0);
				
				double lon = Double.parseDouble(params[5]);
				char lonPos = params[6].charAt(0);
				
				gpsData.setLatitude(""+Nmea0183ToDecimalConverter.convertLatitudeToDecimal(lat, latPos));
				gpsData.setLongitude(""+Nmea0183ToDecimalConverter.convertLongitudeToDecimal(lon, lonPos));
				
			}

			gpsData.setGroundSpeedKnts(Double.parseDouble(params[7]));
			gpsData.setOrientation(Double.parseDouble(params[8]));

			// Missing magnetic declination (value and orientation)
		}
	}

	/**
	 * Track Made Good and Ground Speed Data (http://aprs.gids.nl/nmea/#vtg and
	 * http://www.hemispheregps.com/gpsreference/GPVTG.htm)
	 * 
	 * @param params
	 *            : Parameters extracted from GPVTG sentence
	 */
	private void parseGPVTGSentence(String[] params) {
		if (params.length == 10) {
			if (params[1].length() != 0)
				gpsData.setOrientation(Double.parseDouble(params[1]));

			if (params[5].length() != 0)
				gpsData.setGroundSpeedKnts(Double.parseDouble(params[5]));

			if (params[7].length() != 0)
				gpsData.setGroundSpeedKmh(Double.parseDouble(params[7]));
		}
	}

	/*
	 * GPS Functions
	 */
	public String getReleaseVersion() throws InterruptedException {
		serial.write("$PMTK605*31\r\n");

		Thread.sleep(1000);
		int index = -1;
		String str = null;
		for (int i = 0; i < ackResponses.size(); i++) {
			if (ackResponses.get(i).startsWith("$PMTK705")) {
				index = i;
				str = ackResponses.get(i);
			}
		}

		if (index != -1) {
			ackResponses.remove(index);
		}

		return str;
	}

	public boolean startLog() throws InterruptedException {
		serial.write("$PMTK185,0*22\r\n");

		Thread.sleep(1000);
		int index = ackResponses.lastIndexOf("$PMTK001,185,3*3C");
		if (index != -1) {
			ackResponses.remove(index);
			return true;
		}

		return false;

	}

	public boolean stopLog() throws InterruptedException {
		serial.write("$PMTK185,1*23\r\n");

		Thread.sleep(1000);
		int index = ackResponses.lastIndexOf("$PMTK001,185,3*3C");
		if (index != -1) {
			ackResponses.remove(index);
			return true;
		}

		return false;
	}

	public boolean eraseLog() throws InterruptedException {
		serial.write("$PMTK184,1*22\r\n");

		Thread.sleep(1000);
		int index = ackResponses.lastIndexOf("$PMTK001,184,3*3D");
		if (index != -1) {
			ackResponses.remove(index);
			return true;
		}

		return false;
	}

	public boolean enableAlwaysLocateStandby() throws InterruptedException {
		serial.write("$PMTK225,8*23\r\n");

		Thread.sleep(1000);
		int index = ackResponses.lastIndexOf("$PMTK001,225,3*35");
		if (index != -1) {
			ackResponses.remove(index);
			return true;
		}

		return false;
	}

	public boolean disableAlwaysLocateStandby() throws InterruptedException {
		serial.write("$PMTK225,0*2B\r\n");

		Thread.sleep(1000);
		int index = ackResponses.lastIndexOf("$PMTK001,225,3*35");
		if (index != -1) {
			ackResponses.remove(index);
			return true;
		}

		return false;
	}

	public boolean enableAIC() throws InterruptedException {
		serial.write("$PMTK286,1*23\r\n");

		Thread.sleep(1000);
		int index = ackResponses.lastIndexOf("$PMTK001,286,3*3C");
		if (index != -1) {
			ackResponses.remove(index);
			return true;
		}

		return false;
	}

	public boolean disableAIC() throws InterruptedException {
		serial.write("$PMTK286,0*23\r\n");

		Thread.sleep(1000);
		int index = ackResponses.lastIndexOf("$PMTK001,286,3*3C");
		if (index != -1) {
			ackResponses.remove(index);
			return true;
		}

		return false;
	}

	/*
	 * Enables a logging of the received NMEA information in the disk (on the
	 * selected path)
	 */
	public void enableLocalLog() {
		try {
			Calendar cal = Calendar.getInstance();
			cal.getTime();
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

			localLogPrintWriterOut = new PrintWriter(FILE_NAME
					+ sdf.format(cal.getTime()) + ".log");
			localLog = true;
		} catch (FileNotFoundException e) {
			System.err.println("[GPSModuleInput] Unable to start local log");
			e.printStackTrace();
		}
	}
}
