package com.dayan.platform.service;

import com.dayan.platform.dto.MqttDtos.ConnectionRequest;
import com.dayan.platform.dto.MqttDtos.SubscriptionRequest;
import com.dayan.platform.vo.MqttViews.ConnectionView;
import com.dayan.platform.vo.MqttViews.Overview;
import com.dayan.platform.vo.MqttViews.SubscriptionView;
import java.util.List;

public interface MqttManagementService {

    Overview overview();

    List<ConnectionView> connections();

    ConnectionView createConnection(ConnectionRequest request, long userId);

    ConnectionView updateConnection(long id, ConnectionRequest request);

    ConnectionView changeConnectionStatus(long id, boolean enabled);

    ConnectionView testConnection(long id);

    void deleteConnection(long id);

    List<SubscriptionView> subscriptions(Long connectionId);

    SubscriptionView createSubscription(SubscriptionRequest request, long userId);

    SubscriptionView updateSubscription(long id, SubscriptionRequest request);

    SubscriptionView changeSubscriptionStatus(long id, boolean enabled);

    void deleteSubscription(long id);
}
