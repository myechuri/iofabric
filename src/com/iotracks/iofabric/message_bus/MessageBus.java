package com.iotracks.iofabric.message_bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.iotracks.iofabric.element.Element;
import com.iotracks.iofabric.element.ElementManager;
import com.iotracks.iofabric.element.Route;
import com.iotracks.iofabric.status_reporter.StatusReporter;
import com.iotracks.iofabric.utils.Constants;
import com.iotracks.iofabric.utils.Constants.ModulesStatus;
import com.iotracks.iofabric.utils.configuration.Configuration;
import com.iotracks.iofabric.utils.logging.LoggingService;

/**
 * Message Bus module
 * 
 * @author saeid
 *
 */
public class MessageBus {
	
	private final String MODULE_NAME = "Message Bus";

	private MessageBusServer messageBusServer;
	private Map<String, Route> routes;
	private Map<String, MessagePublisher> publishers;
	private Map<String, MessageReceiver> receivers;
	private MessageIdGenerator idGenerator;
	private static MessageBus instance;
	private ElementManager elementManager;
	private Object updateLock = new Object();
	
	private long lastSpeedTime, lastSpeedMessageCount;
	
	private MessageBus() {
	}
	
	public static MessageBus getInstance() {
		if (instance == null) {
			synchronized (MessageBus.class) {
				if (instance == null) { 
					instance = new MessageBus();
					instance.start();
				}
			}
		}
		return instance;
	}
	
	
	/**
	 * enables real-time {@link Message} receiving of an {@link Element} 
	 * 
	 * @param receiver - ID of {@link Element}
	 */
	public synchronized void enableRealTimeReceiving(String receiver) {
		MessageReceiver rec = receivers.get(receiver); 
		if (rec == null)
			return;
		rec.enableRealTimeReceiving();
	}

	/**
	 * disables real-time {@link Message} receiving of an {@link Element} 
	 * 
	 * @param receiver - ID of {@link Element}
	 */
	public synchronized void disableRealTimeReceiving(String receiver) {
		MessageReceiver rec = receivers.get(receiver); 
		if (rec == null)
			return;
		rec.disableRealTimeReceiving();
	}

	/**
	 * initialize list of {@link Message} publishers and receivers
	 * 
	 */
	private void init() {
		lastSpeedMessageCount = 0;
		lastSpeedTime = System.currentTimeMillis();
		
		routes = elementManager.getRoutes();
		idGenerator = new MessageIdGenerator();
		publishers = new ConcurrentHashMap<>();
		receivers = new ConcurrentHashMap<>();

		if (routes == null)
			return;
		
		routes.entrySet().stream()
			.filter(route -> route.getValue() != null)
			.filter(route -> route.getValue().getReceivers() != null)
			.forEach(entry -> {
					String publisher = entry.getKey();
					Route route = entry.getValue();
				
					try {
						messageBusServer.createProducer(publisher);
					} catch (Exception e) {
						LoggingService.logWarning(MODULE_NAME + "(" + publisher + ")",
								"unable to start publisher module --> " + e.getMessage());
					}
					publishers.put(publisher, new MessagePublisher(publisher, route, messageBusServer.getProducer(publisher)));

					receivers.putAll(entry.getValue().getReceivers()
							.stream()
							.filter(item -> !receivers.containsKey(item))
							.collect(Collectors.toMap(item -> item, item -> {
								try {
									messageBusServer.createCosumer(item);
								} catch (Exception e) {
									LoggingService.logWarning(MODULE_NAME + "(" + item + ")",
											"unable to start receiver module --> " + e.getMessage());
								}
								return new MessageReceiver(item, messageBusServer.getConsumer(item));
							})));
			});

	}
	
	/**
	 * calculates the average speed of {@link Message} moving through ioFabric
	 * 
	 */
	private final Runnable calculateSpeed = () -> {
		while (true) {
			try {
				Thread.sleep(Constants.SPEED_CALCULATION_FREQ_MINUTES * 60 * 1000);

				LoggingService.logInfo(MODULE_NAME, "calculating message processing speed");

				long now = System.currentTimeMillis();
				long msgs = StatusReporter.getMessageBusStatus().getProcessedMessages();

				float speed = ((float)(msgs - lastSpeedMessageCount)) / ((now - lastSpeedTime) / 1000f);
				StatusReporter.setMessageBusStatus().setAverageSpeed(speed);
				lastSpeedMessageCount = msgs;
				lastSpeedTime = now;
			} catch (Exception e) {}
		}
	};
	
	/**
	 * monitors HornetQ server
	 * 
	 */
	private final Runnable checkMessageServerStatus = () -> {
		while (true) {
			try {
				Thread.sleep(5000);

				LoggingService.logInfo(MODULE_NAME, "check message bus server status");
				if (!messageBusServer.isServerActive()) {
					LoggingService.logWarning(MODULE_NAME, "server is not active. restarting...");
					stop();
					try {
						messageBusServer.startServer();
						LoggingService.logInfo(MODULE_NAME, "server restarted");
						init();
					} catch (Exception e) {
						LoggingService.logWarning(MODULE_NAME, "server restart failed --> " + e.getMessage());
					}
				}

				publishers.entrySet().forEach(entry -> {
					String publisher = entry.getKey();
					if (messageBusServer.isProducerClosed(publisher)) {
						LoggingService.logWarning(MODULE_NAME, "producer module for " + publisher + " stopped. restarting...");
						entry.getValue().close();
						Route route = routes.get(publisher);
						if (route.equals(null) || route.getReceivers() == null || route.getReceivers().size() == 0) {
							publishers.remove(publisher);
						} else {
							try {
								messageBusServer.createProducer(publisher);
								publishers.put(publisher, new MessagePublisher(publisher, route, messageBusServer.getProducer(publisher)));
								LoggingService.logInfo(MODULE_NAME, "producer module restarted");
							} catch (Exception e) {
								LoggingService.logWarning(MODULE_NAME, "unable to restart producer module for " + publisher + " --> " + e.getMessage());
							}
						}
					}
				});

				receivers.entrySet().forEach(entry -> {
					String receiver = entry.getKey();
					if (messageBusServer.isConsumerClosed(receiver)) {
						LoggingService.logWarning(MODULE_NAME, "consumer module for " + receiver + " stopped. restarting...");
						entry.getValue().close();
						try {
							messageBusServer.createCosumer(receiver);
							receivers.put(receiver, new MessageReceiver(receiver, messageBusServer.getConsumer(receiver)));
							LoggingService.logInfo(MODULE_NAME, "consumer module restarted");
						} catch (Exception e) {
							LoggingService.logWarning(MODULE_NAME, "unable to restart consumer module for " + receiver + " --> " + e.getMessage());
						}
					}
				});
			} catch (Exception e) {
			}
		}
	};
	
	/**
	 * updates routing, list of publishers and receivers
	 * Field Agent calls this method when any changes applied
	 * 
	 */
	public void update() {
		synchronized (updateLock) {
			Map<String, Route> newRoutes = elementManager.getRoutes();
			List<String> newPublishers = new ArrayList<>();
			List<String> newReceivers = new ArrayList<>();
			
			if (newRoutes != null) {
				newRoutes.entrySet()
					.stream()
					.filter(route -> route.getValue() != null)
					.filter(route -> route.getValue().getReceivers() != null)
					.forEach(entry -> {
						newPublishers.add(entry.getKey());
						newReceivers.addAll(entry.getValue().getReceivers()
								.stream().filter(item -> !newReceivers.contains(item))
								.collect(Collectors.toList()));
					});
			}
			
			publishers.entrySet().forEach(entry -> {
				if (!newPublishers.contains(entry.getKey())) {
					entry.getValue().close();
					messageBusServer.removeProducer(entry.getKey());
				} else {
					entry.getValue().updateRoute(newRoutes.get(entry.getKey()));
				}
			});
			publishers.entrySet().removeIf(entry -> !newPublishers.contains(entry.getKey()));
			publishers.putAll(
					newPublishers.stream()
					.filter(publisher -> !publishers.containsKey(publisher))
					.collect(Collectors.toMap(publisher -> publisher, 
							publisher -> new MessagePublisher(publisher, newRoutes.get(publisher), messageBusServer.getProducer(publisher)))));

			receivers.entrySet().forEach(entry -> {
				if (!newReceivers.contains(entry.getKey())) {
					entry.getValue().close();
					messageBusServer.removeConsumer(entry.getKey());
				}
			});
			receivers.entrySet().removeIf(entry -> !newReceivers.contains(entry.getKey()));
			receivers.putAll(
					newReceivers.stream()
					.filter(receiver -> !receivers.containsKey(receiver))
					.collect(Collectors.toMap(receiver -> receiver, 
							receiver -> new MessageReceiver(receiver, messageBusServer.getConsumer(receiver)))));

			routes = newRoutes;

			StatusReporter.getMessageBusStatus()
				.getPublishedMessagesPerElement().entrySet().removeIf(entry -> {
					return !elementManager.elementExists(entry.getKey());
				});
			elementManager.getElements().forEach(e -> {
				if (!StatusReporter.getMessageBusStatus().getPublishedMessagesPerElement().entrySet().contains(e.getElementId()))
						StatusReporter.getMessageBusStatus().getPublishedMessagesPerElement().put(e.getElementId(), 0l);
			});
		}
	}
	
	/**
	 * sets  memory usage limit of HornetQ
	 * {@link Configuration} calls this method when any changes applied
	 * 
	 */
	public void instanceConfigUpdated() {
		messageBusServer.setMemoryLimit();
	}
	
	/**
	 * starts Message Bus module
	 * 
	 */
	public void start() {
		elementManager = ElementManager.getInstance();
		
		messageBusServer = new MessageBusServer();
		try {
			LoggingService.logInfo(MODULE_NAME, "STARTING MESSAGE BUS SERVER");
			messageBusServer.startServer();
			messageBusServer.initialize();
		} catch (Exception e) {
			try {
				messageBusServer.stopServer();
			} catch (Exception e1) {}
			LoggingService.logWarning(MODULE_NAME, "unable to start message bus server --> " + e.getMessage());
			StatusReporter.setSupervisorStatus().setModuleStatus(Constants.MESSAGE_BUS, ModulesStatus.STOPPED);
		}
		
		LoggingService.logInfo(MODULE_NAME, "MESSAGE BUS SERVER STARTED");
		init();

		new Thread(calculateSpeed, "MessageBus : CalculateSpeed").start();
		new Thread(checkMessageServerStatus, "MessageBus : CheckMessageBusServerStatus").start();
	}
	
	/**
	 * closes receivers and publishers and stops HornetQ server
	 * 
	 */
	public void stop() {
		for (MessageReceiver receiver : receivers.values()) 
			receiver.close();
		
		for (MessagePublisher publisher : publishers.values())
			publisher.close();
		try {
			messageBusServer.stopServer();
		} catch (Exception e) {}
	}

	/**
	 * returns {@link MessagePublisher}
	 * 
	 * @param publisher - ID of {@link Element}
	 * @return
	 */
	public MessagePublisher getPublisher(String publisher) {
		return publishers.get(publisher);
	}

	/**
	 * returns {@link MessageReceiver}
	 * 
	 * @param receiver - ID of {@link Element}
	 * @return
	 */
	public MessageReceiver getReceiver(String receiver) {
		return receivers.get(receiver);
	}
	
	/**
	 * returns next generated message id
	 * 
	 * @return
	 */
	public synchronized String getNextId() {
		return idGenerator.getNextId();
	}
	
	/**
	 * returns routes
	 * 
	 * @return
	 */
	public synchronized Map<String, Route> getRoutes() {
		return elementManager.getRoutes();
	}
}
