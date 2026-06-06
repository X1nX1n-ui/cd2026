package com.cd.server.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cd.server.AssetDataService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AssetDataServiceImpl implements AssetDataService {

    private final JdbcTemplate jdbcTemplate;

    public AssetDataServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public boolean saveAssetData(String assetType, String payload) {
        JSONObject jsonObject;
        try {
            jsonObject = JSON.parseObject(normalizePayload(payload));
        } catch (Exception ex) {
            return false;
        }

        String normalizedType = normalizeAssetType(assetType);
        if (normalizedType == null) {
            return false;
        }

        return switch (normalizedType) {
            case "account" -> saveAccounts(jsonObject, payload);
            case "service" -> saveServices(jsonObject, payload);
            case "process" -> saveProcesses(jsonObject, payload);
            case "app" -> saveApps(jsonObject, payload);
            default -> false;
        };
    }

    private boolean saveAccounts(JSONObject jsonObject, String payload) {
        String macAddress = normalizeMacAddress(jsonObject.getString("mac_address"));
        JSONArray accounts = jsonObject.getJSONArray("accounts");
        JSONArray shadowAccounts = jsonObject.getJSONArray("shadow_accounts");
        Integer accountCount = jsonObject.getInteger("account_count");
        Integer shadowAccountCount = jsonObject.getInteger("shadow_account_count");

        if (macAddress == null || accounts == null || shadowAccounts == null
            || accountCount == null || shadowAccountCount == null) {
            return false;
        }

        jdbcTemplate.update("""
                INSERT INTO `accounts` (
                    mac_address, accounts, shadow_accounts, account_count, shadow_account_count, raw_payload
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                macAddress,
                JSON.toJSONString(accounts),
                JSON.toJSONString(shadowAccounts),
                accountCount,
                shadowAccountCount,
                payload
        );
        return true;
    }

    private boolean saveServices(JSONObject jsonObject, String payload) {
        String macAddress = normalizeMacAddress(jsonObject.getString("mac_address"));
        JSONArray services = jsonObject.getJSONArray("services");
        Integer serviceCount = jsonObject.getInteger("service_count");

        if (macAddress == null || services == null || serviceCount == null) {
            return false;
        }

        jdbcTemplate.update("""
                INSERT INTO `services` (
                    mac_address, services, service_count, raw_payload
                ) VALUES (?, ?, ?, ?)
                """,
                macAddress,
                JSON.toJSONString(services),
                serviceCount,
                payload
        );
        return true;
    }

    private boolean saveProcesses(JSONObject jsonObject, String payload) {
        String macAddress = normalizeMacAddress(jsonObject.getString("mac_address"));
        JSONArray processes = jsonObject.getJSONArray("processes");
        Integer processCount = jsonObject.getInteger("process_count");

        if (macAddress == null || processes == null || processCount == null) {
            return false;
        }

        jdbcTemplate.update("""
                INSERT INTO `processes` (
                    mac_address, processes, process_count, raw_payload
                ) VALUES (?, ?, ?, ?)
                """,
                macAddress,
                JSON.toJSONString(processes),
                processCount,
                payload
        );
        return true;
    }

    private boolean saveApps(JSONObject jsonObject, String payload) {
        String macAddress = normalizeMacAddress(jsonObject.getString("mac_address"));
        JSONArray apps = jsonObject.getJSONArray("apps");
        Integer appCount = jsonObject.getInteger("app_count");

        if (macAddress == null || apps == null || appCount == null) {
            return false;
        }

        jdbcTemplate.update("""
                INSERT INTO `apps` (
                    mac_address, apps, app_count, raw_payload
                ) VALUES (?, ?, ?, ?)
                """,
                macAddress,
                JSON.toJSONString(apps),
                appCount,
                payload
        );
        return true;
    }

    private String normalizeMacAddress(String macAddress) {
        if (macAddress == null) {
            return null;
        }
        String normalized = macAddress.trim().replace(':', '-').toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeAssetType(String assetType) {
        if (assetType == null) {
            return null;
        }
        String normalized = assetType.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizePayload(String payload) {
        if (payload == null) {
            return "{}";
        }
        return payload.replace('\'', '"');
    }
}
