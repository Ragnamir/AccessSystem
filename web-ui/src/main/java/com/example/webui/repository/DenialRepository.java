package com.example.webui.repository;

import java.util.List;

public interface DenialRepository {
    List<DenialView> findAll(int offset, int limit);
    long count();
}

