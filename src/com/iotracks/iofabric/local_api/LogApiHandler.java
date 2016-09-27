package com.iotracks.iofabric.local_api;

import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;

import com.iotracks.iofabric.utils.logging.LoggingService;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

public class LogApiHandler implements Callable<Object> {
	private final String MODULE_NAME = "Local API";

	private final HttpRequest req;
	private ByteBuf outputBuffer;
	private final byte[] content;

	public LogApiHandler(HttpRequest request, ByteBuf outputBuffer, byte[] content) {
		this.req = request;
		this.outputBuffer = outputBuffer;
		this.content = content;
	}

	@Override
	public Object call() throws Exception {
		HttpHeaders headers = req.headers();

		if (req.getMethod() != POST) {
			LoggingService.logWarning(MODULE_NAME, "Request method not allowed");
			return new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED);
		}

		if (!(headers.get(HttpHeaders.Names.CONTENT_TYPE).trim().split(";")[0].equalsIgnoreCase("application/json"))) {
			String errorMsg = " Incorrect content type ";
			LoggingService.logWarning(MODULE_NAME, errorMsg);
			outputBuffer.writeBytes(errorMsg.getBytes());
			return new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST, outputBuffer);
		}

		try {
			String msgString = new String(content, StandardCharsets.UTF_8);
			JsonReader reader = Json.createReader(new StringReader(msgString));
			JsonObject jsonObject = reader.readObject();

			String logMessage = jsonObject.getString("message");
			String logType = jsonObject.getString("type");
			String elementId = jsonObject.getString("id");
			boolean result = true;
			if (logType.equals("info"))
				result = LoggingService.elementLogInfo(elementId, logMessage);
			else
				result = LoggingService.elementLogWarning(elementId, logMessage);
			
			if (!result) 
				throw new Exception("Logger error");
		} catch (Exception e) {
			
			String errorMsg = " Log message pasring error, " + e.getMessage();
			LoggingService.logWarning(MODULE_NAME, errorMsg);
			outputBuffer.writeBytes(errorMsg.getBytes());
			return new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST, outputBuffer);
		}

		JsonBuilderFactory factory = Json.createBuilderFactory(null);
		JsonObjectBuilder builder = factory.createObjectBuilder();
		builder.add("status", "okay");

		String sendMessageResult = builder.build().toString();
		outputBuffer.writeBytes(sendMessageResult.getBytes());
		FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK, outputBuffer);
		HttpHeaders.setContentLength(res, outputBuffer.readableBytes());
		return res;
	}

}
