package com.iotracks.iofabric.utils;

import java.io.PrintStream;

/**
 * holds IOFabric constants
 * 
 * @author saeid
 *
 */
public class Constants {
	public enum ModulesStatus {
		STARTING, RUNNING, STOPPED
	}

	public enum DockerStatus {
		NOT_PRESENT, RUNNING, STOPPED
	}

	public enum ElementState {
		BUILDING, FAILED_VERIFICATION, STARTING, RUNNING, STOPPED 
	}
	
	public enum LinkStatus {
		FAILED_VERIFICATION, FAILED_LOGIN, CONNECTED
	}

	public enum ControllerStatus {
		NOT_PROVISIONED, BROKEN, OK
	}
	
	public static final String VERSION = "1.28";
	
	public static final int NUMBER_OF_MODULES = 6;

	public static final int RESOURCE_CONSUMPTION_MANAGER = 0;
	public static final int PROCESS_MANAGER = 1;
	public static final int STATUS_REPORTER = 2;
	public static final int LOCAL_API = 3;
	public static final int MESSAGE_BUS = 4;
	public static final int FIELD_AGENT = 5;

	public static PrintStream systemOut;

	public static final String address = "iofabric.message_bus";
	public static final String commandlineAddress = "iofabric.commandline";
	
	public static final int KiB = 1024;
	public static final int MiB = 1024 * 1024;
	public static final int GiB = 1024 * 1024 * 1024;

	public static String osArch = System.getProperty("os.arch");

	public static final int STATUS_REPORT_FREQ_SECONDS = osArch.equals("arm") ? 10 : 5;

	public static final int GET_CHANGES_LIST_FREQ_SECONDS = osArch.equals("arm") ? 30 : 20;
	public static final int PING_CONTROLLER_FREQ_SECONDS = 60;
	public static final int POST_STATUS_FREQ_SECONDS = osArch.equals("arm") ? 20 : 5;

	public static final int SPEED_CALCULATION_FREQ_MINUTES = 1;

	public static final int MONITOR_CONTAINERS_STATUS_FREQ_SECONDS = osArch.equals("arm") ? 30 : 10;
	public static final int MONITOR_REGISTRIES_STATUS_FREQ_SECONDS = osArch.equals("arm") ? 120 : 60;

	public static final long GET_USAGE_DATA_FREQ_SECONDS = osArch.equals("arm") ? 20 : 5;

	public static final String DOCKER_API_VERSION = osArch.equals("arm") ? "1.22" : "1.23";

	public static int SET_SYSTEM_TIME_FREQ_SECONDS = 60;
	
	public static int getFabricType() {
		if (osArch.equals("arm"))
			return 2;
		else
			return 1;
	}
}

