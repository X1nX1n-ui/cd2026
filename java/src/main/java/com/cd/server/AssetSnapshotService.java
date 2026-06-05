package com.cd.server;

import com.cd.entity.AssetSnapshotView;
import com.cd.entity.PageResult;

public interface AssetSnapshotService {

    PageResult<AssetSnapshotView> page(String assetType, int pageNo, int pageSize, String keyword);

    AssetSnapshotView getById(String assetType, Long id);

    void deleteById(String assetType, Long id);
}
