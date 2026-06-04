package com.cd.mapper;

import com.cd.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LoginLogMapper {

    int insert(LoginLog loginLog);

    List<LoginLog> selectPage(@Param("userName") String userName,
                              @Param("offset") int offset,
                              @Param("pageSize") int pageSize);

    long count(@Param("userName") String userName);

    long countToday(@Param("startTime") LocalDateTime startTime,
                    @Param("endTime") LocalDateTime endTime);

    long countTodayFailure(@Param("startTime") LocalDateTime startTime,
                           @Param("endTime") LocalDateTime endTime);
}
