package com.iotracks.iofabric.command_line;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.json.Json;
import javax.json.JsonObject;

import com.iotracks.iofabric.field_agent.FieldAgent;
import com.iotracks.iofabric.status_reporter.StatusReporter;
import com.iotracks.iofabric.utils.Constants;
import com.iotracks.iofabric.utils.configuration.Configuration;
import com.iotracks.iofabric.utils.logging.LoggingService;

/**
 * to parse command-line parameters 
 * 
 * @author saeid
 *
 */
public class CommandLineParser {

	/**
	 * parse command-line parameters 
	 * 
	 * @param command - command-line parameters
	 * 
	 * @return String
	 */
	public static String parse(String command) {
		String[] args = command.split(" ");
		StringBuilder result = new StringBuilder();

		if (args[0].equals("stop")) {
			System.setOut(Constants.systemOut);
			System.exit(0);
		}

		if (args[0].equals("start")) {
			return "";
		}

		if (args[0].equals("help") || args[0].equals("--help") || args[0].equals("-h") || args[0].equals("-?")) {
			result.append(showHelp());
			return result.toString();
		}

		if (args[0].equals("version") || args[0].equals("--version") || args[0].equals("-v")) {
			result.append("ioFabric 1.20");
			result.append("\nCopyright (C) 2016 iotracks, inc.");
			result.append("\nLicense ######### http://iotracks.com/license");
			result.append(
					"\nThis is open-source software with a commercial license: your usage is free until you use it in production commercially.");
			result.append("\nThere is NO WARRANTY, to the extent permitted by law.");

			return result.toString();
		}

		if (args[0].equals("status")) {
			return StatusReporter.getStatusReport();
		}

		if (args[0].equals("deprovision")) {
			result.append("Deprovisioning from controller...");
			try {
				result.append(FieldAgent.getInstance().deProvision());
			} catch (Exception e) {}

			return result.toString();
		}

		if (args[0].equals("info")) {
			result.append(Configuration.getConfigReport());

			return result.toString();
		}

		if (args[0].equals("provision")) {
			if (args.length < 2) {
				return showHelp();
			}
			String provisionKey = args[1];
			result.append("Provisioning with key \"" + provisionKey + "\"...");
			JsonObject provisioningResult = FieldAgent.getInstance().provision(provisionKey);
			if (provisioningResult == null) {
				result.append("\nProvisioning failed");
			} else if (provisioningResult.getString("status").equals("ok")) {
				String instanceId = provisioningResult.getString("id");
				result.append(String.format("\nSuccess - instance ID is %s", instanceId));
			} else {
				result.append(String.format("\nProvisioning failed - %s", provisioningResult.getString("errormessage")));
			}

			return result.toString();
		}

		if (args[0].equals("config")) {
			if (args.length == 1) {
				return showHelp();
			}
			
			if (args.length == 2) {
				if (args[1].equals("defaults")) {
					try {
						Configuration.resetToDefault();
					} catch (Exception e) {
						return "Error resetting configuration.";
					}
					return "Configuration has been reset to its defaults.";
				} else if  (!args[1].equals("-ac"))
					return showHelp();
			}

			Map<String, Object> config = new HashMap<>();
			int i = 1;
			while (i < args.length) {
				if (args.length - i < 2 && !args[i].equals("-ac"))
					return showHelp();
				if (!args[i].equals("-d") && !args[i].equals("-dl") && !args[i].equals("-m") && !args[i].equals("-p")
						&& !args[i].equals("-a") && !args[i].equals("-ac") && !args[i].equals("-c")
						&& !args[i].equals("-n") && !args[i].equals("-l") && !args[i].equals("-ld") && !args[i].equals("-lc"))
					return showHelp();

				String option = args[i].substring(1);
				String value = "";
				if(option.equals("ac") && args.length == 2){
					value = ""; i += 1; 
				}
				else if(option.equals("ac") && ((args[i+1].equals("-d") || args[i+1].equals("-dl") || args[i+1].equals("-m") || args[i+1].equals("-p")
						|| args[i+1].equals("-a") || args[i+1].equals("-ac") || args[i+1].equals("-c")
						|| args[i+1].equals("-n") || args[i+1].equals("-l") || args[i+1].equals("-ld") || args[i+1].equals("-lc")))){
					value = ""; i += 1; 
				}
				else{ value = args[i + 1];
					i += 2;
				}
				config.put(option, value);
			}

			try {

				HashMap<String, String> oldValuesMap = Configuration.getOldNodeValuesForParameters(config.keySet());
				HashMap<String, String> errorMap = Configuration.setConfig(config, false);

				for (Entry<String, String> e : errorMap.entrySet())
					result.append("\n\tError : " + e.getValue());

				for (Entry<String, Object> e : config.entrySet()){
					if(!errorMap.containsKey(e.getKey())){
						String newValue = e.getValue().toString();
						if(e.getValue().toString().startsWith("+")) newValue = e.getValue().toString().substring(1);
						result.append("\n\tChange accepted for Parameter : -").append(e.getKey()).append(", Old value was :").append(oldValuesMap.get(e.getKey()))
						.append(", New Value is : ").append(newValue);
					}
				}
			} catch (Exception e) {
				LoggingService.logWarning("Command-line Parser", "error updating new config.");
				result.append("error updating new config : " + e.getMessage());
			}

			return result.toString();
		}

		return showHelp();
	}

	/**
	 * returns help 
	 * 
	 * @return String
	 */
	private static String showHelp() {
		StringBuilder help = new StringBuilder();
		help.append("Usage 1: iofabric [OPTION]\n" + 
				"Usage 2: iofabric [COMMAND] <Argument>\n" + 
				"Usage 3: iofabric [COMMAND] [Parameter] <Value>\n" + 
				"\n" + 
				"Option           GNU long option         Meaning\n" + 
				"======           ===============         =======\n" + 
				"-h, -?           --help                  Show this message\n" + 
				"-v               --version               Display the software version and\n" + 
				"                                         license information\n" + 
				"\n" + 
				"\n" + 
				"Command          Arguments               Meaning\n" + 
				"=======          =========               =======\n" + 
				"help                                     Show this message\n" + 
				"version                                  Display the software version and\n" + 
				"                                         license information\n" + 
				"status                                   Display current status information\n" + 
				"                                         about the software\n" + 
				"provision        <provisioning key>      Attach this software to the\n" + 
				"                                         configured ioFabric controller\n" + 
				"deprovision                              Detach this software from all\n" + 
				"                                         ioFabric controllers\n" + 
				"info                                     Display the current configuration\n" + 
				"                                         and other information about the\n" + 
				"                                         software\n" + 
				"config           [Parameter] [VALUE]     Change the software configuration\n" + 
				"                                         according to the options provided\n" + 
				"                 -d <#GB Limit>          Set the limit, in GiB, of disk space\n" + 
				"                                         that the software is allowed to use\n" + 
				"                 -dl <dir>               Set the directory to use for disk\n" + 
				"                                         storage\n" + 
				"                 -m <#MB Limit>          Set the limit, in MiB, of RAM memory that\n" + 
				"                                         the software is allowed to use for\n" + 
				"                                         messages\n" + 
				"                 -p <#cpu % Limit>       Set the limit, in percentage, of CPU\n" + 
				"                                         time that the software is allowed\n" + 
				"                                         to use\n" + 
				"                 -a <uri>                Set the uri of the fabric controller\n" + 
				"                                         to which this software connects\n" + 
				"                 -ac <filepath>          Set the file path of the SSL/TLS\n" + 
				"                                         certificate for validating the fabric\n" + 
				"                                         controller identity\n" + 
				"                 -c <uri>                Set the UNIX socket or network address\n" + 
				"                                         that the Docker daemon is using\n" + 
				"                 -n <network adapter>    Set the name of the network adapter\n" + 
				"                                         that holds the correct IP address of \n" + 
				"                                         this machine\n" + 
				"                 -l <#MB Limit>          Set the limit, in MiB, of disk space\n" + 
				"                                         that the log files can consume\n" + 
				"                 -ld <dir>               Set the directory to use for log file\n" + 
				"                                         storage\n" + 
				"                 -lc <#log files>        Set the number of log files to evenly\n" + 
				"                                         split the log storage limit\n" + 
				"\n" + 
				"\n" + 
				"Report bugs to: kilton@iotracks.com\n" + 
				"ioFabric home page: http://iotracks.com");

		return help.toString();
	}

}
