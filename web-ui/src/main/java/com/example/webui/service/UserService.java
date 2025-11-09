package com.example.webui.service;

import com.example.webui.repository.UserRepository;
import com.example.webui.repository.UserView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public PageResult<UserView> findAllWithState(int page, int size) {
        int offset = page * size;
        List<UserView> items = userRepository.findAllWithState(offset, size);
        long total = userRepository.count();
        return new PageResult<>(items, total, page, size);
    }

    public List<UserView> findAll() {
        return userRepository.findAllWithState();
    }
}

