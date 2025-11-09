package com.example.webui.repository;

import java.util.List;

public interface CheckpointRepository {
    List<CheckpointView> findAll(int offset, int limit);
    List<CheckpointView> findAll();
    long count();
}

