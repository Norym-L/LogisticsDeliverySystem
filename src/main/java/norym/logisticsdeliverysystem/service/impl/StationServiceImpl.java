package norym.logisticsdeliverysystem.service.impl;

import norym.logisticsdeliverysystem.dto.StationView;
import norym.logisticsdeliverysystem.mapper.StationMapper;
import norym.logisticsdeliverysystem.service.StationService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StationServiceImpl implements StationService
{
    private final StationMapper stationMapper;

    public StationServiceImpl(StationMapper stationMapper)
    {
        this.stationMapper = stationMapper;
    }

    @Override
    public List<StationView> list()
    {
        return stationMapper.findAll().stream()
                .map(s -> new StationView(s.getId(), s.getCode(), s.getName(), s.getAddress()))
                .toList();
    }
}
