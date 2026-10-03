package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.MqttDtos.ConnectionRequest;
import com.dayan.platform.dto.MqttDtos.StatusRequest;
import com.dayan.platform.dto.MqttDtos.SubscriptionRequest;
import com.dayan.platform.service.MqttManagementService;
import com.dayan.platform.vo.MqttViews.ConnectionView;
import com.dayan.platform.vo.MqttViews.Overview;
import com.dayan.platform.vo.MqttViews.SubscriptionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${app.api.base-path}/basic/mqtt")
@PreAuthorize("hasAuthority('basic:mqtt:view')")
public class MqttManagementController {

    private final MqttManagementService mqttService;

    public MqttManagementController(MqttManagementService mqttService) {
        this.mqttService = mqttService;
    }

    @GetMapping("/overview")
    public Overview overview() {
        return mqttService.overview();
    }

    @GetMapping("/connections")
    public List<ConnectionView> connections() {
        return mqttService.connections();
    }

    @PostMapping("/connections")
    @PreAuthorize("hasAuthority('basic:mqtt:manage')")
    @Audited(module = "MQTT", action = "CREATE_CONNECTION",
            targetType = "MQTT_CONNECTION", targetId = "#result.id()")
    public ConnectionView createConnection(
            @Valid @RequestBody ConnectionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return mqttService.createConnection(request, userId(jwt));
    }

    @PutMapping("/connections/{id}")
    @PreAuthorize("hasAuthority('basic:mqtt:manage')")
    @Audited(module = "MQTT", action = "UPDATE_CONNECTION",
            targetType = "MQTT_CONNECTION", targetId = "#id")
    public ConnectionView updateConnection(
            @PathVariable long id,
            @Valid @RequestBody ConnectionRequest request
    ) {
        return mqttService.updateConnection(id, request);
    }

    @PatchMapping("/connections/{id}/status")
    @PreAuthorize("hasAuthority('basic:mqtt:manage')")
    @Audited(module = "MQTT", action = "CHANGE_CONNECTION_STATUS",
            targetType = "MQTT_CONNECTION", targetId = "#id")
    public ConnectionView changeConnectionStatus(
            @PathVariable long id,
            @Valid @RequestBody StatusRequest request
    ) {
        return mqttService.changeConnectionStatus(id, request.enabled());
    }

    @PostMapping("/connections/{id}/test")
    @PreAuthorize("hasAuthority('basic:mqtt:test')")
    @Audited(module = "MQTT", action = "TEST_CONNECTION",
            targetType = "MQTT_CONNECTION", targetId = "#id")
    public ConnectionView testConnection(@PathVariable long id) {
        return mqttService.testConnection(id);
    }

    @DeleteMapping("/connections/{id}")
    @PreAuthorize("hasAuthority('basic:mqtt:manage')")
    @Audited(module = "MQTT", action = "DELETE_CONNECTION",
            targetType = "MQTT_CONNECTION", targetId = "#id")
    public void deleteConnection(@PathVariable long id) {
        mqttService.deleteConnection(id);
    }

    @GetMapping("/subscriptions")
    public List<SubscriptionView> subscriptions(
            @RequestParam(required = false) @Positive Long connectionId
    ) {
        return mqttService.subscriptions(connectionId);
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("hasAuthority('basic:mqtt:manage')")
    @Audited(module = "MQTT", action = "CREATE_SUBSCRIPTION",
            targetType = "MQTT_SUBSCRIPTION", targetId = "#result.id()")
    public SubscriptionView createSubscription(
            @Valid @RequestBody SubscriptionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return mqttService.createSubscription(request, userId(jwt));
    }

    @PutMapping("/subscriptions/{id}")
    @PreAuthorize("hasAuthority('basic:mqtt:manage')")
    @Audited(module = "MQTT", action = "UPDATE_SUBSCRIPTION",
            targetType = "MQTT_SUBSCRIPTION", targetId = "#id")
    public SubscriptionView updateSubscription(
            @PathVariable long id,
            @Valid @RequestBody SubscriptionRequest request
    ) {
        return mqttService.updateSubscription(id, request);
    }

    @PatchMapping("/subscriptions/{id}/status")
    @PreAuthorize("hasAuthority('basic:mqtt:manage')")
    @Audited(module = "MQTT", action = "CHANGE_SUBSCRIPTION_STATUS",
            targetType = "MQTT_SUBSCRIPTION", targetId = "#id")
    public SubscriptionView changeSubscriptionStatus(
            @PathVariable long id,
            @Valid @RequestBody StatusRequest request
    ) {
        return mqttService.changeSubscriptionStatus(id, request.enabled());
    }

    @DeleteMapping("/subscriptions/{id}")
    @PreAuthorize("hasAuthority('basic:mqtt:manage')")
    @Audited(module = "MQTT", action = "DELETE_SUBSCRIPTION",
            targetType = "MQTT_SUBSCRIPTION", targetId = "#id")
    public void deleteSubscription(@PathVariable long id) {
        mqttService.deleteSubscription(id);
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }
}
