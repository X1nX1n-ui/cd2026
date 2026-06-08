package com.cd.mapper;

import com.cd.entity.ProbeScheduleConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProbeScheduleConfigMapper {

    ProbeScheduleConfig selectCurrent();

    int insert(ProbeScheduleConfig config);

    int update(ProbeScheduleConfig config);
}
