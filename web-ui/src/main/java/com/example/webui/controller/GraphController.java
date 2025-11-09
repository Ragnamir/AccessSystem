package com.example.webui.controller;

import com.example.webui.repository.CheckpointRepository;
import com.example.webui.repository.CheckpointView;
import com.example.webui.repository.ZoneRepository;
import com.example.webui.repository.ZoneView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/graph")
public class GraphController {

    private static final String OUT_ZONE_CODE = "OUT";

    private final ZoneRepository zoneRepository;
    private final CheckpointRepository checkpointRepository;

    public GraphController(ZoneRepository zoneRepository, CheckpointRepository checkpointRepository) {
        this.zoneRepository = zoneRepository;
        this.checkpointRepository = checkpointRepository;
    }

    @GetMapping
    public GraphResponse graph() {
        Map<String, ZoneNode> zonesByCode = new LinkedHashMap<>();
        List<ZoneView> zoneViews = zoneRepository.findAll();
        for (ZoneView zoneView : zoneViews) {
            zonesByCode.put(zoneView.code(), new ZoneNode(
                zoneView.id(),
                zoneView.code(),
                zoneView.createdAt()
            ));
        }

        List<CheckpointLink> checkpointLinks = new ArrayList<>();
        List<CheckpointView> checkpointViews = checkpointRepository.findAll();
        for (CheckpointView checkpoint : checkpointViews) {
            String fromCode = checkpoint.fromZoneCode() != null ? checkpoint.fromZoneCode() : OUT_ZONE_CODE;
            String toCode = checkpoint.toZoneCode() != null ? checkpoint.toZoneCode() : OUT_ZONE_CODE;
            zonesByCode.computeIfAbsent(fromCode, code -> new ZoneNode(null, code, null));
            zonesByCode.computeIfAbsent(toCode, code -> new ZoneNode(null, code, null));
            checkpointLinks.add(new CheckpointLink(
                checkpoint.id(),
                checkpoint.code(),
                fromCode,
                toCode
            ));
        }

        zonesByCode.computeIfAbsent(OUT_ZONE_CODE, code -> new ZoneNode(null, code, null));

        return new GraphResponse(
            new ArrayList<>(zonesByCode.values()),
            checkpointLinks
        );
    }

    public record GraphResponse(
        List<ZoneNode> zones,
        List<CheckpointLink> checkpoints
    ) {}

    public record ZoneNode(
        UUID id,
        String code,
        Instant createdAt
    ) {}

    public record CheckpointLink(
        UUID id,
        String code,
        String fromZoneCode,
        String toZoneCode
    ) {}
}


