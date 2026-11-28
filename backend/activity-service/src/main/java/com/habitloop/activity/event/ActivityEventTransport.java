package com.habitloop.activity.event;

public interface ActivityEventTransport {
    void publish(String key, String envelope);
}
