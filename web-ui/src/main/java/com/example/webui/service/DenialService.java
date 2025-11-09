package com.example.webui.service;

import com.example.webui.repository.DenialRepository;
import com.example.webui.repository.DenialView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DenialService {
    
    private final DenialRepository denialRepository;
    
    public DenialService(DenialRepository denialRepository) {
        this.denialRepository = denialRepository;
    }
    
    public PageResult<DenialView> findAll(int page, int size) {
        int offset = page * size;
        List<DenialView> items = denialRepository.findAll(offset, size);
        long total = denialRepository.count();
        return new PageResult<>(items, total, page, size);
    }
}

