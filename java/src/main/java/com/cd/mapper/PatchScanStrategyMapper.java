package com.cd.mapper;

import com.cd.entity.PatchScanStrategy;
import org.apache.ibatis.annotations.Param;

public interface PatchScanStrategyMapper {

    PatchScanStrategy selectConfig();

    int upsert(PatchScanStrategy config);
}
