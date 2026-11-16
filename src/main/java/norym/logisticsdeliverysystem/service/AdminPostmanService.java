package norym.logisticsdeliverysystem.service;

import norym.logisticsdeliverysystem.dto.PostmanInfo;
import norym.logisticsdeliverysystem.dto.PostmanTask;

import java.util.List;

/**
 * 管理端查看配送员档案与手里的单。
 */
public interface AdminPostmanService
{
    /** 配送员列表；stationId 为空时查全部 */
    List<PostmanInfo> list(Long stationId);

    /**
     * 某配送员的任务。
     *
     * @param restrictStationId 节点管理员传自己的站，用于限制只能看本站的人；路径管理员传 null
     */
    List<PostmanTask> tasks(Long postmanId, String type, Long restrictStationId);
}
