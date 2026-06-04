layui.use(["element", "layer"], function () {
    const element = layui.element;
    const layer = layui.layer;
    const tabsFilter = "mainTabs";
    const menuTree = document.getElementById("menuTree");
    const contextMenu = document.getElementById("tabContextMenu");
    const logoutButton = document.getElementById("logoutButton");
    const tabState = new Map();
    let contextTabId = "";
    let defaultTab = null;

    init();

    function init() {
        bindTabs();
        bindContextMenu();
        bindLogout();

        Promise.all([
            PlatformUtils.request("/api/auth/me"),
            PlatformUtils.request("/api/auth/menus")
        ])
            .then(function (results) {
                const authResult = results[0];
                const menus = Array.isArray(results[1]) ? results[1] : [];

                if (!authResult.success || !authResult.user) {
                    PlatformUtils.redirectToLogin();
                    return;
                }

                PlatformUtils.setCurrentUser(authResult.user);
                renderCurrentUser(authResult.user);
                renderMenus(menus);

                defaultTab = findDefaultTab(menus);
                if (!defaultTab) {
                    layer.msg("当前账号未分配可访问菜单", {icon: 2, time: 1400});
                    return;
                }
                openTab(defaultTab);
            })
            .catch(function (error) {
                layer.msg(error.message || "系统初始化失败", {icon: 2, time: 1200});
            });
    }

    function bindTabs() {
        element.on("tab(" + tabsFilter + ")", function (data) {
            const tabId = data.elem.getAttribute("lay-id");
            syncMenuState(tabId);
        });

        element.on("tabDelete(" + tabsFilter + ")", function (data) {
            const tabId = data.elem.getAttribute("lay-id");
            tabState.delete(tabId);
            hideContextMenu();
            if (tabState.size === 0 && defaultTab) {
                openTab(defaultTab);
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

    function bindLogout() {
        logoutButton.addEventListener("click", function () {
            PlatformUtils.clearAuth();
            PlatformUtils.redirectToLogin();
        });
    }

    function renderCurrentUser(user) {
        document.getElementById("currentUserName").textContent = user.userName || "--";
    }

    function renderMenus(menus) {
        if (!menus.length) {
            menuTree.innerHTML = '<li class="menu-empty">当前暂无可访问功能</li>';
            return;
        }

        menuTree.innerHTML = menus.map(renderMenuNode).join("");
        element.render("nav", "sideNav");
        menuTree.addEventListener("click", handleMenuClick);
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
    }

    function openTab(tab) {
        if (!tab || !tab.url) {
            return;
        }

        if (!tabState.has(tab.id)) {
            tabState.set(tab.id, tab);
            element.tabAdd(tabsFilter, {
                id: tab.id,
                title: PlatformUtils.escapeHtml(tab.title),
                content: '<div class="tab-panel"><iframe class="tab-frame" src="' + tab.url + '" frameborder="0"></iframe></div>'
            });
            if (tab.closable === false) {
                removeTabCloseIcon(tab.id);
            }
        }
        element.tabChange(tabsFilter, tab.id);
        syncMenuState(tab.id);
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
        element.tabDelete(tabsFilter, contextTabId);
        tabState.delete(contextTabId);
    }

    function closeOtherTabs() {
        const ids = Array.from(tabState.keys()).filter(function (id) {
            return (!defaultTab || id !== defaultTab.id) && id !== contextTabId;
        });
        ids.forEach(function (id) {
            element.tabDelete(tabsFilter, id);
            tabState.delete(id);
        });
        if (contextTabId && tabState.has(contextTabId)) {
            element.tabChange(tabsFilter, contextTabId);
        }
    }

    function closeAllTabs() {
        Array.from(tabState.keys()).forEach(function (id) {
            if (!defaultTab || id !== defaultTab.id) {
                element.tabDelete(tabsFilter, id);
                tabState.delete(id);
            }
        });
        if (defaultTab) {
            openTab(defaultTab);
        }
    }

    function hideContextMenu() {
        contextMenu.classList.remove("is-visible");
    }

    function syncCurrentTab() {
        const currentTab = document.querySelector(".layui-tab-title li.layui-this");
        if (!currentTab) {
            return;
        }
        syncMenuState(currentTab.getAttribute("lay-id"));
    }

    function syncMenuState(tabId) {
        document.querySelectorAll(".layui-nav-tree .layui-this").forEach(function (item) {
            item.classList.remove("layui-this");
        });

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
            activeLi.classList.add("layui-this");
            if (activeLi.querySelector(".layui-nav-child")) {
                activeLi.classList.add("layui-nav-itemed");
            }
        }
    }

    function findDefaultTab(menus) {
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
});
