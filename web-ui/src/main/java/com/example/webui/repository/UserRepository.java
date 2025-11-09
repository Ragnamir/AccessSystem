package com.example.webui.repository;

import java.util.List;

public interface UserRepository {
    List<UserView> findAllWithState(int offset, int limit);
    List<UserView> findAllWithState();
    long count();
}

