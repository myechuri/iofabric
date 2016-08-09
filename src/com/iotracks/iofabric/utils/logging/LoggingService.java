package com.iotracks.iofabric.utils.logging;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Date;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.iotracks.iofabric.utils.configuration.Configuration;

/**
 * sets up and starts logging
 * 
 * @author saeid
 *
 */
public final class LoggingService {

	private static Logger logger = null;

	private LoggingService() {

	}

	/**
	 * logs Level.INFO message
	 * 
	 * @param moduleName - name of module
	 * @param msg - message
	 */
	public static void logInfo(String moduleName, String msg) {
		if (Configuration.debugging)
			System.out.println(String.format("%s : %s (%s)", moduleName, msg, new Date(System.currentTimeMillis())));
		else
			logger.log(Level.INFO, String.format("[%s] : %s", moduleName, msg));
	}

	/**
	 * logs Level.WARNING message
	 * 
	 * @param moduleName - name of module
	 * @param msg - message
	 */
	public static void logWarning(String moduleName, String msg) {
		if (Configuration.debugging)
			System.out.println(String.format("%s : %s (%s)", moduleName, msg, new Date(System.currentTimeMillis())));
		else
			logger.log(Level.WARNING, String.format("[%s] : %s", moduleName, msg));
	}

	/**
	 * sets up logging
	 * 
	 * @throws IOException
	 */
	public static void setupLogger() throws IOException {
		int maxFileSize = (int) (Configuration.getLogDiskLimit() * 1_000_000); 
		int logFileCount = Configuration.getLogFileCount();
		final File logDirectory = new File(Configuration.getLogDiskDirectory());

		logDirectory.mkdirs();

		UserPrincipalLookupService lookupservice = FileSystems.getDefault().getUserPrincipalLookupService();
		final GroupPrincipal group = lookupservice.lookupPrincipalByGroupName("iofabric");
		Files.getFileAttributeView(logDirectory.toPath(), PosixFileAttributeView.class,
				LinkOption.NOFOLLOW_LINKS).setGroup(group);
		Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwx---");
		Files.setPosixFilePermissions(logDirectory.toPath(), perms);

		final String logFilePattern = logDirectory.getPath() + "/iofabric.%g.log";
		
		if (logger != null) {
			for (Handler f : logger.getHandlers())
				f.close();
		}
		
		Handler logFileHandler = new FileHandler(logFilePattern, (maxFileSize / logFileCount) * 1_000, logFileCount);
	
		logFileHandler.setFormatter(new LogFormatter());
	
		logger = Logger.getLogger("com.iotracks.iofabric");
		logger.addHandler(logFileHandler);

		logger.setUseParentHandlers(false);

		logger.info("logger started.");
	}
	
	/**
	 * resets logging with new configurations
	 * this method called by {@link Configuration}
	 * 
	 */
	public static void instanceConfigUpdated() {
		try {
			setupLogger();
		} catch (Exception e) {}
	}
	
}
