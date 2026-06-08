with open(r"d:\3\code\java\src\main\resources\static\js\index.js", "rb") as f:
    data = f.read()

C = lambda s: s.encode("utf-8")

old = (
    b"function bootstrapFromCache() {\n"
    b"        const cachedUser = PlatformUtils.getCurrentUser();\n"
    b"        const cachedMenus = readMenuCache();\n"
    b"\n"
    b"        if (cachedUser) {\n"
    b"            renderCurrentUser(cachedUser);\n"
    b"        }\n"
    b"\n"
    b"        openFallbackHomeTab();\n"
    b"\n"
    b"        if (cachedMenus.length) {\n"
    b"            renderMenus(cachedMenus);\n"
    b"            const cachedDefaultTab = findDefaultTab(cachedMenus);\n"
    b"            if (cachedDefaultTab) {\n"
    b"                defaultTab = cachedDefaultTab;\n"
    b"                openTab(cachedDefaultTab);\n"
    b"            } else {\n"
    b"                ensureDefaultTab(cachedMenus, true);\n"
    b"            }\n"
    b"        } else {\n"
    b'            menuTree.innerHTML = ' + C("'<li class=\"menu-empty\">正在加载菜单...</li>';") + b'\n'
    b"        }\n"
    b"    }"
)

new = (
    b"function bootstrapFromCache() {\n"
    b"        const cachedUser = PlatformUtils.getCurrentUser();\n"
    b"        const cachedMenus = readMenuCache();\n"
    b"\n"
    b"        if (cachedUser) {\n"
    b"            renderCurrentUser(cachedUser);\n"
    b"        }\n"
    b"\n"
    b"        if (cachedMenus.length) {\n"
    b"            renderMenus(cachedMenus);\n"
    b"            const cachedDefaultTab = findDefaultTab(cachedMenus);\n"
    b"            if (cachedDefaultTab) {\n"
    b"                defaultTab = cachedDefaultTab;\n"
    b"                openTab(cachedDefaultTab);\n"
    b"            } else {\n"
    b"                ensureDefaultTab(cachedMenus, true);\n"
    b"            }\n"
    b"        } else {\n"
    b'            menuTree.innerHTML = ' + C("'<li class=\"menu-empty\">正在加载菜单...</li>';") + b'\n'
    b"        }\n"
    b"\n"
    b"        openFallbackHomeTab();\n"
    b"    }"
)

if old in data:
    data = data.replace(old, new)
    print("Fixed: fallback tab moved after menu tabs")
else:
    print("NOT FOUND")

with open(r"d:\3\code\java\src\main\resources\static\js\index.js", "wb") as f:
    f.write(data)