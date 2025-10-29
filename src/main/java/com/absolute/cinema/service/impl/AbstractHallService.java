package com.absolute.cinema.service.impl;

import com.absolute.cinema.common.exception.custom.BadRequestException;
import com.absolute.cinema.dto.hall.HallDTO;
import com.absolute.cinema.entity.Hall;
import com.absolute.cinema.mapper.HallMapper;
import com.absolute.cinema.repository.HallRepository;
import com.absolute.cinema.service.HallPlanService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

abstract class AbstractHallService {
    
    protected final HallRepository hallRepository;
    protected final HallMapper hallMapper;
    protected final HallPlanService hallPlanService;

    protected AbstractHallService(HallRepository hallRepository, HallMapper hallMapper, HallPlanService hallPlanService) {
        this.hallRepository = hallRepository;
        this.hallMapper = hallMapper;
        this.hallPlanService = hallPlanService;
    }

    protected abstract HallDTO getById(UUID id);

    protected void scheduleHallMaintenance(UUID hallId, LocalDateTime datetime) {
        throw new UnsupportedOperationException("Maintenance scheduling not implemented");
    }

    protected boolean validateHallCapacity(UUID hallId, int requiredSeats) {
        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new BadRequestException("Hall not found"));
        return hall.getNumber() >= requiredSeats;
    }

    protected void notifyHallStatusChange(UUID hallId, String status) {
        throw new UnsupportedOperationException("Hall status notifications not implemented");
    }

    protected List<Hall> filterHallsByCapacity(int minSeats, int maxSeats) {
        Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);
        return hallRepository.findAll(pageable).getContent().stream()
                .filter(hall -> hall.getNumber() >= minSeats && hall.getNumber() <= maxSeats)
                .toList();
    }

    protected List<Hall> getAllHallsWithMinCapacity(int minSeats) {
        return List.of();
    }

    protected boolean isHallAvailable(UUID hallId, LocalDateTime from, LocalDateTime to) {
        return false;
    }
}
