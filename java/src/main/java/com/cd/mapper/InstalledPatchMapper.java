package com.cd.mapper;

import com.cd.entity.InstalledPatch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface InstalledPatchMapper {

    int insertBatch(@Param("list") List<InstalledPatch> list);

    int deleteByMacAddress(@Param("macAddress") String macAddress);

    List<InstalledPatch> selectByMacAddress(@Param("macAddress") String macAddress);

    List<InstalledPatch> selectByMacAddressAndScanTime(@Param("macAddress") String macAddress, @Param("scanTime") String scanTime);
}
