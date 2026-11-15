package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.StationView;

import java.util.List;

public interface StationService
{
    /** 下单页选起止站用，只返回启用中的驿站 */
    List<StationView> list();
}
