package com.iotracks.iofabric.local_api;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;

import java.util.Hashtable;

import com.iotracks.iofabric.message_bus.Message;
import com.iotracks.iofabric.message_bus.MessageBus;
import com.iotracks.iofabric.message_bus.MessageBusUtil;
import com.iotracks.iofabric.status_reporter.StatusReporter;
import com.iotracks.iofabric.utils.BytesUtil;
import com.iotracks.iofabric.utils.logging.LoggingService;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;

/**
 * Hadler for the real-time message websocket Open real-time message websocket
 * Send and receive real-time messages
 * 
 * @author ashita
 * @since 2016
 */
public class MessageWebsocketHandler {

	private static final Byte OPCODE_PING = 0x9;
	private static final Byte OPCODE_PONG = 0xA;
	private static final Byte OPCODE_ACK = 0xB;
	private static final Byte OPCODE_MSG = 0xD;
	private static final Byte OPCODE_RECEIPT = 0xE;

	private final String MODULE_NAME = "Local API";
	private static final String WEBSOCKET_PATH = "/v2/message/socket";

	private WebSocketServerHandshaker handshaker;

	/**
	 * Handler to open the websocket for the real-time message websocket
	 * 
	 * @param ChannelHandlerContext,
	 *            FullHttpRequest
	 * @return void
	 */
	public void handle(ChannelHandlerContext ctx, HttpRequest req) throws Exception {
		String uri = req.getUri();
		uri = uri.substring(1);
		String[] tokens = uri.split("/");
		String publisherId;

		if (tokens.length < 5) {
			LoggingService.logWarning(MODULE_NAME, " Missing ID or ID value in URL ");
			return;
		} else {
			publisherId = tokens[4].trim().split("\\?")[0];
		}

		// Handshake
		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req),
				null, true, Integer.MAX_VALUE);
		handshaker = wsFactory.newHandshaker(req);
		if (handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
		} else {
			handshaker.handshake(ctx.channel(), req);
		}

		Hashtable<String, ChannelHandlerContext> messageSocketMap = WebSocketMap.messageWebsocketMap;
		messageSocketMap.put(publisherId, ctx);
		StatusReporter.setLocalApiStatus().setOpenConfigSocketsCount(WebSocketMap.messageWebsocketMap.size());
		MessageBus.getInstance().enableRealTimeReceiving(publisherId);

		LoggingService.logInfo(MODULE_NAME, "Handshake end....");
		return;
	}

	/**
	 * Handler for the real-time messages Receive ping and send pong Sending and
	 * receiving real-time messages
	 * 
	 * @param ChannelHandlerContext,
	 *            WebSocketFrame
	 * @return void
	 */
	public void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {

		if (frame instanceof PingWebSocketFrame) {
			ByteBuf buffer = frame.content();
			if (buffer.readableBytes() == 1) {
				Byte opcode = buffer.readByte();
				if (opcode == OPCODE_PING.intValue()) {
					if (WebsocketUtil.hasContextInMap(ctx, WebSocketMap.messageWebsocketMap)) {
						ByteBuf buffer1 = ctx.alloc().buffer();
						buffer1.writeByte(OPCODE_PONG.intValue());
						ctx.channel().writeAndFlush(new PongWebSocketFrame(buffer1));
					}
				}
			} else {
				LoggingService.logInfo(MODULE_NAME, "Ping opcode not found");
			}

			return;
		}

		if (frame instanceof BinaryWebSocketFrame) {
			ByteBuf input = frame.content();
			if (!input.isReadable()) {
				return;
			}

			byte[] byteArray = new byte[input.readableBytes()];
			int readerIndex = input.readerIndex();
			input.getBytes(readerIndex, byteArray);
			Byte opcode = 0;

			if(byteArray.length >= 1){
				opcode = byteArray[0];
			}else{
				return;
			}

			if (opcode == OPCODE_MSG.intValue()) {
				if (byteArray.length >= 2) {
					opcode = byteArray[0];
					Message message = null;

					if (WebsocketUtil.hasContextInMap(ctx, WebSocketMap.messageWebsocketMap)) {

						int totalMsgLength = BytesUtil.bytesToInteger(BytesUtil.copyOfRange(byteArray, 1, 5));
						try {
							message = new Message(BytesUtil.copyOfRange(byteArray, 5, totalMsgLength + 5));
							LoggingService.logInfo(MODULE_NAME, message.toString());
						} catch (Exception e) {
							LoggingService.logInfo(MODULE_NAME, "wrong message format  " + e.getMessage());
							LoggingService.logInfo(MODULE_NAME, "Validation fail");
						}

						MessageBusUtil messageBus = new MessageBusUtil();
						messageBus.publishMessage(message);

						String messageId = message.getId();
						Long msgTimestamp = message.getTimestamp();
						ByteBuf buffer1 = ctx.alloc().buffer();

						buffer1.writeByte(OPCODE_RECEIPT.intValue());

						// send Length
						int msgIdLength = messageId.length();
						buffer1.writeByte(msgIdLength);
						buffer1.writeByte(Long.BYTES);

						// Send opcode, id and timestamp
						buffer1.writeBytes(messageId.getBytes());
						buffer1.writeBytes(BytesUtil.longToBytes(msgTimestamp));
						ctx.channel().write(new BinaryWebSocketFrame(buffer1));
					}
					return;
				}
			} else if (opcode == OPCODE_ACK.intValue()) {
				WebSocketMap.unackMessageSendingMap.remove(ctx);
				return;
			}
			
			return;
		}

		// Check for closing frame
		if (frame instanceof CloseWebSocketFrame) {
			ctx.channel().close();
			MessageBus.getInstance()
			.disableRealTimeReceiving(WebsocketUtil.getIdForWebsocket(ctx, WebSocketMap.messageWebsocketMap));
			WebsocketUtil.removeWebsocketContextFromMap(ctx, WebSocketMap.messageWebsocketMap);
			StatusReporter.setLocalApiStatus().setOpenConfigSocketsCount(WebSocketMap.messageWebsocketMap.size());
			return;
		}
	}

	/**
	 * Helper to send real-time messages
	 * 
	 * @param String,
	 *            Message
	 * @return void
	 */
	public void sendRealTimeMessage(String receiverId, Message message) {
		ChannelHandlerContext ctx = null;
		Hashtable<String, ChannelHandlerContext> messageSocketMap = WebSocketMap.messageWebsocketMap;

		if (messageSocketMap != null && messageSocketMap.containsKey(receiverId)) {
			ctx = messageSocketMap.get(receiverId);
			WebSocketMap.unackMessageSendingMap.put(ctx, new MessageSentInfo(message, 1, System.currentTimeMillis()));

			int totalMsgLength = 0;

			byte[] bytesMsg = null;
			try {
				bytesMsg = message.getBytes();
			} catch (Exception e) {
				LoggingService.logWarning(MODULE_NAME, "Problem in retrieving the message");
			}

			totalMsgLength = bytesMsg.length;

			ByteBuf buffer1 = ctx.alloc().buffer(totalMsgLength + 5);
			// Send Opcode
			buffer1.writeByte(OPCODE_MSG);
			// Total Length
			buffer1.writeBytes(BytesUtil.integerToBytes(totalMsgLength));
			// Message
			buffer1.writeBytes(bytesMsg);
			ctx.channel().writeAndFlush(new BinaryWebSocketFrame(buffer1));
		} else {
			LoggingService.logWarning(MODULE_NAME, "No active real-time websocket found for " + receiverId);
		}

	}

	/**
	 * Websocket path
	 * 
	 * @param FullHttpRequest
	 * @return void
	 */
	private static String getWebSocketLocation(HttpRequest req) {
		String location = req.headers().get(HOST) + WEBSOCKET_PATH;
		if (LocalApiServer.SSL) {
			return "wss://" + location;
		} else {
			return "ws://" + location;
		}
	}
}