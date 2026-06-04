package com.cd.mapper;

import com.cd.entity.Host;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface HostMapper {

    long count(@Param("keyword") String keyword, @Param("status") String status);

    List<Host> selectPage(@Param("keyword") String keyword,
                          @Param("status") String status,
                          @Param("offset") int offset,
                          @Param("pageSize") int pageSize);

    Host selectById(@Param("id") Long id);

    Host selectByMacAddress(@Param("macAddress") String macAddress);

    int refreshStatusesByUpdatedAt();

    int insert(Host host);

    int update(Host host);

    int upsertByMacAddress(Host host);

    int updateStatusAndUpdatedAtByMacAddress(@Param("macAddress") String macAddress,
                                             @Param("status") String status);

    int deleteById(@Param("id") Long id);
}
