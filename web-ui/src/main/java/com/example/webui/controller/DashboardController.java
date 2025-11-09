package com.example.webui.controller;

import com.example.webui.service.CheckpointService;
import com.example.webui.service.DenialService;
import com.example.webui.service.EventService;
import com.example.webui.service.PageResult;
import com.example.webui.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {
    
    private final CheckpointService checkpointService;
    private final UserService userService;
    private final EventService eventService;
    private final DenialService denialService;
    
    public DashboardController(
            CheckpointService checkpointService,
            UserService userService,
            EventService eventService,
            DenialService denialService) {
        this.checkpointService = checkpointService;
        this.userService = userService;
        this.eventService = eventService;
        this.denialService = denialService;
    }
    
    @GetMapping("/")
    public String index() {
        return "index";
    }
    
    @GetMapping("/zones")
    public String zones() {
        return "zones";
    }

    @GetMapping("/checkpoints")
    public String checkpoints(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        PageResult<?> result = checkpointService.findAll(page, size);
        model.addAttribute("page", result);
        return "checkpoints";
    }
    
    @GetMapping("/users")
    public String users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        PageResult<?> result = userService.findAllWithState(page, size);
        model.addAttribute("page", result);
        return "users";
    }
    
    @GetMapping("/events")
    public String events(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        PageResult<?> result = eventService.findRecent(page, size);
        model.addAttribute("page", result);
        return "events";
    }
    
    @GetMapping("/denials")
    public String denials(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        PageResult<?> result = denialService.findAll(page, size);
        model.addAttribute("page", result);
        return "denials";
    }
}

