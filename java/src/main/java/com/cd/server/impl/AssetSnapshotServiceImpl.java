package com.cd.server.impl;

import com.cd.entity.AssetSnapshotView;
import com.cd.entity.PageResult;
import com.cd.exception.ResourceNotFoundException;
import com.cd.server.AssetSnapshotService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AssetSnapshotServiceImpl implements AssetSnapshotService {

    private static final Map<String, AssetTableMeta> TABLE_META = Map.of(
            "account", new AssetTableMeta("accounts", "accounts", "shadow_accounts", "account_count", "shadow_account_count"),
            "service", new AssetTableMeta("services", "services", null, "service_count", null),
            "process", new AssetTableMeta("processes", "processes", null, "process_count", null),
            "app", new AssetTableMeta("apps", "apps", null, "app_count", null)
    );

    private final JdbcTemplate jdbcTemplate;

    public AssetSnapshotServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResult<AssetSnapshotView> page(String assetType, int pageNo, int pageSize, String keyword) {
        AssetTableMeta meta = requireMeta(assetType);
        int validPageNo = Math.max(pageNo, 1);
        int validPageSize = Math.max(pageSize, 1);
        int offset = (validPageNo - 1) * validPageSize;
        String normalizedKeyword = trimToNull(keyword);

        String baseWhere = normalizedKeyword == null
                ? ""
                : " WHERE mac_address LIKE CONCAT('%', ?, '%')";

        Long total = normalizedKeyword == null
                ? jdbcTemplate.queryForObject("SELECT COUNT(1) FROM `" + meta.tableName() + "`", Long.class)
                : jdbcTemplate.queryForObject("SELECT COUNT(1) FROM `" + meta.tableName() + "`" + baseWhere, Long.class, normalizedKeyword);

        String sql = "SELECT id, mac_address, "
                + meta.primaryPayloadColumn() + " AS primary_payload, "
                + buildSecondarySelect(meta.secondaryPayloadColumn()) + ", "
                + meta.primaryCountColumn() + " AS primary_count, "
                + buildSecondaryCountSelect(meta.secondaryCountColumn()) + ", "
                + "raw_payload, created_at, updated_at "
                + "FROM `" + meta.tableName() + "`"
                + baseWhere
                + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?";

        List<AssetSnapshotView> records = normalizedKeyword == null
                ? jdbcTemplate.query(sql, rowMapper(), validPageSize, offset)
                : jdbcTemplate.query(sql, rowMapper(), normalizedKeyword, validPageSize, offset);

        PageResult<AssetSnapshotView> pageResult = new PageResult<>();
        pageResult.setPageNo(validPageNo);
        pageResult.setPageSize(validPageSize);
        pageResult.setTotal(total == null ? 0 : total);
        pageResult.setRecords(records);
        return pageResult;
    }

    @Override
    public AssetSnapshotView getById(String assetType, Long id) {
        AssetTableMeta meta = requireMeta(assetType);
        List<AssetSnapshotView> records = jdbcTemplate.query(
                "SELECT id, mac_address, "
                        + meta.primaryPayloadColumn() + " AS primary_payload, "
                        + buildSecondarySelect(meta.secondaryPayloadColumn()) + ", "
                        + meta.primaryCountColumn() + " AS primary_count, "
                        + buildSecondaryCountSelect(meta.secondaryCountColumn()) + ", "
                        + "raw_payload, created_at, updated_at "
                        + "FROM `" + meta.tableName() + "` WHERE id = ?",
                rowMapper(),
                id
        );
        if (records.isEmpty()) {
            throw new ResourceNotFoundException("asset snapshot not found, id=" + id);
        }
        return records.get(0);
    }

    @Override
    @Transactional
    public void deleteById(String assetType, Long id) {
        AssetTableMeta meta = requireMeta(assetType);
        int rows = jdbcTemplate.update("DELETE FROM `" + meta.tableName() + "` WHERE id = ?", id);
        if (rows == 0) {
            throw new ResourceNotFoundException("asset snapshot not found, id=" + id);
        }
    }

    private AssetTableMeta requireMeta(String assetType) {
        String normalizedType = trimToNull(assetType);
        AssetTableMeta meta = normalizedType == null ? null : TABLE_META.get(normalizedType.toLowerCase(Locale.ROOT));
        if (meta == null) {
            throw new ResourceNotFoundException("asset type not found: " + assetType);
        }
        return meta;
    }

    private RowMapper<AssetSnapshotView> rowMapper() {
        return new RowMapper<>() {
            @Override
            public AssetSnapshotView mapRow(ResultSet rs, int rowNum) throws SQLException {
                AssetSnapshotView view = new AssetSnapshotView();
                view.setId(rs.getLong("id"));
                view.setMacAddress(rs.getString("mac_address"));
                view.setPrimaryPayload(rs.getString("primary_payload"));
                view.setSecondaryPayload(rs.getString("secondary_payload"));
                view.setPrimaryCount((Integer) rs.getObject("primary_count"));
                view.setSecondaryCount((Integer) rs.getObject("secondary_count"));
                view.setRawPayload(rs.getString("raw_payload"));
                if (rs.getTimestamp("created_at") != null) {
                    view.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
                if (rs.getTimestamp("updated_at") != null) {
                    view.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
                return view;
            }
        };
    }

    private String buildSecondarySelect(String secondaryPayloadColumn) {
        return secondaryPayloadColumn == null ? "NULL AS secondary_payload" : secondaryPayloadColumn + " AS secondary_payload";
    }

    private String buildSecondaryCountSelect(String secondaryCountColumn) {
        return secondaryCountColumn == null ? "NULL AS secondary_count" : secondaryCountColumn + " AS secondary_count";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record AssetTableMeta(
            String tableName,
            String primaryPayloadColumn,
            String secondaryPayloadColumn,
            String primaryCountColumn,
            String secondaryCountColumn
    ) {
    }
}
