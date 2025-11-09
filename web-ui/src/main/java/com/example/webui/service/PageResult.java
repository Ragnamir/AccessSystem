package com.example.webui.service;

import java.util.List;

public record PageResult<T>(
    List<T> items,
    long total,
    int page,
    int size
) {
    public int getTotalPages() {
        return size > 0 ? (int) Math.ceil((double) total / size) : 0;
    }
    
    public boolean hasNext() {
        return (page + 1) * size < total;
    }
    
    public boolean hasPrevious() {
        return page > 0;
    }
}

