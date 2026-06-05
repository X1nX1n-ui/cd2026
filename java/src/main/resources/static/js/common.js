window.PlatformUtils = (function () {
    const authStorageKey = "threat-platform-auth";
    const rememberedLoginKey = "threat-platform-remembered";

    function readJson(storage, key) {
        try {
            const value = storage.getItem(key);
            return value ? JSON.parse(value) : null;
        } catch (error) {
            return null;
        }
    }

    function writeJson(storage, key, value) {
        storage.setItem(key, JSON.stringify(value));
    }

    function getAuthData() {
        return readJson(localStorage, authStorageKey) || readJson(sessionStorage, authStorageKey) || {};
    }

    function saveAuth(token, user, rememberMe) {
        clearAuth();
        const payload = {
            token: token,
            user: user || null
        };
        if (rememberMe) {
            writeJson(localStorage, authStorageKey, payload);
        } else {
            writeJson(sessionStorage, authStorageKey, payload);
        }
    }

    function clearAuth() {
        localStorage.removeItem(authStorageKey);
        sessionStorage.removeItem(authStorageKey);
    }

    function getToken() {
        return getAuthData().token || "";
    }

    function getCurrentUser() {
        return getAuthData().user || null;
    }

    function setCurrentUser(user) {
        const authData = getAuthData();
        if (!authData.token) {
            return;
        }
        saveAuth(authData.token, user, !!readJson(localStorage, authStorageKey));
    }

    function saveRememberedLogin(data) {
        if (!data || !data.rememberMe) {
            localStorage.removeItem(rememberedLoginKey);
            return;
        }
        writeJson(localStorage, rememberedLoginKey, {
            userName: data.userName || "",
            rememberMe: true,
            hasActiveToken: !!data.hasActiveToken
        });
    }

    function getRememberedLogin() {
        return readJson(localStorage, rememberedLoginKey) || {};
    }

    function readSessionCache(key, maxAgeMs) {
        if (!key) {
            return null;
        }
        const payload = readJson(sessionStorage, key);
        if (!payload || typeof payload !== "object") {
            return null;
        }
        if (maxAgeMs && payload.cachedAt && Date.now() - payload.cachedAt > maxAgeMs) {
            sessionStorage.removeItem(key);
            return null;
        }
        return payload.data;
    }

    function writeSessionCache(key, data) {
        if (!key) {
            return;
        }
        writeJson(sessionStorage, key, {
            cachedAt: Date.now(),
            data: data
        });
    }

    function authHeaders(extraHeaders) {
        const headers = Object.assign({}, extraHeaders || {});
        const token = getToken();
        if (token) {
            headers.Authorization = "Bearer " + token;
        }
        return headers;
    }

    function redirectToLogin() {
        clearAuth();
        if (window.top) {
            window.top.location.href = "/login.html";
            return;
        }
        window.location.href = "/login.html";
    }

    function request(url, options) {
        const requestOptions = Object.assign({}, options || {});
        requestOptions.headers = authHeaders(requestOptions.headers);

        return fetch(url, requestOptions).then(async function (response) {
            const contentType = (response.headers.get("content-type") || "").toLowerCase();
            let data = {};

            if (response.status !== 204) {
                if (contentType.indexOf("application/json") >= 0) {
                    try {
                        data = await response.json();
                    } catch (error) {
                        data = {};
                    }
                } else {
                    const text = await response.text();
                    data = text ? JSON.parse(text) : {};
                }
            }

            if (response.status === 401) {
                redirectToLogin();
                throw new Error(data.message || "未登录或登录已过期");
            }

            if (!response.ok) {
                throw new Error(data.message || "请求失败");
            }
            return data;
        });
    }

    function formatDateTime(value) {
        if (!value) {
            return "--";
        }
        return String(value).replace("T", " ").substring(0, 19);
    }

    function renderStatus(value) {
        const statusMap = {
            NORMAL: "正常",
            DISABLED: "禁用",
            LOCKED: "锁定"
        };
        return statusMap[value] || value || "--";
    }

    function renderHostStatus(value) {
        const normalized = value === null || value === undefined ? "" : String(value).trim().toUpperCase();
        if (normalized === "1" || normalized === "ONLINE") {
            return '<span class="status-pill status-pill-online">在线</span>';
        }
        if (normalized === "0" || normalized === "OFFLINE") {
            return '<span class="status-pill status-pill-offline">下线</span>';
        }
        return '<span class="status-pill status-pill-unknown">' + escapeHtml(normalized || "--") + "</span>";
    }

    function renderLoginResult(value) {
        const resultMap = {
            SUCCESS: "成功",
            FAILURE: "失败",
            LOCKED: "锁定",
            DISABLED: "禁用"
        };
        return resultMap[value] || value || "--";
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return "";
        }
        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function renderValue(value) {
        return escapeHtml(value === null || value === undefined || value === "" ? "--" : value);
    }

    function hasPermission(permissionCode) {
        const user = getCurrentUser();
        const permissionCodes = user && Array.isArray(user.permissionCodes) ? user.permissionCodes : [];
        return permissionCodes.includes(permissionCode);
    }

    return {
        request: request,
        authHeaders: authHeaders,
        getToken: getToken,
        saveAuth: saveAuth,
        clearAuth: clearAuth,
        redirectToLogin: redirectToLogin,
        getCurrentUser: getCurrentUser,
        setCurrentUser: setCurrentUser,
        saveRememberedLogin: saveRememberedLogin,
        getRememberedLogin: getRememberedLogin,
        readSessionCache: readSessionCache,
        writeSessionCache: writeSessionCache,
        hasPermission: hasPermission,
        formatDateTime: formatDateTime,
        renderStatus: renderStatus,
        renderHostStatus: renderHostStatus,
        renderLoginResult: renderLoginResult,
        escapeHtml: escapeHtml,
        renderValue: renderValue
    };
}());
