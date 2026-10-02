package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.AiAgentDtos.AgentRequest;
import com.dayan.platform.model.AiAgent;
import com.dayan.platform.model.AiModel;
import com.dayan.platform.repository.mapper.AiAgentMapper;
import com.dayan.platform.repository.mapper.AiModelMapper;
import com.dayan.platform.service.AiAgentService;
import com.dayan.platform.vo.AiAgentViews.AgentDebugResult;
import com.dayan.platform.vo.AiAgentViews.AgentView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiAgentServiceImpl implements AiAgentService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int ERROR_RESPONSE_LIMIT = 500;

    private final AiAgentMapper agentMapper;
    private final AiModelMapper modelMapper;
    private final ModelCredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiAgentServiceImpl(
            AiAgentMapper agentMapper,
            AiModelMapper modelMapper,
            ModelCredentialCipher credentialCipher,
            ObjectMapper objectMapper
    ) {
        this.agentMapper = agentMapper;
        this.modelMapper = modelMapper;
        this.credentialCipher = credentialCipher;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentView> list() {
        return agentMapper.selectAllViews();
    }

    @Override
    @Transactional(readOnly = true)
    public AgentView get(long id) {
        return requireView(id);
    }

    @Override
    @Transactional
    public AgentView create(AgentRequest request) {
        requireUsableModel(request.modelId());
        AiAgent agent = new AiAgent();
        apply(agent, request);
        try {
            agentMapper.insert(agent);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "An Agent with the same name already exists");
        }
        return requireView(agent.getId());
    }

    @Override
    @Transactional
    public AgentView update(long id, AgentRequest request) {
        AiAgent agent = requireAgent(id);
        requireUsableModel(request.modelId());
        apply(agent, request);
        agent.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            agentMapper.updateById(agent);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "An Agent with the same name already exists");
        }
        return requireView(id);
    }

    @Override
    @Transactional
    public AgentView changeStatus(long id, boolean enabled) {
        AiAgent agent = requireAgent(id);
        if (enabled) {
            requireUsableModel(agent.getModelId());
        }
        agent.setEnabled(enabled);
        agent.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        agentMapper.updateById(agent);
        return requireView(id);
    }

    @Override
    @Transactional
    public void delete(long id) {
        requireAgent(id);
        agentMapper.deleteById(id);
    }

    @Override
    public AgentDebugResult debug(long id, String message) {
        AiAgent agent = requireAgent(id);
        if (!Boolean.TRUE.equals(agent.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Disabled Agents cannot be debugged");
        }
        AiModel model = requireUsableModel(agent.getModelId());
        URI endpoint = publicEndpoint(model.getModelUrl());
        long startedAt = System.nanoTime();
        OffsetDateTime completedAt;
        try {
            HttpResponse<String> response = httpClient.send(
                    debugRequest(agent, model, endpoint, message.trim()),
                    HttpResponse.BodyHandlers.ofString()
            );
            completedAt = OffsetDateTime.now(ZoneOffset.UTC);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "Model returned HTTP " + response.statusCode() + responseExcerpt(response.body())
                );
            }
            return new AgentDebugResult(
                    responseContent(response.body()),
                    model.getName(),
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
                    completedAt
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CONFLICT, "Agent debug request was interrupted");
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent debug request failed: " + safeMessage(exception));
        }
    }

    private void apply(AiAgent agent, AgentRequest request) {
        agent.setName(request.name().trim());
        agent.setDescription(trimToNull(request.description()));
        agent.setSystemPrompt(request.systemPrompt().trim());
        agent.setModelId(request.modelId());
        agent.setTemperature(request.temperature());
        agent.setMaxTokens(request.maxTokens());
        agent.setEnabled(request.enabled());
    }

    private HttpRequest debugRequest(AiAgent agent, AiModel model, URI endpoint, String message)
            throws JsonProcessingException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.getName());
        body.put("messages", List.of(
                Map.of("role", "system", "content", agent.getSystemPrompt()),
                Map.of("role", "user", "content", message)
        ));
        body.put("temperature", agent.getTemperature());
        body.put("max_tokens", agent.getMaxTokens());

        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        String accessKey = credentialCipher.decrypt(model.getAccessKeyCiphertext());
        String secretKey = credentialCipher.decrypt(model.getSecretKeyCiphertext());
        String bearerToken = StringUtils.hasText(secretKey) ? secretKey : accessKey;
        if (StringUtils.hasText(bearerToken)) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (StringUtils.hasText(accessKey)) {
            builder.header("X-Access-Key", accessKey);
        }
        if (StringUtils.hasText(secretKey)) {
            builder.header("X-Secret-Key", secretKey);
        }
        return builder.build();
    }

    private String responseContent(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual() && StringUtils.hasText(content.asText())) {
                return content.asText();
            }
            JsonNode outputText = root.path("output_text");
            if (outputText.isTextual() && StringUtils.hasText(outputText.asText())) {
                return outputText.asText();
            }
            throw new BusinessException(ErrorCode.CONFLICT, "Model response does not contain assistant content");
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Model returned an invalid JSON response");
        }
    }

    private URI publicEndpoint(String value) {
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getUserInfo() != null) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Model URL is invalid");
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isPrivateAddress(address)) {
                    throw new BusinessException(
                            ErrorCode.INVALID_ARGUMENT,
                            "Model URL must not resolve to a private or local address"
                    );
                }
            }
            return uri;
        } catch (URISyntaxException | UnknownHostException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Model URL could not be resolved");
        }
    }

    private boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        return address instanceof Inet6Address && (bytes[0] & 0xfe) == 0xfc;
    }

    private AiAgent requireAgent(long id) {
        AiAgent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Agent not found");
        }
        return agent;
    }

    private AgentView requireView(long id) {
        AgentView view = agentMapper.selectViewById(id);
        if (view == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Agent not found");
        }
        return view;
    }

    private AiModel requireUsableModel(long id) {
        AiModel model = modelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Model not found");
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Selected model is disabled");
        }
        if (!List.of("CHAT", "MULTIMODAL").contains(model.getModelType())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Agent requires a chat-capable model");
        }
        return model;
    }

    private String responseExcerpt(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return ": " + truncate(compact, ERROR_RESPONSE_LIMIT);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return truncate(StringUtils.hasText(message) ? message : exception.getClass().getSimpleName(), 300);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
