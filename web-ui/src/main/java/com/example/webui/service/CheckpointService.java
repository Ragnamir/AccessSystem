package com.example.webui.service;

import com.example.webui.repository.CheckpointRepository;
import com.example.webui.repository.CheckpointView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CheckpointService {
    
    private final CheckpointRepository checkpointRepository;
    
    public CheckpointService(CheckpointRepository checkpointRepository) {
        this.checkpointRepository = checkpointRepository;
    }
    
    public PageResult<CheckpointView> findAll(int page, int size) {
        int offset = page * size;
        List<CheckpointView> items = checkpointRepository.findAll(offset, size);
        long total = checkpointRepository.count();
        return new PageResult<>(items, total, page, size);
    }
}

