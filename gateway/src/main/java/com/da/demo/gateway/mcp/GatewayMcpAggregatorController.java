package com.da.demo.gateway.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Spring AI / Model Context Protocol (MCP) Unified Gateway Aggregator.
 * Aggregates all MCP tools across Admin, Booking, and Inventory microservices into a single /mcp endpoint.
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class GatewayMcpAggregatorController {

    private static final Logger log = LoggerFactory.getLogger(GatewayMcpAggregatorController.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${mcp.backend.admin.url:http://localhost:8081/mcp/admin/message}")
    private String adminMcpUrl;

    @Value("${mcp.backend.booking.url:http://localhost:8083/mcp/booking/message}")
    private String bookingMcpUrl;

    @Value("${mcp.backend.inventory.url:http://localhost:8084/mcp/inventory/message}")
    private String inventoryMcpUrl;

    // Fast tool name -> backend URL lookup
    private static final Set<String> ADMIN_TOOLS = Set.of(
            "addBusDetails", "getAllBuses", "getStats", "getBusByNumber", "fetchBuses", "deleteBus"
    );
    private static final Set<String> BOOKING_TOOLS = Set.of(
            "bookBusTicket", "getBookingsByUser", "getBookingById", "cancelBooking"
    );
    private static final Set<String> INVENTORY_TOOLS = Set.of(
            "getAvailableBus", "getAvailableSeats", "saveBusSeats"
    );

    public GatewayMcpAggregatorController(ObjectMapper objectMapper) {
        this.webClient = WebClient.builder().build();
        this.objectMapper = objectMapper;
    }

    /**
     * Unified Server-Sent Events (SSE) handshake for MCP clients (VS Code, Claude, Cursor).
     */
    @GetMapping(value = {"/mcp", "/mcp/sse"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sseEndpoint(@RequestParam(value = "sessionId", required = false) String requestedSessionId) {
        String sessionId = (requestedSessionId != null && !requestedSessionId.isBlank())
                ? requestedSessionId
                : UUID.randomUUID().toString();

        log.info("MCP Client connected to unified gateway SSE. Session ID: {}", sessionId);

        // 1. Initial endpoint event directing client to POST endpoint with sessionId
        ServerSentEvent<String> endpointEvent = ServerSentEvent.<String>builder()
                .event("endpoint")
                .data("/mcp/message?sessionId=" + sessionId)
                .build();

        // 2. Periodic keep-alive heartbeat ping every 15s
        Flux<ServerSentEvent<String>> heartbeats = Flux.interval(Duration.ofSeconds(15))
                .map(seq -> ServerSentEvent.<String>builder()
                        .comment("ping")
                        .build());

        return Flux.concat(Flux.just(endpointEvent), heartbeats);
    }

    /**
     * Unified JSON-RPC 2.0 message handler.
     * Aggregates tools/list across all microservices and routes tools/call dynamically.
     */
    @PostMapping(value = "/mcp/message", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> handleMessage(@RequestBody String requestBody, @RequestParam(value = "sessionId", required = false) String sessionId) {
        try {
            JsonNode request = objectMapper.readTree(requestBody);
            String method = request.has("method") ? request.get("method").asText() : "";
            JsonNode idNode = request.get("id");

            log.info("Received MCP JSON-RPC method: {} (id: {})", method, idNode);

            Mono<JsonNode> resultMono = switch (method) {
                case "initialize" -> handleInitialize(idNode);
                case "notifications/initialized" -> Mono.just(objectMapper.createObjectNode());
                case "ping" -> handlePing(idNode);
                case "tools/list" -> handleToolsList(request);
                case "tools/call" -> handleToolsCall(request);
                default -> {
                    log.warn("Unknown or unsupported MCP method: {}", method);
                    ObjectNode errResponse = objectMapper.createObjectNode();
                    errResponse.put("jsonrpc", "2.0");
                    if (idNode != null) errResponse.set("id", idNode);
                    ObjectNode error = errResponse.putObject("error");
                    error.put("code", -32601);
                    error.put("message", "Method not found: " + method);
                    yield Mono.just(errResponse);
                }
            };

            return resultMono.map(node -> {
                try {
                    return objectMapper.writeValueAsString(node);
                } catch (Exception e) {
                    return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Serialization error\"}}";
                }
            });
        } catch (Exception ex) {
            log.error("Failed to parse incoming MCP request: {}", ex.getMessage());
            return Mono.just("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}");
        }
    }

    private Mono<JsonNode> handleInitialize(JsonNode idNode) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (idNode != null) response.set("id", idNode);

        ObjectNode result = response.putObject("result");
        result.put("protocolVersion", "2024-11-05");

        ObjectNode capabilities = result.putObject("capabilities");
        ObjectNode tools = capabilities.putObject("tools");
        tools.put("listChanged", true);

        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "omnibus-gateway-mcp");
        serverInfo.put("version", "1.0.0");

        return Mono.just(response);
    }

    private Mono<JsonNode> handlePing(JsonNode idNode) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (idNode != null) response.set("id", idNode);
        response.putObject("result");
        return Mono.just(response);
    }

    /**
     * Aggregates tools from all 3 microservices in parallel.
     */
    private Mono<JsonNode> handleToolsList(JsonNode request) {
        Mono<JsonNode> adminTools = fetchToolsFromService(adminMcpUrl, request);
        Mono<JsonNode> bookingTools = fetchToolsFromService(bookingMcpUrl, request);
        Mono<JsonNode> inventoryTools = fetchToolsFromService(inventoryMcpUrl, request);

        return Mono.zip(adminTools, bookingTools, inventoryTools)
                .map(tuple -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("jsonrpc", "2.0");
                    if (request.has("id")) response.set("id", request.get("id"));

                    ObjectNode result = response.putObject("result");
                    ArrayNode mergedTools = result.putArray("tools");

                    appendTools(mergedTools, tuple.getT1());
                    appendTools(mergedTools, tuple.getT2());
                    appendTools(mergedTools, tuple.getT3());

                    log.info("Aggregated {} total tools across all microservices", mergedTools.size());
                    return response;
                });
    }

    private Mono<JsonNode> fetchToolsFromService(String url, JsonNode originalRequest) {
        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(originalRequest)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(4))
                .onErrorResume(e -> {
                    log.warn("Failed to fetch tools from {}: {}", url, e.getMessage());
                    return Mono.empty();
                });
    }

    private void appendTools(ArrayNode target, JsonNode source) {
        if (source != null && source.has("result") && source.get("result").has("tools")) {
            JsonNode tools = source.get("result").get("tools");
            if (tools.isArray()) {
                for (JsonNode tool : tools) {
                    target.add(tool);
                }
            }
        }
    }

    /**
     * Dispatches tool execution to the appropriate microservice based on tool name.
     */
    private Mono<JsonNode> handleToolsCall(JsonNode request) {
        String extractedToolName = "";
        if (request.has("params") && request.get("params").has("name")) {
            extractedToolName = request.get("params").get("name").asText();
        }
        final String toolName = extractedToolName;

        String targetUrl = resolveBackendForTool(toolName);
        log.info("Routing MCP tool call '{}' to {}", toolName, targetUrl);

        return webClient.post()
                .uri(targetUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    log.error("Error executing tool '{}' on {}: {}", toolName, targetUrl, e.getMessage());
                    ObjectNode errResponse = objectMapper.createObjectNode();
                    errResponse.put("jsonrpc", "2.0");
                    if (request.has("id")) errResponse.set("id", request.get("id"));
                    ObjectNode error = errResponse.putObject("error");
                    error.put("code", -32603);
                    error.put("message", "Internal tool execution failure: " + e.getMessage());
                    return Mono.just(errResponse);
                });
    }

    private String resolveBackendForTool(String toolName) {
        if (ADMIN_TOOLS.contains(toolName)) {
            return adminMcpUrl;
        } else if (BOOKING_TOOLS.contains(toolName)) {
            return bookingMcpUrl;
        } else if (INVENTORY_TOOLS.contains(toolName)) {
            return inventoryMcpUrl;
        }
        return adminMcpUrl;
    }
}
