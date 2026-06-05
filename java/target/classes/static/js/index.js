layui.use(["element", "layer"], function () {
    const element = layui.element;
    const layer = layui.layer;
    const tabsFilter = "mainTabs";
    const menuCacheKey = "threat-platform-menu-cache";
    const menuTree = document.getElementById("menuTree");
    const contextMenu = document.getElementById("tabContextMenu");
    const logoutButton = document.getElementById("logoutButton");
    const profileEntryButton = document.getElementById("profileEntryButton");
    const sidebarToggleButton = document.getElementById("sidebarToggleButton");
    const sidebarOverlay = document.getElementById("sidebarOverlay");
    const clearTabsButton = document.getElementById("clearTabsButton");
    const tabState = new Map();
    const collapseBreakpoint = 1200;
    const fallbackHomeTab = {
        id: "dashboard-home",
        title: "后台主页",
        url: "/pages/dashboard/home.html",
        closable: false
    };
    let contextTabId = "";
    let defaultTab = null;
    const handleViewportResize = debounce(applyResponsiveSidebar, 80);

    init();

    function init() {
        bindTabs();
        bindContextMenu();
        bindMenuTree();
        bindProfileEntry();
        bindLogout();
        bindResponsiveShell();
        bindTabTools();

        bootstrapFromCache();
        hydrateCurrentUser();
        hydrateMenus();
    }

    function bootstrapFromCache() {
        const cachedUser = PlatformUtils.getCurrentUser();
        const cachedMenus = readMenuCache();

        if (cachedUser) {
            renderCurrentUser(cachedUser);
        }

        openFallbackHomeTab();

        if (cachedMenus.length) {
            renderMenus(cachedMenus);
            const cachedDefaultTab = findDefaultTab(cachedMenus);
            if (cachedDefaultTab) {
                defaultTab = cachedDefaultTab;
                openTab(cachedDefaultTab);
            } else {
                ensureDefaultTab(cachedMenus, true);
            }
        } else {
            menuTree.innerHTML = '<li class="menu-empty">正在加载菜单...</li>';
        }
    }

    function hydrateCurrentUser() {
        PlatformUtils.request("/api/auth/me")
            .then(function (authResult) {
                if (!authResult.success || !authResult.user) {
                    PlatformUtils.redirectToLogin();
                    return;
                }

                PlatformUtils.setCurrentUser(authResult.user);
                renderCurrentUser(authResult.user);
            })
            .catch(function (error) {
                layer.msg(error.message || "用户信息加载失败", {icon: 2, time: 1200});
            });
    }

    function hydrateMenus() {
        PlatformUtils.request("/api/auth/menus")
            .then(function (menus) {
                const normalizedMenus = Array.isArray(menus) ? menus : [];
                writeMenuCache(normalizedMenus);
                renderMenus(normalizedMenus);
                ensureDefaultTab(normalizedMenus, false);
                prefetchPageOptions(normalizedMenus);
            })
            .catch(function (error) {
                layer.msg(error.message || "菜单加载失败", {icon: 2, time: 1200});
            });
    }

    function bindTabs() {
        element.on("tab(" + tabsFilter + ")", function (data) {
            const tabId = getTabIdFromEvent(data);
            if (!tabId) {
                return;
            }
            syncMenuState(tabId);
        });

        element.on("tabDelete(" + tabsFilter + ")", function (data) {
            const tabId = getTabIdFromEvent(data);
            if (!tabId) {
                hideContextMenu();
                window.setTimeout(syncCurrentTab, 0);
                return;
            }
            disposeTabFrame(tabId);
            tabState.delete(tabId);
            hideContextMenu();
            if (tabState.size === 0) {
                clearMenuState();
                return;
            }
            window.setTimeout(syncCurrentTab, 0);
        });
    }

    function bindContextMenu() {
        document.querySelector(".layui-tab-title").addEventListener("contextmenu", function (event) {
            const tabItem = event.target.closest("li[lay-id]");
            if (!tabItem) {
                return;
            }
            event.preventDefault();
            contextTabId = tabItem.getAttribute("lay-id");
            const left = Math.min(event.clientX, window.innerWidth - 180);
            const top = Math.min(event.clientY, window.innerHeight - 140);
            contextMenu.style.left = left + "px";
            contextMenu.style.top = top + "px";
            contextMenu.classList.add("is-visible");
        });

        document.addEventListener("click", hideContextMenu);

        contextMenu.querySelectorAll("[data-action]").forEach(function (item) {
            item.addEventListener("click", function () {
                const action = item.dataset.action;
                if (action === "closeCurrent") {
                    closeCurrentTab();
                }
                if (action === "closeOthers") {
                    closeOtherTabs();
                }
                if (action === "closeAll") {
                    closeAllTabs();
                }
                hideContextMenu();
            });
        });
    }

    function bindMenuTree() {
        if (menuTree.dataset.bound === "true") {
            return;
        }
        menuTree.addEventListener("click", handleMenuClick);
        menuTree.dataset.bound = "true";
    }

    function bindResponsiveShell() {
        applyResponsiveSidebar();

        sidebarToggleButton.addEventListener("click", function () {
            if (!isCompactViewport()) {
                return;
            }
            document.body.classList.toggle("is-sidebar-open");
        });

        sidebarOverlay.addEventListener("click", function () {
            document.body.classList.remove("is-sidebar-open");
        });

        window.addEventListener("resize", handleViewportResize);
    }

    function bindLogout() {
        logoutButton.addEventListener("click", function () {
            PlatformUtils.clearAuth();
            PlatformUtils.redirectToLogin();
        });
    }

    function bindTabTools() {
        clearTabsButton.addEventListener("click", function () {
            closeAllTabs(true);
        });
    }

    function bindProfileEntry() {
        profileEntryButton.addEventListener("click", function () {
            openTab({
                id: "user-profile",
                title: "个人信息",
                url: "/pages/user/profile.html",
                closable: true
            });
        });
    }

    function renderCurrentUser(user) {
        document.getElementById("currentUserName").textContent = user.userName || "--";
        document.getElementById("currentUserAvatar").src = user.userHeader || "/images/default-avatar.svg";
    }

    function renderMenus(menus) {
        if (!menus.length) {
            menuTree.innerHTML = '<li class="menu-empty">当前暂无可访问功能</li>';
            return;
        }

        menuTree.innerHTML = menus.map(renderMenuNode).join("");
        element.render("nav", "sideNav");
        lucide.createIcons();
    }

    function renderMenuNode(menu) {
        const icon = menu.icon ? '<i data-lucide="' + PlatformUtils.escapeHtml(menu.icon) + '"></i>' : "";
        const label = '<span class="menu-link"><span class="menu-link-icon">' + icon + '</span><span class="menu-link-text">' + PlatformUtils.escapeHtml(menu.title) + '</span></span>';

        if (menu.children && menu.children.length) {
            return '<li class="layui-nav-item">'
                + '<a href="javascript:;">' + label + '</a>'
                + '<dl class="layui-nav-child">'
                + menu.children.map(function (child) {
                    return '<dd><a href="javascript:;" data-tab-id="' + PlatformUtils.escapeHtml(child.tabId) + '" data-tab-title="' + PlatformUtils.escapeHtml(child.title) + '" data-tab-url="' + PlatformUtils.escapeHtml(child.url || "") + '" data-closable="true">' + renderChildLabel(child) + '</a></dd>';
                }).join("")
                + '</dl>'
                + '</li>';
        }

        return '<li class="layui-nav-item">'
            + '<a href="javascript:;" data-tab-id="' + PlatformUtils.escapeHtml(menu.tabId) + '" data-tab-title="' + PlatformUtils.escapeHtml(menu.title) + '" data-tab-url="' + PlatformUtils.escapeHtml(menu.url || "") + '" data-closable="false">' + label + '</a>'
            + '</li>';
    }

    function renderChildLabel(menu) {
        const icon = menu.icon ? '<i data-lucide="' + PlatformUtils.escapeHtml(menu.icon) + '"></i>' : "";
        return '<span class="menu-link"><span class="menu-link-icon">' + icon + '</span><span class="menu-link-text">' + PlatformUtils.escapeHtml(menu.title) + '</span></span>';
    }

    function handleMenuClick(event) {
        const menuItem = event.target.closest("[data-tab-id]");
        if (!menuItem) {
            return;
        }
        const url = menuItem.dataset.tabUrl || "";
        if (!url) {
            return;
        }
        openTab({
            id: menuItem.dataset.tabId,
            title: menuItem.dataset.tabTitle,
            url: url,
            closable: menuItem.dataset.closable !== "false"
        });

        if (isCompactViewport()) {
            document.body.classList.remove("is-sidebar-open");
        }
    }

    function openTab(tab) {
        if (!tab || !tab.url) {
            return;
        }

        const existingTab = document.querySelector('.layui-tab-title li[lay-id="' + tab.id + '"]');
        if (!existingTab && tabState.has(tab.id)) {
            tabState.delete(tab.id);
        }

        if (!tabState.has(tab.id)) {
            tabState.set(tab.id, tab);
            element.tabAdd(tabsFilter, {
                id: tab.id,
                title: '<span class="app-tab-label">' + PlatformUtils.escapeHtml(tab.title) + "</span>",
                content: '<div class="tab-panel"><iframe class="tab-frame" data-tab-id="' + PlatformUtils.escapeHtml(tab.id) + '" src="' + tab.url + '" frameborder="0"></iframe></div>'
            });
            if (tab.closable === false) {
                removeTabCloseIcon(tab.id);
            }
        }
        element.tabChange(tabsFilter, tab.id);
        syncMenuState(tab.id);
    }

    function ensureDefaultTab(menus, fromCache) {
        defaultTab = findDefaultTab(menus);
        if (!defaultTab) {
            if (!fromCache) {
                layer.msg("当前账号未分配可访问菜单", {icon: 2, time: 1400});
            }
            return;
        }

        const activeTabId = getActiveTabId();
        if (!activeTabId || activeTabId === fallbackHomeTab.id) {
            if (activeTabId === fallbackHomeTab.id && defaultTab.id !== fallbackHomeTab.id) {
                tabState.delete(fallbackHomeTab.id);
                disposeTabFrame(fallbackHomeTab.id);
                element.tabDelete(tabsFilter, fallbackHomeTab.id);
            }
            openTab(defaultTab);
            return;
        }
        syncMenuState(activeTabId);
    }

    function openFallbackHomeTab() {
        if (getActiveTabId() || tabState.size > 0) {
            return;
        }
        openTab(fallbackHomeTab);
    }

    function removeTabCloseIcon(tabId) {
        const tabItem = document.querySelector('.layui-tab-title li[lay-id="' + tabId + '"]');
        if (!tabItem) {
            return;
        }
        const closeIcon = tabItem.querySelector(".layui-tab-close");
        if (closeIcon) {
            closeIcon.remove();
        }
    }

    function closeCurrentTab() {
        if (!contextTabId || (defaultTab && contextTabId === defaultTab.id)) {
            return;
        }
        tabState.delete(contextTabId);
        disposeTabFrame(contextTabId);
        element.tabDelete(tabsFilter, contextTabId);
    }

    function closeOtherTabs() {
        const ids = Array.from(tabState.keys()).filter(function (id) {
            return (!defaultTab || id !== defaultTab.id) && id !== contextTabId;
        });
        ids.forEach(function (id) {
            tabState.delete(id);
            disposeTabFrame(id);
        });
        ids.forEach(function (id) {
            element.tabDelete(tabsFilter, id);
        });
        if (contextTabId && tabState.has(contextTabId)) {
            element.tabChange(tabsFilter, contextTabId);
        }
    }

    function closeAllTabs(includeDefault) {
        const ids = Array.from(tabState.keys()).filter(function (id) {
            if (includeDefault) {
                return true;
            }
            return !defaultTab || id !== defaultTab.id;
        });
        ids.forEach(function (id) {
            tabState.delete(id);
            disposeTabFrame(id);
        });
        ids.forEach(function (id) {
            if (includeDefault || !defaultTab || id !== defaultTab.id) {
                element.tabDelete(tabsFilter, id);
            }
        });
        if (!includeDefault && defaultTab) {
            openTab(defaultTab);
        }
    }

    function hideContextMenu() {
        contextMenu.classList.remove("is-visible");
    }

    function disposeTabFrame(tabId) {
        const iframe = document.querySelector('.tab-frame[data-tab-id="' + tabId + '"]');
        if (!iframe) {
            return;
        }
        iframe.src = "about:blank";
    }

    function getTabIdFromEvent(data) {
        if (!data) {
            return "";
        }

        const elem = data.elem;
        if (elem && typeof elem.getAttribute === "function") {
            return elem.getAttribute("lay-id") || "";
        }

        if (elem && elem[0] && typeof elem[0].getAttribute === "function") {
            return elem[0].getAttribute("lay-id") || "";
        }

        if (typeof data.index === "number") {
            const tabItems = document.querySelectorAll(".layui-tab-title li[lay-id]");
            const tabItem = tabItems[data.index];
            if (tabItem) {
                return tabItem.getAttribute("lay-id") || "";
            }
        }

        return "";
    }

    function getActiveTabId() {
        const currentTab = document.querySelector(".layui-tab-title li.layui-this");
        return currentTab ? currentTab.getAttribute("lay-id") || "" : "";
    }

    function syncCurrentTab() {
        const currentTab = document.querySelector(".layui-tab-title li.layui-this");
        if (!currentTab) {
            clearMenuState();
            return;
        }
        syncMenuState(currentTab.getAttribute("lay-id"));
    }

    function clearMenuState() {
        document.querySelectorAll(".layui-nav-tree .layui-this").forEach(function (item) {
            item.classList.remove("layui-this");
        });
        document.querySelectorAll(".layui-nav-tree .menu-parent-active").forEach(function (item) {
            item.classList.remove("menu-parent-active");
        });
    }

    function syncMenuState(tabId) {
        clearMenuState();

        const activeMenu = document.querySelector('[data-tab-id="' + tabId + '"]');
        if (!activeMenu) {
            return;
        }

        const activeDd = activeMenu.closest("dd");
        if (activeDd) {
            activeDd.classList.add("layui-this");
        }

        const activeLi = activeMenu.closest(".layui-nav-item");
        if (activeLi) {
            if (activeLi.querySelector(".layui-nav-child")) {
                activeLi.classList.add("menu-parent-active");
                activeLi.classList.add("layui-nav-itemed");
            } else {
                activeLi.classList.add("layui-this");
            }
        }
    }

    function findDefaultTab(menus) {
        const preferredDefault = findMenuByUrl(menus, "/pages/dashboard/home.html");
        if (preferredDefault) {
            return {
                id: preferredDefault.tabId,
                title: preferredDefault.title,
                url: preferredDefault.url,
                closable: false
            };
        }

        const queue = menus.slice();
        while (queue.length) {
            const current = queue.shift();
            if (current.url) {
                return {
                    id: current.tabId,
                    title: current.title,
                    url: current.url,
                    closable: false
                };
            }
            if (Array.isArray(current.children) && current.children.length) {
                queue.unshift.apply(queue, current.children);
            }
        }
        return null;
    }

    function findMenuByUrl(menus, targetUrl) {
        const queue = Array.isArray(menus) ? menus.slice() : [];
        while (queue.length) {
            const current = queue.shift();
            if (current && current.url === targetUrl) {
                return current;
            }
            if (current && Array.isArray(current.children) && current.children.length) {
                queue.push.apply(queue, current.children);
            }
        }
        return null;
    }

    function prefetchPageOptions(menus) {
        const menuUrls = collectMenuUrls(menus);
        window.setTimeout(function () {
            if (menuUrls["/pages/user/list.html"] && !PlatformUtils.readSessionCache("page-role-options-cache", 5 * 60 * 1000)) {
                PlatformUtils.request("/api/roles/options")
                    .then(function (data) {
                        PlatformUtils.writeSessionCache("page-role-options-cache", Array.isArray(data) ? data : []);
                    })
                    .catch(function () {});
            }

            if ((menuUrls["/pages/system/roles.html"] || menuUrls["/pages/system/permissions.html"])
                && !PlatformUtils.readSessionCache("page-permission-options-cache", 5 * 60 * 1000)) {
                PlatformUtils.request("/api/permissions/options")
                    .then(function (data) {
                        PlatformUtils.writeSessionCache("page-permission-options-cache", Array.isArray(data) ? data : []);
                    })
                    .catch(function () {});
            }
        }, 180);
    }

    function collectMenuUrls(menus) {
        const urlMap = {};
        const queue = Array.isArray(menus) ? menus.slice() : [];
        while (queue.length) {
            const current = queue.shift();
            if (current && current.url) {
                urlMap[current.url] = true;
            }
            if (current && Array.isArray(current.children) && current.children.length) {
                queue.push.apply(queue, current.children);
            }
        }
        return urlMap;
    }

    function readMenuCache() {
        try {
            const cached = sessionStorage.getItem(menuCacheKey) || localStorage.getItem(menuCacheKey);
            const parsed = cached ? JSON.parse(cached) : [];
            return Array.isArray(parsed) ? parsed : [];
        } catch (error) {
            return [];
        }
    }

    function writeMenuCache(menus) {
        try {
            const serialized = JSON.stringify(Array.isArray(menus) ? menus : []);
            sessionStorage.setItem(menuCacheKey, serialized);
            localStorage.setItem(menuCacheKey, serialized);
        } catch (error) {
            // Ignore cache write failures to avoid blocking page startup.
        }
    }

    function isCompactViewport() {
        return window.innerWidth < collapseBreakpoint;
    }

    function applyResponsiveSidebar() {
        document.body.classList.toggle("is-sidebar-collapsed", isCompactViewport());
        if (!isCompactViewport()) {
            document.body.classList.remove("is-sidebar-open");
        }
    }

    function debounce(fn, delay) {
        let timer = null;
        return function () {
            const context = this;
            const args = arguments;
            window.clearTimeout(timer);
            timer = window.setTimeout(function () {
                fn.apply(context, args);
            }, delay);
        };
    }
});
