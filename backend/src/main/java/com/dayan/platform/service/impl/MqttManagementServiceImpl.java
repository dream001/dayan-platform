package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.MqttDtos.ConnectionRequest;
import com.dayan.platform.dto.MqttDtos.SubscriptionRequest;
import com.dayan.platform.model.MqttConnection;
import com.dayan.platform.model.MqttSubscription;
import com.dayan.platform.repository.mapper.MqttConnectionMapper;
import com.dayan.platform.repository.mapper.MqttSubscriptionMapper;
import com.dayan.platform.service.MqttManagementService;
import com.dayan.platform.vo.MqttViews.ConnectionView;
import com.dayan.platform.vo.MqttViews.Overview;
import com.dayan.platform.vo.MqttViews.SubscriptionView;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MqttManagementServiceImpl implements MqttManagementService {

    private static final String NEVER = "NEVER";
    private static final String AVAILABLE = "AVAILABLE";
    private static final String UNAVAILABLE = "UNAVAILABLE";
    private static final int MESSAGE_LIMIT = 480;

    private final MqttConnectionMapper connectionMapper;
    private final MqttSubscriptionMapper subscriptionMapper;
    private final StorageCredentialCipher credentialCipher;

    public MqttManagementServiceImpl(
            MqttConnectionMapper connectionMapper,
            MqttSubscriptionMapper subscriptionMapper,
            StorageCredentialCipher credentialCipher
    ) {
        this.connectionMapper = connectionMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.credentialCipher = credentialCipher;
    }

    @Override
    public Overview overview() {
        List<ConnectionView> connections = connectionMapper.selectViews();
        List<SubscriptionView> subscriptions = subscriptionMapper.selectViews(null);
        return new Overview(
                connections.size(),
                connections.stream().filter(ConnectionView::enabled).count(),
                connections.stream().filter(item -> AVAILABLE.equals(item.status())).count(),
                subscriptions.size(),
                subscriptions.stream().filter(SubscriptionView::enabled).count()
        );
    }

    @Override
    public List<ConnectionView> connections() {
        return connectionMapper.selectViews();
    }

    @Override
    @Transactional
    public ConnectionView createConnection(ConnectionRequest request, long userId) {
        validateConnection(request);
        requireUniqueConnection(request.name(), request.clientId(), null);
        MqttConnection connection = new MqttConnection();
        applyConnection(connection, request, false);
        connection.setStatus(NEVER);
        connection.setCreatedBy(userId);
        try {
            connectionMapper.insert(connection);
        } catch (DuplicateKeyException exception) {
            throw conflict("MQTT connection name or client ID already exists");
        }
        return requireConnectionView(connection.getId());
    }

    @Override
    @Transactional
    public ConnectionView updateConnection(long id, ConnectionRequest request) {
        MqttConnection connection = requireConnection(id);
        validateConnection(request);
        requireUniqueConnection(request.name(), request.clientId(), id);
        applyConnection(connection, request, true);
        connection.setStatus(NEVER);
        connection.setLastCheckedAt(null);
        connection.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            connectionMapper.updateById(connection);
        } catch (DuplicateKeyException exception) {
            throw conflict("MQTT connection name or client ID already exists");
        }
        connectionMapper.resetCheckResult(id);
        return requireConnectionView(id);
    }

    @Override
    @Transactional
    public ConnectionView changeConnectionStatus(long id, boolean enabled) {
        MqttConnection connection = requireConnection(id);
        connection.setEnabled(enabled);
        connection.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        connectionMapper.updateById(connection);
        return requireConnectionView(id);
    }

    @Override
    public ConnectionView testConnection(long id) {
        MqttConnection connection = requireConnection(id);
        long started = System.nanoTime();
        OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String status = AVAILABLE;
        String message = "Connection successful";
        try {
            connectAndClose(connection);
        } catch (RuntimeException exception) {
            status = UNAVAILABLE;
            message = safeMessage(exception);
        }
        long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        connectionMapper.updateCheckResult(id, status, message, latencyMs, checkedAt);
        return requireConnectionView(id);
    }

    @Override
    @Transactional
    public void deleteConnection(long id) {
        requireConnection(id);
        connectionMapper.deleteById(id);
    }

    @Override
    public List<SubscriptionView> subscriptions(Long connectionId) {
        if (connectionId != null) {
            requireConnection(connectionId);
        }
        return subscriptionMapper.selectViews(connectionId);
    }

    @Override
    @Transactional
    public SubscriptionView createSubscription(SubscriptionRequest request, long userId) {
        validateSubscription(request);
        requireUniqueSubscription(request.connectionId(), request.topicFilter(), null);
        MqttSubscription subscription = new MqttSubscription();
        applySubscription(subscription, request);
        subscription.setCreatedBy(userId);
        try {
            subscriptionMapper.insert(subscription);
        } catch (DuplicateKeyException exception) {
            throw conflict("MQTT topic subscription already exists");
        }
        return requireSubscriptionView(subscription.getId());
    }

    @Override
    @Transactional
    public SubscriptionView updateSubscription(long id, SubscriptionRequest request) {
        requireSubscription(id);
        validateSubscription(request);
        requireUniqueSubscription(request.connectionId(), request.topicFilter(), id);
        MqttSubscription subscription = new MqttSubscription();
        subscription.setId(id);
        applySubscription(subscription, request);
        subscription.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            subscriptionMapper.updateById(subscription);
        } catch (DuplicateKeyException exception) {
            throw conflict("MQTT topic subscription already exists");
        }
        return requireSubscriptionView(id);
    }

    @Override
    @Transactional
    public SubscriptionView changeSubscriptionStatus(long id, boolean enabled) {
        MqttSubscription subscription = requireSubscription(id);
        subscription.setEnabled(enabled);
        subscription.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        subscriptionMapper.updateById(subscription);
        return requireSubscriptionView(id);
    }

    @Override
    @Transactional
    public void deleteSubscription(long id) {
        requireSubscription(id);
        subscriptionMapper.deleteById(id);
    }

    private void applyConnection(
            MqttConnection connection,
            ConnectionRequest request,
            boolean preserveBlankCredentials
    ) {
        connection.setName(request.name().trim());
        connection.setClientId(request.clientId().trim());
        connection.setBrokerUrl(normalizeBrokerUrl(request.brokerUrl()));
        connection.setTlsEnabled(request.tlsEnabled());
        connection.setCleanSession(request.cleanSession());
        connection.setKeepAliveSeconds(request.keepAliveSeconds());
        connection.setConnectionTimeoutSeconds(request.connectionTimeoutSeconds());
        connection.setEnabled(request.enabled());
        if (!preserveBlankCredentials || StringUtils.hasText(request.username())) {
            connection.setUsernameCiphertext(credentialCipher.encrypt(trimToNull(request.username())));
        }
        if (!preserveBlankCredentials || StringUtils.hasText(request.password())) {
            connection.setPasswordCiphertext(credentialCipher.encrypt(trimToNull(request.password())));
        }
    }

    private void applySubscription(MqttSubscription subscription, SubscriptionRequest request) {
        subscription.setConnectionId(request.connectionId());
        subscription.setTopicFilter(request.topicFilter().trim());
        subscription.setQos(request.qos());
        subscription.setDescription(trimToNull(request.description()));
        subscription.setEnabled(request.enabled());
    }

    private void validateConnection(ConnectionRequest request) {
        String brokerUrl = normalizeBrokerUrl(request.brokerUrl());
        boolean secureScheme = brokerUrl.startsWith("ssl://") || brokerUrl.startsWith("wss://");
        if (secureScheme != request.tlsEnabled()) {
            throw invalid("TLS must match the broker URL scheme");
        }
    }

    private String normalizeBrokerUrl(String value) {
        String brokerUrl = value.trim();
        try {
            URI uri = new URI(brokerUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!List.of("tcp", "ssl", "ws", "wss").contains(scheme)
                    || !StringUtils.hasText(uri.getHost())) {
                throw invalid("MQTT broker URL must use tcp, ssl, ws, or wss");
            }
            return brokerUrl;
        } catch (URISyntaxException exception) {
            throw invalid("MQTT broker URL is invalid");
        }
    }

    private void validateSubscription(SubscriptionRequest request) {
        requireConnection(request.connectionId());
        try {
            MqttTopic.validate(request.topicFilter().trim(), true);
        } catch (IllegalArgumentException exception) {
            throw invalid("MQTT topic filter is invalid");
        }
    }

    private void connectAndClose(MqttConnection connection) {
        String probeClientId = connection.getClientId() + "-probe-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MqttClient client = null;
        try {
            client = new MqttClient(connection.getBrokerUrl(), probeClientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            options.setKeepAliveInterval(connection.getKeepAliveSeconds());
            options.setConnectionTimeout(connection.getConnectionTimeoutSeconds());
            String username = credentialCipher.decrypt(connection.getUsernameCiphertext());
            String password = credentialCipher.decrypt(connection.getPasswordCiphertext());
            if (StringUtils.hasText(username)) {
                options.setUserName(username);
            }
            if (StringUtils.hasText(password)) {
                options.setPassword(password.toCharArray());
            }
            client.connect(options);
            client.disconnect();
        } catch (MqttException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (MqttException ignored) {
                    // Connection test result has already been captured.
                }
            }
        }
    }

    private void requireUniqueConnection(String name, String clientId, Long excludeId) {
        if (connectionMapper.countDuplicate(name.trim(), clientId.trim(), excludeId) > 0) {
            throw conflict("MQTT connection name or client ID already exists");
        }
    }

    private void requireUniqueSubscription(long connectionId, String topicFilter, Long excludeId) {
        if (subscriptionMapper.countDuplicate(connectionId, topicFilter.trim(), excludeId) > 0) {
            throw conflict("MQTT topic subscription already exists");
        }
    }

    private MqttConnection requireConnection(long id) {
        MqttConnection connection = connectionMapper.selectById(id);
        if (connection == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MQTT connection not found");
        }
        return connection;
    }

    private ConnectionView requireConnectionView(long id) {
        ConnectionView view = connectionMapper.selectView(id);
        if (view == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MQTT connection not found");
        }
        return view;
    }

    private MqttSubscription requireSubscription(long id) {
        MqttSubscription subscription = subscriptionMapper.selectById(id);
        if (subscription == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MQTT subscription not found");
        }
        return subscription;
    }

    private SubscriptionView requireSubscriptionView(long id) {
        SubscriptionView view = subscriptionMapper.selectView(id);
        if (view == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MQTT subscription not found");
        }
        return view;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeMessage(RuntimeException exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = StringUtils.hasText(root.getMessage())
                ? root.getMessage()
                : "Connection failed";
        return message.length() > MESSAGE_LIMIT ? message.substring(0, MESSAGE_LIMIT) : message;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}
