package com.example.webui.repository;

import java.util.List;

public interface EventRepository {
    List<EventView> findRecent(int offset, int limit);
    long count();
}

