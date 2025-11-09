package com.example.webui.service;

import com.example.webui.repository.EventRepository;
import com.example.webui.repository.EventView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {
    
    private final EventRepository eventRepository;
    
    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }
    
    public PageResult<EventView> findRecent(int page, int size) {
        int offset = page * size;
        List<EventView> items = eventRepository.findRecent(offset, size);
        long total = eventRepository.count();
        return new PageResult<>(items, total, page, size);
    }
}

