package com.cd.mapper;

import com.cd.entity.PatchSecurityResultEntity;
import com.cd.entity.PatchSecurityRiskEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PatchSecurityResultMapper {

    int upsertResult(PatchSecurityResultEntity result);

    int insertRisks(@Param("risks") List<PatchSecurityRiskEntity> risks);

    int deleteRisksByHostId(@Param("hostId") Long hostId);

    List<PatchSecurityResultEntity> findAtRiskHosts(@Param("keyword") String keyword);

    PatchSecurityResultEntity findByHostId(@Param("hostId") Long hostId);

    List<PatchSecurityRiskEntity> findRisksByHostId(@Param("hostId") Long hostId);

    List<Long> findAllHostIdsWithPatches();
    int updateAiSummary(@Param("hostId") Long hostId, @Param("aiSummary") String aiSummary);
    List<PatchSecurityResultEntity> findAllHostsWithAnalysis(@Param("keyword") String keyword);
}