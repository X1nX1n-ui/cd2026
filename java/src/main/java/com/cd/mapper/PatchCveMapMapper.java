package com.cd.mapper;

import com.cd.entity.PatchCveMap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PatchCveMapMapper {

    List<PatchCveMap> findByPatchIds(@Param("patchIds") List<String> patchIds);

    List<PatchCveMap> findByCveIds(@Param("cveIds") List<String> cveIds);

    List<PatchCveMap> findMissingPatchesForOs(
        @Param("vendor") String vendor,
        @Param("productPatterns") List<String> productPatterns
    );

    List<PatchCveMap> findSupersedingPatches(@Param("patchId") String patchId);

    List<PatchCveMap> findAllSecurityPatches();
}