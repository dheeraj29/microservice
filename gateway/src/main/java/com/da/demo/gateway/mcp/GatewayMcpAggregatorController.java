package com.da.demo.gateway.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Spring AI / Model Context Protocol (MCP) Unified Gateway Aggregator.
 * Aggregates all Fleet, Booking, and Inventory microservice capabilities as native MCP tools.
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class GatewayMcpAggregatorController {

    private static final Logger log = LoggerFactory.getLogger(GatewayMcpAggregatorController.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${mcp.backend.admin.base-url:http://localhost:8081}")
    private String adminBaseUrl;

    @Value("${mcp.backend.booking.base-url:http://localhost:8083}")
    private String bookingBaseUrl;

    @Value("${mcp.backend.inventory.base-url:http://localhost:8084/inventoryservice/v1}")
    private String inventoryBaseUrl;

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
     */
    @PostMapping(value = {"/mcp", "/mcp/", "/mcp/sse", "/mcp/message"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> handleMessage(@RequestBody String requestBody, @RequestParam(value = "sessionId", required = false) String sessionId) {
        try {
            JsonNode request = objectMapper.readTree(requestBody);
            String method = request.has("method") ? request.get("method").asText() : "";
            JsonNode idNode = request.get("id");

            log.info("Received MCP JSON-RPC method: {} (id: {})", method, idNode);

            // JSON-RPC notifications have no ID and MUST return 202/204 without a response body
            if (idNode == null || method.startsWith("notifications/")) {
                return Mono.just(ResponseEntity.accepted().body(""));
            }

            Mono<JsonNode> resultMono = switch (method) {
                case "initialize" -> handleInitialize(idNode);
                case "ping" -> handlePing(idNode);
                case "tools/list" -> handleToolsList(idNode);
                case "tools/call" -> handleToolsCall(request);
                default -> {
                    log.warn("Unknown or unsupported MCP method: {}", method);
                    ObjectNode errResponse = objectMapper.createObjectNode();
                    errResponse.put("jsonrpc", "2.0");
                    errResponse.set("id", idNode);
                    ObjectNode error = errResponse.putObject("error");
                    error.put("code", -32601);
                    error.put("message", "Method not found: " + method);
                    yield Mono.just(errResponse);
                }
            };

            return resultMono.map(node -> {
                try {
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(objectMapper.writeValueAsString(node));
                } catch (Exception e) {
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Serialization error\"}}");
                }
            });
        } catch (Exception ex) {
            log.error("Failed to parse incoming MCP request: {}", ex.getMessage());
            return Mono.just(ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}"));
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
     * Exposes the complete unified catalog of tools across Admin, Booking, and Inventory microservices.
     */
    private Mono<JsonNode> handleToolsList(JsonNode idNode) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (idNode != null) response.set("id", idNode);

        ObjectNode result = response.putObject("result");
        ArrayNode tools = result.putArray("tools");

        // 1. Admin Service Tools
        addTool(tools, "getAllBuses", "Retrieve all scheduled buses and routes across the transport fleet", Map.of());
        addTool(tools, "getDashboardStats", "Get executive dashboard statistics including total fleet count, capacity, and system health", Map.of());
        addTool(tools, "findBusDetailsByNumber", "Find bus details, route, and price by unique numeric bus number", Map.of("busNumber", "integer"));
        addTool(tools, "findBusDetailsBySourceAndDestination", "Find all bus numbers operating between departure and destination cities", Map.of("source", "string", "destination", "string"));
        addTool(tools, "addBusDetails", "Register and schedule a new bus in the transport fleet", Map.of("busNumber", "integer", "source", "string", "destination", "string", "price", "string", "totalSeats", "string"));
        addTool(tools, "deleteBusDetails", "Remove a bus from service by its numeric bus number", Map.of("busNumber", "integer"));

        // 2. Inventory Service Tools
        addTool(tools, "getSeatAvailability", "Check seat vacancy and return available bus number operating between cities", Map.of("source", "string", "destination", "string", "requiredSeats", "integer"));
        addTool(tools, "getBusSeatLayout", "Retrieve live interactive seat map layout and availability grid for a bus", Map.of("busNumber", "integer"));
        addTool(tools, "saveBusInventory", "Initialize and register seat capacity inventory for a new bus", Map.of("busNumber", "integer", "totalSeats", "integer"));

        // 3. Booking Service Tools
        addTool(tools, "bookBusTicket", "Book passenger bus seats and confirm travel reservation", Map.of("source", "string", "destination", "string", "requiredSeats", "integer", "bookingUser", "string", "busNumber", "integer"));
        addTool(tools, "getMyBookings", "Retrieve list of all active and past bookings for a passenger username", Map.of("username", "string"));
        addTool(tools, "getBookingById", "Get detailed booking information and ticket status by numeric booking ID", Map.of("bookingId", "integer"));
        addTool(tools, "cancelBooking", "Cancel an existing passenger booking and release reserved seats", Map.of("bookingId", "integer", "username", "string"));

        log.info("Returning {} unified MCP tools to client", tools.size());
        return Mono.just(response);
    }

    private void addTool(ArrayNode tools, String name, String description, Map<String, String> properties) {
        ObjectNode tool = tools.addObject();
        tool.put("name", name);
        tool.put("description", description);

        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            ObjectNode prop = props.putObject(entry.getKey());
            prop.put("type", entry.getValue());
            prop.put("description", entry.getKey() + " parameter");
        }
    }

    /**
     * Executes the requested tool on the appropriate microservice via reactive WebClient.
     */
    private Mono<JsonNode> handleToolsCall(JsonNode request) {
        JsonNode idNode = request.get("id");
        String toolName = "";
        JsonNode args = objectMapper.createObjectNode();

        if (request.has("params")) {
            JsonNode params = request.get("params");
            if (params.has("name")) toolName = params.get("name").asText();
            if (params.has("arguments")) args = params.get("arguments");
        }

        final String finalToolName = toolName;
        log.info("Executing MCP tool: {} with args: {}", finalToolName, args);

        return executeToolRequest(finalToolName, args)
                .map(content -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("jsonrpc", "2.0");
                    if (idNode != null) response.set("id", idNode);

                    ObjectNode result = response.putObject("result");
                    ArrayNode contentArr = result.putArray("content");
                    ObjectNode textNode = contentArr.addObject();
                    textNode.put("type", "text");
                    textNode.put("text", content);
                    return (JsonNode) response;
                })
                .onErrorResume(e -> {
                    log.error("Tool execution failed for {}: {}", finalToolName, e.getMessage());
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("jsonrpc", "2.0");
                    if (idNode != null) response.set("id", idNode);

                    ObjectNode result = response.putObject("result");
                    result.put("isError", true);
                    ArrayNode contentArr = result.putArray("content");
                    ObjectNode textNode = contentArr.addObject();
                    textNode.put("type", "text");
                    textNode.put("text", "Error executing " + finalToolName + ": " + e.getMessage());
                    return Mono.just(response);
                });
    }

    private Mono<String> executeToolRequest(String toolName, JsonNode args) {
        return switch (toolName) {
            // Admin Tools
            case "getAllBuses" ->
                    webClient.get().uri(adminBaseUrl + "/allBuses").retrieve().bodyToMono(String.class);
            case "getDashboardStats" ->
                    webClient.get().uri(adminBaseUrl + "/dashboardStats").retrieve().bodyToMono(String.class);
            case "findBusDetailsByNumber" -> {
                int busNo = args.has("busNumber") ? args.get("busNumber").asInt() : 101;
                yield webClient.get().uri(adminBaseUrl + "/findBusDetailsByNumber?busNumber=" + busNo).retrieve().bodyToMono(String.class);
            }
            case "findBusDetailsBySourceAndDestination" -> {
                String src = args.has("source") ? args.get("source").asText() : "";
                String dst = args.has("destination") ? args.get("destination").asText() : "";
                yield webClient.get().uri(adminBaseUrl + "/findBusDetailsBySourceAndDestination?source=" + src + "&destination=" + dst).retrieve().bodyToMono(String.class);
            }
            case "addBusDetails" ->
                    webClient.post().uri(adminBaseUrl + "/addBusDetails").contentType(MediaType.APPLICATION_JSON).bodyValue(args).retrieve().bodyToMono(String.class);
            case "deleteBusDetails" -> {
                int busNo = args.has("busNumber") ? args.get("busNumber").asInt() : 0;
                yield webClient.delete().uri(adminBaseUrl + "/deleteBusDetails?busNumber=" + busNo).retrieve().bodyToMono(String.class);
            }

            // Inventory Tools
            case "getSeatAvailability" -> {
                String src = args.has("source") ? args.get("source").asText() : "";
                String dst = args.has("destination") ? args.get("destination").asText() : "";
                int req = args.has("requiredSeats") ? args.get("requiredSeats").asInt() : 1;
                yield webClient.get().uri(inventoryBaseUrl + "/getSeatAvailability?source=" + src + "&destination=" + dst + "&requiredSeats=" + req).retrieve().bodyToMono(String.class);
            }
            case "getBusSeatLayout" -> {
                int busNo = args.has("busNumber") ? args.get("busNumber").asInt() : 101;
                yield webClient.get().uri(inventoryBaseUrl + "/busSeatLayout/" + busNo).retrieve().bodyToMono(String.class);
            }
            case "saveBusInventory" -> {
                int busNo = args.has("busNumber") ? args.get("busNumber").asInt() : 0;
                int seats = args.has("totalSeats") ? args.get("totalSeats").asInt() : 40;
                yield webClient.get().uri(inventoryBaseUrl + "/addBus?busNumber=" + busNo + "&totalSeats=" + seats).retrieve().bodyToMono(String.class);
            }

            // Booking Tools
            case "bookBusTicket" -> {
                String src = args.has("source") ? args.get("source").asText() : "New York";
                String dst = args.has("destination") ? args.get("destination").asText() : "Boston";
                int req = args.has("requiredSeats") ? args.get("requiredSeats").asInt() : 1;
                String user = args.has("bookingUser") ? args.get("bookingUser").asText() : "john_doe";
                int busNo = args.has("busNumber") ? args.get("busNumber").asInt() : 101;
                yield webClient.post().uri(bookingBaseUrl + "/bookSeat?source=" + src + "&destination=" + dst + "&requiredSeats=" + req + "&bookingUser=" + user + "&busNumber=" + busNo).retrieve().bodyToMono(String.class);
            }
            case "getMyBookings" -> {
                String user = args.has("username") ? args.get("username").asText() : "john_doe";
                yield webClient.get().uri(bookingBaseUrl + "/myBookings?username=" + user).retrieve().bodyToMono(String.class);
            }
            case "getBookingById" -> {
                int id = args.has("bookingId") ? args.get("bookingId").asInt() : 1;
                yield webClient.get().uri(bookingBaseUrl + "/booking/" + id).retrieve().bodyToMono(String.class);
            }
            case "cancelBooking" -> {
                int id = args.has("bookingId") ? args.get("bookingId").asInt() : 1;
                String user = args.has("username") ? args.get("username").asText() : "john_doe";
                yield webClient.post().uri(bookingBaseUrl + "/cancelBooking?bookingId=" + id + "&username=" + user).retrieve().bodyToMono(String.class);
            }

            default -> Mono.just("Tool not found: " + toolName);
        };
    }
}
