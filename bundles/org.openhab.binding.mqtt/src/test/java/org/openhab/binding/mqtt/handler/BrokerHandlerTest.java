/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mqtt.handler;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.binding.mqtt.internal.MqttThingID;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttConnectionState;
import org.openhab.core.io.transport.mqtt.MqttException;
import org.openhab.core.io.transport.mqtt.MqttService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.osgi.service.cm.ConfigurationException;

/**
 * Test cases for {@link BrokerHandler}.
 *
 * @author David Graeff - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class BrokerHandlerTest {
    private ScheduledExecutorService scheduler;

    private @Mock ThingHandlerCallback callback;
    private @Mock Bridge thing;
    private @Mock MqttService service;

    private MqttBrokerConnectionEx connection;

    private BrokerHandler handler;

    @BeforeEach
    public void setUp() throws ConfigurationException, MqttException {
        scheduler = new ScheduledThreadPoolExecutor(1);
        when(thing.getUID()).thenReturn(MqttThingID.getThingUID("10.10.0.10", 80));
        connection = spy(new MqttBrokerConnectionEx("10.10.0.10", 80, false, "BrokerHandlerTest"));
        connection.setTimeoutExecutor(scheduler, 10);
        connection.setConnectionCallback(connection);

        Configuration config = new Configuration();
        when(thing.getConfiguration()).thenReturn(config);

        handler = spy(new BrokerHandlerEx(thing, connection));
        handler.setCallback(callback);
    }

    @AfterEach
    public void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    public void handlerInitWithoutUrl()
            throws InterruptedException, IllegalArgumentException, MqttException, ConfigurationException {
        // Assume it is a real handler and not a mock as defined above
        handler = new BrokerHandler(thing);
        assertThrows(IllegalArgumentException.class, () -> initializeHandlerWaitForTimeout());
    }

    @Test
    public void createBrokerConnection() {
        Configuration config = new Configuration();
        config.put("host", "10.10.0.10");
        config.put("port", 80);
        when(thing.getConfiguration()).thenReturn(config);
        handler.initialize();
        verify(handler).createBrokerConnection();
    }

    @Test
    public void handlerInit()
            throws InterruptedException, IllegalArgumentException, MqttException, ConfigurationException {
        assertThat(initializeHandlerWaitForTimeout(), is(true));

        ArgumentCaptor<ThingStatusInfo> statusInfoCaptor = ArgumentCaptor.forClass(ThingStatusInfo.class);
        verify(callback, atLeast(3)).statusUpdated(eq(thing), statusInfoCaptor.capture());
        assertThat(statusInfoCaptor.getValue().getStatus(), is(ThingStatus.ONLINE));
    }

    /**
     * Utility method for tests that need the handler to be initialized to go on.
     *
     * @return Return true if successful. You usually want to use:
     *         assertThat(initializeHandlerWaitForTimeout(), is(true));
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws MqttException
     * @throws ConfigurationException
     */
    boolean initializeHandlerWaitForTimeout()
            throws InterruptedException, IllegalArgumentException, MqttException, ConfigurationException {
        MqttBrokerConnection c = connection;

        MqttConnectionObserverEx o = new MqttConnectionObserverEx();
        c.addConnectionObserver(o);

        assertThat(connection.connectionState(), is(MqttConnectionState.DISCONNECTED));
        handler.initialize();
        verify(connection, times(2)).addConnectionObserver(any());
        verify(connection, times(1)).start();
        // First we expect a CONNECTING state and then a CONNECTED unique state change
        assertThat(o.counter, is(2));
        // First we expect a CONNECTING state and then a CONNECTED state change
        // (and other CONNECTED after the future completes)
        verify(handler, times(3)).connectionStateChanged(any(), any());
        return true;
    }
}
