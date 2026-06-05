package com.cd.server;

import com.cd.entity.AssetSnapshotView;
import com.cd.entity.PageResult;

import java.util.List;

public interface AssetSnapshotService {

    PageResult<AssetSnapshotView> page(String assetType, int pageNo, int pageSize, String keyword);

    AssetSnapshotView getById(String assetType, Long id);

    AssetSnapshotView getByMacAddress(String assetType, String macAddress);

    List<AssetSnapshotView> listByMacAddress(String assetType, String macAddress);

    void deleteById(String assetType, Long id);
}
