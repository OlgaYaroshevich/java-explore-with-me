package ru.practicum.ewm.stats.server.data;

import lombok.experimental.UtilityClass;
import ru.practicum.ewm.stats.dto.EndpointHitDto;

@UtilityClass
public class StatsMapper {
    public EndpointHit fromDto(EndpointHitDto dto) {
        EndpointHit entity = new EndpointHit();
        entity.setApp(dto.getApp());
        entity.setUri(dto.getUri());
        entity.setIp(dto.getIp());
        entity.setTimestamp(dto.getHitTimestamp());
        return entity;
    }
}